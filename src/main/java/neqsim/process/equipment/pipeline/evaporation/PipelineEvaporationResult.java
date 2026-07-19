package neqsim.process.equipment.pipeline.evaporation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.thermo.system.SystemInterface;

/** Result of an axial pipeline evaporation study, with generic aliases used by gas dissolution. */
public class PipelineEvaporationResult {
  private final List<EvaporationProfilePoint> profile;
  private final boolean completeEvaporation;
  private final double completeEvaporationDistance;
  private final double maximumComponentMolarBalanceError;
  private final double relativeEnergyBalanceError;
  private final List<String> warnings;
  private final SystemInterface outletSystem;
  private final boolean gasDissolution;

  /**
   * Constructor used by the solver.
   *
   * @param profile axial profile
   * @param completeEvaporation whether the configured completion criterion was reached
   * @param completeEvaporationDistance interpolated completion distance in m, or NaN
   * @param maximumComponentMolarBalanceError largest component balance error in mol/s
   * @param relativeEnergyBalanceError relative overall energy balance error
   * @param warnings calculation warnings
   * @param outletSystem final two-phase system
   */
  PipelineEvaporationResult(List<EvaporationProfilePoint> profile, boolean completeEvaporation,
      double completeEvaporationDistance, double maximumComponentMolarBalanceError, double relativeEnergyBalanceError,
      List<String> warnings, SystemInterface outletSystem) {
    this(profile, completeEvaporation, completeEvaporationDistance, maximumComponentMolarBalanceError,
        relativeEnergyBalanceError, warnings, outletSystem, false);
  }

  /** Constructor used by the evaporation and dissolution solvers. */
  PipelineEvaporationResult(List<EvaporationProfilePoint> profile, boolean completeEvaporation,
      double completeEvaporationDistance, double maximumComponentMolarBalanceError, double relativeEnergyBalanceError,
      List<String> warnings, SystemInterface outletSystem, boolean gasDissolution) {
    this.profile = Collections.unmodifiableList(new ArrayList<EvaporationProfilePoint>(profile));
    this.completeEvaporation = completeEvaporation;
    this.completeEvaporationDistance = completeEvaporationDistance;
    this.maximumComponentMolarBalanceError = maximumComponentMolarBalanceError;
    this.relativeEnergyBalanceError = relativeEnergyBalanceError;
    this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    this.outletSystem = outletSystem.clone();
    if (outletSystem.getNumberOfPhases() > 1) {
      this.outletSystem.setPhaseType(1, outletSystem.getPhase(1).getType());
      this.outletSystem.getPhase(1).setType(outletSystem.getPhase(1).getType());
    }
    this.gasDissolution = gasDissolution;
  }

  /** @return immutable axial profile */
  public List<EvaporationProfilePoint> getProfile() {
    return profile;
  }

  /** @return whether the configured completion criterion was reached */
  public boolean isCompleteEvaporation() {
    return !gasDissolution && completeEvaporation;
  }

  /** @return whether the configured tracked-inventory completion criterion was reached */
  public boolean isCompleteTransfer() {
    return completeEvaporation;
  }

  /** @return whether the injected-gas dissolution criterion was reached */
  public boolean isCompleteDissolution() {
    return gasDissolution && completeEvaporation;
  }

  /** @return interpolated completion distance in m, or NaN when incomplete */
  public double getCompleteEvaporationDistance() {
    return gasDissolution ? Double.NaN : completeEvaporationDistance;
  }

  /** @return interpolated transfer completion distance in m, or NaN when incomplete */
  public double getCompletionDistance() {
    return completeEvaporationDistance;
  }

  /** @return interpolated complete-dissolution distance in m, or NaN when inapplicable or incomplete */
  public double getCompleteDissolutionDistance() {
    return gasDissolution ? completeEvaporationDistance : Double.NaN;
  }

  /** @return true when this result tracks injected gas dissolving into liquid */
  public boolean isGasDissolutionStudy() {
    return gasDissolution;
  }

  /** @return largest absolute component molar balance error in mol/s */
  public double getMaximumComponentMolarBalanceError() {
    return maximumComponentMolarBalanceError;
  }

  /** @return relative overall energy balance error */
  public double getRelativeEnergyBalanceError() {
    return relativeEnergyBalanceError;
  }

  /** @return immutable calculation warning list */
  public List<String> getWarnings() {
    return warnings;
  }

  /** @return defensive clone of the final two-phase system */
  public SystemInterface getOutletSystem() {
    SystemInterface copy = outletSystem.clone();
    if (outletSystem.getNumberOfPhases() > 1) {
      copy.setPhaseType(1, outletSystem.getPhase(1).getType());
      copy.getPhase(1).setType(outletSystem.getPhase(1).getType());
    }
    return copy;
  }
}
