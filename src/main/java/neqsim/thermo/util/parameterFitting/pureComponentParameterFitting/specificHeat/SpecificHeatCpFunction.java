package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.specificHeat;

import org.apache.logging.log4j.*;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

public class SpecificHeatCpFunction extends LevenbergMarquardtFunction {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(SpecificHeatCpFunction.class);

    public SpecificHeatCpFunction() {
        params = new double[4];
    }

    @Override
    public double calcValue(double[] dependentValues) {
        system.init(0);
        try {
            thermoOps.TPflash();
        } catch (Exception e) {
            logger.error(e.toString());
        }

        system.init(3);
        return system.getPhase(0).getCp("kJ/kgK");
    }

    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        if (i == 1) {
            system.getPhases()[0].getComponents()[0].setCpB(value);
            system.getPhases()[1].getComponents()[0].setCpB(value);
        }
        if (i == 0) {
            system.getPhases()[0].getComponents()[0].setCpA(value);
            system.getPhases()[1].getComponents()[0].setCpA(value);
        }
        if (i == 2) {
            system.getPhases()[0].getComponents()[0].setCpC(value);
            system.getPhases()[1].getComponents()[0].setCpC(value);
        }
        if (i == 3) {
            system.getPhases()[0].getComponents()[0].setCpD(value);
            system.getPhases()[1].getComponents()[0].setCpD(value);
        }
    }
}
