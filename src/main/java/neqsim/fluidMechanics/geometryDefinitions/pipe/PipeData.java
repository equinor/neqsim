package neqsim.fluidMechanics.geometryDefinitions.pipe;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinition;
import neqsim.fluidMechanics.geometryDefinitions.internalGeometry.wall.PipeWall;

public class PipeData extends GeometryDefinition implements  neqsim.thermo.ThermodynamicConstantsInterface{

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
    
    public void init(){
        super.init();
       }
    
    public Object clone(){
        PipeData clonedPipe = null;
        try{
            clonedPipe = (PipeData) super.clone();
        }
        catch(Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedPipe;
    }
}