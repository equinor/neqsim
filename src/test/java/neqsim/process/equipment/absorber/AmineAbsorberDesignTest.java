package neqsim.process.equipment.absorber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for amine absorber design parameters and TEG Fs-factor validation.
 *
 * @author NeqSim
 */
public class AmineAbsorberDesignTest {

  // ============================================================================
  // TEG Fs-factor tests
  // ============================================================================

  @Test
  public void testFsFactorUtilization() {
    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("TEG test");
    // Without streams, Fs should be zero
    assertEquals(0.0, absorber.getFsFactor(), 1e-10);
    assertEquals(3.0, absorber.getMaxAllowableFsFactor(), 1e-10);
    assertTrue(absorber.isFsFactorWithinDesignLimit());
    assertEquals(0.0, absorber.getFsFactorUtilization(), 1e-10);
  }

  @Test
  public void testMinimumDiameterZeroWithoutStreams() {
    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("TEG test");
    assertEquals(0.0, absorber.getMinimumDiameterForFsLimit(), 1e-10);
  }

  @Test
  public void testValidateContactorDesignOutput() {
    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("TEG test");
    String validation = absorber.validateContactorDesign();
    assertNotNull(validation);
    assertTrue(validation.contains("TEG Contactor Design Validation"));
    assertTrue(validation.contains("Fs factor"));
    assertTrue(validation.contains("Fs within limit"));
  }

  // ============================================================================
  // Amine absorber design tests
  // ============================================================================

  @Test
  public void testAmineAbsorberCreation() {
    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("Amine Absorber");
    assertNotNull(absorber);
    assertEquals("MDEA", absorber.getAmineType());
    assertEquals(50.0, absorber.getAmineConcentrationWtPct(), 1e-10);
    assertEquals(0.90, absorber.getCO2RemovalEfficiency(), 1e-10);
    assertEquals(0.99, absorber.getH2SRemovalEfficiency(), 1e-10);
    assertEquals(0.20, absorber.getFoamingDesignMargin(), 1e-10);
  }

  @Test
  public void testAmineAbsorberConfiguration() {
    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("Amine Absorber");
    absorber.setAmineType("DEA");
    absorber.setAmineConcentrationWtPct(35.0);
    absorber.setCO2RemovalEfficiency(0.95);
    absorber.setH2SRemovalEfficiency(0.999);
    absorber.setFoamingDesignMargin(0.25);
    absorber.setMaxPackingHeightPerSection(5.0);

    assertEquals("DEA", absorber.getAmineType());
    assertEquals(35.0, absorber.getAmineConcentrationWtPct(), 1e-10);
    assertEquals(0.95, absorber.getCO2RemovalEfficiency(), 1e-10);
    assertEquals(0.999, absorber.getH2SRemovalEfficiency(), 1e-10);
    assertEquals(0.25, absorber.getFoamingDesignMargin(), 1e-10);
    assertEquals(5.0, absorber.getMaxPackingHeightPerSection(), 1e-10);
  }

  @Test
  public void testRichAmineLoadingCalculation() {
    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("Test");
    absorber.setLeanAmineLoading(0.01);
    absorber.setApproachToEquilibrium(0.70);

    double equilibriumLoading = 0.50;
    double richLoading = absorber.calcRichAmineLoading(equilibriumLoading);

    // rich = lean + (eq - lean) * approach = 0.01 + (0.50 - 0.01) * 0.70 = 0.353
    assertEquals(0.353, richLoading, 0.001);
    assertEquals(richLoading, absorber.getRichAmineLoading(), 1e-10);
  }

  @Test
  public void testCirculationRateCalculation() {
    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("Test");
    absorber.setAmineConcentrationWtPct(50.0);
    absorber.setLeanAmineLoading(0.01);
    absorber.setApproachToEquilibrium(0.70);
    absorber.calcRichAmineLoading(0.50);

    // Typical MDEA: density ~1050, molar mass ~0.119 kg/mol
    double rate = absorber.calcRequiredCirculationRate(10.0, // mol/s acid gas to remove
        1050.0, // kg/m3
        0.119); // kg/mol

    assertTrue(rate > 0);
    assertEquals(rate, absorber.getRequiredCirculationRate(), 1e-10);
  }

  @Test
  public void testPackingHeightCalculation() {
    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("Test");
    absorber.setMaxPackingHeightPerSection(5.0);

    // 1.0 m HTU, 12 NTU = 12 m total = 3 sections
    absorber.calcPackingHeight(1.0, 12.0);

    assertEquals(12.0, absorber.getRequiredPackingHeight(), 1e-10);
    assertEquals(3, absorber.getNumberOfPackingSections());
  }

  @Test
  public void testPackingHeightSingleSection() {
    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("Test");
    absorber.setMaxPackingHeightPerSection(6.0);

    // 0.7 m HTU, 5 NTU = 3.5 m = 1 section
    absorber.calcPackingHeight(0.7, 5.0);

    assertEquals(3.5, absorber.getRequiredPackingHeight(), 1e-10);
    assertEquals(1, absorber.getNumberOfPackingSections());
  }

  @Test
  public void testDemisterKFactor() {
    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("Test");

    // K = Vs * sqrt(rho_g / (rho_l - rho_g))
    // K = 2.0 * sqrt(50 / (1050 - 50)) = 2.0 * sqrt(0.05) = 2.0 * 0.2236 = 0.447
    double k = absorber.calcDemisterKFactor(2.0, 50.0, 1050.0);

    assertEquals(0.447, k, 0.001);
    assertFalse(absorber.isDemisterWithinLimit()); // 0.447 > 0.08
  }

  @Test
  public void testDemisterKFactorWithinLimit() {
    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("Test");

    // K = 0.3 * sqrt(5 / (1050 - 5)) = 0.3 * 0.069 = 0.0207
    double k = absorber.calcDemisterKFactor(0.3, 5.0, 1050.0);

    assertTrue(k < 0.08);
    assertTrue(absorber.isDemisterWithinLimit());
  }

  @Test
  public void testAmineTemperatureMargin() {
    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("Test");
    absorber.setAmineTemperatureMarginC(6.0);

    // Gas at 30C, amine at 37C -> margin 7C > 6C
    assertTrue(absorber.checkAmineTemperatureMargin(30.0, 37.0));
    assertTrue(absorber.isAmineTemperatureAdequate());

    // Gas at 30C, amine at 33C -> margin 3C < 6C
    assertFalse(absorber.checkAmineTemperatureMargin(30.0, 33.0));
    assertFalse(absorber.isAmineTemperatureAdequate());
  }

  @Test
  public void testFoamingMarginCalculation() {
    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("Test");
    absorber.setFoamingDesignMargin(0.20);

    double effectiveCapacity = absorber.getEffectiveGasCapacityWithFoamingMargin(100.0);
    assertEquals(120.0, effectiveCapacity, 1e-10);
  }

  @Test
  public void testDesignValidation() {
    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("Test");
    absorber.setFoamingDesignMargin(0.20);
    absorber.setApproachToEquilibrium(0.70);
    absorber.checkAmineTemperatureMargin(30.0, 37.0);

    Map<String, SimpleAmineAbsorber.DesignCheck> checks = absorber.validateDesign();

    assertNotNull(checks);
    assertTrue(checks.containsKey("foaming_margin"));
    assertTrue(checks.get("foaming_margin").isPassed());
    assertTrue(checks.containsKey("approach_to_equilibrium"));
    assertTrue(checks.get("approach_to_equilibrium").isPassed());
    assertTrue(checks.containsKey("amine_temperature"));
    assertTrue(checks.get("amine_temperature").isPassed());
  }

  @Test
  public void testDesignSummaryOutput() {
    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("Test");
    absorber.setAmineType("MDEA");
    absorber.calcRichAmineLoading(0.50);
    absorber.calcPackingHeight(1.0, 8.0);

    String summary = absorber.getDesignSummary();
    assertNotNull(summary);
    assertTrue(summary.contains("Amine Absorber Design Summary"));
    assertTrue(summary.contains("MDEA"));
  }

  @Test
  public void testGasCarryUnder() {
    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("Test");
    assertEquals(0.03, absorber.getGasCarryUnder(), 1e-10);
    absorber.setGasCarryUnder(0.05);
    assertEquals(0.05, absorber.getGasCarryUnder(), 1e-10);
  }

  // ============================================================================
  // ProcessSystem integration tests
  // ============================================================================

  @Test
  public void testAmineAbsorberInProcessSystem() {
    // Build a sour gas fluid with methane + CO2
    SystemSrkEos sourGas = new SystemSrkEos(273.15 + 40.0, 70.0);
    sourGas.addComponent("methane", 0.90);
    sourGas.addComponent("CO2", 0.10);
    sourGas.setMixingRule("classic");

    Stream sourGasStream = new Stream("Sour Gas Feed", sourGas);
    sourGasStream.setFlowRate(50000.0, "kg/hr");

    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("MDEA Absorber", sourGasStream);
    absorber.setCO2RemovalEfficiency(0.90);

    ProcessSystem process = new ProcessSystem();
    process.add(sourGasStream);
    process.add(absorber);
    process.run();

    // Verify streams are wired correctly
    List<StreamInterface> inlets = absorber.getInletStreams();
    List<StreamInterface> outlets = absorber.getOutletStreams();
    assertEquals(1, inlets.size()); // only sour gas (no lean amine set)
    assertEquals(1, outlets.size()); // only sweet gas (no rich amine without lean amine)

    // Sweet gas should have much less CO2 than the feed
    StreamInterface sweetGas = absorber.getSweetGasOutStream();
    assertNotNull(sweetGas);
    assertNotNull(sweetGas.getThermoSystem());

    double feedCO2 =
        sourGasStream.getThermoSystem().getPhase(0).getComponent("CO2").getNumberOfmoles();
    double sweetCO2 = sweetGas.getThermoSystem().getPhase(0).getComponent("CO2").getNumberOfmoles();
    assertTrue(sweetCO2 < feedCO2 * 0.15, "CO2 in sweet gas should be <15% of feed CO2");
  }

  @Test
  public void testAmineAbsorberStreamIntrospection() {
    SystemSrkEos sourGas = new SystemSrkEos(273.15 + 35.0, 60.0);
    sourGas.addComponent("methane", 0.85);
    sourGas.addComponent("CO2", 0.10);
    sourGas.addComponent("H2S", 0.005);
    sourGas.addComponent("ethane", 0.045);
    sourGas.setMixingRule("classic");

    Stream sourGasStream = new Stream("Sour Gas", sourGas);
    sourGasStream.setFlowRate(30000.0, "kg/hr");

    SystemSrkEos amineFluid = new SystemSrkEos(273.15 + 42.0, 60.0);
    amineFluid.addComponent("MDEA", 0.50);
    amineFluid.addComponent("water", 0.50);
    amineFluid.setMixingRule("classic");

    Stream leanAmineStream = new Stream("Lean Amine", amineFluid);
    leanAmineStream.setFlowRate(20000.0, "kg/hr");

    SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("Amine Absorber", sourGasStream);
    absorber.setLeanAmineInStream(leanAmineStream);
    absorber.setCO2RemovalEfficiency(0.95);
    absorber.setH2SRemovalEfficiency(0.99);

    ProcessSystem process = new ProcessSystem();
    process.add(sourGasStream);
    process.add(leanAmineStream);
    process.add(absorber);
    process.run();

    // Both inlet streams should be reported
    List<StreamInterface> inlets = absorber.getInletStreams();
    assertEquals(2, inlets.size());

    // Both outlet streams should be reported
    List<StreamInterface> outlets = absorber.getOutletStreams();
    assertEquals(2, outlets.size());

    // Rich amine should exist
    StreamInterface richAmine = absorber.getRichAmineOutStream();
    assertNotNull(richAmine);
    assertNotNull(richAmine.getThermoSystem());
  }
}
