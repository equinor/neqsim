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
public class BinaryWSParameterFittingToSolubilityData extends WongSandlerFunction {

    private static final long serialVersionUID = 1000;

    int phase = 1;
    int type = 1;

    /** Creates new Test */
    public BinaryWSParameterFittingToSolubilityData() {
    }

    public BinaryWSParameterFittingToSolubilityData(int phase, int type) {
        this.phase = phase;
        this.type = type;
    }

    @Override
	public double calcValue(double[] dependentValues) {
        thermoOps.TPflash();
        if (type == 1) {
            // System.out.println("x " + system.getPhases()[1].getComponents()[0].getx());
            return system.getPhases()[phase].getComponents()[0].getx();
        } else {
            return system.getPhases()[phase].getComponents()[1].getx();
        }
    }

    @Override
	public double calcTrueValue(double val) {
        return val;
    }
}