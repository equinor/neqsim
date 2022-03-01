package neqsim.fluidMechanics.geometryDefinitions.reactor;

import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinition;

/**
 * <p>
 * ReactorData class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ReactorData extends GeometryDefinition {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for ReactorData.
     * </p>
     */
    public ReactorData() {}

    /**
     * <p>
     * Constructor for ReactorData.
     * </p>
     *
     * @param diameter a double
     */
    public ReactorData(double diameter) {
        super(diameter);
    }

    /**
     * <p>
     * Constructor for ReactorData.
     * </p>
     *
     * @param diameter a double
     * @param roughness a double
     */
    public ReactorData(double diameter, double roughness) {
        super(diameter, roughness);
        packing =
                new neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings.PallRingPacking(
                        "PallRingPacking");
    }

    /**
     * <p>
     * Constructor for ReactorData.
     * </p>
     *
     * @param diameter a double
     * @param packingType a int
     */
    public ReactorData(double diameter, int packingType) {
        super(diameter);
        setPackingType(packingType);
    }

    /** {@inheritDoc} */
    @Override
    public void setPackingType(int i) {
        // if(i!=100){
        packing =
                new neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings.PallRingPacking(
                        "PallRingPacking");
        // }
    }

    /**
     * <p>
     * setPackingType.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setPackingType(String name) {
        if (name.equals("pallring")) {
            packing =
                    new neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings.PallRingPacking(
                            "pallring");
        } else if (name.equals("rashigring")) {
            packing =
                    new neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings.RachigRingPacking(
                            "rashigring");
        } else {
            System.out.println("pakcing " + name + " not defined in database - using pallrings");
            packing =
                    new neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings.PallRingPacking(
                            "pallring");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setPackingType(String name, String material, int size) {
        if (name.equals("pallring")) {
            packing =
                    new neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings.PallRingPacking(
                            "pallring", material, size);
        } else if (name.equals("rashigring")) {
            packing =
                    new neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings.RachigRingPacking(
                            "rashigring", material, size);
        } else {
            System.out.println("pakcing " + name + " not defined in database - using pallrings");
            packing =
                    new neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings.PallRingPacking(
                            "pallring", material, size);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void init() {
        super.init();
    }

    /** {@inheritDoc} */
    @Override
    public ReactorData clone() {
        ReactorData clonedPipe = null;
        try {
            clonedPipe = (ReactorData) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        return clonedPipe;
    }
}
