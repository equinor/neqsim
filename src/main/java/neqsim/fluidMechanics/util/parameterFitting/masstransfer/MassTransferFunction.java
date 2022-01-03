package neqsim.fluidMechanics.util.parameterFitting.masstransfer;

/**
 * <p>MassTransferFunction class.</p>
 *
 * @author Even Solbraa
 */
public class MassTransferFunction extends
        neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for MassTransferFunction.</p>
     */
    public MassTransferFunction() {
        params = new double[1];
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        system.setTemperature(dependentValues[0]);
        system.init(0);
        system.init(1);
        try {
            thermoOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            System.out.println(e.toString());
        }
        return Math.log(system.getPressure());
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        system.getPhases()[0].getComponents()[i].setAcentricFactor(value);
        system.getPhases()[1].getComponents()[i].setAcentricFactor(value);
    }
}
