/*
 * FunctionInterface.java
 *
 * Created on 30. januar 2001, 21:40
 */

package neqsim.statistics.parameterfitting;

import neqsim.thermo.system.SystemInterface;

/**
 * FunctionInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface FunctionInterface extends Cloneable {
  /**
   * calcValue.
   *
   * @param dependentValues an array of type double
   * @return a double
   */
  public double calcValue(double[] dependentValues);

  /**
   * calcTrueValue.
   *
   * @param val a double
   * @return a double
   */
  public double calcTrueValue(double val);

  /**
   * setFittingParams.
   *
   * @param i a int
   * @param value a double
   */
  public void setFittingParams(int i, double value);

  /**
   * setDatabaseParameters.
   */
  public void setDatabaseParameters();

  /**
   * getFittingParams.
   *
   * @param i a int
   * @return a double
   */
  public double getFittingParams(int i);

  /**
   * getFittingParams.
   *
   * @return an array of type double
   */
  public double[] getFittingParams();

  /**
   * getNumberOfFittingParams.
   *
   * @return a int
   */
  public int getNumberOfFittingParams();

  /**
   * setInitialGuess.
   *
   * @param guess an array of type double
   */
  public void setInitialGuess(double[] guess);

  /**
   * clone.
   *
   * @return a {@link neqsim.statistics.parameterfitting.FunctionInterface} object
   */
  public FunctionInterface clone();

  /**
   * setThermodynamicSystem.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setThermodynamicSystem(SystemInterface system);

  /**
   * getSystem.
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getSystem();

  /**
   * getBounds.
   *
   * @return an array of type double
   */
  public double[][] getBounds();

  /**
   * getLowerBound.
   *
   * @param i a int
   * @return a double
   */
  public double getLowerBound(int i);

  /**
   * getUpperBound.
   *
   * @param i a int
   * @return a double
   */
  public double getUpperBound(int i);

  /**
   * setBounds.
   *
   * @param bounds an array of type double
   */
  public void setBounds(double[][] bounds);
}
