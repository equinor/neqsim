package neqsim.process.fielddevelopment.screening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.concept.FieldConcept;

/**
 * Regression tests for the fugitive methane CO2e basis in the screening EmissionsTracker.
 *
 * <p>
 * 1 MSm3/d of gas at 0.01% fugitive loss and 0.7 kg/Sm3 methane is 25.55 t CH4 per year, which is 715.4 t CO2e with the
 * IPCC AR5 100-year GWP of 28.
 * </p>
 */
class EmissionsTrackerTest {
  private static final double EXPECTED_FUGITIVE_CO2E_TONNES = 715.4;

  @Test
  @DisplayName("Fugitive CO2e for 1 MSm3/d uses the AR5 methane GWP")
  void testFugitiveEmissionsUseAr5Gwp() {
    FieldConcept concept = FieldConcept.gasTieback("GWP regression", 30.0, 2, 0.5);

    EmissionsTracker.EmissionsReport report = new EmissionsTracker().quickEstimate(concept);

    assertEquals(EXPECTED_FUGITIVE_CO2E_TONNES, report.getFugitiveEmissionsTonnesPerYear(), 1e-6);
    assertEquals(EXPECTED_FUGITIVE_CO2E_TONNES, report.getEmissionSources().get("fugitive"), 1e-6);
  }

  @Test
  @DisplayName("Lifecycle fugitive CO2e follows the same AR5 methane GWP")
  void testLifecycleFugitiveEmissionsUseAr5Gwp() {
    FieldConcept concept = FieldConcept.gasTieback("GWP regression", 30.0, 2, 0.5);
    Map<Integer, Double> profile = new LinkedHashMap<Integer, Double>();
    profile.put(2030, 365.0e6);
    profile.put(2031, 182.5e6);

    LifecycleEmissionsProfile lifecycle = new EmissionsTracker().estimateLifecycle(concept, null, profile);
    List<LifecycleEmissionsProfile.AnnualEmissions> annual = lifecycle.getAnnualEmissions();

    assertEquals(2, annual.size());
    assertEquals(EXPECTED_FUGITIVE_CO2E_TONNES, annual.get(0).getFugitiveEmissionsTonnes(), 1e-6);
    assertEquals(EXPECTED_FUGITIVE_CO2E_TONNES / 2.0, annual.get(1).getFugitiveEmissionsTonnes(), 1e-6);
  }
}
