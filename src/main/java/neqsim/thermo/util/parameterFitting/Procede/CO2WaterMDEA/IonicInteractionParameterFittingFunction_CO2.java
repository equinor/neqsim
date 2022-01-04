package neqsim.thermo.util.parameterFitting.Procede.CO2WaterMDEA;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;

/**
 * <p>
 * IonicInteractionParameterFittingFunction_CO2 class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class IonicInteractionParameterFittingFunction_CO2 extends LevenbergMarquardtFunction {
    private static final long serialVersionUID = 1000;
    int type = 0;
    int phase = 0;
    static Logger logger = LogManager.getLogger(IonicInteractionParameterFittingFunction_CO2.class);

    /**
     * <p>
     * Constructor for IonicInteractionParameterFittingFunction_CO2.
     * </p>
     */
    public IonicInteractionParameterFittingFunction_CO2() {}

    /**
     * <p>
     * Constructor for IonicInteractionParameterFittingFunction_CO2.
     * </p>
     *
     * @param phase a int
     * @param type a int
     */
    public IonicInteractionParameterFittingFunction_CO2(int phase, int type) {
        this.phase = phase;
        this.type = type;
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        try {
            thermoOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            logger.error(e.toString());
        }
        if (type == 0) {
            return (system.getPressure() * system.getPhases()[0].getComponent(0).getx()
                    * system.getPhase(0).getComponent(0).getFugasityCoeffisient());
        } else {
            return (system.getPressure());
        }
    }

    /** {@inheritDoc} */
    @Override
    public double calcTrueValue(double val) {
        return (val);
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
        params[i] = value;
        int MDEAplusNumb = 0, MDEANumb = 0, CO2Numb = 0, HCO3numb = 0, Waternumb = 0, CO3numb = 0,
                OHnumb = 0;

        int j = 0;
        do {
            MDEAplusNumb = j;
            j++;
        } while (!system.getPhases()[1].getComponents()[j - 1].getComponentName().equals("MDEA+"));
        j = 0;

        do {
            MDEANumb = j;
            j++;
        } while (!system.getPhases()[1].getComponents()[j - 1].getComponentName().equals("MDEA"));

        j = 0;
        do {
            CO2Numb = j;
            j++;
        } while (!system.getPhases()[1].getComponents()[j - 1].getComponentName().equals("CO2"));

        if (CO2Numb != 0) {
            logger.error("-------------ERROR in CO2 number------------");
        }

        j = 0;
        do {
            HCO3numb = j;
            j++;
        } while (!system.getPhases()[1].getComponents()[j - 1].getComponentName().equals("HCO3-"));

        j = 0;
        do {
            CO3numb = j;
            j++;
        } while (!system.getPhases()[0].getComponents()[j - 1].getComponentName().equals("CO3--"));

        j = 0;
        do {
            OHnumb = j;
            j++;
        } while (!system.getPhases()[1].getComponents()[j - 1].getComponentName().equals("OH-"));

        j = 0;
        do {
            Waternumb = j;
            j++;
        } while (!system.getPhases()[1].getComponents()[j - 1].getComponentName().equals("water"));

        if (i == 0) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, Waternumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, Waternumb, value);

            if (((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1].getRefPhase(MDEAplusNumb))
                    .getElectrolyteMixingRule() != null) {
                ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0].getRefPhase(MDEAplusNumb))
                        .getElectrolyteMixingRule().setWijParameter(0, 1, value);
                ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0].getRefPhase(MDEAplusNumb))
                        .getElectrolyteMixingRule().setWijParameter(1, 0, value);
                ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1].getRefPhase(MDEAplusNumb))
                        .getElectrolyteMixingRule().setWijParameter(0, 1, value);
                ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1].getRefPhase(MDEAplusNumb))
                        .getElectrolyteMixingRule().setWijParameter(1, 0, value);
            }
        }

        if (i == 1) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, HCO3numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, HCO3numb, value);
        }

        if (i == 2) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, MDEANumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, MDEANumb, value);
        }

        if (i == 3) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, CO2Numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, CO2Numb, value);
        }

        if (i == 4) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, CO3numb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, CO3numb, value);
        }

        // if(i==0){
        ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                .setWijParameter(MDEAplusNumb, OHnumb, 1e-10);
        ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                .setWijParameter(MDEAplusNumb, OHnumb, 1e-10);
        // }
    }
}
