package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.util.empiric.BukacekWaterInGas;

/**
 * Tests for {@link AnalyserSampleSystem}.
 */
public class AnalyserSampleSystemTest {
  /**
   * Natural gas with a given water mole fraction at 63 bara and 25 C.
   *
   * @param waterMoleFraction water mole fraction
   * @return fluid
   */
  private static SystemInterface gas(double waterMoleFraction) {
    SystemInterface f = new SystemSrkCPAstatoil(273.15 + 25.0, 63.0);
    double dry = 1.0 - waterMoleFraction;
    f.addComponent("methane", 0.85 * dry);
    f.addComponent("ethane", 0.08 * dry);
    f.addComponent("propane", 0.04 * dry);
    f.addComponent("n-butane", 0.02 * dry);
    f.addComponent("n-hexane", 0.01 * dry);
    f.addComponent("water", waterMoleFraction);
    f.addComponent("TEG", 0.0);
    f.setMixingRule(10);
    return f;
  }

  @Test
  void dryGasDoesNotCondenseWaterInWarmTube() {
    double x = BukacekWaterInGas.getWaterInGas(273.15 - 40.0, 63.0);
    AnalyserSampleSystem s = new AnalyserSampleSystem(gas(x));
    s.setTubeTemperatureC(20.0);
    assertEquals(-40.0, s.getWaterDewPointAtTapC(), 0.05);
    assertFalse(s.isWaterCondensingInTube());
    assertEquals(60.0, s.getWaterCondensationMarginK(), 0.1);
    // pressure letdown lowers the dew point
    assertTrue(s.getWaterDewPointAtRegulatorOutletC() < s.getWaterDewPointAtTapC() - 10.0);
  }

  @Test
  void wetGasCondensesInColdTube() {
    double x = BukacekWaterInGas.getWaterInGas(273.15 + 25.0, 63.0);
    AnalyserSampleSystem s = new AnalyserSampleSystem(gas(x));
    s.setTubeTemperatureC(20.0);
    assertTrue(s.isWaterCondensingInTube());
    s.setTubeTemperatureC(30.0);
    assertFalse(s.isWaterCondensingInTube());
  }

  @Test
  void hydrocarbonDewPointIsFinite() {
    AnalyserSampleSystem s = new AnalyserSampleSystem(gas(50.0e-6));
    double hcdp = s.getHydrocarbonDewPointC(63.0);
    assertTrue(Double.isFinite(hcdp), "hydrocarbon dew point should be finite");
    assertTrue(hcdp > -60.0 && hcdp < 60.0, "hydrocarbon dew point in a plausible range: " + hcdp);
  }

  @Test
  void transportLagScalesWithFlow() {
    AnalyserSampleSystem s = new AnalyserSampleSystem(gas(50.0e-6));
    s.setTubeGeometry(1.4e-3, 9.0);
    s.setFlowsNlPerHour(15.0, 90.0);
    double lag1 = s.getTransportLagSeconds();
    s.setFlowsNlPerHour(15.0, 0.0);
    double lag2 = s.getTransportLagSeconds();
    assertTrue(lag1 > 1.0 && lag1 < 600.0, "lag " + lag1);
    assertEquals(105.0 / 15.0, lag2 / lag1, 1.0e-6);
  }

  @Test
  void glycolFilmPersistsMuchLongerThanWaterFilm() {
    AnalyserSampleSystem s = new AnalyserSampleSystem(gas(50.0e-6));
    double water = s.getWaterFilmDryOutTimeHours(1.0e-3, 60.0, 2.0, 15.0);
    double teg = s.getGlycolFilmDryOutTimeHours(1.0e-3, 60.0, 2.0, 15.0);
    assertTrue(water > 0.0 && Double.isFinite(water));
    assertTrue(teg > 100.0 * water, "glycol film should persist far longer: water " + water + " h, TEG " + teg);
    Map<String, Object> r = s.getResults();
    assertTrue(r.containsKey("glycolFilm1g_dryOutAtCell_h"));
    assertTrue(s.toJson().contains("waterDewPointAtTap_C"));
  }
}
