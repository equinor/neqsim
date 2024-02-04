/*
 * GasScrubber.java
 *
 * Created on 12. mars 2001, 19:48
 */

package neqsim.processSimulation.processEquipment.separator;

import neqsim.processSimulation.mechanicalDesign.separator.GasScrubberMechanicalDesign;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

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
   * <p>
   * Constructor for GasScrubber.
   * </p>
   */
  @Deprecated
  public GasScrubber() {
    this("GasScrubber");
  }

  /**
   * <p>
   * Constructor for GasScrubber.
   * </p>
   *
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
   */
  @Deprecated
  public GasScrubber(StreamInterface inletStream) {
    this("GasScrubber", inletStream);
  }

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
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
   */
  public GasScrubber(String name, StreamInterface inletStream) {
    super(name, inletStream);
    this.setOrientation("vertical");
  }

  /**
   * {@inheritDoc}
   *
   * @return a
   *         {@link neqsim.processSimulation.mechanicalDesign.separator.GasScrubberMechanicalDesign}
   *         object
   */
  @Override
  public GasScrubberMechanicalDesign getMechanicalDesign() {
    return new GasScrubberMechanicalDesign(this);
  }

}
