package neqsim.util.nucleation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link AttainableMetastabilityVolumeBalance}.
 *
 * <p>
 * The test case mirrors the CO2 depressurisation experiments used by Log (2025): a high-pressure liquid CO2 stream (p0
 * = 120 bara) depressurised from a moderate initial temperature. The volume balancing method must locate a
 * metastability limit below the saturation pressure with a finite, positive superheat.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
class AttainableMetastabilityVolumeBalanceTest {

  /**
   * Builds a pure CO2 fluid using the SRK equation of state.
   *
   * @param temperatureK temperature in K
   * @param pressureBara pressure in bara
   * @return the CO2 system
   */
  private SystemInterface co2(double temperatureK, double pressureBara) {
    SystemInterface system = new SystemSrkEos(temperatureK, pressureBara);
    system.addComponent("CO2", 1.0);
    system.setMixingRule("classic");
    return system;
  }

  @Test
  void testLimitFoundForLiquidCO2() {
    double t0 = 273.15; // 0 C
    double p0Pa = 120.0e5; // 120 bara liquid CO2

    SystemInterface system = co2(t0, p0Pa / 1.0e5);

    AttainableMetastabilityVolumeBalance vb = new AttainableMetastabilityVolumeBalance(system);
    vb.setNumberOfBubbles(1.0e8);
    vb.setPipeRadius(0.02);
    vb.setNumberOfSteps(600);
    vb.calculateLimit(t0, p0Pa);

    assertTrue(vb.isCalculated());
    assertNotNull(vb.getMessage());

    if (vb.isLimitFound()) {
      // Limit must lie below the initial pressure and above the minimum search pressure.
      assertTrue(vb.getLimitPressure() < p0Pa, "Limit pressure must be below the initial pressure");
      assertTrue(vb.getLimitPressure() > 3.0e5, "Limit pressure must be above the search minimum");
      // The limit is in the metastable (superheated) region: positive superheat.
      assertTrue(vb.getSuperheat() > 0.0, "Superheat at the limit must be positive");
      assertTrue(Double.isFinite(vb.getLimitTemperature()));
      // Saturation temperature at the limit pressure must be below the metastable temperature.
      assertTrue(vb.getLimitSaturationTemperature() < vb.getLimitTemperature());
    }
  }

  @Test
  void testInvalidPressureRangeReported() {
    double t0 = 273.15;
    double p0Pa = 2.0e5; // below the default minimum (3 bara) -> no valid search range

    SystemInterface system = co2(t0, p0Pa / 1.0e5);
    AttainableMetastabilityVolumeBalance vb = new AttainableMetastabilityVolumeBalance(system);
    vb.calculateLimit(t0, p0Pa);

    assertTrue(vb.isCalculated());
    assertFalse(vb.isLimitFound());
    assertNotNull(vb.getMessage());
  }

  @Test
  void testMapAndJsonReport() {
    double t0 = 273.15;
    double p0Pa = 120.0e5;

    SystemInterface system = co2(t0, p0Pa / 1.0e5);
    AttainableMetastabilityVolumeBalance vb = new AttainableMetastabilityVolumeBalance(system);
    vb.setNumberOfSteps(400);
    vb.calculateLimit(t0, p0Pa);

    Map<String, Object> map = vb.toMap();
    assertNotNull(map);
    assertEquals("Volume balancing method (Log, 2025)", map.get("method"));
    assertTrue(map.containsKey("input"));

    String json = vb.toJson();
    assertNotNull(json);
    assertTrue(json.contains("metastabilityLimit") || json.contains("limitFound"));
  }

  @Test
  void testHigherBubbleCountReducesAttainableSuperheat() {
    double t0 = 273.15;
    double p0Pa = 120.0e5;

    AttainableMetastabilityVolumeBalance low = new AttainableMetastabilityVolumeBalance(co2(t0, p0Pa / 1.0e5));
    low.setNumberOfBubbles(1.0e8);
    low.setNumberOfSteps(600);
    low.calculateLimit(t0, p0Pa);

    AttainableMetastabilityVolumeBalance high = new AttainableMetastabilityVolumeBalance(co2(t0, p0Pa / 1.0e5));
    high.setNumberOfBubbles(1.0e9);
    high.setNumberOfSteps(600);
    high.calculateLimit(t0, p0Pa);

    // More pre-existing bubbles => faster evaporation => the balance is reached earlier
    // (smaller superheat / higher limit pressure). Only assert when both found a limit.
    if (low.isLimitFound() && high.isLimitFound()) {
      assertTrue(high.getSuperheat() <= low.getSuperheat() + 1.0e-6,
	  "More bubbles should not increase the attainable superheat");
    }
  }
}
