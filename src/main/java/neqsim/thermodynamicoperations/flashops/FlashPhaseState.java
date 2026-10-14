package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Internal phase-state transfers for bounded flash trials.
 *
 * <p>
 * A compact snapshot retains phase mapping, configured roots, fractions, compositions and each phase's K-values.
 * Restoring it rebuilds fugacity caches at the unchanged temperature, pressure and feed. It does not retain complete
 * phase objects or derivative caches: the physical phase slots must remain available with the same component order.
 * Accepted detached candidates instead transfer cloned phase objects, with an explicit choice to retain their
 * initialized caches or rebuild them. Neither operation chooses a root policy or decides whether a trial is acceptable.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
final class FlashPhaseState {
  private final int numberOfPhases;
  private final PhaseType[] phaseTypes;
  private final PhaseType[] rootTypes;
  private final int[] phaseIndices;
  private final double[] betas;
  private final double[][] compositions;
  private final double[][] kValues;
  private final double gibbsEnergy;

  /**
   * Captures the active phase values without evaluating thermodynamic properties.
   *
   * @param source initialized split to retain
   * @param gibbsEnergy separately evaluated reference energy, or NaN when not needed
   */
  private FlashPhaseState(SystemInterface source, double gibbsEnergy) {
    numberOfPhases = source.getNumberOfPhases();
    int numberOfComponents = source.getPhase(0).getNumberOfComponents();
    this.gibbsEnergy = gibbsEnergy;
    phaseTypes = new PhaseType[numberOfPhases];
    rootTypes = new PhaseType[numberOfPhases];
    phaseIndices = new int[numberOfPhases];
    betas = new double[numberOfPhases];
    compositions = new double[numberOfPhases][numberOfComponents];
    kValues = new double[numberOfPhases][numberOfComponents];
    for (int phase = 0; phase < numberOfPhases; phase++) {
      phaseTypes[phase] = source.getPhase(phase).getType();
      rootTypes[phase] = source.getPhaseType(phase);
      phaseIndices[phase] = source.getPhaseIndex(phase);
      betas[phase] = source.getBeta(phase);
      for (int component = 0; component < numberOfComponents; component++) {
        compositions[phase][component] = source.getPhase(phase).getComponent(component).getx();
        kValues[phase][component] = source.getPhase(phase).getComponent(component).getK();
      }
    }
  }

  /**
   * Retains a split whose reference energy is already managed by the caller.
   *
   * @param source initialized split to retain
   * @return independent compact snapshot
   */
  static FlashPhaseState capture(SystemInterface source) {
    return new FlashPhaseState(source, Double.NaN);
  }

  /**
   * Retains a split and its extensive Gibbs energy for a bounded refinement.
   *
   * @param source initialized split to retain
   * @return independent compact snapshot including its reference energy
   */
  static FlashPhaseState captureWithGibbsEnergy(SystemInterface source) {
    return new FlashPhaseState(source, source.getGibbsEnergy());
  }

  /**
   * Gets the retained active phase count.
   *
   * @return active phase count
   */
  int getNumberOfPhases() {
    return numberOfPhases;
  }

  /**
   * Gets the resulting phase classification, separate from its configured root selector.
   *
   * @param phase logical phase index
   * @return retained phase classification
   */
  PhaseType getPhaseType(int phase) {
    return phaseTypes[phase];
  }

  /**
   * Gets the configured root selector.
   *
   * @param phase logical phase index
   * @return retained root selector
   */
  PhaseType getRootType(int phase) {
    return rootTypes[phase];
  }

  /**
   * Gets the physical storage slot backing a logical phase.
   *
   * @param phase logical phase index
   * @return retained physical phase-array index
   */
  int getPhaseIndex(int phase) {
    return phaseIndices[phase];
  }

  /**
   * Gets the retained phase fraction.
   *
   * @param phase logical phase index
   * @return retained molar phase fraction
   */
  double getBeta(int phase) {
    return betas[phase];
  }

  /**
   * Gets the reference energy when it was requested during capture.
   *
   * @return extensive Gibbs energy in J, or NaN for a snapshot captured without energy
   */
  double getGibbsEnergy() {
    return gibbsEnergy;
  }

  /**
   * Checks that every active phase retains its original classification at the same logical index.
   *
   * @param candidate refined split
   * @return true when phase count and ordered classifications are unchanged
   */
  boolean hasSamePhaseTypes(SystemInterface candidate) {
    if (candidate.getNumberOfPhases() != numberOfPhases) {
      return false;
    }
    for (int phase = 0; phase < numberOfPhases; phase++) {
      if (candidate.getPhase(phase).getType() != phaseTypes[phase]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Restores the active phase count and retained phase values, then initializes fugacity caches once.
   *
   * @param target system with unchanged temperature, pressure, feed and retained physical slots
   */
  void restore(SystemInterface target) {
    target.setNumberOfPhases(numberOfPhases);
    restorePhaseValues(target);
  }

  /**
   * Restores a refinement without changing its already retained active phase count.
   *
   * <p>
   * Mapping is restored before accessing a phase. Configured selectors, rather than resulting classifications, are
   * reinstated so initialization uses the original root policy. If phase shifting is disabled, these selectors must
   * still be present in the retained physical slots. Fraction normalization and level-one initialization retain the
   * existing rollback boundary; higher derivative caches require their usual subsequent initialization.
   * </p>
   *
   * @param target system with unchanged feed, temperature, pressure and active phase count
   */
  void restorePhaseValues(SystemInterface target) {
    for (int phase = 0; phase < numberOfPhases; phase++) {
      target.setPhaseIndex(phase, phaseIndices[phase]);
      target.setPhaseType(phase, rootTypes[phase]);
      target.setBeta(phase, betas[phase]);
      for (int component = 0; component < compositions[phase].length; component++) {
        target.getPhase(phase).getComponent(component).setx(compositions[phase][component]);
        target.getPhase(phase).getComponent(component).setK(kValues[phase][component]);
      }
    }
    target.normalizeBeta();
    target.init(1);
  }

  /**
   * Copies an accepted detached candidate into canonical target slots while retaining its initialized phase caches.
   *
   * <p>
   * Complete phase clones preserve accepted near-critical roots and own their mutable component buffers. No
   * initialization or fraction normalization is performed. The caller supplies matching feed and thermodynamic model
   * and the same temperature and pressure in each destination physical slot, because setPhase retains that slot's
   * temperature and pressure. It permits any required root-selector changes and retains responsibility for acceptance.
   * </p>
   *
   * @param source accepted candidate distinct from the target
   * @param target live system receiving the accepted phases
   */
  static void copyInitializedPhases(SystemInterface source, SystemInterface target) {
    target.setNumberOfPhases(source.getNumberOfPhases());
    for (int phase = 0; phase < source.getNumberOfPhases(); phase++) {
      target.setPhaseIndex(phase, phase);
      target.setPhase(source.getPhase(phase).clone(), phase);
      target.setPhaseType(phase, source.getPhaseType(phase));
      target.setBeta(phase, source.getBeta(phase));
    }
  }

  /**
   * Copies an accepted detached candidate and rebuilds its fugacity caches at the established adoption boundary.
   *
   * @param source accepted candidate distinct from the target, with the same feed, temperature and pressure
   * @param target live system receiving the accepted phases
   */
  static void copyAndReinitializePhases(SystemInterface source, SystemInterface target) {
    copyInitializedPhases(source, target);
    target.normalizeBeta();
    target.init(1);
  }
}
