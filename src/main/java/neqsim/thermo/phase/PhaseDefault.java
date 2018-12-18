/*
 * PhaseEos.java
 *
 * Created on 3. juni 2000, 14:38
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentInterface;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class PhaseDefault extends Phase{

    private static final long serialVersionUID = 1000;
    
    protected ComponentInterface defComponent = null;
    
    /** Creates new PhaseEos */
    public PhaseDefault() {
        
    }
    public PhaseDefault(ComponentInterface comp){
        super();
        defComponent = comp;
    }
    
    public void setComponentType(ComponentInterface comp){
        defComponent = comp;
    }
    
    public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber){
        super.addcomponent(moles);
        try{
            componentArray[compNumber] = defComponent.getClass().newInstance();
        }
        catch(Exception e){
            logger.error("err " + e.toString());
        }
        componentArray[compNumber].createComponent(componentName, moles, molesInPhase, compNumber);
    }
    
    
    
    public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase, double beta){ // type = 0 start init type =1 gi nye betingelser
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
    }
    
    public double molarVolume(double pressure, double temperature, double A, double B, int phase) throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException{
        return 1.0;
    }
    
      public void resetMixingRule(int type){
      }
    
    public double getMolarVolume(){
        return 1.0;
    }
    
     
    public double getGibbsEnergy(){
        double val=0.0;
        for (int i=0; i < numberOfComponents; i++){
            val += getComponent(i).getNumberOfMolesInPhase()*(getComponent(i).getLogFugasityCoeffisient());//+Math.log(getComponent(i).getx()*getComponent(i).getAntoineVaporPressure(temperature)));
        }
        return R*temperature*((val)+Math.log(pressure)*numberOfMolesInPhase);
    }
}