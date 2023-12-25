package neqsim.processSimulation.processEquipment.reservoir;

import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.pipeline.PipeBeggsAndBrills;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.util.Adjuster;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processSystem.ProcessSystem;

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

    System.out.println("production index " + wellflow.getWellProductionIndex() + " MSm3/day/bar^2");
    System.out.println("reservoir pressure " + producedGasStream.getPressure("bara"));
    System.out
        .println("pres bottomhole " + wellflow.getOutletStream().getPressure("bara") + " bara");

  }

  @Test
  void testRunTransient() {
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
    reservoirOps.setLowPressureLimit(10.0, "bara");

    StreamInterface producedGasStream = reservoirOps.addGasProducer("gasproducer_1");
    producedGasStream.setFlowRate(9.0, "MSm3/day");

    WellFlow wellflow = new WellFlow("well flow unit");
    wellflow.setInletStream(producedGasStream);
    wellflow.setWellProductionIndex(10.000100751427403E-3);

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills(wellflow.getOutletStream());
    pipe.setPipeWallRoughness(5e-6);
    pipe.setLength(300.0);
    pipe.setElevation(300);
    pipe.setDiameter(0.625);

    PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills(wellflow.getOutletStream());
    pipeline.setPipeWallRoughness(5e-6);
    pipeline.setLength(60000.0);
    pipeline.setElevation(200);
    pipeline.setDiameter(0.725);

    ThrottlingValve chokeValve = new ThrottlingValve("chocke");
    chokeValve.setInletStream(pipeline.getOutletStream());
    chokeValve.setOutletPressure(55.0, "bara");


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

    System.out.println("production flow rate " + producedGasStream.getFlowRate("MSm3/day"));
    System.out.println("production index " + wellflow.getWellProductionIndex() + " MSm3/day/bar^2");
    System.out.println("reservoir pressure " + producedGasStream.getPressure("bara"));
    System.out
        .println("pres bottomhole " + wellflow.getOutletStream().getPressure("bara") + " bara");
    System.out.println("xmas pressure " + pipe.getOutletStream().getPressure("bara") + " bara");
    System.out
        .println("top side pressure " + pipeline.getOutletStream().getPressure("bara") + " bara");

    // process.setTimeStep(60 * 60 * 24 * 365);

    for (int i = 0; i < 10; i++) {
      reservoirOps.runTransient(60 * 60 * 24 * 365);
      process.run();
      System.out.println("production flow rate " + producedGasStream.getFlowRate("MSm3/day"));
      System.out.println("reservoir pressure " + wellflow.getInletStream().getPressure("bara"));
      System.out
          .println("pres bottomhole " + wellflow.getOutletStream().getPressure("bara") + " bara");

      System.out.println("xmas pressure " + pipe.getOutletStream().getPressure("bara") + " bara");
      System.out
          .println("top side pressure " + pipeline.getOutletStream().getPressure("bara") + " bara");
      System.out
          .println("Total produced gas " + reservoirOps.getGasProductionTotal("GMSm3") + " GMSm3");
      System.out.println("gas velocity " + pipeline.getSuperficialVelocity());

    }

    Compressor compressor = new Compressor("subcomp");
    compressor.setInletStream(pipe.getOutletStream());
    pipeline.setInletStream(compressor.getOutletStream());

    process.add(3, compressor);
    // process.run();

    for (int i = 0; i < 8; i++) {
      reservoirOps.runTransient(60 * 60 * 24 * 365);
      compressor.setOutletPressure(pipe.getOutletPressure() * 2.0);
      process.run();
      System.out.println("Compressor in pressure " + compressor.getInletStream().getPressure()
          + " out pressure  " + compressor.getOutletStream().getPressure());
      System.out.println("production flow rate " + producedGasStream.getFlowRate("MSm3/day"));
      System.out.println("reservoir pressure " + wellflow.getInletStream().getPressure("bara"));
      System.out
          .println("pres bottomhole " + wellflow.getOutletStream().getPressure("bara") + " bara");

      System.out.println("xmas pressure " + pipe.getOutletStream().getPressure("bara") + " bara");
      System.out
          .println("top side pressure " + pipeline.getOutletStream().getPressure("bara") + " bara");
      System.out
          .println("Total produced gas " + reservoirOps.getGasProductionTotal("GMSm3") + " GMSm3");
      System.out.println("gas velocity " + pipeline.getSuperficialVelocity());

    }

    adjuster.setMaxAdjustedValue(4.0);
    adjuster.setTargetVariable(pipeline.getOutletStream(), "pressure", 22.0, "bara");

    for (int i = 0; i < 5; i++) {
      reservoirOps.runTransient(60 * 60 * 24 * 365);
      compressor.setOutletPressure(pipe.getOutletPressure() * 3.5);
      process.run();
      System.out.println("Compressor in pressure " + compressor.getInletStream().getPressure()
          + " out pressure  " + compressor.getOutletStream().getPressure());
      System.out.println("production flow rate " + producedGasStream.getFlowRate("MSm3/day"));
      System.out.println("reservoir pressure " + wellflow.getInletStream().getPressure("bara"));
      System.out
          .println("pres bottomhole " + wellflow.getOutletStream().getPressure("bara") + " bara");

      System.out.println("xmas pressure " + pipe.getOutletStream().getPressure("bara") + " bara");
      System.out
          .println("top side pressure " + pipeline.getOutletStream().getPressure("bara") + " bara");
      System.out
          .println("Total produced gas " + reservoirOps.getGasProductionTotal("GMSm3") + " GMSm3");
      System.out.println("gas velocity " + pipeline.getSuperficialVelocity());

    }

    adjuster.setMaxAdjustedValue(2.0);

    for (int i = 0; i < 15; i++) {
      if (producedGasStream.getPressure() > 15) {
        reservoirOps.runTransient(60 * 60 * 24 * 365);
        compressor.setOutletPressure(pipe.getOutletPressure() * 3.5);
        process.run();

        System.out.println("Compressor in pressure " + compressor.getInletStream().getPressure()
            + " out pressure  " + compressor.getOutletStream().getPressure());
        System.out.println("production flow rate " + producedGasStream.getFlowRate("MSm3/day"));
        System.out.println("reservoir pressure " + wellflow.getInletStream().getPressure("bara"));
        System.out
            .println("pres bottomhole " + wellflow.getOutletStream().getPressure("bara") + " bara");

        System.out.println("xmas pressure " + pipe.getOutletStream().getPressure("bara") + " bara");
        System.out.println(
            "top side pressure " + pipeline.getOutletStream().getPressure("bara") + " bara");
        System.out.println(
            "Total produced gas " + reservoirOps.getGasProductionTotal("GMSm3") + " GMSm3");
        System.out.println("gas velocity " + pipeline.getSuperficialVelocity());
      }

    }



    // we install a subsea compressor


  }


}
