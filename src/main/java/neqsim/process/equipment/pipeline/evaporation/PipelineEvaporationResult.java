package neqsim.process.equipment.pipeline.evaporation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.thermo.system.SystemInterface;

/** Result of an axial pipeline evaporation study. */
public class PipelineEvaporationResult {
  private final List<EvaporationProfilePoint> profile;
  private final boolean completeEvaporation;
  private final double completeEvaporationDistance;
  private final double maximumComponentMolarBalanceError;
  private final double relativeEnergyBalanceError;
  private final List<String> warnings;
  private final SystemInterface outletSystem;

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
    this.profile = Collections.unmodifiableList(new ArrayList<EvaporationProfilePoint>(profile));
    this.completeEvaporation = completeEvaporation;
    this.completeEvaporationDistance = completeEvaporationDistance;
    this.maximumComponentMolarBalanceError = maximumComponentMolarBalanceError;
    this.relativeEnergyBalanceError = relativeEnergyBalanceError;
    this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    this.outletSystem = outletSystem.clone();
  }

  /** @return immutable axial profile */
  public List<EvaporationProfilePoint> getProfile() {
    return profile;
  }

  /** @return whether the configured completion criterion was reached */
  public boolean isCompleteEvaporation() {
    return completeEvaporation;
  }

  /** @return interpolated completion distance in m, or NaN when incomplete */
  public double getCompleteEvaporationDistance() {
    return completeEvaporationDistance;
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
    return outletSystem.clone();
  }
}
