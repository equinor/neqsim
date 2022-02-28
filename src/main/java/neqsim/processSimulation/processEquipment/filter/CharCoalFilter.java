package neqsim.processSimulation.processEquipment.filter;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

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
     * @param name
     * @param inStream
     */
    public CharCoalFilter(String name, StreamInterface inStream) {
        super(name, inStream);
    }
}
