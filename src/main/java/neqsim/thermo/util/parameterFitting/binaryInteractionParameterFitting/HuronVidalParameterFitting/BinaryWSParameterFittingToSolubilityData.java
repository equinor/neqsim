package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

/**
 * <p>
 * BinaryWSParameterFittingToSolubilityData class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class BinaryWSParameterFittingToSolubilityData extends WongSandlerFunction {
    int phase = 1;
    int type = 1;

    /**
     * <p>
     * Constructor for BinaryWSParameterFittingToSolubilityData.
     * </p>
     */
    public BinaryWSParameterFittingToSolubilityData() {}

    /**
     * <p>
     * Constructor for BinaryWSParameterFittingToSolubilityData.
     * </p>
     *
     * @param phase a int
     * @param type a int
     */
    public BinaryWSParameterFittingToSolubilityData(int phase, int type) {
        this.phase = phase;
        this.type = type;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public double calcTrueValue(double val) {
        return val;
    }
}
