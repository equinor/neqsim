package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.ionicInteractionCoefficientFitting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;

/**
 * <p>
 * IonicInteractionParameterFittingFunctionCo2nacl class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class IonicInteractionParameterFittingFunctionCo2nacl extends LevenbergMarquardtFunction {
    private static final long serialVersionUID = 1000;
    static Logger logger =
            LogManager.getLogger(IonicInteractionParameterFittingFunctionCo2nacl.class);

    /**
     * <p>
     * Constructor for IonicInteractionParameterFittingFunctionCo2nacl.
     * </p>
     */
    public IonicInteractionParameterFittingFunctionCo2nacl() {}

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        try {
            thermoOps.TPflash();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return system.getPhase(1).getComponent(0).getx()
                / (1.0 - system.getPhase(1).getComponent(2).getx()
                        - system.getPhase(1).getComponent(3).getx());
    }

    /** {@inheritDoc} */
    @Override
    public double calcTrueValue(double val) {
        return val;
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        int CO2Numb = 0, Naplusnumb = 0;
        int j = 0;
        do {
            CO2Numb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName().equals("CO2"));
        j = 0;

        do {
            Naplusnumb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName()
                .equals(system.getPhases()[0].getComponents()[2].getComponentName()));

        if (i == 0) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(Naplusnumb, CO2Numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(Naplusnumb, CO2Numb, value);
        }

        if (i == 1) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijT1Parameter(Naplusnumb, CO2Numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijT1Parameter(Naplusnumb, CO2Numb, value);
        }
    }

}
