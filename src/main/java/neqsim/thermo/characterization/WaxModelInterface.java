package neqsim.thermo.characterization;

/**
 * <p>
 * WaxModelInterface interface.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface WaxModelInterface extends java.io.Serializable, Cloneable {
  /**
   * <p>
   * addTBPWax.
   * </p>
   */
  public void addTBPWax();

  /**
   * <p>
   * clone.
   * </p>
   *
   * @return a {@link neqsim.thermo.characterization.WaxModelInterface} object
   */
  public WaxModelInterface clone();

  /**
   * <p>
   * setWaxParameters.
   * </p>
   *
   * @param parameters an array of type double
   */
  public void setWaxParameters(double[] parameters);

  /**
   * <p>
   * getWaxParameters.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getWaxParameters();

  /**
   * <p>
   * setWaxParameter.
   * </p>
   *
   * @param i a int
   * @param parameters a double
   */
  public void setWaxParameter(int i, double parameters);

  /**
   * <p>
   * setParameterWaxHeatOfFusion.
   * </p>
   *
   * @param i a int
   * @param parameters a double
   */
  public void setParameterWaxHeatOfFusion(int i, double parameters);

  /**
   * <p>
   * setParameterWaxHeatOfFusion.
   * </p>
   *
   * @param parameterWaxHeatOfFusion an array of type double
   */
  public void setParameterWaxHeatOfFusion(double[] parameterWaxHeatOfFusion);

  /**
   * <p>
   * removeWax.
   * </p>
   */
  public void removeWax();

  /**
   * <p>
   * getParameterWaxHeatOfFusion.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getParameterWaxHeatOfFusion();

  /**
   * <p>
   * getParameterWaxTriplePointTemperature.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getParameterWaxTriplePointTemperature();

  /**
   * <p>
   * setParameterWaxTriplePointTemperature.
   * </p>
   *
   * @param parameterWaxTriplePointTemperature an array of type double
   */
  public void setParameterWaxTriplePointTemperature(double[] parameterWaxTriplePointTemperature);

  /**
   * <p>
   * setParameterWaxTriplePointTemperature.
   * </p>
   *
   * @param i a int
   * @param parameters a double
   */
  public void setParameterWaxTriplePointTemperature(int i, double parameters);
}
