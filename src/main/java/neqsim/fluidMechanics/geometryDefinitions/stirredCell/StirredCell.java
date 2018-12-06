package neqsim.fluidMechanics.geometryDefinitions.stirredCell;

import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinition;

public class StirredCell extends GeometryDefinition implements neqsim.thermo.ThermodynamicConstantsInterface{

    private static final long serialVersionUID = 1000;
    
    
    public StirredCell() {
    }
    
    public StirredCell(double diameter) {
        super(diameter);
        
    }
    public StirredCell(double diameter, double roughness) {
        super(diameter, roughness);
    }
    
    public void init(){
        super.init();
       }
    
    public Object clone(){
        StirredCell clonedPipe = null;
        try{
            clonedPipe = (StirredCell) super.clone();
        }
        catch(Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedPipe;
    }
}