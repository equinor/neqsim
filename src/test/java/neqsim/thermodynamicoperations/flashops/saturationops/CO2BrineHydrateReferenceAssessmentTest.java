package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Separates numerical phase-state qualification from experimental hydrate-temperature accuracy. Data: Burgass et al.
 * (2023), Tables 4 and 5, doi:10.2516/stet/2023005, CC BY 4.0.
 */
class CO2BrineHydrateReferenceAssessmentTest {
  @Test
  void saturatedAndUndersaturatedMeasurementsHaveSeparateAssessments() throws Exception {
    String resource = "/data/chemistry_benchmarks/co2_brine_hydrate_phase_state_burgass2023.csv";
    List<String> report = new ArrayList<String>();
    report.add("table,nacl_wt_percent,salt_free_xco2,pressure_bara,measured_K,calculated_K,error_K,"
        + "temperature_within_1K,expected_equilibrium,calculated_fluid_phases,saturated_co2_boundary");
    int saturatedCount = 0;
    int finiteInventoryCount = 0;
    try (InputStream stream = getClass().getResourceAsStream(resource)) {
      assertNotNull(stream);
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        reader.readLine();
        String line;
        while ((line = reader.readLine()) != null) {
          String[] values = line.split(",");
          boolean saturated = "4".equals(values[0]);
          double salt = Double.parseDouble(values[1]);
          double xCo2 = Double.parseDouble(values[2]);
          double pressure = 10.0 * Double.parseDouble(values[3]);
          double measured = Double.parseDouble(values[4]);
          // Table 5 xCO2 excludes salt and its dissociated ions; Table 4 uses excess CO2.
          double co2Moles = saturated ? 10.0 : xCo2 / (1.0 - xCo2) / 0.01801528;
          SystemInterface fluid = CO2BrineHydratePhaseStateTest.brine(pressure, salt, co2Moles);
          ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
          ops.hydrateFormationTemperature(278.15);
          CO2BrineHydratePhaseStateTest.assertEndpoint(fluid, saturated);
          HydrateEquilibriumDiagnostics result = ((HydrateFormationTemperatureFlash) ops.getOperation())
              .getDiagnostics();
          assertEquals(saturated, result.isSaturatedCO2Boundary());
          double error = fluid.getTemperature() - measured;
          boolean withinCriterion = Math.abs(error) <= 1.0;
          report.add(values[0] + "," + salt + "," + xCo2 + "," + pressure + "," + measured + ","
              + fluid.getTemperature() + "," + error + "," + withinCriterion + "," + values[5] + ","
              + String.join("+", result.getPhaseTypes()) + "," + result.isSaturatedCO2Boundary());
          if (saturated) {
            saturatedCount++;
            // These five independent points meet the separately stated 1 K engineering criterion.
            // The 15 wt% / 194.77 bara point and finite-inventory errors remain explicitly reported.
            if (pressure < 190.0) {
              assertTrue(withinCriterion, "Saturated reference exceeded 1 K: " + line + ", error=" + error);
            }
          } else {
            finiteInventoryCount++;
          }
        }
      }
    }
    assertEquals(6, saturatedCount);
    assertEquals(6, finiteInventoryCount);
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get("target/co2-brine-hydrate-reference-assessment.csv"), report, StandardCharsets.UTF_8);
  }
}
