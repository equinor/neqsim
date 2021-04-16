/*
 * AtractiveTermInterface.java
 *
 * Created on 13. mai 2001, 21:54
 */

package neqsim.thermo.component.atractiveEosTerm;

/**
 *
 * @author esol
 * @version
 */
public interface AtractiveTermInterface extends Cloneable {
    public void init();

    public double alpha(double temperature);

    public double aT(double temperature);

    public double diffalphaT(double temperature);

    public double diffdiffalphaT(double temperature);

    public double diffaT(double temperature);

    public double diffdiffaT(double temperature);

    public double getParameters(int i);

    public void setm(double val);

    public void setParameters(int i, double val);

    public Object clone();

}
