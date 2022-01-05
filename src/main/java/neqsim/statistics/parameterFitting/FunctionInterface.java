/*
 * FunctionInterface.java
 *
 * Created on 30. januar 2001, 21:40
 */
package neqsim.statistics.parameterFitting;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * FunctionInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface FunctionInterface extends Cloneable {
    /**
     * <p>
     * calcValue.
     * </p>
     *
     * @param dependentValues an array of {@link double} objects
     * @return a double
     */
    public double calcValue(double[] dependentValues);

    /**
     * <p>
     * calcTrueValue.
     * </p>
     *
     * @param val a double
     * @return a double
     */
    public double calcTrueValue(double val);

    /**
     * <p>
     * setFittingParams.
     * </p>
     *
     * @param i a int
     * @param value a double
     */
    public void setFittingParams(int i, double value);

    /**
     * <p>
     * setDatabaseParameters.
     * </p>
     */
    public void setDatabaseParameters();

    /**
     * <p>
     * getFittingParams.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getFittingParams(int i);

    /**
     * <p>
     * getFittingParams.
     * </p>
     *
     * @return an array of {@link double} objects
     */
    public double[] getFittingParams();

    /**
     * <p>
     * getNumberOfFittingParams.
     * </p>
     *
     * @return a int
     */
    public int getNumberOfFittingParams();

    /**
     * <p>
     * setInitialGuess.
     * </p>
     *
     * @param guess an array of {@link double} objects
     */
    public void setInitialGuess(double[] guess);

    /**
     * <p>
     * clone.
     * </p>
     *
     * @return a {@link neqsim.statistics.parameterFitting.FunctionInterface} object
     */
    public FunctionInterface clone();

    /**
     * <p>
     * setThermodynamicSystem.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public void setThermodynamicSystem(SystemInterface system);

    /**
     * <p>
     * getSystem.
     * </p>
     *
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface getSystem();

    /**
     * <p>
     * getBounds.
     * </p>
     *
     * @return an array of {@link double} objects
     */
    public double[][] getBounds();

    /**
     * <p>
     * getLowerBound.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getLowerBound(int i);

    /**
     * <p>
     * getUpperBound.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getUpperBound(int i);

    /**
     * <p>
     * setBounds.
     * </p>
     *
     * @param bounds an array of {@link double} objects
     */
    public void setBounds(double[][] bounds);
}
