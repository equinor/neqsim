package neqsim.fluidMechanics.geometryDefinitions.reactor;

import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinition;

public class ReactorData extends GeometryDefinition {
    private static final long serialVersionUID = 1000;

    public ReactorData() {}

    public ReactorData(double diameter) {
        super(diameter);
    }

    public ReactorData(double diameter, double roughness) {
        super(diameter, roughness);
        packing =
                new neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings.PallRingPacking();
    }

    public ReactorData(double diameter, int packingType) {
        super(diameter);
        setPackingType(packingType);
    }

    @Override
    public void setPackingType(int i) {
        // if(i!=100){
        packing =
                new neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings.PallRingPacking();
        // }
    }

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

    @Override
    public void init() {
        super.init();
    }

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
