package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.separator.GasScrubberSimple;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class simpleGasScrubber {

    private static final long serialVersionUID = 1000;

    /**
     * This method is just meant to test the thermo package.
     */
    public static void main(String args[]) {

        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 20.00);
        testSystem.addComponent("methane", 1200.00);
        testSystem.addComponent("water", 1200.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testOps.TPflash();

        Stream stream_1 = new Stream("Stream1", testSystem);

        GasScrubberSimple gasScrubber = new GasScrubberSimple("Scrubber", stream_1);
        // gasScrubber.addScrubberSection("mesh");
        // gasScrubber.addScrubberSection("mesh2");
        Stream stream_2 = new Stream(gasScrubber.getGasOutStream());
        stream_2.setName("gas from scrubber");
        Stream stream_3 = new Stream(gasScrubber.getLiquidOutStream());
        stream_3.setName("liquid from scrubber");

        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(gasScrubber);
        operations.add(stream_2);
        operations.add(stream_3);
        operations.run();
        // operations.displayResult();

        operations.getSystemMechanicalDesign().setCompanySpecificDesignStandards("StatoilTR");

        // gasScrubber.getMechanicalDesign().calcDesign();
        // gasScrubber.getMechanicalDesign().displayResults();
        operations.getSystemMechanicalDesign().runDesignCalculation();
        double vol = operations.getSystemMechanicalDesign().getTotalVolume();
    }
}