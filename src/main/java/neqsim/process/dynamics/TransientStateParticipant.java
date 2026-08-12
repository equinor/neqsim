package neqsim.process.dynamics;

import java.io.Serializable;
import neqsim.process.ProcessElementInterface;

/**
 * Contract for a process element that can participate in an identity-preserving transient step transaction.
 *
 * <p>
 * Implementations capture only mutable state that can change during one physical timestep. The owning object is never
 * replaced during rollback; {@link #restoreTransientState(Serializable)} must apply the supplied snapshot to the same
 * object instance. Configuration that may be changed by an event action must be included as well as physical inventory,
 * controller integral, clock, calculation identifier, and other mutable solver state.
 * </p>
 *
 * <p>
 * A state identity must remain stable for the lifetime of the participant and must be unique within one
 * {@code ProcessSystem}. It is diagnostic and provenance metadata, not an object lookup key. Transaction code still
 * keys participants by Java object identity.
 * </p>
 *
 * <p>
 * Implementing this interface establishes rollback mechanics only. It does not by itself establish conservation
 * accuracy, timestep independence, numerical stability, or engineering approval.
 * </p>
 *
 * @param <S> immutable or defensively copied serializable snapshot type
 * @author Even Solbraa
 * @version 1.0
 */
public interface TransientStateParticipant<S extends Serializable> extends ProcessElementInterface {
  /**
   * Returns the stable state identity used in transaction diagnostics and replay provenance.
   *
   * @return non-null, non-empty identity unique within one process system
   */
  String getTransientStateIdentity();

  /**
   * Returns a deterministic reason why this implementation cannot provide complete transaction coverage.
   *
   * <p>
   * The default is {@code null}, meaning that the participant is ready to capture. Extensible base classes should
   * override this method when inherited snapshot logic cannot account for subclass-owned state.
   * </p>
   *
   * @return {@code null} when complete, otherwise a non-empty blocking diagnostic
   */
  default String getTransientStateCoverageIssue() {
    return null;
  }

  /**
   * Captures the complete mutable state owned by this participant.
   *
   * @return non-null snapshot independent of later participant mutation
   */
  S captureTransientState();

  /**
   * Restores a previously captured snapshot to this same object instance.
   *
   * @param snapshot snapshot returned by {@link #captureTransientState()}
   */
  void restoreTransientState(S snapshot);
}
