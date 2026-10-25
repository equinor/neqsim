package neqsim.pvtsimulation.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.LBCViscosityMethod;
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DifferentialLiberation;
import neqsim.pvtsimulation.simulation.MultiStageSeparatorTest;
import neqsim.pvtsimulation.simulation.SaturationPressure;
import neqsim.pvtsimulation.simulation.ViscositySim;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.component.ComponentEos;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.component.ComponentPR;
import neqsim.thermo.component.attractiveeosterm.AttractiveTermInterface;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemPrEos1978;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.readwrite.EclipseFluidReadWrite;
import neqsim.thermo.util.readwrite.JsonFluidReadWrite;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.apache.commons.lang3.SerializationUtils;

/**
 * Reproducible fluid-model capture, PVT experiments and bounded E300 delivery checks.
 *
 * <p>
 * All calculations operate on independent serialized copies. The native snapshot is the exact NeqSim model checkpoint;
 * the human-readable parameter inventory and E300 file have explicitly narrower contracts. E300 qualification is
 * limited to the declared temperature/pressure grid and experiments, and does not establish agreement with laboratory
 * measurements or another simulator.
 *
 * @author ESOL
 * @version 1.0
 */
public final class PVTFluidStudy {
  private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
  private static final double REPLAY_RELATIVE_TOLERANCE = 5.0e-4;
  private static final double REPLAY_ABSOLUTE_TOLERANCE = 1.0e-7;
  private static final String LEGACY_REPORT_LIMITATIONS = "Legacy PVTReportGenerator output only: unpopulated zero saturation pressure and metadata are not verified study findings. Canonical reports must use normalized results and separately qualify observations, saturation type and missing inputs.";

  /** Prevent instantiation of the stateless utility. */
  private PVTFluidStudy() {
  }

  /**
   * Configure the declared hydrocarbon viscosity model across current and latent gas/oil property slots.
   *
   * <p>
   * Each allocated phase owns separate physical-property objects for gas and oil. Configuring only its current type
   * leaves the other object's default active after a subsequent phase-type change. This method is intended for initial
   * study-model construction, before transport coefficients are tuned.
   *
   * @param fluid initialized study fluid to configure
   * @param model explicit LBC or PFCT viscosity model
   * @throws IllegalArgumentException if the fluid or model is unsupported
   */
  public static void configureViscosity(SystemInterface fluid, String model) {
    requireFluid(fluid);
    if (!"LBC".equals(model) && !"PFCT".equals(model)) {
      throw new IllegalArgumentException("PVT study viscosity model must be LBC or PFCT");
    }
    for (int p = 0; p < fluid.getMaxNumberOfPhases(); p++) {
      PhaseInterface phase = fluid.getPhase(p);
      PhaseType originalType = phase.getType();
      try {
        for (PhaseType type : new PhaseType[] {originalType, PhaseType.GAS, PhaseType.OIL}) {
          phase.setType(type);
          phase.getPhysicalProperties().setViscosityModel(model);
        }
      } finally {
        phase.setType(originalType);
      }
    }
  }

  /**
   * Capture the component slate, active EOS parameters, directed BIPs and transport-model settings.
   *
   * @param fluid final characterized fluid; not mutated
   * @return JSON inventory; native serialization is required for exact model restoration
   */
  public static String capture(SystemInterface fluid) {
    requireFluid(fluid);
    SystemInterface copy = copy(fluid);
    JsonObject root = new JsonObject();
    root.addProperty("schema", "neqsim-pvt-fluid-study/1");
    root.addProperty("systemClass", copy.getClass().getName());
    root.addProperty("model", copy.getModelName());
    root.addProperty("mixingRule", String.valueOf(copy.getMixingRule()));
    root.addProperty("temperatureK", copy.getTemperature());
    root.addProperty("pressureBara", copy.getPressure());
    root.addProperty("totalMoles", copy.getTotalNumberOfMoles());
    root.addProperty("exactRestoreFormat", "NeqSim native Java serialization; same software version required");
    root.addProperty("inventoryScope",
        "Active cubic EOS, composition, volume translation, BIPs and viscosity settings");
    root.addProperty("hydrateCheck", copy.getHydrateCheck());
    root.addProperty("solidPhaseCheck", copy.doSolidPhaseCheck());
    root.addProperty("chemicalSystem", copy.isChemicalSystem());
    JsonArray components = new JsonArray();
    for (int i = 0; i < copy.getNumberOfComponents(); i++) {
      ComponentInterface component = copy.getComponent(i);
      JsonObject entry = new JsonObject();
      entry.addProperty("index", i);
      entry.addProperty("name", component.getComponentName());
      entry.addProperty("moleFraction", component.getz());
      entry.addProperty("molarMassKgPerMol", component.getMolarMass());
      entry.addProperty("criticalTemperatureK", component.getTC());
      entry.addProperty("criticalPressureBara", component.getPC());
      entry.addProperty("criticalVolumeCm3PerMol", component.getCriticalVolume());
      entry.addProperty("acentricFactor", component.getAcentricFactor());
      entry.addProperty("normalBoilingPointK", component.getNormalBoilingPoint());
      entry.addProperty("normalLiquidDensityGPerCm3", component.getNormalLiquidDensity());
      entry.addProperty("isTBPFraction", component.isIsTBPfraction());
      entry.addProperty("isPlusFraction", component.isIsPlusFraction());
      entry.addProperty("rackettZ", component.getRacketZ());
      entry.addProperty("volumeCorrectionConst", component.getVolumeCorrectionConst());
      entry.addProperty("appliedVolumeCorrection", component.getVolumeCorrection());
      entry.addProperty("volumeCorrectionT", component.getVolumeCorrectionT());
      entry.addProperty("parachor", component.getParachorParameter());
      entry.addProperty("attractiveTermNumber", component.getAttractiveTermNumber());
      AttractiveTermInterface term = component.getAttractiveTerm();
      if (term != null) {
        entry.addProperty("attractiveTermClass", term.getClass().getName());
        entry.addProperty("attractiveM", term.getm());
        double[] parameters = new double[term.getNumberOfParameters()];
        for (int j = 0; j < parameters.length; j++) {
          parameters[j] = term.getParameters(j);
        }
        entry.add("attractiveParameters", array(parameters));
      }
      if (component instanceof ComponentEosInterface) {
        ComponentEosInterface eos = (ComponentEosInterface) component;
        entry.addProperty("a", eos.geta());
        entry.addProperty("b", eos.getb());
        entry.add("deltaEosParameters", array(eos.getDeltaEosParameters()));
      }
      if (component instanceof ComponentEos) {
        ComponentEos eos = (ComponentEos) component;
        entry.addProperty("hasOmegaAOverride", eos.hasOmegaAOverride());
        if (eos.hasOmegaAOverride()) {
          entry.addProperty("omegaAOverride", eos.getOmegaAOverride());
        }
      }
      if (component instanceof ComponentPR) {
        ComponentPR pr = (ComponentPR) component;
        entry.addProperty("omegaB", pr.getOmegaB());
        entry.addProperty("hasOmegaBOverride", pr.hasOmegaBOverride());
      }
      components.add(entry);
    }
    root.add("components", components);
    JsonArray phases = new JsonArray();
    for (int p = 0; p < copy.getMaxNumberOfPhases(); p++) {
      PhaseInterface phase = copy.getPhase(p);
      JsonObject entry = new JsonObject();
      entry.addProperty("index", p);
      entry.addProperty("active", p < copy.getNumberOfPhases());
      entry.addProperty("type", phase.getType().toString());
      entry.addProperty("volumeCorrectionEnabled", phase.useVolumeCorrection());
      entry.addProperty("viscosityModel", phase.getPhysicalProperties().getViscosityModel().getClass().getName());
      entry.add("cspViscosityCorrectionFactors",
          array(phase.getPhysicalProperties().getCspViscosityCorrectionFactors()));
      entry.add("lbcParameters", array(phase.getPhysicalProperties().getLbcParameters()));
      if (phase.getPhysicalProperties().isLBCViscosityModel()) {
        entry.add("lbcCriticalVolumesCm3PerMol",
            array(((LBCViscosityMethod) phase.getPhysicalProperties().getViscosityModel()).getCriticalVolumes()));
      }
      JsonArray phaseComponents = new JsonArray();
      for (int i = 0; i < copy.getNumberOfComponents(); i++) {
        phaseComponents.add(eosParameters(phase.getComponent(i)));
      }
      entry.add("eosComponentParameters", phaseComponents);
      if (phase instanceof PhaseEosInterface) {
        EosMixingRulesInterface rule = ((PhaseEosInterface) phase).getEosMixingRule();
        entry.addProperty("bMixType", rule.getBmixType());
        JsonArray pairs = new JsonArray();
        for (int i = 0; i < copy.getNumberOfComponents(); i++) {
          for (int j = 0; j < copy.getNumberOfComponents(); j++) {
            JsonObject pair = new JsonObject();
            pair.addProperty("i", i);
            pair.addProperty("j", j);
            pair.addProperty("kij", rule.getBinaryInteractionParameter(i, j));
            pair.addProperty("kijT1", rule.getBinaryInteractionParameterT1(i, j));
            pairs.add(pair);
          }
        }
        entry.add("binaryInteractionParameters", pairs);
      }
      phases.add(entry);
    }
    root.add("phases", phases);
    if (supportedClass(copy)) {
      root.add("portableFluidJson",
          JsonParser.parseString(JsonFluidReadWrite.toJsonString(copy, copy.getTemperature() - 273.15)));
      root.addProperty("portableFluidJsonLimitations",
          "Convenience export: not an exact checkpoint; mixing-rule, alpha, OmegaA and transport extensions may be omitted");
    }
    return JSON.toJson(root);
  }

  /**
   * Run six independent thermodynamic identities and numerical derivative checks on a copy.
   *
   * @param fluid fluid at the temperature and pressure to check
   * @return JSON booleans; exceptions propagate rather than being reported as a pass
   */
  public static String checkThermodynamics(SystemInterface fluid) {
    requireFluid(fluid);
    SystemInterface trial = copy(fluid);
    new ThermodynamicOperations(trial).TPflash();
    trial.init(3);
    ThermodynamicModelTest test = new ThermodynamicModelTest(trial);
    ThermodynamicModelTest numericalTest = new ThermodynamicModelTest(trial);
    numericalTest.setMaxError(1.0e-6);
    JsonObject checks = new JsonObject();
    checks.addProperty("fugacityGibbsIdentity", test.checkFugacityCoefficients());
    checks.addProperty("compositionIdentity", test.checkFugacityCoefficientsDn());
    checks.addProperty("compositionSymmetry", test.checkFugacityCoefficientsDn2());
    checks.addProperty("pressureIdentity", test.checkFugacityCoefficientsDP());
    checks.addProperty("temperatureIdentity", test.checkFugacityCoefficientsDT());
    checks.addProperty("numericalDerivatives", numericalTest.checkNumerically());
    boolean passed = true;
    for (java.util.Map.Entry<String, JsonElement> entry : checks.entrySet()) {
      passed &= entry.getValue().getAsBoolean();
    }
    checks.addProperty("passed", passed);
    checks.addProperty("temperatureK", trial.getTemperature());
    checks.addProperty("pressureBara", trial.getPressure());
    checks.addProperty("activePhases", trial.getNumberOfPhases());
    checks.addProperty("identityAbsoluteTolerance", 1.0e-10);
    checks.addProperty("numericalScaledTolerance", 1.0e-6);
    return JSON.toJson(checks);
  }

  /**
   * Execute an existing PVT implementation without modifying the final fluid model.
   *
   * @param fluid final fluid
   * @param kind CCE, CVD, DLE, VISCOSITY, TP or SATURATION
   * @param temperatureK experiment temperature in K
   * @param pressures experiment pressure grid in bara; depletion grids must decrease strictly
   * @return normalized JSON results and a clearly qualified legacy report for reference
   */
  public static String simulate(SystemInterface fluid, String kind, double temperatureK, double[] pressures) {
    requireFluid(fluid);
    requireGrid(temperatureK, pressures);
    if (kind == null) {
      throw new IllegalArgumentException("Experiment kind is required");
    }
    String experiment = kind.toUpperCase(Locale.ROOT);
    SystemInterface trial = copy(fluid);
    trial.setTemperature(temperatureK);
    JsonObject result = new JsonObject();
    result.addProperty("kind", experiment);
    result.addProperty("temperatureK", temperatureK);
    result.add("pressuresBara", array(pressures));
    JsonObject values = new JsonObject();
    PVTReportGenerator report = new PVTReportGenerator(copy(fluid));
    report.setReservoirConditions(pressures[0], temperatureK - 273.15);
    if ("CCE".equals(experiment)) {
      requireDescending(pressures);
      ConstantMassExpansion simulation = new ConstantMassExpansion(trial);
      simulation.setTemperature(temperatureK, "K");
      simulation.setPressures(pressures.clone());
      simulation.runCalc();
      values.add("relativeVolume", finiteArray(simulation.getRelativeVolume(), "CCE relative volume"));
      values.add("liquidRelativeVolume", array(simulation.getLiquidRelativeVolume()));
      values.add("phaseZeroDensityKgPerM3", array(simulation.getDensity()));
      values.add("oilDensityKgPerM3", array(simulation.getOilDensity()));
      values.add("gasZ", array(simulation.getZgas()));
      values.add("yFactor", array(simulation.getYfactor()));
      result.addProperty("saturationPressureBara", simulation.getSaturationPressure());
      report.addCCE(simulation);
    } else if ("CVD".equals(experiment)) {
      requireDescending(pressures);
      ConstantVolumeDepletion simulation = new ConstantVolumeDepletion(trial);
      simulation.setTemperature(temperatureK, "K");
      simulation.setPressures(pressures.clone());
      simulation.runCalc();
      values.add("relativeVolume", finiteArray(simulation.getRelativeVolume(), "CVD relative volume"));
      values.add("liquidRelativeVolume", array(simulation.getLiquidRelativeVolume()));
      values.add("gasZ", array(simulation.getZgas()));
      values.add("mixtureZ", array(simulation.getZmix()));
      values.add("cumulativeMolePercentDepleted", array(simulation.getCummulativeMolePercDepleted()));
      result.addProperty("saturationPressureBara", simulation.getSaturationPressure());
      report.addCVD(simulation);
    } else if ("DLE".equals(experiment)) {
      requireDescending(pressures);
      DifferentialLiberation simulation = new DifferentialLiberation(trial);
      simulation.setTemperature(temperatureK, "K");
      simulation.setPressures(pressures.clone());
      simulation.runCalc();
      values.add("boM3PerSm3", finiteArray(simulation.getBo(), "DLE Bo"));
      values.add("rsSm3PerSm3", finiteArray(simulation.getRs(), "DLE Rs"));
      values.add("bgM3PerSm3", array(simulation.getBg()));
      values.add("oilDensityKgPerM3", array(simulation.getOilDensity()));
      values.add("gasZ", array(simulation.getZgas()));
      result.addProperty("saturationPressureBara", simulation.getSaturationPressure());
      report.addDLE(simulation);
    } else if ("VISCOSITY".equals(experiment)) {
      ViscositySim simulation = new ViscositySim(trial);
      double[] temperatures = new double[pressures.length];
      Arrays.fill(temperatures, temperatureK);
      simulation.setTemperaturesAndPressures(temperatures, pressures.clone());
      simulation.runCalc();
      values.add("gasViscosityPaS", phaseArray(simulation.getGasViscosity(), simulation.getGasPhaseAvailable()));
      values.add("oilViscosityPaS", phaseArray(simulation.getOilViscosity(), simulation.getOilPhaseAvailable()));
      values.add("aqueousViscosityPaS",
          phaseArray(simulation.getAqueousViscosity(), simulation.getAqueousPhaseAvailable()));
      JsonObject availability = new JsonObject();
      availability.add("gas", JSON.toJsonTree(simulation.getGasPhaseAvailable()));
      availability.add("oil", JSON.toJsonTree(simulation.getOilPhaseAvailable()));
      availability.add("aqueous", JSON.toJsonTree(simulation.getAqueousPhaseAvailable()));
      result.add("phaseAvailability", availability);
      result.addProperty("absentPhaseConvention",
          "JSON null when the corresponding flashed phase is absent; phaseAvailability records each point");
      report.addViscosity(simulation);
    } else if ("TP".equals(experiment)) {
      result.add("states", tpStates(trial, temperatureK, pressures));
    } else if ("SATURATION".equals(experiment)) {
      SaturationPressure simulation = new SaturationPressure(trial);
      simulation.setTemperature(temperatureK, "K");
      simulation.run();
      double saturation = simulation.getSaturationPressure();
      result.addProperty("saturationPressureBara", saturation);
      result.addProperty("qualifiedBoundary", saturation > 1.0 && saturation < 1000.0);
      result.addProperty("searchLimitBara", 1000.0);
      report.addSaturationPressure(simulation);
    } else {
      throw new IllegalArgumentException("Unsupported PVT experiment: " + kind);
    }
    result.add("values", values);
    if (!"TP".equals(experiment)) {
      result.addProperty("legacyReportMarkdown", report.generateMarkdownReport());
      result.addProperty("legacyReportLimitations", LEGACY_REPORT_LIMITATIONS);
    }
    return JSON.toJson(result);
  }

  /**
   * Execute the existing multi-stage separator test, including the supplied stock-tank stage.
   *
   * @param fluid final fluid
   * @param reservoirTemperatureK reservoir temperature in K
   * @param reservoirPressureBara reservoir pressure in bara
   * @param stagePressuresBara separator and stock-tank pressures, in order
   * @param stageTemperaturesK temperatures corresponding to every supplied stage
   * @return normalized JSON with stage results and PVT report
   */
  public static String simulateSeparator(SystemInterface fluid, double reservoirTemperatureK,
      double reservoirPressureBara, double[] stagePressuresBara, double[] stageTemperaturesK) {
    requireFluid(fluid);
    requireGrid(reservoirTemperatureK, stagePressuresBara);
    requirePositive(reservoirPressureBara, "Reservoir pressure");
    if (stageTemperaturesK == null || stageTemperaturesK.length != stagePressuresBara.length) {
      throw new IllegalArgumentException("Every separator stage needs a temperature");
    }
    requireDescending(stagePressuresBara);
    MultiStageSeparatorTest simulation = new MultiStageSeparatorTest(copy(fluid));
    simulation.setReservoirConditions(reservoirPressureBara, reservoirTemperatureK - 273.15);
    for (int i = 0; i < stagePressuresBara.length; i++) {
      requirePositive(stageTemperaturesK[i], "Stage temperature");
      simulation.addSeparatorStage(stagePressuresBara[i], stageTemperaturesK[i] - 273.15);
    }
    simulation.run();
    JsonObject result = new JsonObject();
    result.addProperty("kind", "SEPARATOR");
    result.addProperty("totalGORSm3PerSm3", simulation.getTotalGOR());
    result.addProperty("boM3PerSm3", simulation.getBo());
    result.addProperty("stockTankOilDensityKgPerM3", simulation.getStockTankOilDensity());
    result.addProperty("stockTankAPIGravity", simulation.getStockTankAPIGravity());
    result.add("stages", JSON.toJsonTree(simulation.getStageResults()));
    PVTReportGenerator report = new PVTReportGenerator(copy(fluid));
    report.setReservoirConditions(reservoirPressureBara, reservoirTemperatureK - 273.15);
    report.addSeparatorTest(simulation);
    result.addProperty("legacyReportMarkdown", report.generateMarkdownReport());
    result.addProperty("legacyReportLimitations", LEGACY_REPORT_LIMITATIONS);
    return JSON.toJson(result);
  }

  /**
   * Save an exact model checkpoint for use by the same NeqSim/software version.
   *
   * @param fluid final fluid
   * @param outputFile output file path
   * @throws IOException if the checkpoint cannot be written
   */
  public static void writeNativeSnapshot(SystemInterface fluid, String outputFile) throws IOException {
    requireFluid(fluid);
    Files.write(Paths.get(outputFile), SerializationUtils.serialize(fluid));
  }

  /**
   * Restore a native checkpoint produced by this workflow. Only read trusted self-generated files.
   *
   * @param inputFile trusted native checkpoint
   * @return independently deserialized fluid
   * @throws IOException if the checkpoint cannot be read
   */
  public static SystemInterface readNativeSnapshot(String inputFile) throws IOException {
    Object restored = SerializationUtils.deserialize(Files.readAllBytes(Paths.get(inputFile)));
    if (!(restored instanceof SystemInterface)) {
      throw new IllegalArgumentException("Checkpoint does not contain a NeqSim fluid");
    }
    SystemInterface fluid = (SystemInterface) restored;
    requireFluid(fluid);
    return fluid;
  }

  /**
   * Export a supported, constant-BIP cubic fluid using the existing E300 writer.
   *
   * @param fluid final fluid
   * @param outputFile E300 output path
   * @param temperatureK reservoir temperature in K
   * @return export metadata; a completed replay audit is still required
   * @throws IOException if the file cannot be written
   */
  public static String exportE300(SystemInterface fluid, String outputFile, double temperatureK) throws IOException {
    requirePositive(temperatureK, "Temperature");
    requireExportable(fluid);
    EclipseFluidReadWrite.write(copy(fluid), outputFile, temperatureK - 273.15);
    JsonObject result = new JsonObject();
    result.addProperty("written", true);
    result.addProperty("replayRequired", true);
    result.addProperty("systemClass", fluid.getClass().getName());
    result.addProperty("temperatureK", temperatureK);
    result.addProperty("relativeTolerance", REPLAY_RELATIVE_TOLERANCE);
    result.addProperty("absoluteTolerance", REPLAY_ABSOLUTE_TOLERANCE);
    return JSON.toJson(result);
  }

  /**
   * Read an E300 file independently and compare active parameters, TP states and a complete CCE grid.
   *
   * @param original exact final model
   * @param e300File exported E300 file
   * @param temperatureK replay temperature in K
   * @param pressures descending replay pressure grid in bara
   * @return JSON containing every discrepancy; passed is false on a mismatch
   */
  public static synchronized String auditE300(SystemInterface original, String e300File, double temperatureK,
      double[] pressures) {
    requireExportable(original);
    requireGrid(temperatureK, pressures);
    requireDescending(pressures);
    boolean cache = EclipseFluidReadWrite.isUseCache();
    String prefix = EclipseFluidReadWrite.pseudoName;
    SystemInterface imported;
    try {
      EclipseFluidReadWrite.setUseCache(false);
      EclipseFluidReadWrite.pseudoName = "";
      imported = original instanceof SystemPrEos ? EclipseFluidReadWrite.readWithPrOmegaB(e300File)
          : EclipseFluidReadWrite.read(e300File);
    } finally {
      EclipseFluidReadWrite.setUseCache(cache);
      EclipseFluidReadWrite.pseudoName = prefix;
    }
    JsonObject result = new JsonObject();
    JsonArray differences = new JsonArray();
    result.addProperty("scope",
        "Active EOS parameters, TP phase states and CCE at the declared grid; not a laboratory validation");
    result.addProperty("relativeTolerance", REPLAY_RELATIVE_TOLERANCE);
    result.addProperty("absoluteTolerance", REPLAY_ABSOLUTE_TOLERANCE);
    result.addProperty("temperatureK", temperatureK);
    result.add("pressuresBara", array(pressures));
    if (original.getClass() != imported.getClass()) {
      differences.add("EOS class changed on import");
    }
    compareParameters(original, imported, differences);
    JsonObject originalCapture = JsonParser.parseString(capture(original)).getAsJsonObject();
    JsonObject importedCapture = JsonParser.parseString(capture(imported)).getAsJsonObject();
    for (int p = 0; p < original.getMaxNumberOfPhases(); p++) {
      JsonObject expectedPhase = originalCapture.getAsJsonArray("phases").get(p).getAsJsonObject();
      JsonObject actualPhase = importedCapture.getAsJsonArray("phases").get(p).getAsJsonObject();
      compareTree("phase[" + p + "].viscosityModel", expectedPhase.get("viscosityModel"),
          actualPhase.get("viscosityModel"), differences);
      compareTree("phase[" + p + "].lbcParameters", expectedPhase.get("lbcParameters"),
          actualPhase.get("lbcParameters"), differences);
      if (expectedPhase.has("lbcCriticalVolumesCm3PerMol")) {
        compareTree("phase[" + p + "].lbcCriticalVolumesCm3PerMol", expectedPhase.get("lbcCriticalVolumesCm3PerMol"),
            actualPhase.get("lbcCriticalVolumesCm3PerMol"), differences);
      }
    }
    JsonArray expectedTP = tpStates(copy(original), temperatureK, pressures);
    JsonArray actualTP = tpStates(copy(imported), temperatureK, pressures);
    compareTree("TP", expectedTP, actualTP, differences);
    result.add("originalTP", expectedTP);
    result.add("importedTP", actualTP);
    JsonObject expectedCCE = JsonParser.parseString(simulate(original, "CCE", temperatureK, pressures))
        .getAsJsonObject();
    JsonObject actualCCE = JsonParser.parseString(simulate(imported, "CCE", temperatureK, pressures)).getAsJsonObject();
    compareTree("CCE", expectedCCE.getAsJsonObject("values"), actualCCE.getAsJsonObject("values"), differences);
    compareNumber("CCE.saturationPressureBara", expectedCCE.get("saturationPressureBara").getAsDouble(),
        actualCCE.get("saturationPressureBara").getAsDouble(), differences);
    result.add("originalCCE", expectedCCE.get("values"));
    result.add("importedCCE", actualCCE.get("values"));
    JsonArray consistencyByState = new JsonArray();
    for (double pressure : pressures) {
      SystemInterface check = copy(imported);
      check.setTemperature(temperatureK);
      check.setPressure(pressure);
      JsonObject consistency = JsonParser.parseString(checkThermodynamics(check)).getAsJsonObject();
      consistencyByState.add(consistency);
      if (!consistency.get("passed").getAsBoolean()) {
        differences.add("Imported fluid failed thermodynamic consistency checks at " + pressure + " bara");
      }
    }
    result.add("importedThermodynamicChecks", consistencyByState.get(0));
    result.add("importedThermodynamicChecksByState", consistencyByState);
    result.add("differences", differences);
    result.addProperty("passed", differences.size() == 0);
    return JSON.toJson(result);
  }

  /**
   * Run an audit in a fresh Java process; a failed audit returns a nonzero exit status.
   *
   * @param args audit, trusted native checkpoint, E300 path, temperature K, comma-separated pressures bara, output JSON
   * file path
   * @throws IOException if an input cannot be read
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 6 || !"audit".equals(args[0])) {
      throw new IllegalArgumentException("Usage: audit model.ser fluid.e300 temperatureK pressuresBaraCSV audit.json");
    }
    String[] tokens = args[4].split(",");
    double[] pressures = new double[tokens.length];
    for (int i = 0; i < tokens.length; i++) {
      pressures[i] = Double.parseDouble(tokens[i]);
    }
    String audit = auditE300(readNativeSnapshot(args[1]), args[2], Double.parseDouble(args[3]), pressures);
    Files.write(Paths.get(args[5]), audit.getBytes(StandardCharsets.UTF_8));
    if (!JsonParser.parseString(audit).getAsJsonObject().get("passed").getAsBoolean()) {
      System.exit(1);
    }
  }

  /**
   * Reject active model choices that the E300 representation cannot preserve.
   *
   * @param fluid final model to qualify
   */
  private static void requireExportable(SystemInterface fluid) {
    requireFluid(fluid);
    if (!supportedClass(fluid) || fluid.getMixingRule() != EosMixingRuleType.CLASSIC) {
      throw new IllegalArgumentException(
          "E300 study delivery supports exact SRK, PR or PR1978 classes with CLASSIC mixing rule only");
    }
    if (fluid.getHydrateCheck() || fluid.doSolidPhaseCheck() || fluid.isChemicalSystem()) {
      throw new IllegalArgumentException("Hydrate, solid and chemical models are outside the E300 study contract");
    }
    requireUniformTransport(fluid);
    int expectedTerm = fluid.getClass() == SystemSrkEos.class ? 0 : fluid.getClass() == SystemPrEos.class ? 1 : 6;
    PhaseInterface referencePhase = fluid.getPhase(0);
    String viscosityModel = referencePhase.getPhysicalProperties().getViscosityModel().getClass().getSimpleName();
    if (!"LBCViscosityMethod".equals(viscosityModel) && !"PFCTViscosityMethodMod86".equals(viscosityModel)) {
      throw new IllegalArgumentException("E300 delivery requires explicit uniform LBC or PFCT viscosity models");
    }
    if (referencePhase.getPhysicalProperties().isLBCViscosityModel()) {
      for (double coefficient : referencePhase.getPhysicalProperties().getLbcParameters()) {
        if (!Double.isFinite(coefficient)) {
          throw new IllegalArgumentException("LBC viscosity coefficients must be finite");
        }
      }
      for (double volume : ((LBCViscosityMethod) referencePhase.getPhysicalProperties().getViscosityModel())
          .getCriticalVolumes()) {
        requirePositive(volume, "LBC viscosity critical volume");
      }
    }
    for (int p = 0; p < fluid.getMaxNumberOfPhases(); p++) {
      PhaseInterface phase = fluid.getPhase(p);
      if (!(phase instanceof PhaseEosInterface)) {
        throw new IllegalArgumentException("Only cubic EOS phases can be exported");
      }
      EosMixingRulesInterface rule = ((PhaseEosInterface) phase).getEosMixingRule();
      if (rule.getBmixType() != 0) {
        throw new IllegalArgumentException("Custom covolume mixing rules are not represented in E300");
      }
      if (phase.useVolumeCorrection() != referencePhase.useVolumeCorrection()
          || !phase.getPhysicalProperties().getViscosityModel().getClass()
              .equals(referencePhase.getPhysicalProperties().getViscosityModel().getClass())
          || !Arrays.equals(phase.getPhysicalProperties().getLbcParameters(),
              referencePhase.getPhysicalProperties().getLbcParameters())) {
        throw new IllegalArgumentException("Phase-specific volume or transport models cannot be represented in E300");
      }
      if (phase.getPhysicalProperties().isLBCViscosityModel() && !Arrays.equals(
          ((LBCViscosityMethod) phase.getPhysicalProperties().getViscosityModel()).getCriticalVolumes(),
          ((LBCViscosityMethod) referencePhase.getPhysicalProperties().getViscosityModel()).getCriticalVolumes())) {
        throw new IllegalArgumentException("Phase-specific LBC critical volumes cannot be represented in E300");
      }
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        ComponentInterface component = phase.getComponent(i);
        if (!eosParameters(component).equals(eosParameters(referencePhase.getComponent(i)))) {
          throw new IllegalArgumentException("Phase-specific EOS parameters cannot be represented in E300");
        }
        if (component.getAttractiveTermNumber() != expectedTerm || component.getVolumeCorrectionT() != 0.0) {
          throw new IllegalArgumentException(
              "Custom attractive terms or temperature-dependent volume shifts are unsupported");
        }
        double[] factors = phase.getPhysicalProperties().getCspViscosityCorrectionFactors();
        if (factors != null) {
          for (double factor : factors) {
            if (factor != 1.0) {
              throw new IllegalArgumentException("Fitted CSP viscosity factors are not represented in E300");
            }
          }
        }
        for (int j = 0; j < fluid.getNumberOfComponents(); j++) {
          double kij = rule.getBinaryInteractionParameter(i, j);
          if (!Double.isFinite(kij) || rule.getBinaryInteractionParameterT1(i, j) != 0.0
              || kij != rule.getBinaryInteractionParameter(j, i)
              || kij != ((PhaseEosInterface) referencePhase).getEosMixingRule().getBinaryInteractionParameter(i, j)) {
            throw new IllegalArgumentException("E300 requires finite, symmetric, temperature-independent BIPs");
          }
        }
      }
    }
  }

  /**
   * Reject latent gas/oil transport differences without changing the caller's selected phase types.
   *
   * @param fluid initialized model whose current and latent hydrocarbon transport slots must agree
   * @throws IllegalArgumentException if an effective transport setting would be lost by export
   */
  private static void requireUniformTransport(SystemInterface fluid) {
    PhaseInterface reference = fluid.getPhase(0);
    Class<?> modelClass = reference.getPhysicalProperties().getViscosityModel().getClass();
    double[] coefficients = reference.getPhysicalProperties().getLbcParameters();
    double[] volumes = reference.getPhysicalProperties().isLBCViscosityModel()
        ? ((LBCViscosityMethod) reference.getPhysicalProperties().getViscosityModel()).getCriticalVolumes()
        : null;
    for (int p = 0; p < fluid.getMaxNumberOfPhases(); p++) {
      PhaseInterface phase = fluid.getPhase(p);
      PhaseType originalType = phase.getType();
      try {
        for (PhaseType type : new PhaseType[] {originalType, PhaseType.GAS, PhaseType.OIL}) {
          phase.setType(type);
          if (!modelClass.equals(phase.getPhysicalProperties().getViscosityModel().getClass())
              || !Arrays.equals(coefficients, phase.getPhysicalProperties().getLbcParameters())) {
            throw new IllegalArgumentException("Phase-type-specific viscosity settings cannot be represented in E300");
          }
          if (volumes != null && !Arrays.equals(volumes,
              ((LBCViscosityMethod) phase.getPhysicalProperties().getViscosityModel()).getCriticalVolumes())) {
            throw new IllegalArgumentException(
                "Phase-type-specific LBC critical volumes cannot be represented in E300");
          }
          double[] factors = phase.getPhysicalProperties().getCspViscosityCorrectionFactors();
          if (factors != null) {
            for (double factor : factors) {
              if (factor != 1.0) {
                throw new IllegalArgumentException("Fitted CSP viscosity factors are not represented in E300");
              }
            }
          }
        }
      } finally {
        phase.setType(originalType);
      }
    }
  }

  /**
   * Inventory EOS quantities that must agree between allocated phases before export.
   *
   * @param component component whose active cubic parameters are captured
   * @return canonical scalar and coefficient inventory without phase amounts
   */
  private static JsonObject eosParameters(ComponentInterface component) {
    JsonObject entry = new JsonObject();
    entry.addProperty("componentClass", component.getClass().getName());
    entry.addProperty("TcK", component.getTC());
    entry.addProperty("PcBara", component.getPC());
    entry.addProperty("acentricFactor", component.getAcentricFactor());
    entry.addProperty("molarMassKgPerMol", component.getMolarMass());
    entry.addProperty("criticalVolumeCm3PerMol", component.getCriticalVolume());
    entry.addProperty("normalBoilingPointK", component.getNormalBoilingPoint());
    entry.addProperty("rackettZ", component.getRacketZ());
    entry.addProperty("volumeCorrectionConst", component.getVolumeCorrectionConst());
    entry.addProperty("volumeCorrectionT", component.getVolumeCorrectionT());
    entry.addProperty("appliedVolumeCorrection", component.getVolumeCorrection());
    entry.addProperty("parachor", component.getParachorParameter());
    entry.addProperty("attractiveTermNumber", component.getAttractiveTermNumber());
    if (component.getAttractiveTerm() != null) {
      AttractiveTermInterface term = component.getAttractiveTerm();
      entry.addProperty("attractiveClass", term.getClass().getName());
      entry.addProperty("m", term.getm());
      double[] coefficients = new double[term.getNumberOfParameters()];
      for (int i = 0; i < coefficients.length; i++) {
        coefficients[i] = term.getParameters(i);
      }
      entry.add("alphaCoefficients", array(coefficients));
    }
    if (component instanceof ComponentEosInterface) {
      ComponentEosInterface eos = (ComponentEosInterface) component;
      entry.addProperty("a", eos.geta());
      entry.addProperty("b", eos.getb());
      entry.add("deltaEosParameters", array(eos.getDeltaEosParameters()));
    }
    if (component instanceof ComponentEos && ((ComponentEos) component).hasOmegaAOverride()) {
      entry.addProperty("omegaAOverride", ((ComponentEos) component).getOmegaAOverride());
    }
    if (component instanceof ComponentPR) {
      entry.addProperty("omegaB", ((ComponentPR) component).getOmegaB());
    }
    return entry;
  }

  /**
   * Compare material identity and active cubic parameters after file import.
   *
   * @param original final model before export
   * @param imported independently parsed E300 model
   * @param differences accumulator of failed comparisons
   */
  private static void compareParameters(SystemInterface original, SystemInterface imported, JsonArray differences) {
    if (original.getNumberOfComponents() != imported.getNumberOfComponents()) {
      differences.add("Component count changed on import");
      return;
    }
    for (int i = 0; i < original.getNumberOfComponents(); i++) {
      ComponentInterface expected = original.getComponent(i);
      ComponentInterface actual = imported.getComponent(i);
      String key = "component[" + i + "].";
      if (!expected.getComponentName().replace("_PC", "").equals(actual.getComponentName().replace("_PC", ""))) {
        differences
            .add(key + "identity: expected " + expected.getComponentName() + ", imported " + actual.getComponentName());
      }
      compareNumber(key + "moleFraction", expected.getz(), actual.getz(), differences);
      compareNumber(key + "Tc", expected.getTC(), actual.getTC(), differences);
      compareNumber(key + "Pc", expected.getPC(), actual.getPC(), differences);
      compareNumber(key + "acentricFactor", expected.getAcentricFactor(), actual.getAcentricFactor(), differences);
      compareNumber(key + "molarMass", expected.getMolarMass(), actual.getMolarMass(), differences);
      compareNumber(key + "criticalVolume", expected.getCriticalVolume(), actual.getCriticalVolume(), differences);
      compareNumber(key + "normalBoilingPoint", expected.getNormalBoilingPoint(), actual.getNormalBoilingPoint(),
          differences);
      compareNumber(key + "parachor", expected.getParachorParameter(), actual.getParachorParameter(), differences);
      ComponentEosInterface first = (ComponentEosInterface) expected;
      ComponentEosInterface second = (ComponentEosInterface) actual;
      compareNumber(key + "a", first.geta(), second.geta(), differences);
      compareNumber(key + "b", first.getb(), second.getb(), differences);
      double shiftExpected = original.getPhase(0).useVolumeCorrection() ? expected.getVolumeCorrection() / first.getb()
          : 0.0;
      double shiftActual = imported.getPhase(0).useVolumeCorrection() ? actual.getVolumeCorrection() / second.getb()
          : 0.0;
      compareNumber(key + "effectiveVolumeShift", shiftExpected, shiftActual, differences);
      compareNumber(key + "alphaAt250K", expected.getAttractiveTerm().alpha(250.0),
          actual.getAttractiveTerm().alpha(250.0), differences);
      compareNumber(key + "alphaAt500K", expected.getAttractiveTerm().alpha(500.0),
          actual.getAttractiveTerm().alpha(500.0), differences);
      EosMixingRulesInterface firstRule = ((PhaseEosInterface) original.getPhase(0)).getEosMixingRule();
      EosMixingRulesInterface secondRule = ((PhaseEosInterface) imported.getPhase(0)).getEosMixingRule();
      for (int j = 0; j < original.getNumberOfComponents(); j++) {
        compareNumber(key + "kij[" + j + "]", firstRule.getBinaryInteractionParameter(i, j),
            secondRule.getBinaryInteractionParameter(i, j), differences);
      }
    }
  }

  /**
   * Calculate phase-resolved equilibrium and transport results on an independent copy per state.
   *
   * @param fluid source model
   * @param temperatureK grid temperature in K
   * @param pressures positive pressure grid in bara
   * @return phase fractions, compositions, Z, density and viscosity for every state
   */
  private static JsonArray tpStates(SystemInterface fluid, double temperatureK, double[] pressures) {
    JsonArray states = new JsonArray();
    for (double pressure : pressures) {
      SystemInterface trial = copy(fluid);
      trial.setTemperature(temperatureK);
      trial.setPressure(pressure);
      new ThermodynamicOperations(trial).TPflash();
      trial.initPhysicalProperties();
      JsonObject state = new JsonObject();
      state.addProperty("pressureBara", pressure);
      state.addProperty("phaseCount", trial.getNumberOfPhases());
      state.addProperty("densityKgPerM3", trial.getDensity("kg/m3"));
      JsonArray phases = new JsonArray();
      for (int p = 0; p < trial.getNumberOfPhases(); p++) {
        PhaseInterface phase = trial.getPhase(p);
        JsonObject entry = new JsonObject();
        entry.addProperty("type", phase.getType().toString());
        entry.addProperty("moleFraction", trial.getBeta(p));
        entry.addProperty("z", phase.getZ());
        entry.addProperty("densityKgPerM3", phase.getDensity("kg/m3"));
        entry.addProperty("viscosityPaS", phase.getPhysicalProperties().getViscosity());
        double[] composition = new double[trial.getNumberOfComponents()];
        for (int i = 0; i < composition.length; i++) {
          composition[i] = phase.getComponent(i).getx();
        }
        entry.add("composition", finiteArray(composition, "Phase composition"));
        phases.add(entry);
      }
      state.add("phases", phases);
      states.add(state);
    }
    return states;
  }

  /**
   * Compare corresponding normalized result trees while preserving categorical identity.
   *
   * @param path result coordinate being compared
   * @param expected original result
   * @param actual imported result
   * @param differences accumulator of failed comparisons
   */
  private static void compareTree(String path, JsonElement expected, JsonElement actual, JsonArray differences) {
    if (expected == null || actual == null || expected.isJsonNull() || actual.isJsonNull()) {
      if (expected == null || actual == null || !expected.equals(actual)) {
        differences.add(path + ": missing or non-finite replay result");
      }
    } else if (expected.isJsonObject() && actual.isJsonObject()) {
      for (java.util.Map.Entry<String, JsonElement> entry : expected.getAsJsonObject().entrySet()) {
        compareTree(path + "." + entry.getKey(), entry.getValue(), actual.getAsJsonObject().get(entry.getKey()),
            differences);
      }
    } else if (expected.isJsonArray() && actual.isJsonArray()) {
      if (expected.getAsJsonArray().size() != actual.getAsJsonArray().size()) {
        differences.add(path + ": array size differs");
        return;
      }
      for (int i = 0; i < expected.getAsJsonArray().size(); i++) {
        compareTree(path + "[" + i + "]", expected.getAsJsonArray().get(i), actual.getAsJsonArray().get(i),
            differences);
      }
    } else if (expected.isJsonPrimitive() && expected.getAsJsonPrimitive().isNumber() && actual.isJsonPrimitive()
        && actual.getAsJsonPrimitive().isNumber()) {
      compareNumber(path, expected.getAsDouble(), actual.getAsDouble(), differences);
    } else if (!expected.equals(actual)) {
      differences.add(path + ": value differs");
    }
  }

  /**
   * Apply the declared fixed absolute-plus-relative replay tolerance.
   *
   * @param name result coordinate
   * @param expected original finite value
   * @param actual imported finite value
   * @param differences accumulator of failed comparisons
   */
  private static void compareNumber(String name, double expected, double actual, JsonArray differences) {
    double tolerance = REPLAY_ABSOLUTE_TOLERANCE + REPLAY_RELATIVE_TOLERANCE * Math.abs(expected);
    if (!Double.isFinite(expected) || !Double.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
      differences.add(name + ": expected " + expected + ", imported " + actual + ", tolerance " + tolerance);
    }
  }

  /**
   * Reject missing or non-finite required experiment outputs.
   *
   * @param values experiment result array
   * @param name physical quantity for the error message
   * @return JSON array of finite values
   */
  private static JsonArray finiteArray(double[] values, String name) {
    if (values == null || values.length == 0) {
      throw new IllegalStateException(name + " is missing");
    }
    for (double value : values) {
      if (!Double.isFinite(value)) {
        throw new IllegalStateException(name + " contains a non-finite result");
      }
    }
    return array(values);
  }

  /**
   * Encode optional numerical outputs, retaining undefined or absent-phase values as JSON null.
   *
   * @param values possibly absent optional array
   * @return JSON array with finite values or null entries
   */
  private static JsonArray array(double[] values) {
    JsonArray result = new JsonArray();
    if (values != null) {
      for (double value : values) {
        result.add(Double.isFinite(value) ? JSON.toJsonTree(value) : JsonNull.INSTANCE);
      }
    }
    return result;
  }

  /**
   * Encode phase-dependent outputs using flashed presence instead of inferring absence from zero.
   *
   * @param values viscosity values from the existing simulation
   * @param available presence flags at the same flashed points
   * @return finite available values, including zero, and null for absent phases
   */
  private static JsonArray phaseArray(double[] values, boolean[] available) {
    if (values == null || available == null || values.length != available.length) {
      throw new IllegalStateException("Viscosity values and phase availability must have matching lengths");
    }
    JsonArray result = new JsonArray();
    for (int i = 0; i < values.length; i++) {
      if (available[i] && !Double.isFinite(values[i])) {
        throw new IllegalStateException("Available phase viscosity contains a non-finite result");
      }
      result.add(available[i] ? JSON.toJsonTree(values[i]) : JsonNull.INSTANCE);
    }
    return result;
  }

  /**
   * Test exact supported cubic system classes without accepting differently parameterized subclasses.
   *
   * @param fluid source model
   * @return true only for the supported SRK, PR and PR1978 system classes
   */
  private static boolean supportedClass(SystemInterface fluid) {
    return fluid.getClass() == SystemSrkEos.class || fluid.getClass() == SystemPrEos.class
        || fluid.getClass() == SystemPrEos1978.class;
  }

  /**
   * Isolate every nested model object through the existing serialization copy route.
   *
   * @param fluid trusted source model
   * @return independent exact model copy
   */
  private static SystemInterface copy(SystemInterface fluid) {
    return SerializationUtils.clone(fluid);
  }

  /**
   * Require an explicitly initialized and normalized final composition.
   *
   * @param fluid source model
   */
  private static void requireFluid(SystemInterface fluid) {
    if (fluid == null || fluid.getNumberOfComponents() == 0) {
      throw new IllegalArgumentException("A fluid with at least one component is required");
    }
    requirePositive(fluid.getTotalNumberOfMoles(), "Total mole inventory");
    double sum = 0.0;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      double fraction = fluid.getComponent(i).getz();
      if (!Double.isFinite(fraction) || fraction < 0.0) {
        throw new IllegalArgumentException("Mole fractions must be finite and nonnegative");
      }
      sum += fraction;
    }
    if (Math.abs(sum - 1.0) > 1.0e-8) {
      throw new IllegalArgumentException("Final mole fractions must sum to one");
    }
  }

  /**
   * Validate positive finite experiment coordinates before running simulations.
   *
   * @param temperatureK temperature in K
   * @param pressures pressures in bara
   */
  private static void requireGrid(double temperatureK, double[] pressures) {
    requirePositive(temperatureK, "Temperature");
    if (pressures == null || pressures.length == 0) {
      throw new IllegalArgumentException("At least one replay pressure is required");
    }
    for (double pressure : pressures) {
      requirePositive(pressure, "Pressure");
    }
  }

  /**
   * Require the decreasing pressure order used by depletion protocols.
   *
   * @param pressures pressure sequence in bara
   */
  private static void requireDescending(double[] pressures) {
    for (int i = 1; i < pressures.length; i++) {
      if (pressures[i] >= pressures[i - 1]) {
        throw new IllegalArgumentException("PVT depletion pressures must decrease strictly");
      }
    }
  }

  /**
   * Reject an invalid absolute thermodynamic coordinate.
   *
   * @param value coordinate value
   * @param name coordinate description for the error message
   */
  private static void requirePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }
}
