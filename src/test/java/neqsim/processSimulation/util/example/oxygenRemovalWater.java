package neqsim.processSimulation.util.example;

import neqsim.processsimulation.processequipment.heatExchanger.Heater;
import neqsim.processsimulation.processequipment.mixer.Mixer;
import neqsim.processsimulation.processequipment.separator.Separator;
import neqsim.processsimulation.processequipment.stream.Stream;
import neqsim.processsimulation.processequipment.valve.ThrottlingValve;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>oxygenRemovalWater class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class oxygenRemovalWater {
    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        neqsim.thermo.Fluid fluidCreator = new neqsim.thermo.Fluid();
        fluidCreator.setHasWater(true);
        neqsim.thermo.system.SystemInterface fluid1 = fluidCreator.create("air").autoSelectModel();
        fluid1.setMultiPhaseCheck(true);
        neqsim.thermo.system.SystemInterface fluid2 = fluidCreator.create("water");
        fluid1.setPressure(ThermodynamicConstantsInterface.referencePressure);
        fluid1.setTemperature(273.15 + 10);
        fluid1.setTotalFlowRate(1.0, "kg/hr");
        fluid2.setPressure(ThermodynamicConstantsInterface.referencePressure);
        fluid2.setTemperature(273.15 + 10);
        fluid2.setTotalFlowRate(3500.0, "kg/hr");

        Stream stream_air = new Stream("StreamAir", fluid1);
        Stream stream_water = new Stream("StreamWater", fluid2);

        Mixer mix = new Mixer("mixer");
        mix.addStream(stream_air);
        // mix.addStream(stream_water);

        Separator separator = new Separator("separator", mix.getOutletStream());

        Heater heater1 = new Heater("heater1", separator.getLiquidOutStream());
        heater1.setOutTemperature(273.15 + 20);

        ThrottlingValve LP_valve = new ThrottlingValve("LPventil", heater1.getOutletStream());
        LP_valve.setOutletPressure(30.0e-3);

        neqsim.processsimulation.processsystem.ProcessSystem operations =
                new neqsim.processsimulation.processsystem.ProcessSystem();
        operations.add(stream_air);
        operations.add(stream_water);
        operations.add(mix);
        operations.add(separator);
        operations.add(heater1);
        operations.add(LP_valve);

        operations.run();
        separator.getThermoSystem().display();
        LP_valve.getThermoSystem().display();

        double wtFracO2 = LP_valve.getThermoSystem().getPhase("aqueous").getWtFrac("oxygen") * 1e9;
        System.out.println("oxygen ppb " + wtFracO2);
    }
}
