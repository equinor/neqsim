package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

public class oxygenRemovalWater {
    public static void main(String[] args) {
        neqsim.thermo.Fluid.setHasWater(true);
        neqsim.thermo.system.SystemInterface fluid1 = neqsim.thermo.Fluid.create("air").autoSelectModel();
        fluid1.setMultiPhaseCheck(true);
        neqsim.thermo.system.SystemInterface fluid2 = neqsim.thermo.Fluid.create("water");
        fluid1.setPressure(1.01325);
        fluid1.setTemperature(273.15 + 10);
        fluid1.setTotalFlowRate(1.0, "kg/hr");
        fluid2.setPressure(1.01325);
        fluid2.setTemperature(273.15 + 10);
        fluid2.setTotalFlowRate(3500.0, "kg/hr");

        Stream stream_air = new Stream("StreamAir", fluid1);
        Stream stream_water = new Stream("StreamWater", fluid2);

        Mixer mix = new Mixer("mixer");
        mix.addStream(stream_air);
        // mix.addStream(stream_water);

        Separator separator = new Separator(mix.getOutStream());

        Heater heater1 = new Heater(separator.getLiquidOutStream());
        heater1.setOutTemperature(273.15 + 20);

        ThrottlingValve LP_valve = new ThrottlingValve("LPventil", heater1.getOutStream());
        LP_valve.setOutletPressure(30.0e-3);

        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
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
