package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentHydrate;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.flashops.CO2BrinePhaseEquilibrium;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Self-contained measured-state hydrate partition; numerical diagnostics are not accuracy qualification.
 *
 * @author NeqSim campaign
 * @version 1.0
 */
class CO2BrineHydrateMeasuredStateAssessmentTest {
  private static final Logger logger = LogManager.getLogger(CO2BrineHydrateMeasuredStateAssessmentTest.class);
  private static final double WATER_MOLAR_MASS = 0.01801528;

  private static final String FIXTURE = "/data/chemistry_benchmarks/co2_brine_hydrate_dholabhai1993_full.csv";
  private static final String OUTPUT = "target/co2-brine-hydrate-measured-state-assessment.csv";

  /**
   * Write every pair before asserting numerical acceptance, without depending on another producer.
   *
   * @throws Exception if source records or output cannot be read or written
   */
  @Tag("slow")
  @Test
  void measuredStatePartitionRetainsEveryRecipeAndChecksItsOwnRoots() throws Exception {
    List<String> fixture = readFixture();
    List<String> output = new ArrayList<String>();
    output.add("id,recipe,family,T_K,P_bara,R_brine,R_water,aqueous_term,guest_hydrate_term,identity_error,"
        + "xco2_brine,xco2_water,guest_brine,guest_water,faq_brine,faq_water,fhyd_brine,fhyd_water,"
        + "structure_brine,structure_water,original_feed,reconstructed,charge,molecular,beta_norm,"
        + "composition_norm,ion_leak,trial_brine,trial_water,phases_brine,phases_water,native_root_temperature_K,root_residual,"
        + "repeat_max,accepted,status");
    int failed = 0;
    for (String record : fixture.subList(1, fixture.size())) {
      String[] fields = record.split(",");
      String prefix = fields[0] + "," + fields[1] + "," + fields[2];
      String row;
      double rawRootTemperature = Double.NaN;
      try {
        double temperature = Double.parseDouble(fields[7]);
        double pressure = 10.0 * Double.parseDouble(fields[8]);
        State brine = calculate(fields, temperature, pressure, false);
        State water = calculate(fields, temperature, pressure, true);
        State repeat = calculate(fields, temperature, pressure, false);
        rawRootTemperature = nativeRootTemperature(fields, pressure);
        State root = calculate(fields, rawRootTemperature, pressure, false);
        double[] terms = partition(brine.aqueousWater, brine.hydrateWater, water.aqueousWater, water.hydrateWater);
        double rb = terms[0];
        double r0 = terms[1];
        double aqueous = terms[2];
        double guest = terms[3];
        double closure = terms[4];
        double rootResidual = 1.0 - root.hydrateWater / root.aqueousWater;
        double repeatMaximum = Math.max(Math.abs(brine.aqueousWater - repeat.aqueousWater),
            Math.max(Math.abs(brine.hydrateWater - repeat.hydrateWater),
                Math.max(Math.abs(brine.xco2 - repeat.xco2), Math.abs(brine.guest - repeat.guest))));
        boolean accepted = brine.accepted && water.accepted && repeat.accepted && root.accepted
            && Math.abs(closure) <= 1.0e-12 && Math.abs(rootResidual) <= 1.0e-6 && repeatMaximum <= 1.0e-12;
        row = prefix + "," + temperature + "," + pressure + "," + rb + "," + r0 + "," + aqueous + "," + guest + ","
            + closure + "," + brine.xco2 + "," + water.xco2 + "," + brine.guest + "," + water.guest + ","
            + brine.aqueousWater + "," + water.aqueousWater + "," + brine.hydrateWater + "," + water.hydrateWater + ","
            + brine.structure + "," + water.structure;
        for (int i = 0; i < brine.metrics.length; i++) {
          row += ","
              + Math.max(Math.max(brine.metrics[i], water.metrics[i]), Math.max(repeat.metrics[i], root.metrics[i]));
        }
        row += "," + brine.trial + "," + water.trial + "," + brine.phases + "," + water.phases + ","
            + rawRootTemperature + "," + rootResidual + "," + repeatMaximum + "," + accepted
            + ",diagnostic_not_qualified";
        if (!accepted) {
          failed++;
        }
      } catch (Exception ex) {
        failed++;
        row = failedRow(fields, rawRootTemperature, ex);
        logger.error("Retained failed measured-state record {}", fields[0], ex);
      }
      output.add(row);
      logger.info("MEASURED_STATE,{}", row);
    }
    Path destination = Paths.get(OUTPUT);
    Files.write(destination, output, StandardCharsets.UTF_8);
    logger.info("PARTITION_COMPLETE rows={} failures={}", output.size() - 1, failed);
    assertEquals(111, output.size(), "Every measured recipe must be retained");
    assertEquals(0, failed, "All failed states remain in the CSV; no physical accuracy pass band is asserted");
  }

  /**
   * Freeze the already source-audited fixture rather than adding or silently changing measurements.
   *
   * @throws Exception if the shared reference cannot be read
   */
  @Test
  void sharedFixtureHasTheFrozenHashAndCompleteRecipeCoverage() throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream stream = getClass().getResourceAsStream(FIXTURE)) {
      if (stream == null) {
        throw new IllegalStateException("Missing source fixture");
      }
      byte[] buffer = new byte[4096];
      int length;
      while ((length = stream.read(buffer)) != -1) {
        digest.update(buffer, 0, length);
      }
    }
    StringBuilder hex = new StringBuilder();
    for (byte value : digest.digest()) {
      hex.append(String.format("%02x", value & 0xff));
    }
    assertEquals("07b4a213e10bc49ae3251dd6c1efd95410cf17894e4d69980dd6e6baafe17201", hex.toString());
    List<String> fixture = readFixture();
    Set<String> ids = new HashSet<String>();
    Set<String> recipes = new HashSet<String>();
    Set<String> families = new HashSet<String>();
    for (String line : fixture.subList(1, fixture.size())) {
      String[] fields = line.split(",");
      assertEquals(9, fields.length);
      ids.add(fields[0]);
      recipes.add(fields[1]);
      families.add(fields[2]);
    }
    assertEquals(111, fixture.size());
    assertEquals(110, ids.size());
    assertEquals(28, recipes.size());
    assertEquals(6, families.size());
  }

  /** Check partition signs, pure-water zero contrast, identity closure and fail-closed inputs. */
  @Test
  void partitionHasZeroPureWaterContrastAndClosesAlgebraically() {
    double[] pure = partition(0.005, 0.004, 0.005, 0.004);
    assertEquals(0.0, pure[2], 0.0);
    assertEquals(0.0, pure[3], 0.0);
    assertEquals(0.0, pure[4], 0.0);
    double[] mixed = partition(0.004, 0.005, 0.008, 0.007);
    assertTrue(mixed[2] < 0.0);
    assertTrue(mixed[3] > 0.0);
    assertEquals(0.0, mixed[4], 1.0e-15);
    assertThrows(IllegalArgumentException.class, () -> partition(0.0, 0.005, 0.008, 0.007));
    assertThrows(IllegalArgumentException.class, () -> partition(0.004, Double.NaN, 0.008, 0.007));
  }

  /**
   * Failed calculations retain known input T/P, raw root T and the full unqualified record.
   *
   * @throws Exception if the shared fixture cannot be read
   */
  @Test
  void failureRecordRetainsKnownInputAndRawRootTemperature() throws Exception {
    String[] fields = null;
    for (String line : readFixture()) {
      if (line.startsWith("Na15K5-04,")) {
        fields = line.split(",");
      }
    }
    if (fields == null) {
      throw new IllegalStateException("Missing measured-state failure control");
    }
    String[] record = failedRow(fields, 267.53313919751, new IllegalStateException("deliberate")).split(",");
    assertEquals(36, record.length);
    assertEquals("269.82", record[3]);
    assertEquals("30.5", record[4]);
    assertEquals("NaN", record[5]);
    assertEquals(267.53313919751, Double.parseDouble(record[31]), 0.0);
    assertEquals("false", record[34]);
    assertEquals("error_IllegalStateException", record[35]);
  }

  /**
   * Unit hydrate-water composition is required even when a corrupted reference gives finite numbers.
   *
   * @throws Exception if the reference fixture is unavailable
   */
  @Test
  void nativeRootCheckRejectsAFeedNormalizedHydrateWaterReference() throws Exception {
    String[] source = readFixture().get(1).split(",");
    double pressure = 10.0 * Double.parseDouble(source[8]);
    double temperature = nativeRootTemperature(source, pressure);
    SystemInterface fluid = createFluid(source, temperature, pressure, false);
    new CO2BrinePhaseEquilibrium(fluid).run();
    new HydrateFormationTemperatureFlash(fluid).setFug();
    PhaseInterface hydrate = fluid.getPhase(4);
    double feedWaterFraction = fluid.getPhase(0).getComponent("water").getz();
    hydrate.getComponent("water").setx(feedWaterFraction);
    double aqueousFugacity = fluid.getPhase("aqueous").getFugacity("water");
    assertTrue(Math.abs(1.0 - hydrate.getFugacity("water") / aqueousFugacity) > 1.0e-6);
    hydrate.getComponent("water").setx(1.0);
    assertTrue(Math.abs(1.0 - hydrate.getFugacity("water") / aqueousFugacity) <= 1.0e-6);
  }

  /**
   * Read the unchanged source fixture through the classpath, independent of checkout location.
   *
   * @return header and every original measured record
   * @throws Exception if the reference resource cannot be read
   */
  private static List<String> readFixture() throws Exception {
    List<String> records = new ArrayList<String>();
    try (InputStream stream = CO2BrineHydrateMeasuredStateAssessmentTest.class.getResourceAsStream(FIXTURE)) {
      if (stream == null) {
        throw new IllegalStateException("Missing source fixture");
      }
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          records.add(line);
        }
      }
    }
    return records;
  }

  /**
   * Preserve all columns and known measured/root temperatures after an exception.
   *
   * @param source original fixture fields
   * @param rawRootTemperature computed native root, or NaN if it was unavailable
   * @param failure calculation failure
   * @return full-width rejected CSV row
   */
  private static String failedRow(String[] source, double rawRootTemperature, Exception failure) {
    String[] fields = new String[36];
    Arrays.fill(fields, "NaN");
    fields[0] = source[0];
    fields[1] = source[1];
    fields[2] = source[2];
    fields[3] = source[7];
    fields[4] = Double.toString(10.0 * Double.parseDouble(source[8]));
    fields[31] = Double.toString(rawRootTemperature);
    fields[34] = "false";
    fields[35] = "error_" + failure.getClass().getSimpleName();
    return String.join(",", fields);
  }

  /**
   * Split the exact matched-state fugacity ratio without fitting or changing a model.
   *
   * @param aqueousBrine brine aqueous-water fugacity in bara
   * @param hydrateBrine brine guest-driven hydrate-water fugacity in bara
   * @param aqueousWater CO2-bearing salt-free aqueous-water fugacity in bara
   * @param hydrateWater salt-free guest-driven hydrate-water fugacity in bara
   * @return R brine, R water, aqueous contrast, guest contrast and identity residual
   */
  private static double[] partition(double aqueousBrine, double hydrateBrine, double aqueousWater,
      double hydrateWater) {
    for (double value : new double[] {aqueousBrine, hydrateBrine, aqueousWater, hydrateWater}) {
      if (!Double.isFinite(value) || value <= 0.0) {
        throw new IllegalArgumentException("Partition requires positive finite fugacities");
      }
    }
    double rb = Math.log(aqueousBrine / hydrateBrine);
    double rw = Math.log(aqueousWater / hydrateWater);
    double aqueous = Math.log(aqueousBrine / aqueousWater);
    double guest = -Math.log(hydrateBrine / hydrateWater);
    return new double[] {rb, rw, aqueous, guest, rb - rw - aqueous - guest};
  }

  /**
   * Compute each native root cold without reading another assessment's output.
   *
   * @param source original measured recipe
   * @param pressure absolute bara pressure
   * @return native calculated hydrate temperature in kelvin
   */
  private static double nativeRootTemperature(String[] source, double pressure) {
    SystemInterface fluid = createFluid(source, 283.15, pressure, false);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.hydrateFormationTemperature(278.15);
    HydrateEquilibriumDiagnostics diagnostic = ((HydrateFormationTemperatureFlash) operations.getOperation())
        .getDiagnostics();
    if (!diagnostic.isConverged() || !diagnostic.isSaturatedCO2Boundary()) {
      throw new IllegalStateException("Native root is not a converged saturated boundary");
    }
    return fluid.getTemperature();
  }

  /**
   * Evaluates the native constrained fluid and GF hydrate fugacity at a fixed state.
   *
   * @param source original measured recipe
   * @param temperature fixed kelvin temperature
   * @param pressure fixed absolute bara pressure
   * @param saltFree whether to remove all salts, retaining water and CO2 inventory
   * @return raw state with independent numerical checks, not hydrate equilibrium acceptance
   */
  private static State calculate(String[] source, double temperature, double pressure, boolean saltFree) {
    SystemInterface fluid = createFluid(source, temperature, pressure, saltFree);
    int count = fluid.getNumberOfComponents();
    double total = fluid.getTotalNumberOfMoles();
    double[] input = new double[count];
    for (int i = 0; i < count; i++) {
      input[i] = fluid.getPhase(0).getComponent(i).getNumberOfmoles() / total;
    }
    CO2BrinePhaseEquilibrium operation = new CO2BrinePhaseEquilibrium(fluid);
    operation.run();
    new HydrateFormationTemperatureFlash(fluid).setFug();
    // Match updateFluidAndHydrate: phase init inside setFug restores feed x, not unit hydrate water x.
    fluid.getPhase(4).getComponent("water").setx(1.0);
    State result = new State();
    result.trial = operation.getMinimumTrialDistance();
    PhaseInterface aqueous = fluid.getPhase("aqueous");
    PhaseInterface gas = fluid.getPhase("gas");
    result.aqueousWater = aqueous.getFugacity("water");
    result.hydrateWater = fluid.getPhase(4).getFugacity("water");
    result.guest = gas.getFugacity("CO2");
    result.xco2 = aqueous.getComponent("CO2").getx();
    result.structure = ((ComponentHydrate) fluid.getPhase(4).getComponent("water")).getHydrateStructure();
    double betaSum = 0.0;
    result.accepted = fluid.getNumberOfPhases() == 2 && Double.isFinite(result.trial);
    double totalRatio = fluid.getTotalNumberOfMoles() / total;
    result.metrics[0] = Math.abs(totalRatio - 1.0);
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      PhaseInterface phase = fluid.getPhase(p);
      result.phases += (p == 0 ? "" : "+") + phase.getType().name();
      double beta = fluid.getBeta(p);
      result.accepted &= Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0;
      betaSum += beta;
      double sum = 0.0;
      double charge = 0.0;
      for (int i = 0; i < count; i++) {
        double x = phase.getComponent(i).getx();
        result.accepted &= Double.isFinite(x) && x >= 0.0 && x <= 1.0;
        sum += x;
        charge += x * phase.getComponent(i).getIonicCharge();
        if (phase.getType() != PhaseType.AQUEOUS && phase.getComponent(i).getIonicCharge() != 0.0) {
          result.metrics[6] = Math.max(result.metrics[6], x);
        }
      }
      result.metrics[2] = Math.max(result.metrics[2], Math.abs(charge));
      result.metrics[5] = Math.max(result.metrics[5], Math.abs(sum - 1.0));
    }
    for (int i = 0; i < count; i++) {
      double recovered = 0.0;
      for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
        recovered += fluid.getBeta(p) * fluid.getPhase(p).getComponent(i).getx();
      }
      result.metrics[0] = Math.max(result.metrics[0], Math.abs(totalRatio * recovered - input[i]));
      result.metrics[0] = Math.max(result.metrics[0], Math.abs(fluid.getPhase(0).getComponent(i).getz() - input[i]));
      result.metrics[1] = Math.max(result.metrics[1], Math.abs(recovered - fluid.getPhase(0).getComponent(i).getz()));
    }
    for (String species : new String[] {"CO2", "water"}) {
      result.metrics[3] = Math.max(result.metrics[3],
          Math.abs(Math.log(aqueous.getFugacity(species) / gas.getFugacity(species))));
    }
    result.metrics[4] = Math.abs(betaSum - 1.0);
    double[] limits = {1.0e-10, 1.0e-10, 1.0e-10, 1.0e-8, 1.0e-12, 1.0e-9, 1.0e-40};
    for (int i = 0; i < limits.length; i++) {
      result.accepted &= Double.isFinite(result.metrics[i]) && result.metrics[i] <= limits[i];
    }
    for (double value : new double[] {result.aqueousWater, result.hydrateWater, result.guest, result.xco2}) {
      result.accepted &= Double.isFinite(value) && value > 0.0;
    }
    return result;
  }

  /**
   * Construct an independent cold feed in the original kg-water and wet-basis salt convention.
   *
   * @param source original measured recipe
   * @param temperature initial kelvin temperature
   * @param pressure absolute bara pressure
   * @param saltFree whether to omit salts while retaining the CO2/water inventory
   * @return newly constructed nonreactive electrolyte-CPA fluid
   */
  private static SystemInterface createFluid(String[] source, double temperature, double pressure, boolean saltFree) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(temperature, pressure);
    fluid.addComponent("CO2", 10.0);
    fluid.addComponent("water", 1.0 / WATER_MOLAR_MASS);
    double na = saltFree ? 0.0 : Double.parseDouble(source[4]);
    double k = saltFree ? 0.0 : Double.parseDouble(source[5]);
    double ca = saltFree ? 0.0 : Double.parseDouble(source[6]);
    double denominator = 100.0 - na - k - ca;
    double nNa = na / denominator / 0.05844277;
    double nK = k / denominator / 0.0745513;
    double nCa = ca / denominator / 0.11098;
    if (nNa > 0.0) {
      fluid.addComponent("Na+", nNa);
    }
    if (nK > 0.0) {
      fluid.addComponent("K+", nK);
    }
    if (nCa > 0.0) {
      fluid.addComponent("Ca++", nCa);
    }
    if (nNa + nK + nCa > 0.0) {
      fluid.addComponent("Cl-", nNa + nK + 2.0 * nCa);
    }
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.setHydrateCheck(true);
    return fluid;
  }

  /**
   * Raw state values; every metric remains visible even when acceptance fails.
   *
   * @author NeqSim campaign
   * @version 1.0
   */
  private static final class State {
    private double aqueousWater;
    private double hydrateWater;
    private double guest;
    private double xco2;
    private double trial;
    private int structure;
    private String phases = "";
    private boolean accepted;
    private final double[] metrics = new double[7];
  }
}
