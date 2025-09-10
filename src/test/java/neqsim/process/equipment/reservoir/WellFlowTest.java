package neqsim.process.equipment.reservoir;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;

public class WellFlowTest {
  @Test
  void testRun() {
    neqsim.thermo.system.SystemInterface fluid1 =
        new neqsim.thermo.system.SystemPrEos(373.15, 100.0);
    fluid1.addComponent("water", 3.599);
    fluid1.addComponent("nitrogen", 0.599);
    fluid1.addComponent("CO2", 0.51);
    fluid1.addComponent("methane", 62.8);
    fluid1.addComponent("n-heptane", 12.8);
    fluid1.setMixingRule(2);
    fluid1.setMultiPhaseCheck(true);

    SimpleReservoir reservoirOps = new SimpleReservoir("Well 1 reservoir");
    reservoirOps.setReservoirFluid(fluid1, 1e9, 10.0, 10.0e7);

    StreamInterface producedGasStream = reservoirOps.addGasProducer("gasproducer_1");
    producedGasStream.setFlowRate(1.0, "MSm3/day");

    WellFlow wellflow = new WellFlow("well flow unit");
    wellflow.setInletStream(producedGasStream);
    wellflow.setWellProductionIndex(5.000100751427403E-4);

    ProcessSystem process = new ProcessSystem();
    process.add(reservoirOps);
    process.add(wellflow);

    process.run();
    /*
     * System.out.println("production index " + wellflow.getWellProductionIndex() +
     * " MSm3/day/bar^2"); System.out.println("reservoir pressure " +
     * producedGasStream.getPressure("bara")); System.out .println("pres bottomhole " +
     * wellflow.getOutletStream().getPressure("bara") + " bara");
     */
  }

  @Test
  void testRunTransientRes2() {
    neqsim.thermo.system.SystemInterface fluid1 =
        new neqsim.thermo.system.SystemPrEos(298.15, 38.0);

    fluid1.addComponent("water", 3.599);
    fluid1.addComponent("nitrogen", 0.599);
    fluid1.addComponent("CO2", 0.51);
    fluid1.addComponent("methane", 99.8);
    fluid1.setMixingRule(2);
    fluid1.setMultiPhaseCheck(false);

    double producxtionIndex = 10.000100751427403E-3;

    neqsim.process.equipment.reservoir.SimpleReservoir reservoirOps =
        new neqsim.process.equipment.reservoir.SimpleReservoir("Well 1 reservoir");
    reservoirOps.setReservoirFluid(fluid1.clone(), 700000000.0, 1.0, 10.0e7);
    reservoirOps.setLowPressureLimit(10.0, "bara");

    StreamInterface producedGasStream = reservoirOps.addGasProducer("SLP_A32566GI");
    producedGasStream.setFlowRate(9.0, "MSm3/day");

    neqsim.process.equipment.reservoir.WellFlow wellflow =
        new neqsim.process.equipment.reservoir.WellFlow("well flow unit");
    wellflow.setInletStream(producedGasStream);
    wellflow.setWellProductionIndex(producxtionIndex);

    neqsim.process.equipment.pipeline.PipeBeggsAndBrills pipe =
        new neqsim.process.equipment.pipeline.PipeBeggsAndBrills("pipe",
            wellflow.getOutletStream());
    pipe.setPipeWallRoughness(5e-6);
    pipe.setLength(170.0);
    pipe.setElevation(170);
    pipe.setDiameter(0.625);

    neqsim.process.equipment.compressor.Compressor compressor =
        new neqsim.process.equipment.compressor.Compressor("subcomp");
    compressor.setInletStream(pipe.getOutletStream());
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.6);
    compressor.setCompressionRatio(2.0);

    neqsim.process.equipment.heatexchanger.Cooler intercooler =
        new neqsim.process.equipment.heatexchanger.Cooler("cooler", compressor.getOutletStream());
    intercooler.setOutTemperature(25.0, "C");

    neqsim.process.equipment.compressor.Compressor compressor2 =
        new neqsim.process.equipment.compressor.Compressor("subcomp2");
    compressor2.setInletStream(intercooler.getOutletStream());
    compressor2.setUsePolytropicCalc(true);
    compressor2.setPolytropicEfficiency(0.6);
    compressor2.setCompressionRatio(2.0);

    neqsim.process.equipment.heatexchanger.Heater cooler1 =
        new neqsim.process.equipment.heatexchanger.Heater("cooler 1",
            compressor2.getOutletStream());
    cooler1.setOutTemperature(30.0, "C");

    neqsim.process.equipment.pipeline.PipeBeggsAndBrills pipeline =
        new neqsim.process.equipment.pipeline.PipeBeggsAndBrills("pipeline",
            cooler1.getOutletStream());
    pipeline.setPipeWallRoughness(50e-6);
    pipeline.setLength(50 * 1e3);
    pipeline.setElevation(0);
    pipeline.setDiameter(17.0 * 0.0254);
    double richgas_inlet_pressure = 150.0;
    double max_gas_production = 9.0;

    neqsim.process.equipment.util.Adjuster adjuster =
        new neqsim.process.equipment.util.Adjuster("adjuster");
    adjuster.setTargetVariable(pipeline.getOutletStream(), "pressure", richgas_inlet_pressure,
        "bara");
    adjuster.setAdjustedVariable(producedGasStream, "flow", "MSm3/day");
    adjuster.setMaxAdjustedValue(max_gas_production);
    adjuster.setMinAdjustedValue(1.0);

    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(reservoirOps);
    process.add(wellflow);
    process.add(pipe);
    process.add(compressor);
    process.add(intercooler);
    process.add(compressor2);
    process.add(cooler1);
    process.add(pipeline);
    process.add(adjuster);
    process.run();

    // System.out
    // .println("gas production " + reservoirOps.getGasProdution("Sm3/day") / 1e6 + " MSm3/day");
  }

  @Test
  void testRunTransient() {
    neqsim.thermo.system.SystemInterface fluid1 =
        new neqsim.thermo.system.SystemPrEos(298.15, 60.0);
    fluid1.addComponent("water", 3.599);
    fluid1.addComponent("nitrogen", 0.599);
    fluid1.addComponent("CO2", 0.51);
    fluid1.addComponent("methane", 62.8);
    fluid1.setMixingRule(2);
    fluid1.setMultiPhaseCheck(true);

    SimpleReservoir reservoirOps = new SimpleReservoir("Well 1 reservoir");
    reservoirOps.setReservoirFluid(fluid1, 7e8, 1.0, 10.0e7);
    reservoirOps.setLowPressureLimit(10.0, "bara");

    StreamInterface producedGasStream = reservoirOps.addGasProducer("gasproducer_1");
    producedGasStream.setFlowRate(9.0, "MSm3/day");

    WellFlow wellflow = new WellFlow("well flow unit");
    wellflow.setInletStream(producedGasStream);
    wellflow.setWellProductionIndex(10.000100751427403E-3);

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe", wellflow.getOutletStream());
    pipe.setPipeWallRoughness(5e-6);
    pipe.setLength(300.0);
    pipe.setElevation(300);
    pipe.setDiameter(0.625);

    PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("pipeline", pipe.getOutletStream());
    pipeline.setPipeWallRoughness(5e-6);
    pipeline.setLength(60000.0);
    pipeline.setElevation(200);
    pipeline.setDiameter(0.725);

    ThrottlingValve chokeValve = new ThrottlingValve("chocke");
    chokeValve.setInletStream(pipeline.getOutletStream());
    chokeValve.setOutletPressure(5.0, "bara");

    Adjuster adjuster = new Adjuster("adjuster");
    adjuster.setTargetVariable(pipeline.getOutletStream(), "pressure",
        chokeValve.getOutletPressure(), "bara");
    adjuster.setAdjustedVariable(producedGasStream, "flow", "MSm3/day");
    adjuster.setMaxAdjustedValue(9.0);
    adjuster.setMinAdjustedValue(1.0);

    ProcessSystem process = new ProcessSystem();
    process.add(reservoirOps);
    process.add(wellflow);
    process.add(pipe);
    process.add(pipeline);
    process.add(adjuster);
    process.run();
    /*
     * System.out.println("production flow rate " + producedGasStream.getFlowRate("MSm3/day"));
     * System.out.println("production index " + wellflow.getWellProductionIndex() +
     * " MSm3/day/bar^2"); System.out.println("reservoir pressure " +
     * producedGasStream.getPressure("bara")); System.out .println("pres bottomhole " +
     * wellflow.getOutletStream().getPressure("bara") + " bara");
     * System.out.println("xmas pressure " + pipe.getOutletStream().getPressure("bara") + " bara");
     * System.out .println("top side pressure " + pipeline.getOutletStream().getPressure("bara") +
     * " bara");
     */
    // process.setTimeStep(60 * 60 * 24 * 365);

    for (int i = 0; i < 8; i++) {
      reservoirOps.runTransient(60 * 60 * 365);
      process.run();
      if (pipeline.getOutletStream().getPressure("bara") < 5.0) {
        continue;
      }
      /*
       * System.out.println("production flow rate " + producedGasStream.getFlowRate("MSm3/day"));
       * System.out.println("reservoir pressure " + wellflow.getInletStream().getPressure("bara"));
       * System.out .println("pres bottomhole " + wellflow.getOutletStream().getPressure("bara") +
       * " bara");
       *
       * System.out.println("xmas pressure " + pipe.getOutletStream().getPressure("bara") +
       * " bara"); System.out .println("top side pressure " +
       * pipeline.getOutletStream().getPressure("bara") + " bara"); System.out
       * .println("Total produced gas " + reservoirOps.getGasProductionTotal("GMSm3") + " GMSm3");
       * System.out.println("gas velocity " + pipeline.getInletSuperficialVelocity());
       */
    }
  }

  @Test
  void testCalcWellFlow() {
    neqsim.thermo.system.SystemInterface fluid1 =
        new neqsim.thermo.system.SystemPrEos(373.15, 100.0);
    fluid1.addComponent("water", 3.599);
    fluid1.addComponent("nitrogen", 0.599);
    fluid1.addComponent("CO2", 0.51);
    fluid1.addComponent("methane", 62.8);
    fluid1.setMixingRule(2);
    fluid1.setMultiPhaseCheck(true);

    SimpleReservoir reservoirOps = new SimpleReservoir("Well 1 reservoir");
    reservoirOps.setReservoirFluid(fluid1, 1e9, 1.0, 10.0e7);
    reservoirOps.setLowPressureLimit(10.0, "bara");

    StreamInterface producedGasStream = reservoirOps.addGasProducer("gasproducer_1");
    producedGasStream.setFlowRate(9.0, "MSm3/day");

    WellFlow wellflow = new WellFlow("well flow unit");
    wellflow.setInletStream(producedGasStream);

    double permeability = 50.0; // milli darcy
    // wellflow.setDarcyLawParameters(permeability, );
    // wellflow.setWellProductionIndex(10.000100751427403E-3);
  }

  @Test
  void testVogelCorrelation() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemPrEos(298.15, 200.0);
    fluid.addComponent("methane", 100.0);
    fluid.setMixingRule(2);

    SimpleReservoir reservoir = new SimpleReservoir("res");
    reservoir.setReservoirFluid(fluid, 1.0e9, 0.0, 0.0);

    StreamInterface prod = reservoir.addGasProducer("well1");
    prod.setFlowRate(0.5, "MSm3/day");

    WellFlow wellflow = new WellFlow("wf");
    wellflow.setInletStream(prod);
    wellflow.setVogelParameters(0.5, 100.0, 200.0);

    ProcessSystem process = new ProcessSystem();
    process.add(reservoir);
    process.add(wellflow);
    process.run();

    assert (Math.abs(wellflow.getOutletStream().getPressure("bara") - 100.0) < 1.0);
  }
}
