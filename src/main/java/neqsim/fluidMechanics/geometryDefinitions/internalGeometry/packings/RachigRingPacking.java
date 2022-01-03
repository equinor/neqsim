/*
 * PallRingPacking.java
 *
 * Created on 25. august 2001, 23:58
 */

package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings;

/**
 * <p>RachigRingPacking class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class RachigRingPacking extends Packing {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new PallRingPacking
     */
    public RachigRingPacking() {
    }

    /**
     * <p>Constructor for RachigRingPacking.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public RachigRingPacking(String name) {
        super(name);
    }

    /**
     * <p>Constructor for RachigRingPacking.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param material a {@link java.lang.String} object
     * @param size a int
     */
    public RachigRingPacking(String name, String material, int size) {
        super(name, material, size);
    }

}
