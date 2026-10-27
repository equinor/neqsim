package neqsim.process.dynamics;

import java.io.Serializable;
import java.util.List;

/**
 * Supplies an explicitly scaled numerical state without mutating the process or producing external effects.
 *
 * <p>
 * Providers must cover every numerical state relevant to their participant's error assessment. They must return the
 * same identities, units and tolerance policy before and after every trial. This declaration complements the complete
 * rollback snapshot; it does not replace that snapshot or establish physical qualification.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public interface TransientErrorStateProvider extends Serializable {
  /**
   * Reads the current state without mutation.
   *
   * @return non-null list of immutable state values, with stable identities and explicit tolerances
   */
  List<TransientErrorStateVariable> getTransientErrorState();
}
