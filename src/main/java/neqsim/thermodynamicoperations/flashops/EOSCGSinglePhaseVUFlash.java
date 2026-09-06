package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.gerg.NeqSimEOSCG;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Solves a pure EOS-CG single-phase VU state at the density imposed by its volume. */
final class EOSCGSinglePhaseVUFlash {
  private static final Logger logger = LogManager.getLogger(EOSCGSinglePhaseVUFlash.class);
  private static final int MAXIMUM_ROOT_ITERATIONS = 80;
  private static final int BRACKET_SEARCH_ITERATIONS = 24;
  private static final double MINIMUM_TEMPERATURE = 50.0;
  private static final double MAXIMUM_TEMPERATURE = 5000.0;

  private final SystemInterface system;
  private final double targetVolume;
  private final double targetInternalEnergy;

  /**
   * Create the fixed-density single-phase solver.
   *
   * @param system pure-component EOS-CG system
   * @param targetVolume specified volume in NeqSim internal volume units
   * @param targetInternalEnergy specified total internal energy in J
   */
  EOSCGSinglePhaseVUFlash(SystemInterface system, double targetVolume, double targetInternalEnergy) {
    this.system = system;
    this.targetVolume = targetVolume;
    this.targetInternalEnergy = targetInternalEnergy;
  }

  /**
   * Solve and apply a stable homogeneous state.
   *
   * @return {@code true} when a stable single-phase state satisfies both specifications
   */
  boolean solve() {
    double initialTemperature = clampTemperature(system.getTemperature());
    Candidate initialCandidate = evaluate(initialTemperature);
    if (initialCandidate == null) {
      return false;
    }
    if (meetsEnergySpecification(initialCandidate)) {
      return applyIfStable(initialCandidate);
    }

    int preferredDirection = initialCandidate.energyResidual > 0.0 ? -1 : 1;
    Bracket bracket = findBracket(initialCandidate, preferredDirection);
    if (bracket == null) {
      bracket = findBracket(initialCandidate, -preferredDirection);
    }
    if (bracket == null) {
      return false;
    }

    Candidate bestCandidate = Math.abs(bracket.lower.energyResidual) <= Math.abs(bracket.upper.energyResidual)
        ? bracket.lower
        : bracket.upper;
    for (int iteration = 0; iteration < MAXIMUM_ROOT_ITERATIONS; iteration++) {
      Candidate candidate = evaluate(interpolateTemperature(bracket));
      if (candidate == null) {
        return false;
      }
      if (Math.abs(candidate.energyResidual) < Math.abs(bestCandidate.energyResidual)) {
        bestCandidate = candidate;
      }
      if (meetsEnergySpecification(candidate)) {
        return applyIfStable(candidate);
      }
      if (sameResidualSign(bracket.lower, candidate)) {
        bracket = new Bracket(candidate, bracket.upper);
      } else {
        bracket = new Bracket(bracket.lower, candidate);
      }
    }

    return meetsEnergySpecification(bestCandidate) && applyIfStable(bestCandidate);
  }

  private Bracket findBracket(Candidate initialCandidate, int direction) {
    Candidate previousCandidate = initialCandidate;
    double step = Math.max(1.0, 0.02 * initialCandidate.temperature);
    for (int iteration = 0; iteration < BRACKET_SEARCH_ITERATIONS; iteration++) {
      double temperature = clampTemperature(initialCandidate.temperature + direction * step);
      if (temperature == previousCandidate.temperature) {
        break;
      }
      Candidate candidate = evaluate(temperature);
      if (candidate == null) {
        candidate = refineBranchBoundary(previousCandidate, temperature);
        if (candidate != null && !sameResidualSign(initialCandidate, candidate)) {
          return orderedBracket(initialCandidate, candidate);
        }
        break;
      }
      if (!sameResidualSign(initialCandidate, candidate)) {
        return orderedBracket(initialCandidate, candidate);
      }
      previousCandidate = candidate;
      step *= 1.8;
    }
    return null;
  }

  private Candidate refineBranchBoundary(Candidate validCandidate, double invalidTemperature) {
    Candidate boundaryCandidate = validCandidate;
    double invalidBoundary = invalidTemperature;
    for (int iteration = 0; iteration < MAXIMUM_ROOT_ITERATIONS; iteration++) {
      double temperature = 0.5 * (boundaryCandidate.temperature + invalidBoundary);
      if (temperature == boundaryCandidate.temperature || temperature == invalidBoundary) {
        break;
      }
      Candidate candidate = evaluate(temperature);
      if (candidate == null) {
        invalidBoundary = temperature;
      } else {
        boundaryCandidate = candidate;
      }
    }
    return boundaryCandidate;
  }

  private Candidate evaluate(double temperature) {
    try {
      SystemInterface trialSystem = system.clone();
      trialSystem.setTemperature(temperature);
      double totalMoles = trialSystem.getTotalNumberOfMoles();
      double molarDensityMolPerL = 100.0 * totalMoles / targetVolume;
      double[] properties = new NeqSimEOSCG(trialSystem.getPhase(0)).propertiesEOSCG(molarDensityMolPerL);
      double pressureBar = properties[0] / 100.0;
      double internalEnergy = properties[6] * totalMoles;
      if (!Double.isFinite(internalEnergy) || !Double.isFinite(pressureBar) || pressureBar <= 0.0) {
        return null;
      }
      return new Candidate(temperature, pressureBar, internalEnergy - targetInternalEnergy);
    } catch (RuntimeException exception) {
      logger.debug("Fixed-volume EOS-CG candidate failed at {} K", temperature, exception);
      return null;
    }
  }

  private boolean applyIfStable(Candidate candidate) {
    if (!isStable(candidate)) {
      return false;
    }
    try {
      system.setTemperature(candidate.temperature);
      system.setPressure(candidate.pressureBar);
      new TPflash(system).run();
      system.init(3);
      return specificationsAreSatisfied(system);
    } catch (RuntimeException exception) {
      logger.debug("Converged EOS-CG candidate could not be applied", exception);
      return false;
    }
  }

  private boolean isStable(Candidate candidate) {
    if (!outsideSaturationVolumeEnvelope(candidate)) {
      return false;
    }
    try {
      SystemInterface trialSystem = system.clone();
      trialSystem.setTemperature(candidate.temperature);
      trialSystem.setPressure(candidate.pressureBar);
      new TPflash(trialSystem).run();
      trialSystem.init(3);
      return trialSystem.getNumberOfPhases() == 1 && specificationsAreSatisfied(trialSystem);
    } catch (RuntimeException exception) {
      logger.debug("EOS-CG single-phase stability check failed", exception);
      return false;
    }
  }

  private boolean outsideSaturationVolumeEnvelope(Candidate candidate) {
    double triplePressureBar = system.getPhase(0).getComponent(0).getTriplePointPressure();
    double criticalPressureBar = system.getPhase(0).getComponent(0).getPC();
    if (candidate.pressureBar <= triplePressureBar || candidate.pressureBar >= criticalPressureBar) {
      return true;
    }
    try {
      SystemInterface saturationSystem = system.clone();
      saturateAtPressure(saturationSystem, candidate.pressureBar);
      int gasPhaseIndex = phaseIndex(saturationSystem, PhaseType.GAS);
      int liquidPhaseIndex = otherPhaseIndex(saturationSystem, gasPhaseIndex);
      double gasMolarVolume = saturationSystem.getPhase(gasPhaseIndex).getMolarVolume();
      double liquidMolarVolume = saturationSystem.getPhase(liquidPhaseIndex).getMolarVolume();
      double lowerMolarVolume = Math.min(gasMolarVolume, liquidMolarVolume);
      double upperMolarVolume = Math.max(gasMolarVolume, liquidMolarVolume);
      double rootSeparationTolerance = VUflashPureEOSCG.SPECIFICATION_RELATIVE_TOLERANCE
          * Math.max(upperMolarVolume, 1.0e-30);
      if (upperMolarVolume - lowerMolarVolume <= rootSeparationTolerance) {
        return false;
      }
      double targetMolarVolume = targetVolume / system.getTotalNumberOfMoles();
      double tolerance = VUflashPureEOSCG.SPECIFICATION_RELATIVE_TOLERANCE
          * Math.max(Math.max(Math.abs(targetMolarVolume), upperMolarVolume), 1.0e-30);
      return targetMolarVolume < lowerMolarVolume - tolerance || targetMolarVolume > upperMolarVolume + tolerance;
    } catch (Exception exception) {
      logger.debug("Saturation-volume check failed at {} bar", candidate.pressureBar, exception);
      return false;
    }
  }

  private boolean meetsEnergySpecification(Candidate candidate) {
    double tolerance = VUflashPureEOSCG.SPECIFICATION_RELATIVE_TOLERANCE
        * Math.max(Math.abs(targetInternalEnergy), 1.0);
    return Math.abs(candidate.energyResidual) <= tolerance;
  }

  private boolean specificationsAreSatisfied(SystemInterface targetSystem) {
    double volumeResidual = Math.abs(targetSystem.getVolume() - targetVolume)
        / Math.max(Math.abs(targetVolume), 1.0e-30);
    double energyResidual = Math.abs(targetSystem.getInternalEnergy() - targetInternalEnergy)
        / Math.max(Math.abs(targetInternalEnergy), 1.0);
    return Double.isFinite(volumeResidual) && Double.isFinite(energyResidual)
        && volumeResidual <= VUflashPureEOSCG.SPECIFICATION_RELATIVE_TOLERANCE
        && energyResidual <= VUflashPureEOSCG.SPECIFICATION_RELATIVE_TOLERANCE;
  }

  private static double interpolateTemperature(Bracket bracket) {
    double temperatureRange = bracket.upper.temperature - bracket.lower.temperature;
    double residualRange = bracket.upper.energyResidual - bracket.lower.energyResidual;
    double temperature = bracket.lower.temperature - bracket.lower.energyResidual * temperatureRange / residualRange;
    double margin = 1.0e-12 * Math.max(Math.abs(temperatureRange), 1.0);
    if (!Double.isFinite(temperature) || temperature <= bracket.lower.temperature + margin
        || temperature >= bracket.upper.temperature - margin) {
      return 0.5 * (bracket.lower.temperature + bracket.upper.temperature);
    }
    return temperature;
  }

  private static void saturateAtPressure(SystemInterface targetSystem, double pressureBar) throws Exception {
    targetSystem.setPressure(pressureBar);
    new ThermodynamicOperations(targetSystem).bubblePointTemperatureFlash();
    targetSystem.init(3);
  }

  private static int phaseIndex(SystemInterface targetSystem, PhaseType phaseType) {
    for (int phaseIndex = 0; phaseIndex < targetSystem.getNumberOfPhases(); phaseIndex++) {
      if (targetSystem.getPhase(phaseIndex).getType() == phaseType) {
        return phaseIndex;
      }
    }
    throw new IllegalStateException("Saturation flash is missing phase type " + phaseType);
  }

  private static int otherPhaseIndex(SystemInterface targetSystem, int phaseIndex) {
    for (int candidate = 0; candidate < targetSystem.getNumberOfPhases(); candidate++) {
      if (candidate != phaseIndex) {
        return candidate;
      }
    }
    throw new IllegalStateException("Saturation flash is missing a second phase");
  }

  private static Bracket orderedBracket(Candidate first, Candidate second) {
    return first.temperature <= second.temperature ? new Bracket(first, second) : new Bracket(second, first);
  }

  private static boolean sameResidualSign(Candidate first, Candidate second) {
    return Math.signum(first.energyResidual) == Math.signum(second.energyResidual);
  }

  private static double clampTemperature(double temperature) {
    return Math.max(MINIMUM_TEMPERATURE, Math.min(MAXIMUM_TEMPERATURE, temperature));
  }

  private static final class Candidate {
    private final double temperature;
    private final double pressureBar;
    private final double energyResidual;

    private Candidate(double temperature, double pressureBar, double energyResidual) {
      this.temperature = temperature;
      this.pressureBar = pressureBar;
      this.energyResidual = energyResidual;
    }
  }

  private static final class Bracket {
    private final Candidate lower;
    private final Candidate upper;

    private Bracket(Candidate lower, Candidate upper) {
      this.lower = lower;
      this.upper = upper;
    }
  }
}
