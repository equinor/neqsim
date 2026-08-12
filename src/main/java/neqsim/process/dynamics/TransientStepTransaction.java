package neqsim.process.dynamics;

/**
 * Single-use identity-preserving transaction around one physical transient step.
 *
 * <p>
 * Closing an open transaction rolls it back. A committed transaction retains the trial state; a
 * rolled-back transaction restores the captured state in place. Implementations are not thread
 * safe and must be used by the thread that owns the associated simulation step.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public interface TransientStepTransaction extends AutoCloseable {
  /** Transaction lifecycle state. */
  enum Status {
    /** Snapshot captured and neither committed nor rolled back. */
    OPEN,
    /** Trial state accepted. */
    COMMITTED,
    /** Captured state restored. */
    ROLLED_BACK
  }

  /**
   * Accepts the trial state and releases the rollback snapshot.
   */
  void commit();

  /**
   * Restores the captured state in place. Calling this after rollback is a no-op.
   */
  void rollback();

  /**
   * Returns the transaction lifecycle state.
   *
   * @return current status
   */
  Status getStatus();

  /**
   * Returns whether the transaction is still open.
   *
   * @return {@code true} before commit or rollback
   */
  default boolean isOpen() {
    return getStatus() == Status.OPEN;
  }

  /**
   * Rolls back an open transaction.
   */
  @Override
  void close();
}
