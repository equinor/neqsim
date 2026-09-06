package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;

/** Volume-energy flash specialized for pure-component EOS-CG systems. */
public class VUflashPureEOSCG extends Flash {
  private static final long serialVersionUID = 1000;
  static final double SPECIFICATION_RELATIVE_TOLERANCE = 1.0e-8;

  private final double targetVolume;
  private final double targetInternalEnergy;

  /**
   * Create a pure-component EOS-CG volume-energy flash.
   *
   * @param system thermodynamic system containing one component and using EOS-CG
   * @param targetVolume specified total volume in NeqSim internal volume units
   * @param targetInternalEnergy specified total internal energy in J
   */
  public VUflashPureEOSCG(SystemInterface system, double targetVolume, double targetInternalEnergy) {
    this.system = system;
    this.targetVolume = targetVolume;
    this.targetInternalEnergy = targetInternalEnergy;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    validateInput();

    EOSCGSinglePhaseVUFlash singlePhaseFlash = new EOSCGSinglePhaseVUFlash(system, targetVolume, targetInternalEnergy);
    if (singlePhaseFlash.solve()) {
      return;
    }

    EOSCGSaturationVUFlash twoPhaseFlash = new EOSCGSaturationVUFlash(system, targetVolume, targetInternalEnergy);
    if (twoPhaseFlash.solve()) {
      return;
    }

    throw new IllegalStateException(
        "Pure EOS-CG VU flash could not find a stable single-phase or saturation-line solution");
  }

  private void validateInput() {
    if (!Double.isFinite(targetVolume) || targetVolume <= 0.0) {
      throw new IllegalArgumentException("Specified volume must be finite and positive");
    }
    if (!Double.isFinite(targetInternalEnergy)) {
      throw new IllegalArgumentException("Specified internal energy must be finite");
    }
    if (system.getPhase(0).getNumberOfComponents() != 1 || !"EOS-CG".equals(system.getModelName())) {
      throw new IllegalArgumentException("VUflashPureEOSCG requires a pure-component EOS-CG system");
    }
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
