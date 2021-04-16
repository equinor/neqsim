/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.statistics.parameterFitting;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author Even Solbraa
 * @version
 */
public abstract class BaseFunction extends java.lang.Object implements Cloneable, FunctionInterface {

    private static final long serialVersionUID = 1000;

    public double[] params = null;
    public double[][] bounds = null;

    public SystemInterface system;
    public ThermodynamicOperations thermoOps;

    public BaseFunction() {
    }

    public Object clone() {
        BaseFunction clonedClass = null;
        try {
            clonedClass = (BaseFunction) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        // if(system!=null) clonedClass.system = (SystemInterface) system.clone();
        clonedClass.params = params.clone();
        System.arraycopy(params, 0, clonedClass.params, 0, params.length);

        return clonedClass;
    }

    public void setThermodynamicSystem(SystemInterface system) {
        this.system = system;
        thermoOps = new ThermodynamicOperations(system);
    }

    public double getFittingParams(int i) {
        return params[i];
    }

    public SystemInterface getSystem() {
        return system;
    }

    public double[] getFittingParams() {
        return params;
    }

    public int getNumberOfFittingParams() {
        return params.length;
    }

    public void setInitialGuess(double[] guess) {
        System.out.println("start fitting " + guess.length + " parameter(s)...");
        params = new double[guess.length];
        System.arraycopy(guess, 0, params, 0, guess.length);
    }

    public abstract double calcValue(double[] dependentValues);

    public double calcTrueValue(double val) {
        return val;
    }

    public abstract void setFittingParams(int i, double value);

    public void setDatabaseParameters() {
    }

    /**
     * Getter for property bounds.
     * 
     * @return Value of property bounds.
     */
    public double getLowerBound(int i) {
        return this.bounds[i][0];
    }

    public double getUpperBound(int i) {
        return this.bounds[i][1];
    }

    public double[][] getBounds() {
        return this.bounds;
    }

    /**
     * Setter for property bounds.
     * 
     * @param bounds New value of property bounds.
     */
    public void setBounds(double[][] bounds) {
        this.bounds = bounds;
    }

}
