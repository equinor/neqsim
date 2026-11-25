package neqsim.process.safety.release;

import java.io.Serializable;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/** Immutable SI input for a release, independent of process orchestration and transport layers. */
public final class ReleaseFlowRequest implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Preparation applied to the upstream state before release-model evaluation. */
  public enum UpstreamPreparation {
    /** Re-equilibrate the supplied overall state with a TP flash before calculation. */
    EQUILIBRATE_TP,
    /** Preserve one explicitly selected material-phase root from an initialized inventory. */
    PRESERVE_SELECTED_PHASE
  }

  private final SystemInterface fluid;
  private final double diameterM;
  private final double dischargeCoefficient;
  private final double backPressurePa;
  private final double flowPathLengthM;
  private final double darcyFrictionFactor;
  private final UpstreamPreparation upstreamPreparation;
  private final PhaseType selectedUpstreamPhase;

  /**
   * Creates a request with a defensive fluid copy.
   *
   * @param fluid upstream stagnation state; amount sets composition, not the release rate
   * @param diameterM circular opening diameter in m
   * @param dischargeCoefficient effective-area factor in (0, 1]
   * @param backPressurePa receiving absolute pressure in Pa
   * @throws IllegalArgumentException for absent fluid or invalid geometry/boundary conditions
   */
  public ReleaseFlowRequest(SystemInterface fluid, double diameterM, double dischargeCoefficient,
      double backPressurePa) {
    this(fluid, diameterM, dischargeCoefficient, backPressurePa, 0.0, 0.0);
  }

  /**
   * Creates a request for a constant-area pipe release with a defensive fluid copy.
   *
   * @param fluid upstream stagnation state; amount sets composition, not the release rate
   * @param diameterM pipe internal diameter in m
   * @param dischargeCoefficient effective full-bore area factor in (0, 1]
   * @param backPressurePa receiving absolute pressure in Pa
   * @param flowPathLengthM pipe length from inventory boundary to release plane in m
   * @param darcyFrictionFactor specified Darcy friction factor, dimensionless
   * @throws IllegalArgumentException for absent fluid or invalid geometry/boundary conditions
   */
  public ReleaseFlowRequest(SystemInterface fluid, double diameterM, double dischargeCoefficient, double backPressurePa,
      double flowPathLengthM, double darcyFrictionFactor) {
    this(fluid, diameterM, dischargeCoefficient, backPressurePa, flowPathLengthM, darcyFrictionFactor,
        UpstreamPreparation.EQUILIBRATE_TP, null);
  }

  /**
   * Creates the complete immutable request.
   *
   * @param fluid upstream fluid snapshot
   * @param diameterM opening or pipe diameter in m
   * @param dischargeCoefficient effective-area factor
   * @param backPressurePa receiving absolute pressure in Pa
   * @param flowPathLengthM pipe length in m, or zero for a short opening
   * @param darcyFrictionFactor specified Darcy friction factor, or zero for a short opening
   * @param upstreamPreparation upstream-state preparation policy
   * @param selectedUpstreamPhase selected phase, or null for overall TP equilibration
   */
  private ReleaseFlowRequest(SystemInterface fluid, double diameterM, double dischargeCoefficient,
      double backPressurePa, double flowPathLengthM, double darcyFrictionFactor,
      UpstreamPreparation upstreamPreparation, PhaseType selectedUpstreamPhase) {
    if (fluid == null) {
      throw new IllegalArgumentException("Upstream fluid is required");
    }
    positive(diameterM, "diameterM");
    positive(dischargeCoefficient, "dischargeCoefficient");
    positive(backPressurePa, "backPressurePa");
    nonnegative(flowPathLengthM, "flowPathLengthM");
    nonnegative(darcyFrictionFactor, "darcyFrictionFactor");
    if ((flowPathLengthM == 0.0) != (darcyFrictionFactor == 0.0)) {
      throw new IllegalArgumentException("Pipe length and Darcy friction factor must both be zero or positive");
    }
    if (darcyFrictionFactor > 1.0) {
      throw new IllegalArgumentException("Darcy friction factor must be <= 1");
    }
    if (dischargeCoefficient > 1.0 || !Double.isFinite(Math.PI * diameterM * diameterM / 4.0)
        || Math.PI * diameterM * diameterM / 4.0 == 0.0) {
      throw new IllegalArgumentException("Coefficient must be <= 1 and opening area finite and positive");
    }
    this.fluid = fluid.clone();
    this.diameterM = diameterM;
    this.dischargeCoefficient = dischargeCoefficient;
    this.backPressurePa = backPressurePa;
    this.flowPathLengthM = flowPathLengthM;
    this.darcyFrictionFactor = darcyFrictionFactor;
    this.upstreamPreparation = upstreamPreparation;
    this.selectedUpstreamPhase = selectedUpstreamPhase;
  }

  /**
   * Creates a short-opening request from one explicitly selected inventory phase.
   *
   * <p>
   * The inventory must already contain the requested material phase. The selected phase is defensively extracted and
   * retained as the upstream root instead of being reinterpreted as the inventory's overall TP equilibrium. This is
   * required at saturated or slightly metastable withdrawal boundaries where a tank can contain liquid while a cold
   * single-root TP flash chooses vapour. The release model still owns all downstream equilibrium calculations.
   * </p>
   *
   * @param inventory initialized source inventory containing the selected phase
   * @param phaseType selected GAS, OIL, LIQUID or AQUEOUS withdrawal phase
   * @param diameterM circular opening diameter in m
   * @param dischargeCoefficient effective-area factor in (0, 1]
   * @param backPressurePa receiving absolute pressure in Pa
   * @return immutable phase-selected request
   * @throws IllegalArgumentException for an absent or unsupported selected phase or invalid request input
   */
  public static ReleaseFlowRequest forSelectedPhase(SystemInterface inventory, PhaseType phaseType, double diameterM,
      double dischargeCoefficient, double backPressurePa) {
    return forSelectedPhase(inventory, phaseType, diameterM, dischargeCoefficient, backPressurePa, 0.0, 0.0);
  }

  /**
   * Creates a constant-area pipe request from one explicitly selected inventory phase.
   *
   * @param inventory initialized source inventory containing the selected phase
   * @param phaseType selected GAS, OIL, LIQUID or AQUEOUS withdrawal phase
   * @param diameterM pipe internal diameter in m
   * @param dischargeCoefficient effective full-bore area factor in (0, 1]
   * @param backPressurePa receiving absolute pressure in Pa
   * @param flowPathLengthM pipe length from inventory boundary to release plane in m
   * @param darcyFrictionFactor specified Darcy friction factor, dimensionless
   * @return immutable phase-selected request
   * @throws IllegalArgumentException for an absent or unsupported selected phase or invalid request input
   */
  public static ReleaseFlowRequest forSelectedPhase(SystemInterface inventory, PhaseType phaseType, double diameterM,
      double dischargeCoefficient, double backPressurePa, double flowPathLengthM, double darcyFrictionFactor) {
    if (inventory == null || phaseType == null) {
      throw new IllegalArgumentException("Inventory and selected phase are required");
    }
    if (phaseType != PhaseType.GAS && phaseType != PhaseType.OIL && phaseType != PhaseType.LIQUID
        && phaseType != PhaseType.AQUEOUS) {
      throw new IllegalArgumentException("Selected phase must be GAS, OIL, LIQUID or AQUEOUS");
    }
    SystemInterface source = inventory.clone();
    for (int phase = 0; phase < source.getNumberOfPhases(); phase++) {
      if (source.getPhase(phase).getType() == phaseType) {
        SystemInterface selected = source.phaseToSystem(phase);
        selected.init(3);
        positive(selected.getMass("kg"), "selected phase mass");
        return new ReleaseFlowRequest(selected, diameterM, dischargeCoefficient, backPressurePa, flowPathLengthM,
            darcyFrictionFactor, UpstreamPreparation.PRESERVE_SELECTED_PHASE, phaseType);
      }
    }
    throw new IllegalArgumentException("Selected upstream phase is absent: " + phaseType);
  }

  static double positive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
    return value;
  }

  static double nonnegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
    return value;
  }

  /** @return independent upstream fluid copy */
  public SystemInterface getFluid() {
    return fluid.clone();
  }

  /** @return declared upstream-state preparation */
  public UpstreamPreparation getUpstreamPreparation() {
    return upstreamPreparation;
  }

  /** @return whether one explicitly selected upstream phase is preserved */
  public boolean hasSelectedUpstreamPhase() {
    return selectedUpstreamPhase != null;
  }

  /**
   * @return selected upstream phase
   * @throws IllegalStateException when the request uses overall TP equilibration
   */
  public PhaseType getSelectedUpstreamPhase() {
    if (selectedUpstreamPhase == null) {
      throw new IllegalStateException("Request does not preserve a selected upstream phase");
    }
    return selectedUpstreamPhase;
  }

  /** @return opening diameter in m */
  public double getDiameterM() {
    return diameterM;
  }

  /** @return dimensionless effective-area factor */
  public double getDischargeCoefficient() {
    return dischargeCoefficient;
  }

  /** @return receiving absolute pressure in Pa */
  public double getBackPressurePa() {
    return backPressurePa;
  }

  /** @return true when this request defines a finite constant-area pipe path */
  public boolean hasFlowPath() {
    return flowPathLengthM > 0.0;
  }

  /** @return pipe flow-path length in m, or zero for a short opening */
  public double getFlowPathLengthM() {
    return flowPathLengthM;
  }

  /** @return specified Darcy friction factor, or zero for a short opening */
  public double getDarcyFrictionFactor() {
    return darcyFrictionFactor;
  }

  void requireShortOpening() {
    if (hasFlowPath()) {
      throw new UnsupportedOperationException("Short-opening model cannot ignore configured pipe geometry");
    }
  }

  /** @return physical circular area in m2 */
  public double getAreaM2() {
    return Math.PI * diameterM * diameterM / 4.0;
  }

  /** @return discharge coefficient multiplied by physical area, in m2 */
  public double getEffectiveAreaM2() {
    return dischargeCoefficient * getAreaM2();
  }
}
