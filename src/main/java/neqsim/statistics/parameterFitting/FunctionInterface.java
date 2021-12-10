/*
 * FunctionInterface.java
 *
 * Created on 30. januar 2001, 21:40
 */

package neqsim.statistics.parameterFitting;

import neqsim.thermo.system.SystemInterface;

/**
 * @author  Even Solbraa
 * @version
 */
public interface FunctionInterface extends Cloneable {
    public double calcValue(double[] dependentValues);

    public double calcTrueValue(double val);

    public void setFittingParams(int i, double value);

    public void setDatabaseParameters();

    public double getFittingParams(int i);

    public double[] getFittingParams();

    public int getNumberOfFittingParams();

    public void setInitialGuess(double[] guess);

    public FunctionInterface clone();

    public void setThermodynamicSystem(SystemInterface system);

    public SystemInterface getSystem();

    public double[][] getBounds();

    public double getLowerBound(int i);

    public double getUpperBound(int i);

    public void setBounds(double[][] bounds);
}
