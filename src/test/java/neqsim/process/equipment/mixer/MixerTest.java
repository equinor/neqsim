package neqsim.process.equipment.mixer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * @author ESOL
 */
class MixerTest {
  private static class InitTrackingSystemSrkEos extends SystemSrkEos {
    private static final long serialVersionUID = 1000L;
    private AtomicInteger levelTwoCalls = new AtomicInteger();
    private AtomicInteger levelThreeCalls = new AtomicInteger();

    InitTrackingSystemSrkEos(double temperature, double pressure) {
      super(temperature, pressure);
    }

    @Override
    public InitTrackingSystemSrkEos clone() {
      InitTrackingSystemSrkEos cloned = (InitTrackingSystemSrkEos) super.clone();
      if (cloned == null) {
        throw new IllegalStateException("Failed to clone initialization-tracking fluid");
      }
      cloned.levelTwoCalls = levelTwoCalls;
      cloned.levelThreeCalls = levelThreeCalls;
      return cloned;
    }

    @Override
    public void init(int initType) {
      super.init(initType);
      if (levelTwoCalls != null && initType == 2) {
        levelTwoCalls.incrementAndGet();
      } else if (levelThreeCalls != null && initType == 3) {
        levelThreeCalls.incrementAndGet();
      }
    }

    void resetInitCounts() {
      levelTwoCalls.set(0);
      levelThreeCalls.set(0);
    }

    int getLevelTwoCalls() {
      return levelTwoCalls.get();
    }

    int getLevelThreeCalls() {
      return levelThreeCalls.get();
    }
  }

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
   * An algebraic mixer must remain usable when a process switches all equipment to dynamic mode.
   */
  @Test
  void testRunTransientAsAlgebraicEquipment() {
    SystemSrkEos firstFluid = new SystemSrkEos(298.15, 10.0);
    firstFluid.addComponent("methane", 1.0);
    firstFluid.setMixingRule(2);
    Stream first = new Stream("first transient inlet", firstFluid);
    first.setFlowRate(100.0, "kg/hr");

    SystemSrkEos secondFluid = new SystemSrkEos(303.15, 10.0);
    secondFluid.addComponent("methane", 1.0);
    secondFluid.setMixingRule(2);
    Stream second = new Stream("second transient inlet", secondFluid);
    second.setFlowRate(50.0, "kg/hr");

    Mixer mixer = new Mixer("transient mixer");
    mixer.addStream(first);
    mixer.addStream(second);
    mixer.setCalculateSteadyState(false);

    ProcessSystem process = new ProcessSystem("mixer transient regression");
    process.add(first);
    process.add(second);
    process.add(mixer);

    UUID stepId = UUID.randomUUID();
    process.runTransient(2.0, stepId);
    process.runTransient(2.0, stepId);

    assertEquals(150.0, mixer.getOutletStream().getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(2.0, mixer.getTime(), 0.0,
        "repeated evaluations with the same identifier must advance the mixer clock once");
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

    double inletEnthalpyJ = hotStream.getFluid().getEnthalpy("J") + coolStream.getFluid().getEnthalpy("J");

    Mixer testMixer = new Mixer("enthalpy closure mixer");
    testMixer.addStream(hotStream);
    testMixer.addStream(coolStream);
    testMixer.run();

    assertEquals(inletEnthalpyJ, testMixer.getOutletStream().getFluid().getEnthalpy("J"), 1e-3);
  }

  /**
   * Mixer inlet enthalpy requires caloric properties but not level-3 composition derivatives. The optimized path must
   * match a level-3 reference at the base state and a nearby operating point.
   */
  @Test
  void testMixStreamEnthalpyUsesMinimumThermodynamicInitializationLevel() {
    InitTrackingSystemSrkEos fluid = new InitTrackingSystemSrkEos(323.15, 70.0);
    fluid.addComponent("nitrogen", 0.02);
    fluid.addComponent("CO2", 0.03);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-heptane", 0.04);
    fluid.setMixingRule("classic");

    Stream hotStream = new Stream("tracked hot stream", fluid);
    hotStream.setFlowRate(15000.0, "kg/hr");
    hotStream.run();

    Stream coolStream = new Stream("tracked cool stream", fluid.clone());
    coolStream.setTemperature(313.15, "K");
    coolStream.setFlowRate(10000.0, "kg/hr");
    coolStream.run();

    Mixer mixer = new Mixer("tracked enthalpy mixer");
    mixer.addStream(hotStream);
    mixer.addStream(coolStream);

    assertMinimumEnthalpyInitialization(fluid, mixer);

    coolStream.setTemperature(318.15, "K");
    coolStream.run();
    assertMinimumEnthalpyInitialization(fluid, mixer);
  }

  private static void assertMinimumEnthalpyInitialization(InitTrackingSystemSrkEos fluid, Mixer mixer) {
    double expectedEnthalpy = 0.0;
    for (StreamInterface inlet : mixer.getInletStreams()) {
      inlet.getThermoSystem().init(3);
      expectedEnthalpy += inlet.getThermoSystem().getEnthalpy();
    }

    double[] temperatures = new double[mixer.getInletStreams().size()];
    double[] pressures = new double[mixer.getInletStreams().size()];
    double[] flows = new double[mixer.getInletStreams().size()];
    for (int index = 0; index < mixer.getInletStreams().size(); index++) {
      StreamInterface inlet = mixer.getInletStreams().get(index);
      temperatures[index] = inlet.getTemperature("K");
      pressures[index] = inlet.getPressure("bara");
      flows[index] = inlet.getFlowRate("kg/hr");
    }

    fluid.resetInitCounts();
    double actualEnthalpy = mixer.calcMixStreamEnthalpy();

    assertEquals(expectedEnthalpy, actualEnthalpy, Math.max(1.0e-8, Math.abs(expectedEnthalpy) * 1.0e-12));
    assertEquals(mixer.getInletStreams().size(), fluid.getLevelTwoCalls(),
        "Every active inlet still requires caloric initialization");
    assertEquals(0, fluid.getLevelThreeCalls(), "Mixer enthalpy must not calculate composition derivatives");
    for (int index = 0; index < mixer.getInletStreams().size(); index++) {
      StreamInterface inlet = mixer.getInletStreams().get(index);
      assertEquals(temperatures[index], inlet.getTemperature("K"), 0.0);
      assertEquals(pressures[index], inlet.getPressure("bara"), 0.0);
      assertEquals(flows[index], inlet.getFlowRate("kg/hr"), 0.0);
    }
  }

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

  @Test
  void testInletOrderPermutationsProduceEquivalentMultiphaseState() {
    List<int[]> permutations = Arrays.asList(new int[] { 0, 1, 2 }, new int[] { 0, 2, 1 }, new int[] { 1, 0, 2 },
        new int[] { 1, 2, 0 }, new int[] { 2, 0, 1 }, new int[] { 2, 1, 0 });
    SystemInterface reference = null;

    for (int mixerType = 0; mixerType < 2; mixerType++) {
      for (int[] permutation : permutations) {
        Stream[] inlets = createMultiphasePermutationInlets();
        Mixer mixer = mixerType == 0 ? new Mixer("permuted mixer") : new StaticMixer("permuted static mixer");
        for (int inletIndex : permutation) {
          mixer.addStream(inlets[inletIndex]);
        }

        mixer.run();
        inlets[1].setFlowRate(0.1, "kg/hr");
        inlets[1].run();
        mixer.run();

        SystemInterface actual = mixer.getOutletStream().getFluid();
        assertTrue(actual.doMultiPhaseCheck(),
            "the mixed system must retain multiphase checking requested by any active inlet");
        assertTrue(actual.hasPhaseType("gas"));
        assertEquals(0.0, mixer.getMassBalance("kg/hr"), 1.0e-6);

        if (reference == null) {
          reference = actual.clone();
        } else {
          assertEquivalentThermodynamicState(reference, actual);
        }
      }
    }
  }

  @Test
  void testEqualFlowTemplateTieDoesNotUseInsertionOrder() {
    SystemSrkEos richFluid = new SystemSrkEos(310.15, 40.0);
    richFluid.addComponent("methane", 0.7);
    richFluid.addComponent("ethane", 0.3);
    richFluid.setMixingRule(1);

    SystemSrkEos leanFluid = new SystemSrkEos(310.15, 40.0);
    leanFluid.addComponent("methane", 0.9);
    leanFluid.addComponent("ethane", 0.1);
    leanFluid.setMixingRule(2);

    Stream rich = new Stream("equal-flow rich gas", richFluid);
    rich.setFlowRate(100.0, "kg/hr");
    rich.run();
    Stream lean = new Stream("equal-flow lean gas", leanFluid);
    lean.setFlowRate(100.0, "kg/hr");
    lean.run();

    Mixer forward = new Mixer("forward equal-flow mixer");
    forward.addStream(rich);
    forward.addStream(lean);
    forward.run();

    Mixer reverse = new Mixer("reverse equal-flow mixer");
    reverse.addStream(lean);
    reverse.addStream(rich);
    reverse.run();

    assertEquals(forward.getThermoSystem().getMixingRule(), reverse.getThermoSystem().getMixingRule());
    assertEquivalentThermodynamicState(forward.getThermoSystem(), reverse.getThermoSystem());
  }

  private static Stream[] createMultiphasePermutationInlets() {
    SystemInterface gasFluid = new SystemSrkCPAstatoil(278.45, 37.21325);
    gasFluid.addComponent("methane", 5.0);
    gasFluid.addComponent("water", 0.11833608283886514);
    gasFluid.addComponent("MEG", 0.0);
    gasFluid.setMixingRule(10);
    gasFluid.setMultiPhaseCheck(true);

    SystemInterface megFluid = gasFluid.clone();
    megFluid.setMolarComposition(new double[] { 0.0, 0.1099744114900417, 0.8900255885099583 });
    megFluid.setMultiPhaseCheck(false);

    SystemInterface waterFluid = gasFluid.clone();
    waterFluid.setMolarComposition(new double[] { 0.0, 1.0, 0.0 });
    waterFluid.setMultiPhaseCheck(false);

    Stream gas = new Stream("bulk gas", gasFluid);
    gas.setFlowRate(168958.0, "Sm3/hr");
    gas.setTemperature(29.0, "C");
    gas.setPressure(74.1, "barg");
    gas.run();

    Stream meg = new Stream("lean MEG", megFluid);
    meg.setFlowRate(0.01, "kg/hr");
    meg.setTemperature(29.0, "C");
    meg.setPressure(74.1, "barg");
    meg.run();

    Stream water = new Stream("trim water", waterFluid);
    water.setFlowRate(0.02, "kg/hr");
    water.setTemperature(29.0, "C");
    water.setPressure(74.1, "barg");
    water.run();
    return new Stream[] { gas, meg, water };
  }

  private static void assertEquivalentThermodynamicState(SystemInterface expected, SystemInterface actual) {
    assertEquals(expected.getTemperature("K"), actual.getTemperature("K"), 1.0e-8);
    assertEquals(expected.getPressure("bara"), actual.getPressure("bara"), 1.0e-10);
    assertEquals(expected.getFlowRate("kg/hr"), actual.getFlowRate("kg/hr"),
        Math.abs(expected.getFlowRate("kg/hr")) * 1.0e-10);
    assertEquals(expected.getTotalNumberOfMoles(), actual.getTotalNumberOfMoles(),
        Math.abs(expected.getTotalNumberOfMoles()) * 1.0e-10);
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    assertEquals(expected.getNumberOfComponents(), actual.getNumberOfComponents());
    for (int componentIndex = 0; componentIndex < expected.getNumberOfComponents(); componentIndex++) {
      String componentName = expected.getComponent(componentIndex).getName();
      assertEquals(expected.getComponent(componentName).getz(), actual.getComponent(componentName).getz(), 1.0e-12,
          componentName + " overall composition must be inlet-order invariant");
      assertEquals(expected.getComponent(componentName).getNumberOfmoles(),
          actual.getComponent(componentName).getNumberOfmoles(),
          Math.abs(expected.getComponent(componentName).getNumberOfmoles()) * 1.0e-10,
          componentName + " inventory must be inlet-order invariant");
    }
    for (int phaseIndex = 0; phaseIndex < expected.getNumberOfPhases(); phaseIndex++) {
      assertEquals(expected.getPhase(phaseIndex).getType(), actual.getPhase(phaseIndex).getType());
      assertEquals(expected.getPhase(phaseIndex).getBeta(), actual.getPhase(phaseIndex).getBeta(), 1.0e-10);
    }
  }
}
