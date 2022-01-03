/*
 * RacketFunction.java
 *
 * Created on 24. januar 2001, 21:15
 */
package neqsim.physicalProperties.util.parameterFitting.pureComponentParameterFitting.pureCompInterfaceTension;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import org.apache.logging.log4j.*;

/**
 * <p>ParachorFunction class.</p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ParachorFunction extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(ParachorFunction.class);

    /**
     * <p>Constructor for ParachorFunction.</p>
     */
    public ParachorFunction() {
        params = new double[1];
    }

	/** {@inheritDoc} */
    @Override
	public double calcValue(double[] dependentValues) {
        system.init(3);
        try {
            thermoOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            logger.error("error", e);
        }
        system.initPhysicalProperties();
        return system.getInterphaseProperties().getSurfaceTension(0, 1) * 1e3;
    }

	/** {@inheritDoc} */
    @Override
	public void setFittingParams(int i, double value) {
        params[i] = value;
        system.getPhases()[0].getComponent(0).setParachorParameter(value);
        system.getPhases()[1].getComponent(0).setParachorParameter(value);

    }
}
