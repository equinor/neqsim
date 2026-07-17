package neqsim.process.engineering.production;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
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
    if (projectName == null || projectName.trim().isEmpty() || model == null || configurations == null) {
      throw new IllegalArgumentException("projectName, model and configurations are required");
    }
    Map<String, AreaResult> areas = new LinkedHashMap<String, AreaResult>();
    List<String> blockers = new ArrayList<String>();
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
        EngineeringSimulationResult simulation = ProcessToEngineeringSimulator.run(project, configuration.policy,
            caseParallelism);
        areas.put(areaName, new AreaResult(project, simulation,
            project.getProductionReadinessBasis().getAutoConfigurationResult()));
      } catch (RuntimeException exception) {
        blockers.add("AREA_EXECUTION_FAILED:" + areaName + ":" + exception.getMessage());
      }
    }
    List<Map<String, Object>> sharedStreams = sharedStreamDependencies(model);
    return new Result(areas, sharedStreams, blockers);
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
    private final List<String> blockers;
    private final String fingerprint;

    Result(Map<String, AreaResult> areas, List<Map<String, Object>> sharedStreamDependencies, List<String> blockers) {
      this.areas = Collections.unmodifiableMap(new LinkedHashMap<String, AreaResult>(areas));
      this.sharedStreamDependencies = Collections
          .unmodifiableList(new ArrayList<Map<String, Object>>(sharedStreamDependencies));
      this.blockers = Collections.unmodifiableList(new ArrayList<String>(blockers));
      this.fingerprint = fingerprint(areas, sharedStreamDependencies);
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

    public String getFingerprint() {
      return fingerprint;
    }

    /** Compiles one governed package per area plus a process-model coordination manifest. */
    public Path compile(Path outputDirectory) throws IOException {
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
      Path file = outputDirectory.resolve("process-model-engineering-manifest.json");
      Files.write(file, GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
      return file;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "neqsim_process_model_engineering_manifest.v1");
      result.put("complete", Boolean.valueOf(isComplete()));
      result.put("blockers", blockers);
      result.put("fingerprint", fingerprint);
      result.put("sharedStreamDependencies", sharedStreamDependencies);
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

  private static String fingerprint(Map<String, AreaResult> areas, List<Map<String, Object>> sharedStreams) {
    StringBuilder value = new StringBuilder();
    for (Map.Entry<String, AreaResult> entry : areas.entrySet()) {
      value.append(entry.getKey()).append(':').append(entry.getValue().configuration.getConfigurationFingerprint())
          .append(';');
    }
    value.append(GSON.toJson(sharedStreams));
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8));
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
