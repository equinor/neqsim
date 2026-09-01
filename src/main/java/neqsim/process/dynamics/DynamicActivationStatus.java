package neqsim.process.dynamics;

/**
 * Runtime activation state for an audited transient implementation.
 *
 * <p>
 * This dimension is intentionally separate from {@link DynamicCapability}. Capability describes what kind of state an
 * element can own; activation describes whether that stateful path is actually selected by the current runtime
 * configuration. Neither value is a quantitative validation or professional-readiness certificate.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public enum DynamicActivationStatus {
  /** The element is algebraic and therefore has no independent stateful path to activate. */
  NOT_APPLICABLE,

  /** A type-specific audit verifies that the stateful transient path is active for the current configuration. */
  ACTIVE,

  /** A type-specific audit verifies that the stateful transient path is inactive for the current configuration. */
  INACTIVE,

  /** Dynamic execution is requested/enabled but required runtime configuration is incomplete. */
  INCOMPLETE_CONFIGURATION,

  /** Runtime activation semantics for this element type have not yet been audited explicitly. */
  UNVERIFIED
}
