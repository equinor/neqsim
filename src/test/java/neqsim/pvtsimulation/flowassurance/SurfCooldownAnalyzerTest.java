package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link SurfCooldownAnalyzer} SURF cooldown and no-touch-time screening.
 *
 * @author ESOL
 * @version 1.0
 */
public class SurfCooldownAnalyzerTest extends neqsim.NeqSimTest {

  /**
   * Builds a representative wet hydrocarbon gas with free water for hydrate evaluation.
   *
   * @param temperatureCelsius operating temperature in degrees Celsius
   * @param pressureBara operating pressure in bara
   * @return a configured fluid
   */
  private SystemInterface wetGas(double temperatureCelsius, double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(273.15 + temperatureCelsius, pressureBara);
    fluid.addComponent("methane", 0.82);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("water", 0.09);
    fluid.setMixingRule("classic");
    return fluid;
  }

  @Test
  public void testNoTouchTimeIsPositiveAndVerdictSet() {
    SurfCooldownAnalyzer analyzer = new SurfCooldownAnalyzer(wetGas(65.0, 120.0));
    analyzer.setInternalDiameter(0.254);
    analyzer.setWallThickness(0.0159);
    analyzer.setInsulationThickness(0.060);
    analyzer.setOverallUValue(2.5);
    analyzer.setSeabedTemperature(4.0);
    analyzer.setHydrateMargin(3.0);
    analyzer.calculate();

    assertTrue(analyzer.getNoTouchTimeHours() > 0.0, "no-touch time should be positive");
    assertNotNull(analyzer.getVerdict());
    assertNotNull(analyzer.toJson());
    assertTrue(analyzer.getFluidDensity() > 0.0, "fluid density should be extracted");
    assertTrue(analyzer.getFluidSpecificHeat() > 0.0, "specific heat should be extracted");
    assertTrue(analyzer.getHydrateEquilibriumTemperatureK() > 273.0,
	"hydrate equilibrium temperature should be computed");
  }

  @Test
  public void testThinnerInsulationReducesNoTouchTime() {
    SurfCooldownAnalyzer thick = new SurfCooldownAnalyzer(wetGas(65.0, 120.0));
    thick.setOverallUValue(1.5);
    thick.setSeabedTemperature(4.0);
    thick.calculate();

    SurfCooldownAnalyzer thin = new SurfCooldownAnalyzer(wetGas(65.0, 120.0));
    thin.setOverallUValue(6.0);
    thin.setSeabedTemperature(4.0);
    thin.calculate();

    assertTrue(thin.getNoTouchTimeHours() < thick.getNoTouchTimeHours(),
	"higher U-value (poorer insulation) must give a shorter no-touch time");
  }

  @Test
  public void testRequiredNoTouchTimeDrivesVerdict() {
    SurfCooldownAnalyzer analyzer = new SurfCooldownAnalyzer(wetGas(65.0, 120.0));
    analyzer.setOverallUValue(3.0);
    analyzer.setSeabedTemperature(4.0);
    // Demand an unrealistically long no-touch time to force a CRITICAL verdict.
    analyzer.setRequiredNoTouchTimeHours(10000.0);
    analyzer.calculate();

    assertEquals(SurfCooldownAnalyzer.VERDICT_CRITICAL, analyzer.getVerdict(),
	"an unreachable required no-touch time must yield CRITICAL");
  }

  @Test
  public void testDryGasReportsNoHydrateRisk() {
    SystemInterface dryGas = new SystemSrkEos(273.15 + 65.0, 120.0);
    dryGas.addComponent("methane", 0.90);
    dryGas.addComponent("ethane", 0.07);
    dryGas.addComponent("propane", 0.03);
    dryGas.setMixingRule("classic");

    SurfCooldownAnalyzer analyzer = new SurfCooldownAnalyzer(dryGas);
    analyzer.setOverallUValue(3.0);
    analyzer.setSeabedTemperature(4.0);
    analyzer.calculate();

    assertEquals(SurfCooldownAnalyzer.VERDICT_NO_HYDRATE_RISK, analyzer.getVerdict(),
	"a dry gas without free water should report no hydrate risk");
  }
}
