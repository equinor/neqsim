/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */

package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class BinaryHVParameterFittingToSolubilityData2 extends HuronVidalFunction {

    private static final long serialVersionUID = 1000;

    int phase = 1;

    /** Creates new Test */
    public BinaryHVParameterFittingToSolubilityData2() {
    }

    public BinaryHVParameterFittingToSolubilityData2(int phase) {
        this.phase = phase;
    }

    @Override
	public double calcValue(double[] dependentValues) {
        thermoOps.TPflash();
        // System.out.println("x " + system.getPhases()[1].getComponents()[0].getx());
        return system.getPhases()[phase].getComponents()[0].getx();
    }

    @Override
	public double calcTrueValue(double val) {
        return val;
    }
}