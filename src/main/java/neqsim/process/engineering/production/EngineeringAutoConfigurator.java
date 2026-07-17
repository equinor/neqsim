package neqsim.process.engineering.production;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.ProcessToEngineeringDesignBuilder;
import neqsim.process.engineering.design.EngineeringDesignModule;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

/** Applies explicit project policy and reports every recognized process item that remains unconfigured. */
public final class EngineeringAutoConfigurator {
  private EngineeringAutoConfigurator() {
  }

  public static Result configure(EngineeringProject project, EngineeringAutoConfigurationPolicy policy) {
    if (project == null || policy == null) {
      throw new IllegalArgumentException("project and policy must not be null");
    }
    ProcessToEngineeringDesignBuilder builder = ProcessToEngineeringDesignBuilder.on(project);
    Set<String> configuredTags = existingConfiguredTags(project);
    boolean hiddenDefaultsUsed = false;
    for (EngineeringAutoConfigurationPolicy.SliceRule rule : policy.getSlices()) {
      hiddenDefaultsUsed |= !rule.explicitDesignInputs;
      int configuredCoreItems = (configuredTags.contains(rule.separatorTag) ? 1 : 0)
          + (configuredTags.contains(rule.compressorTag) ? 1 : 0) + (configuredTags.contains(rule.lineTag) ? 1 : 0);
      if (configuredCoreItems > 0 && configuredCoreItems < 3) {
        throw new IllegalStateException(
            "Partially configured inlet/compression/export slice must be resolved before " + "automatic configuration");
      }
      if (configuredCoreItems == 0) {
        if (rule.explicitDesignInputs) {
          builder
              .separatorBasis(rule.separatorLiquidDensityKgM3, rule.separatorSoudersBrownCoefficient,
                  rule.separatorLiquidRetentionTimeSeconds)
              .exportLineLimits(rule.maximumLineVelocityMPerS, rule.maximumPressureGradientBarPerKm)
              .compressorDrivers(rule.compressorDriverMarginFraction, rule.compressorDriverCandidatesKw);
        }
        builder.addInletCompressionExportSlice(rule.separatorTag, rule.compressorTag, rule.lineTag, rule.valveTag,
            rule.instrumentTag);
      }
      configuredTags.add(rule.separatorTag);
      configuredTags.add(rule.compressorTag);
      configuredTags.add(rule.lineTag);
      if (!rule.valveTag.isEmpty()) {
        configuredTags.add(rule.valveTag);
      }
      if (!rule.instrumentTag.isEmpty()) {
        configuredTags.add(rule.instrumentTag);
      }
    }
    for (EngineeringAutoConfigurationPolicy.PumpRule rule : policy.getPumps()) {
      if (!configuredTags.contains(rule.tag)) {
        builder.addPumpDesign(rule.tag, rule.margin, rule.minimumNpshMarginM, rule.drivers);
      }
      configuredTags.add(rule.tag);
    }
    for (EngineeringAutoConfigurationPolicy.HeatExchangerRule rule : policy.getExchangers()) {
      if (!configuredTags.contains(rule.tag)) {
        builder.addHeatExchangerDesign(rule.tag, rule.overallU, rule.lmtd, rule.margin, rule.areas);
      }
      configuredTags.add(rule.tag);
    }
    for (EngineeringAutoConfigurationPolicy.InventoryRule rule : policy.getInventories()) {
      if (!configuredTags.contains(rule.tag)) {
        builder.addInventoryDesign(rule.tag, rule.workingTime, rule.usableFraction, rule.volumes);
      }
      configuredTags.add(rule.tag);
    }
    for (EngineeringAutoConfigurationPolicy.ControlValveRule rule : policy.getControlValves()) {
      if (!configuredTags.contains(rule.tag)) {
        builder.addControlValveDesign(rule.tag, rule.designOpening, rule.maximumOpening, rule.cvCandidates);
      }
      configuredTags.add(rule.tag);
    }
    for (EngineeringAutoConfigurationPolicy.RatedCapacityRule rule : policy.getRatedCapacities()) {
      if (!configuredTags.contains(rule.tag)) {
        builder.addRatedCapacity(rule.tag, rule.metric, rule.capacityName, rule.unit, rule.margin, rule.candidates);
      }
      configuredTags.add(rule.tag);
    }
    for (EngineeringAutoConfigurationPolicy.NetworkRule rule : policy.getNetworks()) {
      boolean missing = false;
      for (neqsim.process.engineering.design.modules.PipingNetworkDesignModule.SegmentDefinition segment : rule.segments) {
        missing |= !configuredTags.contains(segment.getLineTag());
      }
      if (missing) {
        builder.addPipingNetworkDesign(rule.id, rule.rulePack, rule.segments);
      }
      for (neqsim.process.engineering.design.modules.PipingNetworkDesignModule.SegmentDefinition segment : rule.segments) {
        configuredTags.add(segment.getLineTag());
      }
    }
    List<Map<String, Object>> inventory = new ArrayList<Map<String, Object>>();
    List<String> unconfigured = new ArrayList<String>();
    for (ProcessEquipmentInterface unit : project.getEngineeringProcessSystem().getUnitOperations()) {
      if (unit == null || unit instanceof StreamInterface || !recognized(unit)) {
        continue;
      }
      boolean configured = configuredTags.contains(unit.getName());
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("equipmentTag", unit.getName());
      row.put("javaClass", unit.getClass().getName());
      row.put("family", family(unit));
      row.put("configured", Boolean.valueOf(configured));
      row.put("action", configured ? "NONE" : "ADD_EXPLICIT_PROJECT_POLICY_RULE");
      row.put("simulatedOperatingState", finiteState(unit));
      row.put("operatingStateSource", "PROCESS_MODEL_PRIMARY_OUTLET");
      inventory.add(row);
      if (!configured) {
        unconfigured.add(unit.getName());
      }
    }
    Map<String, List<String>> dependencies = dependencyGraph(project.getEngineeringDesignModules());
    List<String> blockers = new ArrayList<String>();
    if (project.getExecutableDesignCases().isEmpty()) {
      blockers.add("NO_EXECUTABLE_DESIGN_CASES");
    }
    if (project.getEngineeringDesignModules().isEmpty()) {
      blockers.add("NO_ENGINEERING_DESIGN_MODULES");
    }
    if (hiddenDefaultsUsed) {
      blockers.add("HIDDEN_SCREENING_DEFAULTS_USED");
    }
    for (String tag : unconfigured) {
      blockers.add("UNCONFIGURED_RECOGNIZED_EQUIPMENT:" + tag);
    }
    String fingerprint = fingerprint(project, policy, dependencies);
    return new Result(policy.getId(), policy.getRevision(), configuredTags, unconfigured, inventory,
        project.getEngineeringDesignModules().size(), hiddenDefaultsUsed, dependencies, blockers, fingerprint);
  }

  private static Map<String, Map<String, Object>> finiteState(ProcessEquipmentInterface unit) {
    Map<String, Map<String, Object>> result = new LinkedHashMap<String, Map<String, Object>>();
    for (Map.Entry<String, Map<String, Object>> entry : unit.getEquipmentState("C", "bara", "kg/hr").entrySet()) {
      Object value = entry.getValue().get("value");
      if (!(value instanceof Number) || Double.isFinite(((Number) value).doubleValue())) {
        result.put(entry.getKey(), new LinkedHashMap<String, Object>(entry.getValue()));
      }
    }
    return result;
  }

  private static Map<String, List<String>> dependencyGraph(List<EngineeringDesignModule> modules) {
    Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
    for (EngineeringDesignModule module : modules) {
      List<String> prerequisites = new ArrayList<String>();
      int rank = disciplineRank(module.getId());
      for (EngineeringDesignModule candidate : modules) {
        int candidateRank = disciplineRank(candidate.getId());
        if (candidateRank < rank && candidateRank >= Math.max(10, rank - 30)) {
          prerequisites.add(candidate.getId());
        }
      }
      result.put(module.getId(), Collections.unmodifiableList(prerequisites));
    }
    return Collections.unmodifiableMap(result);
  }

  private static int disciplineRank(String moduleId) {
    if (moduleId != null && moduleId.length() >= 2) {
      try {
        return Integer.parseInt(moduleId.substring(0, 2));
      } catch (NumberFormatException ignored) {
        // Non-numbered project modules execute after the standard discipline chain.
      }
    }
    return 99;
  }

  private static String fingerprint(EngineeringProject project, EngineeringAutoConfigurationPolicy policy,
      Map<String, List<String>> dependencies) {
    StringBuilder canonical = new StringBuilder();
    canonical.append(policy.fingerprintMaterial()).append('|').append(project.getRevision()).append('|');
    for (ProcessEquipmentInterface unit : project.getProcessSystem().getUnitOperations()) {
      if (unit != null) {
        canonical.append(unit.getName()).append(':').append(unit.getClass().getName()).append(':')
            .append(finiteState(unit)).append(';');
      }
    }
    for (neqsim.process.engineering.designcase.EngineeringDesignCase designCase : project.getExecutableDesignCases()) {
      canonical.append("case:").append(designCase.toMap()).append(';');
    }
    for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
      canonical.append("module:").append(entry.getKey()).append("<-").append(entry.getValue()).append(';');
    }
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte value : digest) {
        hex.append(String.format("%02x", value & 0xff));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
    }
  }

  private static Set<String> existingConfiguredTags(EngineeringProject project) {
    Set<String> result = new LinkedHashSet<String>();
    if (project.getProductionReadinessBasis() != null
        && project.getProductionReadinessBasis().getAutoConfigurationResult() != null) {
      result.addAll(project.getProductionReadinessBasis().getAutoConfigurationResult().getConfiguredTags());
    }
    for (EngineeringDesignModule module : project.getEngineeringDesignModules()) {
      for (ProcessEquipmentInterface unit : project.getEngineeringProcessSystem().getUnitOperations()) {
        if (unit != null && primaryModuleConfigured(module.getId(), unit)) {
          result.add(unit.getName());
        }
      }
    }
    return result;
  }

  private static boolean primaryModuleConfigured(String moduleId, ProcessEquipmentInterface unit) {
    String family = family(unit);
    String prefix = "";
    if ("SEPARATOR_OR_SCRUBBER".equals(family)) {
      prefix = "10-separator-process-design-";
    } else if ("COMPRESSOR".equals(family)) {
      prefix = "30-compressor-package-design-";
    } else if ("PUMP".equals(family)) {
      prefix = "31-pump-package-design-";
    } else if ("HEAT_EXCHANGER_OR_HEATER".equals(family)) {
      prefix = "32-heat-exchanger-design-";
    } else if ("TANK_OR_VESSEL".equals(family)) {
      prefix = "15-inventory-design-";
    } else if ("PIPING".equals(family)) {
      prefix = "20-line-hydraulics-";
    } else if ("VALVE".equals(family)) {
      prefix = "40-control-valve-design-";
    } else if ("COLUMN_OR_ABSORBER".equals(family)) {
      return moduleId.startsWith("35-rated-capacity-" + unit.getName() + "-");
    }
    return !prefix.isEmpty() && moduleId.equals(prefix + unit.getName());
  }

  private static boolean recognized(ProcessEquipmentInterface unit) {
    return !family(unit).equals("UNRECOGNIZED");
  }

  private static String family(ProcessEquipmentInterface unit) {
    String name = unit.getClass().getSimpleName().toLowerCase();
    if (name.contains("separator") || name.contains("scrubber")) {
      return "SEPARATOR_OR_SCRUBBER";
    }
    if (name.contains("compressor")) {
      return "COMPRESSOR";
    }
    if (name.contains("pump")) {
      return "PUMP";
    }
    if (name.contains("heater") || name.contains("cooler") || name.contains("exchanger")) {
      return "HEAT_EXCHANGER_OR_HEATER";
    }
    if (name.contains("column") || name.contains("absorber")) {
      return "COLUMN_OR_ABSORBER";
    }
    if (name.contains("tank") || name.contains("vessel")) {
      return "TANK_OR_VESSEL";
    }
    if (name.contains("pipe") || name.contains("pipeline")) {
      return "PIPING";
    }
    if (name.contains("valve")) {
      return "VALVE";
    }
    return "UNRECOGNIZED";
  }

  /** Immutable configuration coverage result. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String policyId;
    private final String policyRevision;
    private final Set<String> configuredTags;
    private final List<String> unconfiguredRecognizedTags;
    private final List<Map<String, Object>> equipmentInventory;
    private final int moduleCount;
    private final boolean hiddenDefaultsUsed;
    private final Map<String, List<String>> moduleDependencies;
    private final List<String> executionBlockers;
    private final String configurationFingerprint;

    Result(String policyId, String policyRevision, Set<String> configuredTags, List<String> unconfigured,
        List<Map<String, Object>> equipmentInventory, int moduleCount, boolean hiddenDefaultsUsed,
        Map<String, List<String>> moduleDependencies, List<String> executionBlockers,
        String configurationFingerprint) {
      this.policyId = policyId;
      this.policyRevision = policyRevision;
      this.configuredTags = Collections.unmodifiableSet(new LinkedHashSet<String>(configuredTags));
      this.unconfiguredRecognizedTags = Collections.unmodifiableList(new ArrayList<String>(unconfigured));
      this.equipmentInventory = Collections.unmodifiableList(new ArrayList<Map<String, Object>>(equipmentInventory));
      this.moduleCount = moduleCount;
      this.hiddenDefaultsUsed = hiddenDefaultsUsed;
      this.moduleDependencies = moduleDependencies;
      this.executionBlockers = Collections.unmodifiableList(new ArrayList<String>(executionBlockers));
      this.configurationFingerprint = configurationFingerprint;
    }

    public boolean isComplete() {
      return moduleCount > 0 && unconfiguredRecognizedTags.isEmpty() && !hiddenDefaultsUsed;
    }

    /** @return true when configuration and executable case inputs are sufficient to start the design loop */
    public boolean isExecutionReady() {
      return isComplete() && executionBlockers.isEmpty();
    }

    public Set<String> getConfiguredTags() {
      return configuredTags;
    }

    public List<String> getUnconfiguredRecognizedTags() {
      return unconfiguredRecognizedTags;
    }

    public List<Map<String, Object>> getEquipmentInventory() {
      return equipmentInventory;
    }

    public Map<String, List<String>> getModuleDependencies() {
      return moduleDependencies;
    }

    public List<String> getExecutionBlockers() {
      return executionBlockers;
    }

    public String getConfigurationFingerprint() {
      return configurationFingerprint;
    }

    /** Conservatively identifies calculations and package artifacts invalidated by a configuration revision. */
    public RevisionImpact compareWith(Result baseline) {
      if (baseline == null) {
        return new RevisionImpact("NO_BASELINE", new ArrayList<String>(moduleDependencies.keySet()),
            standardInvalidatedArtifacts());
      }
      if (configurationFingerprint.equals(baseline.configurationFingerprint)) {
        return new RevisionImpact("UNCHANGED", Collections.<String>emptyList(), Collections.<String>emptyList());
      }
      Set<String> modules = new LinkedHashSet<String>();
      modules.addAll(baseline.moduleDependencies.keySet());
      modules.addAll(moduleDependencies.keySet());
      return new RevisionImpact("CONFIGURATION_CHANGED", new ArrayList<String>(modules),
          standardInvalidatedArtifacts());
    }

    private static List<String> standardInvalidatedArtifacts() {
      List<String> artifacts = new ArrayList<String>();
      Collections.addAll(artifacts, "design-case-envelope.json", "engineering-calculation-dag.json",
          "engineering-model.json", "equipment-register.json", "line-register.json", "valve-list.json",
          "instrument-register.json", "engineering-production-readiness.json", "plant.dexpi.xml");
      return artifacts;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("policyId", policyId);
      result.put("policyRevision", policyRevision);
      result.put("configuredTags", new ArrayList<String>(configuredTags));
      result.put("unconfiguredRecognizedTags", new ArrayList<String>(unconfiguredRecognizedTags));
      result.put("equipmentInventory", equipmentInventory);
      result.put("moduleCount", Integer.valueOf(moduleCount));
      result.put("complete", Boolean.valueOf(isComplete()));
      result.put("hiddenDefaultsUsed", Boolean.valueOf(hiddenDefaultsUsed));
      result.put("executionReady", Boolean.valueOf(isExecutionReady()));
      result.put("executionBlockers", executionBlockers);
      result.put("moduleDependencies", moduleDependencies);
      result.put("configurationFingerprint", configurationFingerprint);
      return result;
    }
  }

  /** Immutable conservative invalidation result between two controlled configuration revisions. */
  public static final class RevisionImpact implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String status;
    private final List<String> invalidatedModules;
    private final List<String> invalidatedArtifacts;

    RevisionImpact(String status, List<String> invalidatedModules, List<String> invalidatedArtifacts) {
      this.status = status;
      this.invalidatedModules = Collections.unmodifiableList(new ArrayList<String>(invalidatedModules));
      this.invalidatedArtifacts = Collections.unmodifiableList(new ArrayList<String>(invalidatedArtifacts));
    }

    public String getStatus() {
      return status;
    }

    public List<String> getInvalidatedModules() {
      return invalidatedModules;
    }

    public List<String> getInvalidatedArtifacts() {
      return invalidatedArtifacts;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("status", status);
      result.put("invalidatedModules", invalidatedModules);
      result.put("invalidatedArtifacts", invalidatedArtifacts);
      result.put("requiresRerun", Boolean.valueOf(!invalidatedModules.isEmpty()));
      return result;
    }
  }
}
