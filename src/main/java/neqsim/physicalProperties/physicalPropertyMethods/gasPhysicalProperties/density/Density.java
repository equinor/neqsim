/*
 * Density.java
 *
 * Created on 24. januar 2001, 19:49
 */

package neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.density;

import neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.GasPhysicalPropertyMethod;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class Density extends GasPhysicalPropertyMethod implements neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DensityInterface{

    private static final long serialVersionUID = 1000;
    
    /** Creates new Density */
    public Density() {
    }
    
    public Density(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface gasPhase) {
        this.gasPhase = gasPhase;
    }
    
    public Object clone(){
        Density properties = null;
        
        try{
            properties = (Density) super.clone();
        }
        catch(Exception e) {
            e.printStackTrace(System.err);
        }
        
        return properties;
    }
    /**
     *Returns the density of the phase.
     *Unit: kg/m^3
     */
    public double calcDensity(){
        
        double tempVar=0;
        if(gasPhase.getPhase().useVolumeCorrection()){
            for(int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
                tempVar += gasPhase.getPhase().getComponents()[i].getx()*gasPhase.getPhase().getComponents()[i].getVolumeCorrection();
            }
        }
        return 1.0/(gasPhase.getPhase().getMolarVolume()-tempVar)*gasPhase.getPhase().getMolarMass()*1e5;
    }
}
