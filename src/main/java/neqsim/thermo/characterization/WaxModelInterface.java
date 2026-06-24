package neqsim.thermo.characterization;

/**
 * WaxModelInterface interface.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface WaxModelInterface extends java.io.Serializable, Cloneable {
  /**
   * addTBPWax.
   */
  public void addTBPWax();

  /**
   * clone.
   *
   * @return a {@link neqsim.thermo.characterization.WaxModelInterface} object
   */
  public WaxModelInterface clone();

  /**
   * setWaxParameters.
   *
   * @param parameters an array of type double
   */
  public void setWaxParameters(double[] parameters);

  /**
   * getWaxParameters.
   *
   * @return an array of type double
   */
  public double[] getWaxParameters();

  /**
   * setWaxParameter.
   *
   * @param i a int
   * @param parameters a double
   */
  public void setWaxParameter(int i, double parameters);

  /**
   * setParameterWaxHeatOfFusion.
   *
   * @param i a int
   * @param parameters a double
   */
  public void setParameterWaxHeatOfFusion(int i, double parameters);

  /**
   * setParameterWaxHeatOfFusion.
   *
   * @param parameterWaxHeatOfFusion an array of type double
   */
  public void setParameterWaxHeatOfFusion(double[] parameterWaxHeatOfFusion);

  /**
   * removeWax.
   */
  public void removeWax();

  /**
   * getParameterWaxHeatOfFusion.
   *
   * @return an array of type double
   */
  public double[] getParameterWaxHeatOfFusion();

  /**
   * getParameterWaxTriplePointTemperature.
   *
   * @return an array of type double
   */
  public double[] getParameterWaxTriplePointTemperature();

  /**
   * setParameterWaxTriplePointTemperature.
   *
   * @param parameterWaxTriplePointTemperature an array of type double
   */
  public void setParameterWaxTriplePointTemperature(double[] parameterWaxTriplePointTemperature);

  /**
   * setParameterWaxTriplePointTemperature.
   *
   * @param i a int
   * @param parameters a double
   */
  public void setParameterWaxTriplePointTemperature(int i, double parameters);
}
