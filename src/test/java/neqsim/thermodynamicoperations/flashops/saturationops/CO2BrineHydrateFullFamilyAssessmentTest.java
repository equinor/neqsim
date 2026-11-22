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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * No-fit assessment of all 110 supported Dholabhai (1993) endpoints, not an accuracy qualification.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CO2BrineHydrateFullFamilyAssessmentTest {
  private static final Logger logger = LogManager.getLogger(CO2BrineHydrateFullFamilyAssessmentTest.class);
  private static final String FIXTURE = "/data/chemistry_benchmarks/co2_brine_hydrate_dholabhai1993_full.csv";
  private static final String HEADER = "point_id,recipe,family,source_table,nacl_wt_percent,kcl_wt_percent,"
      + "cacl2_wt_percent,measured_temperature_K,pressure_MPa";

  /**
   * Freeze the transcribed corpus, source-table membership, recipe coverage and original K10/K15 overlap.
   *
   * @throws Exception if a reference resource cannot be read
   */
  @Test
  void fixturePreservesAllSupportedRecipesAndOriginalKclPoints() throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream stream = getClass().getResourceAsStream(FIXTURE)) {
      assertNotNull(stream);
      byte[] buffer = new byte[4096];
      int length;
      while ((length = stream.read(buffer)) != -1) {
        digest.update(buffer, 0, length);
      }
    }
    StringBuilder hex = new StringBuilder();
    for (byte value : digest.digest()) {
      hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
    }
    assertEquals("07b4a213e10bc49ae3251dd6c1efd95410cf17894e4d69980dd6e6baafe17201", hex.toString());
    List<ReferencePoint> points = readReference();
    assertEquals(110, points.size());
    Set<String> identifiers = new HashSet<String>();
    Set<String> recipes = new HashSet<String>();
    Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    Map<String, ReferencePoint> overlap = new LinkedHashMap<String, ReferencePoint>();
    int tableThree = 0;
    for (ReferencePoint point : points) {
      assertTrue(identifiers.add(point.fields[0]));
      recipes.add(point.recipe);
      counts.put(point.family, counts.containsKey(point.family) ? counts.get(point.family) + 1 : 1);
      assertTrue(point.saltTotal >= 0.0 && point.saltTotal < 100.0);
      assertTrue(point.temperature > 0.0 && point.pressure > 0.0);
      assertTrue("III".equals(point.fields[3]) || "IV".equals(point.fields[3]));
      if ("III".equals(point.fields[3])) {
        tableThree++;
      }
      if ("K10".equals(point.recipe) || "K15".equals(point.recipe)) {
        overlap.put(point.recipe + ":" + point.pressure, point);
      }
    }
    assertEquals(28, recipes.size());
    assertEquals(54, tableThree);
    assertEquals(6, counts.size());
    String[] families = {"Pure-water", "NaCl", "KCl", "CaCl2", "NaCl-KCl", "NaCl-CaCl2"};
    int[] expected = {4, 25, 21, 21, 17, 22};
    for (int index = 0; index < families.length; index++) {
      assertEquals(expected[index], counts.get(families[index]).intValue());
    }
    assertEquals(8, overlap.size());
    String old = "/data/chemistry_benchmarks/co2_kcl_hydrate_dholabhai1993.csv";
    try (InputStream stream = getClass().getResourceAsStream(old)) {
      assertNotNull(stream);
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        reader.readLine();
        String line;
        int matched = 0;
        while ((line = reader.readLine()) != null) {
          String[] values = line.split(",");
          ReferencePoint point = overlap.get(values[0] + ":" + Double.parseDouble(values[2]));
          assertNotNull(point);
          assertEquals(Double.parseDouble(values[1]), point.kcl, 0.0);
          assertEquals(Double.parseDouble(values[3]), point.temperature, 0.0);
          assertEquals(values[4], point.fields[3]);
          matched++;
        }
        assertEquals(8, matched);
      }
    }
  }

  /**
   * Retain all numerical outcomes and per-family/per-recipe residuals before asserting numerical acceptance.
   *
   * @throws Exception if fixture or diagnostic output cannot be read or written
   */
  @Tag("slow")
  @Test
  void fullFamilyRetainsEveryEndpointWithoutAnAccuracyPassband() throws Exception {
    List<String> output = new ArrayList<String>();
    output.add(HEADER + ",pressure_bara,calculated_temperature_K,error_K,original_feed_fraction_residual,"
        + "component_balance_residual,charge_residual,molecular_log_fugacity_residual,hydrate_residual,"
        + "water_activity_proxy,aqueous_x_co2,guest_co2_fugacity_bara,aqueous_water_fugacity_bara,"
        + "hydrate_water_fugacity_bara,phase_fraction_normalization_residual,composition_normalization_residual,"
        + "maximum_nonaqueous_ion_fraction,fluid_phases,saturated_co2_boundary,numerical_checks_passed,status");
    Map<String, List<Result>> families = new LinkedHashMap<String, List<Result>>();
    Map<String, List<Result>> recipes = new LinkedHashMap<String, List<Result>>();
    int passed = 0;
    for (ReferencePoint point : readReference()) {
      Result result = calculate(point);
      output.add(result.csv);
      passed += result.accepted ? 1 : 0;
      addResult(families, point.family, result);
      addResult(recipes, point.recipe, result);
    }
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get("target/co2-brine-hydrate-dholabhai1993-full-assessment.csv"), output,
        StandardCharsets.UTF_8);
    logMetrics("family", families);
    logMetrics("recipe", recipes);
    assertEquals(111, output.size(), "Every reference point, including a failed calculation, must be retained");
    assertEquals(110, passed, "Numerical rejection is retained in the CSV; physical error has no pass band");
  }

  /**
   * Calculate an unchanged model from an independent cold start, preserving failed outcomes.
   *
   * @param point measured recipe and endpoint
   * @return retained numerical disposition and signed experimental residual
   */
  private Result calculate(ReferencePoint point) {
    String prefix = String.join(",", point.fields) + "," + 10.0 * point.pressure;
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(283.15, 10.0 * point.pressure);
    try {
      fluid.addComponent("CO2", 10.0);
      fluid.addComponent("water", 1.0 / 0.01801528);
      double scale = 1.0 / (100.0 - point.saltTotal);
      double sodium = point.nacl * scale / 0.05844277;
      double potassium = point.kcl * scale / 0.0745513;
      double calcium = point.cacl2 * scale / 0.11098;
      if (sodium > 0.0) {
        fluid.addComponent("Na+", sodium);
      }
      if (potassium > 0.0) {
        fluid.addComponent("K+", potassium);
      }
      if (calcium > 0.0) {
        fluid.addComponent("Ca++", calcium);
      }
      if (point.saltTotal > 0.0) {
        fluid.addComponent("Cl-", sodium + potassium + 2.0 * calcium);
      }
      fluid.setMixingRule(10);
      fluid.setHydrateCheck(true);
      fluid.setMultiPhaseCheck(true);
      double[] original = new double[fluid.getNumberOfComponents()];
      double totalInput = fluid.getTotalNumberOfMoles();
      for (int component = 0; component < original.length; component++) {
        original[component] = fluid.getPhase(0).getComponent(component).getNumberOfmoles();
      }
      ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
      operations.hydrateFormationTemperature(278.15);
      HydrateEquilibriumDiagnostics diagnostics = ((HydrateFormationTemperatureFlash) operations.getOperation())
          .getDiagnostics();
      double originalResidual = 0.0;
      double betaSum = 0.0;
      double compositionResidual = 0.0;
      double ionLeak = 0.0;
      List<String> phaseNames = new ArrayList<String>();
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        phaseNames.add(fluid.getPhase(phase).getType().toString());
        betaSum += fluid.getBeta(phase);
        double compositionSum = 0.0;
        for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
          double x = fluid.getPhase(phase).getComponent(component).getx();
          compositionSum += x;
          if (fluid.getPhase(phase).getType() != PhaseType.AQUEOUS
              && fluid.getPhase(phase).getComponent(component).getIonicCharge() != 0.0) {
            ionLeak = Math.max(ionLeak, x);
          }
        }
        compositionResidual = Math.max(compositionResidual, Math.abs(compositionSum - 1.0));
      }
      for (int component = 0; component < original.length; component++) {
        double recovered = 0.0;
        for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
          recovered += fluid.getTotalNumberOfMoles() * fluid.getBeta(phase)
              * fluid.getPhase(phase).getComponent(component).getx();
        }
        originalResidual = Math.max(originalResidual, Math.abs(recovered - original[component]) / totalInput);
      }
      PhaseInterface aqueous = fluid.getPhase("aqueous");
      int water = aqueous.getComponent("water").getComponentNumber();
      double activity = aqueous.getComponent(water).getx() * aqueous.getActivityCoefficient(water);
      double temperature = fluid.getTemperature();
      double error = temperature - point.temperature;
      double[] numbers = {temperature, error, originalResidual, diagnostics.getComponentBalanceResidual(),
          diagnostics.getChargeResidual(), diagnostics.getFluidFugacityResidual(), diagnostics.getHydrateResidual(),
          activity, aqueous.getComponent("CO2").getx(), fluid.getPhase("gas").getFugacity("CO2"),
          aqueous.getFugacity("water"), fluid.getPhase(4).getFugacity("water"), Math.abs(betaSum - 1.0),
          compositionResidual, ionLeak};
      String status = "numerically_accepted_not_physically_qualified";
      boolean accepted = true;
      try {
        CO2BrineHydratePhaseStateTest.assertEndpoint(fluid, true);
        for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
          double beta = fluid.getBeta(phase);
          assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0);
        }
        assertTrue(fluid.hasPhaseType(PhaseType.GAS));
        assertTrue(diagnostics.isConverged() && diagnostics.isSaturatedCO2Boundary());
        assertTrue(originalResidual <= 1.0e-10);
        for (double value : numbers) {
          assertTrue(Double.isFinite(value));
        }
      } catch (AssertionError rejection) {
        accepted = false;
        status = "numerical_rejection:"
            + String.valueOf(rejection.getMessage()).replace(',', ';').replace('\n', ' ').replace('\r', ' ');
      }
      StringBuilder row = new StringBuilder(prefix);
      for (double value : numbers) {
        row.append(',').append(value);
      }
      row.append(',').append(String.join("+", phaseNames)).append(',').append(diagnostics.isSaturatedCO2Boundary())
          .append(',').append(accepted).append(',').append(status);
      return new Result(row.toString(), error, accepted);
    } catch (RuntimeException failure) {
      logger.warn("Dholabhai point {} failed: {}", point.fields[0], failure.toString());
      return failedResult(point, fluid, failure);
    }
  }

  /**
   * Keep a thrown calculation's returned temperature visible without treating it as an accepted endpoint.
   *
   * @param point original factual row
   * @param fluid caller state after the failure
   * @param failure numerical failure
   * @return full-width failed row with raw temperature and unqualified error
   */
  private Result failedResult(ReferencePoint point, SystemInterface fluid, RuntimeException failure) {
    double rawTemperature = fluid.getTemperature();
    StringBuilder row = new StringBuilder(String.join(",", point.fields));
    row.append(',').append(10.0 * point.pressure).append(',').append(rawTemperature).append(',')
        .append(rawTemperature - point.temperature);
    for (int index = 0; index < 13; index++) {
      row.append(",NaN");
    }
    row.append(",UNKNOWN,false,false,").append(failure.getClass().getSimpleName());
    return new Result(row.toString(), Double.NaN, false);
  }

  /** Check that a failed solve retains raw values but cannot enter physical residual metrics. */
  @Test
  void failedCalculationKeepsRawTemperatureAndFullWidthRecord() {
    ReferencePoint point = new ReferencePoint(
        new String[] {"K10-04", "K10", "KCl", "III", "0.00", "10.02", "0.00", "269.02", "1.130"});
    Result failed = failedResult(point, new SystemElectrolyteCPAstatoil(270.0, 11.3),
        new IllegalStateException("test failure"));
    String[] columns = failed.csv.split(",");
    assertEquals(29, columns.length);
    assertEquals(270.0, Double.parseDouble(columns[10]), 0.0);
    assertTrue(!failed.accepted && Double.isNaN(failed.error));
    assertEquals("false", columns[27]);
    assertEquals("IllegalStateException", columns[28]);
  }

  /**
   * Add one result without pooling recipes or removing numerical failures.
   *
   * @param groups ordered grouping
   * @param key recipe or family label
   * @param result retained outcome
   */
  private void addResult(Map<String, List<Result>> groups, String key, Result result) {
    if (!groups.containsKey(key)) {
      groups.put(key, new ArrayList<Result>());
    }
    groups.get(key).add(result);
  }

  /**
   * Log recomputable family/recipe statistics, explicitly counting rejected states.
   *
   * @param kind grouping identity
   * @param groups ordered results
   */
  private void logMetrics(String kind, Map<String, List<Result>> groups) {
    for (Map.Entry<String, List<Result>> group : groups.entrySet()) {
      int compared = 0;
      double sum = 0.0;
      double absolute = 0.0;
      double squared = 0.0;
      double maximum = 0.0;
      for (Result result : group.getValue()) {
        if (result.accepted) {
          compared++;
          sum += result.error;
          absolute += Math.abs(result.error);
          squared += result.error * result.error;
          maximum = Math.max(maximum, Math.abs(result.error));
        }
      }
      logger.info("Dholabhai {}={} measured={} compared={} bias_K={} MAE_K={} RMSE_K={} max_abs_K={}", kind,
          group.getKey(), group.getValue().size(), compared, sum / compared, absolute / compared,
          Math.sqrt(squared / compared), maximum);
    }
  }

  /**
   * Read factual values in original K/MPa and wet-mass-percent conventions.
   *
   * @return all supported reference states in source order
   * @throws Exception if the fixture is missing or malformed
   */
  private List<ReferencePoint> readReference() throws Exception {
    List<ReferencePoint> points = new ArrayList<ReferencePoint>();
    try (InputStream stream = getClass().getResourceAsStream(FIXTURE)) {
      assertNotNull(stream);
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        assertEquals(HEADER, reader.readLine());
        String line;
        while ((line = reader.readLine()) != null) {
          String[] fields = line.split(",");
          assertEquals(9, fields.length);
          points.add(new ReferencePoint(fields));
        }
      }
    }
    return points;
  }

  /** One factual recipe/end-point row; no fitted coefficient. */
  private static final class ReferencePoint {
    private final String[] fields;
    private final String recipe;
    private final String family;
    private final double nacl;
    private final double kcl;
    private final double cacl2;
    private final double saltTotal;
    private final double temperature;
    private final double pressure;

    /**
     * Parse source units without rounding recipe concentrations to their nominal labels.
     *
     * @param fields nine source columns
     */
    private ReferencePoint(String[] fields) {
      this.fields = fields;
      recipe = fields[1];
      family = fields[2];
      nacl = Double.parseDouble(fields[4]);
      kcl = Double.parseDouble(fields[5]);
      cacl2 = Double.parseDouble(fields[6]);
      saltTotal = nacl + kcl + cacl2;
      temperature = Double.parseDouble(fields[7]);
      pressure = Double.parseDouble(fields[8]);
    }
  }

  /** Retained numerical disposition, distinct from physical reference accuracy. */
  private static final class Result {
    private final String csv;
    private final double error;
    private final boolean accepted;

    /**
     * Capture a row without changing its numerical or experimental outcome.
     *
     * @param csv complete diagnostic row
     * @param error calculated minus measured temperature in K
     * @param accepted whether the original numerical gates passed
     */
    private Result(String csv, double error, boolean accepted) {
      this.csv = csv;
      this.error = error;
      this.accepted = accepted;
    }
  }
}
