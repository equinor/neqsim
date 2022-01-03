package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

/**
 * <p>BinaryHVParameterFittingToSolubilityData2 class.</p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class BinaryHVParameterFittingToSolubilityData2 extends HuronVidalFunction {

    private static final long serialVersionUID = 1000;

    int phase = 1;


    /**
     * <p>Constructor for BinaryHVParameterFittingToSolubilityData2.</p>
     */
    public BinaryHVParameterFittingToSolubilityData2() {}

    /**
     * <p>Constructor for BinaryHVParameterFittingToSolubilityData2.</p>
     *
     * @param phase a int
     */
    public BinaryHVParameterFittingToSolubilityData2(int phase) {
        this.phase = phase;
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        thermoOps.TPflash();
        // System.out.println("x " + system.getPhases()[1].getComponents()[0].getx());
        return system.getPhases()[phase].getComponents()[0].getx();
    }

    /** {@inheritDoc} */
    @Override
    public double calcTrueValue(double val) {
        return val;
    }
}
