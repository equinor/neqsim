package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import java.io.Serializable;

/**
 * Coupled pressure-momentum correction for the transient two-fluid finite-volume state.
 *
 * <p>
 * The explicit transport step supplies a provisional conservative state. This class solves a compressible pressure
 * equation formed from the fixed-cell-volume residual and the pressure correction to the total volumetric flux. The
 * same face pressure correction is then applied to every phase mass flux and momentum, so phase masses remain globally
 * conservative while pressure and velocity are advanced in the same accepted step.
 *
 * <p>
 * The correction is intentionally independent of closure correlations. Phase compressibility is supplied through the
 * local phase sound speeds, and the pressure equation uses the mobility {@code A * sum(alpha_k / rho_k)}. A fixed
 * outlet pressure is represented by a Dirichlet pressure correction; the inlet and non-fixed outlet use zero correction
 * gradient.
 */
public final class CoupledPressureMomentumSolver implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final int GAS_MASS = 0;
  private static final int OIL_MASS = 1;
  private static final int WATER_MASS = 2;
  private static final int GAS_MOMENTUM = 3;
  private static final int OIL_MOMENTUM = 4;
  private static final int WATER_MOMENTUM = 5;
  private static final int PHASE_COUNT = 3;
  private static final double MIN_DENSITY = 1.0e-6;
  private static final double MIN_SOUND_SPEED = 1.0;
  private static final double MIN_DIAGONAL = 1.0e-24;

  private int maximumIterations = 12;
  private double relativeVolumeTolerance = 1.0e-7;
  private double pressureRelaxation = 0.7;
  private double maximumRelativePressureCorrection = 0.25;

  /**
   * Immutable result of one coupled pressure-momentum correction.
   */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double[][] state;
    private final double[] pressure;
    private final double[] gasDensity;
    private final double[] oilDensity;
    private final double[] waterDensity;
    private final double[] outletBoundaryMassCorrectionKg;
    private final int iterations;
    private final double maximumRelativeVolumeResidual;
    private final boolean converged;
    private final boolean pressureCorrectionLimited;

    private Result(double[][] state, double[] pressure, double[] gasDensity, double[] oilDensity, double[] waterDensity,
        double[] outletBoundaryMassCorrectionKg, int iterations, double maximumRelativeVolumeResidual,
        boolean converged, boolean pressureCorrectionLimited) {
      this.state = state;
      this.pressure = pressure;
      this.gasDensity = gasDensity;
      this.oilDensity = oilDensity;
      this.waterDensity = waterDensity;
      this.outletBoundaryMassCorrectionKg = outletBoundaryMassCorrectionKg;
      this.iterations = iterations;
      this.maximumRelativeVolumeResidual = maximumRelativeVolumeResidual;
      this.converged = converged;
      this.pressureCorrectionLimited = pressureCorrectionLimited;
    }

    /** @return corrected conservative state */
    public double[][] getState() {
      return copy(state);
    }

    /** @return corrected cell pressure in Pa */
    public double[] getPressure() {
      return pressure.clone();
    }

    /** @return corrected gas density in kg/m3 */
    public double[] getGasDensity() {
      return gasDensity.clone();
    }

    /** @return corrected oil density in kg/m3 */
    public double[] getOilDensity() {
      return oilDensity.clone();
    }

    /** @return corrected water density in kg/m3 */
    public double[] getWaterDensity() {
      return waterDensity.clone();
    }

    /** @return signed gas, oil, and water mass added to the reported outlet transfer in kg */
    public double[] getOutletBoundaryMassCorrectionKg() {
      return outletBoundaryMassCorrectionKg.clone();
    }

    /** @return nonlinear correction iterations used */
    public int getIterations() {
      return iterations;
    }

    /** @return largest absolute cell-volume residual divided by cell area */
    public double getMaximumRelativeVolumeResidual() {
      return maximumRelativeVolumeResidual;
    }

    /** @return true when the volume residual met the configured tolerance */
    public boolean isConverged() {
      return converged;
    }

    /** @return true when at least one nonlinear pressure correction was limited */
    public boolean isPressureCorrectionLimited() {
      return pressureCorrectionLimited;
    }
  }

  /**
   * Correct a provisional conservative state with a coupled pressure and phase-momentum solve.
   *
   * @param provisionalState conservative variables in kg/m and kg/s
   * @param timeStep accepted substep in s
   * @param pressure cell pressure in Pa
   * @param areas cell cross-sectional area in m2
   * @param lengths cell axial length in m
   * @param gasDensity gas density in kg/m3
   * @param oilDensity oil density in kg/m3
   * @param waterDensity water density in kg/m3
   * @param gasSoundSpeed gas sound speed in m/s
   * @param oilSoundSpeed oil sound speed in m/s
   * @param waterSoundSpeed water sound speed in m/s
   * @param outletPressure fixed outlet pressure in Pa
   * @param outletPressureFixed true for a Dirichlet outlet pressure
   * @return corrected state, pressure, density, and convergence diagnostics
   */
  public Result correct(double[][] provisionalState, double timeStep, double[] pressure, double[] areas,
      double[] lengths, double[] gasDensity, double[] oilDensity, double[] waterDensity, double[] gasSoundSpeed,
      double[] oilSoundSpeed, double[] waterSoundSpeed, double outletPressure, boolean outletPressureFixed) {
    validateInputs(provisionalState, timeStep, pressure, areas, lengths, gasDensity, oilDensity, waterDensity,
        gasSoundSpeed, oilSoundSpeed, waterSoundSpeed);

    int cellCount = provisionalState.length;
    double[][] correctedState = copy(provisionalState);
    double[] correctedPressure = pressure.clone();
    double[][] densities = { gasDensity.clone(), oilDensity.clone(), waterDensity.clone() };
    double[][] soundSpeeds = { gasSoundSpeed.clone(), oilSoundSpeed.clone(), waterSoundSpeed.clone() };

    int iterations = 0;
    double[] outletBoundaryMassCorrectionKg = new double[PHASE_COUNT];
    boolean converged = false;
    boolean correctionLimited = false;
    double maximumResidual = calculateMaximumRelativeVolumeResidual(correctedState, areas, densities);

    while (iterations < maximumIterations && maximumResidual > relativeVolumeTolerance) {
      iterations++;
      double[][] phaseAreas = calculatePhaseAreas(correctedState, densities);
      double[] lower = new double[cellCount];
      double[] diagonal = new double[cellCount];
      double[] upper = new double[cellCount];
      double[] rightHandSide = new double[cellCount];

      for (int cell = 0; cell < cellCount; cell++) {
        double compressibleArea = 0.0;
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          double density = Math.max(densities[phase][cell], MIN_DENSITY);
          double soundSpeed = Math.max(soundSpeeds[phase][cell], MIN_SOUND_SPEED);
          compressibleArea += phaseAreas[phase][cell] / (density * soundSpeed * soundSpeed);
        }

        double leftCoefficient = 0.0;
        if (cell > 0) {
          double faceDistance = 0.5 * (lengths[cell - 1] + lengths[cell]);
          double mobility = faceMobility(cell - 1, cell, phaseAreas, areas, densities);
          leftCoefficient = timeStep * timeStep * mobility / (lengths[cell] * faceDistance);
        }

        double rightCoefficient = 0.0;
        if (cell < cellCount - 1) {
          double faceDistance = 0.5 * (lengths[cell] + lengths[cell + 1]);
          double mobility = faceMobility(cell, cell + 1, phaseAreas, areas, densities);
          rightCoefficient = timeStep * timeStep * mobility / (lengths[cell] * faceDistance);
        }

        lower[cell] = -leftCoefficient;
        diagonal[cell] = Math.max(compressibleArea + leftCoefficient + rightCoefficient, MIN_DIAGONAL);
        upper[cell] = -rightCoefficient;
        rightHandSide[cell] = phaseAreas[GAS_MASS][cell] + phaseAreas[OIL_MASS][cell] + phaseAreas[WATER_MASS][cell]
            - areas[cell];
      }

      if (outletPressureFixed) {
        int outlet = cellCount - 1;
        lower[outlet] = 0.0;
        diagonal[outlet] = 1.0;
        upper[outlet] = 0.0;
        rightHandSide[outlet] = outletPressure - correctedPressure[outlet];
      }

      double[] pressureCorrection = solveTridiagonal(lower, diagonal, upper, rightHandSide);
      for (int cell = 0; cell < cellCount; cell++) {
        pressureCorrection[cell] *= pressureRelaxation;
        double limit = Math.max(1.0e4, maximumRelativePressureCorrection * correctedPressure[cell]);
        if (Math.abs(pressureCorrection[cell]) > limit) {
          pressureCorrection[cell] = Math.copySign(limit, pressureCorrection[cell]);
          correctionLimited = true;
        }
      }

      double[] iterationOutletCorrection = applyConservativeMassFluxCorrection(correctedState, timeStep,
          pressureCorrection, phaseAreas, areas, lengths, densities, outletPressureFixed);
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        outletBoundaryMassCorrectionKg[phase] += iterationOutletCorrection[phase];
      }
      applyMomentumCorrection(correctedState, timeStep, pressureCorrection, phaseAreas, lengths);

      for (int cell = 0; cell < cellCount; cell++) {
        correctedPressure[cell] = Math.max(1.0e5, correctedPressure[cell] + pressureCorrection[cell]);
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          double soundSpeed = Math.max(soundSpeeds[phase][cell], MIN_SOUND_SPEED);
          densities[phase][cell] = Math.max(MIN_DENSITY,
              densities[phase][cell] + pressureCorrection[cell] / (soundSpeed * soundSpeed));
        }
      }

      maximumResidual = calculateMaximumRelativeVolumeResidual(correctedState, areas, densities);
    }

    converged = maximumResidual <= relativeVolumeTolerance;
    return new Result(correctedState, correctedPressure, densities[GAS_MASS], densities[OIL_MASS],
        densities[WATER_MASS], outletBoundaryMassCorrectionKg, iterations, maximumResidual, converged,
        correctionLimited);
  }

  private static double[][] calculatePhaseAreas(double[][] state, double[][] densities) {
    int cellCount = state.length;
    double[][] phaseAreas = new double[PHASE_COUNT][cellCount];
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      for (int cell = 0; cell < cellCount; cell++) {
        phaseAreas[phase][cell] = Math.max(state[cell][phase], 0.0) / Math.max(densities[phase][cell], MIN_DENSITY);
      }
    }
    return phaseAreas;
  }

  private static double faceMobility(int leftCell, int rightCell, double[][] phaseAreas, double[] cellAreas,
      double[][] densities) {
    double faceArea = 0.5 * (cellAreas[leftCell] + cellAreas[rightCell]);
    double mobility = 0.0;
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      double leftAlpha = Math.max(0.0, Math.min(1.0, phaseAreas[phase][leftCell] / cellAreas[leftCell]));
      double rightAlpha = Math.max(0.0, Math.min(1.0, phaseAreas[phase][rightCell] / cellAreas[rightCell]));
      double alpha = 0.5 * (leftAlpha + rightAlpha);
      double density = 0.5 * (densities[phase][leftCell] + densities[phase][rightCell]);
      mobility += alpha / Math.max(density, MIN_DENSITY);
    }
    return faceArea * mobility;
  }

  private static double[] applyConservativeMassFluxCorrection(double[][] state, double timeStep,
      double[] pressureCorrection, double[][] phaseAreas, double[] areas, double[] lengths, double[][] densities,
      boolean outletPressureFixed) {
    int cellCount = state.length;
    double[][] faceMassFlowCorrection = new double[PHASE_COUNT][cellCount + 1];

    for (int face = 1; face < cellCount; face++) {
      int leftCell = face - 1;
      int rightCell = face;
      double faceDistance = 0.5 * (lengths[leftCell] + lengths[rightCell]);
      double pressureGradient = (pressureCorrection[rightCell] - pressureCorrection[leftCell]) / faceDistance;
      double faceArea = 0.5 * (areas[leftCell] + areas[rightCell]);

      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double leftAlpha = Math.max(0.0, Math.min(1.0, phaseAreas[phase][leftCell] / areas[leftCell]));
        double rightAlpha = Math.max(0.0, Math.min(1.0, phaseAreas[phase][rightCell] / areas[rightCell]));
        double faceAlpha = 0.5 * (leftAlpha + rightAlpha);
        faceMassFlowCorrection[phase][face] = -timeStep * faceAlpha * faceArea * pressureGradient;
      }
    }

    for (int cell = 0; cell < cellCount; cell++) {
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double divergence = (faceMassFlowCorrection[phase][cell + 1] - faceMassFlowCorrection[phase][cell])
            / lengths[cell];
        state[cell][phase] -= timeStep * divergence;
      }
    }

    double[] outletBoundaryMassCorrectionKg = new double[PHASE_COUNT];
    if (outletPressureFixed) {
      int outlet = cellCount - 1;
      double occupiedArea = 0.0;
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        occupiedArea += Math.max(state[outlet][phase], 0.0)
            / Math.max(densities[phase][outlet], MIN_DENSITY);
      }
      if (!(occupiedArea > 0.0) || !Double.isFinite(occupiedArea)) {
        throw new IllegalStateException("Fixed-pressure outlet has no finite phase volume");
      }
      double areaResidual = occupiedArea - areas[outlet];
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double density = Math.max(densities[phase][outlet], MIN_DENSITY);
        double phaseArea = Math.max(state[outlet][phase], 0.0) / density;
        double massPerLengthCorrection = -areaResidual * phaseArea / occupiedArea * density;
        state[outlet][phase] += massPerLengthCorrection;
        outletBoundaryMassCorrectionKg[phase] -= massPerLengthCorrection * lengths[outlet];
      }
    }
    return outletBoundaryMassCorrectionKg;
  }

  private static void applyMomentumCorrection(double[][] state, double timeStep, double[] pressureCorrection,
      double[][] phaseAreas, double[] lengths) {
    int cellCount = state.length;
    for (int cell = 0; cell < cellCount; cell++) {
      double pressureGradient;
      if (cell == 0) {
        pressureGradient = (pressureCorrection[1] - pressureCorrection[0]) / (0.5 * (lengths[0] + lengths[1]));
      } else if (cell == cellCount - 1) {
        pressureGradient = (pressureCorrection[cell] - pressureCorrection[cell - 1])
            / (0.5 * (lengths[cell - 1] + lengths[cell]));
      } else {
        double distance = 0.5 * lengths[cell - 1] + lengths[cell] + 0.5 * lengths[cell + 1];
        pressureGradient = (pressureCorrection[cell + 1] - pressureCorrection[cell - 1]) / distance;
      }

      state[cell][GAS_MOMENTUM] -= timeStep * phaseAreas[GAS_MASS][cell] * pressureGradient;
      state[cell][OIL_MOMENTUM] -= timeStep * phaseAreas[OIL_MASS][cell] * pressureGradient;
      state[cell][WATER_MOMENTUM] -= timeStep * phaseAreas[WATER_MASS][cell] * pressureGradient;
    }
  }

  private static double calculateMaximumRelativeVolumeResidual(double[][] state, double[] areas, double[][] densities) {
    double maximumResidual = 0.0;
    for (int cell = 0; cell < state.length; cell++) {
      double occupiedArea = 0.0;
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        occupiedArea += Math.max(state[cell][phase], 0.0) / Math.max(densities[phase][cell], MIN_DENSITY);
      }
      maximumResidual = Math.max(maximumResidual, Math.abs(occupiedArea - areas[cell]) / areas[cell]);
    }
    return maximumResidual;
  }

  private static double[] solveTridiagonal(double[] lower, double[] diagonal, double[] upper, double[] rightHandSide) {
    int size = rightHandSide.length;
    double[] modifiedUpper = new double[size];
    double[] modifiedRightHandSide = new double[size];
    double[] solution = new double[size];

    double firstPivot = Math.abs(diagonal[0]) > MIN_DIAGONAL ? diagonal[0] : Math.copySign(MIN_DIAGONAL, diagonal[0]);
    modifiedUpper[0] = upper[0] / firstPivot;
    modifiedRightHandSide[0] = rightHandSide[0] / firstPivot;

    for (int row = 1; row < size; row++) {
      double pivot = diagonal[row] - lower[row] * modifiedUpper[row - 1];
      if (Math.abs(pivot) <= MIN_DIAGONAL) {
        pivot = Math.copySign(MIN_DIAGONAL, pivot);
      }
      modifiedUpper[row] = row < size - 1 ? upper[row] / pivot : 0.0;
      modifiedRightHandSide[row] = (rightHandSide[row] - lower[row] * modifiedRightHandSide[row - 1]) / pivot;
    }

    solution[size - 1] = modifiedRightHandSide[size - 1];
    for (int row = size - 2; row >= 0; row--) {
      solution[row] = modifiedRightHandSide[row] - modifiedUpper[row] * solution[row + 1];
    }
    return solution;
  }

  private static void validateInputs(double[][] state, double timeStep, double[] pressure, double[] areas,
      double[] lengths, double[] gasDensity, double[] oilDensity, double[] waterDensity, double[] gasSoundSpeed,
      double[] oilSoundSpeed, double[] waterSoundSpeed) {
    if (state == null || state.length < 2 || state[0].length < 6) {
      throw new IllegalArgumentException("At least two cells and six conservative variables are required");
    }
    if (!Double.isFinite(timeStep) || timeStep <= 0.0) {
      throw new IllegalArgumentException("timeStep must be positive and finite");
    }
    int cellCount = state.length;
    double[][] arrays = { pressure, areas, lengths, gasDensity, oilDensity, waterDensity, gasSoundSpeed, oilSoundSpeed,
        waterSoundSpeed };
    for (double[] array : arrays) {
      if (array == null || array.length != cellCount) {
        throw new IllegalArgumentException("Every cell-property array must match the state length");
      }
    }
    for (int cell = 0; cell < cellCount; cell++) {
      if (state[cell] == null || state[cell].length != state[0].length) {
        throw new IllegalArgumentException("The conservative state must be rectangular");
      }
      if (!(areas[cell] > 0.0) || !(lengths[cell] > 0.0)) {
        throw new IllegalArgumentException("Cell area and length must be positive");
      }
    }
  }

  private static double[][] copy(double[][] source) {
    double[][] result = new double[source.length][];
    for (int row = 0; row < source.length; row++) {
      result[row] = source[row].clone();
    }
    return result;
  }

  /** @param maximumIterations maximum nonlinear correction iterations */
  public void setMaximumIterations(int maximumIterations) {
    if (maximumIterations < 1) {
      throw new IllegalArgumentException("maximumIterations must be at least one");
    }
    this.maximumIterations = maximumIterations;
  }

  /** @param tolerance maximum accepted relative cell-volume residual */
  public void setRelativeVolumeTolerance(double tolerance) {
    if (!(tolerance > 0.0) || !Double.isFinite(tolerance)) {
      throw new IllegalArgumentException("tolerance must be positive and finite");
    }
    this.relativeVolumeTolerance = tolerance;
  }

  /** @param relaxation pressure-correction relaxation in the interval (0, 1] */
  public void setPressureRelaxation(double relaxation) {
    if (!(relaxation > 0.0) || relaxation > 1.0 || !Double.isFinite(relaxation)) {
      throw new IllegalArgumentException("relaxation must be in the interval (0, 1]");
    }
    this.pressureRelaxation = relaxation;
  }
}
