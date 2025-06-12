/*
 * SampleValue.java
 *
 * Created on 22. januar 2001, 23:01
 */

package neqsim.statistics.parameterfitting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * SampleValue class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SampleValue implements Cloneable {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SampleValue.class);
  protected FunctionInterface testFunction;
  double sampleValue = 0;
  double[] dependentValues;
  String reference = "unknown";
  String description = "unknown";
  /**
   * Standard deviation of function value.
   */
  double standardDeviation = 0.0001;
  public SystemInterface system;
  public ThermodynamicOperations thermoOps;
  /**
   * Standard deviation of dependent variables.
   */
  double[] standardDeviations;

  /**
   * <p>
   * Constructor for SampleValue.
   * </p>
   */
  public SampleValue() {}

  /**
   * <p>
   * Constructor for SampleValue.
   * </p>
   *
   * @param sampleValue a double
   * @param standardDeviation a double
   * @param dependentValues an array of type double
   */
  public SampleValue(double sampleValue, double standardDeviation, double[] dependentValues) {
    this.dependentValues = new double[dependentValues.length];
    this.sampleValue = sampleValue;
    this.standardDeviation = standardDeviation;
    System.arraycopy(dependentValues, 0, this.dependentValues, 0, dependentValues.length);
  }

  /**
   * <p>
   * Constructor for SampleValue.
   * </p>
   *
   * @param sampleValue a double
   * @param standardDeviation a double
   * @param dependentValues an array of type double
   * @param standardDeviations an array of type double
   */
  public SampleValue(double sampleValue, double standardDeviation, double[] dependentValues,
      double[] standardDeviations) {
    this(sampleValue, standardDeviation, dependentValues);
    this.standardDeviations = standardDeviations;
  }

  /** {@inheritDoc} */
  @Override
  public SampleValue clone() {
    SampleValue clonedValue = null;
    try {
      clonedValue = (SampleValue) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    // this was modified 20.05.2002
    // clonedValue.system = system.clone();
    clonedValue.testFunction = testFunction.clone();
    clonedValue.dependentValues = this.dependentValues.clone();
    System.arraycopy(dependentValues, 0, clonedValue.dependentValues, 0, dependentValues.length);

    return clonedValue;
  }

  /**
   * <p>
   * setThermodynamicSystem.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setThermodynamicSystem(SystemInterface system) {
    this.system = system; // system.clone();
    thermoOps = new ThermodynamicOperations(system);
    this.getFunction().setThermodynamicSystem(this.system);
  }

  /**
   * <p>
   * setFunction.
   * </p>
   *
   * @param function a {@link neqsim.statistics.parameterfitting.BaseFunction} object
   */
  public void setFunction(BaseFunction function) {
    testFunction = function;
  }

  /**
   * <p>
   * getFunction.
   * </p>
   *
   * @return a {@link neqsim.statistics.parameterfitting.FunctionInterface} object
   */
  public FunctionInterface getFunction() {
    return testFunction;
  }

  /**
   * <p>
   * Getter for the field <code>standardDeviation</code>.
   * </p>
   *
   * @return a double
   */
  public double getStandardDeviation() {
    return standardDeviation;
  }

  /**
   * <p>
   * Getter for the field <code>standardDeviation</code>.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getStandardDeviation(int i) {
    return standardDeviations[i];
  }

  /**
   * <p>
   * Getter for the field <code>sampleValue</code>.
   * </p>
   *
   * @return a double
   */
  public double getSampleValue() {
    return sampleValue;
  }

  /**
   * <p>
   * Getter for the field <code>dependentValues</code>.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getDependentValues() {
    return dependentValues;
  }

  /**
   * <p>
   * getDependentValue.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getDependentValue(int i) {
    return dependentValues[i];
  }

  /**
   * <p>
   * Setter for the field <code>dependentValues</code>.
   * </p>
   *
   * @param vals an array of type double
   */
  public void setDependentValues(double[] vals) {
    System.arraycopy(vals, 0, this.dependentValues, 0, dependentValues.length);
  }

  /**
   * <p>
   * setDependentValue.
   * </p>
   *
   * @param i a int
   * @param val a double
   */
  public void setDependentValue(int i, double val) {
    this.dependentValues[i] = val;
  }

  /**
   * Getter for property reference.
   *
   * @return Value of property reference.
   */
  public java.lang.String getReference() {
    return reference;
  }

  /**
   * Setter for property reference.
   *
   * @param reference New value of property reference.
   */
  public void setReference(java.lang.String reference) {
    this.reference = reference;
  }

  /**
   * Getter for property description.
   *
   * @return Value of property description.
   */
  public java.lang.String getDescription() {
    return description;
  }

  /**
   * Setter for property description.
   *
   * @param description New value of property description.
   */
  public void setDescription(java.lang.String description) {
    this.description = description;
  }
}
