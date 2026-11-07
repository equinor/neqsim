package neqsim.process.engineering;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidNativeControlSystem;
import neqsim.process.engineering.pid.PidSafetyFunctionImporter;
import neqsim.process.engineering.pid.PidSafetyFunctionValidator;
import neqsim.process.engineering.pid.PidSimulationBindings;
import neqsim.process.engineering.pid.ProfessionalPidPackageExporter;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.measurementdevice.PushButton;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;
import neqsim.process.safety.sif.ClosedLoopSafetyFunction;
import neqsim.process.safety.sif.SafetyFunctionChannel;
import neqsim.process.util.scenario.CompressorAutomationStudy;
import neqsim.process.util.scenario.CompressorAutomationStudy.CaseBundle;
import neqsim.process.util.scenario.CompressorAutomationStudy.CaseDefinition;
import neqsim.process.util.scenario.CompressorAutomationStudy.Design;
import neqsim.process.util.scenario.CompressorAutomationStudy.Profile;
import neqsim.process.util.scenario.CompressorAutomationStudy.Train;

/**
 * Runs the public compressor model, canonical instrumentation, native acceptance cases and professional export.
 *
 * <p>
 * This is a thin composition of existing engineering APIs. It does not implement another transient solver or verdict
 * engine. The synthetic map, installed independence, machinery-protection data, normative drawing approval and safety
 * qualification remain explicit engineering review tasks. Unsupported canonical safety-policy edits fail readback.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class CompressorAutomationWorkflow {
  private static final Logger logger = LogManager.getLogger(CompressorAutomationWorkflow.class);
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final String PROJECT_ID = "COMPRESSOR-AUTOMATION";
  private static final String QUALIFICATION = "CONTROLLED_STANDARDS_VENDOR_INSTALLED_INDEPENDENCE_SIL_AND_CONSTRUCTION_HOLD";

  /** Prevents utility construction. */
  private CompressorAutomationWorkflow() {
  }

  /**
   * Runs every public native profile at a declared 0.1-second physical timestep.
   *
   * @param design explicit scalar synthetic design assumptions
   * @param directory new output directory
   * @return structured execution status with distinct native criterion outcomes and review holds
   * @throws IOException if inputs or actual artifacts cannot be written
   */
  public static Map<String, Object> run(Design design, Path directory) throws IOException {
    return run(design, null, defaultCases(), directory);
  }

  /**
   * Executes an optional revised canonical proposal and an explicit native acceptance selection.
   *
   * <p>
   * Canonical control settings are applied to an isolated healthy train before fault construction. Fault receipts are
   * frozen from declared overrides; the runtime observer only verifies readback and never erases faults. The existing
   * coordinated strict engineering runner owns case execution and its typed results.
   * </p>
   *
   * @param design design assumptions copied before use
   * @param canonicalProposal optional edited canonical graph, or null to import actual native objects
   * @param definitions explicit case definitions; the output records whether the selection is a subset
   * @param directory new output directory, never overwritten
   * @return detached status map
   * @throws IOException if artifact publication fails
   * @throws IllegalArgumentException if input, canonical policy, fault scope or output identity is invalid
   */
  public static Map<String, Object> run(Design design, PidDesignModel canonicalProposal,
      List<CaseDefinition> definitions, Path directory) throws IOException {
    if (design == null || definitions == null || definitions.isEmpty() || directory == null) {
      throw new IllegalArgumentException("A design, nonempty case selection and new output directory are required");
    }
    Path output = directory.toAbsolutePath().normalize();
    if (Files.exists(output)) {
      throw new IllegalArgumentException("Output directory must not already exist: " + output);
    }
    Design accepted = designFromJson(GSON.toJson(design));
    List<CaseDefinition> cases = new ArrayList<CaseDefinition>(definitions);
    Set<String> caseKeys = new LinkedHashSet<String>();
    for (CaseDefinition definition : cases) {
      if (definition == null || !caseKeys.add(caseKey(definition))) {
        throw new IllegalArgumentException("Case definitions must be nonnull and have unique profile/timestep IDs");
      }
    }
    Train original = CompressorAutomationStudy.create(accepted);
    original.getProcess().run();
    Train proposalSource = original.copy();
    proposalSource.initializeDynamics();
    EngineeringProject seedProject = project(proposalSource);
    PidDesignModel imported = PidNativeControlSystem.fromProcess(seedProject);
    for (ClosedLoopSafetyFunction function : proposalSource.getSafetyFunctions()) {
      imported = PidSafetyFunctionImporter.fromNativeFunction(seedProject, imported,
          PidNativeControlSystem.bind(seedProject, imported), proposalSource.getProcess(), function, role(function));
    }
    PidDesignModel canonical = canonicalProposal == null ? imported
        : PidDesignModel.fromJson(GSON.toJson(canonicalProposal.toMap()));
    Train governed = original.copy();
    PidNativeControlSystem.applyCanonicalConfiguration(seedProject, canonical,
        PidNativeControlSystem.bind(seedProject, canonical), governed.getProcess());
    EngineeringProject project = project(governed);
    PidSimulationBindings bindings = PidNativeControlSystem.bind(project, canonical);
    Files.createDirectories(output.getParent());
    Files.createDirectory(output);
    Map<String, Object> status = new LinkedHashMap<String, Object>();
    status.put("schemaVersion", "neqsim.compressor_automation_workflow.v1");
    status.put("executionStatus", "RUNNING");
    status.put("qualification", QUALIFICATION);
    status.put("fitnessForConstruction", Boolean.FALSE);
    status.put("projectId", project.getProjectId());
    status.put("acceptedDesign", GSON.fromJson(GSON.toJson(accepted), Map.class));
    try {
      status.put("runtimeClasses", runtimeClasses(governed));
      write(output.resolve("workflow-status.json"), status);
      write(output.resolve("design-input.json"), status.get("acceptedDesign"));
      write(output.resolve("steady-state.json"), steadySnapshot(original));
      write(output.resolve("imported-native-proposal.json"), imported.toMap());
      write(output.resolve("canonical-pid.json"), canonical.toMap());
      write(output.resolve("automation-registers.json"),
          PidNativeControlSystem.automationRegisters(project, canonical, bindings));
      List<CaseExecution> executions = new ArrayList<CaseExecution>();
      for (CaseDefinition requested : cases) {
        Train prototype = governed.copy();
        prototype.initializeDynamics();
        prototype.applyFaultOverrides(requested);
        Map<String, Map<String, Object>> controlFaults = controlFaults(prototype, requested);
        PidNativeControlSystem.ConfiguredControlSystem control = PidNativeControlSystem.validateConfigured(project,
            canonical, bindings, prototype.getProcess(), controlFaults);
        Map<String, PidSafetyFunctionValidator.ConfiguredFunction> functions = safetyReceipts(project, canonical,
            bindings, prototype, requested);
        CaseDefinition declared = requested.withEvidenceReference(control.getEvidenceReference());
        for (PidSafetyFunctionValidator.ConfiguredFunction function : functions.values()) {
          declared = declared.withEvidenceReference(function.getEvidenceReference());
        }
        ReceiptObserver observer = new ReceiptObserver(project, canonical, bindings, control, functions);
        CaseBundle bundle = CompressorAutomationStudy.createCase(governed, declared, observer);
        project.addDynamicSafetyScenario(bundle.getScenario());
        executions.add(new CaseExecution(declared, bundle, control, functions));
      }
      EngineeringSimulationResult simulation = EngineeringSimulationRunner.runStrict(project);
      List<Map<String, Object>> feedback = new ArrayList<Map<String, Object>>();
      List<Map<String, Object>> crosswalk = new ArrayList<Map<String, Object>>();
      int passed = 0;
      int errors = 0;
      if (simulation.getDynamicScenarioResults().size() != executions.size()) {
        throw new IllegalStateException("Coordinated runner did not return every declared case");
      }
      for (int index = 0; index < executions.size(); index++) {
        CaseExecution execution = executions.get(index);
        DynamicSafetyScenarioResult nativeResult = simulation.getDynamicScenarioResults().get(index);
        CompressorAutomationStudy.Result result = execution.bundle.complete(nativeResult);
        Path caseDirectory = output.resolve("case-" + caseKey(execution.definition));
        Files.createDirectory(caseDirectory);
        write(caseDirectory.resolve("scenario.json"), execution.bundle.getScenario().toMap());
        write(caseDirectory.resolve("result.json"), result.toMap());
        Map<String, Object> trace = new LinkedHashMap<String, Object>();
        trace.put("samples", result.getSamples());
        trace.put("source", "ACTUAL_NATIVE_CONFIGURED_COPY_NOT_A_RECONSTRUCTED_TRAJECTORY");
        write(caseDirectory.resolve("full-trace.json"), trace);
        write(caseDirectory.resolve("native-result.json"), nativeResult.toMap());
        byte[] nativeBytes = nativeResult.toJson().getBytes(StandardCharsets.UTF_8);
        Files.write(caseDirectory.resolve("native-result.raw.txt"), nativeBytes);
        Map<String, Object> configuration = new LinkedHashMap<String, Object>();
        configuration.put("controlSystem", execution.control.toMap());
        configuration.put("declaredFaultOverrides", execution.definition.getFaultOverrides());
        List<Map<String, Object>> safety = new ArrayList<Map<String, Object>>();
        for (PidSafetyFunctionValidator.ConfiguredFunction function : execution.functions.values()) {
          safety.add(function.toMap());
          feedback.add(PidSafetyFunctionValidator.attachResult(function, execution.bundle.getScenario(), nativeResult,
              maintained(execution.bundle.getScenario())));
        }
        configuration.put("safetyFunctions", safety);
        write(caseDirectory.resolve("configuration.json"), configuration);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("scenarioId", nativeResult.getScenarioId());
        row.put("caseDirectory", output.relativize(caseDirectory).toString());
        row.put("nativeResultFingerprint", sha256(nativeBytes));
        row.put("rawNativeTextSha256", sha256(Files.readAllBytes(caseDirectory.resolve("native-result.raw.txt"))));
        row.put("strictNativeResultFileSha256",
            sha256(Files.readAllBytes(caseDirectory.resolve("native-result.json"))));
        row.put("strictFullTraceFileSha256", sha256(Files.readAllBytes(caseDirectory.resolve("full-trace.json"))));
        row.put("controlConfigurationFingerprint", execution.control.getFingerprint());
        row.put("nativeRequiredCriteriaPassed", Boolean.valueOf(nativeResult.isPassed()));
        List<?> nativeErrors = (List<?>) nativeResult.toMap().get("errors");
        row.put("nativeErrors", new ArrayList<Object>(nativeErrors));
        crosswalk.add(row);
        passed += nativeResult.isPassed() ? 1 : 0;
        errors += nativeErrors.isEmpty() ? 0 : 1;
      }
      write(output.resolve("engineering-simulation-results.json"), simulation.toMap());
      write(output.resolve("dynamic-pid-feedback.json"), map("safetyFunctionResults", feedback));
      write(output.resolve("native-evidence-crosswalk.json"), map("cases", crosswalk));
      EngineeringEvidenceRecord evidence = new EngineeringEvidenceRecord("COMPRESSOR-NATIVE-EXECUTED-FEEDBACK",
          "ACTUAL_CONFIGURED_COMPRESSOR_SCENARIO_RESULTS", project.getRevision())
          .setTitle("Canonical control and safety settings with actual native case results")
          .setSourceOrganization("NeqSim synthetic public compressor workflow")
          .setChecksum(sha256(Files.readAllBytes(output.resolve("dynamic-pid-feedback.json"))));
      for (ProcessEquipmentInterface unit : governed.getProcess().getUnitOperations()) {
        evidence.linkEquipment(unit.getName());
      }
      project.addEvidenceRecord(evidence);
      Dexpi20PlantExportMetadata metadata = Dexpi20PlantExportMetadata
          .builder(Instant.now().toString(), "NeqSim synthetic compressor automation", "NeqSim", "workspace")
          .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, PROJECT_ID)
          .build();
      Path exported = ProfessionalPidPackageExporter.export(project, canonical, bindings, metadata,
          output.resolve("native-pid-package"));
      write(output.resolve("project-validation.json"), project.validate());
      status.put("executionStatus", errors == 0 ? "COMPLETE_REVIEW_REQUIRED" : "COMPLETE_WITH_NATIVE_EXECUTION_ERRORS");
      status.put("nativeProfileCount", Integer.valueOf(executions.size()));
      status.put("nativeCriteriaPassCount", Integer.valueOf(passed));
      status.put("nativeCriteriaFailCount", Integer.valueOf(executions.size() - passed));
      status.put("nativeExecutionErrorCount", Integer.valueOf(errors));
      status.put("allPublicProfilesSelected", Boolean.valueOf(allProfiles(cases)));
      status.put("nativePackage", output.relativize(exported).toString());
      status.put("nativePackageManifestSha256", sha256(Files.readAllBytes(exported.resolve("package-manifest.json"))));
      status.put("externalStrictLayoutAndVisualReview", "PENDING_SEPARATE_AGENT_REVIEW");
      status.put("nativeJsonPolicy",
          "Strict JSON uses null plus nonFiniteValues; exact native text/fingerprint retained separately");
      status.put("designBasisApplicability",
          "Inherited NORSOK template requirements are proposals; facility/jurisdiction and controlled editions require review");
      status.put("sourceRef", System.getProperty("neqsim.sourceRef", "UNPINNED_WORKSPACE_BUILD"));
      status.put("javaVersion", System.getProperty("java.version"));
      status.put("canonicalControlEditsApplied", Boolean.valueOf(canonicalProposal != null));
      write(output.resolve("workflow-status.json"), status);
      logger.info("Compressor automation workflow completed at {} with {} native PASS and {} native FAIL cases", output,
          passed, executions.size() - passed);
      return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(status));
    } catch (IOException | RuntimeException failure) {
      status.put("executionStatus", "EXECUTION_ERROR");
      status.put("errorClass", failure.getClass().getName());
      status.put("errorMessage", failure.getMessage());
      try {
        write(output.resolve("workflow-status.json"), status);
      } catch (IOException writeFailure) {
        failure.addSuppressed(writeFailure);
      }
      throw failure;
    }
  }

  /**
   * Returns the complete public profile selection without hiding expected physical failures.
   *
   * @return immutable list using native 20-second/event-at-two-second case defaults
   */
  public static List<CaseDefinition> defaultCases() {
    List<CaseDefinition> result = new ArrayList<CaseDefinition>();
    for (Profile profile : Profile.values()) {
      result.add(new CaseDefinition(profile.name(), 0.1));
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Reads strict scalar and composition design JSON with no unknown, duplicate, null, string-coerced or non-finite
   * values.
   *
   * <p>
   * Omitted fields retain the public Design constructor defaults. Physical design relationships are subsequently
   * checked by the native model constructor before execution.
   * </p>
   *
   * @param json scalar/composition Design JSON object
   * @return independently owned typed design
   * @throws IllegalArgumentException if syntax, field identity or finite scalar typing is invalid
   */
  public static Design designFromJson(String json) {
    if (json == null) {
      throw new IllegalArgumentException("Design JSON is required");
    }
    Set<String> fields = new LinkedHashSet<String>();
    for (Field field : Design.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers())
          && (field.getType() == double.class || "composition".equals(field.getName()))) {
        fields.add(field.getName());
      }
    }
    JsonObject values = new JsonObject();
    Set<String> seen = new LinkedHashSet<String>();
    try (JsonReader reader = new JsonReader(new StringReader(json))) {
      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        if (!fields.contains(name) || !seen.add(name)) {
          throw new IllegalArgumentException("Unknown or duplicate design field: " + name);
        }
        if ("composition".equals(name)) {
          values.add(name, readComposition(reader));
          continue;
        }
        if (reader.peek() != JsonToken.NUMBER) {
          throw new IllegalArgumentException("Nonnumeric design field: " + name);
        }
        double value = reader.nextDouble();
        if (!Double.isFinite(value)) {
          throw new IllegalArgumentException("Design values must be finite: " + name);
        }
        values.addProperty(name, Double.valueOf(value));
      }
      reader.endObject();
      if (reader.peek() != JsonToken.END_DOCUMENT) {
        throw new IllegalArgumentException("Trailing content after Design JSON");
      }
    } catch (IOException | IllegalStateException failure) {
      throw new IllegalArgumentException("Invalid Design JSON", failure);
    }
    return GSON.fromJson(values, Design.class);
  }

  /**
   * Reads explicit component mole-fraction inputs without implicit string coercion or duplicate names.
   *
   * @param reader strict design reader positioned at the composition object
   * @return numeric composition object; the native factory validates and normalizes the positive sum
   * @throws IOException if reading fails
   */
  private static JsonObject readComposition(JsonReader reader) throws IOException {
    JsonObject result = new JsonObject();
    Set<String> seen = new LinkedHashSet<String>();
    reader.beginObject();
    while (reader.hasNext()) {
      String name = reader.nextName();
      if (name.trim().isEmpty() || !seen.add(name) || reader.peek() != JsonToken.NUMBER) {
        throw new IllegalArgumentException("Unique component names and numeric mole fractions are required");
      }
      double value = reader.nextDouble();
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Composition must be finite: " + name);
      }
      result.addProperty(name, Double.valueOf(value));
    }
    reader.endObject();
    return result;
  }

  /**
   * Runs the packaged workflow from an agent or command line.
   *
   * @param arguments design JSON path or '-', new output directory, and optional canonical-P&amp;ID JSON path
   * @throws IOException if inputs or outputs are unavailable
   * @throws IllegalArgumentException if argument or governed configuration validation fails
   */
  public static void main(String[] arguments) throws IOException {
    if (arguments == null || arguments.length < 2 || arguments.length > 3) {
      throw new IllegalArgumentException(
          "Usage: CompressorAutomationWorkflow <design.json|-> <new-output-dir> [canonical-pid.json]");
    }
    Design design = "-".equals(arguments[0]) ? new Design()
        : designFromJson(new String(Files.readAllBytes(Paths.get(arguments[0])), StandardCharsets.UTF_8));
    PidDesignModel canonical = arguments.length == 3
        ? PidDesignModel.fromJson(new String(Files.readAllBytes(Paths.get(arguments[2])), StandardCharsets.UTF_8))
        : null;
    Map<String, Object> result = run(design, canonical, defaultCases(), Paths.get(arguments[1]));
    if (((Number) result.get("nativeExecutionErrorCount")).intValue() != 0) {
      throw new IllegalStateException("Native execution errors retained; inspect workflow-status.json");
    }
  }

  /**
   * Builds requirements without creating instrument proposals from requirements.
   *
   * @param train actual healthy source train
   * @return governed synthetic project
   */
  private static EngineeringProject project(Train train) {
    EngineeringProject project = NorsokOffshoreEngineeringBuilder
        .from("Synthetic natural-gas compressor automation", train.getProcess(), "2.0.1").projectId(PROJECT_ID)
        .registerProposedInstruments(false).build().setRevision("A");
    project.getDesignBasis().setJurisdiction("Unspecified synthetic example; applicability review required")
        .setFacilityType("Synthetic natural-gas compressor train")
        .setProjectPhase("Simulation proposal; controlled standards and engineering qualification HOLD");
    return project;
  }

  /**
   * Reads actual steady port states and work/heat duties without constructing a second process model.
   *
   * @param train converged native train
   * @return explicit-unit stream and duty readback
   */
  private static Map<String, Object> steadySnapshot(Train train) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("source", "ACTUAL_CONVERGED_NATIVE_PORTS");
    result.put("operatingSnapshot", train.snapshot());
    result.put("compressorPowerW", Double.valueOf(train.getCompressor().getPower()));
    result.put("coolerDutyW", Double.valueOf(train.getAftercooler().getDuty()));
    Map<String, StreamInterface> ports = new LinkedHashMap<String, StreamInterface>();
    for (ProcessEquipmentInterface unit : train.getProcess().getUnitOperations()) {
      for (StreamInterface port : unit.getInletStreams()) {
        ports.put(port.getName(), port);
      }
      for (StreamInterface port : unit.getOutletStreams()) {
        ports.put(port.getName(), port);
      }
    }
    Map<String, Object> streams = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, StreamInterface> entry : ports.entrySet()) {
      StreamInterface port = entry.getValue();
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("pressureBara", Double.valueOf(port.getPressure("bara")));
      row.put("temperatureK", Double.valueOf(port.getTemperature("K")));
      row.put("massFlowKgPerSecond", Double.valueOf(port.getFlowRate("kg/sec")));
      row.put("molesPerSecond", Double.valueOf(port.getFluid().getTotalNumberOfMoles()));
      Map<String, Object> composition = new LinkedHashMap<String, Object>();
      double[] fractions = port.getFluid().getMolarComposition();
      for (int index = 0; index < fractions.length; index++) {
        composition.put(port.getFluid().getComponent(index).getComponentName(), Double.valueOf(fractions[index]));
      }
      row.put("componentMoleFractions", composition);
      row.put("compositionBasis",
          "NATIVE_OVERALL_MOLE_FRACTIONS; zero-flow intensive state is not transported inventory");
      row.put("nativeStoredEnthalpyW", Double.valueOf(port.getFluid().getEnthalpy()));
      row.put("activeEnthalpyFluxW",
          Double.valueOf(port.getFluid().getTotalNumberOfMoles() > 0.0 ? port.getFluid().getEnthalpy() : 0.0));
      row.put("zeroFlowPolicy",
          "Inactive native zero-molar streams have zero transport flux; stored intensive state is separate");
      streams.put(entry.getKey(), row);
    }
    result.put("streams", streams);
    return result;
  }

  /**
   * Determines manual versus automatic compressor role from actual channel objects.
   *
   * @param function actual registered function
   * @return explicit supported compressor role
   */
  private static String role(ClosedLoopSafetyFunction function) {
    boolean manual = !function.getChannels().isEmpty();
    for (SafetyFunctionChannel channel : function.getChannels()) {
      manual &= channel.getMeasurementSource() instanceof PushButton;
    }
    return manual ? "COMPRESSOR_MANUAL_ESD" : "COMPRESSOR_ESD";
  }

  /**
   * Validates each actual native function with only its declared target fault overrides.
   *
   * @param project governed source
   * @param canonical canonical graph
   * @param bindings current identities
   * @param train isolated configured case prototype or actual runtime copy
   * @param definition declared native case
   * @return ordered receipts keyed by actual native function tag
   */
  private static Map<String, PidSafetyFunctionValidator.ConfiguredFunction> safetyReceipts(EngineeringProject project,
      PidDesignModel canonical, PidSimulationBindings bindings, Train train, CaseDefinition definition) {
    Map<String, PidSafetyFunctionValidator.ConfiguredFunction> result = new LinkedHashMap<String, PidSafetyFunctionValidator.ConfiguredFunction>();
    Map<String, Map<String, Object>> all = targetFaults(train, definition);
    for (ClosedLoopSafetyFunction function : train.getSafetyFunctions()) {
      Map<String, Map<String, Object>> selected = new LinkedHashMap<String, Map<String, Object>>();
      for (ProcessEquipmentInterface target : function.getTargetEquipment()) {
        if (all.containsKey(target.getName())) {
          selected.put(target.getName(), all.get(target.getName()));
        }
      }
      result.put(function.getName(),
          PidSafetyFunctionValidator.validateConfigured(project, canonical, bindings, train.getProcess(),
              function.getName(), function, Collections.<String, SafetyFunctionChannel.FaultMode>emptyMap(), selected));
    }
    return result;
  }

  /**
   * Translates the model's explicit mechanical-fault declarations into native control readback keys.
   *
   * @param train actual named train
   * @param definition explicit model case
   * @return exact allowed per-tag control faults
   */
  private static Map<String, Map<String, Object>> controlFaults(Train train, CaseDefinition definition) {
    Map<String, Object> declared = definition.getFaultOverrides();
    Map<String, Map<String, Object>> result = new LinkedHashMap<String, Map<String, Object>>();
    Set<String> recognized = new LinkedHashSet<String>();
    recognized.add("profile");
    recognized.add("driverCommandAvailable");
    fault(result, declared, recognized, "asvOpeningTravelSeconds", train.getAntiSurgeValve().getName(),
        "openingTravelSeconds");
    fault(result, declared, recognized, "asvActuatorAvailable", train.getAntiSurgeValve().getName(),
        "actuatorAvailable");
    fault(result, declared, recognized, "inletActuatorAvailable", train.getInletValve().getName(), "actuatorAvailable");
    if (!recognized.containsAll(declared.keySet())) {
      throw new IllegalArgumentException(
          "Native case declares a fault unsupported by this workflow: " + declared.keySet());
    }
    return result;
  }

  /**
   * Produces only target fault keys accepted by the safety-policy validator.
   *
   * @param train actual named train
   * @param definition explicit fault declaration
   * @return exact native target faults
   */
  private static Map<String, Map<String, Object>> targetFaults(Train train, CaseDefinition definition) {
    Map<String, Object> declared = definition.getFaultOverrides();
    Map<String, Map<String, Object>> result = new LinkedHashMap<String, Map<String, Object>>();
    Set<String> recognized = new LinkedHashSet<String>();
    fault(result, declared, recognized, "asvActuatorAvailable", train.getAntiSurgeValve().getName(),
        "actuatorAvailable");
    fault(result, declared, recognized, "inletActuatorAvailable", train.getInletValve().getName(), "actuatorAvailable");
    fault(result, declared, recognized, "driverCommandAvailable", train.getCompressor().getName(), "commandAvailable");
    return result;
  }

  /**
   * Copies a named declaration without deriving expected settings from actual faulted hardware.
   *
   * @param result per-tag output
   * @param declared immutable model declarations
   * @param recognized tracked source keys
   * @param source declaration key
   * @param target exact native tag
   * @param setting native policy key
   */
  private static void fault(Map<String, Map<String, Object>> result, Map<String, Object> declared,
      Set<String> recognized, String source, String target, String setting) {
    recognized.add(source);
    if (declared.containsKey(source)) {
      Map<String, Object> values = result.get(target);
      if (values == null) {
        values = new LinkedHashMap<String, Object>();
        result.put(target, values);
      }
      values.put(setting, declared.get(source));
    }
  }

  /**
   * Selects actual required full-window bounds from the controlled native scenario.
   *
   * @param scenario executed definition
   * @return required maintained criterion IDs
   */
  private static Set<String> maintained(DynamicSafetyScenario scenario) {
    Set<String> result = new LinkedHashSet<String>();
    for (DynamicScenarioCriterion criterion : scenario.getCriteria()) {
      if (criterion.isRequired()
          && criterion.getEvaluationMode() == DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS
          && Math.abs(criterion.getDeadlineSeconds()
              - (scenario.getDurationSeconds() - scenario.getTriggerTimeSeconds())) <= 1.0e-9) {
        result.add(criterion.getId());
      }
    }
    return result;
  }

  /**
   * Returns whether every currently public profile was explicitly included.
   *
   * @param cases actual selected cases
   * @return true when every public enum profile is covered
   */
  private static boolean allProfiles(List<CaseDefinition> cases) {
    Set<Profile> found = new LinkedHashSet<Profile>();
    for (CaseDefinition definition : cases) {
      found.add(definition.getProfile());
    }
    return found.size() == Profile.values().length;
  }

  /**
   * Creates a stable filesystem-safe native profile/timestep key.
   *
   * @param definition immutable case
   * @return key preserving profile and exact decimal timestep
   */
  private static String caseKey(CaseDefinition definition) {
    return definition.getProfile().name() + "-dt-" + Double.toString(definition.getTimeStepSeconds());
  }

  /**
   * Creates a one-entry strict-JSON envelope.
   *
   * @param key entry name
   * @param value entry value
   * @return detached ordered map
   */
  private static Map<String, Object> map(String key, Object value) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put(key, value);
    return result;
  }

  /**
   * Writes strict JSON, retaining every non-finite source-number path rather than inventing a finite value.
   *
   * @param file output artifact
   * @param value native detached map/list/primitive tree
   * @throws IOException if writing fails
   */
  private static void write(Path file, Object value) throws IOException {
    List<Map<String, Object>> nonFinite = new ArrayList<Map<String, Object>>();
    Object normalized = normalize(value, "$", nonFinite);
    if (!nonFinite.isEmpty()) {
      Map<String, Object> envelope = new LinkedHashMap<String, Object>();
      if (normalized instanceof Map) {
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) normalized).entrySet()) {
          envelope.put(String.valueOf(entry.getKey()), entry.getValue());
        }
      } else {
        envelope.put("data", normalized);
      }
      envelope.put("nonFiniteValues", nonFinite);
      normalized = envelope;
    }
    Files.write(file, GSON.toJson(normalized).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Copies a JSON-shaped tree while registering undefined or unbounded native floating-point values.
   *
   * @param value current native value
   * @param path exact source path
   * @param nonFinite explicit normalization register
   * @return strict JSON value; non-finite numbers become null, never zero
   */
  private static Object normalize(Object value, String path, List<Map<String, Object>> nonFinite) {
    if (value instanceof Double || value instanceof Float) {
      double number = ((Number) value).doubleValue();
      if (!Double.isFinite(number)) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("path", path);
        row.put("nativeKind", Double.isNaN(number) ? "NaN" : number > 0.0 ? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY");
        row.put("representation", "NULL_NOT_ZERO; consult native validity/reason fields and raw native text");
        nonFinite.add(row);
        return null;
      }
    }
    if (value instanceof Map) {
      Map<String, Object> copy = new LinkedHashMap<String, Object>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        String key = String.valueOf(entry.getKey());
        copy.put(key, normalize(entry.getValue(), path + "." + key, nonFinite));
      }
      return copy;
    }
    if (value instanceof Iterable) {
      List<Object> copy = new ArrayList<Object>();
      for (Object item : (Iterable<?>) value) {
        copy.add(normalize(item, path + "[" + copy.size() + "]", nonFinite));
      }
      return copy;
    }
    if (value != null && value.getClass().isArray()) {
      List<Object> copy = new ArrayList<Object>();
      for (int index = 0; index < Array.getLength(value); index++) {
        copy.add(normalize(Array.get(value, index), path + "[" + index + "]", nonFinite));
      }
      return copy;
    }
    return value;
  }

  /**
   * Records actual loaded bytecodes and locations, independently of a caller-provided source-ref label.
   *
   * @param train actual governed source objects
   * @return per-class byte hashes and code-source locations
   * @throws IOException if loaded bytecodes cannot be inspected
   */
  private static Map<String, Object> runtimeClasses(Train train) throws IOException {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    List<Class<?>> classes = new ArrayList<Class<?>>();
    Collections.addAll(classes, CompressorAutomationWorkflow.class, CompressorAutomationStudy.class,
        PidNativeControlSystem.class, PidSafetyFunctionValidator.class, EngineeringSimulationRunner.class,
        ProfessionalPidPackageExporter.class, train.getProcess().getClass(), train.getCompressor().getClass(),
        train.getAntiSurgeController().getClass(), train.getAntiSurgeValve().getClass(),
        train.getDischargeCheckValve().getClass(), train.getSuctionScrubber().getClass());
    for (Class<?> type : classes) {
      String resource = "/" + type.getName().replace('.', '/') + ".class";
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (InputStream input = type.getResourceAsStream(resource)) {
        if (input == null) {
          throw new IOException("Loaded workflow bytecode is unavailable: " + type.getName());
        }
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
          bytes.write(buffer, 0, count);
        }
      }
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("classBytecodeSha256", sha256(bytes.toByteArray()));
      row.put("codeSource", type.getProtectionDomain().getCodeSource() == null ? null
          : type.getProtectionDomain().getCodeSource().getLocation().toString());
      result.put(type.getName(), row);
    }
    return result;
  }

  /**
   * Hashes the exact supplied byte representation.
   *
   * @param bytes original bytes, before or after normalization as named by the caller
   * @return lowercase SHA-256
   */
  private static String sha256(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder result = new StringBuilder();
      for (byte value : digest) {
        result.append(String.format("%02x", Integer.valueOf(value & 0xff)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 is unavailable", failure);
    }
  }

  /**
   * Runtime case records containing exact owned bundles and frozen configuration receipts.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class CaseExecution {
    private final CaseDefinition definition;
    private final CaseBundle bundle;
    private final PidNativeControlSystem.ConfiguredControlSystem control;
    private final Map<String, PidSafetyFunctionValidator.ConfiguredFunction> functions;

    /**
     * Retains one declared case without cloning or rebuilding its process graph.
     *
     * @param definition frozen timing and receipt references
     * @param bundle actual native scenario factory
     * @param control frozen control configuration
     * @param functions frozen actual safety-function configurations
     */
    private CaseExecution(CaseDefinition definition, CaseBundle bundle,
        PidNativeControlSystem.ConfiguredControlSystem control,
        Map<String, PidSafetyFunctionValidator.ConfiguredFunction> functions) {
      this.definition = definition;
      this.bundle = bundle;
      this.control = control;
      this.functions = functions;
    }
  }

  /**
   * Runtime-only readback verifier. Serialized observers deliberately cannot receive execution credit.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class ReceiptObserver implements CompressorAutomationStudy.ConfigurationObserver {
    private static final long serialVersionUID = 1L;
    private final transient EngineeringProject project;
    private final transient PidDesignModel canonical;
    private final transient PidSimulationBindings bindings;
    private final transient PidNativeControlSystem.ConfiguredControlSystem control;
    private final transient Map<String, PidSafetyFunctionValidator.ConfiguredFunction> functions;

    /**
     * Retains live runtime identities and detached receipts for inspection only.
     *
     * @param project governed healthy source
     * @param canonical frozen proposal
     * @param bindings current identities
     * @param control expected control receipt
     * @param functions expected safety receipts
     */
    private ReceiptObserver(EngineeringProject project, PidDesignModel canonical, PidSimulationBindings bindings,
        PidNativeControlSystem.ConfiguredControlSystem control,
        Map<String, PidSafetyFunctionValidator.ConfiguredFunction> functions) {
      this.project = project;
      this.canonical = canonical;
      this.bindings = bindings;
      this.control = control;
      this.functions = functions;
    }

    /** {@inheritDoc} */
    @Override
    public void inspect(Train train, CaseDefinition definition) {
      if (project == null || canonical == null || bindings == null || control == null || functions == null) {
        throw new IllegalStateException("A deserialized runtime-only verifier must be rebuilt from governed inputs");
      }
      control.assertSameConfiguration(PidNativeControlSystem.validateConfigured(project, canonical, bindings,
          train.getProcess(), controlFaults(train, definition)));
      Map<String, PidSafetyFunctionValidator.ConfiguredFunction> actual = safetyReceipts(project, canonical, bindings,
          train, definition);
      if (!actual.keySet().equals(functions.keySet())) {
        throw new IllegalStateException("The runtime copy has different native safety functions");
      }
      for (String tag : functions.keySet()) {
        functions.get(tag).assertSameConfiguration(actual.get(tag));
      }
    }
  }
}
