/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.thermo.characterization;

import java.io.Serializable;

/**
 *
 * @author ESOL
 */
public interface WaxModelInterface extends Serializable, Cloneable {
 
    public void addTBPWax();

    public Object clone();

    public void setWaxParameters(double[] parameters);

    public double[] getWaxParameters();

    public void setWaxParameter(int i, double parameters);

    public void setParameterWaxHeatOfFusion(int i, double parameters);

    public void removeWax();

    public double[] getParameterWaxHeatOfFusion();

    public void setParameterWaxHeatOfFusion(double[] parameterWaxHeatOfFusion);

    public double[] getParameterWaxTriplePointTemperature();

    public void setParameterWaxTriplePointTemperature(double[] parameterWaxTriplePointTemperature);
    
   public void setParameterWaxTriplePointTemperature(int i, double parameters);
}
