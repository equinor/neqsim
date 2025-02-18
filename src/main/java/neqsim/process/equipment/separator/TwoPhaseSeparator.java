package neqsim.process.equipment.separator;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * <p>
 * TwoPhaseSeparator class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TwoPhaseSeparator extends Separator {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for TwoPhaseSeparator.
   *
   * @param name name of separator
   */
  public TwoPhaseSeparator(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for TwoPhaseSeparator.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public TwoPhaseSeparator(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }
}
