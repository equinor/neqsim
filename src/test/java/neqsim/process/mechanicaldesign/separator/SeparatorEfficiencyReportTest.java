package neqsim.process.mechanicaldesign.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.separator.internals.DemistingInternal;
import neqsim.process.mechanicaldesign.separator.internals.InternalOperatingWindow;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the separator/scrubber separation-efficiency model ({@link SeparatorEfficiencyReport},
 * {@link InternalOperatingWindow}, and the {@code calculateSeparationEfficiency()} entry point on
 * {@link SeparatorMechanicalDesign}).
 *
 * @author NeqSim
 */
public class SeparatorEfficiencyReportTest {

  /**
   * Builds a run, sized two-phase separator on a gas-condensate fluid.
   *
   * @return the sized separator
   */
  private Separator buildTwoPhaseSeparator() {
    SystemInterface fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-heptane", 0.08);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(40.0, "bara");
    feed.run();

    Separator sep = new Separator("two-phase separator", feed);
    sep.run();

    SeparatorMechanicalDesign md = (SeparatorMechanicalDesign) sep.getMechanicalDesign();
    md.calcDesign();
    md.setDesign();
    return sep;
  }

  /**
   * Builds a run, sized three-phase separator on a gas-oil-water fluid.
   *
   * @return the sized three-phase separator
   */
  private ThreePhaseSeparator buildThreePhaseSeparator() {
    SystemInterface fluid = new SystemSrkCPAstatoil(318.15, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("nC10", 0.15);
    fluid.addComponent("water", 0.10);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(80000.0, "kg/hr");
    feed.setTemperature(45.0, "C");
    feed.setPressure(50.0, "bara");
    feed.run();

    ThreePhaseSeparator sep = new ThreePhaseSeparator("three-phase separator", feed);
    sep.run();

    SeparatorMechanicalDesign md = (SeparatorMechanicalDesign) sep.getMechanicalDesign();
    md.calcDesign();
    md.setDesign();
    return sep;
  }

  @Test
  public void testTwoPhaseEfficiencyReport() {
    Separator sep = buildTwoPhaseSeparator();
    SeparatorMechanicalDesign md = (SeparatorMechanicalDesign) sep.getMechanicalDesign();

    SeparatorEfficiencyReport report = md.calculateSeparationEfficiency();
    assertNotNull(report, "report should not be null");
    assertFalse(report.isThreePhase(), "gas-condensate case should be two-phase");
    assertTrue(report.getOperatingKFactor() > 0.0, "operating K-factor should be positive after sizing");
    assertFalse(report.getWindows().isEmpty(), "at least the mist eliminator window should be present");

    double eff = report.getOverallGasLiquidEfficiency();
    assertTrue(eff >= 0.0 && eff <= 1.0, "overall gas-liquid efficiency should be in [0,1]");
    assertNotNull(report.getVerdict(), "verdict should be set");

    String json = report.toJson();
    assertNotNull(json);
    assertTrue(json.contains("operatingKFactor_m_s"), "JSON should contain K-factor field");
    assertTrue(json.contains("internals"), "JSON should contain internals windows");
  }

  @Test
  public void testThreePhaseEfficiencyReport() {
    ThreePhaseSeparator sep = buildThreePhaseSeparator();
    SeparatorMechanicalDesign md = (SeparatorMechanicalDesign) sep.getMechanicalDesign();

    SeparatorEfficiencyReport report = md.calculateSeparationEfficiency();
    assertNotNull(report);
    assertTrue(report.isThreePhase(), "gas-oil-water case should be three-phase");
    assertTrue(report.getOperatingKFactor() > 0.0);

    double eff = report.getOverallGasLiquidEfficiency();
    assertTrue(eff >= 0.0 && eff <= 1.0, "overall gas-liquid efficiency should be in [0,1]");

    // Liquid-liquid carry-over fractions must be physical.
    assertTrue(report.getOilInWaterFraction() >= 0.0 && report.getOilInWaterFraction() <= 1.0);
    assertTrue(report.getWaterInOilFraction() >= 0.0 && report.getWaterInOilFraction() <= 1.0);

    String json = report.toJson();
    assertTrue(json.contains("oilInWaterFraction"), "three-phase JSON should include liquid-liquid fractions");
  }

  @Test
  public void testEfficiencyModelDisabledByDefault() {
    Separator sep = buildTwoPhaseSeparator();
    SeparatorMechanicalDesign md = (SeparatorMechanicalDesign) sep.getMechanicalDesign();

    assertFalse(md.isEfficiencyModelEnabled(), "efficiency model must be off by default");
    assertFalse(sep.isDetailedEntrainmentCalculation(), "detailed entrainment must be off by default");

    // Producing a report is read-only and must not switch on the run-time model.
    md.calculateSeparationEfficiency();
    assertFalse(sep.isDetailedEntrainmentCalculation(),
        "generating a report must not enable the run-time entrainment model");
  }

  @Test
  public void testEnableEfficiencyModelTogglesSeparator() {
    Separator sep = buildTwoPhaseSeparator();
    SeparatorMechanicalDesign md = (SeparatorMechanicalDesign) sep.getMechanicalDesign();

    md.setEfficiencyModelEnabled(true);
    assertTrue(md.isEfficiencyModelEnabled());
    assertTrue(sep.isDetailedEntrainmentCalculation(), "enabling efficiency model enables detailed entrainment");
    assertNotNull(sep.getPerformanceCalculator().getMistEliminatorCurve(),
        "enabling the model configures the mist eliminator curve");

    sep.run();

    md.setEfficiencyModelEnabled(false);
    assertFalse(md.isEfficiencyModelEnabled());
    assertFalse(sep.isDetailedEntrainmentCalculation(), "disabling efficiency model disables detailed entrainment");
  }

  @Test
  public void testManualEntrainmentStillWorksWithModelOff() {
    Separator sep = buildTwoPhaseSeparator();
    SeparatorMechanicalDesign md = (SeparatorMechanicalDesign) sep.getMechanicalDesign();

    assertFalse(md.isEfficiencyModelEnabled());
    // Manually specify 1% oil carry-over to gas — the legacy capability.
    sep.setEntrainment(0.01, "mole", "feed", "oil", "gas");
    sep.run();

    // The manual specification must not be overridden by the physics model (which is off).
    assertFalse(sep.isDetailedEntrainmentCalculation());
    assertNotNull(sep.getGasOutStream());
  }

  @Test
  public void testKFactorWindowClassification() {
    // Wire-mesh default window is roughly [0.02, 0.107] m/s.
    DemistingInternal wireMesh = DemistingInternal.fromDatabase("wire_mesh", "");
    assertTrue(wireMesh.getMaxKFactor() > wireMesh.getMinKFactor(), "max K must exceed min K");

    double minK = wireMesh.getMinKFactor();
    double maxK = wireMesh.getMaxKFactor();

    InternalOperatingWindow belowWindow = wireMesh.getOperatingWindow(minK * 0.5);
    assertEquals(InternalOperatingWindow.WindowStatus.BELOW_MIN_TURNDOWN, belowWindow.getStatus(),
        "K below minimum should classify as below-turndown");

    InternalOperatingWindow inWindow = wireMesh.getOperatingWindow((minK + maxK) / 2.0);
    assertEquals(InternalOperatingWindow.WindowStatus.IN_RANGE, inWindow.getStatus(),
        "K inside the window should classify as in-range");
    assertTrue(inWindow.isInRange());

    InternalOperatingWindow aboveWindow = wireMesh.getOperatingWindow(maxK * 1.5);
    assertEquals(InternalOperatingWindow.WindowStatus.ABOVE_MAX_FLOODING, aboveWindow.getStatus(),
        "K above maximum should classify as flooding");
    assertTrue(aboveWindow.getUtilization() > 1.0, "utilization above maximum K must exceed 1.0");
  }

  @Test
  public void testDatabaseSubTypeLookup() {
    DemistingInternal highEff = DemistingInternal.fromDatabase("wire_mesh", "High Efficiency");
    assertEquals("wire_mesh", highEff.getType());
    assertEquals("High Efficiency", highEff.getSubType());
    assertTrue(highEff.getMaxKFactor() > 0.0);
    assertTrue(highEff.getMaxEfficiency() > 0.99, "high-efficiency mesh should have very high rated efficiency");

    // Vane pack lookup maps to the VANE_PACK database type.
    DemistingInternal vane = DemistingInternal.fromDatabase("vane_pack", "");
    assertEquals("vane_pack", vane.getType());
    assertTrue(vane.getMaxKFactor() > 0.0);
  }
}
