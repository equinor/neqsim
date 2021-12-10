package neqsim.fluidMechanics.geometryDefinitions.pipe;

import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinition;
import neqsim.fluidMechanics.geometryDefinitions.internalGeometry.wall.PipeWall;

public class PipeData extends GeometryDefinition {
    private static final long serialVersionUID = 1000;

    public PipeData() {
        wall = new PipeWall();
    }

    public PipeData(double diameter) {
        super(diameter);
        wall = new PipeWall();
    }

    public PipeData(double diameter, double roughness) {
        super(diameter, roughness);
        wall = new PipeWall();
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public PipeData clone() {
        PipeData clonedPipe = null;
        try {
            clonedPipe = (PipeData) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedPipe;
    }
}
