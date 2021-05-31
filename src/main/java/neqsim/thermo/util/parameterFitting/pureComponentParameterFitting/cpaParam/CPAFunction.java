/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */
package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.cpaParam;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class CPAFunction extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(CPAFunction.class);

    /**
     * Creates new Test
     */
    public CPAFunction() {
    }

    @Override
	public double calcValue(double[] dependentValues) {
        // system.setTemperature(dependentValues[0]);
        system.init(0);
        // system.setPressure(system.getPhases()[0].getComponents()[0].getAntoineVaporPressure(dependentValues[0]));
        // System.out.println("pres from antoine: " + system.getPressure());
        try {
            thermoOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            logger.error(e.toString());
        }
        // System.out.println("pres: " + system.getPressure());
        return system.getPressure();
    }

    @Override
	public double calcTrueValue(double val) {
        return val;
    }

    @Override
	public void setFittingParams(int i, double value) {
        params[i] = value;

        // i += 5;

        if (i == 11) {
            system.getPhases()[0].getComponents()[0].seta(value * 1e4);
            system.getPhases()[1].getComponents()[0].seta(value * 1e4);
        }
        if (i == 10) {
            system.getPhases()[0].getComponents()[0].setb(value);
            system.getPhases()[1].getComponents()[0].setb(value);
        }
        if (i == 12) {
            system.getPhase(0).getComponent(0).getAtractiveTerm().setm(value);
            system.getPhases()[1].getComponents()[0].getAtractiveTerm().setm(value);
        }
        if (i == 14) {
            system.getPhase(0).getComponent(0).setAssociationEnergy(value * 1e4);
            system.getPhase(1).getComponent(0).setAssociationEnergy(value * 1e4);
        }
        if (i == 13) {
            system.getPhase(0).getComponent(0).setAssociationVolume(value);
            system.getPhase(1).getComponent(0).setAssociationVolume(value);
        }

        if (i == 15) {
            system.getPhase(0).getComponent(0).getAtractiveTerm().setm(value);
            system.getPhases()[1].getComponents()[0].getAtractiveTerm().setm(value);
        }
        if (i >= 5 && i < 8) {
            system.getPhases()[0].getComponents()[0].setMatiascopemanParams(i - 5, value);
            system.getPhases()[1].getComponents()[0].setMatiascopemanParams(i - 5, value);
            system.getPhases()[0].getComponents()[0].getAtractiveTerm().setParameters(i - 5, value);
            system.getPhases()[1].getComponents()[0].getAtractiveTerm().setParameters(i - 5, value);
        }
        if (i == 0) {
            system.getPhases()[0].getComponents()[0].setRacketZCPA(value);
            system.getPhases()[1].getComponents()[0].setRacketZCPA(value);
        }

        if (i == 1) {
            system.getPhases()[0].getComponents()[0].setVolumeCorrectionT_CPA(value);
            system.getPhases()[1].getComponents()[0].setVolumeCorrectionT_CPA(value);
        }

    }
}