package neqsim.thermo.characterization;

/**
 * @author ESOL
 */
public interface WaxModelInterface extends java.io.Serializable, Cloneable {

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
