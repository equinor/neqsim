/*
 * AtractiveTermSrk.java
 *
 * Created on 13. mai 2001, 21:59
 */

package neqsim.thermo.component.atractiveEosTerm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 *
 * @author  esol
 * @version
 */
public class AtractiveTermPrDanesh extends AtractiveTermPr1978{

    private static final long serialVersionUID = 1000;

    double mMod = 0;
    /** Creates new AtractiveTermSrk */
    public AtractiveTermPrDanesh(ComponentEosInterface component) {
        super(component);
    }
    
    
    public Object clone(){
        AtractiveTermPrDanesh atractiveTerm = null;
        try{
            atractiveTerm = (AtractiveTermPrDanesh) super.clone();
        }
        catch(Exception e) {
            e.printStackTrace(System.err);
        }
        
        return atractiveTerm;
    }
    
    public void init(){
        m = (0.37464 + 1.54226 * component.getAcentricFactor() - 0.26992 * component.getAcentricFactor() * component.getAcentricFactor());
    }
    
    public double alpha(double temperature){
        if(temperature>component.getTC()) {
            mMod = m*1.21;
        } else {
            mMod= m;
        }
        return Math.pow(1.0+mMod*(1.0-Math.sqrt(temperature/component.getTC())),2.0);
    }
    
    public double aT(double temperature){
        return component.geta() * alpha(temperature);
    }
    
    public double diffalphaT(double temperature) {
        if(temperature>component.getTC()) {
            mMod = m*1.21;
        } else {
            mMod= m;
        }
        return -(1.0+mMod*(1.0-Math.sqrt(temperature/component.getTC())))*mMod/Math.sqrt(temperature/component.getTC())/component.getTC();
    }
    
    public double diffdiffalphaT(double temperature) {
        if(temperature>component.getTC()) {
            mMod = m*1.21;
        } else {
            mMod= m;
        }
        
        return  mMod*mMod/temperature/component.getTC()/2.0+(1.0+mMod*(1.0-Math.sqrt(temperature/component.getTC())))*m/Math.sqrt(temperature*temperature*temperature/(Math.pow(component.getTC(),3.0)))/(component.getTC()*component.getTC())/2.0;
        
    }
    
    public double diffaT(double temperature) {
        return component.geta()*diffalphaT(temperature);
    }
    
    public double diffdiffaT(double temperature) {
        return component.geta()*diffdiffalphaT(temperature);
    }
    
}
