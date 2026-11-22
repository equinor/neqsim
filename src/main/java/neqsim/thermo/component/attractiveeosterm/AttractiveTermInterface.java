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
   * Returns the number of coefficients exposed by {@link #getParameters(int)}.
   *
   * <p>
   * The default preserves the three-coefficient contract of existing implementations. Terms with a different
   * coefficient count must override this method so callers can inspect every tunable coefficient.
   * </p>
   *
   * @return number of indexed attractive-term coefficients
   */
  public default int getNumberOfParameters() {
    return 3;
  }

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
   * Set the component this attractive term reads its critical properties from.
   *
   * <p>
   * The alpha function is evaluated from the live component state, so a cloned attractive term must be re-pointed at
   * the cloned component. Otherwise it keeps evaluating against the component it was originally built for and any
   * critical-property tuning applied after the clone is ignored.
   * </p>
   *
   * @param component the component owning this attractive term
   */
  public void setComponent(neqsim.thermo.component.ComponentEosInterface component);

  /**
   * getm.
   *
   * @return a double
   */
  public double getm();
}
