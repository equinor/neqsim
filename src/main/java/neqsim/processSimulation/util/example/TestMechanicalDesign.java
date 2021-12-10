package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;

/**
 *
 * @author esol
 */
public class TestMechanicalDesign {
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 20.0), 66.00);
        testSystem.addComponent("methane", 5e6, "Sm3/day");
        testSystem.addComponent("water", 3000, "kg/hr");

        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        Stream stream_1 = new Stream("Stream1", testSystem);
        /*
         * AdiabaticPipe pipel = new AdiabaticPipe(stream_1); pipel.setDiameter(1.0);
         * pipel.setLength(100);
         */

        Separator sep = new Separator(stream_1);
        // sep.addSeparatorSection("tray", "");
        // sep.getMechanicalDesign().getMaterialDesignStandard().readMaterialDesignStandard("Carbon
        // Steel Plates and Sheets", "SA-516", "55", 1);
        // sep.getMechanicalDesign().getJointEfficiencyStandard().readJointEfficiencyStandard("Double
        // Welded", "Fully Redipgraphed");
        sep.getMechanicalDesign().setMaxOperationPressure(150.0);
        sep.addSeparatorSection("top mesh", "meshpad");

        /*
         * sep.setInternalDiameter(3.750); sep.setSeparatorLength(4.0);
         * sep.getMechanicalDesign().setMaxOperationPressure(70.0);
         * sep.addSeparatorSection("bottom manway", "manway");
         * sep.addSeparatorSection("dp nozzle 1", "nozzle");
         * sep.getSeparatorSection("dp nozzle 1").getMechanicalDesign().
         * setNominalSize("DN 100"); sep.addSeparatorSection("dp nozzle 2", "nozzle");
         * sep.getSeparatorSection("dp nozzle 2").getMechanicalDesign().
         * setNominalSize("DN 100"); sep.addSeparatorSection("inlet vane", "vane");
         * sep.getSeparatorSection("inlet vane").setCalcEfficiency(true);
         * sep.addSeparatorSection("top mesh", "meshpad");
         * sep.getSeparatorSection(1).setCalcEfficiency(true);
         * sep.addSeparatorSection("top manway", "manway");
         * sep.getSeparatorSection("top manway").getMechanicalDesign().setANSIclass(300)
         * ; sep.getSeparatorSection("top manway").getMechanicalDesign().
         * setNominalSize("DN 500");
         */
        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(sep);
        operations.run();
        operations.displayResult();
        sep.getMechanicalDesign().calcDesign();
        // do design of process
        // operations.getSystemMechanicalDesign().setCompanySpecificDesignStandards("StatoilTR");

        // operations.runProcessDesignCalculation();
        // prosessdesign do: calculate flow rates of TEG, amine, calculates heat duty
        // requirements, compressor power requirements, number of trays, packing height,
        // numner of theoretical stages,
        // operations.runMechanicalDesignCalculation();
        // calculates diameters of colums, wall thickness, weight, size of equipment,
        // tray spacing
        // operations.getSystemMechanicalDesign().runDesignCalculation();

        // operations.getSystemMechanicalDesign().setDesign();
        // operations.run();
        // sep.getGasLoadFactor();
        // operations.calcDesign();
        // sep.getMechanicalDesign().calcDesign();

        // scrubber.getMechanicalDesign().setDesignStandard("ASME - Pressure Vessel
        // Code");
        // scrubber.getMechanicalDesign().setDesignStandard("BS 5500 - Pressure
        // Vessel");
        // System.out.println("vane top veight " + sep.getSeparatorSection("inlet
        // vane").getMechanicalDesign().getTotalWeight());
        // System.out.println("gas vel " + sep.getGasSuperficialVelocity());
        // System.out.println("gas load factor oil " + sep.getGasLoadFactor());
        // System.out.println("gas load factor water " + scrubber.getGasLoadFactor(2));
        // System.out.println("derated gas load factor oil " +
        // sep.getDeRatedGasLoadFactor());
        // System.out.println("derated gas load factor water " +
        // scrubber.getDeRatedGasLoadFactor(2));
        // System.out.println("minimum liquid seal height " +
        // sep.getSeparatorSection(0).getMinimumLiquidSealHeight());
        // sep.getMechanicalDesign().displayResults();
    }
}
