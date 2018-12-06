/*
 * PhaseGE.java
 *
 * Created on 11. juli 2000, 21:00
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.mixingRule.EosMixingRules;
import neqsim.thermo.mixingRule.EosMixingRulesInterface;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class PhaseGE extends Phase  implements PhaseGEInterface,neqsim.thermo.ThermodynamicConstantsInterface  {

    private static final long serialVersionUID = 1000;
    
    public EosMixingRules mixSelect = new EosMixingRules();
    public EosMixingRulesInterface mixRuleEos;
    
    /** Creates new PhaseGE */
    public PhaseGE() {
        super();
        phaseTypeName = "liquid";
        componentArray = new ComponentGEInterface[MAX_NUMBER_OF_COMPONENTS];
    }
    
    public void init(double temperature, double pressure, double totalNumberOfMoles,double beta, int numberOfComponents, int type, int phase){ // type = 0 start init type =1 gi nye betingelser
        for (int i=0; i < numberOfComponents; i++){
            componentArray[i].init(temperature, pressure, totalNumberOfMoles, beta, type);
        }
        this.getExessGibbsEnergy(this, numberOfComponents, temperature, pressure, type);
    }
    
    public void init(double totalNumberOfMoles, int numberOfComponents, int initType, int phase, double beta){
        super.init(totalNumberOfMoles, numberOfComponents, initType, phase, beta);
        if(initType!=0) {
            getExessGibbsEnergy(this, numberOfComponents, temperature, pressure, phase);
        }
    }
    
    public void setMixingRule(int type){
        mixingRuleDefined = true;
        super.setMixingRule(2);
        mixRuleEos = mixSelect.getMixingRule(2, this);
    }
    
    public void resetMixingRule(int type){
        mixingRuleDefined = true;
        super.setMixingRule(2);
        mixRuleEos = mixSelect.resetMixingRule(2, this);
    }
    
    public double molarVolume(double pressure, double temperature,double  A, double B, int phase){
        return 1;
    }
    
    public double molarVolumeAnalytic(double pressure, double temperature,double  A, double B, int phase){
        return 1;
    }
    
    public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber){
        super.addcomponent(molesInPhase);
    }
    
    public void setAlpha(double[][] alpha){
    }
    
    public void setDij(double[][] Dij){
    }
    
    
    public double getExessGibbsEnergy(PhaseInterface phase, int numberOfComponents, double temperature, double pressure, int phasetype){
        System.out.println("this getExxess should never be used.......");
        return 0;
    }
    
    public double getGibbsEnergy(){
        return 0;
    }
    
    public double getExessGibbsEnergy(){
        System.out.println("this getExxess should never be used.......");
        return 0;
    }
    
    public void setDijT(double[][] DijT){
        
    }
    public double getActivityCoefficientSymetric(int k){
        return ((ComponentGEInterface) getComponent(k)).getGamma();
    }
    
    public double getActivityCoefficient(int k){
        return ((ComponentGEInterface) getComponent(k)).getGamma();
    }
    
    public double getActivityCoefficientInfDilWater(int k, int p){
        if(refPhase==null) {
            initRefPhases(false,getComponent(p).getName());
        }
        refPhase[k].setTemperature(temperature);
        refPhase[k].setPressure(pressure);
        refPhase[k].init(refPhase[k].getNumberOfMolesInPhase(), 2, 1, this.getPhaseType(), 1.0);
        ((PhaseGEInterface)refPhase[k]).getExessGibbsEnergy(refPhase[k], 2, refPhase[k].getTemperature(), refPhase[k].getPressure(), refPhase[k].getPhaseType());
        return ((ComponentGEInterface) refPhase[k].getComponent(0)).getGamma();
    }
    
    public double getActivityCoefficientInfDil(int k){
        PhaseInterface dilphase = (PhaseInterface)this.clone();
        dilphase.addMoles(k,-(1.0-1e-10)*dilphase.getComponent(k).getNumberOfMolesInPhase());
        dilphase.getComponent(k).setx(1e-10);
        dilphase.init(dilphase.getNumberOfMolesInPhase(), dilphase.getNumberOfComponents(), 1, dilphase.getPhaseType(), 1.0);
        ((PhaseGEInterface)dilphase).getExessGibbsEnergy(dilphase, 2, dilphase.getTemperature(), dilphase.getPressure(), dilphase.getPhaseType());
        return ((ComponentGEInterface) dilphase.getComponent(0)).getGamma();
    }
    
}