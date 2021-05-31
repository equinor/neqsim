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
public class BinaryHVparameterFitToActivityCoefficientFunction extends HuronVidalFunction {

    private static final long serialVersionUID = 1000;

    /** Creates new Test */
    public BinaryHVparameterFitToActivityCoefficientFunction() {

    }

    @Override
	public double calcValue(double[] dependentValues) {
        system.init(0);
        system.init(1);

//        double fug = system.getPhases()[1].getComponents()[0].getFugasityCoeffisient();
//        double pureFug = system.getPhases()[1].getPureComponentFugacity(0);
        double val = system.getPhase(1).getActivityCoefficient(0);
        // System.out.println("activity: " + val);
        return val;
    }

    @Override
	public double calcTrueValue(double val) {
        return val;
    }
}
