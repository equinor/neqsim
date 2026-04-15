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

    // At high temperatures, risk should be low
    HydrateRiskMapper.RiskPoint firstPoint = profile.getPoints().get(0);
    assertTrue(firstPoint.riskLevel == RiskLevel.LOW || firstPoint.riskLevel == RiskLevel.MEDIUM,
        "At 60°C, risk should be low or medium");

    String json = profile.toJson();
    assertNotNull(json);
    assertFalse(json.isEmpty());
    assertTrue(json.contains("riskLevel"), "JSON should contain risk levels");
  }

  @Test
  public void testCustomThresholds() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 10.0, 80.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
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
    fluid.setMixingRule("classic");

    HydrateRiskMapper mapper = new HydrateRiskMapper(fluid);
    mapper.addProfilePoint(0.0, 100.0, 50.0);
    mapper.addProfilePoint(50.0, 80.0, 5.0);

    HydrateRiskMapper.RiskProfile profile = mapper.calculate();

    assertNotNull(profile.getOverallRisk());
  }
}
