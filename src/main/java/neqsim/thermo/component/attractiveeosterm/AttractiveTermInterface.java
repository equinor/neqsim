/*
 * AttractiveTermInterface.java
 *
 * Created on 13. mai 2001, 21:54
 */

package neqsim.thermo.component.attractiveeosterm;

/**
 * <p>
 * AttractiveTermInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface AttractiveTermInterface extends Cloneable, java.io.Serializable {
  /**
   * <p>
   * init.
   * </p>
   */
  public void init();

  /**
   * <p>
   * alpha.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double alpha(double temperature);

  /**
   * <p>
   * Calculates the the alpha function with respect to temperature.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double aT(double temperature);

  /**
   * <p>
   * Calculates the first derivative of the alpha function with respect to temperature.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double diffalphaT(double temperature);

  /**
   * <p>
   * Calculates the second derivative of the alpha function with respect to temperature.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double diffdiffalphaT(double temperature);

  /**
   * <p>
   * diffaT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double diffaT(double temperature);

  /**
   * <p>
   * diffdiffaT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double diffdiffaT(double temperature);

  /**
   * <p>
   * getParameters.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getParameters(int i);

  /**
   * <p>
   * setm.
   * </p>
   *
   * @param val a double
   */
  public void setm(double val);

  /**
   * <p>
   * setParameters.
   * </p>
   *
   * @param i a int
   * @param val a double
   */
  public void setParameters(int i, double val);

  /**
   * <p>
   * clone.
   * </p>
   *
   * @return a {@link neqsim.thermo.component.attractiveeosterm.AttractiveTermInterface} object
   */
  public AttractiveTermInterface clone();

  /**
   * <p>
   * getm.
   * </p>
   *
   * @return a double
   */
  public double getm();
}
