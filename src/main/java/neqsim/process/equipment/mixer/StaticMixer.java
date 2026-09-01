/*
 * staticMixer.java
 *
 * Created on 11. mars 2001, 01:49
 */

package neqsim.process.equipment.mixer;

/**
 * Backward-compatible mixer type using the standard {@link Mixer} implementation.
 *
 * <p>
 * Keeping this class preserves source and serialization compatibility for existing process models while ensuring that
 * both mixer names use the same phase bookkeeping, mass balance, and optimized-execution behavior.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class StaticMixer extends Mixer {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for StaticMixer.
   *
   * @param name a {@link java.lang.String} object
   */
  public StaticMixer(String name) {
    super(name);
  }
}
