/*
 * Density.java
 *
 * Created on 24. januar 2001, 19:49
 */

package neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.density;

import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.LiquidPhysicalPropertyMethod;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class Density extends LiquidPhysicalPropertyMethod implements neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DensityInterface{

    private static final long serialVersionUID = 1000;
    
      /** Creates new Density */
    public Density() {
    }
    
    public Density(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface liquidPhase) {
        this.liquidPhase = liquidPhase;
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
        
        double tempVar=0.0;
        if(liquidPhase.getPhase().useVolumeCorrection()){
            for(int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
                tempVar += liquidPhase.getPhase().getComponents()[i].getx()*(liquidPhase.getPhase().getComponents()[i].getVolumeCorrection()+liquidPhase.getPhase().getComponents()[i].getVolumeCorrectionT()*(liquidPhase.getPhase().getTemperature()-288.15)) ;
            }
        }
        //System.out.println("density correction tempvar " + tempVar);
       return 1.0/(liquidPhase.getPhase().getMolarVolume()-tempVar)*liquidPhase.getPhase().getMolarMass()*1.0e5;
    }
}
