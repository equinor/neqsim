/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */

package neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.diffusivity;

import neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.CommonPhysicalPropertyMethod;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class Diffusivity extends CommonPhysicalPropertyMethod implements neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DiffusivityInterface, Cloneable{

    private static final long serialVersionUID = 1000;
    
    double[][] binaryDiffusionCoeffisients, binaryLennardJonesOmega;
    double[] effectiveDiffusionCoefficient;
    
    /** Creates new Conductivity */
    public Diffusivity() {
    }
    
    public Diffusivity(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {
        super(phase);
        binaryDiffusionCoeffisients = new double[phase.getPhase().getNumberOfComponents()][phase.getPhase().getNumberOfComponents()];
        binaryLennardJonesOmega = new double[phase.getPhase().getNumberOfComponents()][phase.getPhase().getNumberOfComponents()];
        effectiveDiffusionCoefficient = new double[phase.getPhase().getNumberOfComponents()];
        
    }
    
    public Object clone(){
        Diffusivity properties = null;
        
        try{
            properties = (Diffusivity) super.clone();
        }
        catch(Exception e) {
            e.printStackTrace(System.err);
        }
        
        return properties;
    }
    
    
    public double calcBinaryDiffusionCoefficient(int i, int j, int method){
        
        return 1.0e-6;
    }
    
    public double[][] calcDiffusionCoeffisients(int binaryDiffusionCoefficientMethod , int multicomponentDiffusionMethod){
        
        for(int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
            for(int j = 0; j < phase.getPhase().getNumberOfComponents(); j++) {
                binaryDiffusionCoeffisients[i][j] =  calcBinaryDiffusionCoefficient(i, j, binaryDiffusionCoefficientMethod);
                //System.out.println("diff gas  " + binaryDiffusionCoeffisients[i][j]);
            }
        }
        
        if(multicomponentDiffusionMethod==0){
            // ok use full matrix
        }
        else if(multicomponentDiffusionMethod==0){
            //calcEffectiveDiffusionCoeffisients();
        }
        return binaryDiffusionCoeffisients;
    }
    
    
    public void calcEffectiveDiffusionCoeffisients(){
        double sum=0;
        
        for(int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
            sum = 0;
            for(int j = 0; j < phase.getPhase().getNumberOfComponents(); j++) {
                if(i==j){
                }
                else{
                    sum += phase.getPhase().getComponents()[j].getx()/binaryDiffusionCoeffisients[i][j];
                }
            }
            effectiveDiffusionCoefficient[i] = (1.0-phase.getPhase().getComponents()[i].getx())/sum;
        }
    }
    
    public double getFickBinaryDiffusionCoefficient(int i, int j){
        return binaryDiffusionCoeffisients[i][j];
    }
    
    public double getEffectiveDiffusionCoefficient(int i){
        return effectiveDiffusionCoefficient[i];
    }
    
    public double getMaxwellStefanBinaryDiffusionCoefficient(int i, int j){
     /*   double temp = (i==j)? 1.0: 0.0;
        double nonIdealCorrection = temp + gasPhase.getPhase().getComponents()[i].getx() * gasPhase.getPhase().getComponents()[i].getdfugdn(j) *  gasPhase.getPhase().getNumberOfMolesInPhase();
        if (Double.isNaN(nonIdealCorrection)) nonIdealCorrection=1.0;
        return binaryDiffusionCoeffisients[i][j]/nonIdealCorrection; // shuld be divided by non ideality factor
      */
        return binaryDiffusionCoeffisients[i][j];
    }
    
}
