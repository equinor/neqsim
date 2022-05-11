package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.ionicInteractionCoefficientFitting;

/**
 * <p>
 * IonicInteractionParameterFittingFunctionCH4_1 class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class IonicInteractionParameterFittingFunctionCH4_1
        extends IonicInteractionParameterFittingFunctionCH4 {
    /**
     * <p>
     * Constructor for IonicInteractionParameterFittingFunctionCH4_1.
     * </p>
     */
    public IonicInteractionParameterFittingFunctionCH4_1() {}

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        try {
            thermoOps.bubblePointPressureFlash(false);
            // System.out.println("pres " +
            // system.getPressure()*system.getPhases()[0].getComponent(0).getx());
        } catch (Exception e) {
            // System.out.println(e.toString());
        }
        return system.getPressure() * system.getPhase(0).getComponent(1).getx();
    }
}
