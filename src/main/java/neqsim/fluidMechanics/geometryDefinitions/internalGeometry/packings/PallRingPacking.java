/*
 * PallRingPacking.java
 *
 * Created on 25. august 2001, 23:58
 */
package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings;

/**
 * <p>
 * PallRingPacking class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PallRingPacking extends Packing {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for PallRingPacking.
     * </p>
     */
    public PallRingPacking() {
        super();
    }

    /**
     * <p>
     * Constructor for PallRingPacking.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public PallRingPacking(String name) {
        super(name);
    }

    /**
     * <p>
     * Constructor for PallRingPacking.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param material a {@link java.lang.String} object
     * @param size a int
     */
    public PallRingPacking(String name, String material, int size) {
        super(name, material, size);
    }
}
