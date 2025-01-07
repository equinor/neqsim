package neqsim.process.equipment.separator.sectiontype;

import neqsim.process.equipment.separator.Separator;

/**
 * <p>
 * PackedSection class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PackedSection extends SeparatorSection {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for PackedSection.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param type a {@link java.lang.String} object
   * @param sep a {@link neqsim.process.equipment.separator.Separator} object
   */
  public PackedSection(String name, String type, Separator sep) {
    super(name, type, sep);
  }

  /** {@inheritDoc} */
  @Override
  public double calcEfficiency() {
    double gasLoadF = getSeparator().getGasLoadFactor();
    if (gasLoadF > 0.1) {
      return 0.1 / gasLoadF;
    } else {
      return 1.0;
    }
  }
}
