package neqsim.process.equipment.separator;

import java.util.UUID;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

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
   * @param name        a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface}
   *                    object
   */
  public TwoPhaseSeparator(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

}
