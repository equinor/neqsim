package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermo.phase.PitzerParameterDatasets;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Concentration coverage and independent experimental assessment. A successful test run includes explicitly recorded
 * accuracy gaps and unsupported measurements; it does not mean that every reference point meets the 1 K criterion.
 */
@Tag("slow")
public class PitzerHydrateSalinityValidationTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(PitzerHydrateSalinityValidationTest.class);
  private static final String[] CATIONS = { "Na+", "K+", "Ca++", "Mg++" };
  private static final double[] SALT_MOLAR_MASS = { 0.05844, 0.0745513, 0.11098, 0.095211 };
  private static final double TEMPERATURE_CRITERION_K = 1.0;

  private Map<String, Double> explicitZeroZeta() {
    Map<String, Double> values = new LinkedHashMap<String, Double>();
    for (String cation : CATIONS) {
      values.put(cation, 0.0);
    }
    return values;
  }

  /** Water and anhydrous salt mass parts, converted to moles on a one-kilogram water basis. */
  private SystemPitzer brine(double[] massParts, double pressure) {
    SystemPitzer fluid = new SystemPitzer(280.0, pressure);
    fluid.addComponent("CO2", 10.0); // Excess CO2 for the saturated reference measurements.
    fluid.addComponent("water", 1.0 / 0.01801528);
    double chloride = 0.0;
    for (int i = 0; i < CATIONS.length; i++) {
      double moles = massParts[i + 1] / massParts[0] / SALT_MOLAR_MASS[i];
      // Retain zero-amount ions: the fresh and saline limits must use the SAME neutral parameter dataset.
      fluid.addComponent(CATIONS[i], moles);
      chloride += moles * (i < 2 ? 1.0 : 2.0);
    }
    fluid.addComponent("Cl-", chloride);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    // All missing zeta terms and K-Mg theta are explicit screening assumptions, never fitted to the references.
    fluid.applyPhreeqcCo2ChlorideParameters(explicitZeroZeta(), 0.0);
    return fluid;
  }

  private SystemPitzer singleSalt(int salt, double weightPercent, double pressure) {
    double[] massParts = { 100.0 - weightPercent, 0.0, 0.0, 0.0, 0.0 };
    massParts[salt + 1] = weightPercent;
    return brine(massParts, pressure);
  }

  private PitzerHydrateFlash solve(SystemPitzer fluid) {
    PitzerHydrateFlash operation = new PitzerHydrateFlash(fluid, false);
    operation.run();
    assertTrue(operation.isConverged());
    assertEquals(0.0, operation.getResidual(), 1.0e-8);
    assertTrue(operation.getWaterActivity() > 0.0 && operation.getWaterActivity() < 1.0);
    assertTrue(fluid.hasPhaseType("gas") || fluid.hasPhaseType("oil"), "Saturated reference needs excess CO2");
    PhaseInterface aqueous = fluid.getPhase("aqueous");
    for (String cation : CATIONS) {
      assertEquals(aqueous.getComponent(cation).getNumberOfmoles(),
          aqueous.getComponent(cation).getNumberOfMolesInPhase(), 1.0e-6, cation);
    }
    assertEquals(aqueous.getComponent("Cl-").getNumberOfmoles(), aqueous.getComponent("Cl-").getNumberOfMolesInPhase(),
        1.0e-6);
    return operation;
  }

  @Test
  void referenceAssessmentRetainsAccuracyGapsAndUnsupportedPoints() throws Exception {
    List<String> report = new ArrayList<String>();
    report.add("source,brine,measured_K,pressure_bara,calculated_K,error_K,status,reported_phases,calculated_phases");
    int belowRange = 0;
    int precipitation = 0;
    int noBracket = 0;
    int withinCriterion = 0;
    int outsideCriterion = 0;
    int phaseMismatch = 0;
    int naclComparisons = 0;
    String resource = "/data/chemistry_benchmarks/pitzer_co2_hydrate_dissociation.csv";
    InputStream stream = getClass().getResourceAsStream(resource);
    assertNotNull(stream);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("#") || line.startsWith("source") || line.trim().isEmpty()) {
          continue;
        }
        String[] columns = line.split(",");
        assertEquals(11, columns.length);
        double measured = Double.parseDouble(columns[7]);
        double pressure = 10.0 * Double.parseDouble(columns[8]);
        String prefix = columns[0] + "," + columns[1] + "," + measured + "," + pressure + ",";
        if ("NaCl25".equals(columns[1])) {
          precipitation++;
          report.add(prefix + ",,SALT_PRECIPITATION_NOT_MODELED," + columns[10] + ",");
          continue;
        }
        if (measured < 274.19) {
          belowRange++;
          report.add(prefix + ",,BELOW_HENRY_RANGE," + columns[10] + ",");
          continue;
        }
        double[] massParts = new double[5];
        for (int i = 0; i < massParts.length; i++) {
          massParts[i] = Double.parseDouble(columns[i + 2]);
        }
        SystemPitzer fluid = brine(massParts, pressure);
        try {
          solve(fluid);
        } catch (IllegalStateException exception) {
          assertTrue(exception.getMessage().contains("No Pitzer hydrate equilibrium bracket"), exception.getMessage());
          assertTrue(exception.getMessage().contains("274.19-323.15 K"), "Report the effective Henry-limited range");
          noBracket++;
          report.add(prefix + ",,NO_BRACKET," + columns[10] + ",");
          continue;
        }
        double error = fluid.getTemperature() - measured;
        boolean within = Math.abs(error) <= TEMPERATURE_CRITERION_K;
        if (within) {
          withinCriterion++;
        } else {
          outsideCriterion++;
        }
        String phases = fluid.hasPhaseType("oil") ? "LLwH" : "VLwH";
        if (!phases.equals(columns[10])) {
          phaseMismatch++;
        }
        report.add(prefix + fluid.getTemperature() + "," + error + "," + (within ? "WITHIN_1_K" : "OUTSIDE_1_K") + ","
            + columns[10] + "," + phases);
        if ("Burgass2023".equals(columns[0])) {
          assertEquals(measured, fluid.getTemperature(), TEMPERATURE_CRITERION_K, prefix);
          naclComparisons++;
        }
      }
    }
    Files.write(Paths.get("target", "pitzer-hydrate-reference-assessment.csv"), report, StandardCharsets.UTF_8);
    // Accounting assertions protect against hiding failed comparisons or dropping unsupported reference rows.
    assertEquals(57, report.size() - 1);
    assertEquals(35, belowRange);
    assertEquals(5, precipitation);
    assertEquals(2, noBracket);
    assertEquals(6, naclComparisons);
    assertEquals(9, withinCriterion);
    assertEquals(6, outsideCriterion); // Known mixed-brine accuracy gaps, not passing experimental validation.
    assertEquals(1, phaseMismatch); // NaCl 5 wt%, 42.26 bara: SRK selects liquid CO2 at the computed temperature.
    logger.info("57 references: {} within 1 K, {} outside, {} no bracket, {} cold, {} precipitation; {} phase mismatch",
        withinCriterion, outsideCriterion, noBracket, belowRange, precipitation, phaseMismatch);
  }

  @Test
  void concentrationGridHasMonotonicInhibitionAndExplicitUnavailableRoots() throws Exception {
    List<String> report = new ArrayList<String>();
    report.add("salt,weight_percent,pressure_bara,temperature_K,water_activity,status");
    int converged = 0;
    int unavailable = 0;
    for (int salt = 0; salt < CATIONS.length; salt++) {
      for (double pressure : new double[] { 30.0, 100.0, 300.0 }) {
        double lastTemperature = Double.POSITIVE_INFINITY;
        double lastActivity = Double.POSITIVE_INFINITY;
        boolean previousUnavailable = false;
        for (double weightPercent : new double[] { 0.0, 1.0, 2.5, 5.0, 7.5, 10.0, 15.0, 20.0 }) {
          SystemPitzer fluid = singleSalt(salt, weightPercent, pressure);
          String prefix = CATIONS[salt] + "," + weightPercent + "," + pressure + ",";
          try {
            PitzerHydrateFlash operation = solve(fluid);
            assertFalse(previousUnavailable, "A colder root cannot return to the temperature range as salt increases");
            assertTrue(fluid.getTemperature() < lastTemperature);
            assertTrue(operation.getWaterActivity() < lastActivity);
            lastTemperature = fluid.getTemperature();
            lastActivity = operation.getWaterActivity();
            converged++;
            report.add(prefix + lastTemperature + "," + lastActivity + ",CONVERGED");
          } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("No Pitzer hydrate equilibrium bracket"),
                exception.getMessage());
            assertEquals(280.0, fluid.getTemperature(), 0.0);
            unavailable++;
            previousUnavailable = true;
            report.add(prefix + ",,NO_BRACKET");
          }
        }
      }
    }
    Files.write(Paths.get("target", "pitzer-hydrate-concentration-grid.csv"), report, StandardCharsets.UTF_8);
    assertEquals(80, converged);
    assertEquals(16, unavailable);
  }

  @Test
  void vanishingSaltRecoversTheSameNeutralParameterReference() {
    for (int salt = 0; salt < CATIONS.length; salt++) {
      SystemPitzer fresh = singleSalt(salt, 0.0, 100.0);
      SystemPitzer dilute = singleSalt(salt, 1.0e-6, 100.0);
      PitzerHydrateFlash freshResult = solve(fresh);
      PitzerHydrateFlash diluteResult = solve(dilute);
      assertEquals(fresh.getTemperature(), dilute.getTemperature(), 1.0e-5);
      assertEquals(freshResult.getWaterActivity(), diluteResult.getWaterActivity(), 1.0e-7);
    }
  }

  /** Ren et al. (2025), doi:10.1021/acs.energyfuels.5c02264, abstract and SI Table S2. */
  @Test
  void magnesiumInhibitionComparesWithIndependentConcentrationMeasurements() {
    SystemPitzer fresh = singleSalt(3, 0.0, 30.0);
    solve(fresh);
    // SI converts 200 and 400 mM to 1.90 and 3.81 wt%, respectively. Do not equate molarity and molality.
    double[][] references = { { 1.90, 0.96 }, { 3.81, 1.57 } };
    for (double[] reference : references) {
      SystemPitzer saline = singleSalt(3, reference[0], 30.0);
      solve(saline);
      double suppression = fresh.getTemperature() - saline.getTemperature();
      logger.info("Ren 2025 MgCl2 {} wt% at 30 bara: measured suppression={} K, calculated={} K", reference[0],
          reference[1], suppression);
      // Same 1 K engineering criterion as the other measurements, not an experimental uncertainty claim.
      assertEquals(reference[1], suppression, TEMPERATURE_CRITERION_K);
    }
  }

  @Test
  void potassiumMagnesiumThetaMustBeExplicitAndFiniteBeforeMutation() {
    SystemPitzer fluid = brine(new double[] { 90.0, 0.0, 5.0, 0.0, 5.0 }, 100.0);
    PhasePitzer aqueous = (PhasePitzer) fluid.getPhases()[1];
    fluid.applyPhreeqcCo2ChlorideParameters(explicitZeroZeta(), 0.035);
    String dataset = aqueous.getParameterDatasetId();
    assertEquals(PitzerParameterDatasets.PHREEQC_CO2_CHLORIDE_USER_THETA_ZETA_ID, dataset);
    // In this fixture K+ and Mg++ are indices 3 and 5. Theta is symmetric and constant with temperature.
    assertEquals(0.035, aqueous.getThetaij(3, 5, 280.0), 0.0);
    assertEquals(0.035, aqueous.getThetaij(5, 3, 300.0), 0.0);
    assertThrows(IllegalArgumentException.class, () -> fluid.applyPhreeqcCo2ChlorideParameters(explicitZeroZeta()));
    assertThrows(IllegalArgumentException.class,
        () -> fluid.applyPhreeqcCo2ChlorideParameters(explicitZeroZeta(), Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> fluid.applyPhreeqcCo2ChlorideParameters(explicitZeroZeta(), Double.POSITIVE_INFINITY));
    assertEquals(dataset, aqueous.getParameterDatasetId());
    assertEquals(0.035, aqueous.getThetaij(3, 5, 280.0), 0.0);
    solve(fluid);
  }

  @Test
  void magnesiumTemperaturePressureRoundTripPreservesInventory() throws Exception {
    SystemPitzer fluid = singleSalt(3, 7.5, 100.0);
    solve(fluid);
    double temperature = fluid.getTemperature();
    fluid.setPressure(50.0);
    new ThermodynamicOperations(fluid).hydrateFormationPressure();
    assertEquals(100.0, fluid.getPressure(), 0.002);
    assertEquals(temperature, fluid.getTemperature(), 0.0);
    double expectedMagnesium = 7.5 / 92.5 / SALT_MOLAR_MASS[3];
    assertEquals(expectedMagnesium, fluid.getPhase("aqueous").getComponent("Mg++").getNumberOfMolesInPhase(), 1.0e-6);
    assertEquals(2.0 * expectedMagnesium, fluid.getPhase("aqueous").getComponent("Cl-").getNumberOfMolesInPhase(),
        1.0e-6);
  }
}
