package neqsim.process.mechanicaldesign;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.costestimation.CostEstimateBaseClass;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.GasScrubber;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesign;
import neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class SystemMechanicalDesignTest {
  static neqsim.process.processmodel.ProcessSystem operations;

  @BeforeAll
  static void createProcess() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.addComponent("water", 51.0);
    thermoSystem.addComponent("nitrogen", 51.0);
    thermoSystem.addComponent("CO2", 51.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 51.0);
    thermoSystem.addComponent("propane", 51.0);
    thermoSystem.addComponent("i-butane", 51.0);
    thermoSystem.addComponent("n-butane", 51.0);
    thermoSystem.addComponent("iC5", 51.0);
    thermoSystem.addComponent("nC5", 1.0);

    thermoSystem.addTBPfraction("C6", 1.0, 86.0 / 1000.0, 0.66);
    thermoSystem.addTBPfraction("C7", 1.0, 91.0 / 1000.0, 0.74);
    thermoSystem.addTBPfraction("C8", 1.0, 103.0 / 1000.0, 0.77);
    thermoSystem.addTBPfraction("C9", 1.0, 117.0 / 1000.0, 0.79);
    thermoSystem.addPlusFraction("C10_C12", 1.0, 145.0 / 1000.0, 0.80);
    thermoSystem.addPlusFraction("C13_C14", 1.0, 181.0 / 1000.0, 0.8279);
    thermoSystem.addPlusFraction("C15_C16", 1.0, 212.0 / 1000.0, 0.837);
    thermoSystem.addPlusFraction("C17_C19", 1.0, 248.0 / 1000.0, 0.849);
    thermoSystem.addPlusFraction("C20_C22", 1.0, 289.0 / 1000.0, 0.863);
    thermoSystem.addPlusFraction("C23_C25", 1.0, 330.0 / 1000.0, 0.875);
    thermoSystem.addPlusFraction("C26_C30", 1.0, 387.0 / 1000.0, 0.88);
    thermoSystem.addPlusFraction("C31_C38", 1.0, 471.0 / 1000.0, 0.90);
    thermoSystem.addPlusFraction("C38_C80", 1.0, 662.0 / 1000.0, 0.92);
    thermoSystem.setMixingRule("classic");
    thermoSystem.setMultiPhaseCheck(true);
    thermoSystem.setMolarComposition(new double[] {0.034266, 0.005269, 0.039189, 0.700553, 0.091154,
        0.050908, 0.007751, 0.014665, 0.004249, 0.004878, 0.004541, 0.007189, 0.006904, 0.004355,
        0.007658, 0.003861, 0.003301, 0.002624, 0.001857, 0.001320, 0.001426, 0.001164, 0.000916});
    // thermoSystem.prettyPrint();

    Stream feedStream = new Stream("feed stream", thermoSystem);
    feedStream.setFlowRate(604094, "kg/hr");
    feedStream.setTemperature(25.5, "C");
    feedStream.setPressure(26.0, "bara");

    neqsim.process.equipment.separator.ThreePhaseSeparator seprator1stStage =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("1st stage separator",
            feedStream);

    ThrottlingValve valve1 = new ThrottlingValve("valve1", seprator1stStage.getLiquidOutStream());
    valve1.setOutletPressure(19.0);

    Heater oilHeater = new Heater("oil heater", valve1.getOutletStream());
    oilHeater.setOutTemperature(359.0);

    neqsim.process.equipment.separator.ThreePhaseSeparator seprator2ndStage =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("2nd stage separator",
            oilHeater.getOutletStream());

    ThrottlingValve valve2 = new ThrottlingValve("valve2", seprator2ndStage.getLiquidOutStream());
    valve2.setOutletPressure(2.7);

    StreamInterface recircstream1 = valve2.getOutletStream().clone("oilRecirc1");
    recircstream1.setFlowRate(1e-6, "kg/hr");

    neqsim.process.equipment.separator.ThreePhaseSeparator seprator3rdStage =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("3rd stage separator");
    seprator3rdStage.addStream(valve2.getOutletStream());
    seprator3rdStage.addStream(recircstream1);

    ThrottlingValve pipeloss1st =
        new ThrottlingValve("pipeloss1st", seprator3rdStage.getGasOutStream());
    pipeloss1st.setOutletPressure(2.7 - 0.03);

    Heater coolerLP = new Heater("cooler LP", pipeloss1st.getOutletStream());
    coolerLP.setOutTemperature(273.15 + 25.0);

    Separator sepregenGas = new Separator("sepregenGas", coolerLP.getOutletStream());

    Pump oil1pump = new Pump("oil1pump", sepregenGas.getLiquidOutStream());
    oil1pump.setOutletPressure(19.);

    ThrottlingValve valveLP1 = new ThrottlingValve("valvseLP1", oil1pump.getOutletStream());
    valveLP1.setOutletPressure(2.7);

    Recycle recycle1 = new Recycle("oil recirc 1");
    recycle1.addStream(valveLP1.getOutletStream());
    recycle1.setOutletStream(recircstream1);
    recycle1.setTolerance(1e-2);

    operations = new neqsim.process.processmodel.ProcessSystem();
    operations.add(feedStream);
    operations.add(seprator1stStage);
    operations.add(valve1);
    operations.add(oilHeater);
    operations.add(seprator2ndStage);
    operations.add(valve2);
    operations.add(recircstream1);
    operations.add(seprator3rdStage);
    operations.add(pipeloss1st);
    operations.add(coolerLP);
    operations.add(sepregenGas);
    operations.add(oil1pump);
    operations.add(valveLP1);
    operations.add(recycle1);

    operations.run();
  }

  @Test
  void testRunDesignCalculationforProcess() {
    // Test to run desgn calculation for a full process using the
    // SystemMechanicalDesign class
    SystemMechanicalDesign mecDesign = new SystemMechanicalDesign(operations);
    mecDesign.runDesignCalculation();

    /*
     * System.out.println("total process weight " + mecDesign.getTotalWeight() + " kg");
     * System.out.println("total process volume " + mecDesign.getTotalVolume() + " m3");
     * System.out.println("total plot space " + mecDesign.getTotalPlotSpace() + " m2");
     * System.out.println("separator inner diameter " + ((Separator)
     * operations.getUnit("sepregenGas")).getMechanicalDesign().innerDiameter);
     * System.out.println("valve weight " + ((ThrottlingValve)
     * operations.getUnit("valve1")).getMechanicalDesign().getWeightTotal());
     */
  }

  @Test
  void testRunDesignCalculationforSeparator() {
    // Test to run design calculation for a process unit (separator using the
    // SeparatorMechanicalDesign class)
    SeparatorMechanicalDesign sepMechDesign =
        new SeparatorMechanicalDesign((Separator) operations.getUnit("sepregenGas"));
    sepMechDesign.calcDesign();
    /*
     * System.out.println("separator inner diameter " + sepMechDesign.innerDiameter);
     * System.out.println("separator weight vessel shell " + sepMechDesign.weigthVesselShell);
     * System.out.println("separator weight structual steel " + sepMechDesign.weightStructualSteel);
     */
  }

  @Test
  void testRunDesignCalculationforGasScrubber() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 120.0);
    thermoSystem.addComponent("nitrogen", 1.0);
    thermoSystem.addComponent("methane", 99.0);
    thermoSystem.setMixingRule("classic");

    Stream inlets = new Stream("inlet stream", thermoSystem);
    inlets.setTemperature(20.0, "C");
    inlets.setPressure(120.0, "bara");
    inlets.setFlowRate(15.0, "MSm3/day");
    inlets.run();

    GasScrubber sep1 = new GasScrubber("scrubber1", inlets);
    sep1.run();

    GasScrubberMechanicalDesign sepMechDesign = new GasScrubberMechanicalDesign(sep1);
    sepMechDesign.setMaxOperationPressure(180);
    sepMechDesign.calcDesign();
    /*
     * System.out.println("separator inner diameter " + sepMechDesign.innerDiameter);
     * System.out.println("separator weight vessel shell " + sepMechDesign.weigthVesselShell);
     * System.out.println("separator weight structual steel " + sepMechDesign.weightStructualSteel);
     */
    sep1.addSeparatorSection("first mesh", "meshpad");
    sepMechDesign.calcDesign();
  }

  @Test
  void testRunDesignCalculationforValve() {
    ValveMechanicalDesign valve1MechDesign =
        new ValveMechanicalDesign((ThrottlingValve) operations.getUnit("valve1"));
    valve1MechDesign.calcDesign();
    // System.out.println("valve total weight " + valve1MechDesign.getWeightTotal());
  }

  @Test
  void testRunDesignForPipeline() {
    AdiabaticPipe pipe = new AdiabaticPipe("pipe1",
        ((neqsim.process.equipment.separator.ThreePhaseSeparator) operations
            .getUnit("1st stage separator")).getGasOutStream());
    pipe.setDiameter(1.0);
    pipe.setLength(1000.0);
    pipe.setPipeWallRoughness(10e-6);
    pipe.setInletElevation(0.0);
    pipe.setOutletElevation(20.0);

    pipe.run();

    // System.out.println("out pressure " + pipe.getOutletStream().getPressure("bara"));

    PipelineMechanicalDesign pipeMechDesign = new PipelineMechanicalDesign(pipe);
    pipeMechDesign.setMaxOperationPressure(100.0);
    pipeMechDesign.setMaxOperationTemperature(273.155 + 60.0);
    pipeMechDesign.setMinOperationPressure(50.0);
    pipeMechDesign.setMaxDesignGassVolumeFlow(100.0);
    pipeMechDesign.setCompanySpecificDesignStandards("Statoil");
    pipeMechDesign.calcDesign();

    // System.out.println("wall thickness " + pipeMechDesign.getWallThickness());
  }

  @Test
  void testCostEstimateProcess() {
    SystemMechanicalDesign mecDesign = new SystemMechanicalDesign(operations);
    mecDesign.runDesignCalculation();

    CostEstimateBaseClass costEst = new CostEstimateBaseClass(mecDesign);
    costEst.getWeightBasedCAPEXEstimate();

    // System.out.println("weight based cost estmate " + costEst.getWeightBasedCAPEXEstimate());
  }
}
