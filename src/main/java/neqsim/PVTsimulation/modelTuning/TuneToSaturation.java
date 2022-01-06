package neqsim.PVTsimulation.modelTuning;

import neqsim.PVTsimulation.simulation.SimulationInterface;

/**
 * <p>
 * TuneToSaturation class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class TuneToSaturation extends BaseTuningClass {
    /**
     * <p>
     * Constructor for TuneToSaturation.
     * </p>
     *
     * @param simulation a {@link neqsim.PVTsimulation.simulation.SimulationInterface} object
     */
    public TuneToSaturation(SimulationInterface simulation) {
        super(simulation);
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        double error = 1.0;
        double maxError = 0.01;
        int plusNumber = 0;
        double plusMolarMass = 0;
        getSimulation().getThermoSystem().setTemperature(saturationTemperature);
        getSimulation().getThermoSystem().setPressure(saturationPressure - 50.0);
        // getSimulation().getThermoSystem().display();
        for (int i = 0; i < getSimulation().getThermoSystem().getPhase(0)
                .getNumberOfComponents(); i++) {
            if (getSimulation().getThermoSystem().getPhase(0).getComponent(i).isIsPlusFraction()) {
                plusNumber = i;
                plusMolarMass = getSimulation().getThermoSystem().getPhase(0)
                        .getComponent(plusNumber).getMolarMass();
            }
        }
        getSimulation().getThermoSystem().getCharacterization().characterisePlusFraction();
        getSimulation().getThermoSystem().createDatabase(true);
        getSimulation().getThermoSystem()
                .setMixingRule(getSimulation().getThermoSystem().getMixingRule());
        getSimulation().getThermoSystem().init(0);
        getSimulation().getThermoSystem().init(1);

        getSimulation().run();
        double dp = getSimulation().getThermoSystem().getPressure() - saturationPressure;
        if (Math.abs(dp) < maxError) {
            return;
        }
        double dpOld = 0;
        int iter = 0;
        double sign = 1.0;
        while (Math.abs(dp) > 1e-3 && iter < 50) {
            iter++;
            dp = getSimulation().getThermoSystem().getPressure() - saturationPressure;
            plusMolarMass -= sign * dp / 1000.0;
            getSimulation().setThermoSystem(getSimulation().getBaseThermoSystem().clone());
            getSimulation().getThermoSystem().resetCharacterisation();
            getSimulation().getThermoSystem().createDatabase(true);
            getSimulation().getThermoSystem()
                    .setMixingRule(getSimulation().getThermoSystem().getMixingRule());
            // getSimulation().getThermoSystem().init(0);
            // getSimulation().getThermoSystem().init(1);
            getSimulation().getThermoSystem().setTemperature(saturationTemperature);
            getSimulation().getThermoSystem().setPressure(saturationPressure);
            // getSimulation().getThermoSystem().display();
            for (int i = 0; i < getSimulation().getThermoSystem().getMaxNumberOfPhases(); i++) {
                getSimulation().getThermoSystem().getPhase(i).getComponent(plusNumber)
                        .setMolarMass(plusMolarMass);
            }
            // getSimulation().getThermoSystem().display();
            getSimulation().getThermoSystem().getCharacterization().characterisePlusFraction();
            getSimulation().getThermoSystem().createDatabase(true);
            getSimulation().getThermoSystem()
                    .setMixingRule(getSimulation().getThermoSystem().getMixingRule());
            // getSimulation().getThermoSystem().init(0);
            // getSimulation().getThermoSystem().init(1);
            getSimulation().run();
            if (Math.abs(dpOld) < Math.abs(dp)) {
                sign *= -1.0;
            }
            dpOld = dp;

            System.out.println("pressure " + getSimulation().getThermoSystem().getPressure() + "dp "
                    + dp + " molarmass" + plusMolarMass);
        }
    }
}
