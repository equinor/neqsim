/*
 * PhaseGENRTL.java
 *
 * Created on 17. juli 2000, 20:51
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGeDuanSun;
import neqsim.thermo.component.ComponentGeNRTL;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class PhaseDuanSun extends PhaseGE {

    private static final long serialVersionUID = 1000;
    
    double[][] alpha;
    String[][] mixRule;
    double[][] intparam;
    double[][] Dij;
    double GE=0.0;
    /** Creates new PhaseGENRTLmodifiedHV */
    
    public PhaseDuanSun() {
        super();
    }
    
    public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber){
        super.addcomponent(molesInPhase);
        componentArray[compNumber] = new ComponentGeDuanSun(componentName, moles, molesInPhase,compNumber);
    }
    
    public void setMixingRule(int type){
        super.setMixingRule(type);
        this.alpha = mixSelect.getNRTLalpha();
        this.Dij = mixSelect.getNRTLDij();
    }
    
    public void setAlpha(double[][] alpha){
        for(int i=0;i<alpha.length;i++){
            System.arraycopy(alpha[i], 0, this.alpha[i], 0, alpha[0].length);
        }
    }
    
    public void setDij(double[][] Dij){
        for(int i=0;i<Dij.length;i++){
            System.arraycopy(Dij[i], 0, this.Dij[i], 0, Dij[0].length);
        }
    }
    
    public double getExessGibbsEnergy(PhaseInterface phase, int numberOfComponents, double temperature, double pressure, int phasetype){
        GE = 0;
        for (int i=0; i < numberOfComponents; i++){
            GE += phase.getComponents()[i].getx()*Math.log(((ComponentGeDuanSun) componentArray[i]).getGamma(phase, numberOfComponents, temperature,  pressure, phasetype, alpha, Dij));
        }
        
        return R*temperature*numberOfMolesInPhase*GE;//phase.getNumberOfMolesInPhase()*
    }
    
    public double getGibbsEnergy(){
        return R*temperature*numberOfMolesInPhase*(GE+Math.log(pressure));
    }
    
    public double getExessGibbsEnergy(){
        //double GE = getExessGibbsEnergy(this, numberOfComponents, temperature,  pressure, phaseType);
        return GE;
    }
    
   
}
