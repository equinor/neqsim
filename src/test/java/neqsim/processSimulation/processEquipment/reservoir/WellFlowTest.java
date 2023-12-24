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
    fluid1.addPlusFraction("C7", 10.5, 180.0 / 1000.0, 840.0 / 1000.0);
    fluid1.getCharacterization().characterisePlusFraction();
    fluid1.setMixingRule(2);
    fluid1.setMultiPhaseCheck(true);

    SimpleReservoir reservoirOps = new SimpleReservoir("Well 1 reservoir");
    reservoirOps.setReservoirFluid(fluid1, 1e9, 100000.0, 10.0e7);

    StreamInterface producedGasStream = reservoirOps.addGasProducer("gasproducer_1");
    producedGasStream.setFlowRate(1000.0, "kg/day");

    WellFlow wellflow = new WellFlow("well flow unit");
    wellflow.setInletStream(producedGasStream);

    ProcessSystem process = new ProcessSystem();
    process.add(reservoirOps);
    process.add(producedGasStream);
    process.add(wellflow);


  }

  @Test
  void testRunTransient() {

  }


}
