/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class BinaryHVParameterFittingToSolubilityData extends  HuronVidalFunction{

    private static final long serialVersionUID = 1000;
    
    int phase = 1;
    int type = 1;
    /** Creates new Test */
    public BinaryHVParameterFittingToSolubilityData() {
    }
    
    public BinaryHVParameterFittingToSolubilityData(int phase, int type) {
        this.phase = phase;
        this.type = type;
    }
    
    public double calcValue(double[] dependentValues){
        
        if(type==1){
            thermoOps.TPflash();
            //system.display();
            //System.out.println("x " + system.getPhases()[1].getComponents()[0].getx());
            return system.getPhases()[phase].getComponents()[0].getx();
        } else if(type==10){
            try{
                thermoOps.bubblePointPressureFlash(true);
            } catch(Exception e){
                e.printStackTrace();
                return system.getPressure()*system.getPhase(0).getComponents()[0].getx();
            }
            return system.getPressure()*system.getPhase(0).getComponents()[0].getx();
        } else{
             thermoOps.TPflash();
            return system.getPhases()[phase].getComponents()[1].getx();
        }
    }
    
    public double calcTrueValue(double val){
        return val;
    }
}