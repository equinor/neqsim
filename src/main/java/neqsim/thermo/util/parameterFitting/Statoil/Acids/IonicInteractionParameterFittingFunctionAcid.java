package neqsim.thermo.util.parameterFitting.Statoil.Acids;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;

/**
 * <p>
 * IonicInteractionParameterFittingFunctionAcid class.
 * </p>
 *
 * @author Neeraj
 * @version $Id: $Id
 */
public class IonicInteractionParameterFittingFunctionAcid extends LevenbergMarquardtFunction {
    private static final long serialVersionUID = 1000;
    int type = 0;
    int phase = 0;
    static Logger logger = LogManager.getLogger(IonicInteractionParameterFittingFunctionAcid.class);

    /**
     * <p>
     * Constructor for IonicInteractionParameterFittingFunctionAcid.
     * </p>
     */
    public IonicInteractionParameterFittingFunctionAcid() {}

    /**
     * <p>
     * Constructor for IonicInteractionParameterFittingFunctionAcid.
     * </p>
     *
     * @param phase a int
     * @param type a int
     */
    public IonicInteractionParameterFittingFunctionAcid(int phase, int type) {
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

        return system.getPressure();
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
        int MDEAplusNumb = 0, MDEANumb = 0, CO2Numb = 0, HCO3numb = 0, Waternumb = 0, CO3numb = 0,
                OHnumb = 0, AcidNumb = 0, AcnegNumb = 0;

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

        j = 0;
        do {
            AcidNumb = j;
            j++;
        } while (!system.getPhases()[1].getComponents()[j - 1].getComponentName()
                .equals("AceticAcid"));

        j = 0;
        do {
            AcnegNumb = j;
            j++;
        } while (!system.getPhases()[1].getComponents()[j - 1].getComponentName().equals("Ac-"));

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

        ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                .setWijParameter(MDEAplusNumb, Waternumb, 0.0004092282);
        ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                .setWijParameter(MDEAplusNumb, Waternumb, 0.0004092282);

        if (((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1].getRefPhase(MDEAplusNumb))
                .getElectrolyteMixingRule() != null) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0].getRefPhase(MDEAplusNumb))
                    .getElectrolyteMixingRule().setWijParameter(0, 1, 0.0004092282);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0].getRefPhase(MDEAplusNumb))
                    .getElectrolyteMixingRule().setWijParameter(1, 0, 0.0004092282);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1].getRefPhase(MDEAplusNumb))
                    .getElectrolyteMixingRule().setWijParameter(0, 1, 0.0004092282);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1].getRefPhase(MDEAplusNumb))
                    .getElectrolyteMixingRule().setWijParameter(1, 0, 0.0004092282);
        }

        ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                .setWijParameter(MDEAplusNumb, HCO3numb, -0.0001293147);
        ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                .setWijParameter(MDEAplusNumb, HCO3numb, -0.0001293147);

        ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                .setWijParameter(MDEAplusNumb, MDEANumb, 0.0019465801);
        ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                .setWijParameter(MDEAplusNumb, MDEANumb, 0.0019465801);

        ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                .setWijParameter(MDEAplusNumb, CO2Numb, 0.0002481365);
        ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                .setWijParameter(MDEAplusNumb, CO2Numb, 0.0002481365);

        ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                .setWijParameter(MDEAplusNumb, CO3numb, -0.0003581646);
        ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                .setWijParameter(MDEAplusNumb, CO3numb, -0.0003581646);

        ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                .setWijParameter(MDEAplusNumb, OHnumb, 1e-10);
        ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                .setWijParameter(MDEAplusNumb, OHnumb, 1e-10);

        if (i == 0) {
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[0]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, AcnegNumb, value);
            ((PhaseModifiedFurstElectrolyteEos) system.getPhases()[1]).getElectrolyteMixingRule()
                    .setWijParameter(MDEAplusNumb, AcnegNumb, value);
        }
    }
}
