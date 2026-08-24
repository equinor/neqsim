package neqsim.process.equipment.pipeline;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Fixed-volume, compressible upstream boundary coupled by phase-resolved mass transfer.
 *
 * <p>
 * The volume owns gas, oil, and water inventories. A source adds phase mass and the connected pipe withdraws the inlet
 * mass reported by its conservative finite-volume flux. Pressure is then solved from fixed total volume using
 * {@code d rho / d p = 1 / c^2}. The model is deliberately composable: it contains no pipe closures and can be
 * connected to any boundary that reports the same three phase-mass withdrawals.
 * </p>
 */
public final class UpstreamCompressibleVolume implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final int PHASE_COUNT = 3;
  private static final int MAXIMUM_ITERATIONS = 30;
  private static final double RELATIVE_VOLUME_TOLERANCE = 1.0e-10;
  private static final double MINIMUM_PRESSURE_PA = 1.0e4;
  private static final double MINIMUM_DENSITY_KG_M3 = 1.0e-6;

  private final double volumeM3;
  private final double[] phaseMassKg;
  private final double[] phaseDensityKgM3;
  private final double[] phaseSoundSpeedMS;
  private final double[] sourceMassFlowKgS = new double[PHASE_COUNT];
  private double pressurePa;
  private double cumulativeSourceMassKg;
  private double cumulativeWithdrawalMassKg;
  private double maximumRelativeVolumeResidual;
  private int pressureIterations;

  /**
   * Create a volume from a pressure-consistent phase inventory.
   *
   * @param volumeM3 fixed internal volume in m3
   * @param pressurePa initial absolute pressure in Pa
   * @param phaseMassKg initial gas, oil, and water mass in kg
   * @param phaseDensityKgM3 initial gas, oil, and water density in kg/m3
   * @param phaseSoundSpeedMS gas, oil, and water sound speed in m/s
   */
  public UpstreamCompressibleVolume(double volumeM3, double pressurePa, double[] phaseMassKg, double[] phaseDensityKgM3,
      double[] phaseSoundSpeedMS) {
    requirePositiveFinite(volumeM3, "Volume");
    requirePositiveFinite(pressurePa, "Pressure");
    this.phaseMassKg = requireNonNegativePhaseValues(phaseMassKg, "Phase mass");
    this.phaseDensityKgM3 = requirePositivePhaseValues(phaseDensityKgM3, "Phase density");
    this.phaseSoundSpeedMS = requirePositivePhaseValues(phaseSoundSpeedMS, "Phase sound speed");
    this.volumeM3 = volumeM3;
    this.pressurePa = pressurePa;
    maximumRelativeVolumeResidual = Math.abs(calculateOccupiedVolumeM3() - volumeM3) / volumeM3;
    if (maximumRelativeVolumeResidual > RELATIVE_VOLUME_TOLERANCE) {
      throw new IllegalArgumentException(
          "Initial phase masses and densities must fill the fixed volume; relative residual="
              + maximumRelativeVolumeResidual);
    }
  }

  /** Set phase-resolved source rates entering the volume. */
  public void setSourceMassFlowRates(double gasKgS, double oilKgS, double waterKgS) {
    double[] rates = { gasKgS, oilKgS, waterKgS };
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      if (!Double.isFinite(rates[phase]) || rates[phase] < 0.0) {
        throw new IllegalArgumentException("Source phase mass-flow rates must be finite and non-negative");
      }
      sourceMassFlowKgS[phase] = rates[phase];
    }
  }

  /**
   * Advance the volume after an accepted pipe step.
   *
   * @param timeStepSeconds accepted step duration in s
   * @param withdrawnPhaseMassKg signed gas, oil, and water mass delivered to the pipe in kg; negative values represent
   * phase return from the pipe
   */
  public void advance(double timeStepSeconds, double[] withdrawnPhaseMassKg) {
    requirePositiveFinite(timeStepSeconds, "Time step");
    double[] withdrawal = requireFinitePhaseValues(withdrawnPhaseMassKg, "Withdrawn phase mass");
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      double sourceMass = sourceMassFlowKgS[phase] * timeStepSeconds;
      double updatedMass = phaseMassKg[phase] + sourceMass - withdrawal[phase];
      double tolerance = 1.0e-12 * Math.max(1.0, phaseMassKg[phase] + sourceMass);
      if (updatedMass < -tolerance) {
        throw new IllegalStateException("Upstream volume phase " + phase + " depleted by pipe withdrawal");
      }
      phaseMassKg[phase] = Math.max(updatedMass, 0.0);
      cumulativeSourceMassKg += sourceMass;
      cumulativeWithdrawalMassKg += withdrawal[phase];
    }
    solvePressureClosure();
  }

  private void solvePressureClosure() {
    pressureIterations = 0;
    for (int iteration = 1; iteration <= MAXIMUM_ITERATIONS; iteration++) {
      double occupiedVolume = calculateOccupiedVolumeM3();
      double residualM3 = occupiedVolume - volumeM3;
      maximumRelativeVolumeResidual = Math.abs(residualM3) / volumeM3;
      pressureIterations = iteration;
      if (maximumRelativeVolumeResidual <= RELATIVE_VOLUME_TOLERANCE) {
        return;
      }

      double pressureDerivativeMagnitude = 0.0;
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        if (phaseMassKg[phase] > 0.0) {
          double density = phaseDensityKgM3[phase];
          double soundSpeed = phaseSoundSpeedMS[phase];
          pressureDerivativeMagnitude += phaseMassKg[phase] / (density * density * soundSpeed * soundSpeed);
        }
      }
      if (!(pressureDerivativeMagnitude > 0.0) || !Double.isFinite(pressureDerivativeMagnitude)) {
        throw new IllegalStateException("Upstream volume has no finite compressibility");
      }

      double pressureCorrection = residualM3 / pressureDerivativeMagnitude;
      double lowerLimit = MINIMUM_PRESSURE_PA - pressurePa;
      double magnitudeLimit = 0.25 * Math.max(pressurePa, MINIMUM_PRESSURE_PA);
      pressureCorrection = Math.max(lowerLimit, Math.min(magnitudeLimit, pressureCorrection));
      pressureCorrection = Math.max(-magnitudeLimit, pressureCorrection);
      pressurePa += pressureCorrection;
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double densityCorrection = pressureCorrection / (phaseSoundSpeedMS[phase] * phaseSoundSpeedMS[phase]);
        phaseDensityKgM3[phase] = Math.max(MINIMUM_DENSITY_KG_M3, phaseDensityKgM3[phase] + densityCorrection);
      }
    }
    throw new IllegalStateException(
        "Upstream-volume pressure closure did not converge; relative volume residual=" + maximumRelativeVolumeResidual);
  }

  private double calculateOccupiedVolumeM3() {
    double occupied = 0.0;
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      occupied += phaseMassKg[phase] / phaseDensityKgM3[phase];
    }
    return occupied;
  }

  private static double[] requireNonNegativePhaseValues(double[] values, String label) {
    double[] copy = requireFinitePhaseValues(values, label);
    for (double value : copy) {
      if (value < 0.0) {
        throw new IllegalArgumentException(label + " values must be non-negative");
      }
    }
    return copy;
  }

  private static double[] requireFinitePhaseValues(double[] values, String label) {
    if (values == null || values.length != PHASE_COUNT) {
      throw new IllegalArgumentException(label + " must contain gas, oil, and water values");
    }
    double[] copy = Arrays.copyOf(values, values.length);
    for (double value : copy) {
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException(label + " values must be finite");
      }
    }
    return copy;
  }

  private static double[] requirePositivePhaseValues(double[] values, String label) {
    double[] copy = requireNonNegativePhaseValues(values, label);
    for (double value : copy) {
      if (value <= 0.0) {
        throw new IllegalArgumentException(label + " values must be positive");
      }
    }
    return copy;
  }

  private static void requirePositiveFinite(double value, String label) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(label + " must be positive and finite");
    }
  }

  public double getVolumeM3() {
    return volumeM3;
  }

  public double getPressurePa() {
    return pressurePa;
  }

  public double getPhaseMassKg(int phase) {
    return phaseMassKg[phase];
  }

  public double getSourceMassFlowRateKgS(int phase) {
    return sourceMassFlowKgS[phase];
  }

  public double getTotalMassKg() {
    return phaseMassKg[0] + phaseMassKg[1] + phaseMassKg[2];
  }

  public double getCumulativeSourceMassKg() {
    return cumulativeSourceMassKg;
  }

  public double getCumulativeWithdrawalMassKg() {
    return cumulativeWithdrawalMassKg;
  }

  public double getMaximumRelativeVolumeResidual() {
    return maximumRelativeVolumeResidual;
  }

  public int getPressureIterations() {
    return pressureIterations;
  }
}
