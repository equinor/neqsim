package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class MultiStreamHeatExchangerTest2 {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(MultiStreamHeatExchangerTest.class);

  static neqsim.thermo.system.SystemInterface testSystem;
  Stream gasStream;

  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 60.0), 20.00);
    testSystem.addComponent("methane", 120.00);
    testSystem.addComponent("ethane", 120.0);
    testSystem.addComponent("n-heptane", 3.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  @Test
  void testRun1() {
    Stream stream_Hot = new Stream("Stream1", testSystem);
    stream_Hot.setTemperature(100.0, "C");
    stream_Hot.setFlowRate(1000.0, "kg/hr");

    Stream stream_Cold = new Stream("Stream2", testSystem.clone());
    stream_Cold.setTemperature(20.0, "C");
    stream_Cold.setFlowRate(310.0, "kg/hr");

    Stream stream_Cold2 = new Stream("Stream3", testSystem.clone());
    stream_Cold2.setTemperature(0.0, "C");
    stream_Cold2.setFlowRate(50.0, "kg/hr");

    MultiStreamHeatExchanger2 heatEx = new MultiStreamHeatExchanger2("heatEx");
    heatEx.addInStream(stream_Hot);
    heatEx.addInStream(stream_Cold);
    heatEx.addInStream(stream_Cold2);
    // heatEx.setUAvalue(1000);
    heatEx.setTemperatureApproach(5);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_Hot);
    operations.add(stream_Cold);
    operations.add(stream_Cold2);
    operations.add(heatEx);

    operations.run();

    assertEquals(95, heatEx.getOutStream(1).getTemperature("C"), 1e-3);
    assertEquals(95, heatEx.getOutStream(2).getTemperature("C"), 1e-3);
    assertEquals(70.5921794735, heatEx.getOutStream(0).getTemperature("C"), 1e-3);

    heatEx.setUAvalue(1000);

    operations.run();
    assertEquals(97.992627692, heatEx.getOutStream(1).getTemperature("C"), 1e-3);
    assertEquals(97.992627692, heatEx.getOutStream(2).getTemperature("C"), 1e-3);
    assertEquals(69.477801, heatEx.getOutStream(0).getTemperature("C"), 1e-3);
    assertEquals(1000, heatEx.getUAvalue(), 0.1);
  }

}
