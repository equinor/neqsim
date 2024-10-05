/*
 * GasScrubber.java
 *
 * Created on 12. mars 2001, 19:48
 */

package neqsim.processsimulation.processequipment.separator;

import neqsim.processsimulation.mechanicaldesign.separator.GasScrubberMechanicalDesign;
import neqsim.processsimulation.processequipment.stream.StreamInterface;

/**
 * <p>
 * GasScrubber class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class GasScrubber extends Separator {
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for GasScrubber.
   *
   * @param name name of gas scrubber
   */
  public GasScrubber(String name) {
    super(name);
    this.setOrientation("vertical");
  }

  /**
   * <p>
   * Constructor for GasScrubber.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.processsimulation.processequipment.stream.Stream} object
   */
  public GasScrubber(String name, StreamInterface inletStream) {
    super(name, inletStream);
    this.setOrientation("vertical");
  }

  /** {@inheritDoc} */
  @Override
  public GasScrubberMechanicalDesign getMechanicalDesign() {
    return new GasScrubberMechanicalDesign(this);
  }
}
