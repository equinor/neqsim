package neqsim.process.engineering.production;

import java.io.Serializable;
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
              .separatorBasis(rule.separatorGasResidenceTimeSeconds, rule.separatorSoudersBrownCoefficient,
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
      inventory.add(row);
      if (!configured) {
        unconfigured.add(unit.getName());
      }
    }
    return new Result(policy.getId(), policy.getRevision(), configuredTags, unconfigured, inventory,
        project.getEngineeringDesignModules().size(), hiddenDefaultsUsed);
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

    Result(String policyId, String policyRevision, Set<String> configuredTags, List<String> unconfigured,
        List<Map<String, Object>> equipmentInventory, int moduleCount, boolean hiddenDefaultsUsed) {
      this.policyId = policyId;
      this.policyRevision = policyRevision;
      this.configuredTags = Collections.unmodifiableSet(new LinkedHashSet<String>(configuredTags));
      this.unconfiguredRecognizedTags = Collections.unmodifiableList(new ArrayList<String>(unconfigured));
      this.equipmentInventory = Collections.unmodifiableList(new ArrayList<Map<String, Object>>(equipmentInventory));
      this.moduleCount = moduleCount;
      this.hiddenDefaultsUsed = hiddenDefaultsUsed;
    }

    public boolean isComplete() {
      return moduleCount > 0 && unconfiguredRecognizedTags.isEmpty() && !hiddenDefaultsUsed;
    }

    public Set<String> getConfiguredTags() {
      return configuredTags;
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
      return result;
    }
  }
}
