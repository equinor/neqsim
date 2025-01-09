package neqsim.process.equipment.reservoir;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.StreamInterface;

public class SimpleReservoirTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SimpleReservoirTest.class);

  @Test
  void testRun2() {
    neqsim.thermo.system.SystemInterface fluid1 =
        new neqsim.thermo.system.SystemPrEos(373.15, 900.0);
    fluid1.addComponent("water", 3.599);
    fluid1.addComponent("nitrogen", 0.599);
    fluid1.addComponent("CO2", 0.51);
    fluid1.addComponent("methane", 62.8);
    fluid1.addComponent("ethane", 8.12);
    fluid1.addComponent("propane", 4.95);
    fluid1.addComponent("i-butane", 1.25);
    fluid1.addComponent("n-butane", 1.25);
    fluid1.addComponent("i-pentane", 0.25);
    fluid1.addComponent("n-pentane", 0.25);
    fluid1.addComponent("n-hexane", 0.81);
    fluid1.addPlusFraction("C7", 10.5, 180.0 / 1000.0, 840.0 / 1000.0);
    fluid1.getCharacterization().characterisePlusFraction();
    fluid1.setMixingRule(2);
    fluid1.setMultiPhaseCheck(true);

    SimpleReservoir reservoirOps = new SimpleReservoir("Well 1 reservoir");
    reservoirOps.setReservoirFluid(fluid1, 0, 635949179.71, 10.0e7);

    StreamInterface producedOilStream = reservoirOps.addOilProducer("oilproducer_1");
    producedOilStream.setFlowRate(6500.0 / 0.86 * 1000.0 * 4, "kg/day");

    StreamInterface producedWaterStream = reservoirOps.addWaterProducer("waterproducer_1");
    producedWaterStream.setFlowRate(10000, "kg/day");

    StreamInterface injectorGasStream = reservoirOps.addGasInjector("gasinjector_1");
    neqsim.thermo.system.SystemInterface fluidGas = fluid1.clone();
    fluidGas.setMolarComposition(new double[] {0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0});
    injectorGasStream.setFluid(fluidGas);
    injectorGasStream.setFlowRate(5.0, "MSm3/day");

    StreamInterface injectorWaterStream = reservoirOps.addWaterInjector("waterinjector_1");
    neqsim.thermo.system.SystemInterface fluidWater = fluid1.clone();
    fluidWater.setMolarComposition(new double[] {1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0});
    injectorWaterStream.setFluid(fluidWater);
    injectorWaterStream.setFlowRate(8000.0 * 1000 / 3.0 * 2, "kg/day");

    reservoirOps.run();

    double deltaTime = 24 * 60 * 60.0 * 365;
    for (int i = 0; i < 10; i++) {
      reservoirOps.runTransient(deltaTime);
    }
    Assertions.assertEquals(355.19330033985693,
        reservoirOps.getReservoirFluid().getPressure("bara"), 0.1);
    Assertions.assertEquals(11.698, reservoirOps.getWaterProdution("Sm3/day"), 0.1);

    reservoirOps.setLowPressureLimit(52.0e5, "Pa");
    Assertions.assertEquals(52.0, reservoirOps.getLowPressureLimit("bara"), 0.1);
    Assertions.assertEquals(52.0e5, reservoirOps.getLowPressureLimit("Pa"), 0.1);
  }
}
