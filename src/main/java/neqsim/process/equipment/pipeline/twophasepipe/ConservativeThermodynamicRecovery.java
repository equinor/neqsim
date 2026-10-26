package neqsim.process.equipment.pipeline.twophasepipe;

import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.thermo.phase.PhaseIdealGas;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Recovers common pressure and temperature from fixed phase masses, momenta, energy and cell volume.
 *
 * <p>
 * Each supplied homogeneous EOS state represents the actual transported composition of one phase. Recovery varies only
 * pressure and temperature; it never flashes or redistributes components between phases. All energy quantities are per
 * unit pipe length, and the caloric reference may therefore give negative internal energy.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class ConservativeThermodynamicRecovery {
  /** Relative occupied-volume tolerance. */
  private static final double VOLUME_TOLERANCE = 1.0e-9;
  /** Relative internal-energy tolerance on a positive thermal-energy scale. */
  private static final double ENERGY_TOLERANCE = 1.0e-9;
  /** Logarithmic finite-difference increment. */
  private static final double DERIVATIVE_STEP = 1.0e-5;
  /** Maximum nonlinear iterations. */
  private static final int MAX_ITERATIONS = 40;

  /** Prevent construction of this stateless utility. */
  private ConservativeThermodynamicRecovery() {
  }

  /**
   * Recover a conservative endpoint within the numerical temperature interval 50 to 2000 K.
   *
   * @param section candidate conservative cell, unchanged by this method
   * @param phaseStates gas/oil/aqueous homogeneous EOS templates; null is allowed only for an absent phase
   * @return recovered thermodynamic state
   * @throws IllegalArgumentException if inputs are inadmissible
   * @throws IllegalStateException if no admissible converged recovery is found
   */
  public static Result recover(TwoFluidSection section, SystemInterface[] phaseStates) {
    return recover(section, phaseStates, 50.0, 2000.0);
  }

  /**
   * Recover pressure and temperature without modifying a cell, its inventories, or the supplied EOS states.
   *
   * @param section candidate conservative cell
   * @param phaseStates gas/oil/aqueous homogeneous EOS templates at fixed phase compositions
   * @param minimumTemperatureK lower admissible temperature in K
   * @param maximumTemperatureK upper admissible temperature in K
   * @return converged thermodynamic endpoint
   * @throws IllegalArgumentException if inputs are inadmissible
   * @throws IllegalStateException if the EOS or nonlinear recovery fails
   */
  public static Result recover(TwoFluidSection section, SystemInterface[] phaseStates, double minimumTemperatureK,
      double maximumTemperatureK) {
    if (section == null || phaseStates == null || phaseStates.length != 3 || !(minimumTemperatureK > 0.0)
        || !Double.isFinite(minimumTemperatureK) || !(maximumTemperatureK > minimumTemperatureK)
        || !Double.isFinite(maximumTemperatureK)) {
      throw new IllegalArgumentException("Recovery requires a cell, three phase slots and finite temperature bounds");
    }
    double[] state = section.getStateVector();
    double area = section.getArea();
    if (!(area > 0.0) || !Double.isFinite(area) || !(section.getPressure() > 0.0)
        || !Double.isFinite(section.getPressure()) || !Double.isFinite(section.getTemperature())
        || section.getTemperature() < minimumTemperatureK || section.getTemperature() > maximumTemperatureK) {
      throw new IllegalArgumentException("Recovery geometry and initial pressure/temperature must be admissible");
    }
    SystemInterface[] fluids = new SystemInterface[3];
    double kineticEnergy = 0.0;
    double totalMass = 0.0;
    for (double value : state) {
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Recovery conservative state must be finite");
      }
    }
    for (int phase = 0; phase < 3; phase++) {
      if (state[phase] < 0.0 || state[phase] == 0.0 && state[phase + 3] != 0.0) {
        throw new IllegalArgumentException("Recovery masses must be nonnegative; absent phases cannot carry momentum");
      }
      if (state[phase] > 0.0) {
        if (phaseStates[phase] == null || phaseStates[phase].getNumberOfPhases() != 1) {
          throw new IllegalArgumentException("Every present phase needs one homogeneous EOS state");
        }
        fluids[phase] = phaseStates[phase].clone();
        double velocity = state[phase + 3] / state[phase];
        kineticEnergy += 0.5 * state[phase] * velocity * velocity;
        totalMass += state[phase];
      }
    }
    double targetInternalEnergy = state[6] - kineticEnergy;
    if (!(totalMass > 0.0) || !Double.isFinite(kineticEnergy) || !Double.isFinite(targetInternalEnergy)) {
      throw new IllegalArgumentException("Recovery requires finite internal energy and positive total mass");
    }
    double logPressure = Math.log(section.getPressure());
    double logTemperature = Math.log(section.getTemperature());
    Evaluation current = evaluate(fluids, state, logPressure, logTemperature);
    double energyScale = Math.max(1.0,
        Math.max(Math.abs(targetInternalEnergy), current.heatCapacityPerLength * section.getTemperature()));
    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
      double volumeResidual = current.occupiedArea / area - 1.0;
      double energyResidual = (current.internalEnergy - targetInternalEnergy) / energyScale;
      if (Math.abs(volumeResidual) <= VOLUME_TOLERANCE && Math.abs(energyResidual) <= ENERGY_TOLERANCE) {
        // Leave every returned EOS template at the converged state, including its transport properties.
        current = evaluate(fluids, state, logPressure, logTemperature);
        return new Result(state, area, Math.exp(logPressure), Math.exp(logTemperature), fluids,
            current.occupiedArea / area - 1.0, current.internalEnergy - targetInternalEnergy, iteration);
      }
      Evaluation pressurePlus = evaluate(fluids, state, logPressure + DERIVATIVE_STEP, logTemperature);
      Evaluation pressureMinus = evaluate(fluids, state, logPressure - DERIVATIVE_STEP, logTemperature);
      Evaluation temperaturePlus = evaluate(fluids, state, logPressure, logTemperature + DERIVATIVE_STEP);
      Evaluation temperatureMinus = evaluate(fluids, state, logPressure, logTemperature - DERIVATIVE_STEP);
      double a = (pressurePlus.occupiedArea - pressureMinus.occupiedArea) / (2.0 * DERIVATIVE_STEP * area);
      double b = (temperaturePlus.occupiedArea - temperatureMinus.occupiedArea) / (2.0 * DERIVATIVE_STEP * area);
      double c = (pressurePlus.internalEnergy - pressureMinus.internalEnergy) / (2.0 * DERIVATIVE_STEP * energyScale);
      double d = (temperaturePlus.internalEnergy - temperatureMinus.internalEnergy)
          / (2.0 * DERIVATIVE_STEP * energyScale);
      double determinant = a * d - b * c;
      if (!(a < 0.0) || !(determinant < 0.0) || !Double.isFinite(determinant)) {
        throw new IllegalStateException("Conservative P/T recovery encountered an unstable or singular EOS branch");
      }
      double pressureStep = (-d * volumeResidual + b * energyResidual) / determinant;
      double temperatureStep = (c * volumeResidual - a * energyResidual) / determinant;
      double stepScale = Math.max(1.0, Math.max(Math.abs(pressureStep), Math.abs(temperatureStep)) / 0.5);
      pressureStep /= stepScale;
      temperatureStep /= stepScale;
      double residualNorm = Math.max(Math.abs(volumeResidual), Math.abs(energyResidual));
      boolean accepted = false;
      for (int lineSearch = 0; lineSearch < 18; lineSearch++) {
        double damping = Math.scalb(1.0, -lineSearch);
        double trialLogPressure = logPressure + damping * pressureStep;
        double trialLogTemperature = logTemperature + damping * temperatureStep;
        double trialTemperature = Math.exp(trialLogTemperature);
        if (!Double.isFinite(trialLogPressure) || !Double.isFinite(trialLogTemperature)
            || trialTemperature < minimumTemperatureK || trialTemperature > maximumTemperatureK) {
          continue;
        }
        try {
          Evaluation trial = evaluate(fluids, state, trialLogPressure, trialLogTemperature);
          double trialNorm = Math.max(Math.abs(trial.occupiedArea / area - 1.0),
              Math.abs((trial.internalEnergy - targetInternalEnergy) / energyScale));
          if (trialNorm < residualNorm) {
            logPressure = trialLogPressure;
            logTemperature = trialLogTemperature;
            current = trial;
            accepted = true;
            break;
          }
        } catch (RuntimeException invalidTrial) {
          // The accepted cell is untouched; reduce the trial rather than repairing the conservative state.
        }
      }
      if (!accepted) {
        throw new IllegalStateException("Conservative P/T recovery has no admissible decreasing Newton step");
      }
    }
    throw new IllegalStateException(
        "Conservative P/T recovery did not converge within " + MAX_ITERATIONS + " iterations");
  }

  /**
   * Evaluate occupied area and internal energy at fixed phase composition.
   *
   * @param fluids private homogeneous EOS states
   * @param state fixed conservative variables per unit length
   * @param logPressure logarithm of absolute pressure in Pa
   * @param logTemperature logarithm of temperature in K
   * @return thermodynamic residual quantities
   * @throws IllegalStateException if EOS properties are invalid
   */
  private static Evaluation evaluate(SystemInterface[] fluids, double[] state, double logPressure,
      double logTemperature) {
    double pressure = Math.exp(logPressure);
    double temperature = Math.exp(logTemperature);
    if (!(pressure > 0.0) || !Double.isFinite(pressure) || !(temperature > 0.0) || !Double.isFinite(temperature)) {
      throw new IllegalStateException("Nonfinite or nonpositive conservative recovery trial");
    }
    double occupiedArea = 0.0;
    double internalEnergy = 0.0;
    double heatCapacityPerLength = 0.0;
    for (int phase = 0; phase < 3; phase++) {
      if (fluids[phase] == null) {
        continue;
      }
      SystemInterface fluid = fluids[phase];
      fluid.setPressure(pressure / 1.0e5);
      fluid.setTemperature(temperature);
      fluid.init(3);
      fluid.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      PhaseInterface eosPhase = fluid.getPhase(0);
      PhaseType type = eosPhase.getType();
      if (phase == 0 && type != PhaseType.GAS || phase == 1 && type != PhaseType.OIL && type != PhaseType.LIQUID
          || phase == 2 && type != PhaseType.AQUEOUS) {
        throw new IllegalStateException("Conservative recovery EOS state changed its forced phase branch");
      }
      if (!(eosPhase instanceof PhaseIdealGas)) {
        double compressibility = eosPhase.getIsothermalCompressibility();
        if (!(compressibility > 0.0) || !Double.isFinite(compressibility)) {
          throw new IllegalStateException("Unstable phase compressibility in conservative recovery");
        }
      }
      double density = eosPhase.getDensity("kg/m3");
      double enthalpy = eosPhase.getEnthalpy("J/kg");
      double cv = eosPhase.getCv("J/kgK");
      if (!(density > 0.0) || !Double.isFinite(density) || !Double.isFinite(enthalpy) || !(cv > 0.0)
          || !Double.isFinite(cv)) {
        throw new IllegalStateException(
            "Invalid phase density, internal energy or heat capacity in conservative recovery");
      }
      double phaseArea = state[phase] / density;
      occupiedArea += phaseArea;
      internalEnergy += state[phase] * enthalpy - pressure * phaseArea;
      heatCapacityPerLength += state[phase] * cv;
    }
    if (!(occupiedArea > 0.0) || !Double.isFinite(occupiedArea) || !Double.isFinite(internalEnergy)) {
      throw new IllegalStateException("Invalid occupied volume or energy in conservative recovery");
    }
    return new Evaluation(occupiedArea, internalEnergy, heatCapacityPerLength);
  }

  /**
   * Thermodynamic residual quantities.
   *
   * @author NeqSim
   * @version 1.0
   */
  private static final class Evaluation {
    /** Occupied volume per pipe length, in square metres. */
    private final double occupiedArea;
    /** Internal energy per pipe length, in J/m. */
    private final double internalEnergy;
    /** Positive phase-mass-weighted heat capacity, in J/(m K). */
    private final double heatCapacityPerLength;

    /**
     * Store one residual evaluation.
     *
     * @param occupiedArea occupied area in square metres
     * @param internalEnergy internal energy in J/m
     * @param heatCapacityPerLength phase heat capacity in J/(m K)
     */
    private Evaluation(double occupiedArea, double internalEnergy, double heatCapacityPerLength) {
      this.occupiedArea = occupiedArea;
      this.internalEnergy = internalEnergy;
      this.heatCapacityPerLength = heatCapacityPerLength;
    }
  }

  /**
   * Converged fixed-inventory endpoint, which can be applied without changing the conservative variables.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static final class Result {
    /** Conservative variables for which recovery was performed. */
    private final double[] state;
    /** Cell area in square metres. */
    private final double area;
    /** Absolute pressure in Pa. */
    private final double pressurePa;
    /** Temperature in K. */
    private final double temperatureK;
    /** Private converged homogeneous EOS states. */
    private final SystemInterface[] fluids;
    /** Final relative volume residual. */
    private final double relativeVolumeResidual;
    /** Final internal-energy residual in J/m. */
    private final double energyResidualJPerMetre;
    /** Number of accepted Newton iterations. */
    private final int iterations;

    /**
     * Store a verified endpoint and initialize transport properties before it can be applied.
     *
     * @param state conservative endpoint
     * @param area pipe area in square metres
     * @param pressurePa converged pressure in Pa
     * @param temperatureK converged temperature in K
     * @param fluids private converged EOS states
     * @param relativeVolumeResidual relative volume residual
     * @param energyResidualJPerMetre internal-energy residual in J/m
     * @param iterations accepted Newton iteration count
     */
    private Result(double[] state, double area, double pressurePa, double temperatureK, SystemInterface[] fluids,
        double relativeVolumeResidual, double energyResidualJPerMetre, int iterations) {
      this.state = state.clone();
      this.area = area;
      this.pressurePa = pressurePa;
      this.temperatureK = temperatureK;
      this.fluids = fluids;
      this.relativeVolumeResidual = relativeVolumeResidual;
      this.energyResidualJPerMetre = energyResidualJPerMetre;
      this.iterations = iterations;
      for (SystemInterface fluid : fluids) {
        if (fluid != null) {
          fluid.initProperties();
        }
      }
    }

    /**
     * Get common absolute pressure.
     *
     * @return pressure in Pa
     */
    public double getPressurePa() {
      return pressurePa;
    }

    /**
     * Get common temperature.
     *
     * @return temperature in K
     */
    public double getTemperatureK() {
      return temperatureK;
    }

    /**
     * Get a defensive EOS snapshot at the actual fixed phase composition.
     *
     * @param phase gas=0, oil=1, aqueous=2
     * @return cloned homogeneous phase state, or null for an absent phase
     */
    public SystemInterface getPhaseState(int phase) {
      return fluids[phase] == null ? null : fluids[phase].clone();
    }

    /**
     * Get the final occupied-volume residual.
     *
     * @return occupied volume divided by cell volume minus one
     */
    public double getRelativeVolumeResidual() {
      return relativeVolumeResidual;
    }

    /**
     * Get final internal-energy residual.
     *
     * @return reconstructed minus conserved internal energy in J/m
     */
    public double getEnergyResidualJPerMetre() {
      return energyResidualJPerMetre;
    }

    /**
     * Get accepted nonlinear iteration count.
     *
     * @return Newton iteration count
     */
    public int getIterations() {
      return iterations;
    }

    /**
     * Apply pressure, temperature and properties to the exact conservative state for which recovery was performed.
     * Validation completes on a clone before changing the caller's section.
     *
     * @param section section with matching geometry and conservative variables
     * @throws IllegalArgumentException if the section changed since recovery or properties are inadmissible
     */
    public void applyTo(TwoFluidSection section) {
      if (section == null || section.getArea() != area) {
        throw new IllegalArgumentException("Recovery result requires the original cell geometry");
      }
      double[] currentState = section.getStateVector();
      for (int variable = 0; variable < state.length; variable++) {
        if (currentState[variable] != state[variable]) {
          throw new IllegalArgumentException("Conservative state changed after thermodynamic recovery");
        }
      }
      TwoFluidSection candidate = section.clone();
      install(candidate);
      install(section);
    }

    /**
     * Install initialized EOS properties and derive primitives without modifying conservative inventory.
     *
     * @param section validated target cell
     */
    private void install(TwoFluidSection section) {
      section.setPressure(pressurePa);
      section.setTemperature(temperatureK);
      double liquidMass = state[1] + state[2];
      double liquidEnthalpy = 0.0;
      double liquidViscosity = 0.0;
      double liquidSoundSpeed = 0.0;
      double liquidArea = 0.0;
      double oilEnthalpy = 0.0;
      double waterEnthalpy = 0.0;
      double totalMass = state[0] + liquidMass;
      double cv = 0.0;
      for (int phase = 0; phase < 3; phase++) {
        if (fluids[phase] == null) {
          // Endpoint validation requires positive unused phase densities as well.
          if (phase == 0) {
            section.setGasDensity(1.0);
          } else if (phase == 1) {
            section.setOilDensity(700.0);
          } else {
            section.setWaterDensity(1000.0);
          }
          continue;
        }
        PhaseInterface eosPhase = fluids[phase].getPhase(0);
        double density = eosPhase.getDensity("kg/m3");
        double viscosity = eosPhase.getViscosity("kg/msec");
        double soundSpeed = eosPhase.getSoundSpeed();
        double enthalpy = eosPhase.getEnthalpy("J/kg");
        if (!(density > 0.0) || !Double.isFinite(density) || !(viscosity > 0.0) || !Double.isFinite(viscosity)
            || !(soundSpeed > 0.0) || !Double.isFinite(soundSpeed)) {
          throw new IllegalArgumentException("Conservative recovery transport properties must be positive and finite");
        }
        cv += state[phase] * eosPhase.getCv("J/kgK");
        if (phase == 0) {
          section.setGasDensity(density);
          section.setGasViscosity(viscosity);
          section.setGasSoundSpeed(soundSpeed);
          section.setGasEnthalpy(enthalpy);
        } else {
          if (phase == 1) {
            section.setOilDensity(density);
            section.setOilViscosity(viscosity);
            oilEnthalpy = enthalpy;
          } else {
            section.setWaterDensity(density);
            section.setWaterViscosity(viscosity);
            waterEnthalpy = enthalpy;
          }
          liquidEnthalpy += state[phase] * enthalpy;
          double phaseArea = state[phase] / density;
          liquidArea += phaseArea;
          liquidViscosity += phaseArea * viscosity;
          liquidSoundSpeed += phaseArea * soundSpeed;
        }
      }
      if (liquidMass > 0.0) {
        section.setLiquidEnthalpy(liquidEnthalpy / liquidMass);
        section.setLiquidViscosity(liquidViscosity / liquidArea);
        section.setLiquidSoundSpeed(liquidSoundSpeed / liquidArea);
      }
      section.setPhaseEnthalpies(oilEnthalpy, waterEnthalpy);
      section.setMixtureHeatCapacity(cv / totalMass);
      section.setConservativeEndpoint(state, 2.0 * VOLUME_TOLERANCE);
    }
  }
}
