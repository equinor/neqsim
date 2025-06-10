package neqsim.process.equipment.heatexchanger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;

public class MultiStreamHeatExchanger2Test {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(MultiStreamHeatExchanger2Test.class);

  static neqsim.thermo.system.SystemInterface testSystem;
  Stream gasStream;


  @Test
  void testRun1() {

    testSystem = new neqsim.thermo.Fluid().create("dry gas");

    testSystem.setPressure(10.0, "bara");
    testSystem.setTemperature(273.15 + 60.0, "K");
    testSystem.setMixingRule(2);

    Stream streamHot1 = new Stream("Stream1", testSystem.clone());
    streamHot1.setTemperature(100.0, "C");
    streamHot1.setFlowRate(20000.0, "kg/hr");
    Stream streamHot2 = new Stream("Stream2", testSystem.clone());
    streamHot2.setTemperature(90.0, "C");
    streamHot2.setFlowRate(20000.0, "kg/hr");
    Stream streamHot3 = new Stream("Stream3", testSystem.clone());
    streamHot3.setTemperature(70.0, "C");
    streamHot3.setFlowRate(20000.0, "kg/hr");
    Stream streamCold1 = new Stream("Stream4", testSystem.clone());
    streamCold1.setTemperature(0.0, "C");
    streamCold1.setFlowRate(20000.0, "kg/hr");
    Stream streamCold2 = new Stream("Stream5", testSystem.clone());
    streamCold2.setTemperature(10.0, "C");
    streamCold2.setFlowRate(10000.0, "kg/hr");
    Stream streamCold3 = new Stream("Stream6", testSystem.clone());
    streamCold3.setTemperature(20.0, "C");
    streamCold3.setFlowRate(20000.0, "kg/hr");

    // Set up MSHE with new-style method
    MultiStreamHeatExchanger2 heatEx = new MultiStreamHeatExchanger2("heatEx");
    heatEx.addInStreamMSHE(streamHot1, "hot", null); // unknown outlet temp
    heatEx.addInStreamMSHE(streamHot2, "hot", 80.0); // known outlet temp
    heatEx.addInStreamMSHE(streamHot3, "hot", 60.0);
    heatEx.addInStreamMSHE(streamCold1, "cold", null); // known outlet temp
    heatEx.addInStreamMSHE(streamCold2, "cold", null); // unknown outlet temp
    heatEx.addInStreamMSHE(streamCold3, "cold", 30.0); // known outlet temp

    // Two Unknowns
    heatEx.setTemperatureApproach(5.0);
    // Three Unknowns
    heatEx.setUAvalue(70000);


    // Build and run process
    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();

    operations.add(streamHot1);
    operations.add(streamHot2);
    operations.add(streamHot3);
    operations.add(streamCold1);
    operations.add(streamCold2);
    operations.add(streamCold3);
    operations.add(heatEx);

    operations.run();

    // Assertions for solved outlet temperatures
    double solvedHot1OutletTemp = heatEx.getOutStream(0).getTemperature("C");
    double hot2OutletTemp = heatEx.getOutStream(1).getTemperature("C");
    double hot3OutletTemp = heatEx.getOutStream(2).getTemperature("C");
    double cold1OutletTemp = heatEx.getOutStream(3).getTemperature("C");
    double solvedCold2OutletTemp = heatEx.getOutStream(4).getTemperature("C");
    double cold3OutletTemp = heatEx.getOutStream(5).getTemperature("C");

    // Allow some margin due to numerical method
    // assertEquals(80.0, hot2OutletTemp, 0.1);
    // assertEquals(60.0, hot3OutletTemp, 0.1);
    // assertEquals(30.0, cold3OutletTemp, 0.1);

    // assertEquals(12.09, solvedHot1OutletTemp, 2.0);
    // assertEquals(58.38, cold1OutletTemp, 2.0);
    // assertEquals(95.0, solvedCold2OutletTemp, 2.0);

    // Check UA and approach temp
    // assertEquals(5.0, heatEx.getTemperatureApproach(), 1e-2);

    // heatEx.getPrintStreams();
    // heatEx.getCompositeCurve();

  }
}
