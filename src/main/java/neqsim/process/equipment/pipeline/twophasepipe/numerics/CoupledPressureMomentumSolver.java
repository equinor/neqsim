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
  /** Gas pressure-density response used during a coupled correction. */
  public enum GasDensityModel {
    /** Local constant-sound-speed affine acoustic response. */
    AFFINE,
    /** Finite-amplitude barotropic power law matching the supplied local acoustic derivative. */
    POLYTROPIC
  }

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
  private static final double DEFAULT_MIN_PRESSURE = 1.0;

  /**
   * Default nonlinear budget. The former value of 12 stopped before the public Tengesdal coupled case reached the
   * default volume tolerance.
   */
  private int maximumIterations = 24;
  private double relativeVolumeTolerance = 1.0e-7;
  private double pressureRelaxation = 0.7;
  private double maximumRelativePressureCorrection = 0.25;
  private double minimumPressure = DEFAULT_MIN_PRESSURE;
  private GasDensityModel gasDensityModel = GasDensityModel.AFFINE;
  private boolean checkerboardCorrectionEnabled;

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
    private final double[] gasSoundSpeed;
    private final double[] outletBoundaryMassCorrectionKg;
    private final double[][] phaseMassCorrectionsKg;
    private final int iterations;
    private final double maximumRelativeVolumeResidual;
    private final boolean converged;
    private final boolean pressureCorrectionLimited;
    private final double minimumMassFluxCorrectionScale;

    private Result(double[][] state, double[] pressure, double[] gasDensity, double[] oilDensity, double[] waterDensity,
        double[] gasSoundSpeed, double[] outletBoundaryMassCorrectionKg, double[][] phaseMassCorrectionsKg,
        int iterations, double maximumRelativeVolumeResidual, boolean converged, boolean pressureCorrectionLimited,
        double minimumMassFluxCorrectionScale) {
      this.state = state;
      this.pressure = pressure;
      this.gasDensity = gasDensity;
      this.oilDensity = oilDensity;
      this.waterDensity = waterDensity;
      this.gasSoundSpeed = gasSoundSpeed;
      this.outletBoundaryMassCorrectionKg = outletBoundaryMassCorrectionKg;
      this.phaseMassCorrectionsKg = phaseMassCorrectionsKg;
      this.iterations = iterations;
      this.maximumRelativeVolumeResidual = maximumRelativeVolumeResidual;
      this.converged = converged;
      this.pressureCorrectionLimited = pressureCorrectionLimited;
      this.minimumMassFluxCorrectionScale = minimumMassFluxCorrectionScale;
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

    /** @return gas sound speeds consistent with the corrected pressure-density response, in m/s */
    public double[] getGasSoundSpeed() {
      return gasSoundSpeed == null ? null : gasSoundSpeed.clone();
    }

    /** @return signed gas, oil, and water mass added to the reported outlet transfer in kg */
    public double[] getOutletBoundaryMassCorrectionKg() {
      return outletBoundaryMassCorrectionKg.clone();
    }

    /**
     * Get the exact cumulative phase transfers used by the correction, including the outlet projection.
     *
     * @return signed kg indexed by face, then gas/oil/water; positive follows increasing cell index
     */
    public double[][] getPhaseMassCorrectionsKg() {
      return phaseMassCorrectionsKg == null ? new double[0][0] : copy(phaseMassCorrectionsKg);
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

    /** @return smallest phase-mass positivity scale used by an applied nonlinear correction */
    public double getMinimumMassFluxCorrectionScale() {
      return minimumMassFluxCorrectionScale;
    }
  }

  private static final class MassFluxCorrectionResult {
    private final double[] outletBoundaryMassCorrectionKg;
    private final double minimumScale;
    private final double[][] faceScale;

    private MassFluxCorrectionResult(double[] outletBoundaryMassCorrectionKg, double minimumScale,
        double[][] faceScale) {
      this.outletBoundaryMassCorrectionKg = outletBoundaryMassCorrectionKg;
      this.minimumScale = minimumScale;
      this.faceScale = faceScale;
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
    boolean polytropicGas = getGasDensityModel() == GasDensityModel.POLYTROPIC;
    double[] gasExponent = new double[cellCount];
    double[] gasDensityPressureFloor = new double[cellCount];
    if (polytropicGas) {
      for (int cell = 0; cell < cellCount; cell++) {
        double soundSpeed = gasSoundSpeed[cell];
        gasExponent[cell] = gasDensity[cell] * soundSpeed * soundSpeed / pressure[cell];
        if (!(pressure[cell] > 0.0) || !(gasDensity[cell] > 0.0) || !(soundSpeed > 0.0) || !(gasExponent[cell] > 0.0)
            || !Double.isFinite(gasExponent[cell])) {
          throw new IllegalArgumentException(
              "Polytropic gas requires positive finite pressure, density and sound speed");
        }
        gasDensityPressureFloor[cell] = pressure[cell] * Math.pow(MIN_DENSITY / gasDensity[cell], gasExponent[cell]);
      }
    }

    int iterations = 0;
    double[] outletBoundaryMassCorrectionKg = new double[PHASE_COUNT];
    double[][] phaseMassCorrectionsKg = new double[cellCount + 1][PHASE_COUNT];
    double[][] initialPhaseAreas = calculatePhaseAreas(provisionalState, densities);
    double[] interpolationDefect = checkerboardCorrectionEnabled ? interpolationDefect(pressure, lengths)
        : new double[cellCount + 1];
    double[] totalPressureCorrection = new double[cellCount];
    double interpolationFraction = 1.0;
    for (double gradient : interpolationDefect) {
      if (gradient != 0.0) {
        interpolationFraction = 0.0;
        break;
      }
    }
    boolean converged = false;
    boolean correctionLimited = false;
    double minimumMassFluxCorrectionScale = 1.0;
    double[][] activeFaceScale = new double[PHASE_COUNT][cellCount + 1];
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      for (int face = 0; face <= cellCount; face++) {
        activeFaceScale[phase][face] = 1.0;
      }
    }
    double maximumResidual = calculateMaximumRelativeVolumeResidual(correctedState, areas, densities);
    int[] faceDonors = null;

    while (iterations < maximumIterations && (maximumResidual > relativeVolumeTolerance || interpolationFraction < 1.0
        || !outletPressureConverged(correctedPressure, outletPressure, outletPressureFixed))) {
      iterations++;
      double[][] phaseAreas = calculatePhaseAreas(correctedState, densities);
      double[][] fluxPhaseAreas = checkerboardCorrectionEnabled ? initialPhaseAreas : phaseAreas;
      double[] lower = new double[cellCount];
      double[] diagonal = new double[cellCount];
      double[] upper = new double[cellCount];
      double[] rightHandSide = new double[cellCount];
      double[] compressibleAreas = new double[cellCount];

      for (int cell = 0; cell < cellCount; cell++) {
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          double density = Math.max(densities[phase][cell], MIN_DENSITY);
          double soundSpeed = Math.max(soundSpeeds[phase][cell], MIN_SOUND_SPEED);
          if (polytropicGas && phase == GAS_MASS) {
            compressibleAreas[cell] += phaseAreas[phase][cell] / (gasExponent[cell] * correctedPressure[cell]);
          } else {
            compressibleAreas[cell] += phaseAreas[phase][cell] / (density * soundSpeed * soundSpeed);
          }
        }
        rightHandSide[cell] = phaseAreas[GAS_MASS][cell] + phaseAreas[OIL_MASS][cell] + phaseAreas[WATER_MASS][cell]
            - areas[cell];
      }

      if (checkerboardCorrectionEnabled) {
        double[] totalGradient = combinedFaceGradients(totalPressureCorrection, lengths, interpolationDefect,
            interpolationFraction);
        faceDonors = new int[cellCount - 1];
        for (int face = 1; face < cellCount; face++) {
          double direction = totalGradient[face];
          if (direction == 0.0) {
            direction = interpolationDefect[face];
          }
          faceDonors[face - 1] = direction <= 0.0 ? face - 1 : face;
        }
      } else if (faceDonors == null) {
        double[] localCorrection = new double[cellCount];
        for (int cell = 0; cell < cellCount; cell++) {
          localCorrection[cell] = rightHandSide[cell] / Math.max(compressibleAreas[cell], MIN_DIAGONAL);
        }
        if (outletPressureFixed) {
          localCorrection[cellCount - 1] = outletPressure - correctedPressure[cellCount - 1];
        }
        faceDonors = new int[cellCount - 1];
        updateFaceDonors(faceDonors, localCorrection);
      }

      for (int cell = 0; cell < cellCount; cell++) {
        double leftCoefficient = 0.0;
        if (cell > 0) {
          double faceDistance = 0.5 * (lengths[cell - 1] + lengths[cell]);
          double mobility = faceMobility(cell - 1, cell, faceDonors[cell - 1], cell, fluxPhaseAreas, areas, densities,
              activeFaceScale);
          leftCoefficient = timeStep * timeStep * mobility / (lengths[cell] * faceDistance);
          rightHandSide[cell] -= timeStep * timeStep * mobility * (1.0 - interpolationFraction)
              * interpolationDefect[cell] / lengths[cell];
        }

        double rightCoefficient = 0.0;
        if (cell < cellCount - 1) {
          double faceDistance = 0.5 * (lengths[cell] + lengths[cell + 1]);
          double mobility = faceMobility(cell, cell + 1, faceDonors[cell], cell, fluxPhaseAreas, areas, densities,
              activeFaceScale);
          rightCoefficient = timeStep * timeStep * mobility / (lengths[cell] * faceDistance);
          rightHandSide[cell] += timeStep * timeStep * mobility * (1.0 - interpolationFraction)
              * interpolationDefect[cell + 1] / lengths[cell];
        }

        lower[cell] = -leftCoefficient;
        diagonal[cell] = Math.max(compressibleAreas[cell] + leftCoefficient + rightCoefficient, MIN_DIAGONAL);
        upper[cell] = -rightCoefficient;
      }

      if (outletPressureFixed) {
        int outlet = cellCount - 1;
        lower[outlet] = 0.0;
        diagonal[outlet] = 1.0;
        upper[outlet] = 0.0;
        rightHandSide[outlet] = outletPressure - correctedPressure[outlet];
      }

      double[] pressureCorrection = solveTridiagonal(lower, diagonal, upper, rightHandSide);
      // Resolve the upwind donor active set before applying the Newton step.
      // Every re-solve uses the existing iteration budget. The common damping
      // below preserves this direction at every face, unlike independent caps.
      if (!checkerboardCorrectionEnabled && updateFaceDonors(faceDonors, pressureCorrection)) {
        continue;
      }
      double[] proposedTotalPressureCorrection = totalPressureCorrection.clone();
      for (int cell = 0; cell < cellCount; cell++) {
        proposedTotalPressureCorrection[cell] += pressureCorrection[cell];
      }
      double[] proposedTotalGradient = checkerboardCorrectionEnabled
          ? combinedFaceGradients(proposedTotalPressureCorrection, lengths, interpolationDefect, 1.0)
          : null;
      boolean[][] densityResponseActive = new boolean[PHASE_COUNT][cellCount];
      double damping = pressureRelaxation;
      for (int cell = 0; cell < cellCount; cell++) {
        double limit = Math.max(1.0e4, maximumRelativePressureCorrection * correctedPressure[cell]);
        if (pressureCorrection[cell] != 0.0) {
          damping = Math.min(damping, limit / Math.abs(pressureCorrection[cell]));
        }
        if (pressureCorrection[cell] < 0.0) {
          double availablePressure = Math.max(0.0, correctedPressure[cell] - getMinimumPressure());
          damping = Math.min(damping, availablePressure / -pressureCorrection[cell] * (1.0 - 8.0 * Math.ulp(1.0)));
        }
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          densityResponseActive[phase][cell] = checkerboardCorrectionEnabled
              ? correctedState[cell][phase] > 0.0
                  || phaseCanOccupyCellFromFaceGradient(phase, cell, initialPhaseAreas, proposedTotalGradient)
              : phaseCanOccupyCell(phase, cell, phaseAreas, pressureCorrection);
          if (densityResponseActive[phase][cell] && pressureCorrection[cell] < 0.0) {
            if (polytropicGas && phase == GAS_MASS) {
              double pressureDistance = Math.max(0.0, correctedPressure[cell] - gasDensityPressureFloor[cell]);
              damping = Math.min(damping, 0.9 * pressureDistance / -pressureCorrection[cell]);
              continue;
            }
            double soundSpeed = Math.max(soundSpeeds[phase][cell], MIN_SOUND_SPEED);
            double availableDensity = Math.max(0.0, densities[phase][cell] - MIN_DENSITY);
            // A common fraction-to-boundary step keeps the affine acoustic
            // density response positive without independently repairing phases.
            damping = Math.min(damping, 0.9 * availableDensity * soundSpeed * soundSpeed / -pressureCorrection[cell]);
          }
        }
      }
      correctionLimited |= damping < pressureRelaxation;
      if (!(damping > 0.0)) {
        // The declared pressure/density bounds make this Newton direction
        // inadmissible. Report its remaining volume residual to the caller.
        break;
      }
      for (int cell = 0; cell < cellCount; cell++) {
        pressureCorrection[cell] *= damping;
      }

      if (!checkerboardCorrectionEnabled) {
        MassFluxCorrectionResult massFluxCorrection = applyMassFluxCorrection(correctedState, timeStep,
            faceGradients(pressureCorrection, lengths), phaseAreas, areas, lengths, densities, outletPressureFixed,
            phaseMassCorrectionsKg);
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          outletBoundaryMassCorrectionKg[phase] += massFluxCorrection.outletBoundaryMassCorrectionKg[phase];
        }
        minimumMassFluxCorrectionScale = Math.min(minimumMassFluxCorrectionScale, massFluxCorrection.minimumScale);
        activeFaceScale = massFluxCorrection.faceScale;
        applyMomentumCorrection(correctedState, timeStep, pressureCorrection, phaseAreas, lengths);
      }

      for (int cell = 0; cell < cellCount; cell++) {
        correctedPressure[cell] += pressureCorrection[cell];
        totalPressureCorrection[cell] += pressureCorrection[cell];
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          if (densityResponseActive[phase][cell]) {
            if (polytropicGas && phase == GAS_MASS) {
              densities[phase][cell] = gasDensity[cell]
                  * Math.pow(correctedPressure[cell] / pressure[cell], 1.0 / gasExponent[cell]);
              soundSpeeds[phase][cell] = Math
                  .sqrt(gasExponent[cell] * correctedPressure[cell] / densities[phase][cell]);
            } else {
              double soundSpeed = Math.max(soundSpeeds[phase][cell], MIN_SOUND_SPEED);
              if (checkerboardCorrectionEnabled) {
                double referenceDensity = phase == GAS_MASS ? gasDensity[cell]
                    : phase == OIL_MASS ? oilDensity[cell] : waterDensity[cell];
                densities[phase][cell] = referenceDensity + totalPressureCorrection[cell] / (soundSpeed * soundSpeed);
              } else {
                densities[phase][cell] += pressureCorrection[cell] / (soundSpeed * soundSpeed);
              }
            }
          }
        }
      }

      if (checkerboardCorrectionEnabled) {
        interpolationFraction += damping * (1.0 - interpolationFraction);
        // Rebuild from the immutable predictor: neither the old-pressure defect
        // nor an inventory-limited transfer is silently accumulated twice.
        correctedState = copy(provisionalState);
        phaseMassCorrectionsKg = new double[cellCount + 1][PHASE_COUNT];
        MassFluxCorrectionResult massFluxCorrection = applyMassFluxCorrection(correctedState, timeStep,
            combinedFaceGradients(totalPressureCorrection, lengths, interpolationDefect, interpolationFraction),
            initialPhaseAreas, areas, lengths, densities, outletPressureFixed, phaseMassCorrectionsKg);
        outletBoundaryMassCorrectionKg = massFluxCorrection.outletBoundaryMassCorrectionKg;
        minimumMassFluxCorrectionScale = Math.min(minimumMassFluxCorrectionScale, massFluxCorrection.minimumScale);
        activeFaceScale = massFluxCorrection.faceScale;
        applyMomentumCorrection(correctedState, timeStep, totalPressureCorrection, initialPhaseAreas, lengths);
      }

      maximumResidual = calculateMaximumRelativeVolumeResidual(correctedState, areas, densities);
      if (checkerboardCorrectionEnabled && maximumResidual <= relativeVolumeTolerance && interpolationFraction < 1.0) {
        double[][] completeState = copy(provisionalState);
        double[][] completeTransfers = new double[cellCount + 1][PHASE_COUNT];
        MassFluxCorrectionResult completeCorrection = applyMassFluxCorrection(completeState, timeStep,
            combinedFaceGradients(totalPressureCorrection, lengths, interpolationDefect, 1.0), initialPhaseAreas, areas,
            lengths, densities, outletPressureFixed, completeTransfers);
        double completeResidual = calculateMaximumRelativeVolumeResidual(completeState, areas, densities);
        if (completeResidual <= relativeVolumeTolerance) {
          applyMomentumCorrection(completeState, timeStep, totalPressureCorrection, initialPhaseAreas, lengths);
          correctedState = completeState;
          phaseMassCorrectionsKg = completeTransfers;
          outletBoundaryMassCorrectionKg = completeCorrection.outletBoundaryMassCorrectionKg;
          minimumMassFluxCorrectionScale = Math.min(minimumMassFluxCorrectionScale, completeCorrection.minimumScale);
          activeFaceScale = completeCorrection.faceScale;
          maximumResidual = completeResidual;
          interpolationFraction = 1.0;
        }
      }
    }

    converged = maximumResidual <= relativeVolumeTolerance && interpolationFraction == 1.0
        && outletPressureConverged(correctedPressure, outletPressure, outletPressureFixed);
    return new Result(correctedState, correctedPressure, densities[GAS_MASS], densities[OIL_MASS],
        densities[WATER_MASS], soundSpeeds[GAS_MASS], outletBoundaryMassCorrectionKg, phaseMassCorrectionsKg,
        iterations, maximumResidual, converged, correctionLimited, minimumMassFluxCorrectionScale);
  }

  /**
   * The fixed outlet is an independent residual: exact cell-volume closure cannot satisfy a changed boundary.
   *
   * @param pressure corrected cell pressures in Pa
   * @param outletPressure requested outlet pressure in Pa
   * @param outletPressureFixed whether the outlet has a Dirichlet pressure condition
   * @return true if the boundary is free or its relative pressure residual meets the configured tolerance
   */
  private boolean outletPressureConverged(double[] pressure, double outletPressure, boolean outletPressureFixed) {
    return !outletPressureFixed || Double.isFinite(outletPressure) && Double.isFinite(pressure[pressure.length - 1])
        && Math.abs(pressure[pressure.length - 1] - outletPressure) <= relativeVolumeTolerance
            * Math.max(1.0, Math.abs(outletPressure));
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

  private static double[] faceGradients(double[] pressure, double[] lengths) {
    double[] gradients = new double[pressure.length + 1];
    for (int face = 1; face < pressure.length; face++) {
      gradients[face] = (pressure[face] - pressure[face - 1]) / (0.5 * (lengths[face] + lengths[face - 1]));
    }
    return gradients;
  }

  private static double[] combinedFaceGradients(double[] pressureCorrection, double[] lengths, double[] defect,
      double fraction) {
    double[] result = faceGradients(pressureCorrection, lengths);
    for (int face = 1; face < pressureCorrection.length; face++) {
      result[face] += fraction * defect[face];
    }
    return result;
  }

  /** The face/cell gradient difference vanishes for a linear pressure field on a nonuniform mesh. */
  private static double[] interpolationDefect(double[] pressure, double[] lengths) {
    int cellCount = pressure.length;
    double[] result = faceGradients(pressure, lengths);
    double[] cellGradient = new double[cellCount];
    cellGradient[0] = result[1];
    cellGradient[cellCount - 1] = result[cellCount - 1];
    for (int cell = 1; cell < cellCount - 1; cell++) {
      cellGradient[cell] = (pressure[cell + 1] - pressure[cell - 1])
          / (0.5 * lengths[cell - 1] + lengths[cell] + 0.5 * lengths[cell + 1]);
    }
    for (int face = 1; face < cellCount; face++) {
      double interpolatedGradient = (lengths[face] * cellGradient[face - 1] + lengths[face - 1] * cellGradient[face])
          / (lengths[face] + lengths[face - 1]);
      result[face] -= interpolatedGradient;
    }
    return result;
  }

  private static boolean phaseCanOccupyCellFromFaceGradient(int phase, int cell, double[][] phaseAreas,
      double[] gradient) {
    return phaseAreas[phase][cell] > 0.0 || cell > 0 && gradient[cell] < 0.0 && phaseAreas[phase][cell - 1] > 0.0
        || cell + 1 < phaseAreas[phase].length && gradient[cell + 1] > 0.0 && phaseAreas[phase][cell + 1] > 0.0;
  }

  /**
   * Determine whether a phase is present or can enter a cell during this pressure correction.
   *
   * <p>
   * A completely absent phase retains its density placeholder. Its arbitrary density cannot constrain a single-phase
   * pressure solve. A receiver with an upstream phase must instead keep a positive density for the incoming mass.
   * </p>
   *
   * @param phase phase index
   * @param cell cell index
   * @param phaseAreas occupied phase areas before the correction
   * @param pressureCorrection unscaled Newton pressure correction
   * @return true for present or incoming phases
   */
  private static boolean phaseCanOccupyCell(int phase, int cell, double[][] phaseAreas, double[] pressureCorrection) {
    if (phaseAreas[phase][cell] > 0.0) {
      return true;
    }
    if (cell > 0 && pressureCorrection[cell - 1] > pressureCorrection[cell] && phaseAreas[phase][cell - 1] > 0.0) {
      return true;
    }
    return cell < pressureCorrection.length - 1 && pressureCorrection[cell + 1] > pressureCorrection[cell]
        && phaseAreas[phase][cell + 1] > 0.0;
  }

  private static double faceMobility(int leftCell, int rightCell, int donorCell, int volumeCell, double[][] phaseAreas,
      double[] cellAreas, double[][] densities, double[][] activeFaceScale) {
    double faceArea = 0.5 * (cellAreas[leftCell] + cellAreas[rightCell]);
    double mobility = 0.0;
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      double alpha = Math.max(0.0, Math.min(1.0, phaseAreas[phase][donorCell] / cellAreas[donorCell]));
      // The shared mass transfer changes this row's volume at its own density.
      // A face-average density is not the derivative of that volume residual.
      mobility += activeFaceScale[phase][rightCell] * alpha / Math.max(densities[phase][volumeCell], MIN_DENSITY);
    }
    return faceArea * mobility;
  }

  /**
   * Select pressure-correction donors from the higher correction on each face.
   *
   * @param faceDonors active donor cell per internal face, modified in place
   * @param pressureCorrection cell pressure corrections in Pa
   * @return true when any active donor changed
   */
  private static boolean updateFaceDonors(int[] faceDonors, double[] pressureCorrection) {
    boolean changed = false;
    for (int face = 0; face < faceDonors.length; face++) {
      double difference = pressureCorrection[face] - pressureCorrection[face + 1];
      int donor = difference > 0.0 ? face : difference < 0.0 ? face + 1 : faceDonors[face];
      if (donor != face && donor != face + 1) {
        donor = face;
      }
      changed |= donor != faceDonors[face];
      faceDonors[face] = donor;
    }
    return changed;
  }

  static double[] applyConservativeMassFluxCorrection(double[][] state, double timeStep, double[] pressureCorrection,
      double[][] phaseAreas, double[] areas, double[] lengths, double[][] densities, boolean outletPressureFixed) {
    return applyMassFluxCorrection(state, timeStep, faceGradients(pressureCorrection, lengths), phaseAreas, areas,
        lengths, densities, outletPressureFixed, null).outletBoundaryMassCorrectionKg;
  }

  private static MassFluxCorrectionResult applyMassFluxCorrection(double[][] state, double timeStep,
      double[] facePressureGradient, double[][] phaseAreas, double[] areas, double[] lengths, double[][] densities,
      boolean outletPressureFixed, double[][] phaseMassCorrectionsKg) {
    int cellCount = state.length;
    double[][] faceMassFlowCorrection = new double[PHASE_COUNT][cellCount + 1];
    double[][] faceScale = new double[PHASE_COUNT][cellCount + 1];

    for (int face = 1; face < cellCount; face++) {
      int leftCell = face - 1;
      int rightCell = face;
      double pressureGradient = facePressureGradient[face];
      double faceArea = 0.5 * (areas[leftCell] + areas[rightCell]);
      int donor = pressureGradient <= 0.0 ? leftCell : rightCell;

      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double faceAlpha = Math.max(0.0, Math.min(1.0, phaseAreas[phase][donor] / areas[donor]));
        faceMassFlowCorrection[phase][face] = -timeStep * faceAlpha * faceArea * pressureGradient;
      }
    }

    // Upwinding excludes absent donor phases, while this shared budget also
    // prevents a large correction from overdrawing a present donor phase.
    double minimumScale = limitCorrectionFluxesForPositivity(state, timeStep, faceMassFlowCorrection, faceScale,
        lengths);

    if (phaseMassCorrectionsKg != null) {
      for (int face = 1; face < cellCount; face++) {
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          phaseMassCorrectionsKg[face][phase] += timeStep * faceMassFlowCorrection[phase][face];
        }
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
        occupiedArea += Math.max(state[outlet][phase], 0.0) / Math.max(densities[phase][outlet], MIN_DENSITY);
      }
      if (!(occupiedArea > 0.0) || !Double.isFinite(occupiedArea)) {
        throw new IllegalStateException("Fixed-pressure outlet has no finite phase volume");
      }
      double volumeScale = areas[outlet] / occupiedArea;
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double previousMass = state[outlet][phase];
        // Multiplicative projection preserves positivity even for a large volume
        // residual. A negative predictor is left for the caller's step rejection.
        double projectedMass = previousMass > 0.0 ? previousMass * volumeScale : previousMass;
        double massPerLengthCorrection = projectedMass - previousMass;
        state[outlet][phase] = projectedMass;
        outletBoundaryMassCorrectionKg[phase] -= massPerLengthCorrection * lengths[outlet];
        if (phaseMassCorrectionsKg != null) {
          phaseMassCorrectionsKg[cellCount][phase] -= massPerLengthCorrection * lengths[outlet];
        }
      }
    }
    return new MassFluxCorrectionResult(outletBoundaryMassCorrectionKg, minimumScale, faceScale);
  }

  /**
   * Limit simultaneous outgoing correction fluxes by initial inventory and feasible incoming mass.
   *
   * <p>
   * Both faces share one donor budget, and every face keeps a single conservative transfer. Donor scales decrease
   * monotonically until initial mass plus already-limited incoming mass can supply each outgoing correction. This
   * retains feasible through-flow, while the machine-sized margin prevents negative residuals from roundoff.
   * </p>
   *
   * @param state provisional conservative cell state
   * @param timeStep correction timestep in seconds
   * @param faceFluxes per-phase face mass-flow corrections in kg/s, modified in place
   * @param faceScale realized correction scales by phase and face
   * @param lengths cell lengths in meters
   * @return smallest scale applied to a nonzero face correction
   */
  private static double limitCorrectionFluxesForPositivity(double[][] state, double timeStep, double[][] faceFluxes,
      double[][] faceScale, double[] lengths) {
    int cellCount = state.length;
    double minimumScale = 1.0;
    double[] donorFactors = new double[cellCount];
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      for (int cell = 0; cell < cellCount; cell++) {
        donorFactors[cell] = 1.0;
      }
      // A decrease propagates at most one cell per pass in this one-dimensional donor graph.
      for (int pass = 0; pass < cellCount; pass++) {
        boolean changed = false;
        for (int cell = 0; cell < cellCount; cell++) {
          double leftFlux = faceFluxes[phase][cell];
          double rightFlux = faceFluxes[phase][cell + 1];
          double outgoingRate = Math.max(rightFlux, 0.0) + Math.max(-leftFlux, 0.0);
          if (!(outgoingRate > 0.0)) {
            continue;
          }
          double incomingRate = 0.0;
          if (leftFlux > 0.0 && cell > 0) {
            incomingRate += leftFlux * donorFactors[cell - 1];
          }
          if (rightFlux < 0.0 && cell < cellCount - 1) {
            incomingRate -= rightFlux * donorFactors[cell + 1];
          }
          double requestedMass = timeStep * outgoingRate;
          double availableMass = Math.max(state[cell][phase], 0.0) * lengths[cell] + timeStep * incomingRate;
          double permittedScale = 1.0;
          if (requestedMass >= availableMass) {
            permittedScale = Math.min(1.0, availableMass / requestedMass) * (1.0 - 8.0 * Math.ulp(1.0));
          }
          if (permittedScale < donorFactors[cell]) {
            donorFactors[cell] = Math.max(permittedScale, 0.0);
            changed = true;
          }
        }
        if (!changed) {
          break;
        }
      }
      for (int face = 1; face < cellCount; face++) {
        int donor = faceFluxes[phase][face] >= 0.0 ? face - 1 : face;
        faceScale[phase][face] = faceFluxes[phase][face] == 0.0 ? 1.0 : donorFactors[donor];
        minimumScale = Math.min(minimumScale, faceScale[phase][face]);
        faceFluxes[phase][face] *= faceScale[phase][face];
      }
    }
    return minimumScale;
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

  /** @return maximum nonlinear correction iterations */
  public int getMaximumIterations() {
    return maximumIterations;
  }

  /** @param maximumIterations maximum nonlinear correction iterations */
  public void setMaximumIterations(int maximumIterations) {
    if (maximumIterations < 1) {
      throw new IllegalArgumentException("maximumIterations must be at least one");
    }
    this.maximumIterations = maximumIterations;
  }

  /**
   * Get the numerical lower bound for absolute cell pressure.
   *
   * @return minimum absolute pressure in Pa, default 1 Pa
   */
  public double getMinimumPressure() {
    // Serialized solvers from before this setting have a zero-valued field.
    return minimumPressure > 0.0 ? minimumPressure : DEFAULT_MIN_PRESSURE;
  }

  /**
   * Set the numerical lower bound for absolute cell pressure.
   *
   * <p>
   * The default 1 Pa prevents nonpositive pressure without imposing atmospheric pressure on interior cells. A fixed
   * outlet pressure is a boundary condition, not a lower bound throughout the pipe. This setting is not a
   * vapor-pressure or cavitation model; the caller must supply a valid thermodynamic state. If this bound makes volume
   * closure infeasible, the correction reports nonconvergence and does not repair density independently of pressure.
   * </p>
   *
   * @param minimumPressure finite positive absolute pressure in Pa
   * @throws IllegalArgumentException if the pressure is not finite and positive
   */
  public void setMinimumPressure(double minimumPressure) {
    if (!(minimumPressure > 0.0) || !Double.isFinite(minimumPressure)) {
      throw new IllegalArgumentException("minimumPressure must be positive and finite");
    }
    this.minimumPressure = minimumPressure;
  }

  /** @return gas density model, default {@link GasDensityModel#AFFINE} */
  public GasDensityModel getGasDensityModel() {
    return gasDensityModel == null ? GasDensityModel.AFFINE : gasDensityModel;
  }

  /**
   * Select the gas pressure-density response for the coupled correction.
   *
   * <p>
   * The polytropic option anchors {@code rho = rho_ref * (p / p_ref)^(1/gamma)} at the start of each correction, with
   * {@code gamma = c_ref^2 * rho_ref / p_ref}. It preserves the supplied local acoustic derivative and supplies its
   * exact nonlinear Jacobian. Carry the returned gas sound speed into subsequent corrections to preserve this
   * barotropic relation. This is a finite-amplitude local power-law model, not a full real-fluid thermodynamic EOS. Oil
   * and water retain their affine acoustic response.
   * </p>
   *
   * @param gasDensityModel non-null density response model
   * @throws IllegalArgumentException if the model is null
   */
  public void setGasDensityModel(GasDensityModel gasDensityModel) {
    if (gasDensityModel == null) {
      throw new IllegalArgumentException("gasDensityModel must not be null");
    }
    this.gasDensityModel = gasDensityModel;
  }

  /** @return whether the implicit face/cell pressure-gradient interpolation correction is enabled */
  public boolean isCheckerboardCorrectionEnabled() {
    return checkerboardCorrectionEnabled;
  }

  /**
   * Enable pressure-velocity momentum interpolation for collocated cells.
   *
   * <p>
   * The face gradient defect of the old pressure supplies an additional conservative phase mass transfer. A homotopy
   * introduces this term together with the Newton pressure correction, always reconstructing total face transfers and
   * momenta from the immutable predictor. Convergence requires the complete old-pressure term, not an intermediate
   * forcing fraction, to satisfy the volume tolerance. Linear pressure fields are unaffected by the added term. This
   * option does not remove explicit stability limits imposed by an independently selected transport flux.
   * </p>
   *
   * @param enabled true to correct the collocated pressure null mode; false by default
   */
  public void setCheckerboardCorrectionEnabled(boolean enabled) {
    checkerboardCorrectionEnabled = enabled;
  }

  /** @return maximum accepted relative cell-volume and fixed-outlet pressure residual */
  public double getRelativeVolumeTolerance() {
    return relativeVolumeTolerance;
  }

  /** @param tolerance maximum accepted relative cell-volume and fixed-outlet pressure residual */
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
