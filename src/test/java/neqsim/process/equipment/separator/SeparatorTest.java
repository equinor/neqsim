package neqsim.process.equipment.separator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * @author ESOL
 */
class SeparatorTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  double pressure_inlet = 55.0;
  double temperature_inlet = 35.0;
  double gasFlowRate = 5.0;
  ProcessSystem processOps = null;
  Separator sep = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  public void setUpBeforeClass() {
    testSystem = new SystemSrkCPAstatoil(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("nC10", 10.0);
    testSystem.addComponent("water", 10.0);
    testSystem.setMixingRule(10);
    testSystem.setMultiPhaseCheck(true);

    StreamInterface inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    sep = new Separator("inlet separator");
    sep.setInletStream(inletStream);

    processOps = new ProcessSystem();
    processOps.add(inletStream);
    processOps.add(sep);
  }

  @Test
  public void testFlow() {
    LevelTransmitter lt = new LevelTransmitter("levelTransmitter", sep);
    Assertions.assertEquals(0.5, lt.getMeasuredValue(), 1e-12);
    ((StreamInterface) processOps.getUnit("inlet stream")).setFlowRate(0.01, "MSm3/day");
    processOps.run();
    Assertions.assertEquals(0.5, lt.getMeasuredValue(), 1e-12);
    Assertions.assertEquals(lt.getMeasuredValue() * 100, lt.getMeasuredPercentValue(), 1e-12);
  }

  @Test
  public void testOnePhase() {
    ((StreamInterface) processOps.getUnit("inlet stream")).setFlowRate(1.0, "MSm3/day");
    ((StreamInterface) processOps.getUnit("inlet stream")).getFluid()
        .setMolarComposition(new double[] {1.0, 0.0, 0.0, 0.0});

    processOps.run();
  }

  @Test
  public void testSimpleSeparator() {
    neqsim.thermo.system.SystemSrkEos fluid1 = new neqsim.thermo.system.SystemSrkEos(280.0, 10.0);
    fluid1.addComponent("water", 2.7);
    fluid1.addComponent("nitrogen", 0.7);
    fluid1.addComponent("CO2", 2.1);
    fluid1.addComponent("methane", 70.0);
    fluid1.addComponent("ethane", 10.0);
    fluid1.addComponent("propane", 5.0);
    fluid1.addComponent("i-butane", 3.0);
    fluid1.addComponent("n-butane", 2.0);
    fluid1.addComponent("i-pentane", 1.0);
    fluid1.addComponent("n-pentane", 1.0);
    fluid1.addTBPfraction("C6", 1.49985, 86.3 / 1000.0, 0.7432);
    fluid1.addTBPfraction("C7", 0.49985, 103.3 / 1000.0, 0.76432);
    fluid1.addTBPfraction("C8", 0.39985, 125.0 / 1000.0, 0.78432);
    fluid1.addTBPfraction("C9", 0.49985, 145.0 / 1000.0, 0.79432);
    fluid1.addTBPfraction("C10", 0.149985, 165.0 / 1000.0, 0.81);
    fluid1.setMixingRule(2);
    fluid1.setMultiPhaseCheck(true);
    fluid1.setTemperature(55.0, "C");
    fluid1.setPressure(55.0, "bara");

    Stream feedStream = new Stream("feed fluid", fluid1);
    Separator separator1 = new Separator("sep1", feedStream);

    processOps = new ProcessSystem();
    processOps.add(feedStream);
    processOps.add(separator1);
    processOps.run();

    Assertions.assertEquals(0.06976026260, feedStream.getFluid().getPhase(PhaseType.OIL).getBeta(),
        1e-5);

    Assertions.assertEquals(0.06976026260, separator1.getFluid().getPhase(PhaseType.OIL).getBeta(),
        1e-5);
  }

  @Test
  void testSeparatorEntrainmentTransfersExpectedMoles() {
    double oilToGasFraction = 0.1;
    double waterToGasFraction = 0.05;
    double gasToLiquidFraction = 0.08;

    Stream baseFeed = createEntrainmentFeed("baseFeed");
    Separator baselineSeparator = new Separator("baseline separator", baseFeed);
    baselineSeparator.run();

    double baseGasMoles = getPhaseMoles(baselineSeparator.getGasOutStream(), "gas");
    double baseOilMoles = getPhaseMoles(baselineSeparator.getLiquidOutStream(), "oil");
    double baseWaterMoles = getPhaseMoles(baselineSeparator.getLiquidOutStream(), "aqueous");
    Assertions.assertTrue(baseGasMoles > 0.0, "Baseline separator should yield gas phase");
    Assertions.assertTrue(baseOilMoles > 0.0, "Baseline separator should yield oil phase");
    Assertions.assertTrue(baseWaterMoles > 0.0, "Baseline separator should yield aqueous phase");

    Stream expectedFeed = createEntrainmentFeed("expectedFeed");
    SystemInterface expectedSystem = applyEntrainment(expectedFeed.getFluid(), oilToGasFraction,
        waterToGasFraction, gasToLiquidFraction);

    Stream expectedGasStream = new Stream("expectedGas");
    expectedGasStream.setThermoSystemFromPhase(expectedSystem, "gas");
    expectedGasStream.run();

    Stream expectedLiquidStream = new Stream("expectedLiquid");
    expectedLiquidStream.setThermoSystemFromPhase(expectedSystem, "liquid");
    expectedLiquidStream.run();

    double expectedGasMoles = getPhaseMoles(expectedGasStream, "gas");
    double expectedOilMoles = getPhaseMoles(expectedLiquidStream, "oil");
    double expectedWaterMoles = getPhaseMoles(expectedLiquidStream, "aqueous");
    double expectedGasInLiquidMoles = getPhaseMoles(expectedLiquidStream, "gas");

    Stream entrainmentFeed = createEntrainmentFeed("entrainmentFeed");
    Separator entrainmentSeparator = new Separator("entrainment separator", entrainmentFeed);
    entrainmentSeparator.setEntrainment(oilToGasFraction, "mole", "feed", "oil", "gas");
    entrainmentSeparator.setEntrainment(waterToGasFraction, "mole", "feed", "aqueous", "gas");
    entrainmentSeparator.setEntrainment(gasToLiquidFraction, "mole", "feed", "gas", "liquid");
    entrainmentSeparator.run();

    double toleranceGas = Math.max(1e-8, Math.abs(expectedGasMoles) * 1e-6);
    double toleranceLiquid = Math.max(1e-8,
        Math.abs(expectedOilMoles + expectedWaterMoles) * 1e-6);
    double toleranceGasInLiquid = Math.max(1e-8, Math.abs(expectedGasInLiquidMoles) * 1e-6);

    Assertions.assertEquals(expectedGasMoles,
        getPhaseMoles(entrainmentSeparator.getGasOutStream(), "gas"), toleranceGas);
    Assertions.assertEquals(expectedOilMoles,
        getPhaseMoles(entrainmentSeparator.getLiquidOutStream(), "oil"), toleranceLiquid);
    Assertions.assertEquals(expectedWaterMoles,
        getPhaseMoles(entrainmentSeparator.getLiquidOutStream(), "aqueous"), toleranceLiquid);
    Assertions.assertEquals(expectedGasInLiquidMoles,
        getPhaseMoles(entrainmentSeparator.getLiquidOutStream(), "gas"), toleranceGasInLiquid);
  }

  @Test
  void testSeparatorWithoutEntrainmentMatchesBaseline() {
    Stream baselineFeed = createEntrainmentFeed("baselineFeed");
    Separator baselineSeparator = new Separator("baseline separator", baselineFeed);
    baselineSeparator.run();

    Stream zeroEntrainmentFeed = createEntrainmentFeed("zeroEntrainmentFeed");
    Separator zeroEntrainmentSeparator = new Separator("zero entrainment separator",
        zeroEntrainmentFeed);
    zeroEntrainmentSeparator.setEntrainment(0.0, "mole", "feed", "oil", "gas");
    zeroEntrainmentSeparator.setEntrainment(0.0, "mole", "feed", "aqueous", "gas");
    zeroEntrainmentSeparator.setEntrainment(0.0, "mole", "feed", "gas", "liquid");
    zeroEntrainmentSeparator.run();

    double tolerance = 1e-8;

    Assertions.assertEquals(getPhaseMoles(baselineSeparator.getGasOutStream(), "gas"),
        getPhaseMoles(zeroEntrainmentSeparator.getGasOutStream(), "gas"), tolerance);
    Assertions.assertEquals(getPhaseMoles(baselineSeparator.getLiquidOutStream(), "oil"),
        getPhaseMoles(zeroEntrainmentSeparator.getLiquidOutStream(), "oil"), tolerance);
    Assertions.assertEquals(getPhaseMoles(baselineSeparator.getLiquidOutStream(), "aqueous"),
        getPhaseMoles(zeroEntrainmentSeparator.getLiquidOutStream(), "aqueous"), tolerance);
  }

  private Stream createEntrainmentFeed(String name) {
    neqsim.thermo.system.SystemSrkCPAstatoil fluid =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    fluid.addComponent("methane", 72.3870849609375);
    fluid.addComponent("n-heptane", 13.90587639808655);
    fluid.addComponent("water", 40.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream(name, fluid);
    feed.setTemperature(72.6675872802734, "C");
    feed.setPressure(10.6767892837524, "bara");
    feed.setFlowRate(721.3143271348611, "kg/hr");
    feed.run();
    return feed;
  }

  private double getPhaseMoles(StreamInterface stream, String phaseType) {
    if (!stream.getFluid().hasPhaseType(phaseType)) {
      return 0.0;
    }
    return stream.getFluid().getPhase(phaseType).getNumberOfMolesInPhase();
  }

  private SystemInterface applyEntrainment(SystemInterface baseFluid, double oilToGasFraction,
      double waterToGasFraction, double gasToLiquidFraction) {
    SystemInterface workingFluid = baseFluid.clone();

    workingFluid.addPhaseFractionToPhase(oilToGasFraction, "mole", "feed", "oil", "gas");
    workingFluid.addPhaseFractionToPhase(waterToGasFraction, "mole", "feed", "aqueous", "gas");

    if (workingFluid.hasPhaseType("liquid")) {
      workingFluid.addPhaseFractionToPhase(gasToLiquidFraction, "mole", "feed", "gas", "liquid");
    } else if (workingFluid.hasPhaseType("oil") && workingFluid.hasPhaseType("aqueous")) {
      double oilMoles = workingFluid.getPhase("oil").getNumberOfMolesInPhase();
      double waterMoles = workingFluid.getPhase("aqueous").getNumberOfMolesInPhase();
      double liquidTotal = oilMoles + waterMoles;
      if (liquidTotal > 0.0) {
        double oilShare = oilMoles / liquidTotal;
        double waterShare = waterMoles / liquidTotal;
        workingFluid.addPhaseFractionToPhase(gasToLiquidFraction * oilShare, "mole", "feed",
            "gas", "oil");
        workingFluid.addPhaseFractionToPhase(gasToLiquidFraction * waterShare, "mole", "feed",
            "gas", "aqueous");
      }
    } else if (workingFluid.hasPhaseType("oil")) {
      workingFluid.addPhaseFractionToPhase(gasToLiquidFraction, "mole", "feed", "gas", "oil");
    } else if (workingFluid.hasPhaseType("aqueous")) {
      workingFluid.addPhaseFractionToPhase(gasToLiquidFraction, "mole", "feed", "gas",
          "aqueous");
    }

    return workingFluid;
  }
}
