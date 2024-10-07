package neqsim.processsimulation.processequipment.filter;

import neqsim.processsimulation.processequipment.stream.StreamInterface;

/**
 * <p>
 * CharCoalFilter class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CharCoalFilter extends Filter {
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for CharCoalFilter.
   *
   * @param name name of filter
   * @param inStream input stream
   */
  public CharCoalFilter(String name, StreamInterface inStream) {
    super(name, inStream);
  }
}
