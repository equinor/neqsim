package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Solves a pure EOS-CG two-phase VU state along its saturation line. */
final class EOSCGSaturationVUFlash {
  private static final Logger logger = LogManager.getLogger(EOSCGSaturationVUFlash.class);
  private static final int SATURATION_SCAN_INTERVALS = 32;
  private static final int LOCAL_SCAN_INTERVALS = 24;
  private static final int MAXIMUM_ROOT_ITERATIONS = 80;

  private final SystemInterface system;
  private final double targetVolume;
  private final double targetInternalEnergy;

  /**
   * Create the saturation-line solver.
   *
   * @param system pure-component EOS-CG system
   * @param targetVolume specified volume in NeqSim internal volume units
   * @param targetInternalEnergy specified total internal energy in J
   */
  EOSCGSaturationVUFlash(SystemInterface system, double targetVolume, double targetInternalEnergy) {
    this.system = system;
    this.targetVolume = targetVolume;
    this.targetInternalEnergy = targetInternalEnergy;
  }

  /**
   * Solve and apply a two-phase state satisfying the volume and energy specifications.
   *
   * @return {@code true} when a physical saturation-line solution is found
   */
  boolean solve() {
    if (system.getPhase(0).getNumberOfComponents() != 1) {
      return false;
    }
    double totalMoles = system.getTotalNumberOfMoles();
    if (!Double.isFinite(totalMoles) || totalMoles <= 0.0) {
      return false;
    }

    double targetMolarVolume = targetVolume / totalMoles;
    double targetMolarEnergy = targetInternalEnergy / totalMoles;
    double triplePressureBar = system.getPhase(0).getComponent(0).getTriplePointPressure();
    double criticalPressureBar = system.getPhase(0).getComponent(0).getPC();
    double lowerPressureBar = Math.max(0.01, triplePressureBar + 1.0e-6);
    double upperPressureBar = criticalPressureBar - Math.max(1.0e-6, criticalPressureBar * 1.0e-8);
    if (!(lowerPressureBar < upperPressureBar)) {
      return false;
    }

    Candidate[] bracket = findBracket(lowerPressureBar, upperPressureBar, targetMolarVolume, targetMolarEnergy);
    if (bracket == null) {
      return false;
    }
    Candidate solution = refineBracket(bracket[0], bracket[1], targetMolarVolume, targetMolarEnergy);
    if (solution == null || !solution.physical) {
      return false;
    }

    apply(solution);
    validateSpecifications();
    return true;
  }

  private Candidate[] findBracket(double lowerPressureBar, double upperPressureBar, double targetMolarVolume,
      double targetMolarEnergy) {
    Candidate previousCandidate = null;
    double coarsePressureStep = (upperPressureBar - lowerPressureBar) / SATURATION_SCAN_INTERVALS;
    for (int interval = 0; interval <= SATURATION_SCAN_INTERVALS; interval++) {
      double fraction = interval / (double) SATURATION_SCAN_INTERVALS;
      double pressureBar = lowerPressureBar + fraction * (upperPressureBar - lowerPressureBar);
      Candidate candidate = evaluate(pressureBar, targetMolarVolume, targetMolarEnergy);
      if (candidate == null) {
        if (previousCandidate != null) {
          double localUpperPressureBar = Math.min(upperPressureBar, pressureBar + coarsePressureStep);
          Candidate[] localBracket = findLocalBracket(previousCandidate, localUpperPressureBar, targetMolarVolume,
              targetMolarEnergy);
          if (localBracket != null) {
            return localBracket;
          }
          previousCandidate = null;
        }
        continue;
      }
      if (withinVolumeTolerance(candidate, targetMolarVolume)) {
        return new Candidate[] { candidate, candidate };
      }
      if (!candidate.physical) {
        if (previousCandidate != null && residualSignChanged(previousCandidate, candidate)) {
          return new Candidate[] { previousCandidate, candidate };
        }
        previousCandidate = null;
        continue;
      }
      if (previousCandidate != null && residualSignChanged(previousCandidate, candidate)) {
        return new Candidate[] { previousCandidate, candidate };
      }
      previousCandidate = candidate;
    }
    return null;
  }

  private Candidate[] findLocalBracket(Candidate lowerCandidate, double upperPressureBar, double targetMolarVolume,
      double targetMolarEnergy) {
    Candidate previousPhysicalCandidate = lowerCandidate;
    double pressureRange = upperPressureBar - lowerCandidate.pressureBar;
    for (int interval = 1; interval <= LOCAL_SCAN_INTERVALS; interval++) {
      double pressureBar = lowerCandidate.pressureBar + interval / (double) LOCAL_SCAN_INTERVALS * pressureRange;
      Candidate candidate = evaluate(pressureBar, targetMolarVolume, targetMolarEnergy);
      if (candidate == null) {
        continue;
      }
      if (withinVolumeTolerance(candidate, targetMolarVolume)) {
        return new Candidate[] { candidate, candidate };
      }
      if (residualSignChanged(previousPhysicalCandidate, candidate)) {
        return new Candidate[] { previousPhysicalCandidate, candidate };
      }
      if (candidate.physical) {
        previousPhysicalCandidate = candidate;
      }
    }
    return null;
  }

  private Candidate refineBracket(Candidate lowerCandidate, Candidate upperCandidate, double targetMolarVolume,
      double targetMolarEnergy) {
    if (lowerCandidate == upperCandidate) {
      return lowerCandidate;
    }

    Candidate bestPhysicalCandidate = closerPhysicalCandidate(lowerCandidate, upperCandidate);
    for (int iteration = 0; iteration < MAXIMUM_ROOT_ITERATIONS; iteration++) {
      double pressureBar = 0.5 * (lowerCandidate.pressureBar + upperCandidate.pressureBar);
      Candidate candidate = evaluate(pressureBar, targetMolarVolume, targetMolarEnergy);
      if (candidate == null) {
        Candidate lowerBoundary = refineCandidateBoundary(lowerCandidate, pressureBar, targetMolarVolume,
            targetMolarEnergy);
        if (lowerBoundary != lowerCandidate && lowerBoundary.physical
            && residualSignChanged(lowerCandidate, lowerBoundary)) {
          upperCandidate = lowerBoundary;
          bestPhysicalCandidate = lowerBoundary;
          continue;
        }
        Candidate upperBoundary = refineCandidateBoundary(upperCandidate, pressureBar, targetMolarVolume,
            targetMolarEnergy);
        if (upperBoundary != upperCandidate && (upperBoundary.physical || upperCandidate.physical)
            && residualSignChanged(upperBoundary, upperCandidate)) {
          lowerCandidate = upperBoundary;
          if (upperBoundary.physical) {
            bestPhysicalCandidate = upperBoundary;
          }
          continue;
        }
        return null;
      }
      if (candidate.physical) {
        bestPhysicalCandidate = candidate;
      }
      if (candidate.physical && withinVolumeTolerance(candidate, targetMolarVolume)) {
        return candidate;
      }
      if (sameResidualSign(lowerCandidate, candidate)) {
        lowerCandidate = candidate;
      } else {
        upperCandidate = candidate;
      }
    }
    return bestPhysicalCandidate != null && withinVolumeTolerance(bestPhysicalCandidate, targetMolarVolume)
        ? bestPhysicalCandidate
        : null;
  }

  private Candidate refineCandidateBoundary(Candidate validCandidate, double invalidPressureBar,
      double targetMolarVolume, double targetMolarEnergy) {
    Candidate boundaryCandidate = validCandidate;
    double invalidBoundary = invalidPressureBar;
    for (int iteration = 0; iteration < MAXIMUM_ROOT_ITERATIONS; iteration++) {
      double pressureBar = 0.5 * (boundaryCandidate.pressureBar + invalidBoundary);
      if (pressureBar == boundaryCandidate.pressureBar || pressureBar == invalidBoundary) {
        break;
      }
      Candidate candidate = evaluate(pressureBar, targetMolarVolume, targetMolarEnergy);
      if (candidate == null) {
        invalidBoundary = pressureBar;
      } else {
        boundaryCandidate = candidate;
      }
    }
    return boundaryCandidate;
  }

  private Candidate evaluate(double pressureBar, double targetMolarVolume, double targetMolarEnergy) {
    try {
      SystemInterface trialSystem = system.clone();
      saturateAtPressure(trialSystem, pressureBar);
      if (trialSystem.getNumberOfPhases() != 2) {
        return null;
      }
      int gasPhaseIndex = phaseIndex(trialSystem, PhaseType.GAS);
      int liquidPhaseIndex = otherPhaseIndex(trialSystem, gasPhaseIndex);
      PhaseInterface gasPhase = trialSystem.getPhase(gasPhaseIndex);
      PhaseInterface liquidPhase = trialSystem.getPhase(liquidPhaseIndex);
      double gasEnergy = gasPhase.getInternalEnergy("J/mol");
      double liquidEnergy = liquidPhase.getInternalEnergy("J/mol");
      double energyDifference = gasEnergy - liquidEnergy;
      double energyScale = Math.max(Math.max(Math.abs(gasEnergy), Math.abs(liquidEnergy)), 1.0);
      if (!Double.isFinite(energyDifference) || Math.abs(energyDifference) < 1.0e-8 * energyScale) {
        return null;
      }
      double vaporFraction = (targetMolarEnergy - liquidEnergy) / energyDifference;
      if (!Double.isFinite(vaporFraction)) {
        return null;
      }
      boolean physical = vaporFraction >= 0.0 && vaporFraction <= 1.0;
      double molarVolume = vaporFraction * gasPhase.getMolarVolume()
          + (1.0 - vaporFraction) * liquidPhase.getMolarVolume();
      if (!Double.isFinite(molarVolume) || molarVolume <= 0.0) {
        return null;
      }
      return new Candidate(pressureBar, vaporFraction, molarVolume - targetMolarVolume, physical);
    } catch (Exception exception) {
      logger.debug("Pure-component saturation candidate failed at {} bar", pressureBar, exception);
      return null;
    }
  }

  private void apply(Candidate candidate) {
    try {
      saturateAtPressure(system, candidate.pressureBar);
    } catch (Exception exception) {
      throw new IllegalStateException("Pure-component saturation flash failed at " + candidate.pressureBar + " bar",
          exception);
    }
    int gasPhaseIndex = phaseIndex(system, PhaseType.GAS);
    int liquidPhaseIndex = otherPhaseIndex(system, gasPhaseIndex);
    system.setBeta(gasPhaseIndex, candidate.vaporFraction);
    system.setBeta(liquidPhaseIndex, 1.0 - candidate.vaporFraction);
    system.init(3);
  }

  private void validateSpecifications() {
    double volumeResidual = Math.abs(system.getVolume() - targetVolume) / Math.max(Math.abs(targetVolume), 1.0e-30);
    double energyResidual = Math.abs(system.getInternalEnergy() - targetInternalEnergy)
        / Math.max(Math.abs(targetInternalEnergy), 1.0);
    if (!Double.isFinite(volumeResidual) || !Double.isFinite(energyResidual)
        || volumeResidual > VUflashPureEOSCG.SPECIFICATION_RELATIVE_TOLERANCE
        || energyResidual > VUflashPureEOSCG.SPECIFICATION_RELATIVE_TOLERANCE) {
      throw new IllegalStateException("Pure-component saturation VU flash residual exceeds tolerance: volume="
          + volumeResidual + ", internalEnergy=" + energyResidual + ", targetVolume=" + targetVolume + ", actualVolume="
          + system.getVolume() + ", targetInternalEnergy=" + targetInternalEnergy + ", actualInternalEnergy="
          + system.getInternalEnergy());
    }
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

  private static boolean withinVolumeTolerance(Candidate candidate, double targetMolarVolume) {
    return candidate.physical
        && Math.abs(candidate.volumeResidual) <= VUflashPureEOSCG.SPECIFICATION_RELATIVE_TOLERANCE * targetMolarVolume;
  }

  private static boolean residualSignChanged(Candidate first, Candidate second) {
    return !sameResidualSign(first, second);
  }

  private static boolean sameResidualSign(Candidate first, Candidate second) {
    return Math.signum(first.volumeResidual) == Math.signum(second.volumeResidual);
  }

  private static Candidate closerPhysicalCandidate(Candidate first, Candidate second) {
    Candidate best = null;
    if (first.physical) {
      best = first;
    }
    if (second.physical && (best == null || Math.abs(second.volumeResidual) < Math.abs(best.volumeResidual))) {
      best = second;
    }
    return best;
  }

  private static final class Candidate {
    private final double pressureBar;
    private final double vaporFraction;
    private final double volumeResidual;
    private final boolean physical;

    private Candidate(double pressureBar, double vaporFraction, double volumeResidual, boolean physical) {
      this.pressureBar = pressureBar;
      this.vaporFraction = vaporFraction;
      this.volumeResidual = volumeResidual;
      this.physical = physical;
    }
  }
}
