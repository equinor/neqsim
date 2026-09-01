package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for point-specific typed network quality specifications.
 */
class NetworkQualitySpecificationTest {
  @Test
  void testNamedGasPointWithExplicitReferenceConditions() {
    SystemInterface gas = new SystemSrkEos(288.15, 70.0);
    gas.addComponent("methane", 0.94);
    gas.addComponent("ethane", 0.03);
    gas.addComponent("CO2", 0.02);
    gas.addComponent("water", 0.01);
    gas.setMixingRule("classic");

    NetworkQualityProfile profile = new NetworkQualityProfile("Area D synthetic");
    profile.withEffectivePeriod("2026-education", "2026-01-01", null);
    profile.withProvenance("Synthetic public-data example");
    profile.addUpperLimit(GasQualityMetric.CO2_MOLE_PERCENT, 2.5, "mol%");
    profile.addRange(GasQualityMetric.WOBBE_INDEX, 40.0, 60.0, "MJ/Sm3",
        new QualityReference().withIso6976Reference(15.0, 15.0));

    LoopedPipeNetwork network = new LoopedPipeNetwork("quality points");
    network.setFluidTemplate(gas);
    network.addJunctionNode("Kollsnes D2");
    network.getNode("Kollsnes D2").setPressure(70.0e5);
    network.setNodeFluid("Kollsnes D2", gas);
    network.setQualityProfile("Kollsnes D2", profile);

    Map<String, NetworkQualityComplianceReport> reports = network.evaluateQualityProfiles();
    NetworkQualityComplianceReport report = reports.get("Kollsnes D2");

    assertNotNull(report);
    assertTrue(report.isCompliant());
    assertEquals(2, report.getResults().size());
    assertEquals(NetworkQualityResult.Status.PASS, report.getResults().get(0).getStatus());
    assertEquals("2026-education", report.getProfileVersion());
  }

  @Test
  void testNotCalculableMeasuredAttributeFailsExplicitly() {
    SystemInterface oil = new SystemSrkEos(288.15, 1.01325);
    oil.addComponent("nC10", 1.0);
    oil.setMixingRule("classic");
    NetworkQualityProfile profile = new NetworkQualityProfile("Synthetic cargo");
    profile.addMeasuredAttributeLimit("oil", "sulfurMassPercent", null, 1.0, "mass%", "ASTM D4294");

    NetworkQualityComplianceReport report = NetworkQualityEvaluator.evaluate("cargo", profile, oil, null);

    assertFalse(report.isCompliant());
    assertEquals(NetworkQualityResult.Status.NOT_CALCULABLE, report.getResults().get(0).getStatus());
    assertTrue(report.getResults().get(0).getMessage().contains("not supplied"));
  }

  @Test
  void testMeasuredOilAttributeAndJsonRoundTrip() {
    SystemInterface oil = new SystemSrkEos(288.15, 1.01325);
    oil.addComponent("nC10", 1.0);
    oil.setMixingRule("classic");
    NetworkQualityProfile profile = new NetworkQualityProfile("Synthetic blend");
    profile.withEffectivePeriod("v1", "2026-01-01", null);
    profile.addMeasuredAttributeLimit("oil", "sulfurMassPercent", 0.0, 1.0, "mass%", "ASTM D4294");

    LoopedPipeNetwork network = new LoopedPipeNetwork("oil quality");
    network.setFluidTemplate(oil);
    network.addJunctionNode("blend tank");
    network.getNode("blend tank").setPressure(1.01325e5);
    network.setNodeFluid("blend tank", oil);
    network.setQualityAttribute("blend tank", "sulfurMassPercent", 0.35, "mass%", "ASTM D4294", "Synthetic assay",
        "2026-01-01", "mass-weighted");
    network.setQualityProfile("blend tank", profile);
    NetworkQualityComplianceReport report = network.evaluateQualityProfiles().get("blend tank");

    assertTrue(report.isCompliant());
    assertEquals(0.35, report.getResults().get(0).getMargin(), 1.0e-12);
    NetworkQualityProfile restored = NetworkQualityProfile.fromJson(profile.toJson());
    assertEquals("Synthetic blend", restored.getName());
    assertEquals(1, restored.getLimits().size());
    NetworkQualityComplianceReport restoredReport = NetworkQualityComplianceReport.fromJson(report.toJson());
    assertTrue(restoredReport.isCompliant());
    assertEquals("blend tank", restoredReport.getNodeName());
  }

  @Test
  void testBargAndTemperatureReferencesAreUnambiguous() {
    QualityReference reference = QualityReference.atPressureAndTemperature(50.0, "barg", -10.0, "C");
    assertEquals(51.01325, reference.getPressureBara(), 1.0e-12);
    assertEquals(263.15, reference.getTemperatureK(), 1.0e-12);
  }

  @Test
  void testApiGravityAtExplicitTemperatureIsNotReportedAsDensity() {
    SystemInterface oil = new SystemSrkEos(288.15, 5.0);
    oil.addComponent("nC10", 0.7);
    oil.addComponent("nC16", 0.3);
    oil.setMixingRule("classic");
    NetworkQualityProfile profile = new NetworkQualityProfile("Synthetic API");
    profile.addRange(OilQualityMetric.API_GRAVITY, 5.0, 80.0, "degAPI", QualityReference.atTemperature(15.0, "C"));

    NetworkQualityResult result = NetworkQualityEvaluator.evaluate("cargo", profile, oil, null).getResults().get(0);

    assertEquals(NetworkQualityResult.Status.PASS, result.getStatus());
    assertTrue(result.getValue() > 5.0 && result.getValue() < 80.0);
  }

  @Test
  void testOilVaporPressureMetricsUseDistinctMethods() {
    SystemInterface oil = new SystemSrkEos(275.15, 1.0);
    oil.addComponent("methane", 0.0006538);
    oil.addComponent("ethane", 0.006538);
    oil.addComponent("propane", 0.06538);
    oil.addComponent("n-pentane", 0.1545);
    oil.addComponent("nC10", 0.545);
    oil.setMixingRule(2);

    QualityReference reference = QualityReference.atTemperature(37.8, "C");
    NetworkQualityProfile profile = new NetworkQualityProfile("Vapor pressure methods");
    profile.addRange(OilQualityMetric.TRUE_VAPOR_PRESSURE, 0.0, 10.0, "bara", reference);
    profile.addRange(OilQualityMetric.REID_VAPOR_PRESSURE, 0.0, 10.0, "bara", reference);
    profile.addRange(OilQualityMetric.VPCR4, 0.0, 10.0, "bara", reference);

    NetworkQualityComplianceReport report = NetworkQualityEvaluator.evaluate("cargo", profile, oil, null);

    assertTrue(report.isCompliant());
    assertEquals(1.6663, report.getResults().get(0).getValue(), 1.0e-3);
    assertEquals(0.9653, report.getResults().get(1).getValue(), 1.0e-3);
    assertEquals(1.1574, report.getResults().get(2).getValue(), 1.0e-3);
    assertTrue(report.getResults().get(0).getMethod().contains("Standard_TVP"));
    assertTrue(report.getResults().get(1).getMethod().contains("RVP equivalent"));
    assertTrue(report.getResults().get(2).getMethod().contains("VPCR4"));
  }
}
