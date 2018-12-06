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
public class AtractiveTermSrk extends AtractiveTermBaseClass{

    private static final long serialVersionUID = 1000;
   
    /** Creates new AtractiveTermSrk */
    public AtractiveTermSrk(ComponentEosInterface component) {
        super(component);
        m = (0.48 + 1.574 * component.getAcentricFactor() - 0.176 * component.getAcentricFactor() * component.getAcentricFactor());
    }
    
    public Object clone(){
        AtractiveTermSrk atractiveTerm = null;
        try{
            atractiveTerm = (AtractiveTermSrk) super.clone();
        }
        catch(Exception e) {
            e.printStackTrace(System.err);
        }
        return atractiveTerm;
    }
    
    public void setm(double val){
         this.m = val;
         neqsim.MathLib.nonLinearSolver.newtonRhapson solve = new neqsim.MathLib.nonLinearSolver.newtonRhapson();
         solve.setOrder(2);
         double[] acentricConstants = { -0.176, 1.574, (0.48-this.m)};
         solve.setConstants(acentricConstants);
         component.setAcentricFactor(solve.solve(0.2));
         //System.out.println("solve accen " + component.getAcentricFactor());
    }
    
    public void init(){
        m = (0.48 + 1.574 * component.getAcentricFactor() - 0.176 * component.getAcentricFactor() * component.getAcentricFactor());
    }
    
    public double alpha(double temperature){
   //     System.out.println("m " + m);
    //     System.out.println("TC " + component.getTC());
       double temp =  1.0+m*(1.0-Math.sqrt(temperature/component.getTC()));
       return temp*temp;
    }
    
    public double aT(double temperature){
        return component.geta() * alpha(temperature);
    }
    
    public double diffalphaT(double temperature) {
        //System.out.println("m " + m);
        double temp = Math.sqrt(temperature/component.getTC());
        return -(1.0+m*(1.0-temp))*m/temp/component.getTC();
    }
    
    public double diffdiffalphaT(double temperature) {
        double tr = temperature/component.getTC();
        return  m*m/temperature/component.getTC()/2.0+(1.0+m*(1.0-Math.sqrt(tr)))*m/Math.sqrt(tr*tr*tr)/(component.getTC()*component.getTC())/2.0;
        
    }
    
    public double diffaT(double temperature) {
        return component.geta()*diffalphaT(temperature);
    }
    
    public double diffdiffaT(double temperature) {
        return component.geta()*diffdiffalphaT(temperature);
    }
    
}
