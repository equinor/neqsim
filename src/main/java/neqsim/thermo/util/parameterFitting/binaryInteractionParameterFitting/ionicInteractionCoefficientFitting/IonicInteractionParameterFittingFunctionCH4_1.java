package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.ionicInteractionCoefficientFitting;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class IonicInteractionParameterFittingFunctionCH4_1
        extends IonicInteractionParameterFittingFunctionCH4 {
    private static final long serialVersionUID = 1000;

    public IonicInteractionParameterFittingFunctionCH4_1() {}

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
