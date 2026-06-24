/*
 * AttractiveTermInterface.java
 *
 * Created on 13. mai 2001, 21:54
 */

package neqsim.thermo.component.attractiveeosterm;

/**
 * AttractiveTermInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface AttractiveTermInterface extends Cloneable, java.io.Serializable {
  /**
   * init.
   */
  public void init();

  /**
   * alpha.
   *
   * @param temperature a double
   * @return a double
   */
  public double alpha(double temperature);

  /**
   * Calculates the the alpha function with respect to temperature.
   *
   * @param temperature a double
   * @return a double
   */
  public double aT(double temperature);

  /**
   * Calculates the first derivative of the alpha function with respect to temperature.
   *
   * @param temperature a double
   * @return a double
   */
  public double diffalphaT(double temperature);

  /**
   * Calculates the second derivative of the alpha function with respect to temperature.
   *
   * @param temperature a double
   * @return a double
   */
  public double diffdiffalphaT(double temperature);

  /**
   * diffaT.
   *
   * @param temperature a double
   * @return a double
   */
  public double diffaT(double temperature);

  /**
   * diffdiffaT.
   *
   * @param temperature a double
   * @return a double
   */
  public double diffdiffaT(double temperature);

  /**
   * getParameters.
   *
   * @param i a int
   * @return a double
   */
  public double getParameters(int i);

  /**
   * setm.
   *
   * @param val a double
   */
  public void setm(double val);

  /**
   * setParameters.
   *
   * @param i a int
   * @param val a double
   */
  public void setParameters(int i, double val);

  /**
   * clone.
   *
   * @return a {@link neqsim.thermo.component.attractiveeosterm.AttractiveTermInterface} object
   */
  public AttractiveTermInterface clone();

  /**
   * getm.
   *
   * @return a double
   */
  public double getm();
}
