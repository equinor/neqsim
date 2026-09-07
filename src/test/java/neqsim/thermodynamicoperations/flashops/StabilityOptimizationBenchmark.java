package neqsim.thermodynamicoperations.flashops;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Full-state A/B qualification matrix for stability-kernel optimizations.
 *
 * <p>
 * Run {@code main output.json [repeats=1] [case-name-filter=all]} on identical baseline and candidate JVMs. Twelve
 * fluid cases, ordinary/multiphase settings, and four state transitions produce 96 records per repeat. Timings measure
 * complete TPflash calls, excluding construction, property initialization and validation. Repeat counts do not change
 * the number of distinct states. No timing assertion belongs in CI.
 *
 * <p>
 * Cases are synthetic numerical regressions, not experimental validation of an EOS. Strict closure failures and
 * exceptions are recorded without relaxing tolerances or stopping the matrix. An A/B comparison must retain baseline
 * failures visibly and reject any new failure, phase change or material state discrepancy. Fugacity comparisons exclude
 * constrained ions and component phase fractions at or below 1e-20; excluded comparisons are counted explicitly.
 */
public final class StabilityOptimizationBenchmark {
  private static final Logger logger = LogManager.getLogger(StabilityOptimizationBenchmark.class);
  private static final String[] RICH_NAMES = { "nitrogen", "CO2", "methane", "ethane", "propane", "i-butane",
      "n-butane", "i-pentane", "n-pentane", "n-hexane" };
  private static final double[] RICH_MOLES = { 3.43, 0.34, 62.51, 15.65, 13.22, 1.61, 2.48, 0.35, 0.29, 0.12 };

  static final class FluidCase {
    final String name;
    final String eos;
    final double temperature;
    final double pressure;
    final String[] components;
    final double[] moles;

    FluidCase(String name, String eos, double temperature, double pressure, String[] components, double[] moles) {
      this.name = name;
      this.eos = eos;
      this.temperature = temperature;
      this.pressure = pressure;
      this.components = components;
      this.moles = moles;
    }

    SystemInterface create(boolean multiphase, boolean changed) {
      double temperatureK = temperature + (changed ? 0.25 : 0.0);
      double pressureBara = pressure * (changed ? 0.995 : 1.0);
      SystemInterface system;
      if ("PR".equals(eos)) {
        system = new SystemPrEos(temperatureK, pressureBara);
      } else if ("CPA".equals(eos)) {
        system = new SystemSrkCPAstatoil(temperatureK, pressureBara);
      } else if ("electrolyte-CPA".equals(eos)) {
        system = new SystemElectrolyteCPAstatoil(temperatureK, pressureBara);
      } else {
        system = new SystemSrkEos(temperatureK, pressureBara);
      }
      for (int i = 0; i < components.length; i++) {
        system.addComponent(components[i], moles[i]);
      }
      system.setMixingRule(eos.contains("CPA") ? 10 : 2);
      system.setMultiPhaseCheck(multiphase);
      return system;
    }
  }

  static List<FluidCase> cases() {
    return Arrays.asList(
        new FluidCase("srk-dry-gas", "SRK", 323.15, 20.0, new String[] { "methane", "ethane", "nitrogen" },
            new double[] { 0.95, 0.04, 0.01 }),
        new FluidCase("srk-rich-gas", "SRK", 273.15, 60.0, RICH_NAMES, RICH_MOLES),
        new FluidCase("pr-rich-gas", "PR", 273.15, 60.0, RICH_NAMES, RICH_MOLES),
        new FluidCase("pr-near-cricondenbar", "PR", 273.15, 100.0, RICH_NAMES, RICH_MOLES),
        new FluidCase("srk-trace-water", "SRK", 298.15, 60.0,
            new String[] { "methane", "ethane", "n-heptane", "water" }, new double[] { 0.85, 0.10, 0.05, 1.0e-12 }),
        new FluidCase("srk-gas-water", "SRK", 273.15, 300.0, new String[] { "methane", "water" },
            new double[] { 0.85, 0.15 }),
        new FluidCase("cpa-gas-water", "CPA", 298.15, 60.0, new String[] { "methane", "water" },
            new double[] { 0.90, 0.10 }),
        new FluidCase("cpa-three-phase", "CPA", 298.15, 60.0,
            new String[] { "nitrogen", "methane", "ethane", "propane", "n-hexane", "nC10", "MEG", "water" },
            new double[] { 1.0, 85.0, 5.0, 3.0, 1.0, 1.0, 2.0, 5.0 }),
        new FluidCase("srk-co2-rich", "SRK", 298.15, 70.0, new String[] { "CO2", "methane", "nitrogen" },
            new double[] { 0.96, 0.03, 0.01 }),
        new FluidCase("pr-co2-rich", "PR", 298.15, 70.0, new String[] { "CO2", "methane", "nitrogen" },
            new double[] { 0.96, 0.03, 0.01 }),
        new FluidCase("pr-hydrogen-rich", "PR", 148.15, 20.265, new String[] { "hydrogen", "ethane" },
            new double[] { 0.60, 0.40 }),
        new FluidCase("electrolyte-gas-brine", "electrolyte-CPA", 298.15, 10.01325,
            new String[] { "methane", "water", "Na+", "Cl-" }, new double[] { 0.1, 1.0, 0.001, 0.001 }));
  }

  private static void number(JsonObject object, String name, double value) {
    if (Double.isFinite(value)) {
      object.addProperty(name, value);
    } else {
      object.add(name, null);
    }
  }

  private static void number(JsonArray array, double value) {
    if (Double.isFinite(value)) {
      array.add(value);
    } else {
      array.add((Number) null);
    }
  }

  static JsonObject snapshot(SystemInterface system, double originalMoles, double originalMass) {
    system.init(3);
    system.initPhysicalProperties(neqsim.physicalproperties.PhysicalPropertyType.MASS_DENSITY);
    JsonObject state = new JsonObject();
    JsonArray components = new JsonArray();
    JsonArray phases = new JsonArray();
    double betaSum = 0.0;
    double normalizationError = 0.0;
    double materialError = 0.0;
    double fugacityError = 0.0;
    int comparisons = 0;
    int excluded = 0;
    boolean finite = true;
    for (int p = 0; p < system.getNumberOfPhases(); p++) {
      PhaseInterface phase = system.getPhase(p);
      double beta = system.getBeta(p);
      finite &= Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0 && Double.isFinite(phase.getZ())
          && phase.getZ() > 0.0 && Double.isFinite(phase.getDensity()) && phase.getDensity() > 0.0;
      betaSum += beta;
      double xSum = 0.0;
      JsonObject phaseJson = new JsonObject();
      JsonArray composition = new JsonArray();
      JsonArray coefficients = new JsonArray();
      for (int c = 0; c < phase.getNumberOfComponents(); c++) {
        double x = phase.getComponent(c).getx();
        finite &= Double.isFinite(x) && x >= 0.0 && x <= 1.0;
        xSum += x;
        number(composition, x);
        number(coefficients, phase.getComponent(c).getFugacityCoefficient());
      }
      normalizationError = Math.max(normalizationError, Math.abs(xSum - 1.0));
      phaseJson.addProperty("type", phase.getType().toString());
      number(phaseJson, "beta", beta);
      number(phaseJson, "compressibility", phase.getZ());
      number(phaseJson, "densityKgM3", phase.getDensity());
      phaseJson.add("x", composition);
      phaseJson.add("fugacityCoefficients", coefficients);
      phases.add(phaseJson);
    }
    for (int c = 0; c < system.getNumberOfComponents(); c++) {
      ComponentInterface component = system.getPhase(0).getComponent(c);
      JsonObject componentJson = new JsonObject();
      componentJson.addProperty("name", component.getComponentName());
      number(componentJson, "z", component.getz());
      number(componentJson, "moles", component.getNumberOfmoles());
      components.add(componentJson);
      double recovered = 0.0;
      for (int p = 0; p < system.getNumberOfPhases(); p++) {
        recovered += system.getBeta(p) * system.getPhase(p).getComponent(c).getx();
        for (int q = p + 1; q < system.getNumberOfPhases(); q++) {
          ComponentInterface first = system.getPhase(p).getComponent(c);
          ComponentInterface second = system.getPhase(q).getComponent(c);
          if (component.getIonicCharge() != 0 || component.isIsIon() || first.getx() <= 1.0e-20
              || second.getx() <= 1.0e-20) {
            excluded++;
            continue;
          }
          double firstPhi = first.getFugacityCoefficient();
          double secondPhi = second.getFugacityCoefficient();
          finite &= Double.isFinite(firstPhi) && firstPhi > 0.0 && Double.isFinite(secondPhi) && secondPhi > 0.0;
          fugacityError = Math.max(fugacityError,
              Math.abs(Math.log(first.getx()) + Math.log(firstPhi) - Math.log(second.getx()) - Math.log(secondPhi)));
          comparisons++;
        }
      }
      materialError = Math.max(materialError, Math.abs(recovered - component.getz()));
    }
    double density = system.getDensity("kg/m3");
    double enthalpy = system.getEnthalpy();
    double entropy = system.getEntropy();
    double gibbs = system.getGibbsEnergy();
    double massError = Math.abs(system.getMass("kg") - originalMass) / Math.max(1.0e-30, originalMass);
    double moleError = Math.abs(system.getTotalNumberOfMoles() - originalMoles) / Math.max(1.0e-30, originalMoles);
    finite &= Double.isFinite(density) && density > 0.0 && Double.isFinite(enthalpy) && Double.isFinite(entropy)
        && Double.isFinite(gibbs);
    number(state, "temperatureK", system.getTemperature());
    number(state, "pressureBara", system.getPressure());
    number(state, "totalMoles", system.getTotalNumberOfMoles());
    number(state, "massKg", system.getMass("kg"));
    number(state, "densityKgM3", density);
    number(state, "enthalpyJ", enthalpy);
    number(state, "entropyJPerK", entropy);
    number(state, "gibbsEnergyJ", gibbs);
    number(state, "maximumMaterialResidual", materialError);
    number(state, "maximumLogFugacityResidual", fugacityError);
    number(state, "phaseNormalizationResidual", normalizationError);
    number(state, "betaNormalizationResidual", Math.abs(betaSum - 1.0));
    number(state, "massRelativeResidual", massError);
    number(state, "molesRelativeResidual", moleError);
    state.addProperty("fugacityComparisons", comparisons);
    state.addProperty("excludedFugacityComparisons", excluded);
    state.addProperty("finitePhysicalState", finite);
    state.addProperty("validationPassed",
        finite && materialError < 1.0e-10 && fugacityError < 1.0e-8 && normalizationError < 1.0e-12
            && Math.abs(betaSum - 1.0) < 1.0e-12 && massError < 1.0e-12 && moleError < 1.0e-12
            && (system.getNumberOfPhases() == 1 || comparisons > 0));
    state.add("components", components);
    state.add("phases", phases);
    return state;
  }

  private static JsonObject sample(FluidCase testCase, SystemInterface system, boolean multiphase, String sequence,
      int repeat) {
    JsonObject state;
    try {
      double originalMoles = system.getTotalNumberOfMoles();
      double originalMass = inventoryMass(system);
      long start = System.nanoTime();
      new ThermodynamicOperations(system).TPflash();
      long elapsed = System.nanoTime() - start;
      state = snapshot(system, originalMoles, originalMass);
      state.addProperty("elapsedNs", elapsed);
    } catch (RuntimeException exception) {
      state = new JsonObject();
      state.addProperty("validationPassed", false);
      state.addProperty("exception", exception.getClass().getSimpleName() + ": " + exception.getMessage());
    }
    state.addProperty("case", testCase.name);
    state.addProperty("eos", testCase.eos);
    state.addProperty("algorithm", multiphase ? "TPflash-multiphase" : "TPflash-ordinary");
    state.addProperty("sequence", sequence);
    state.addProperty("repeat", repeat);
    return state;
  }

  /** Compute feed mass directly from inventory before phase molar masses have been initialized. */
  static double inventoryMass(SystemInterface system) {
    double mass = 0.0;
    for (int component = 0; component < system.getNumberOfComponents(); component++) {
      ComponentInterface item = system.getPhase(0).getComponent(component);
      mass += item.getNumberOfmoles() * item.getMolarMass();
    }
    return mass;
  }

  /**
   * Writes all states including baseline numerical failures; no physics threshold is relaxed.
   *
   * @param args output path, optional repeat count, optional case-name substring
   * @throws Exception if the output cannot be written or arguments are invalid
   */
  public static void main(String[] args) throws Exception {
    Path output = Paths.get(args.length > 0 ? args[0] : "stability-optimization-benchmark.json");
    int repeats = args.length > 1 ? Integer.parseInt(args[1]) : 1;
    String filter = args.length > 2 ? args[2] : "all";
    if (repeats < 1) {
      throw new IllegalArgumentException("Repeat count must be positive");
    }
    JsonArray states = new JsonArray();
    int failures = 0;
    for (int repeat = 0; repeat < repeats; repeat++) {
      for (FluidCase testCase : cases()) {
        if (!"all".equals(filter) && !testCase.name.contains(filter)) {
          continue;
        }
        for (boolean multiphase : new boolean[] { false, true }) {
          SystemInterface system = testCase.create(multiphase, false);
          states.add(sample(testCase, system, multiphase, "cold", repeat));
          states.add(sample(testCase, system, multiphase, "unchanged", repeat));
          system.setTemperature(testCase.temperature + 0.25);
          system.setPressure(testCase.pressure * 0.995);
          states.add(sample(testCase, system, multiphase, "changed", repeat));
          states.add(sample(testCase, testCase.create(multiphase, true), multiphase, "fresh-changed", repeat));
        }
      }
    }
    if (states.size() == 0) {
      throw new IllegalArgumentException("No cases match " + filter);
    }
    for (int i = 0; i < states.size(); i++) {
      if (!states.get(i).getAsJsonObject().get("validationPassed").getAsBoolean()) {
        failures++;
      }
    }
    JsonObject report = new JsonObject();
    report.addProperty("javaVersion", System.getProperty("java.version"));
    report.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
    report.addProperty("maxHeapBytes", Runtime.getRuntime().maxMemory());
    report.addProperty("repeats", repeats);
    report.addProperty("stateCount", states.size());
    report.addProperty("validationFailures", failures);
    report.addProperty("validationScope", "numerical closure; not independent experimental validation");
    report.add("states", states);
    Files.createDirectories(output.toAbsolutePath().getParent());
    try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
      new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
    }
    logger.info("Flash matrix: {} states, {} validation failures, output {}", states.size(), failures, output);
  }

  private StabilityOptimizationBenchmark() {
  }
}
