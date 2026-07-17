package neqsim.process.engineering.production;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringSimulationResult;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.ProcessToEngineeringSimulator;
import neqsim.process.engineering.deliverables.EngineeringDeliverableCompiler;
import neqsim.process.engineering.design.EngineeringDesignValue;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/** Coordinates explicit process-to-engineering policies across every area in a {@link ProcessModel}. */
public final class ProcessModelEngineeringSimulator {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private ProcessModelEngineeringSimulator() {
  }

  /** Controlled configuration for one process area. */
  public static final class AreaConfiguration implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final EngineeringAutoConfigurationPolicy policy;
    private final List<EngineeringDesignCase> designCases = new ArrayList<EngineeringDesignCase>();
    private boolean registerProposedInstruments;

    public AreaConfiguration(EngineeringAutoConfigurationPolicy policy) {
      if (policy == null) {
        throw new IllegalArgumentException("policy must not be null");
      }
      this.policy = policy;
    }

    public AreaConfiguration addDesignCase(EngineeringDesignCase designCase) {
      if (designCase == null) {
        throw new IllegalArgumentException("designCase must not be null");
      }
      designCases.add(designCase);
      return this;
    }

    public AreaConfiguration registerProposedInstruments(boolean value) {
      registerProposedInstruments = value;
      return this;
    }
  }

  /** Runs every configured area and records conservative dependencies created by shared stream identities. */
  public static Result run(String projectName, ProcessModel model, Map<String, AreaConfiguration> configurations,
      int caseParallelism) {
    return runIncremental(projectName, model, configurations, new EngineeringSharedSystemPolicy("topology-only", "1"),
        null, caseParallelism);
  }

  /** Runs every area with explicit shared-system demand and concurrency inputs. */
  public static Result run(String projectName, ProcessModel model, Map<String, AreaConfiguration> configurations,
      EngineeringSharedSystemPolicy sharedSystemPolicy, int caseParallelism) {
    return runIncremental(projectName, model, configurations, sharedSystemPolicy, null, caseParallelism);
  }

  /**
   * Reruns changed areas and all connected dependants while reusing unchanged results from a controlled baseline.
   */
  public static Result runIncremental(String projectName, ProcessModel model,
      Map<String, AreaConfiguration> configurations, EngineeringSharedSystemPolicy sharedSystemPolicy, Result baseline,
      int caseParallelism) {
    if (projectName == null || projectName.trim().isEmpty() || model == null || configurations == null) {
      throw new IllegalArgumentException("projectName, model and configurations are required");
    }
    if (sharedSystemPolicy == null) {
      throw new IllegalArgumentException("sharedSystemPolicy must not be null");
    }
    List<Map<String, Object>> sharedStreams = sharedStreamDependencies(model);
    String coordinationFingerprint = coordinationFingerprint(sharedSystemPolicy, sharedStreams);
    Map<String, PreparedArea> prepared = new LinkedHashMap<String, PreparedArea>();
    Map<String, AreaResult> areas = new LinkedHashMap<String, AreaResult>();
    List<String> blockers = new ArrayList<String>();
    Set<String> invalidatedAreas = new LinkedHashSet<String>();
    for (String areaName : model.getProcessSystemNames()) {
      AreaConfiguration configuration = configurations.get(areaName);
      if (configuration == null) {
        blockers.add("MISSING_AREA_CONFIGURATION:" + areaName);
        continue;
      }
      ProcessSystem process = model.get(areaName).copy();
      EngineeringProject project = NorsokOffshoreEngineeringBuilder.from(projectName + " - " + areaName, process)
          .projectId(projectName.replaceAll("[^A-Za-z0-9_-]", "-") + "-" + areaName)
          .registerProposedInstruments(configuration.registerProposedInstruments).build();
      for (EngineeringDesignCase designCase : configuration.designCases) {
        project.addDesignCase(designCase);
      }
      try {
        EngineeringAutoConfigurator.Result configured = EngineeringAutoConfigurator.configure(project,
            configuration.policy);
        EngineeringProductionReadinessBasis readiness = new EngineeringProductionReadinessBasis()
            .autoConfigurationResult(configured);
        project.setProductionReadinessBasis(readiness);
        if (!configured.isExecutionReady()) {
          blockers.add("AREA_NOT_EXECUTION_READY:" + areaName + ":" + configured.getExecutionBlockers());
          continue;
        }
        prepared.put(areaName, new PreparedArea(project, configured));
        AreaResult baselineArea = baseline == null ? null : baseline.areas.get(areaName);
        if (baselineArea == null || !configured.getConfigurationFingerprint()
            .equals(baselineArea.configuration.getConfigurationFingerprint())) {
          invalidatedAreas.add(areaName);
        }
      } catch (RuntimeException exception) {
        blockers.add("AREA_CONFIGURATION_FAILED:" + areaName + ":" + exception.getMessage());
      }
    }
    if (baseline == null || !coordinationFingerprint.equals(baseline.coordinationFingerprint)) {
      invalidatedAreas.addAll(prepared.keySet());
    }
    propagateInvalidation(invalidatedAreas, dependencyGroups(sharedStreams, sharedSystemPolicy));

    Set<String> executedAreas = new LinkedHashSet<String>();
    Set<String> reusedAreas = new LinkedHashSet<String>();
    for (Map.Entry<String, PreparedArea> entry : prepared.entrySet()) {
      String areaName = entry.getKey();
      if (!invalidatedAreas.contains(areaName) && baseline != null && baseline.areas.containsKey(areaName)) {
        areas.put(areaName, baseline.areas.get(areaName));
        reusedAreas.add(areaName);
        continue;
      }
      try {
        EngineeringSimulationResult simulation = ProcessToEngineeringSimulator.run(entry.getValue().project,
            caseParallelism);
        areas.put(areaName, new AreaResult(entry.getValue().project, simulation, entry.getValue().configuration));
        executedAreas.add(areaName);
      } catch (RuntimeException exception) {
        blockers.add("AREA_EXECUTION_FAILED:" + areaName + ":" + exception.getMessage());
      }
    }
    List<Map<String, Object>> sharedSystemResults = evaluateSharedSystems(sharedSystemPolicy, areas, blockers);
    return new Result(areas, sharedStreams, sharedSystemPolicy, sharedSystemResults, executedAreas, reusedAreas,
        blockers, coordinationFingerprint);
  }

  private static final class PreparedArea {
    private final EngineeringProject project;
    private final EngineeringAutoConfigurator.Result configuration;

    PreparedArea(EngineeringProject project, EngineeringAutoConfigurator.Result configuration) {
      this.project = project;
      this.configuration = configuration;
    }
  }

  private static List<Map<String, Object>> sharedStreamDependencies(ProcessModel model) {
    Map<StreamInterface, Set<String>> producers = new IdentityHashMap<StreamInterface, Set<String>>();
    Map<StreamInterface, Set<String>> consumers = new IdentityHashMap<StreamInterface, Set<String>>();
    for (String areaName : model.getProcessSystemNames()) {
      for (ProcessEquipmentInterface unit : model.get(areaName).getUnitOperations()) {
        if (unit == null) {
          continue;
        }
        addArea(producers, unit.getOutletStreams(), areaName);
        addArea(consumers, unit.getInletStreams(), areaName);
      }
    }
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    Set<StreamInterface> all = Collections.newSetFromMap(new IdentityHashMap<StreamInterface, Boolean>());
    all.addAll(producers.keySet());
    all.addAll(consumers.keySet());
    for (StreamInterface stream : all) {
      Set<String> sourceAreas = producers.containsKey(stream) ? producers.get(stream) : Collections.<String>emptySet();
      Set<String> targetAreas = consumers.containsKey(stream) ? consumers.get(stream) : Collections.<String>emptySet();
      Set<String> combined = new LinkedHashSet<String>();
      combined.addAll(sourceAreas);
      combined.addAll(targetAreas);
      if (combined.size() < 2) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("stream", stream.getName());
      row.put("sourceAreas", new ArrayList<String>(sourceAreas));
      row.put("targetAreas", new ArrayList<String>(targetAreas));
      row.put("areas", new ArrayList<String>(combined));
      row.put("coordinationStatus", "REVIEW_REQUIRED");
      row.put("invalidationRule", "CHANGE_INVALIDATES_ALL_CONNECTED_AREAS");
      result.add(row);
    }
    Collections.sort(result, new Comparator<Map<String, Object>>() {
      @Override
      public int compare(Map<String, Object> left, Map<String, Object> right) {
        return String.valueOf(left.get("stream")).compareTo(String.valueOf(right.get("stream")));
      }
    });
    return result;
  }

  private static void addArea(Map<StreamInterface, Set<String>> target, List<StreamInterface> streams,
      String areaName) {
    for (StreamInterface stream : streams) {
      if (stream == null) {
        continue;
      }
      Set<String> areas = target.get(stream);
      if (areas == null) {
        areas = new LinkedHashSet<String>();
        target.put(stream, areas);
      }
      areas.add(areaName);
    }
  }

  private static List<Set<String>> dependencyGroups(List<Map<String, Object>> sharedStreams,
      EngineeringSharedSystemPolicy policy) {
    List<Set<String>> result = new ArrayList<Set<String>>();
    for (Map<String, Object> sharedStream : sharedStreams) {
      Set<String> group = new LinkedHashSet<String>();
      Object areas = sharedStream.get("areas");
      if (areas instanceof Iterable<?>) {
        for (Object area : (Iterable<?>) areas) {
          group.add(String.valueOf(area));
        }
      }
      if (group.size() > 1) {
        result.add(group);
      }
    }
    for (EngineeringSharedSystemPolicy.Definition definition : policy.getDefinitions()) {
      if (definition.getAreaNames().size() > 1) {
        result.add(new LinkedHashSet<String>(definition.getAreaNames()));
      }
    }
    return result;
  }

  private static void propagateInvalidation(Set<String> invalidatedAreas, List<Set<String>> groups) {
    boolean changed;
    do {
      changed = false;
      for (Set<String> group : groups) {
        if (!Collections.disjoint(group, invalidatedAreas)) {
          changed |= invalidatedAreas.addAll(group);
        }
      }
    } while (changed);
  }

  private static List<Map<String, Object>> evaluateSharedSystems(EngineeringSharedSystemPolicy policy,
      Map<String, AreaResult> areas, List<String> blockers) {
    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
    for (EngineeringSharedSystemPolicy.Definition definition : policy.getDefinitions()) {
      Map<String, Object> row = new LinkedHashMap<String, Object>(definition.toMap());
      List<Map<String, Object>> contributions = new ArrayList<Map<String, Object>>();
      double simultaneousDemand = 0.0;
      String commonUnit = "";
      boolean valid = definition.getAreaNames().size() >= 2;
      if (!valid) {
        blockers.add("SHARED_SYSTEM_REQUIRES_AT_LEAST_TWO_DEMANDS:" + definition.getId());
      }
      for (EngineeringSharedSystemPolicy.Demand demand : definition.getDemands()) {
        AreaResult area = areas.get(demand.getAreaName());
        EngineeringDesignValue designValue = area == null || area.simulation.getEngineeringDesignLoopResult() == null
            ? null
            : area.simulation.getEngineeringDesignLoopResult().getState().getValues().get(demand.getDesignVariable());
        if (designValue == null) {
          blockers.add("SHARED_SYSTEM_DEMAND_NOT_AVAILABLE:" + definition.getId() + ":" + demand.getAreaName() + ":"
              + demand.getDesignVariable());
          valid = false;
          continue;
        }
        if (commonUnit.isEmpty()) {
          commonUnit = designValue.getUnit();
        } else if (!commonUnit.equals(designValue.getUnit())) {
          blockers.add("SHARED_SYSTEM_UNIT_MISMATCH:" + definition.getId());
          valid = false;
        }
        double contribution = designValue.getValue() * demand.getSimultaneityFactor();
        simultaneousDemand += contribution;
        Map<String, Object> contributionRow = new LinkedHashMap<String, Object>(demand.toMap());
        contributionRow.put("governingValue", Double.valueOf(designValue.getValue()));
        contributionRow.put("unit", designValue.getUnit());
        contributionRow.put("simultaneousContribution", Double.valueOf(contribution));
        contributions.add(contributionRow);
      }
      row.put("contributions", contributions);
      row.put("simultaneousDemand", valid ? Double.valueOf(simultaneousDemand) : null);
      row.put("unit", commonUnit);
      row.put("calculationStatus", valid ? "CALCULATED_REVIEW_REQUIRED" : "BLOCKED");
      results.add(row);
    }
    return results;
  }

  private static String coordinationFingerprint(EngineeringSharedSystemPolicy policy,
      List<Map<String, Object>> sharedStreams) {
    return sha256(policy.fingerprintMaterial() + "|" + GSON.toJson(sharedStreams));
  }

  /** Immutable result for one area. */
  public static final class AreaResult {
    private final EngineeringProject project;
    private final EngineeringSimulationResult simulation;
    private final EngineeringAutoConfigurator.Result configuration;

    AreaResult(EngineeringProject project, EngineeringSimulationResult simulation,
        EngineeringAutoConfigurator.Result configuration) {
      this.project = project;
      this.simulation = simulation;
      this.configuration = configuration;
    }

    public EngineeringProject getProject() {
      return project;
    }

    public EngineeringSimulationResult getSimulation() {
      return simulation;
    }

    public EngineeringAutoConfigurator.Result getConfiguration() {
      return configuration;
    }
  }

  /** Coordinated multi-area result and package compiler. */
  public static final class Result {
    private final Map<String, AreaResult> areas;
    private final List<Map<String, Object>> sharedStreamDependencies;
    private final EngineeringSharedSystemPolicy sharedSystemPolicy;
    private final List<Map<String, Object>> sharedSystemResults;
    private final Set<String> executedAreas;
    private final Set<String> reusedAreas;
    private final List<String> blockers;
    private final String coordinationFingerprint;
    private final String fingerprint;

    Result(Map<String, AreaResult> areas, List<Map<String, Object>> sharedStreamDependencies,
        EngineeringSharedSystemPolicy sharedSystemPolicy, List<Map<String, Object>> sharedSystemResults,
        Set<String> executedAreas, Set<String> reusedAreas, List<String> blockers, String coordinationFingerprint) {
      this.areas = Collections.unmodifiableMap(new LinkedHashMap<String, AreaResult>(areas));
      this.sharedStreamDependencies = Collections
          .unmodifiableList(new ArrayList<Map<String, Object>>(sharedStreamDependencies));
      this.sharedSystemPolicy = sharedSystemPolicy;
      this.sharedSystemResults = Collections.unmodifiableList(new ArrayList<Map<String, Object>>(sharedSystemResults));
      this.executedAreas = Collections.unmodifiableSet(new LinkedHashSet<String>(executedAreas));
      this.reusedAreas = Collections.unmodifiableSet(new LinkedHashSet<String>(reusedAreas));
      this.blockers = Collections.unmodifiableList(new ArrayList<String>(blockers));
      this.coordinationFingerprint = coordinationFingerprint;
      this.fingerprint = fingerprint(areas, sharedStreamDependencies, sharedSystemResults, coordinationFingerprint);
    }

    public boolean isComplete() {
      return !areas.isEmpty() && blockers.isEmpty();
    }

    public Map<String, AreaResult> getAreas() {
      return areas;
    }

    public List<String> getBlockers() {
      return blockers;
    }

    public List<Map<String, Object>> getSharedStreamDependencies() {
      return sharedStreamDependencies;
    }

    public List<Map<String, Object>> getSharedSystemResults() {
      return sharedSystemResults;
    }

    public Set<String> getExecutedAreas() {
      return executedAreas;
    }

    public Set<String> getReusedAreas() {
      return reusedAreas;
    }

    public String getCoordinationFingerprint() {
      return coordinationFingerprint;
    }

    public String getFingerprint() {
      return fingerprint;
    }

    /** Compiles one governed package per area plus a process-model coordination manifest. */
    public Path compile(Path outputDirectory) throws IOException {
      if (!isComplete()) {
        throw new IllegalStateException("Cannot compile an incomplete multi-area engineering result: " + blockers);
      }
      Files.createDirectories(outputDirectory);
      Map<String, Object> packages = new LinkedHashMap<String, Object>();
      for (Map.Entry<String, AreaResult> entry : areas.entrySet()) {
        Path areaDirectory = outputDirectory.resolve(entry.getKey());
        EngineeringDeliverableCompiler.CompilationResult compilation = EngineeringDeliverableCompiler
            .compile(entry.getValue().project, areaDirectory);
        Map<String, Object> packageRow = new LinkedHashMap<String, Object>();
        packageRow.put("directory", entry.getKey());
        packageRow.put("graph", outputDirectory.relativize(compilation.getEngineeringGraphFile()).toString());
        packageRow.put("dexpi", outputDirectory.relativize(compilation.getDexpiResult().getDexpi20File()).toString());
        packageRow.put("configurationFingerprint", entry.getValue().configuration.getConfigurationFingerprint());
        packages.put(entry.getKey(), packageRow);
      }
      Map<String, Object> manifest = toMap();
      manifest.put("areaPackages", packages);
      ProcessModelEngineeringPackageValidator.validateOrThrow(manifest);
      Path schemaDirectory = outputDirectory.resolve("schemas");
      Files.createDirectories(schemaDirectory);
      Path schemaFile = schemaDirectory.resolve("process-model-engineering-manifest.schema.json");
      InputStream schema = ProcessModelEngineeringSimulator.class
          .getResourceAsStream("/neqsim/process/engineering/schema/process-model-engineering-manifest.schema.json");
      if (schema == null) {
        throw new IOException("Bundled process-model engineering manifest schema is missing");
      }
      try {
        Files.copy(schema, schemaFile, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        schema.close();
      }
      Path file = outputDirectory.resolve("process-model-engineering-manifest.json");
      Files.write(file, GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
      return file;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "neqsim_process_model_engineering_manifest.v1");
      result.put("schemaUri", "urn:neqsim:schema:process-model-engineering-manifest:v1");
      result.put("complete", Boolean.valueOf(isComplete()));
      result.put("blockers", blockers);
      result.put("fingerprint", fingerprint);
      result.put("coordinationPolicyId", sharedSystemPolicy.getId());
      result.put("coordinationPolicyRevision", sharedSystemPolicy.getRevision());
      result.put("coordinationFingerprint", coordinationFingerprint);
      result.put("executedAreas", new ArrayList<String>(executedAreas));
      result.put("reusedAreas", new ArrayList<String>(reusedAreas));
      result.put("sharedStreamDependencies", sharedStreamDependencies);
      result.put("sharedSystemResults", sharedSystemResults);
      Map<String, Object> areaRows = new LinkedHashMap<String, Object>();
      for (Map.Entry<String, AreaResult> entry : areas.entrySet()) {
        areaRows.put(entry.getKey(), entry.getValue().configuration.toMap());
      }
      result.put("areas", areaRows);
      result.put("governance",
          "Shared-system concurrency, HAZOP/LOPA decisions and final discipline approvals remain controlled inputs");
      return result;
    }
  }

  private static String fingerprint(Map<String, AreaResult> areas, List<Map<String, Object>> sharedStreams,
      List<Map<String, Object>> sharedSystems, String coordinationFingerprint) {
    StringBuilder value = new StringBuilder();
    for (Map.Entry<String, AreaResult> entry : areas.entrySet()) {
      value.append(entry.getKey()).append(':').append(entry.getValue().configuration.getConfigurationFingerprint())
          .append(';');
    }
    value.append(GSON.toJson(sharedStreams)).append(GSON.toJson(sharedSystems)).append(coordinationFingerprint);
    return sha256(value.toString());
  }

  private static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte item : digest) {
        hex.append(String.format("%02x", item & 0xff));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
    }
  }
}
