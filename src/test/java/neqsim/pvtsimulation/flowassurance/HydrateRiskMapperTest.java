package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.flowassurance.HydrateRiskMapper.RiskLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for HydrateRiskMapper pipeline hydrate risk assessment.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class HydrateRiskMapperTest extends neqsim.NeqSimTest {

  @Test
  public void testSimpleGasProfile() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 100.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("CO2", 0.02);
    fluid.addComponent("water", 0.05);
    fluid.setMixingRule("classic");

    HydrateRiskMapper mapper = new HydrateRiskMapper(fluid);

    // Add typical pipeline profile points (km, bara, °C)
    mapper.addProfilePoint(0.0, 100.0, 60.0);
    mapper.addProfilePoint(10.0, 95.0, 40.0);
    mapper.addProfilePoint(20.0, 90.0, 25.0);
    mapper.addProfilePoint(30.0, 85.0, 15.0);
    mapper.addProfilePoint(40.0, 80.0, 8.0);
    mapper.addProfilePoint(50.0, 75.0, 4.0);

    HydrateRiskMapper.RiskProfile profile = mapper.calculate();

    assertNotNull(profile);
    assertTrue(profile.getPoints().size() == 6, "Should have 6 risk points");
    assertTrue(profile.getFailureReasons().isEmpty(), "Wet gas must converge: " + profile.getFailureReasons());
    assertTrue(profile.getUnknownPointCount() == 0);

    // At 60 C the line is far above the hydrate curve; at 4 C and 75 bara it is inside it
    HydrateRiskMapper.RiskPoint firstPoint = profile.getPoints().get(0);
    assertTrue(firstPoint.riskLevel == RiskLevel.LOW, "At 60°C, risk should be low but was " + firstPoint.riskLevel);
    assertTrue(!Double.isNaN(firstPoint.hydrateTemperatureC));
    HydrateRiskMapper.RiskPoint lastPoint = profile.getPoints().get(5);
    assertTrue(lastPoint.riskLevel == RiskLevel.CRITICAL, "At 4°C, 75 bara the line is in the hydrate region");
    assertTrue(profile.getOverallRisk() == RiskLevel.CRITICAL);
    assertTrue(profile.getCriticalPointCount() >= 1);

    String json = profile.toJson();
    assertNotNull(json);
    assertFalse(json.isEmpty());
    assertTrue(json.contains("riskLevel"), "JSON should contain risk levels");
    assertTrue(json.contains("unknownPointCount"));
  }

  @Test
  public void testDryGasIsUnknownNotLow() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 10.0, 80.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    HydrateRiskMapper mapper = new HydrateRiskMapper(fluid);
    mapper.addProfilePoint(0.0, 80.0, 20.0);
    mapper.addProfilePoint(10.0, 75.0, 10.0);

    HydrateRiskMapper.RiskProfile profile = mapper.calculate();

    // No water: there is no hydrate equilibrium to compare against, so the result must not be reported as safe.
    assertTrue(profile.getOverallRisk() == RiskLevel.UNKNOWN, "was " + profile.getOverallRisk());
    assertTrue(profile.getUnknownPointCount() == 2);
    assertTrue(Double.isNaN(profile.getMinimumSubcoolingC()));
    assertFalse(profile.getFailureReasons().isEmpty());
    assertTrue(profile.getFailureReasons().get(0).contains("water"));
    assertTrue(profile.toJson().contains("failureReasons"));
  }

  @Test
  public void testCustomThresholds() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 10.0, 80.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("water", 0.02);
    fluid.setMixingRule("classic");

    HydrateRiskMapper mapper = new HydrateRiskMapper(fluid);
    mapper.setRiskThresholds(4.0, 8.0);

    mapper.addProfilePoint(0.0, 80.0, 20.0);
    mapper.addProfilePoint(10.0, 75.0, 10.0);

    HydrateRiskMapper.RiskProfile profile = mapper.calculate();

    assertNotNull(profile);
    assertTrue(profile.getPoints().size() == 2);
  }

  @Test
  public void testEmptyProfileThrows() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    HydrateRiskMapper mapper = new HydrateRiskMapper(fluid);

    try {
      mapper.calculate();
      assertTrue(false, "Should have thrown IllegalStateException");
    } catch (IllegalStateException e) {
      // Expected
      assertTrue(e.getMessage().contains("profile point"));
    }
  }

  @Test
  public void testOverallRisk() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 10.0, 100.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("water", 0.02);
    fluid.setMixingRule("classic");

    HydrateRiskMapper mapper = new HydrateRiskMapper(fluid);
    mapper.addProfilePoint(0.0, 100.0, 50.0);
    mapper.addProfilePoint(50.0, 80.0, 5.0);

    HydrateRiskMapper.RiskProfile profile = mapper.calculate();

    assertNotNull(profile.getOverallRisk());
    assertTrue(profile.getOverallRisk() != RiskLevel.UNKNOWN, profile.getFailureReasons().toString());
  }
}
