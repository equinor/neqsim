package neqsim.process.equipment.filter;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * CharCoalFilter class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CharCoalFilter extends Filter {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for CharCoalFilter.
   *
   * @param name name of filter
   * @param inStream input stream
   */
  public CharCoalFilter(String name, StreamInterface inStream) {
    super(name, inStream);
    setFilterServiceType(FilterType.ACTIVATED_CARBON);
  }
}
