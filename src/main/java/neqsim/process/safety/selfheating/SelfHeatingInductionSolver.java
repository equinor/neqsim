package neqsim.process.safety.selfheating;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Transient one-dimensional solver for the induction time (time to ignition) of a self-heating body.
 *
 * <p>
 * {@link PorousMediaSelfHeatingAnalyzer} answers whether a body <i>will</i> ignite. This solver answers <i>when</i>, by
 * integrating the transient conduction equation with an Arrhenius volumetric source
 * </p>
 *
 * <p>
 * {@code rho * c * dT/dt = lambda * (d2T/dr2 + (j / r) * dT/dr) + P * exp(-E / (R * T))}
 * </p>
 *
 * <p>
 * where {@code j} is 0 for a slab, 1 for a cylinder and 2 for a sphere. The timing matters operationally: lagging fires
 * characteristically smoulder for hours or days before breaking out, so a leak that stopped long ago can still ignite,
 * and the absence of an immediate fire after a spill is not evidence of safety.
 * </p>
 *
 * <p>
 * The body starts at a uniform initial temperature and its outer surface is held at the boundary temperature
 * (Dirichlet). Symmetry is imposed at the centre. Integration uses explicit Euler with a time step limited by both the
 * conduction stability criterion and the local reaction time scale, so the step shrinks automatically as the reaction
 * accelerates.
 * </p>
 *
 * <p>
 * A run terminates when the peak temperature exceeds the boundary temperature by the ignition rise (default 100 K), or
 * when the profile becomes steady, or when the maximum simulated time is reached.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class SelfHeatingInductionSolver {
  private static final Logger logger = LogManager.getLogger(SelfHeatingInductionSolver.class);

  /** Universal gas constant [J/(mol K)]. */
  private static final double R_GAS = 8.314462618;

  /** Default number of spatial nodes. */
  private static final int DEFAULT_NODE_COUNT = 41;

  /** Default temperature rise above the boundary temperature that defines ignition [K]. */
  private static final double DEFAULT_IGNITION_RISE_K = 100.0;

  /** Default maximum simulated time [s], equal to 30 days. */
  private static final double DEFAULT_MAX_TIME_S = 30.0 * 24.0 * 3600.0;

  /** Safety factor on the explicit conduction stability limit. */
  private static final double CONDUCTION_SAFETY = 0.4;

  /** Maximum fraction of the Frank-Kamenetskii temperature scale permitted per time step. */
  private static final double REACTION_STEP_FRACTION = 0.02;

  /** Maximum number of time steps before the run is abandoned. */
  private static final int MAX_STEPS = 20000000;

  /** Maximum number of retained history samples. */
  private static final int MAX_HISTORY_POINTS = 500;

  private SelfHeatingGeometry geometry = SelfHeatingGeometry.SLAB;
  private double characteristicDimensionM = Double.NaN;
  private double effectiveConductivityWPerMK = Double.NaN;
  private double volumetricHeatCapacityJPerM3K = Double.NaN;
  private double activationEnergyJPerMol = Double.NaN;
  private double volumetricPreFactorWPerM3 = Double.NaN;
  private double boundaryTemperatureK = Double.NaN;
  private double initialTemperatureK = Double.NaN;
  private double ignitionRiseK = DEFAULT_IGNITION_RISE_K;
  private double maxTimeS = DEFAULT_MAX_TIME_S;
  private int nodeCount = DEFAULT_NODE_COUNT;

  /**
   * Create a transient self-heating solver with no inputs set.
   */
  public SelfHeatingInductionSolver() {
  }

  /**
   * Set the body shape. Only one-dimensional shapes are supported.
   *
   * @param geometry the body shape; must not be null and must satisfy {@link SelfHeatingGeometry#isOneDimensional()}
   * @return this solver for chaining
   * @throws IllegalArgumentException if the geometry is null or not one-dimensional
   */
  public SelfHeatingInductionSolver setGeometry(SelfHeatingGeometry geometry) {
    if (geometry == null) {
      throw new IllegalArgumentException("Geometry must not be null");
    }
    if (!geometry.isOneDimensional()) {
      throw new IllegalArgumentException(
          "Geometry " + geometry + " is not one-dimensional; use SLAB, INFINITE_CYLINDER or SPHERE");
    }
    this.geometry = geometry;
    return this;
  }

  /**
   * Set the characteristic half-dimension of the body.
   *
   * @param value dimension value; must be positive
   * @param unit length unit ("m", "cm", "mm" or "in")
   * @return this solver for chaining
   * @throws IllegalArgumentException if the value is not positive or the unit is unsupported
   */
  public SelfHeatingInductionSolver setCharacteristicDimension(double value, String unit) {
    this.characteristicDimensionM = PorousMediaSelfHeatingAnalyzer.toMetres(value, unit);
    return this;
  }

  /**
   * Set the effective thermal conductivity of the wetted porous medium.
   *
   * @param wPerMK effective thermal conductivity [W/(m K)]; must be positive
   * @return this solver for chaining
   * @throws IllegalArgumentException if the value is not positive
   */
  public SelfHeatingInductionSolver setEffectiveThermalConductivity(double wPerMK) {
    if (!(wPerMK > 0.0)) {
      throw new IllegalArgumentException("Effective thermal conductivity must be positive");
    }
    this.effectiveConductivityWPerMK = wPerMK;
    return this;
  }

  /**
   * Set the volumetric heat capacity of the body directly.
   *
   * @param jPerM3K volumetric heat capacity {@code rho * c} [J/(m3 K)]; must be positive
   * @return this solver for chaining
   * @throws IllegalArgumentException if the value is not positive
   */
  public SelfHeatingInductionSolver setVolumetricHeatCapacity(double jPerM3K) {
    if (!(jPerM3K > 0.0)) {
      throw new IllegalArgumentException("Volumetric heat capacity must be positive");
    }
    this.volumetricHeatCapacityJPerM3K = jPerM3K;
    return this;
  }

  /**
   * Set the volumetric heat capacity from bulk density and specific heat.
   *
   * @param bulkDensityKgPerM3 bulk density of the wetted porous body [kg/m3]; must be positive
   * @param specificHeatJPerKgK specific heat of the wetted porous body [J/(kg K)]; must be positive
   * @return this solver for chaining
   * @throws IllegalArgumentException if either argument is not positive
   */
  public SelfHeatingInductionSolver setBulkProperties(double bulkDensityKgPerM3, double specificHeatJPerKgK) {
    if (!(bulkDensityKgPerM3 > 0.0)) {
      throw new IllegalArgumentException("Bulk density must be positive");
    }
    if (!(specificHeatJPerKgK > 0.0)) {
      throw new IllegalArgumentException("Specific heat must be positive");
    }
    this.volumetricHeatCapacityJPerM3K = bulkDensityKgPerM3 * specificHeatJPerKgK;
    return this;
  }

  /**
   * Set the apparent activation energy of the self-heating reaction.
   *
   * @param value activation-energy value; must be positive
   * @param unit energy unit ("J/mol" or "kJ/mol")
   * @return this solver for chaining
   * @throws IllegalArgumentException if the value is not positive or the unit is unsupported
   */
  public SelfHeatingInductionSolver setActivationEnergy(double value, String unit) {
    double joulesPerMol;
    if ("kJ/mol".equalsIgnoreCase(unit)) {
      joulesPerMol = value * 1000.0;
    } else if ("J/mol".equalsIgnoreCase(unit)) {
      joulesPerMol = value;
    } else {
      throw new IllegalArgumentException("Unsupported activation-energy unit: " + unit + " (use J/mol or kJ/mol)");
    }
    if (!(joulesPerMol > 0.0)) {
      throw new IllegalArgumentException("Activation energy must be positive");
    }
    this.activationEnergyJPerMol = joulesPerMol;
    return this;
  }

  /**
   * Set the volumetric heat-release pre-exponential factor {@code P = A * Q * rho}.
   *
   * @param wPerM3 volumetric heat-release pre-exponential factor [W/m3]; must be positive
   * @return this solver for chaining
   * @throws IllegalArgumentException if the value is not positive
   */
  public SelfHeatingInductionSolver setVolumetricHeatReleasePreFactor(double wPerM3) {
    if (!(wPerM3 > 0.0)) {
      throw new IllegalArgumentException("Volumetric heat-release pre-factor must be positive");
    }
    this.volumetricPreFactorWPerM3 = wPerM3;
    return this;
  }

  /**
   * Set the temperature held at the outer surface of the body.
   *
   * @param value temperature value
   * @param unit temperature unit ("K" or "C")
   * @return this solver for chaining
   */
  public SelfHeatingInductionSolver setBoundaryTemperature(double value, String unit) {
    this.boundaryTemperatureK = new neqsim.util.unit.TemperatureUnit(value, unit).getValue("K");
    return this;
  }

  /**
   * Set the uniform initial temperature of the body. Defaults to the boundary temperature.
   *
   * @param value temperature value
   * @param unit temperature unit ("K" or "C")
   * @return this solver for chaining
   */
  public SelfHeatingInductionSolver setInitialTemperature(double value, String unit) {
    this.initialTemperatureK = new neqsim.util.unit.TemperatureUnit(value, unit).getValue("K");
    return this;
  }

  /**
   * Set the temperature rise above the boundary temperature that defines ignition.
   *
   * @param riseK ignition temperature rise [K]; must be positive
   * @return this solver for chaining
   * @throws IllegalArgumentException if the value is not positive
   */
  public SelfHeatingInductionSolver setIgnitionRise(double riseK) {
    if (!(riseK > 0.0)) {
      throw new IllegalArgumentException("Ignition temperature rise must be positive");
    }
    this.ignitionRiseK = riseK;
    return this;
  }

  /**
   * Set the maximum simulated time.
   *
   * @param value maximum time value; must be positive
   * @param unit time unit ("s", "min", "hr" or "day")
   * @return this solver for chaining
   * @throws IllegalArgumentException if the value is not positive or the unit is unsupported
   */
  public SelfHeatingInductionSolver setMaxTime(double value, String unit) {
    this.maxTimeS = toSeconds(value, unit);
    return this;
  }

  /**
   * Set the number of spatial nodes used in the discretisation.
   *
   * @param nodeCount number of nodes; must be at least 5
   * @return this solver for chaining
   * @throws IllegalArgumentException if the node count is below 5
   */
  public SelfHeatingInductionSolver setNodeCount(int nodeCount) {
    if (nodeCount < 5) {
      throw new IllegalArgumentException("Node count must be at least 5");
    }
    this.nodeCount = nodeCount;
    return this;
  }

  /**
   * Integrate the transient self-heating problem.
   *
   * @return an immutable result containing the induction time and the temperature history
   * @throws IllegalStateException if required inputs are missing or physically invalid
   */
  public SelfHeatingInductionResult solve() {
    validateInputs();
    List<String> warnings = new ArrayList<String>();

    int j = geometry.getShapeFactor();
    double radius = characteristicDimensionM;
    double dx = radius / (nodeCount - 1);
    double alpha = effectiveConductivityWPerMK / volumetricHeatCapacityJPerM3K;
    double startTemperature = Double.isNaN(initialTemperatureK) ? boundaryTemperatureK : initialTemperatureK;

    double[] temperature = new double[nodeCount];
    for (int k = 0; k < nodeCount; k++) {
      temperature[k] = startTemperature;
    }
    temperature[nodeCount - 1] = boundaryTemperatureK;

    double dtConduction = CONDUCTION_SAFETY * dx * dx / (2.0 * (j + 1) * alpha);
    double ignitionTemperature = boundaryTemperatureK + ignitionRiseK;

    List<SelfHeatingTimePoint> history = new ArrayList<SelfHeatingTimePoint>();
    int historyStride = 1;
    int stepsSinceSample = 0;
    history.add(new SelfHeatingTimePoint(0.0, temperature[0], maxOf(temperature)));

    double time = 0.0;
    boolean ignited = false;
    boolean steady = false;
    boolean stepLimitHit = false;
    double inductionTime = Double.NaN;
    double[] next = new double[nodeCount];

    int step = 0;
    for (; step < MAX_STEPS; step++) {
      double peak = maxOf(temperature);
      if (peak >= ignitionTemperature) {
        ignited = true;
        inductionTime = time;
        break;
      }
      if (time >= maxTimeS) {
        break;
      }

      double peakSource = volumetricPreFactorWPerM3 * Math.exp(-activationEnergyJPerMol / (R_GAS * peak));
      double dt = dtConduction;
      if (peakSource > 0.0) {
        double fkScale = R_GAS * peak * peak / activationEnergyJPerMol;
        double dtReaction = REACTION_STEP_FRACTION * fkScale * volumetricHeatCapacityJPerM3K / peakSource;
        dt = Math.min(dt, dtReaction);
      }
      if (time + dt > maxTimeS) {
        dt = maxTimeS - time;
      }

      double maxRate = 0.0;
      for (int k = 0; k < nodeCount - 1; k++) {
        double laplacian;
        if (k == 0) {
          laplacian = 2.0 * (j + 1) * (temperature[1] - temperature[0]) / (dx * dx);
        } else {
          double second = (temperature[k - 1] - 2.0 * temperature[k] + temperature[k + 1]) / (dx * dx);
          double first = (temperature[k + 1] - temperature[k - 1]) / (2.0 * dx);
          laplacian = second + j * first / (k * dx);
        }
        double source = volumetricPreFactorWPerM3 * Math.exp(-activationEnergyJPerMol / (R_GAS * temperature[k]));
        double rate = (effectiveConductivityWPerMK * laplacian + source) / volumetricHeatCapacityJPerM3K;
        next[k] = temperature[k] + dt * rate;
        maxRate = Math.max(maxRate, Math.abs(rate));
      }
      next[nodeCount - 1] = boundaryTemperatureK;

      System.arraycopy(next, 0, temperature, 0, nodeCount);
      time += dt;

      stepsSinceSample++;
      if (stepsSinceSample >= historyStride) {
        stepsSinceSample = 0;
        history.add(new SelfHeatingTimePoint(time, temperature[0], maxOf(temperature)));
        if (history.size() >= MAX_HISTORY_POINTS) {
          decimate(history);
          historyStride *= 2;
        }
      }

      // Extrapolating the current rate over the remaining time would change the peak by less than 0.1 K.
      if (maxRate * (maxTimeS - time) < 0.1 && time > 0.0) {
        steady = true;
        break;
      }
    }
    if (step >= MAX_STEPS) {
      stepLimitHit = true;
      warnings.add("Step limit of " + MAX_STEPS + " reached before ignition or steady state; "
          + "reduce the node count or the maximum simulated time");
    }

    double finalPeak = maxOf(temperature);
    history.add(new SelfHeatingTimePoint(time, temperature[0], finalPeak));

    if (!ignited && steady) {
      warnings.add("A steady temperature profile was reached, so the body is subcritical at this boundary temperature");
    }
    if (!ignited && !steady && !stepLimitHit) {
      warnings.add("Maximum simulated time reached without ignition or a clear steady state; "
          + "extend the simulated time before concluding the case is safe");
    }

    logger.info("Self-heating induction run: geometry={}, ignited={}, inductionTime={} s, peak={} K", geometry, ignited,
        inductionTime, finalPeak);

    return new SelfHeatingInductionResult(geometry, characteristicDimensionM, boundaryTemperatureK, startTemperature,
        ignitionRiseK, ignited, inductionTime, finalPeak, finalPeak - boundaryTemperatureK, steady, time, history,
        warnings);
  }

  /**
   * Halve the length of a history list by discarding every second entry, retaining the first and last.
   *
   * @param history the history list to decimate in place; must not be null
   */
  private static void decimate(List<SelfHeatingTimePoint> history) {
    List<SelfHeatingTimePoint> kept = new ArrayList<SelfHeatingTimePoint>(history.size() / 2 + 1);
    for (int i = 0; i < history.size(); i += 2) {
      kept.add(history.get(i));
    }
    history.clear();
    history.addAll(kept);
  }

  /**
   * Find the maximum value in an array.
   *
   * @param values the array to scan; must not be null or empty
   * @return the maximum value
   */
  private static double maxOf(double[] values) {
    double max = values[0];
    for (int i = 1; i < values.length; i++) {
      if (values[i] > max) {
        max = values[i];
      }
    }
    return max;
  }

  /**
   * Convert a time to seconds.
   *
   * @param value time value; must be positive
   * @param unit time unit ("s", "min", "hr" or "day")
   * @return the time in seconds
   * @throws IllegalArgumentException if the value is not positive or the unit is unsupported
   */
  static double toSeconds(double value, String unit) {
    if (!(value > 0.0)) {
      throw new IllegalArgumentException("Time must be positive");
    }
    if ("s".equalsIgnoreCase(unit) || "sec".equalsIgnoreCase(unit)) {
      return value;
    } else if ("min".equalsIgnoreCase(unit)) {
      return value * 60.0;
    } else if ("hr".equalsIgnoreCase(unit) || "h".equalsIgnoreCase(unit)) {
      return value * 3600.0;
    } else if ("day".equalsIgnoreCase(unit)) {
      return value * 86400.0;
    }
    throw new IllegalArgumentException("Unsupported time unit: " + unit + " (use s, min, hr or day)");
  }

  /**
   * Validate that all mandatory inputs are present and physically meaningful.
   *
   * @throws IllegalStateException if any mandatory input is missing or invalid
   */
  private void validateInputs() {
    if (Double.isNaN(characteristicDimensionM) || characteristicDimensionM <= 0.0) {
      throw new IllegalStateException("Characteristic dimension must be set to a positive value");
    }
    if (Double.isNaN(effectiveConductivityWPerMK) || effectiveConductivityWPerMK <= 0.0) {
      throw new IllegalStateException("Effective thermal conductivity must be set to a positive value");
    }
    if (Double.isNaN(volumetricHeatCapacityJPerM3K) || volumetricHeatCapacityJPerM3K <= 0.0) {
      throw new IllegalStateException("Volumetric heat capacity must be set, either directly or via setBulkProperties");
    }
    if (Double.isNaN(activationEnergyJPerMol) || activationEnergyJPerMol <= 0.0) {
      throw new IllegalStateException("Activation energy must be set to a positive value");
    }
    if (Double.isNaN(volumetricPreFactorWPerM3) || volumetricPreFactorWPerM3 <= 0.0) {
      throw new IllegalStateException("Volumetric heat-release pre-factor must be set to a positive value");
    }
    if (Double.isNaN(boundaryTemperatureK) || boundaryTemperatureK <= 0.0) {
      throw new IllegalStateException("Boundary temperature must be set to a positive value");
    }
  }
}
