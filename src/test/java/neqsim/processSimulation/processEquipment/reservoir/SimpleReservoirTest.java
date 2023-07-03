package neqsim.processSimulation.processEquipment.reservoir;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

public class SimpleReservoirTest {
  static Logger logger = LogManager.getLogger(SimpleReservoirTest.class);

  @Test
  void testRun() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 100.0), 200.00);
    testSystem.addComponent("nitrogen", 0.100);
    testSystem.addComponent("methane", 30.00);
    testSystem.addComponent("ethane", 1.0);
    testSystem.addComponent("propane", 1.0);
    testSystem.addComponent("i-butane", 1.0);
    testSystem.addComponent("n-butane", 1.0);
    testSystem.addComponent("n-hexane", 0.1);
    testSystem.addComponent("n-heptane", 0.1);
    testSystem.addComponent("n-nonane", 1.0);
    testSystem.addComponent("nC10", 1.0);
    testSystem.addComponent("nC12", 3.0);
    testSystem.addComponent("nC15", 3.0);
    testSystem.addComponent("nC20", 3.0);
    testSystem.addComponent("water", 11.0);
    testSystem.setMixingRule(2);
    testSystem.setMultiPhaseCheck(true);

    SimpleReservoir reservoirOps = new SimpleReservoir("Well 1 reservoir");
    reservoirOps.setReservoirFluid(testSystem, 5.0 * 1e7, 552.0 * 1e6, 10.0e6);

    StreamInterface producedOilStream = reservoirOps.addOilProducer("oilproducer_1");
    StreamInterface injectorGasStream = reservoirOps.addGasInjector("gasproducer_1");
    StreamInterface producedGasStream = reservoirOps.addGasProducer("SLP_A32562G");
    StreamInterface waterInjectorStream = reservoirOps.addWaterInjector("SLP_WI32562O");

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(reservoirOps);

    logger.debug("gas in place (GIP) " + reservoirOps.getGasInPlace("GSm3") + " GSm3");
    logger.debug("oil in place (OIP) " + reservoirOps.getOilInPlace("MSm3") + " MSm3");

    producedGasStream.setFlowRate(0.01, "MSm3/day");
    injectorGasStream.setFlowRate(0.01, "MSm3/day");
    producedOilStream.setFlowRate(5000000.0, "kg/day");
    waterInjectorStream.setFlowRate(1210000.0, "kg/day");
    reservoirOps.run();

    reservoirOps.runTransient(60 * 60 * 24 * 3);
    for (int i = 0; i < 1; i++) {
      reservoirOps.runTransient(60 * 60 * 24 * 15);
      logger.debug("water volume"
          + reservoirOps.getReservoirFluid().getPhase("aqueous").getVolume("m3") / 1.0e6);
      logger.debug("oil production  total" + reservoirOps.getOilProductionTotal("Sm3") + " Sm3");
      logger.debug("total produced  " + reservoirOps.getProductionTotal("MSm3 oe") + " MSm3 oe");
    }
    logger.debug("GOR gas  " + reservoirOps.getGasProducer(0).getGOR());
    logger.debug("GOR production " + reservoirOps.GORprodution());
    logger.debug("gas production  " + reservoirOps.getGasProdution("Sm3/day") + " Sm3/day");
    logger.debug("oil production  " + reservoirOps.getOilProdution("Sm3/day") + " Sm3/day");
    logger.debug("oil production  total" + reservoirOps.getOilProductionTotal("Sm3") + " Sm3");
  }
}
