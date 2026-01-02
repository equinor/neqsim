package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;

public class MultiStreamHeatExchanger2Test {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(MultiStreamHeatExchanger2Test.class);

  static neqsim.thermo.system.SystemInterface testSystem;
  Stream gasStream;

  @Test
  void testRun1() {
    // Three Uknown temperatures

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

    assertEquals(12.09, solvedHot1OutletTemp, 1.0);
    assertEquals(58.38, cold1OutletTemp, 1.0);
    assertEquals(95.0, solvedCold2OutletTemp, 1.0);

    // Check UA and approach temp
    assertEquals(5.0, heatEx.getTemperatureApproach(), 1e-2);
    assertEquals(70000, heatEx.getUA(), 1e-2);

    // Composite Curve Points for Ploting
    heatEx.getCompositeCurve();
  }

  @Test
  void testRun2() {
    // Two Uknown temperatures

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
    heatEx.addInStreamMSHE(streamCold1, "cold", 10.0); // known outlet temp
    heatEx.addInStreamMSHE(streamCold2, "cold", null); // unknown outlet temp
    heatEx.addInStreamMSHE(streamCold3, "cold", 30.0); // known outlet temp

    // Two Unknowns
    heatEx.setTemperatureApproach(5.0);

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
    assertEquals(80.0, hot2OutletTemp, 0.1);
    assertEquals(60.0, hot3OutletTemp, 0.1);
    assertEquals(10.0, cold1OutletTemp, 1.0);
    assertEquals(30.0, cold3OutletTemp, 0.1);

    assertEquals(60.4, solvedHot1OutletTemp, 1.0);
    assertEquals(95.0, solvedCold2OutletTemp, 1.0);

    // Check UA and approach temp
    assertEquals(5.0, heatEx.getTemperatureApproach(), 1e-2);
    assertEquals(28883, heatEx.getUA(), 2.0);

    // Composite Curve Points for Ploting
    heatEx.getCompositeCurve();
  }

  @Test
  void testRun3() {
    // One Uknown temperature

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
    heatEx.addInStreamMSHE(streamCold1, "cold", 10.0); // known outlet temp
    heatEx.addInStreamMSHE(streamCold2, "cold", 90.0); // unknown outlet temp
    heatEx.addInStreamMSHE(streamCold3, "cold", 30.0); // known outlet temp

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
    assertEquals(80.0, hot2OutletTemp, 0.1);
    assertEquals(60.0, hot3OutletTemp, 0.1);
    assertEquals(10.0, cold1OutletTemp, 1.0);
    assertEquals(30.0, cold3OutletTemp, 0.1);
    assertEquals(90.0, solvedCold2OutletTemp, 1.0);

    assertEquals(63.0, solvedHot1OutletTemp, 1.0);

    // Check UA and approach temp
    assertEquals(10.0, heatEx.getTemperatureApproach(), 1);
    assertEquals(21825, heatEx.getUA(), 1.0);

    // Composite Curve Points for Ploting
    heatEx.getCompositeCurve();
  }

  @Test
  void testRun4() {
    /*
     * ================================================= HOT STREAMS (H1 – H4)
     * =================================================
     */

    // ---------- H1 ----------
    neqsim.thermo.system.SystemInterface H1Fluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    H1Fluid.addComponent("methane", 0.55);
    H1Fluid.addComponent("ethane", 0.30);
    H1Fluid.addComponent("propane", 0.15);
    H1Fluid.setMixingRule(10);
    H1Fluid.setMultiPhaseCheck(false);
    H1Fluid.init(0);

    Stream H1 = new Stream("H1", H1Fluid);
    H1.setFlowRate(6.0, "kg/hr");
    H1.setTemperature(180, "C");
    H1.setPressure(30, "bara");
    H1.run();

    // ---------- H2 ----------
    neqsim.thermo.system.SystemInterface H2Fluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    H2Fluid.addComponent("methane", 0.50);
    H2Fluid.addComponent("ethane", 0.35);
    H2Fluid.addComponent("propane", 0.15);
    H2Fluid.setMixingRule(10);
    H2Fluid.setMultiPhaseCheck(false);
    H2Fluid.init(0);

    Stream H2 = new Stream("H2", H2Fluid);
    H2.setFlowRate(5.0, "kg/hr");
    H2.setTemperature(160, "C");
    H2.setPressure(28, "bara");
    H2.run();

    // ---------- H3 ----------
    neqsim.thermo.system.SystemInterface H3Fluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    H3Fluid.addComponent("methane", 0.45);
    H3Fluid.addComponent("ethane", 0.35);
    H3Fluid.addComponent("propane", 0.20);
    H3Fluid.setMixingRule(10);
    H3Fluid.setMultiPhaseCheck(false);
    H3Fluid.init(0);

    Stream H3 = new Stream("H3", H3Fluid);
    H3.setFlowRate(12.0, "kg/hr");
    H3.setTemperature(140, "C");
    H3.setPressure(35, "bara");
    H3.run();

    // ---------- H4 ----------
    neqsim.thermo.system.SystemInterface H4Fluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    H4Fluid.addComponent("methane", 0.60);
    H4Fluid.addComponent("ethane", 0.25);
    H4Fluid.addComponent("propane", 0.15);
    H4Fluid.setMixingRule(10);
    H4Fluid.setMultiPhaseCheck(false);
    H4Fluid.init(0);

    Stream H4 = new Stream("H4", H4Fluid);
    H4.setFlowRate(2.0, "kg/hr");
    H4.setTemperature(150, "C");
    H4.setPressure(32, "bara");
    H4.run();

    /*
     * ================================================= COLD STREAMS (C1 – C4)
     * =================================================
     */

    // ---------- C1 ----------
    neqsim.thermo.system.SystemInterface C1Fluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    C1Fluid.addComponent("methane", 0.25);
    C1Fluid.addComponent("ethane", 0.35);
    C1Fluid.addComponent("propane", 0.40);
    C1Fluid.setMixingRule(10);
    C1Fluid.setMultiPhaseCheck(false);
    C1Fluid.init(0);

    Stream C1 = new Stream("C1", C1Fluid);
    C1.setFlowRate(6.0, "kg/hr");
    C1.setTemperature(30, "C");
    C1.setPressure(15, "bara");
    C1.run();

    // ---------- C2 ----------
    neqsim.thermo.system.SystemInterface C2Fluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    C2Fluid.addComponent("methane", 0.30);
    C2Fluid.addComponent("ethane", 0.40);
    C2Fluid.addComponent("propane", 0.30);
    C2Fluid.setMixingRule(10);
    C2Fluid.setMultiPhaseCheck(false);
    C2Fluid.init(0);

    Stream C2 = new Stream("C2", C2Fluid);
    C2.setFlowRate(4.0, "kg/hr");
    C2.setTemperature(50, "C");
    C2.setPressure(20, "bara");
    C2.run();

    // ---------- C3 ----------
    neqsim.thermo.system.SystemInterface C3Fluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    C3Fluid.addComponent("methane", 0.20);
    C3Fluid.addComponent("ethane", 0.45);
    C3Fluid.addComponent("propane", 0.35);
    C3Fluid.setMixingRule(10);
    C3Fluid.setMultiPhaseCheck(false);
    C3Fluid.init(0);

    Stream C3 = new Stream("C3", C3Fluid);
    C3.setFlowRate(10.0, "kg/hr");
    C3.setTemperature(30, "C");
    C3.setPressure(18, "bara");
    C3.run();

    // ---------- C4 ----------
    neqsim.thermo.system.SystemInterface C4Fluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    C4Fluid.addComponent("methane", 0.35);
    C4Fluid.addComponent("ethane", 0.35);
    C4Fluid.addComponent("propane", 0.30);
    C4Fluid.setMixingRule(10);
    C4Fluid.setMultiPhaseCheck(false);
    C4Fluid.init(0);

    Stream C4 = new Stream("C4", C4Fluid);
    C4.setFlowRate(2.0, "kg/hr");
    C4.setTemperature(40, "C");
    C4.setPressure(25, "bara");
    C4.run();

    /*
     * ----------------------------------------------------------------- MULTI-STREAM HEAT EXCHANGER
     * SETUP – testRun4 -----------------------------------------------------------------
     */

    // 1. Create the exchanger object
    MultiStreamHeatExchanger2 heatEx = new MultiStreamHeatExchanger2("heatEx4");

    /*
     * 2. Register the eight inlet streams Pass • "hot" / "cold" • outlet-temperature set-point
     * (null = unknown / to be solved) • order matters: heatEx.getOutStream(i) returns in same order
     */
    // ----- HOT SIDE -----
    heatEx.addInStreamMSHE(H1, "hot", 80.0); // H1Out fixed
    heatEx.addInStreamMSHE(H2, "hot", 90.0); // H2Out fixed
    heatEx.addInStreamMSHE(H3, "hot", null); // H3Out to be solved
    heatEx.addInStreamMSHE(H4, "hot", 60.0); // H4Out fixed

    // ----- COLD SIDE -----
    heatEx.addInStreamMSHE(C1, "cold", 110.0); // C1Out fixed
    heatEx.addInStreamMSHE(C2, "cold", 130.0); // C2Out fixed
    heatEx.addInStreamMSHE(C3, "cold", null); // C3Out to be solved
    heatEx.addInStreamMSHE(C4, "cold", 90.0); // C4Out fixed

    heatEx.setTemperatureApproach(10);

    ThrottlingValve valve1 = new ThrottlingValve("valve1", heatEx.getOutStream(2));
    valve1.setOutletPressure(20.0, "bara");

    /* 3. Build and run the process model */
    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();

    operations.add(H1);
    operations.add(H2);
    operations.add(H3);
    operations.add(H4);
    operations.add(C1);
    operations.add(C2);
    operations.add(C3);
    operations.add(C4);
    operations.add(heatEx);
    operations.add(valve1);

    operations.run(); // <–– calls the exchanger solver

    /*
     * ----------------------------------------------------------------- ASSERTIONS – outlet
     * temperatures, ΔTmin -----------------------------------------------------------------
     */

    // Grab the solved outlet temperatures (order = same as addInStreamMSHE)
    double H1OutTemp = heatEx.getOutStream(0).getTemperature("C");
    double H2OutTemp = heatEx.getOutStream(1).getTemperature("C");
    double H3OutTemp = heatEx.getOutStream(2).getTemperature("C"); // solved
    double H4OutTemp = heatEx.getOutStream(3).getTemperature("C");

    double C1OutTemp = heatEx.getOutStream(4).getTemperature("C");
    double C2OutTemp = heatEx.getOutStream(5).getTemperature("C");
    double C3OutTemp = heatEx.getOutStream(6).getTemperature("C"); // solved
    double C4OutTemp = heatEx.getOutStream(7).getTemperature("C");

    // Allow small numerical tolerance (same style as your earlier test)
    assertEquals(80.0, H1OutTemp, 0.1);
    assertEquals(90.0, H2OutTemp, 0.1);
    assertEquals(60.0, H4OutTemp, 0.1);

    assertEquals(110.0, C1OutTemp, 0.2);
    assertEquals(130.0, C2OutTemp, 0.2);
    assertEquals(90.0, C4OutTemp, 0.1);

    // These two were unknown — compare with the HYSYS results
    assertEquals(66.3, H3OutTemp, 1.0); // HYSYS shows 66.35 °C
    assertEquals(163.0, C3OutTemp, 1.0); // HYSYS shows 163 °C

    assertEquals(55.48, valve1.getOutletStream().getTemperature("C"), 0.1);

    // ΔTmin (pinch) and UA check
    assertEquals(10.0, heatEx.getTemperatureApproach(), 1.0);

    /* 4. (Optional) get composite-curve data for plotting */
    heatEx.getCompositeCurve();
  }
}
