/*
 * PhaseSrkEos.java
 *
 * Created on 3. juni 2000, 14:38
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentSrk;
import neqsim.thermo.component.ComponentSrkPeneloux;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class PhaseSrkPenelouxEos extends PhaseSrkEos{

    private static final long serialVersionUID = 1000;
    
  /** Creates new PhaseSrkEos */
    public PhaseSrkPenelouxEos() {
        super();
     
    }
    
    public Object clone(){
        PhaseSrkPenelouxEos clonedPhase = null;
        try{
            clonedPhase = (PhaseSrkPenelouxEos) super.clone();
        }
        catch(Exception e)
        {
            logger.error("Cloning failed.", e);
        }
        
        return clonedPhase;
    }
    
    public void addcomponent(String componentName, double moles,double molesInPhase, int compNumber){
        super.addcomponent(molesInPhase);
        componentArray[compNumber] = new ComponentSrkPeneloux(componentName, moles, molesInPhase, compNumber);
    }
    
    
    
    
}