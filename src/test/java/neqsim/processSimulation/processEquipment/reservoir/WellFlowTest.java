package neqsim.processSimulation.processEquipment.reservoir;

import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
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

    process.setTimeStep(60 * 60 * 24 * 365);

    for (int i = 0; i < 3; i++) {
      process.runTransient();
      System.out.println("reservoir pressure " + wellflow.getInletStream().getPressure("bara"));
      System.out
          .println("pres bottomhole " + wellflow.getOutletStream().getPressure("bara") + " bara");
    }

  }

  @Test
  void testRunTransient() {

  }


}
