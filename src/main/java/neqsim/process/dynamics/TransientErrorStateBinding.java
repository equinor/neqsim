package neqsim.process.dynamics;

import java.io.Serializable;

/**
 * Immutable association between one rollback participant and its numerical error-state reader.
 *
 * <p>
 * The participant and reader are live model references. The association is immutable, while subsequent provider reads
 * intentionally observe that participant's current trial state.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TransientErrorStateBinding implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final TransientStateParticipant<?> participant;
  private final TransientErrorStateProvider provider;

  /**
   * Associates one state participant with a read-only error-state provider.
   *
   * @param participant exact model object whose state is assessed
   * @param provider read-only numerical state reader
   * @throws IllegalArgumentException if either argument is null
   */
  public TransientErrorStateBinding(TransientStateParticipant<?> participant, TransientErrorStateProvider provider) {
    if (participant == null || provider == null) {
      throw new IllegalArgumentException("participant and provider must not be null");
    }
    this.participant = participant;
    this.provider = provider;
  }

  /**
   * Returns the exact rollback participant.
   *
   * @return participant object
   */
  public TransientStateParticipant<?> getParticipant() {
    return participant;
  }

  /**
   * Returns its numerical state reader.
   *
   * @return read-only state provider
   */
  public TransientErrorStateProvider getProvider() {
    return provider;
  }
}
