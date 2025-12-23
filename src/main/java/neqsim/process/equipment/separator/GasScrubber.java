/*
 * GasScrubber.java
 *
 * Created on 12. mars 2001, 19:48
 */

package neqsim.process.equipment.separator;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign;

/**
 * <p>
 * GasScrubber class.
 * </p>
 *
 * A vertical separator designed primarily for gas-liquid separation in gas
 * processing applications.
 * Gas scrubbers are typically used upstream of compressors to remove liquid
 * droplets from gas.
 *
 * <p>
 * For detailed documentation on separator internals and carry-over
 * calculations, see:
 * <a href=
 * "https://github.com/equinor/neqsim/blob/master/docs/wiki/separators_and_internals.md">
 * Separators and Internals Wiki</a> and
 * <a href=
 * "https://github.com/equinor/neqsim/blob/master/docs/wiki/carryover_calculations.md">
 * Carry-Over Calculations Wiki</a>
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 * @see neqsim.process.equipment.separator.Separator
 * @see neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign
 */
public class GasScrubber extends Separator {
  /** Serialization version UID. */
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
   * @param name        a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.Stream} object
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
