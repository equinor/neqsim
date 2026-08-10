package neqsim.process.dynamics;

/**
 * Describes the audited transient semantics of a process element.
 *
 * <p>
 * This classification is deliberately separate from a runtime steady-state/dynamic-mode switch. It states what kind of
 * transient behaviour an element is known to provide, not whether that behaviour is currently enabled. The contract is
 * used to distinguish genuine stored-state dynamics from algebraic equipment that is merely re-evaluated at each
 * simulation timestamp.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public enum DynamicCapability {
  /** Algebraic relation with no audited physical state integrated across timesteps. */
  ALGEBRAIC,

  /** Lumped dynamic state such as inventory, energy storage, actuator position, or rotating inertia. */
  DYNAMIC_LUMPED,

  /** Spatially distributed transient state such as a finite-volume or method-of-characteristics pipeline model. */
  DYNAMIC_DISTRIBUTED,

  /** Time-varying boundary or source state, for example reservoir depletion or an external dynamic boundary. */
  BOUNDARY_DYNAMIC,

  /** Controller, instrumentation, signal, logic, or other sampled control-system transient state. */
  CONTROL_DYNAMIC,

  /** A custom transient implementation exists, but its physical state/equations have not yet been audited. */
  UNCLASSIFIED_DYNAMIC,

  /** The element explicitly declares that a transient interpretation is unsupported. */
  UNSUPPORTED_DYNAMIC;

  /**
   * Returns whether this capability has an audited semantic category.
   *
   * @return true unless the element still requires transient capability review
   */
  public boolean isAudited() {
    return this != UNCLASSIFIED_DYNAMIC;
  }

  /**
   * Returns whether this category represents explicit time-dependent state rather than a purely algebraic relation.
   *
   * @return true for lumped, distributed, boundary, and control-system dynamics
   */
  public boolean hasExplicitDynamicState() {
    return this == DYNAMIC_LUMPED || this == DYNAMIC_DISTRIBUTED || this == BOUNDARY_DYNAMIC || this == CONTROL_DYNAMIC;
  }

  /**
   * Returns whether an element with this capability may participate in a transient study.
   *
   * <p>
   * Algebraic elements are valid participants when evaluated as algebraic constraints at each physical timestep. The
   * only category that is categorically excluded is {@link #UNSUPPORTED_DYNAMIC}. An unclassified custom transient
   * implementation may run, but remains an engineering-review item until its state/equations are audited.
   * </p>
   *
   * @return false only for explicitly unsupported transient behaviour
   */
  public boolean canParticipateInTransientStudy() {
    return this != UNSUPPORTED_DYNAMIC;
  }
}
