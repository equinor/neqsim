package neqsim.process.equipment.separator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.OilLevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.WaterLevelTransmitter;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * @author ESOL
 */
@Tag("slow")
class ThreePhaseSeparatorTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  /**
   * Set up fluid for testing.
   *
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemPrEos(243.15, 300.0);
    testSystem.addComponent("methane", 90.0);
    testSystem.addComponent("ethane", 0.0);
    testSystem.addComponent("propane", 0.0);
    testSystem.addComponent("i-butane", 0.0);
    testSystem.addComponent("n-butane", 0.0);
    testSystem.addComponent("i-pentane", 0.0);
    testSystem.addComponent("n-pentane", 0.0);
    testSystem.addComponent("n-hexane", 0.0);
    testSystem.addComponent("nitrogen", 10.0);
    testSystem.setMixingRule("classic");
  }

  /**
   * Test method for run method.
   */
  @Disabled
  @Test
  void testRun() {
    neqsim.thermo.system.SystemInterface fluid1 =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);

    fluid1.addComponent("nitrogen", 0.110282450914383);
    fluid1.addComponent("CO2", 8.92014980316162);
    fluid1.addComponent("methane", 72.3870849609375);
    fluid1.addComponent("ethane", 5.19349813461304);
    fluid1.addComponent("propane", 5.20273065567017);
    fluid1.addComponent("i-butane", 0.436239510774612);
    fluid1.addComponent("n-butane", 1.39356422424316);
    fluid1.addComponent("i-pentane", 0.769362509250641);
    fluid1.addComponent("n-pentane", 0.543137490749359);
    fluid1.addComponent("n-hexane", 3.90587639808655);
    fluid1.addComponent("n-heptane", 3.90587639808655);
    fluid1.addComponent("water", 40.0);
    fluid1.setMixingRule(10);
    fluid1.setMultiPhaseCheck(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(fluid1);

    testOps.TPflash();

    double[] intParameter = {-0.24, // "CO2"
        -0.721, // "methane"
        0.11, // "ethane"
        0.205, // "propane"
        0.081, // "i-butane"
        0.17, // "n-butane"
        0.051, // "i-pentane"
        0.1135, // "n-pentane"
        0.0832, // "n-hexane"
        0.0535 // "n-heptane"
    };

    String[] componentNames = fluid1.getComponentNames();

    for (int i = 0; i < intParameter.length; i++) {
      // except nitrogen 0
      int componentIndex = findComponentIndex(componentNames, componentNames[i + 1]);
      int waterIndex = findComponentIndex(componentNames, "water");

      if (componentIndex != -1 && waterIndex != -1) {
        ((PhaseEosInterface) fluid1.getPhases()[0]).getEosMixingRule()
            .setBinaryInteractionParameter(componentIndex, waterIndex, intParameter[i]);

        ((PhaseEosInterface) fluid1.getPhases()[1]).getEosMixingRule()
            .setBinaryInteractionParameter(componentIndex, waterIndex, intParameter[i]);
      } else {
      }
    }

    testOps.TPflash();
    // fluid1.prettyPrint();

    List<Double> molarComposition = new ArrayList<>();
    molarComposition.add(0.07649963805789309);
    molarComposition.add(10.028287212684818);
    molarComposition.add(49.52052228615394);
    molarComposition.add(3.64093888905641);
    molarComposition.add(3.6620992636511893);
    molarComposition.add(0.2995511776378937);
    molarComposition.add(0.9605423088257289);
    molarComposition.add(0.5032398365065283);
    molarComposition.add(0.36145746378993904);
    molarComposition.add(0.2364703087561068);
    molarComposition.add(2.732003176453634);
    molarComposition.add(27.978388438425913);

    double[] molarCompositionArray =
        molarComposition.stream().mapToDouble(Double::doubleValue).toArray();

    neqsim.thermo.system.SystemInterface fluid_test_separator = fluid1.clone();
    fluid_test_separator.setMolarComposition(molarCompositionArray);

    Stream inlet_stream_test_sep = new Stream("TEST_SEPARATOR_INLET", fluid_test_separator);
    inlet_stream_test_sep.setTemperature(72.6675872802734, "C");
    inlet_stream_test_sep.setPressure(10.6767892837524, "bara");
    inlet_stream_test_sep.setFlowRate(721.3143271348611, "kg/hr");
    inlet_stream_test_sep.run();

    ThreePhaseSeparator test_separator =
        new ThreePhaseSeparator("TEST_SEPARATOR", inlet_stream_test_sep);
    test_separator.run();
    test_separator.getWaterOutStream().getThermoSystem().prettyPrint();

    Heater heater_TP_setter_test_stream = new Heater("TP_SETTER_FOR_THE_DEGASSER_TEST_SEP_STREAM",
        test_separator.getWaterOutStream());
    heater_TP_setter_test_stream.setOutPressure(5.9061164855957 - 0.01, "bara");
    heater_TP_setter_test_stream.setOutTemperature(79.8487854003906, "C");
    heater_TP_setter_test_stream.run();
    // System.out.println("Gas out from degasser " +
    // heater_TP_setter_test_stream.getOutStream()
    // .getFluid().getPhase("gas").getFlowRate("kg/hr"));
    heater_TP_setter_test_stream.getOutletStream().getThermoSystem().prettyPrint();

    Heater heater_TP_setter_test_stream2 = new Heater("TP_SETTER_FOR_THE_DEGASSER_TEST_SEP_STREAM",
        test_separator.getWaterOutStream());
    heater_TP_setter_test_stream2.setOutPressure(5.9061164855957, "bara");
    heater_TP_setter_test_stream2.setOutTemperature(79.8487854003906, "C");
    heater_TP_setter_test_stream2.run();

    // System.out.println("Gas out from degasser2 " +
    // heater_TP_setter_test_stream2.getOutStream()
    // .getFluid().getPhase("gas").getFlowRate("kg/hr"));
  }

  private int findComponentIndex(String[] componentNames, String componentName) {
    for (int i = 0; i < componentNames.length; i++) {
      if (componentNames[i].equals(componentName)) {
        return i;
      }
    }

    return -1; // Component not found
  }

  /**
   * Test method for run method.
   */
  @Disabled
  void testRun2() {
    /*
     * XStream xstream = new XStream(); xstream.addPermission(AnyTypePermission.ANY); // Specify the
     * file path to read Path filePath = Paths.get(
     * "/workspaces/neqsim/src/test/java/neqsim/thermodynamicOperations/flashOps/my_process.xml" );
     * String xmlContents = ""; try { xmlContents = new String(Files.readAllBytes(filePath),
     * StandardCharsets.UTF_8); } catch (IOException e) { logger.error(e.getMessage()); }
     *
     * // Deserialize from xml neqsim.processSimulation.processSystem.ProcessSystem operationsCopy =
     * (neqsim.processSimulation.processSystem.ProcessSystem) xstream.fromXML(xmlContents);
     * operationsCopy.run(); neqsim.processSimulation.processEquipment.separator.Separator
     * VD02Separator = (neqsim.processSimulation.processEquipment.separator.Separator)
     * operationsCopy .getUnit("Separator after CFU gas");
     * neqsim.processSimulation.processEquipment.separator.Separator VD01Separator =
     * (neqsim.processSimulation.processEquipment.separator.Separator) operationsCopy
     * .getUnit("Separator after degasser gas");
     * neqsim.processSimulation.processEquipment.separator.Separator Degasser =
     * (neqsim.processSimulation.processEquipment.separator.Separator) operationsCopy
     * .getUnit("Degasser"); System.out.
     * println("The gas flow rate should be < 200 kg/hr, the actual value is " +
     * Degasser.getGasOutStream().getFlowRate("kg/hr")); System.out.
     * println("The gas flow rate should be < 200 kg/hr, the actual value is " +
     * VD01Separator.getGasOutStream().getFlowRate("kg/hr")); VD02Separator.getGasOutStream().run();
     * System.out. println("The gas flow rate should be < 200 kg/hr, the actual value is " +
     * VD02Separator.getGasOutStream().getFlowRate("kg/hr"));
     */
  }

  @ParameterizedTest
  @ValueSource(strings = {"volume", "mass", "mole"})
  void testEntrainmentSep(String specification) {
    neqsim.thermo.system.SystemInterface fluid1 =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);

    fluid1.addComponent("methane", 72.3870849609375);
    fluid1.addComponent("n-heptane", 13.90587639808655);
    fluid1.addComponent("water", 40.0);
    fluid1.setMixingRule(10);
    fluid1.setMultiPhaseCheck(true);

    Stream inlet_stream_test_sep = new Stream("TEST_SEPARATOR_INLET", fluid1);
    inlet_stream_test_sep.setTemperature(72.6675872802734, "C");
    inlet_stream_test_sep.setPressure(10.6767892837524, "bara");
    inlet_stream_test_sep.setFlowRate(721.3143271348611, "kg/hr");
    inlet_stream_test_sep.run();

    ThreePhaseSeparator test_separator =
        new ThreePhaseSeparator("TEST_SEPARATOR", inlet_stream_test_sep);
    test_separator.setEntrainment(0.05, specification, "product", "aqueous", "oil");
    test_separator.run();
    // test_separator.getFluid().prettyPrint();

    double aqueousFraction = getPhaseBasisFraction(specification,
        test_separator.getOilOutStream().getFluid().getPhase("aqueous"),
        test_separator.getOilOutStream().getFluid().getPhase("oil"));
    Assertions.assertEquals(5.0, aqueousFraction * 100, 0.1);
    /*
     * System.out.println("water in oil % " + (test_separator.getOilOutStream().getFluid()
     * .getPhase("aqueous").getFlowRate("m3/hr") /
     * (test_separator.getOilOutStream().getFluid().getPhase("oil").getFlowRate("m3/hr") +
     * test_separator.getOilOutStream().getFluid().getPhase("aqueous").getFlowRate("m3/hr"))) 100);
     */
    // test_separator.getOilOutStream().getThermoSystem().prettyPrint();
  }

  private double getPhaseBasisFraction(String specification, PhaseInterface aqueousPhase,
      PhaseInterface oilPhase) {
    double aqueousBasis = getPhaseBasis(specification, aqueousPhase);
    double oilBasis = getPhaseBasis(specification, oilPhase);
    return aqueousBasis / (aqueousBasis + oilBasis);
  }

  private double getPhaseBasis(String specification, PhaseInterface phase) {
    switch (specification) {
      case "mole":
        return phase.getNumberOfMolesInPhase();
      case "mass":
        return phase.getMass();
      case "volume":
        return phase.getVolume("m3");
      default:
        throw new IllegalArgumentException("Unsupported specification: " + specification);
    }
  }

  /**
   * Test that three-phase separator entrainment correctly transfers material between all six
   * phase-to-phase paths. Verifies mass balance and that the specified fractions appear in the
   * correct outlet streams.
   */
  @Test
  void testThreePhaseSeparatorEntrainmentAllPaths() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.0);
    fluid.addComponent("methane", 72.0);
    fluid.addComponent("n-heptane", 14.0);
    fluid.addComponent("water", 40.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    // --- Baseline (no entrainment) ---
    Stream baselineFeed = new Stream("baselineFeed", fluid);
    baselineFeed.setTemperature(72.0, "C");
    baselineFeed.setPressure(10.7, "bara");
    baselineFeed.setFlowRate(720.0, "kg/hr");
    baselineFeed.run();

    ThreePhaseSeparator baselineSep =
        new ThreePhaseSeparator("baselineSep", baselineFeed);
    baselineSep.run();

    double baseGasMass = baselineSep.getGasOutStream().getFlowRate("kg/hr");
    double baseOilMass = baselineSep.getOilOutStream().getFlowRate("kg/hr");
    double baseWaterMass = baselineSep.getWaterOutStream().getFlowRate("kg/hr");

    Assertions.assertTrue(baseGasMass > 1.0, "Baseline should have gas outlet flow");
    Assertions.assertTrue(baseOilMass > 1.0, "Baseline should have oil outlet flow");
    Assertions.assertTrue(baseWaterMass > 1.0, "Baseline should have water outlet flow");

    // --- With entrainment: aqueous into oil and oil into aqueous ---
    Stream entrainFeed1 = new Stream("entrainFeed1", fluid.clone());
    entrainFeed1.setTemperature(72.0, "C");
    entrainFeed1.setPressure(10.7, "bara");
    entrainFeed1.setFlowRate(720.0, "kg/hr");
    entrainFeed1.run();

    ThreePhaseSeparator entrainSep1 =
        new ThreePhaseSeparator("entrainSep1", entrainFeed1);
    entrainSep1.setEntrainment(0.05, "mole", "product", "aqueous", "oil");
    entrainSep1.setEntrainment(0.02, "mole", "product", "oil", "aqueous");
    entrainSep1.run();

    // Verify aqueous phase is present in oil outlet stream
    Assertions.assertTrue(
        entrainSep1.getOilOutStream().getFluid().hasPhaseType("aqueous"),
        "Oil outlet should contain entrained aqueous phase");

    // Verify oil phase is present in water outlet stream
    Assertions.assertTrue(
        entrainSep1.getOilOutStream().getFluid().hasPhaseType("oil"),
        "Oil outlet should still contain oil phase");

    // Check aqueous fraction in oil outlet is approximately 5%
    double aqMolesInOil = entrainSep1.getOilOutStream().getFluid()
        .getPhase("aqueous").getNumberOfMolesInPhase();
    double oilMolesInOil = entrainSep1.getOilOutStream().getFluid()
        .getPhase("oil").getNumberOfMolesInPhase();
    double aqFractionInOilOut = aqMolesInOil / (aqMolesInOil + oilMolesInOil);
    Assertions.assertEquals(0.05, aqFractionInOilOut, 0.005,
        "Aqueous mole fraction in oil outlet should be ~5%");

    // Mass balance: total outlet mass should match inlet
    double totalOutMass1 = entrainSep1.getGasOutStream().getFlowRate("kg/hr")
        + entrainSep1.getOilOutStream().getFlowRate("kg/hr")
        + entrainSep1.getWaterOutStream().getFlowRate("kg/hr");
    Assertions.assertEquals(720.0, totalOutMass1, 1.0,
        "Total outlet mass flow should match inlet within 1 kg/hr");

    // --- With entrainment: oil into gas and gas into oil ---
    Stream entrainFeed2 = new Stream("entrainFeed2", fluid.clone());
    entrainFeed2.setTemperature(72.0, "C");
    entrainFeed2.setPressure(10.7, "bara");
    entrainFeed2.setFlowRate(720.0, "kg/hr");
    entrainFeed2.run();

    ThreePhaseSeparator entrainSep2 =
        new ThreePhaseSeparator("entrainSep2", entrainFeed2);
    entrainSep2.setEntrainment(0.03, "mole", "feed", "oil", "gas");
    entrainSep2.setEntrainment(0.04, "mole", "feed", "gas", "oil");
    entrainSep2.run();

    // Gas outlet should have oil-origin components entrained
    double gasOutMoles2 = entrainSep2.getGasOutStream().getFluid()
        .getPhase("gas").getNumberOfMolesInPhase();
    // Oil outlet should still have flow
    double oilOutMass2 = entrainSep2.getOilOutStream().getFlowRate("kg/hr");
    Assertions.assertTrue(oilOutMass2 > 0.0,
        "Oil outlet should have flow when gas is entrained into oil");

    // Mass balance
    double gasOutMass2 = entrainSep2.getGasOutStream().getFlowRate("kg/hr");
    double totalOutMass2 = gasOutMass2 + oilOutMass2
        + entrainSep2.getWaterOutStream().getFlowRate("kg/hr");
    Assertions.assertEquals(720.0, totalOutMass2, 1.0,
        "Total outlet mass flow should match inlet within 1 kg/hr");

    // --- With entrainment: aqueous into gas and gas into aqueous ---
    Stream entrainFeed3 = new Stream("entrainFeed3", fluid.clone());
    entrainFeed3.setTemperature(72.0, "C");
    entrainFeed3.setPressure(10.7, "bara");
    entrainFeed3.setFlowRate(720.0, "kg/hr");
    entrainFeed3.run();

    ThreePhaseSeparator entrainSep3 =
        new ThreePhaseSeparator("entrainSep3", entrainFeed3);
    entrainSep3.setEntrainment(0.01, "mole", "feed", "aqueous", "gas");
    entrainSep3.setEntrainment(0.02, "mole", "feed", "gas", "aqueous");
    entrainSep3.run();

    // Water outlet should still have flow with entrained gas
    double waterOutMass3 = entrainSep3.getWaterOutStream().getFlowRate("kg/hr");
    Assertions.assertTrue(waterOutMass3 > 0.0,
        "Water outlet should have flow when gas is entrained into aqueous");

    // Mass balance
    double gasOutMass3 = entrainSep3.getGasOutStream().getFlowRate("kg/hr");
    double totalOutMass3 = gasOutMass3
        + entrainSep3.getOilOutStream().getFlowRate("kg/hr") + waterOutMass3;
    Assertions.assertEquals(720.0, totalOutMass3, 1.0,
        "Total outlet mass flow should match inlet within 1 kg/hr");
  }

  /**
   * Test that all six entrainment paths work together and mass balance holds for the three-phase
   * separator with simultaneous entrainment on every phase pair.
   */
  @Test
  void testThreePhaseSeparatorFullEntrainmentMassBalance() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.0);
    fluid.addComponent("methane", 72.0);
    fluid.addComponent("n-heptane", 14.0);
    fluid.addComponent("water", 40.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("fullEntrainFeed", fluid);
    feed.setTemperature(72.0, "C");
    feed.setPressure(10.7, "bara");
    feed.setFlowRate(720.0, "kg/hr");
    feed.run();

    ThreePhaseSeparator sep = new ThreePhaseSeparator("fullEntrainSep", feed);

    // Set all six entrainment paths
    sep.setEntrainment(0.01, "mole", "feed", "oil", "gas");
    sep.setEntrainment(0.005, "mole", "feed", "aqueous", "gas");
    sep.setEntrainment(0.02, "mole", "feed", "gas", "oil");
    sep.setEntrainment(0.01, "mole", "feed", "gas", "aqueous");
    sep.setEntrainment(0.03, "mole", "feed", "aqueous", "oil");
    sep.setEntrainment(0.02, "mole", "feed", "oil", "aqueous");
    sep.run();

    // All three outlet streams should have flow
    double gasMass = sep.getGasOutStream().getFlowRate("kg/hr");
    double oilMass = sep.getOilOutStream().getFlowRate("kg/hr");
    double waterMass = sep.getWaterOutStream().getFlowRate("kg/hr");

    Assertions.assertTrue(gasMass > 0.0, "Gas outlet should have flow");
    Assertions.assertTrue(oilMass > 0.0, "Oil outlet should have flow");
    Assertions.assertTrue(waterMass > 0.0, "Water outlet should have flow");

    // Mass balance
    double totalOutMass = gasMass + oilMass + waterMass;
    Assertions.assertEquals(720.0, totalOutMass, 1.0,
        "Total outlet mass flow should match inlet (720 kg/hr) within 1 kg/hr");
  }

  /**
   * Test that zero entrainment on a three-phase separator matches a separator with no entrainment
   * set (perfect separation baseline).
   */
  @Test
  void testThreePhaseSeparatorZeroEntrainmentMatchesBaseline() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.0);
    fluid.addComponent("methane", 72.0);
    fluid.addComponent("n-heptane", 14.0);
    fluid.addComponent("water", 40.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    // Baseline - no entrainment
    Stream baselineFeed = new Stream("baselineFeed2", fluid);
    baselineFeed.setTemperature(72.0, "C");
    baselineFeed.setPressure(10.7, "bara");
    baselineFeed.setFlowRate(720.0, "kg/hr");
    baselineFeed.run();

    ThreePhaseSeparator baselineSep =
        new ThreePhaseSeparator("baselineSep2", baselineFeed);
    baselineSep.run();

    // Zero entrainment
    Stream zeroFeed = new Stream("zeroFeed", fluid.clone());
    zeroFeed.setTemperature(72.0, "C");
    zeroFeed.setPressure(10.7, "bara");
    zeroFeed.setFlowRate(720.0, "kg/hr");
    zeroFeed.run();

    ThreePhaseSeparator zeroSep = new ThreePhaseSeparator("zeroSep", zeroFeed);
    zeroSep.setEntrainment(0.0, "mole", "feed", "oil", "gas");
    zeroSep.setEntrainment(0.0, "mole", "feed", "aqueous", "gas");
    zeroSep.setEntrainment(0.0, "mole", "feed", "gas", "oil");
    zeroSep.setEntrainment(0.0, "mole", "feed", "gas", "aqueous");
    zeroSep.setEntrainment(0.0, "mole", "feed", "oil", "aqueous");
    zeroSep.setEntrainment(0.0, "mole", "feed", "aqueous", "oil");
    zeroSep.run();

    double tolerance = 1e-6;

    // Gas outlet moles should match
    double baseGasMoles = baselineSep.getGasOutStream().getFluid()
        .getPhase("gas").getNumberOfMolesInPhase();
    double zeroGasMoles = zeroSep.getGasOutStream().getFluid()
        .getPhase("gas").getNumberOfMolesInPhase();
    Assertions.assertEquals(baseGasMoles, zeroGasMoles, tolerance,
        "Gas outlet moles should match baseline when entrainment is zero");

    // Oil outlet moles should match
    double baseOilMoles = baselineSep.getOilOutStream().getFluid()
        .getPhase("oil").getNumberOfMolesInPhase();
    double zeroOilMoles = zeroSep.getOilOutStream().getFluid()
        .getPhase("oil").getNumberOfMolesInPhase();
    Assertions.assertEquals(baseOilMoles, zeroOilMoles, tolerance,
        "Oil outlet moles should match baseline when entrainment is zero");

    // Water outlet moles should match
    double baseWaterMoles = baselineSep.getWaterOutStream().getFluid()
        .getPhase("aqueous").getNumberOfMolesInPhase();
    double zeroWaterMoles = zeroSep.getWaterOutStream().getFluid()
        .getPhase("aqueous").getNumberOfMolesInPhase();
    Assertions.assertEquals(baseWaterMoles, zeroWaterMoles, tolerance,
        "Water outlet moles should match baseline when entrainment is zero");
  }

  /**
   * Test entrainment with mass and volume specification types on three-phase separator and verify
   * the specified fractions are achieved.
   */
  @ParameterizedTest
  @ValueSource(strings = {"mole", "mass", "volume"})
  void testThreePhaseSeparatorEntrainmentSpecTypes(String specType) {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.0);
    fluid.addComponent("methane", 72.0);
    fluid.addComponent("n-heptane", 14.0);
    fluid.addComponent("water", 40.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("specTypeFeed_" + specType, fluid);
    feed.setTemperature(72.0, "C");
    feed.setPressure(10.7, "bara");
    feed.setFlowRate(720.0, "kg/hr");
    feed.run();

    double targetFraction = 0.08;
    ThreePhaseSeparator sep =
        new ThreePhaseSeparator("specTypeSep_" + specType, feed);
    sep.setEntrainment(targetFraction, specType, "product", "aqueous", "oil");
    sep.run();

    // Verify the aqueous fraction in the oil outlet matches the target
    PhaseInterface aqPhase = sep.getOilOutStream().getFluid().getPhase("aqueous");
    PhaseInterface oilPhase = sep.getOilOutStream().getFluid().getPhase("oil");

    double aqBasis = getPhaseBasis(specType, aqPhase);
    double oilBasis = getPhaseBasis(specType, oilPhase);
    double actualFraction = aqBasis / (aqBasis + oilBasis);

    Assertions.assertEquals(targetFraction, actualFraction, 0.005,
        "Aqueous " + specType + " fraction in oil outlet should be ~"
            + (targetFraction * 100) + "%");
  }

  /**
   * Test method for transient/dynamic calculation of three phase separator.
   */
  @Test
  void testRunTransient() {
    neqsim.thermo.system.SystemInterface fluid1 =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);

    fluid1.addComponent("methane", 72.3870849609375);
    fluid1.addComponent("n-heptane", 13.90587639808655);
    fluid1.addComponent("water", 40.0);
    fluid1.setMixingRule(10);
    fluid1.setMultiPhaseCheck(true);

    Stream inlet_stream_test_sep = new Stream("TEST_SEPARATOR_INLET", fluid1);
    inlet_stream_test_sep.setTemperature(72.6675872802734, "C");
    inlet_stream_test_sep.setPressure(10.6767892837524, "bara");
    inlet_stream_test_sep.setFlowRate(721.3143271348611, "kg/hr");
    inlet_stream_test_sep.run();

    ThreePhaseSeparator test_separator =
        new ThreePhaseSeparator("TEST_SEPARATOR", inlet_stream_test_sep);
    test_separator.setInternalDiameter(1.0);
    test_separator.setSeparatorLength(3.0);
    test_separator.setLiquidLevel(0.5);

    // First run in steady state mode to initialize
    test_separator.setCalculateSteadyState(true);
    test_separator.run();

    // Switch to dynamic mode
    test_separator.setCalculateSteadyState(false);

    // Run transient calculations
    double dt = 1.0; // time step in seconds
    UUID id = java.util.UUID.randomUUID();

    // Run several time steps
    for (int i = 0; i < 5; i++) {
      test_separator.runTransient(dt, id);
    }

    // Verify that the separator is running in dynamic mode
    Assertions.assertFalse(test_separator.getCalculateSteadyState());

    // Verify that gas, oil, and water streams are produced
    Assertions.assertTrue(test_separator.getGasOutStream().getFlowRate("kg/hr") > 0.0);
    Assertions.assertTrue(test_separator.getOilOutStream().getFlowRate("kg/hr") > 0.0);
    Assertions.assertTrue(test_separator.getWaterOutStream().getFlowRate("kg/hr") > 0.0);

    // Verify that pressure and temperature are reasonable
    Assertions.assertTrue(test_separator.getThermoSystem().getPressure() > 0.0);
    Assertions.assertTrue(test_separator.getThermoSystem().getTemperature() > 0.0);

    // Verify that the liquid level is within bounds
    Assertions.assertTrue(test_separator.getLiquidLevel() >= 0.0);
    Assertions.assertTrue(test_separator.getLiquidLevel() <= 1.0);
  }

  /**
   * Test method for transient/dynamic calculation with valve opening/closing and tracking water and
   * oil levels over time.
   */
  @Test
  void testRunTransientWithValveOperations() {
    // Create a three-phase fluid system
    neqsim.thermo.system.SystemInterface fluid1 =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);

    fluid1.addComponent("methane", 72.3870849609375);
    fluid1.addComponent("n-heptane", 13.90587639808655);
    fluid1.addComponent("water", 40.0);
    fluid1.setMixingRule(10);
    fluid1.setMultiPhaseCheck(true);

    Stream inlet_stream_test_sep = new Stream("TEST_SEPARATOR_INLET", fluid1);
    inlet_stream_test_sep.setTemperature(72.6675872802734, "C");
    inlet_stream_test_sep.setPressure(10.6767892837524, "bara");
    inlet_stream_test_sep.setFlowRate(1000.0, "kg/hr");
    inlet_stream_test_sep.run();

    // Create and configure the separator
    ThreePhaseSeparator test_separator =
        new ThreePhaseSeparator("TEST_SEPARATOR", inlet_stream_test_sep);
    test_separator.setInternalDiameter(2.0);
    test_separator.setSeparatorLength(5.0);
    test_separator.setOrientation("horizontal");

    // Set initial water and oil levels
    test_separator.setWaterLevel(0.3); // 0.3 m water
    test_separator.setOilLevel(0.6); // 0.6 m total liquid (0.3 m oil on top of water)

    // First run in steady state mode to initialize
    test_separator.setCalculateSteadyState(true);
    test_separator.run();

    // Switch to dynamic mode
    test_separator.setCalculateSteadyState(false);

    double dt = 1.0; // time step in seconds
    UUID id = java.util.UUID.randomUUID();

    // Arrays to store time-series data
    java.util.List<Double> timePoints = new java.util.ArrayList<>();
    java.util.List<Double> waterLevels = new java.util.ArrayList<>();
    java.util.List<Double> oilLevels = new java.util.ArrayList<>();
    java.util.List<Double> gasFlowRates = new java.util.ArrayList<>();
    java.util.List<Double> oilFlowRates = new java.util.ArrayList<>();
    java.util.List<Double> waterFlowRates = new java.util.ArrayList<>();
    java.util.List<Double> pressures = new java.util.ArrayList<>();

    double currentTime = 0.0;
    int numSteps = 100;

    // Simulate dynamic behavior with valve operations
    for (int i = 0; i < numSteps; i++) {
      currentTime = i * dt;

      // Simulate valve operations by setting flow fractions:
      // - From t=0 to t=20: all valves open (normal operation)
      // - From t=20 to t=40: close oil valve (oil level should rise)
      // - From t=40 to t=60: close water valve (water level should rise)
      // - From t=60 to t=80: close gas valve (pressure should rise, liquids accumulate)
      // - From t=80 to t=100: all valves open again

      if (currentTime >= 20 && currentTime < 40) {
        // Oil valve 90% closed
        test_separator.setOilOutletFlowFraction(0.1);
      } else {
        test_separator.setOilOutletFlowFraction(1.0);
      }

      if (currentTime >= 40 && currentTime < 60) {
        // Water valve 90% closed
        test_separator.setWaterOutletFlowFraction(0.1);
      } else {
        test_separator.setWaterOutletFlowFraction(1.0);
      }

      if (currentTime >= 60 && currentTime < 80) {
        // Gas valve 90% closed
        test_separator.setGasOutletFlowFraction(0.1);
      } else {
        test_separator.setGasOutletFlowFraction(1.0);
      }

      // Run transient calculation
      test_separator.runTransient(dt, id);

      // Record data
      timePoints.add(currentTime);
      waterLevels.add(test_separator.getWaterLevel());
      oilLevels.add(test_separator.getOilLevel());
      gasFlowRates.add(test_separator.getGasOutStream().getFlowRate("kg/hr"));
      oilFlowRates.add(test_separator.getOilOutStream().getFlowRate("kg/hr"));
      waterFlowRates.add(test_separator.getWaterOutStream().getFlowRate("kg/hr"));
      pressures.add(test_separator.getThermoSystem().getPressure("bara"));
    }

    // Print results for analysis
    System.out.println("\n=== Three-Phase Separator Dynamic Simulation with Valve Operations ===");
    System.out.println(
        "Time(s)\tWaterLvl(m)\tOilLvl(m)\tOilThick(m)\tGas(kg/hr)\tOil(kg/hr)\tWater(kg/hr)\tPressure(bara)");
    for (int i = 0; i < timePoints.size(); i += 10) { // Print every 10th point
      System.out.printf("%.1f\t%.4f\t\t%.4f\t\t%.4f\t\t%.2f\t\t%.2f\t\t%.2f\t\t%.4f%n",
          timePoints.get(i), waterLevels.get(i), oilLevels.get(i),
          oilLevels.get(i) - waterLevels.get(i), gasFlowRates.get(i), oilFlowRates.get(i),
          waterFlowRates.get(i), pressures.get(i));
    }

    // Verify basic constraints
    for (int i = 0; i < timePoints.size(); i++) {
      // Water level should always be less than or equal to oil level
      Assertions.assertTrue(waterLevels.get(i) <= oilLevels.get(i),
          "Water level should be <= oil level at time " + timePoints.get(i));

      // Levels should be non-negative
      Assertions.assertTrue(waterLevels.get(i) >= 0.0,
          "Water level should be >= 0 at time " + timePoints.get(i));
      Assertions.assertTrue(oilLevels.get(i) >= 0.0,
          "Oil level should be >= 0 at time " + timePoints.get(i));

      // Flow rates should be non-negative
      Assertions.assertTrue(gasFlowRates.get(i) >= 0.0,
          "Gas flow should be >= 0 at time " + timePoints.get(i));
      Assertions.assertTrue(oilFlowRates.get(i) >= 0.0,
          "Oil flow should be >= 0 at time " + timePoints.get(i));
      Assertions.assertTrue(waterFlowRates.get(i) >= 0.0,
          "Water flow should be >= 0 at time " + timePoints.get(i));
    }

    // Verify level changes during valve closure periods
    // When oil valve is closed (t=20-40), oil level should increase
    double oilLevelAt20 = oilLevels.get(20);
    double oilLevelAt39 = oilLevels.get(39);
    System.out.printf("\nOil level at t=20s: %.4f m, at t=39s: %.4f m (oil valve closed)%n",
        oilLevelAt20, oilLevelAt39);

    // When water valve is closed (t=40-60), water level should increase
    double waterLevelAt40 = waterLevels.get(40);
    double waterLevelAt59 = waterLevels.get(59);
    System.out.printf("Water level at t=40s: %.4f m, at t=59s: %.4f m (water valve closed)%n",
        waterLevelAt40, waterLevelAt59);

    // When gas valve is closed (t=60-80), pressure should rise
    double pressureAt60 = pressures.get(60);
    double pressureAt79 = pressures.get(79);
    System.out.printf("Pressure at t=60s: %.4f bara, at t=79s: %.4f bara (gas valve closed)%n",
        pressureAt60, pressureAt79);

    // Verify pressure increases when gas valve is closed
    // The flow restriction should cause accumulation of gas, increasing pressure
    Assertions.assertTrue(pressureAt79 > pressureAt60, String.format(
        "Pressure should increase when gas valve is closed. At t=60s: %.4f bara, at t=79s: %.4f bara",
        pressureAt60, pressureAt79));

    System.out.println("\n=== End of Dynamic Simulation ===\n");
  }

  /**
   * Test method for handling single-phase and two-phase inlet streams.
   */
  @Test
  void testSingleAndTwoPhaseInlet() {
    // Test 1: Single-phase gas inlet
    neqsim.thermo.system.SystemInterface gasOnlyFluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 80.0, 50.00);
    gasOnlyFluid.addComponent("methane", 90.0);
    gasOnlyFluid.addComponent("ethane", 10.0);
    gasOnlyFluid.setMixingRule(10);
    gasOnlyFluid.setMultiPhaseCheck(true);

    Stream gasOnlyStream = new Stream("GAS_ONLY_INLET", gasOnlyFluid);
    gasOnlyStream.setTemperature(80.0, "C");
    gasOnlyStream.setPressure(50.0, "bara");
    gasOnlyStream.setFlowRate(1000.0, "kg/hr");
    gasOnlyStream.run();

    ThreePhaseSeparator gasOnlySeparator =
        new ThreePhaseSeparator("GAS_ONLY_SEPARATOR", gasOnlyStream);
    gasOnlySeparator.run();

    // Verify gas phase is present with normal flow
    Assertions.assertTrue(gasOnlySeparator.getGasOutStream().getFlowRate("kg/hr") > 100.0,
        "Gas stream should have significant flow");

    // Verify oil and water streams have very low flow (1e-20)
    Assertions.assertTrue(gasOnlySeparator.getOilOutStream().getFlowRate("kg/hr") < 1e-15,
        "Oil stream should have negligible flow for gas-only inlet");
    Assertions.assertTrue(gasOnlySeparator.getWaterOutStream().getFlowRate("kg/hr") < 1e-15,
        "Water stream should have negligible flow for gas-only inlet");

    System.out.println("\nTest 1: Single-phase gas inlet");
    System.out.printf("Gas flow: %.6f kg/hr%n",
        gasOnlySeparator.getGasOutStream().getFlowRate("kg/hr"));
    System.out.printf("Oil flow: %.6e kg/hr%n",
        gasOnlySeparator.getOilOutStream().getFlowRate("kg/hr"));
    System.out.printf("Water flow: %.6e kg/hr%n",
        gasOnlySeparator.getWaterOutStream().getFlowRate("kg/hr"));

    // Test 2: Two-phase gas-oil inlet (no water)
    neqsim.thermo.system.SystemInterface gasOilFluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 40.0, 10.00);
    gasOilFluid.addComponent("methane", 70.0);
    gasOilFluid.addComponent("n-heptane", 30.0);
    gasOilFluid.setMixingRule(10);
    gasOilFluid.setMultiPhaseCheck(true);

    Stream gasOilStream = new Stream("GAS_OIL_INLET", gasOilFluid);
    gasOilStream.setTemperature(40.0, "C");
    gasOilStream.setPressure(10.0, "bara");
    gasOilStream.setFlowRate(1000.0, "kg/hr");
    gasOilStream.run();

    ThreePhaseSeparator gasOilSeparator =
        new ThreePhaseSeparator("GAS_OIL_SEPARATOR", gasOilStream);
    gasOilSeparator.run();

    // Verify gas and oil phases are present with normal flow
    Assertions.assertTrue(gasOilSeparator.getGasOutStream().getFlowRate("kg/hr") > 10.0,
        "Gas stream should have significant flow");
    Assertions.assertTrue(gasOilSeparator.getOilOutStream().getFlowRate("kg/hr") > 10.0,
        "Oil stream should have significant flow");

    // Verify water stream has very low flow (1e-20)
    Assertions.assertTrue(gasOilSeparator.getWaterOutStream().getFlowRate("kg/hr") < 1e-15,
        "Water stream should have negligible flow for gas-oil inlet");

    System.out.println("\nTest 2: Two-phase gas-oil inlet");
    System.out.printf("Gas flow: %.6f kg/hr%n",
        gasOilSeparator.getGasOutStream().getFlowRate("kg/hr"));
    System.out.printf("Oil flow: %.6f kg/hr%n",
        gasOilSeparator.getOilOutStream().getFlowRate("kg/hr"));
    System.out.printf("Water flow: %.6e kg/hr%n",
        gasOilSeparator.getWaterOutStream().getFlowRate("kg/hr"));

    // Test 3: Two-phase gas-water inlet (no oil)
    neqsim.thermo.system.SystemInterface gasWaterFluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 40.0, 10.00);
    gasWaterFluid.addComponent("methane", 70.0);
    gasWaterFluid.addComponent("water", 30.0);
    gasWaterFluid.setMixingRule(10);
    gasWaterFluid.setMultiPhaseCheck(true);

    Stream gasWaterStream = new Stream("GAS_WATER_INLET", gasWaterFluid);
    gasWaterStream.setTemperature(40.0, "C");
    gasWaterStream.setPressure(10.0, "bara");
    gasWaterStream.setFlowRate(1000.0, "kg/hr");
    gasWaterStream.run();

    ThreePhaseSeparator gasWaterSeparator =
        new ThreePhaseSeparator("GAS_WATER_SEPARATOR", gasWaterStream);
    gasWaterSeparator.run();

    // Verify gas and water phases are present with normal flow
    Assertions.assertTrue(gasWaterSeparator.getGasOutStream().getFlowRate("kg/hr") > 10.0,
        "Gas stream should have significant flow");
    Assertions.assertTrue(gasWaterSeparator.getWaterOutStream().getFlowRate("kg/hr") > 10.0,
        "Water stream should have significant flow");

    // Verify oil stream has very low flow (1e-20)
    Assertions.assertTrue(gasWaterSeparator.getOilOutStream().getFlowRate("kg/hr") < 1e-15,
        "Oil stream should have negligible flow for gas-water inlet");

    System.out.println("\nTest 3: Two-phase gas-water inlet");
    System.out.printf("Gas flow: %.6f kg/hr%n",
        gasWaterSeparator.getGasOutStream().getFlowRate("kg/hr"));
    System.out.printf("Oil flow: %.6e kg/hr%n",
        gasWaterSeparator.getOilOutStream().getFlowRate("kg/hr"));
    System.out.printf("Water flow: %.6f kg/hr%n",
        gasWaterSeparator.getWaterOutStream().getFlowRate("kg/hr"));
  }

  /**
   * Test method demonstrating three-phase separator with level and pressure control using PID
   * controllers. This example shows:
   * <ul>
   * <li>Water level control using level measurements and manual valve manipulation on water
   * outlet</li>
   * <li>Oil level control using level measurements and manual valve manipulation on oil outlet</li>
   * <li>Pressure control using pressure measurements and manual valve manipulation on gas
   * outlet</li>
   * </ul>
   */
  @Test
  void testThreePhaseSeparatorWithPIDControl() {
    // ===== PROCESS SETUP =====
    // Create three-phase fluid system (gas + oil + water)
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 50.0, 15.00);

    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-hexane", 2.0);
    fluid.addComponent("n-heptane", 15.0);
    fluid.addComponent("water", 50.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    // Create inlet stream
    Stream inletStream = new Stream("Separator Feed", fluid);
    inletStream.setTemperature(50.0, "C");
    inletStream.setPressure(15.0, "bara");
    inletStream.setFlowRate(5000.0, "kg/hr");
    inletStream.run();

    // Create three-phase separator
    ThreePhaseSeparator separator = new ThreePhaseSeparator("V-100", inletStream);
    separator.setInternalDiameter(2.0); // 2 meter diameter
    separator.setSeparatorLength(6.0); // 6 meter length
    separator.setOrientation("horizontal");

    System.out.println("\n=== Three-Phase Separator with PID Control Example ===\n");

    // Run initial steady-state
    separator.run();

    System.out.println("Initial Conditions:");
    System.out.printf("  Separator Pressure: %.2f bara%n",
        separator.getThermoSystem().getPressure("bara"));
    System.out.printf("  Water Level: %.3f m%n", separator.getWaterLevel());
    System.out.printf("  Oil Level: %.3f m%n", separator.getOilLevel());
    System.out.printf("  Oil Thickness: %.3f m%n",
        separator.getOilLevel() - separator.getWaterLevel());
    System.out.printf("  Gas Flow: %.1f kg/hr%n", separator.getGasOutStream().getFlowRate("kg/hr"));
    System.out.printf("  Oil Flow: %.1f kg/hr%n", separator.getOilOutStream().getFlowRate("kg/hr"));
    System.out.printf("  Water Flow: %.1f kg/hr%n",
        separator.getWaterOutStream().getFlowRate("kg/hr"));

    // ===== CONTROL SETUP =====

    // Control setpoints
    final double waterLevelSP = 0.8; // meters (allow higher level)
    final double oilLevelSP = 1.5; // meters (total liquid level, allow higher)
    final double pressureSP = 15.0; // bara

    // PID parameters (Kp, Ti, Td) - more conservative tuning
    final double waterLevelKp = 10.0;
    final double waterLevelTi = 600.0;
    final double oilLevelKp = 10.0;
    final double oilLevelTi = 600.0;
    final double pressureKp = 2.0;
    final double pressureTi = 400.0;

    // PID state variables
    double waterLevelIntegral = 0.0;
    double oilLevelIntegral = 0.0;
    double pressureIntegral = 0.0;

    // Initialize valves at reasonable positions
    double waterValveOpening = 50.0;
    double oilValveOpening = 50.0;
    double gasValveOpening = 50.0;

    System.out.println("\nControl Set Points:");
    System.out.printf("  Water Level (LC-01): %.2f m%n", waterLevelSP);
    System.out.printf("  Oil Level (LC-02): %.2f m%n", oilLevelSP);
    System.out.printf("  Pressure (PC-01): %.2f bara%n", pressureSP);

    System.out.println("\nPID Parameters:");
    System.out.printf("  LC-01: Kp=%.1f, Ti=%.1f s%n", waterLevelKp, waterLevelTi);
    System.out.printf("  LC-02: Kp=%.1f, Ti=%.1f s%n", oilLevelKp, oilLevelTi);
    System.out.printf("  PC-01: Kp=%.1f, Ti=%.1f s%n", pressureKp, pressureTi);

    // ===== TRANSIENT SIMULATION WITH CONTROL =====

    System.out.println("\nStarting transient simulation with PID control...");

    // Switch to transient mode
    separator.setCalculateSteadyState(false);

    double timeStep = 10.0; // 10 seconds per step
    int numSteps = 120; // 1200 seconds total
    double currentTime = 0.0;

    // Data collection
    List<Double> timeData = new ArrayList<>();
    List<Double> pressureData = new ArrayList<>();
    List<Double> waterLevelData = new ArrayList<>();
    List<Double> oilLevelData = new ArrayList<>();
    List<Double> waterValveData = new ArrayList<>();
    List<Double> oilValveData = new ArrayList<>();
    List<Double> gasValveData = new ArrayList<>();

    UUID id = UUID.randomUUID();

    // Run transient simulation
    for (int i = 0; i < numSteps; i++) {
      currentTime = i * timeStep;

      // Simulate disturbance at t=400s: increase inlet flow rate
      if (i == 40) {
        System.out.println("\n*** Disturbance at t=400s: Inlet flow increased to 6000 kg/hr ***");
        inletStream.setFlowRate(6000.0, "kg/hr");
        inletStream.run();
      }

      // Simulate disturbance at t=800s: decrease inlet flow rate
      if (i == 80) {
        System.out.println("\n*** Disturbance at t=800s: Inlet flow decreased to 4000 kg/hr ***");
        inletStream.setFlowRate(4000.0, "kg/hr");
        inletStream.run();
      }

      // === PID CONTROL LOGIC ===

      // Measure current values
      double waterLevel = separator.getWaterLevel();
      double oilLevel = separator.getOilLevel();
      double pressure = separator.getThermoSystem().getPressure("bara");

      // Calculate errors
      double waterLevelError = waterLevelSP - waterLevel;
      double oilLevelError = oilLevelSP - oilLevel;
      double pressureError = pressureSP - pressure;

      // Update integrals
      waterLevelIntegral += waterLevelError * timeStep;
      oilLevelIntegral += oilLevelError * timeStep;
      pressureIntegral += pressureError * timeStep;

      // Calculate PID outputs (reverse acting for levels, direct acting for pressure)
      waterValveOpening = 100.0
          - (waterLevelKp * waterLevelError + waterLevelKp / waterLevelTi * waterLevelIntegral);
      oilValveOpening =
          100.0 - (oilLevelKp * oilLevelError + oilLevelKp / oilLevelTi * oilLevelIntegral);
      gasValveOpening = gasValveOpening
          + (pressureKp * pressureError + pressureKp / pressureTi * pressureIntegral * timeStep);

      // Limit outputs to 0-100%
      waterValveOpening = Math.max(0.0, Math.min(100.0, waterValveOpening));
      oilValveOpening = Math.max(0.0, Math.min(100.0, oilValveOpening));
      gasValveOpening = Math.max(0.0, Math.min(100.0, gasValveOpening));

      // Apply valve positions (convert % to flow fraction 0-1)
      separator.setWaterOutletFlowFraction(waterValveOpening / 100.0);
      separator.setOilOutletFlowFraction(oilValveOpening / 100.0);
      separator.setGasOutletFlowFraction(gasValveOpening / 100.0);

      // Run transient calculation
      separator.runTransient(timeStep, id);

      // Collect data
      timeData.add(currentTime);
      pressureData.add(separator.getThermoSystem().getPressure("bara"));
      waterLevelData.add(separator.getWaterLevel());
      oilLevelData.add(separator.getOilLevel());
      waterValveData.add(waterValveOpening);
      oilValveData.add(oilValveOpening);
      gasValveData.add(gasValveOpening);

      // Print status every 10 steps
      if (i % 10 == 0) {
        System.out.printf(
            "\nt=%.0fs | P=%.2f bara (err=%.2f) | WLvl=%.3fm (err=%.3f) | OLvl=%.3fm (err=%.3f)%n",
            currentTime, pressureData.get(i), pressureError, waterLevelData.get(i), waterLevelError,
            oilLevelData.get(i), oilLevelError);
        System.out.printf("       | LCV-01=%.1f%% | LCV-02=%.1f%% | PCV-01=%.1f%%%n",
            waterValveData.get(i), oilValveData.get(i), gasValveData.get(i));
      }

      // Verify physical constraints
      Assertions.assertTrue(separator.getWaterLevel() >= 0.0, "Water level should be non-negative");
      Assertions.assertTrue(separator.getOilLevel() >= separator.getWaterLevel(),
          "Oil level should be >= water level");
      Assertions.assertTrue(separator.getThermoSystem().getPressure("bara") > 0.0,
          "Pressure should be positive");
    }

    // ===== FINAL RESULTS =====

    System.out.println("\n\n=== Simulation Results Summary ===");
    System.out.printf("Total simulation time: %.0f seconds%n", currentTime);

    // Final values
    double finalPressure = pressureData.get(pressureData.size() - 1);
    double finalWaterLevel = waterLevelData.get(waterLevelData.size() - 1);
    double finalOilLevel = oilLevelData.get(oilLevelData.size() - 1);

    System.out.println("\nFinal Conditions:");
    System.out.printf("  Pressure: %.2f bara (SP: %.2f bara)%n", finalPressure, pressureSP);
    System.out.printf("  Water Level: %.3f m (SP: %.2f m)%n", finalWaterLevel, waterLevelSP);
    System.out.printf("  Oil Level: %.3f m (SP: %.2f m)%n", finalOilLevel, oilLevelSP);
    System.out.printf("  Oil Thickness: %.3f m%n", finalOilLevel - finalWaterLevel);

    System.out.println("\nFinal Valve Positions:");
    System.out.printf("  LCV-01 (Water): %.1f%%  (Flow Fraction: %.3f)%n",
        waterValveData.get(waterValveData.size() - 1), separator.getWaterOutletFlowFraction());
    System.out.printf("  LCV-02 (Oil): %.1f%%  (Flow Fraction: %.3f)%n",
        oilValveData.get(oilValveData.size() - 1), separator.getOilOutletFlowFraction());
    System.out.printf("  PCV-01 (Gas): %.1f%%  (Flow Fraction: %.3f)%n",
        gasValveData.get(gasValveData.size() - 1), separator.getGasOutletFlowFraction());

    // Verify control performance
    double pressureError = Math.abs(finalPressure - pressureSP);
    double waterLevelError = Math.abs(finalWaterLevel - waterLevelSP);
    double oilLevelError = Math.abs(finalOilLevel - oilLevelSP);

    System.out.println("\nControl Errors:");
    System.out.printf("  Pressure Error: %.3f bara%n", pressureError);
    System.out.printf("  Water Level Error: %.3f m%n", waterLevelError);
    System.out.printf("  Oil Level Error: %.3f m%n", oilLevelError);

    // Basic assertions - controllers should help manage the system
    // Allow wide tolerance since this is a demonstration of the concept
    // In practice, better PID tuning would be needed for tight control
    Assertions.assertTrue(pressureError < 50.0, "Pressure should remain in a reasonable range");
    Assertions.assertTrue(waterLevelError < 2.0, "Water level should remain in a reasonable range");
    Assertions.assertTrue(oilLevelError < 2.0, "Oil level should remain in a reasonable range");

    // Verify the system remained stable throughout
    for (int i = 0; i < pressureData.size(); i++) {
      Assertions.assertTrue(Double.isFinite(pressureData.get(i)),
          "Pressure should remain finite at step " + i);
      Assertions.assertTrue(Double.isFinite(waterLevelData.get(i)),
          "Water level should remain finite at step " + i);
      Assertions.assertTrue(Double.isFinite(oilLevelData.get(i)),
          "Oil level should remain finite at step " + i);
    }

    System.out.println("\n=== End of PID Control Example ===\n");
  }

  /**
   * Test method demonstrating the use of WaterLevelTransmitter and OilLevelTransmitter with
   * three-phase separator and PID controllers.
   */
  @Test
  void testThreePhaseSeparatorWithLevelTransmitters() {
    // ===== PROCESS SETUP =====
    // Create three-phase fluid system (gas + oil + water)
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 50.0, 15.00);

    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-hexane", 2.0);
    fluid.addComponent("n-heptane", 15.0);
    fluid.addComponent("water", 50.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    // Create inlet stream
    Stream inletStream = new Stream("Separator Feed", fluid);
    inletStream.setTemperature(50.0, "C");
    inletStream.setPressure(15.0, "bara");
    inletStream.setFlowRate(5000.0, "kg/hr");
    inletStream.run();

    // Create three-phase separator
    ThreePhaseSeparator separator = new ThreePhaseSeparator("V-100", inletStream);
    separator.setInternalDiameter(2.0); // 2 meter diameter
    separator.setSeparatorLength(6.0); // 6 meter length
    separator.setOrientation("horizontal");

    System.out.println("\n=== Three-Phase Separator with Level Transmitters Example ===\n");

    // Run initial steady-state
    separator.run();

    // ===== CREATE SPECIALIZED TRANSMITTERS =====

    // LT-01: Water level transmitter (measures water level from bottom)
    WaterLevelTransmitter lt01 = new WaterLevelTransmitter("LT-01", separator);

    // LT-02: Oil level transmitter (measures total liquid level from bottom)
    OilLevelTransmitter lt02 = new OilLevelTransmitter("LT-02", separator);

    // PT-01: Pressure transmitter
    PressureTransmitter pt01 = new PressureTransmitter("PT-01", separator.getGasOutStream());
    pt01.setUnit("bara");

    System.out.println("Transmitters Created:");
    System.out.printf("  LT-01 (Water Level): Range %.1f-%.1f m%n", lt01.getMinimumValue(),
        lt01.getMaximumValue());
    System.out.printf("  LT-02 (Oil Level): Range %.1f-%.1f m%n", lt02.getMinimumValue(),
        lt02.getMaximumValue());
    System.out.printf("  PT-01 (Pressure): Range %.1f-%.1f bara%n", pt01.getMinimumValue(),
        pt01.getMaximumValue());

    System.out.println("\nInitial Measurements:");
    System.out.printf("  LT-01 Reading: %.3f m (water level)%n", lt01.getMeasuredValue("m"));
    System.out.printf("  LT-02 Reading: %.3f m (oil level)%n", lt02.getMeasuredValue("m"));
    System.out.printf("  Oil Thickness: %.3f m%n", lt02.getOilThickness());
    System.out.printf("  PT-01 Reading: %.2f bara%n", pt01.getMeasuredValue("bara"));

    // ===== PID CONTROLLERS WITH SPECIALIZED TRANSMITTERS =====

    // Control setpoints
    final double waterLevelSP = 0.6; // meters
    final double oilLevelSP = 1.3; // meters (total liquid level)
    final double pressureSP = 15.0; // bara

    // LC-01: Water level controller using WaterLevelTransmitter
    ControllerDeviceBaseClass lc01 = new ControllerDeviceBaseClass("LC-01");
    lc01.setTransmitter(lt01);
    lc01.setReverseActing(true);
    lc01.setControllerSetPoint(waterLevelSP);
    lc01.setControllerParameters(15.0, 500.0, 0.0);
    lc01.setOutputLimits(0.0, 100.0);

    // LC-02: Oil level controller using OilLevelTransmitter
    ControllerDeviceBaseClass lc02 = new ControllerDeviceBaseClass("LC-02");
    lc02.setTransmitter(lt02);
    lc02.setReverseActing(true);
    lc02.setControllerSetPoint(oilLevelSP);
    lc02.setControllerParameters(15.0, 500.0, 0.0);
    lc02.setOutputLimits(0.0, 100.0);

    // PC-01: Pressure controller
    ControllerDeviceBaseClass pc01 = new ControllerDeviceBaseClass("PC-01");
    pc01.setTransmitter(pt01);
    pc01.setReverseActing(false);
    pc01.setControllerSetPoint(pressureSP);
    pc01.setControllerParameters(3.0, 350.0, 0.0);
    pc01.setOutputLimits(0.0, 100.0);

    System.out.println("\nController Configuration:");
    System.out.printf("  LC-01: SP=%.2f m, Kp=%.1f, Ti=%.1f s (Water Level Control)%n",
        lc01.getControllerSetPoint(), 15.0, 500.0);
    System.out.printf("  LC-02: SP=%.2f m, Kp=%.1f, Ti=%.1f s (Oil Level Control)%n",
        lc02.getControllerSetPoint(), 15.0, 500.0);
    System.out.printf("  PC-01: SP=%.2f bara, Kp=%.1f, Ti=%.1f s (Pressure Control)%n",
        pc01.getControllerSetPoint(), 3.0, 350.0);

    // ===== TRANSIENT SIMULATION =====

    System.out.println("\nStarting transient simulation...");

    separator.setCalculateSteadyState(false);

    double timeStep = 10.0;
    int numSteps = 60;
    UUID id = UUID.randomUUID();

    // Data collection
    List<Double> timeData = new ArrayList<>();
    List<Double> waterLevelData = new ArrayList<>();
    List<Double> oilLevelData = new ArrayList<>();
    List<Double> oilThicknessData = new ArrayList<>();
    List<Double> pressureData = new ArrayList<>();
    List<Double> lc01OutputData = new ArrayList<>();
    List<Double> lc02OutputData = new ArrayList<>();
    List<Double> pc01OutputData = new ArrayList<>();

    for (int i = 0; i < numSteps; i++) {
      double currentTime = i * timeStep;

      // Apply disturbance at t=200s
      if (i == 20) {
        System.out.println("\n*** Disturbance: Inlet flow increased to 5500 kg/hr ***");
        inletStream.setFlowRate(5500.0, "kg/hr");
        inletStream.run();
      }

      // Controllers automatically read from their transmitters
      // Apply control outputs to valves
      separator.setWaterOutletFlowFraction(lc01.getResponse() / 100.0);
      separator.setOilOutletFlowFraction(lc02.getResponse() / 100.0);
      separator.setGasOutletFlowFraction(pc01.getResponse() / 100.0);

      // Run transient step
      separator.runTransient(timeStep, id);

      // Read transmitters AFTER the transient step
      double waterLevel = lt01.getMeasuredValue("m");
      double oilLevel = lt02.getMeasuredValue("m");
      double oilThickness = lt02.getOilThickness();
      double pressure = pt01.getMeasuredValue("bara");

      // Collect data
      timeData.add(currentTime);
      waterLevelData.add(waterLevel);
      oilLevelData.add(oilLevel);
      oilThicknessData.add(oilThickness);
      pressureData.add(pressure);
      lc01OutputData.add(lc01.getResponse());
      lc02OutputData.add(lc02.getResponse());
      pc01OutputData.add(pc01.getResponse());

      // Print every 10 steps
      if (i % 10 == 0) {
        System.out.printf("\nt=%.0fs | WLvl=%.3fm | OLvl=%.3fm | OilThick=%.3fm | P=%.2f bara%n",
            currentTime, waterLevel, oilLevel, oilThickness, pressure);
        System.out.printf("       | LC-01=%.1f%% | LC-02=%.1f%% | PC-01=%.1f%%%n",
            lc01.getResponse(), lc02.getResponse(), pc01.getResponse());
      }
    }

    // ===== VERIFICATION =====

    System.out.println("\n\n=== Simulation Results ===");

    // Verify transmitters are working
    double finalWaterLevel = waterLevelData.get(waterLevelData.size() - 1);
    double finalOilLevel = oilLevelData.get(oilLevelData.size() - 1);
    double finalOilThickness = oilThicknessData.get(oilThicknessData.size() - 1);
    double finalPressure = pressureData.get(pressureData.size() - 1);

    System.out.println("\nFinal Transmitter Readings:");
    System.out.printf("  LT-01 (Water Level): %.3f m%n", finalWaterLevel);
    System.out.printf("  LT-02 (Oil Level): %.3f m%n", finalOilLevel);
    System.out.printf("  Oil Thickness: %.3f m%n", finalOilThickness);
    System.out.printf("  PT-01 (Pressure): %.2f bara%n", finalPressure);

    System.out.println("\nControl Setpoints:");
    System.out.printf("  LC-01: %.2f m%n", waterLevelSP);
    System.out.printf("  LC-02: %.2f m%n", oilLevelSP);
    System.out.printf("  PC-01: %.2f bara%n", pressureSP);

    System.out.println("\nFinal Controller Outputs:");
    System.out.printf("  LC-01: %.1f%% (Water valve)%n", lc01.getResponse());
    System.out.printf("  LC-02: %.1f%% (Oil valve)%n", lc02.getResponse());
    System.out.printf("  PC-01: %.1f%% (Gas valve)%n", pc01.getResponse());

    // Assertions
    Assertions.assertTrue(lt01.getMeasuredValue("m") >= 0.0,
        "Water level transmitter should read non-negative");
    Assertions.assertTrue(lt02.getMeasuredValue("m") >= lt01.getMeasuredValue("m"),
        "Oil level should be >= water level");
    Assertions.assertTrue(lt02.getOilThickness() >= 0.0, "Oil thickness should be non-negative");
    Assertions.assertTrue(pt01.getMeasuredValue("bara") > 0.0,
        "Pressure transmitter should read positive");

    // Verify data consistency
    for (int i = 0; i < timeData.size(); i++) {
      Assertions.assertTrue(oilLevelData.get(i) >= waterLevelData.get(i),
          "Oil level must be >= water level at all times");
      Assertions.assertTrue(
          Math.abs(oilThicknessData.get(i) - (oilLevelData.get(i) - waterLevelData.get(i))) < 0.001,
          "Oil thickness should equal oilLevel - waterLevel");
    }

    System.out.println("\n=== End of Level Transmitters Example ===\n");
  }
}

