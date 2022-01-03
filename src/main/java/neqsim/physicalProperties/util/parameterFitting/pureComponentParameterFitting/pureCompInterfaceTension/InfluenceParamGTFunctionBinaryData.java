/*
 * RacketFunction.java
 *
 * Created on 24. januar 2001, 21:15
 */
package neqsim.physicalProperties.util.parameterFitting.pureComponentParameterFitting.pureCompInterfaceTension;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import org.apache.logging.log4j.*;

/**
 * <p>InfluenceParamGTFunctionBinaryData class.</p>
 *
 * @author Even Solbraa
 */
public class InfluenceParamGTFunctionBinaryData extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(InfluenceParamGTFunctionBinaryData.class);

    /**
     * <p>Constructor for InfluenceParamGTFunctionBinaryData.</p>
     */
    public InfluenceParamGTFunctionBinaryData() {
        params = new double[1];
    }

    /** {@inheritDoc} */
    @Override
	public double calcValue(double[] dependentValues) {
        system.init(3);
        try {
            thermoOps.dewPointMach(system.getPhase(0).getComponent(1).getComponentName(), "dewPointTemperature",
                    system.getTemperature());
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
        for (int kk = 0; kk < system.getPhase(0).getNumberOfComponents(); kk++) {
            system.getPhases()[0].getComponent(kk).setSurfTensInfluenceParam(i, value);
            system.getPhases()[1].getComponent(kk).setSurfTensInfluenceParam(i, value);
        }
    }
}
