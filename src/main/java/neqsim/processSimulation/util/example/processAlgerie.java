package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;

public class processAlgerie {

    private static final long serialVersionUID = 1000;

    /**
     * This method is just meant to test the thermo package.
     */
    public static void main(String args[]) {

        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkCPAstatoil((273.15 + 28.0), 90.0);
        testSystem.addComponent("methane", 79.034);
        testSystem.addComponent("ethane", 7.102);
        testSystem.addComponent("propane", 5.121);
        testSystem.addComponent("CO2", 2.502);
        testSystem.addComponent("nitrogen", 0.727);
    //    testSystem.addComponent("nC10", 2.502);
        testSystem.addComponent("water", 51.0);
//        testSystem.addComponent("TEG", 5.98);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(10);

        testSystem.setMultiPhaseCheck(true);

        Stream stream_1 = new Stream("Stream1", testSystem);

        Separator separator = new Separator("Separator 1", stream_1);

    //    ThrottlingValve valve_1 = new ThrottlingValve(separator.getGasOutStream());
    //    valve_1.setOutletPressure(75.0 + 1.01325);
        // valve_1.setIsoThermal(true);

  //      Heater heater = new Heater(valve_1.getOutStream());
  //      heater.setOutTemperature(273.15 + 46);

         Stream liquidStream = new Stream(separator.getLiquidOutStream());

        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(separator);
     //   operations.add(valve_1);
     //   operations.add(heater);
        operations.add(liquidStream);

        operations.run();
        operations.displayResult();
    }
}
