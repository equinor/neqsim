package neqsim.process.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class BottleneckTest {
  private static final Logger logger = LogManager.getLogger(BottleneckTest.class);

  @Test
  public void testBottleneck() {
    SystemSrkEos testSystem = new SystemSrkEos(298.15, 10.0);
    testSystem.addComponent("methane", 100.0);

    Stream inletStream = new Stream("Inlet Stream", testSystem);
    inletStream.setFlowRate(1000.0, "kg/hr");

    Compressor compressor = new Compressor("Test Compressor", inletStream);
    compressor.setOutletPressure(20.0);
    // Set a low max power to make it a bottleneck
    compressor.getMechanicalDesign().setMaxDesignPower(100.0); // Watts, very low

    Separator separator = new Separator("Test Separator", compressor.getOutletStream());
    // Set a high max flow so it's not a bottleneck
    separator.getMechanicalDesign().setMaxDesignGassVolumeFlow(1000.0); // m3/hr

    ProcessSystem processSystem = new ProcessSystem();
    processSystem.add(inletStream);
    processSystem.add(compressor);
    processSystem.add(separator);

    processSystem.run();

    // Check utilization
    logger.info("Compressor utilization: " + compressor.getCapacityDuty() / compressor.getCapacityMax());
    logger.info("Separator utilization: " + separator.getCapacityDuty() / separator.getCapacityMax());

    // The compressor should be the bottleneck because we set a very low max power
    neqsim.process.equipment.ProcessEquipmentInterface bottleneck = processSystem.getBottleneck();
    logger.info("Bottleneck: " + bottleneck.getName());

    Assertions.assertEquals("Test Compressor", bottleneck.getName());
  }
}
