/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */
package neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.diffusivity;

import org.apache.log4j.Logger;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class Diffusivity  extends neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.SolidPhysicalPropertyMethod implements neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DiffusivityInterface, Cloneable{

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(Diffusivity.class);

    
    double[][] binaryDiffusionCoeffisients;
    double[] effectiveDiffusionCoefficient;
    
    /** Creates new Conductivity */
    
    public Diffusivity() {
    }
    
    public Diffusivity(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhase) {
        super(solidPhase);
        binaryDiffusionCoeffisients = new double[liquidPhase.getPhase().getNumberOfComponents()][liquidPhase.getPhase().getNumberOfComponents()];
        effectiveDiffusionCoefficient = new double[liquidPhase.getPhase().getNumberOfComponents()];
        
    }
    
    public Object clone(){
        Diffusivity properties = null;
        
        try{
            properties = (Diffusivity) super.clone();
        }
        catch(Exception e) {
            logger.error("Cloning failed.",e);
        }
        
        properties.binaryDiffusionCoeffisients = this.binaryDiffusionCoeffisients.clone();
        for(int i=0;i<liquidPhase.getPhase().getNumberOfComponents();i++){
            System.arraycopy(this.binaryDiffusionCoeffisients[i], 0, properties.binaryDiffusionCoeffisients[i], 0, liquidPhase.getPhase().getNumberOfComponents());
        }
        return properties;
    }
    
    public double[][] calcDiffusionCoeffisients(int binaryDiffusionCoefficientMethod , int multicomponentDiffusionMethod){
       
        return binaryDiffusionCoeffisients;
    }
    
    
    public void calcEffectiveDiffusionCoeffisients(){
      
    }
    
    public double getMaxwellStefanBinaryDiffusionCoefficient(int i, int j){
        return binaryDiffusionCoeffisients[i][j];
    }
    
    public double getEffectiveDiffusionCoefficient(int i){
        return effectiveDiffusionCoefficient[i];
    }
    
    public double getFickBinaryDiffusionCoefficient(int i, int j){
        double nonIdealCorrection = 1.0 ;
        return binaryDiffusionCoeffisients[i][j]*nonIdealCorrection; // shuld be divided by non ideality factor
    }

    @Override
    public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
