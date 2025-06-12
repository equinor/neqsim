package neqsim.statistics.parameterfitting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * Abstract BaseFunction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class BaseFunction implements FunctionInterface {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(BaseFunction.class);
  public double[] params = null;
  public double[][] bounds = null;

  public SystemInterface system;
  public ThermodynamicOperations thermoOps;

  /**
   * <p>
   * Constructor for BaseFunction.
   * </p>
   */
  public BaseFunction() {}

  /** {@inheritDoc} */
  @Override
  public BaseFunction clone() {
    BaseFunction clonedClass = null;
    try {
      clonedClass = (BaseFunction) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    // if(system!=null) clonedClass.system = system.clone();
    clonedClass.params = params.clone();
    System.arraycopy(params, 0, clonedClass.params, 0, params.length);

    return clonedClass;
  }

  /** {@inheritDoc} */
  @Override
  public void setThermodynamicSystem(SystemInterface system) {
    this.system = system;
    thermoOps = new ThermodynamicOperations(system);
  }

  /** {@inheritDoc} */
  @Override
  public double[] getFittingParams() {
    return params;
  }

  /** {@inheritDoc} */
  @Override
  public double getFittingParams(int i) {
    return params[i];
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getSystem() {
    return system;
  }

  /** {@inheritDoc} */
  @Override
  public int getNumberOfFittingParams() {
    return params.length;
  }

  /** {@inheritDoc} */
  @Override
  public void setInitialGuess(double[] guess) {
    System.out.println("start fitting " + guess.length + " parameter(s)...");
    params = new double[guess.length];
    System.arraycopy(guess, 0, params, 0, guess.length);
  }

  /** {@inheritDoc} */
  @Override
  public abstract double calcValue(double[] dependentValues);

  /** {@inheritDoc} */
  @Override
  public double calcTrueValue(double val) {
    return val;
  }

  /** {@inheritDoc} */
  @Override
  public abstract void setFittingParams(int i, double value);

  /** {@inheritDoc} */
  @Override
  public void setDatabaseParameters() {}

  /** {@inheritDoc} */
  @Override
  public double getLowerBound(int i) {
    return this.bounds[i][0];
  }

  /** {@inheritDoc} */
  @Override
  public double getUpperBound(int i) {
    return this.bounds[i][1];
  }

  /** {@inheritDoc} */
  @Override
  public double[][] getBounds() {
    return this.bounds;
  }

  /** {@inheritDoc} */
  @Override
  public void setBounds(double[][] bounds) {
    this.bounds = bounds;
  }
}
