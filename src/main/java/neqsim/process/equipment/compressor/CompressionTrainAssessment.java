package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Detached assessment of one converged compressor-train candidate.
 *
 * <p>
 * Sum each compressor body's shaft power once and divide by net exported mass flow. Serial stage flows are never added
 * as production. The assessment supports interstage cooling, HP-body speed and recycle/load-sharing candidate
 * comparisons without running an optimizer or writing control commands. Caller-supplied margins and aggregate
 * shaft-power budget are not OEM design limits. Shared shafts, cooler capacity, coolant power, topology/mass closure
 * and dynamics must be checked in the enclosing ProcessSystem/ProcessModel.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class CompressionTrainAssessment implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final List<CompressorOperatingPointResult> stages;
  private final List<String> violations;
  private final double totalShaftPowerKW;
  private final double netExportKgPerSecond;
  private final double specificEnergyKJPerKg;
  private final double exportPressureBara;

  /**
   * Creates a detached assessment of already solved, unique compressor bodies.
   *
   * @param compressors compressor bodies included in this power-budget group
   * @param export net exported gas stream, after recycle withdrawal
   * @param powerBudgetKW finite positive aggregate shaft-power ceiling in kW
   * @param targetPressureBara finite positive required export absolute pressure in bara
   * @param pressureTolerance nonnegative relative export-pressure tolerance
   * @param minSurgeMargin required distance-to-surge fraction, e.g. 0.1 for ten percent
   * @param minStonewallMargin required distance-to-stonewall fraction
   * @param requireMaps true to reject candidates lacking active map evidence
   * @throws IllegalArgumentException for empty/duplicate bodies or invalid policy limits
   */
  public CompressionTrainAssessment(List<Compressor> compressors, StreamInterface export, double powerBudgetKW,
      double targetPressureBara, double pressureTolerance, double minSurgeMargin, double minStonewallMargin,
      boolean requireMaps) {
    positive(powerBudgetKW, "Power budget");
    positive(targetPressureBara, "Target pressure");
    nonNegative(pressureTolerance, "Pressure tolerance");
    nonNegative(minSurgeMargin, "Surge margin");
    nonNegative(minStonewallMargin, "Stonewall margin");
    if (compressors == null || compressors.isEmpty()) {
      throw new IllegalArgumentException("At least one compressor body is required");
    }
    List<String> faults = new ArrayList<>();
    List<CompressorOperatingPointResult> snapshots = new ArrayList<>();
    Set<Compressor> seen = Collections.newSetFromMap(new IdentityHashMap<Compressor, Boolean>());
    double sumPower = 0.0;
    for (Compressor compressor : compressors) {
      if (compressor == null || !seen.add(compressor)) {
        throw new IllegalArgumentException("Compressor bodies must be nonnull and unique");
      }
      CompressorOperatingPointResult point = compressor.getOperatingPointResult(pressureTolerance);
      snapshots.add(point);
      String name = compressor.getName();
      if (!point.isFeasible()) {
        faults.add(name + ": " + point.getOperatingStatus());
      }
      if (!finiteNonNegative(point.getPowerKW()) || point.getPolytropicEfficiency() <= 0.0
          || point.getPolytropicEfficiency() > 1.0 || !Double.isFinite(point.getPolytropicEfficiency())) {
        faults.add(name + ": invalid power or efficiency");
      }
      sumPower += point.getPowerKW();
      if (!gasOnly(compressor.getInletStream())) {
        faults.add(name + ": missing or wet suction evidence");
      }
      if (!point.isChartActive()) {
        if (requireMaps) {
          faults.add(name + ": map evidence required");
        }
      } else {
        CompressorChartInterface chart = compressor.getCompressorChart();
        try {
          if (!"IN_RANGE".equals(chart.getFlowRangeStatus(point.getFlowM3PerHour(), point.getSpeedRpm()))) {
            faults.add(name + ": flow outside digitized map");
          }
          double low = chart.getMinSpeedCurve();
          double high = chart.getMaxSpeedCurve();
          if (!Double.isFinite(low) || !Double.isFinite(high) || low <= 0 || high < low
              || !Double.isFinite(point.getSpeedRpm()) || point.getSpeedRpm() < low || point.getSpeedRpm() > high) {
            faults.add(name + ": speed outside digitized map or unresolved bounds");
          }
        } catch (RuntimeException ex) {
          faults.add(name + ": unresolved map evaluation");
        }
        if (!Double.isFinite(point.getDistanceToSurge()) || point.getDistanceToSurge() < minSurgeMargin) {
          faults.add(name + ": insufficient surge margin");
        }
        if (!Double.isFinite(point.getDistanceToStonewall()) || point.getDistanceToStonewall() < minStonewallMargin) {
          faults.add(name + ": insufficient stonewall margin");
        }
      }
      // Missing enabled capacity evidence must not disappear behind a false isViolated value.
      for (CompressorOperatingPointResult.ConstraintSnapshot constraint : point.getConstraints()) {
        if (constraint.isEnabled() && !Double.isFinite(constraint.getCurrentValue())) {
          faults.add(name + ": unresolved capacity " + constraint.getName());
        }
      }
    }
    totalShaftPowerKW = sumPower;
    if (!finiteNonNegative(sumPower) || sumPower > powerBudgetKW) {
      faults.add("Aggregate shaft power budget exceeded or unresolved");
    }
    double flow = Double.NaN;
    double pressure = Double.NaN;
    try {
      if (export != null) {
        flow = export.getFlowRate("kg/sec");
        pressure = export.getPressure("bara");
      }
    } catch (RuntimeException ex) {
      faults.add("Unresolved export measurements");
    }
    netExportKgPerSecond = flow;
    exportPressureBara = pressure;
    specificEnergyKJPerKg = Double.isFinite(flow) && flow > 0.0 && finiteNonNegative(sumPower) ? sumPower / flow
        : Double.NaN;
    if (!Double.isFinite(flow) || flow <= 0.0 || !gasOnly(export)) {
      faults.add("Positive net gas export evidence required");
    }
    if (!Double.isFinite(pressure) || pressure <= 0.0
        || Math.abs(pressure - targetPressureBara) / targetPressureBara > pressureTolerance) {
      faults.add("Export pressure target not met or unresolved");
    }
    stages = Collections.unmodifiableList(snapshots);
    violations = Collections.unmodifiableList(faults);
  }

  /**
   * Selects the feasible candidate with least shaft energy per unit net exported mass. Equal energy retains original
   * list order. Infeasible low-power candidates never win.
   *
   * @param candidates converged detached assessments
   * @return best feasible candidate, or null when no candidate is feasible
   * @throws IllegalArgumentException if candidates is null
   */
  public static CompressionTrainAssessment lowestSpecificEnergy(List<CompressionTrainAssessment> candidates) {
    if (candidates == null) {
      throw new IllegalArgumentException("Candidates must not be null");
    }
    CompressionTrainAssessment best = null;
    for (CompressionTrainAssessment candidate : candidates) {
      if (candidate != null && candidate.isFeasible()
          && (best == null || candidate.specificEnergyKJPerKg < best.specificEnergyKJPerKg)) {
        best = candidate;
      }
    }
    return best;
  }

  /**
   * Checks the caller's solved stream for single-gas-phase evidence without mutating it.
   *
   * @param stream solved stream
   * @return true for finite positive gas-state evidence
   */
  private static boolean gasOnly(StreamInterface stream) {
    try {
      if (stream == null) {
        return false;
      }
      SystemInterface fluid = stream.getThermoSystem();
      return fluid != null && fluid.getNumberOfPhases() == 1 && fluid.hasPhaseType("gas")
          && Double.isFinite(fluid.getTemperature()) && fluid.getTemperature() > 0
          && Double.isFinite(fluid.getPressure()) && fluid.getPressure() > 0;
    } catch (RuntimeException ex) {
      return false;
    }
  }

  /**
   * Checks a finite nonnegative value.
   *
   * @param value scalar
   * @return true if valid
   */
  private static boolean finiteNonNegative(double value) {
    return Double.isFinite(value) && value >= 0.0;
  }

  /**
   * Validates a positive policy value.
   *
   * @param value scalar
   * @param name field name
   * @throws IllegalArgumentException if invalid
   */
  private static void positive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  /**
   * Validates a nonnegative policy value.
   *
   * @param value scalar
   * @param name field name
   * @throws IllegalArgumentException if invalid
   */
  private static void nonNegative(double value, String name) {
    if (!finiteNonNegative(value)) {
      throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
  }

  /**
   * Gets detached stage evidence.
   *
   * @return immutable operating-point list
   */
  public List<CompressorOperatingPointResult> getStages() {
    return stages;
  }

  /**
   * Gets simultaneous failed candidate checks.
   *
   * @return immutable diagnostics
   */
  public List<String> getViolations() {
    return violations;
  }

  /**
   * Checks all configured candidate screening limits.
   *
   * @return true when there are no violations
   */
  public boolean isFeasible() {
    return violations.isEmpty();
  }

  /**
   * Gets summed shaft power, with each compressor body counted once.
   *
   * @return power in kW
   */
  public double getTotalShaftPowerKW() {
    return totalShaftPowerKW;
  }

  /**
   * Gets net exported flow; never the sum of serial gross stage flows.
   *
   * @return mass flow in kg/s
   */
  public double getNetExportKgPerSecond() {
    return netExportKgPerSecond;
  }

  /**
   * Gets shaft energy per net export mass (kW divided by kg/s).
   *
   * @return energy in kJ/kg, or NaN for unresolved flow/power
   */
  public double getSpecificEnergyKJPerKg() {
    return specificEnergyKJPerKg;
  }

  /**
   * Gets achieved export pressure.
   *
   * @return absolute pressure in bara
   */
  public double getExportPressureBara() {
    return exportPressureBara;
  }
}
