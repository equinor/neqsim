package neqsim.process.equipment.pipeline;

import java.io.Serializable;
import neqsim.process.safety.release.IdealGasReleaseModel;
import neqsim.process.safety.release.ReleaseFlowRequest;
import neqsim.process.safety.release.ReleaseFlowResult;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Conservative two-control-volume ideal-gas coupling for a single rigid pig.
 *
 * <p>
 * The pig separates upstream and downstream gas inventories. Its pressure area is also the swept pipe area, so one
 * signed displacement expands the upstream volume and contracts the downstream volume by the same amount. The existing
 * {@link PigMotion} model resolves breakaway, stopping and reversal. The existing {@link IdealGasReleaseModel} supplies
 * bidirectional short-orifice bypass, including the choked-flow transition. Beginning-of-substep pressures are used
 * consistently for both gas boundary work and pig pressure work, making the combined gas/pig energy ledger conservative
 * for every accepted substep.
 * </p>
 *
 * <p>
 * This model is deliberately limited to a calorically perfect, single gas phase in two well-mixed control volumes. It
 * does not represent pressure waves, distributed pipe friction, heat transfer, liquid sweeping, residual film, seal
 * deformation, fittings or pig trains. The explicit substep and fractional-transfer controls are numerical safeguards,
 * not fitted physical parameters.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class IdealGasPiggingModel implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final double GAS_CONSTANT_J_MOL_K = 8.31446261815324;
  private static final double EVENT_POSITION_TOLERANCE_M = 1.0e-12;
  private static final int EVENT_BISECTION_ITERATIONS = 80;
  private static final int MAX_INTERNAL_STEPS = 1000000;

  /** Discrete pig location used for launcher and receiver event handling. */
  public enum Location {
    /** Pig is retained at the launcher boundary. */
    LAUNCHER,
    /** Pig is moving or stalled inside the pipe. */
    PIPE,
    /** Pig has contacted the receiver and the trajectory is terminal. */
    RECEIVER
  }

  private final PigMotion mechanics;
  private final SystemInterface gasTemplate;
  private final IdealGasReleaseModel bypassModel = new IdealGasReleaseModel();
  private final double pipeLengthM;
  private final double launcherVolumeM3;
  private final double receiverVolumeM3;
  private final double bypassDiameterM;
  private final double bypassDischargeCoefficient;
  private final double inclinationRadians;
  private final double maximumSubstepSeconds;
  private final double maximumDonorMassFraction;
  private final double maximumPigTravelFraction;
  private final double gamma;
  private final double specificGasConstantJPerKgK;
  private final double specificHeatCvJPerKgK;

  /**
   * Creates a gas-side pigging model with explicit geometry and numerical safeguards.
   *
   * @param mechanics rigid-pig mechanical model; its pressure area is the swept pipe area
   * @param gasTemplate single-phase gas composition used by the ideal-gas bypass model
   * @param pipeLengthM launcher-to-receiver distance in m
   * @param launcherVolumeM3 positive dead volume behind a pig at the launcher in m3
   * @param receiverVolumeM3 positive dead volume ahead of a pig at the receiver in m3
   * @param bypassDiameterM circular bypass diameter in m; zero means a sealed pig
   * @param bypassDischargeCoefficient bypass discharge coefficient in (0, 1] when bypass is open
   * @param inclinationRadians constant pipe inclination in radians, positive uphill
   * @param maximumSubstepSeconds maximum accepted explicit coupling step in s
   * @param maximumDonorMassFraction maximum bypass transfer per substep as a fraction of donor mass, in (0, 0.5]
   * @param maximumPigTravelFraction maximum pig travel per substep as a fraction of pipe length, in (0, 0.25]
   * @throws IllegalArgumentException for an invalid gas, geometry or numerical control
   */
  public IdealGasPiggingModel(PigMotion mechanics, SystemInterface gasTemplate, double pipeLengthM,
      double launcherVolumeM3, double receiverVolumeM3, double bypassDiameterM, double bypassDischargeCoefficient,
      double inclinationRadians, double maximumSubstepSeconds, double maximumDonorMassFraction,
      double maximumPigTravelFraction) {
    if (mechanics == null || gasTemplate == null) {
      throw new IllegalArgumentException("Pig mechanics and a gas template are required");
    }
    positive(pipeLengthM, "pipeLengthM");
    positive(launcherVolumeM3, "launcherVolumeM3");
    positive(receiverVolumeM3, "receiverVolumeM3");
    nonnegative(bypassDiameterM, "bypassDiameterM");
    positive(maximumSubstepSeconds, "maximumSubstepSeconds");
    if (!Double.isFinite(bypassDischargeCoefficient) || bypassDischargeCoefficient <= 0.0
        || bypassDischargeCoefficient > 1.0 || !Double.isFinite(inclinationRadians)
        || Math.abs(inclinationRadians) > Math.PI / 2.0 || !Double.isFinite(maximumDonorMassFraction)
        || maximumDonorMassFraction <= 0.0 || maximumDonorMassFraction > 0.5
        || !Double.isFinite(maximumPigTravelFraction) || maximumPigTravelFraction <= 0.0
        || maximumPigTravelFraction > 0.25) {
      throw new IllegalArgumentException("Require Cd in (0,1], inclination in [-pi/2,pi/2], donor fraction in "
          + "(0,0.5] and travel fraction in (0,0.25]");
    }
    this.mechanics = mechanics;
    this.gasTemplate = initialiseGas(gasTemplate);
    this.pipeLengthM = pipeLengthM;
    this.launcherVolumeM3 = launcherVolumeM3;
    this.receiverVolumeM3 = receiverVolumeM3;
    this.bypassDiameterM = bypassDiameterM;
    this.bypassDischargeCoefficient = bypassDischargeCoefficient;
    this.inclinationRadians = inclinationRadians;
    this.maximumSubstepSeconds = maximumSubstepSeconds;
    this.maximumDonorMassFraction = maximumDonorMassFraction;
    this.maximumPigTravelFraction = maximumPigTravelFraction;
    gamma = this.gasTemplate.getGamma();
    if (!Double.isFinite(gamma) || gamma <= 1.0 || gamma > 2.0) {
      throw new IllegalArgumentException("Gas heat-capacity ratio must be finite and in (1,2]");
    }
    double molarMassKgPerMol = this.gasTemplate.getMolarMass();
    positive(molarMassKgPerMol, "gas molar mass");
    specificGasConstantJPerKgK = GAS_CONSTANT_J_MOL_K / molarMassKgPerMol;
    specificHeatCvJPerKgK = specificGasConstantJPerKgK / (gamma - 1.0);
  }

  /**
   * Creates conservative gas inventories from pressure, temperature and the initial pig position.
   *
   * @param pigPositionM initial pig position in [0, pipe length] m
   * @param pigVelocityMps initial signed pig velocity in m/s
   * @param upstreamPressurePa upstream absolute pressure in Pa
   * @param downstreamPressurePa downstream absolute pressure in Pa
   * @param temperatureK common initial gas temperature in K
   * @return immutable initial coupled state
   */
  public State initialise(double pigPositionM, double pigVelocityMps, double upstreamPressurePa,
      double downstreamPressurePa, double temperatureK) {
    if (!Double.isFinite(pigPositionM) || pigPositionM < 0.0 || pigPositionM > pipeLengthM
        || !Double.isFinite(pigVelocityMps)) {
      throw new IllegalArgumentException("Initial pig position must be in the pipe and velocity finite");
    }
    positive(upstreamPressurePa, "upstreamPressurePa");
    positive(downstreamPressurePa, "downstreamPressurePa");
    positive(temperatureK, "temperatureK");
    double upstreamVolume = upstreamVolume(pigPositionM);
    double downstreamVolume = downstreamVolume(pigPositionM);
    double upstreamMass = upstreamPressurePa * upstreamVolume / (specificGasConstantJPerKgK * temperatureK);
    double downstreamMass = downstreamPressurePa * downstreamVolume / (specificGasConstantJPerKgK * temperatureK);
    double upstreamEnergy = upstreamMass * specificHeatCvJPerKgK * temperatureK;
    double downstreamEnergy = downstreamMass * specificHeatCvJPerKgK * temperatureK;
    Location location = pigPositionM == pipeLengthM ? Location.RECEIVER
        : pigPositionM == 0.0 ? Location.LAUNCHER : Location.PIPE;
    double velocity = location == Location.RECEIVER ? 0.0 : pigVelocityMps;
    return new State(0.0, pigPositionM, velocity, upstreamMass, downstreamMass, upstreamEnergy, downstreamEnergy,
        location);
  }

  /**
   * Advances a state transactionally through a requested duration.
   *
   * @param initial accepted initial state
   * @param durationSeconds requested elapsed time in s, nonnegative
   * @return immutable final state and conservative interval ledger
   */
  public Step advance(State initial, double durationSeconds) {
    requireState(initial);
    nonnegative(durationSeconds, "durationSeconds");
    double initialGasEnergy = initial.upstreamInternalEnergyJ + initial.downstreamInternalEnergyJ;
    double initialPigKinetic = kineticEnergy(initial.pigVelocityMps);
    State state = initial;
    double remaining = durationSeconds;
    double signedBypassMass = 0.0;
    double absoluteBypassMass = 0.0;
    double pressureWork = 0.0;
    double gasBoundaryWork = 0.0;
    double gravityWork = 0.0;
    double frictionHeat = 0.0;
    double impactEnergy = 0.0;
    double chokedSeconds = 0.0;
    double maxAcceptedDonorFraction = 0.0;
    double maxAcceptedTravelFraction = 0.0;
    int internalSteps = 0;
    while (remaining > 0.0) {
      if (++internalSteps > MAX_INTERNAL_STEPS) {
        throw new IllegalStateException("Coupled pig step exceeded the internal-step safeguard");
      }
      if (state.location == Location.RECEIVER) {
        state = state.withElapsed(state.elapsedTimeSeconds + remaining);
        remaining = 0.0;
        break;
      }
      Conditions conditions = conditions(state);
      Bypass bypass = bypass(conditions, state);
      double acceptedDuration = Math.min(remaining, maximumSubstepSeconds);
      if (bypass.absoluteMassRateKgPerS > 0.0) {
        acceptedDuration = Math.min(acceptedDuration,
            maximumDonorMassFraction * bypass.donorMassKg / bypass.absoluteMassRateKgPerS);
      }
      PigMotion.Step motion = mechanics.advance(state.pigPositionM, state.pigVelocityMps,
          conditions.upstreamPressurePa - conditions.downstreamPressurePa, inclinationRadians, acceptedDuration);
      while (motion.getDistanceM() > maximumPigTravelFraction * pipeLengthM
          && acceptedDuration > Math.ulp(state.elapsedTimeSeconds + acceptedDuration)) {
        acceptedDuration *= 0.5;
        motion = mechanics.advance(state.pigPositionM, state.pigVelocityMps,
            conditions.upstreamPressurePa - conditions.downstreamPressurePa, inclinationRadians, acceptedDuration);
      }
      if (motion.getDistanceM() > maximumPigTravelFraction * pipeLengthM * (1.0 + 1.0e-12)) {
        throw new IllegalStateException("Pig travel safeguard cannot be resolved at the current elapsed time");
      }
      if (motion.getStopTimeSeconds() > 0.0 && motion.getStopTimeSeconds() < acceptedDuration) {
        acceptedDuration = motion.getStopTimeSeconds();
        motion = mechanics.advance(state.pigPositionM, state.pigVelocityMps,
            conditions.upstreamPressurePa - conditions.downstreamPressurePa, inclinationRadians, acceptedDuration);
      }
      boolean launcherContact = motion.getPositionM() < 0.0;
      boolean receiverContact = motion.getPositionM() > pipeLengthM;
      if (state.location == Location.LAUNCHER && state.pigVelocityMps == 0.0 && launcherContact) {
        motion = null;
      } else if (launcherContact || receiverContact) {
        double boundary = launcherContact ? 0.0 : pipeLengthM;
        acceptedDuration = eventDuration(state, conditions, acceptedDuration, boundary);
        motion = mechanics.advance(state.pigPositionM, state.pigVelocityMps,
            conditions.upstreamPressurePa - conditions.downstreamPressurePa, inclinationRadians, acceptedDuration);
      }
      if (!(acceptedDuration > 0.0) || state.elapsedTimeSeconds + acceptedDuration == state.elapsedTimeSeconds) {
        throw new IllegalStateException("Accepted coupling substep is below elapsed-time resolution");
      }
      double displacement = motion == null ? 0.0 : motion.getDisplacementM();
      double finalPosition = motion == null ? state.pigPositionM : motion.getPositionM();
      Location finalLocation = finalPosition <= EVENT_POSITION_TOLERANCE_M ? Location.LAUNCHER
          : finalPosition >= pipeLengthM - EVENT_POSITION_TOLERANCE_M ? Location.RECEIVER : Location.PIPE;
      if (finalLocation == Location.LAUNCHER) {
        finalPosition = 0.0;
      } else if (finalLocation == Location.RECEIVER) {
        finalPosition = pipeLengthM;
      }
      double finalVelocity = motion == null ? 0.0 : motion.getVelocityMps();
      double stageImpact = 0.0;
      if ((finalLocation == Location.LAUNCHER || finalLocation == Location.RECEIVER) && finalVelocity != 0.0) {
        stageImpact = kineticEnergy(finalVelocity);
        finalVelocity = 0.0;
      }
      double transferredMass = bypass.signedMassRateKgPerS * acceptedDuration;
      double donorFraction = bypass.donorMassKg == 0.0 ? 0.0 : Math.abs(transferredMass) / bypass.donorMassKg;
      if (donorFraction > maximumDonorMassFraction * (1.0 + 1.0e-12)) {
        throw new IllegalStateException("Accepted bypass transfer exceeded the configured donor fraction");
      }
      double upstreamMass = state.upstreamMassKg - transferredMass;
      double downstreamMass = state.downstreamMassKg + transferredMass;
      double upstreamEnergy = state.upstreamInternalEnergyJ
          - conditions.upstreamPressurePa * mechanics.getPressureAreaM2() * displacement;
      double downstreamEnergy = state.downstreamInternalEnergyJ
          + conditions.downstreamPressurePa * mechanics.getPressureAreaM2() * displacement;
      if (transferredMass > 0.0) {
        double enthalpy = gamma * state.upstreamInternalEnergyJ / state.upstreamMassKg;
        upstreamEnergy -= enthalpy * transferredMass;
        downstreamEnergy += enthalpy * transferredMass;
      } else if (transferredMass < 0.0) {
        double enthalpy = gamma * state.downstreamInternalEnergyJ / state.downstreamMassKg;
        upstreamEnergy -= enthalpy * transferredMass;
        downstreamEnergy += enthalpy * transferredMass;
      }
      requirePositiveInventory(upstreamMass, downstreamMass, upstreamEnergy, downstreamEnergy);
      state = new State(state.elapsedTimeSeconds + acceptedDuration, finalPosition, finalVelocity, upstreamMass,
          downstreamMass, upstreamEnergy, downstreamEnergy, finalLocation);
      signedBypassMass += transferredMass;
      absoluteBypassMass += Math.abs(transferredMass);
      if (bypass.choked) {
        chokedSeconds += acceptedDuration;
      }
      maxAcceptedDonorFraction = Math.max(maxAcceptedDonorFraction, donorFraction);
      maxAcceptedTravelFraction = Math.max(maxAcceptedTravelFraction,
          motion == null ? 0.0 : motion.getDistanceM() / pipeLengthM);
      if (motion != null) {
        pressureWork += motion.getPressureWorkJ();
        gasBoundaryWork -= motion.getPressureWorkJ();
        gravityWork += motion.getGravityWorkJ();
        frictionHeat += motion.getFrictionHeatJ();
      }
      impactEnergy += stageImpact;
      remaining -= acceptedDuration;
      if (remaining < Math.ulp(durationSeconds)) {
        remaining = 0.0;
      }
    }
    double finalGasEnergy = state.upstreamInternalEnergyJ + state.downstreamInternalEnergyJ;
    double finalPigKinetic = kineticEnergy(state.pigVelocityMps);
    double totalMassResidual = state.upstreamMassKg + state.downstreamMassKg - initial.upstreamMassKg
        - initial.downstreamMassKg;
    double combinedEnergyResidual = finalGasEnergy - initialGasEnergy + finalPigKinetic - initialPigKinetic
        - gravityWork + frictionHeat + impactEnergy;
    return new Step(durationSeconds, state, signedBypassMass, absoluteBypassMass, pressureWork, gasBoundaryWork,
        gravityWork, frictionHeat, impactEnergy, chokedSeconds, totalMassResidual, combinedEnergyResidual,
        maxAcceptedDonorFraction, maxAcceptedTravelFraction, internalSteps);
  }

  /** @return launcher-to-receiver distance in m */
  public double getPipeLengthM() {
    return pipeLengthM;
  }

  /** @return swept pipe area in m2 */
  public double getPipeAreaM2() {
    return mechanics.getPressureAreaM2();
  }

  /** @return fixed calorically perfect heat-capacity ratio */
  public double getGamma() {
    return gamma;
  }

  /**
   * Calculates the upstream pressure represented by a state.
   *
   * @param state accepted coupled state
   * @return upstream absolute pressure in Pa
   */
  public double getUpstreamPressurePa(State state) {
    requireState(state);
    return (gamma - 1.0) * state.upstreamInternalEnergyJ / upstreamVolume(state.pigPositionM);
  }

  /**
   * Calculates the downstream pressure represented by a state.
   *
   * @param state accepted coupled state
   * @return downstream absolute pressure in Pa
   */
  public double getDownstreamPressurePa(State state) {
    requireState(state);
    return (gamma - 1.0) * state.downstreamInternalEnergyJ / downstreamVolume(state.pigPositionM);
  }

  /**
   * Calculates the upstream gas temperature represented by a state.
   *
   * @param state accepted coupled state
   * @return upstream temperature in K
   */
  public double getUpstreamTemperatureK(State state) {
    requireState(state);
    return state.upstreamInternalEnergyJ / (state.upstreamMassKg * specificHeatCvJPerKgK);
  }

  /**
   * Calculates the downstream gas temperature represented by a state.
   *
   * @param state accepted coupled state
   * @return downstream temperature in K
   */
  public double getDownstreamTemperatureK(State state) {
    requireState(state);
    return state.downstreamInternalEnergyJ / (state.downstreamMassKg * specificHeatCvJPerKgK);
  }

  /**
   * Calculates current pressures, temperatures and volumes.
   *
   * @param state accepted coupled state
   * @return checked intensive conditions
   */
  private Conditions conditions(State state) {
    double upstreamPressure = getUpstreamPressurePa(state);
    double downstreamPressure = getDownstreamPressurePa(state);
    double upstreamTemperature = getUpstreamTemperatureK(state);
    double downstreamTemperature = getDownstreamTemperatureK(state);
    positive(upstreamPressure, "calculated upstream pressure");
    positive(downstreamPressure, "calculated downstream pressure");
    positive(upstreamTemperature, "calculated upstream temperature");
    positive(downstreamTemperature, "calculated downstream temperature");
    return new Conditions(upstreamPressure, downstreamPressure, upstreamTemperature, downstreamTemperature);
  }

  /**
   * Evaluates signed ideal-gas bypass at the beginning of a coupling substep.
   *
   * @param conditions current intensive conditions
   * @param state current conservative state
   * @return signed bypass rate and donor diagnostics
   */
  private Bypass bypass(Conditions conditions, State state) {
    if (bypassDiameterM == 0.0 || conditions.upstreamPressurePa == conditions.downstreamPressurePa) {
      return new Bypass(0.0, 0.0, false,
          conditions.upstreamPressurePa >= conditions.downstreamPressurePa ? state.upstreamMassKg
              : state.downstreamMassKg);
    }
    boolean forward = conditions.upstreamPressurePa > conditions.downstreamPressurePa;
    double donorPressure = forward ? conditions.upstreamPressurePa : conditions.downstreamPressurePa;
    double receiverPressure = forward ? conditions.downstreamPressurePa : conditions.upstreamPressurePa;
    double donorTemperature = forward ? conditions.upstreamTemperatureK : conditions.downstreamTemperatureK;
    SystemInterface donor = gasTemplate.clone();
    donor.setPressure(donorPressure / 1.0e5);
    donor.setTemperature(donorTemperature);
    ReleaseFlowResult result = bypassModel
        .calculate(new ReleaseFlowRequest(donor, bypassDiameterM, bypassDischargeCoefficient, receiverPressure));
    if (!result.isUsable()) {
      throw new IllegalStateException("Ideal-gas bypass failed: " + result.getStatus());
    }
    double rate = result.getMassFlowRateKgS();
    return new Bypass(forward ? rate : -rate, rate, result.isChoked(),
        forward ? state.upstreamMassKg : state.downstreamMassKg);
  }

  /**
   * Locates the first launcher or receiver contact for a constant-input mechanical substep.
   *
   * @param state initial state
   * @param conditions beginning-of-step pressures
   * @param upperDuration duration known to place the endpoint outside the pipe
   * @param boundaryM launcher or receiver coordinate in m
   * @return event duration in s
   */
  private double eventDuration(State state, Conditions conditions, double upperDuration, double boundaryM) {
    double lower = 0.0;
    double upper = upperDuration;
    boolean receiver = boundaryM == pipeLengthM;
    for (int i = 0; i < EVENT_BISECTION_ITERATIONS; i++) {
      double middle = 0.5 * (lower + upper);
      double position = mechanics
          .advance(state.pigPositionM, state.pigVelocityMps,
              conditions.upstreamPressurePa - conditions.downstreamPressurePa, inclinationRadians, middle)
          .getPositionM();
      if ((receiver && position >= boundaryM) || (!receiver && position <= boundaryM)) {
        upper = middle;
      } else {
        lower = middle;
      }
    }
    return upper;
  }

  /**
   * Checks that a state belongs to this geometry and has positive inventories.
   *
   * @param state state to check
   */
  private void requireState(State state) {
    if (state == null || !Double.isFinite(state.elapsedTimeSeconds) || state.elapsedTimeSeconds < 0.0
        || !Double.isFinite(state.pigPositionM) || state.pigPositionM < 0.0 || state.pigPositionM > pipeLengthM
        || !Double.isFinite(state.pigVelocityMps) || state.location == null
        || (state.location == Location.RECEIVER && state.pigPositionM != pipeLengthM)
        || (state.location == Location.LAUNCHER && state.pigPositionM != 0.0)) {
      throw new IllegalArgumentException("State is incompatible with this pigging geometry");
    }
    requirePositiveInventory(state.upstreamMassKg, state.downstreamMassKg, state.upstreamInternalEnergyJ,
        state.downstreamInternalEnergyJ);
  }

  /**
   * Checks positive gas mass and energy in both chambers.
   *
   * @param upstreamMass upstream gas mass in kg
   * @param downstreamMass downstream gas mass in kg
   * @param upstreamEnergy upstream internal energy in J
   * @param downstreamEnergy downstream internal energy in J
   */
  private static void requirePositiveInventory(double upstreamMass, double downstreamMass, double upstreamEnergy,
      double downstreamEnergy) {
    positive(upstreamMass, "upstream gas mass");
    positive(downstreamMass, "downstream gas mass");
    positive(upstreamEnergy, "upstream gas energy");
    positive(downstreamEnergy, "downstream gas energy");
  }

  /**
   * Creates an initialized defensive gas template.
   *
   * @param source caller gas
   * @return initialized single-gas-phase clone
   */
  private static SystemInterface initialiseGas(SystemInterface source) {
    SystemInterface gas = source.clone();
    try {
      new ThermodynamicOperations(gas).TPflash();
      gas.init(3);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("Gas template could not be initialized", ex);
    }
    if (gas.getNumberOfPhases() != 1 || gas.getPhase(0).getType() != PhaseType.GAS || gas.isChemicalSystem()
        || gas.isForcePhaseTypes() || gas.doSolidPhaseCheck() || gas.getHydrateCheck()) {
      throw new IllegalArgumentException(
          "A single, nonreacting gas phase without forced/solid/hydrate checks is required");
    }
    return gas;
  }

  /**
   * Calculates pig kinetic energy.
   *
   * @param velocityMps signed pig velocity in m/s
   * @return kinetic energy in J
   */
  private double kineticEnergy(double velocityMps) {
    return 0.5 * mechanics.getMassKg() * velocityMps * velocityMps;
  }

  /**
   * Calculates the upstream control-volume size.
   *
   * @param positionM pig position in m
   * @return upstream volume in m3
   */
  private double upstreamVolume(double positionM) {
    return launcherVolumeM3 + mechanics.getPressureAreaM2() * positionM;
  }

  /**
   * Calculates the downstream control-volume size.
   *
   * @param positionM pig position in m
   * @return downstream volume in m3
   */
  private double downstreamVolume(double positionM) {
    return receiverVolumeM3 + mechanics.getPressureAreaM2() * (pipeLengthM - positionM);
  }

  /**
   * Requires a finite positive number.
   *
   * @param value value to check
   * @param name diagnostic name
   */
  private static void positive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  /**
   * Requires a finite nonnegative number.
   *
   * @param value value to check
   * @param name diagnostic name
   */
  private static void nonnegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
  }

  /**
   * Immutable conservative coupled state.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class State implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double elapsedTimeSeconds;
    private final double pigPositionM;
    private final double pigVelocityMps;
    private final double upstreamMassKg;
    private final double downstreamMassKg;
    private final double upstreamInternalEnergyJ;
    private final double downstreamInternalEnergyJ;
    private final Location location;

    /**
     * Retains a checked state owned by the coupled model.
     *
     * @param elapsed elapsed time in s
     * @param position pig position in m
     * @param velocity pig velocity in m/s
     * @param upstreamMass upstream mass in kg
     * @param downstreamMass downstream mass in kg
     * @param upstreamEnergy upstream internal energy in J
     * @param downstreamEnergy downstream internal energy in J
     * @param pigLocation discrete location
     */
    private State(double elapsed, double position, double velocity, double upstreamMass, double downstreamMass,
        double upstreamEnergy, double downstreamEnergy, Location pigLocation) {
      elapsedTimeSeconds = elapsed;
      pigPositionM = position;
      pigVelocityMps = velocity;
      upstreamMassKg = upstreamMass;
      downstreamMassKg = downstreamMass;
      upstreamInternalEnergyJ = upstreamEnergy;
      downstreamInternalEnergyJ = downstreamEnergy;
      location = pigLocation;
    }

    /** @return elapsed simulation time in s */
    public double getElapsedTimeSeconds() {
      return elapsedTimeSeconds;
    }

    /** @return pig position in m */
    public double getPigPositionM() {
      return pigPositionM;
    }

    /** @return signed pig velocity in m/s */
    public double getPigVelocityMps() {
      return pigVelocityMps;
    }

    /** @return upstream gas mass in kg */
    public double getUpstreamMassKg() {
      return upstreamMassKg;
    }

    /** @return downstream gas mass in kg */
    public double getDownstreamMassKg() {
      return downstreamMassKg;
    }

    /** @return upstream gas internal energy in J */
    public double getUpstreamInternalEnergyJ() {
      return upstreamInternalEnergyJ;
    }

    /** @return downstream gas internal energy in J */
    public double getDownstreamInternalEnergyJ() {
      return downstreamInternalEnergyJ;
    }

    /** @return launcher, pipe or receiver location */
    public Location getLocation() {
      return location;
    }

    /**
     * Returns this state at a later elapsed time without changing physical inventories.
     *
     * @param elapsed later elapsed time in s
     * @return copied state
     */
    private State withElapsed(double elapsed) {
      return new State(elapsed, pigPositionM, pigVelocityMps, upstreamMassKg, downstreamMassKg, upstreamInternalEnergyJ,
          downstreamInternalEnergyJ, location);
    }
  }

  /**
   * Immutable accepted-interval ledger.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Step implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double durationSeconds;
    private final State finalState;
    private final double signedBypassMassKg;
    private final double absoluteBypassMassKg;
    private final double pressureWorkJ;
    private final double gasBoundaryWorkJ;
    private final double gravityWorkJ;
    private final double frictionHeatJ;
    private final double contactImpactEnergyJ;
    private final double chokedSeconds;
    private final double totalMassResidualKg;
    private final double combinedEnergyResidualJ;
    private final double maximumAcceptedDonorMassFraction;
    private final double maximumAcceptedPigTravelFraction;
    private final int internalStepCount;

    /**
     * Creates an immutable accepted-interval ledger.
     *
     * @param duration requested duration in s
     * @param state final state
     * @param signedBypass signed upstream-to-downstream bypass mass in kg
     * @param absoluteBypass absolute bypass mass in kg
     * @param pressureWork pressure work into the pig in J
     * @param gasWork moving-boundary work into both gases in J
     * @param gravityWork gravity work into the pig in J
     * @param frictionHeat sliding-friction dissipation in J
     * @param impactEnergy launcher/receiver impact dissipation in J
     * @param chokedTime cumulative choked bypass time in s
     * @param massResidual total gas mass residual in kg
     * @param energyResidual combined gas/pig/potential/dissipation residual in J
     * @param donorFraction maximum accepted donor mass fraction
     * @param travelFraction maximum accepted pig travel fraction
     * @param stepCount internal accepted substep count
     */
    private Step(double duration, State state, double signedBypass, double absoluteBypass, double pressureWork,
        double gasWork, double gravityWork, double frictionHeat, double impactEnergy, double chokedTime,
        double massResidual, double energyResidual, double donorFraction, double travelFraction, int stepCount) {
      durationSeconds = duration;
      finalState = state;
      signedBypassMassKg = signedBypass;
      absoluteBypassMassKg = absoluteBypass;
      pressureWorkJ = pressureWork;
      gasBoundaryWorkJ = gasWork;
      gravityWorkJ = gravityWork;
      frictionHeatJ = frictionHeat;
      contactImpactEnergyJ = impactEnergy;
      chokedSeconds = chokedTime;
      totalMassResidualKg = massResidual;
      combinedEnergyResidualJ = energyResidual;
      maximumAcceptedDonorMassFraction = donorFraction;
      maximumAcceptedPigTravelFraction = travelFraction;
      internalStepCount = stepCount;
    }

    /** @return requested duration in s */
    public double getDurationSeconds() {
      return durationSeconds;
    }

    /** @return immutable final state */
    public State getFinalState() {
      return finalState;
    }

    /** @return signed upstream-to-downstream bypass mass in kg */
    public double getSignedBypassMassKg() {
      return signedBypassMassKg;
    }

    /** @return absolute bypass mass in kg */
    public double getAbsoluteBypassMassKg() {
      return absoluteBypassMassKg;
    }

    /** @return pressure work into the pig in J */
    public double getPressureWorkJ() {
      return pressureWorkJ;
    }

    /** @return moving-boundary work into the gas inventories in J */
    public double getGasBoundaryWorkJ() {
      return gasBoundaryWorkJ;
    }

    /** @return gravity work into the pig in J */
    public double getGravityWorkJ() {
      return gravityWorkJ;
    }

    /** @return nonnegative sliding-friction dissipation in J */
    public double getFrictionHeatJ() {
      return frictionHeatJ;
    }

    /** @return nonnegative launcher/receiver impact dissipation in J */
    public double getContactImpactEnergyJ() {
      return contactImpactEnergyJ;
    }

    /** @return cumulative choked bypass time in s */
    public double getChokedSeconds() {
      return chokedSeconds;
    }

    /** @return total gas mass residual in kg */
    public double getTotalMassResidualKg() {
      return totalMassResidualKg;
    }

    /** @return combined gas, pig, potential and dissipation energy residual in J */
    public double getCombinedEnergyResidualJ() {
      return combinedEnergyResidualJ;
    }

    /** @return maximum accepted bypass transfer divided by beginning donor mass */
    public double getMaximumAcceptedDonorMassFraction() {
      return maximumAcceptedDonorMassFraction;
    }

    /** @return maximum accepted pig travel divided by pipe length */
    public double getMaximumAcceptedPigTravelFraction() {
      return maximumAcceptedPigTravelFraction;
    }

    /** @return number of accepted internal substeps */
    public int getInternalStepCount() {
      return internalStepCount;
    }
  }

  /**
   * Beginning-of-stage intensive conditions.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class Conditions {
    private final double upstreamPressurePa;
    private final double downstreamPressurePa;
    private final double upstreamTemperatureK;
    private final double downstreamTemperatureK;

    /**
     * @param upstreamPressure upstream pressure in Pa
     * @param downstreamPressure downstream pressure in Pa
     * @param upstreamTemperature upstream temperature in K
     * @param downstreamTemperature downstream temperature in K
     */
    private Conditions(double upstreamPressure, double downstreamPressure, double upstreamTemperature,
        double downstreamTemperature) {
      upstreamPressurePa = upstreamPressure;
      downstreamPressurePa = downstreamPressure;
      upstreamTemperatureK = upstreamTemperature;
      downstreamTemperatureK = downstreamTemperature;
    }
  }

  /**
   * Beginning-of-stage bypass result.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class Bypass {
    private final double signedMassRateKgPerS;
    private final double absoluteMassRateKgPerS;
    private final boolean choked;
    private final double donorMassKg;

    /**
     * @param signedRate signed upstream-to-downstream rate in kg/s
     * @param absoluteRate absolute rate in kg/s
     * @param isChoked whether the accepted orifice flow is choked
     * @param donorMass beginning donor mass in kg
     */
    private Bypass(double signedRate, double absoluteRate, boolean isChoked, double donorMass) {
      signedMassRateKgPerS = signedRate;
      absoluteMassRateKgPerS = absoluteRate;
      choked = isChoked;
      donorMassKg = donorMass;
    }
  }
}
