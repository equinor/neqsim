package neqsim.process.equipment.mixer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * @author ESOL
 */
class MixerTest {
  static neqsim.thermo.system.SystemInterface testSystem;
  static neqsim.thermo.system.SystemInterface waterSystem;
  static Stream gasStream;
  static Stream waterStream;

  /**
   * @throws java.lang.Exception
   */
  @BeforeAll
  static void setUpBeforeClass() {
    testSystem = new SystemSrkEos(298.15, 1.0);
    testSystem.addComponent("water", 0.0);
    testSystem.addComponent("methane", 1.0);
    testSystem.addComponent("ethane", 1.0);
    testSystem.addComponent("nC10", 0.1);
    testSystem.addTBPfraction("C10", 0.01, 0.366, 0.94);
    testSystem.setMixingRule(2);
    testSystem.setMultiPhaseCheck(true);

    waterSystem = testSystem.clone();
    waterSystem.setMolarComposition(new double[] { 1.0, 0.0, 0.0, 0.0, 0.0 });

    gasStream = new Stream("turbine stream", testSystem);
    gasStream.setFlowRate(1.0, "MSm3/day");
    gasStream.setTemperature(50.0, "C");
    gasStream.setPressure(2.0, "bara");
    gasStream.run();
    waterStream = new Stream("water stream", waterSystem);
    waterStream.setFlowRate(100000., "kg/day");
    waterStream.setTemperature(50.0, "C");
    waterStream.setPressure(2.0, "bara");
    waterStream.run();
  }

  /**
   * Test method for {@link neqsim.process.equipment.mixer.Mixer#run()}.
   */
  @Test
  void testRun() {
    Mixer testMixer = new Mixer("test mixer");
    testMixer.addStream(gasStream);
    testMixer.addStream(waterStream);
    testMixer.run();
    assertEquals(testMixer.calcMixStreamEnthalpy(), testMixer.getOutletStream().getFluid().getEnthalpy("J"), 1.0);
  }

  /**
   * Active inlets arriving at materially different pressures must raise the pressure-mismatch flag; the outlet still
   * takes the lowest inlet pressure.
   */
  @Test
  void testPressureMismatchFlag() {
    Stream lowP = new Stream("low pressure", testSystem.clone());
    lowP.setFlowRate(1.0, "MSm3/day");
    lowP.setTemperature(40.0, "C");
    lowP.setPressure(20.0, "bara");
    lowP.run();

    Stream highP = new Stream("high pressure", testSystem.clone());
    highP.setFlowRate(1.0, "MSm3/day");
    highP.setTemperature(40.0, "C");
    highP.setPressure(50.0, "bara"); // e.g. a compressor discharge that did reach spec
    highP.run();

    Mixer mismatchMixer = new Mixer("mismatch mixer");
    mismatchMixer.addStream(lowP);
    mismatchMixer.addStream(highP);
    mismatchMixer.run();

    assertTrue(mismatchMixer.isPressureMismatch(),
        "mixer should flag that inlets at 20 and 50 bara were collapsed to the lowest");
    assertEquals(30.0, mismatchMixer.getInletPressureSpread(), 1e-6);
    assertEquals(20.0, mismatchMixer.getOutletStream().getPressure("bara"), 1e-6);
    assertEquals(50.0, mismatchMixer.getMaxInletPressure(), 1e-6);
  }

  /**
   * Inlets at (essentially) the same pressure must NOT raise the pressure-mismatch flag.
   */
  @Test
  void testNoPressureMismatchWhenPressuresMatch() {
    Stream a = new Stream("stream a", testSystem.clone());
    a.setFlowRate(1.0, "MSm3/day");
    a.setTemperature(40.0, "C");
    a.setPressure(30.0, "bara");
    a.run();

    Stream b = new Stream("stream b", testSystem.clone());
    b.setFlowRate(1.0, "MSm3/day");
    b.setTemperature(40.0, "C");
    b.setPressure(30.0, "bara");
    b.run();

    Mixer matchedMixer = new Mixer("matched mixer");
    matchedMixer.addStream(a);
    matchedMixer.addStream(b);
    matchedMixer.run();

    assertFalse(matchedMixer.isPressureMismatch(), "equal inlet pressures must not raise the mismatch flag");
    assertEquals(0.0, matchedMixer.getInletPressureSpread(), 1e-6);
  }

  /**
   * Test method for {@link neqsim.process.equipment.mixer.Mixer#run()}.
   */
  @Test
  void testNeedRecalculation() {
    Mixer testMixer = new Mixer("test mixer");
    testMixer.addStream(gasStream);
    testMixer.addStream(waterStream);
    testMixer.run();
    ProcessSystem processOps = new ProcessSystem();
    processOps.add(gasStream);
    processOps.add(waterStream);
    processOps.add(testMixer);
    processOps.run();
    assertFalse(gasStream.needRecalculation());
    assertFalse(waterStream.needRecalculation());
    gasStream.setFlowRate(100.1, "kg/hr");
    assertTrue(gasStream.needRecalculation());
    processOps.run();
    assertFalse(gasStream.needRecalculation());
  }

  /**
   * Test method for {@link neqsim.process.equipment.mixer.Mixer#run()}.
   */
  @Test
  void testRunDifferentPressures() {
    StreamInterface gasStream2 = (StreamInterface) gasStream.clone();
    StreamInterface waterStream2 = (StreamInterface) waterStream.clone();

    gasStream2.setPressure(10.0, "bara");
    waterStream2.setPressure(30.0, "bara");

    gasStream2.run();
    waterStream2.run();

    Mixer testMixer = new Mixer("test mixer");
    testMixer.addStream(waterStream2);
    testMixer.addStream(gasStream2);
    testMixer.run();

    assertEquals(testMixer.calcMixStreamEnthalpy(), testMixer.getOutletStream().getFluid().getEnthalpy("J"), 1.0);
    assertEquals(10.0, testMixer.getOutletStream().getPressure("bara"), 1e-1);
  }

  @Test
  void testOutletEnthalpyMatchesInletSum() {
    SystemSrkEos hotFluid = new SystemSrkEos(338.15, 85.0);
    hotFluid.addComponent("methane", 0.86);
    hotFluid.addComponent("ethane", 0.14);
    hotFluid.setMixingRule("classic");

    SystemSrkEos coolFluid = new SystemSrkEos(328.15, 82.0);
    coolFluid.addComponent("methane", 0.92);
    coolFluid.addComponent("ethane", 0.08);
    coolFluid.setMixingRule("classic");

    Stream hotStream = new Stream("hot stream", hotFluid);
    hotStream.setFlowRate(15000.0, "kg/hr");
    hotStream.run();

    Stream coolStream = new Stream("cool stream", coolFluid);
    coolStream.setFlowRate(10000.0, "kg/hr");
    coolStream.run();

    double inletEnthalpyJ =
        hotStream.getFluid().getEnthalpy("J") + coolStream.getFluid().getEnthalpy("J");

    Mixer testMixer = new Mixer("enthalpy closure mixer");
    testMixer.addStream(hotStream);
    testMixer.addStream(coolStream);
    testMixer.run();

    assertEquals(inletEnthalpyJ, testMixer.getOutletStream().getFluid().getEnthalpy("J"), 1e-3);

  /**
   * Test method for mass balance conservation in Mixer.
   */
  @Test
  void testMassBalanceConservation() {
    Mixer testMixer = new Mixer("test mixer");
    testMixer.addStream(gasStream);
    testMixer.addStream(waterStream);
    testMixer.run();

    // Mass balance should be approximately zero (outlet flow - inlet flow)
    // getMassBalance() now only counts streams with flow > minimumFlow()
    double massBalance = testMixer.getMassBalance("kg/hr");
    assertEquals(0.0, massBalance, 1e-6, "Mixer mass balance error: outlet flow should equal sum of inlet flows");
  }

  @Test
  void testAddsNewComponentsFromMixedStreams() {
    SystemSrkEos nitrogenSystem = new SystemSrkEos(298.15, 10.0);
    nitrogenSystem.addComponent("nitrogen", 1.0);
    nitrogenSystem.setMixingRule(2);

    SystemSrkEos methaneSystem = new SystemSrkEos(298.15, 10.0);
    methaneSystem.addComponent("methane", 1.0);
    methaneSystem.setMixingRule(2);

    Stream nitrogenStream = new Stream("nitrogen stream", nitrogenSystem);
    Stream methaneStream = new Stream("methane stream", methaneSystem);
    nitrogenStream.run();
    methaneStream.run();

    Mixer testMixer = new Mixer("component mixer");
    testMixer.addStream(nitrogenStream);
    testMixer.addStream(methaneStream);
    testMixer.run();

    assertTrue(testMixer.getThermoSystem().getPhase(0).hasComponent("nitrogen"));
    assertTrue(testMixer.getThermoSystem().getPhase(0).hasComponent("methane"));

    double[] molarComposition = testMixer.getThermoSystem().getMolarComposition();
    assertEquals(0.5, molarComposition[0], 1e-6);
    assertEquals(0.5, molarComposition[1], 1e-6);
  }
}
