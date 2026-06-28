package neqsim.process.costestimation.topsides;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.costestimation.CostEstimateBasis;
import neqsim.process.costestimation.CostEstimateResult;
import neqsim.process.costestimation.EstimateClass;
import neqsim.process.costestimation.MaterialTakeOffItem;
import neqsim.process.costestimation.ProcessCostEstimate;
import neqsim.process.costestimation.ProcessCostEstimate.EquipmentCostSummary;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Offshore topsides facility cost estimator built on the standard NeqSim process cost rollup.
 *
 * <p>
 * The estimator keeps the existing equipment-specific correlations unchanged and adds a facility layer for topsides
 * modules, bulk materials, module integration, offshore installation, hook-up, commissioning, project costs, and scope
 * quality flags. The result is intended for Class 5 to Class 3 study and pre-FEED work unless replaced by vendor quotes
 * and detailed material take-off.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class TopsidesFacilityCostEstimator implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Facility configuration used for topsides factors. */
  public enum FacilityType {
    /** Fixed offshore platform topsides. */
    FIXED_PLATFORM("Fixed platform", 0.18, 0.20, 0.12, 0.90, 0.18),

    /** Floating production storage and offloading topsides. */
    FPSO("FPSO", 0.22, 0.14, 0.10, 0.70, 0.22),

    /** Semi-submersible or tension-leg-platform topsides. */
    SEMI_SUBMERSIBLE("Semi-submersible", 0.24, 0.18, 0.12, 0.80, 0.22),

    /** Onshore processing facility. */
    ONSHORE("Onshore", 0.08, 0.02, 0.04, 0.35, 0.25),

    /** Brownfield host tie-in or modification scope. */
    BROWNFIELD_TIE_IN("Brownfield tie-in", 0.26, 0.12, 0.20, 0.45, 0.12);

    private final String displayName;
    private final double moduleIntegrationFactor;
    private final double installationFactor;
    private final double hookUpFactor;
    private final double structuralWeightFactor;
    private final double utilityOffsiteFactor;

    /**
     * Creates a facility-type factor set.
     *
     * @param displayName display name
     * @param moduleIntegrationFactor module integration fraction of process module cost
     * @param installationFactor offshore installation fraction of direct field cost
     * @param hookUpFactor hook-up and commissioning fraction of direct field cost
     * @param structuralWeightFactor structural steel weight fraction of equipment dry weight
     * @param utilityOffsiteFactor utility and offsite allowance fraction of process module cost
     */
    FacilityType(String displayName, double moduleIntegrationFactor, double installationFactor, double hookUpFactor,
        double structuralWeightFactor, double utilityOffsiteFactor) {
      this.displayName = displayName;
      this.moduleIntegrationFactor = moduleIntegrationFactor;
      this.installationFactor = installationFactor;
      this.hookUpFactor = hookUpFactor;
      this.structuralWeightFactor = structuralWeightFactor;
      this.utilityOffsiteFactor = utilityOffsiteFactor;
    }

    /**
     * Gets the display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Gets the module integration factor.
     *
     * @return module integration factor
     */
    public double getModuleIntegrationFactor() {
      return moduleIntegrationFactor;
    }

    /**
     * Gets the installation factor.
     *
     * @return installation factor
     */
    public double getInstallationFactor() {
      return installationFactor;
    }

    /**
     * Gets the hook-up and commissioning factor.
     *
     * @return hook-up and commissioning factor
     */
    public double getHookUpFactor() {
      return hookUpFactor;
    }

    /**
     * Gets the structural steel weight factor.
     *
     * @return structural steel weight factor
     */
    public double getStructuralWeightFactor() {
      return structuralWeightFactor;
    }

    /**
     * Gets the utility and offsite factor.
     *
     * @return utility and offsite factor
     */
    public double getUtilityOffsiteFactor() {
      return utilityOffsiteFactor;
    }
  }

  /** Project execution context used for brownfield and congestion factors. */
  public enum ProjectContext {
    /** New topsides facility with normal integration scope. */
    GREENFIELD("Greenfield", 1.00),

    /** Brownfield modification on an existing facility. */
    BROWNFIELD_MODIFICATION("Brownfield modification", 1.35),

    /** Tie-in to an existing host facility. */
    HOST_TIE_IN("Host tie-in", 1.50);

    private final String displayName;
    private final double congestionFactor;

    /**
     * Creates a project-context descriptor.
     *
     * @param displayName display name
     * @param congestionFactor brownfield or congestion multiplier
     */
    ProjectContext(String displayName, double congestionFactor) {
      this.displayName = displayName;
      this.congestionFactor = congestionFactor;
    }

    /**
     * Gets the display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Gets the congestion factor.
     *
     * @return congestion factor
     */
    public double getCongestionFactor() {
      return congestionFactor;
    }
  }

  private ProcessSystem processSystem;
  private ProcessCostEstimate processCostEstimate;
  private FacilityType facilityType = FacilityType.FIXED_PLATFORM;
  private ProjectContext projectContext = ProjectContext.GREENFIELD;
  private CostEstimateBasis estimateBasis = new CostEstimateBasis().setDataSource("topsides-factored-mto")
      .setNotes("Topsides facility estimate derived from NeqSim equipment costs plus module and bulk allowances.");
  private double pipingBulkFactor = 0.25;
  private double electricalInstrumentationFactor = 0.16;
  private double insulationPaintFireproofingFactor = 0.06;
  private double hvacTelecomSafetyFactor = 0.08;
  private double engineeringProcurementFactor = 0.14;
  private double constructionManagementFactor = 0.06;
  private double projectContingencyFactor = 0.15;
  private double ownerCostFactor = 0.10;
  private boolean includeUtilitiesAndOffsites = true;

  /**
   * Creates an empty topsides estimator.
   */
  public TopsidesFacilityCostEstimator() {
  }

  /**
   * Creates a topsides estimator for a process system.
   *
   * @param processSystem process system representing the topsides scope
   */
  public TopsidesFacilityCostEstimator(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Creates a topsides estimator for an already calculated process cost estimate.
   *
   * @param processCostEstimate process cost estimate to reuse
   */
  public TopsidesFacilityCostEstimator(ProcessCostEstimate processCostEstimate) {
    this.processCostEstimate = processCostEstimate;
  }

  /**
   * Sets the process system.
   *
   * @param processSystem process system representing the topsides scope
   * @return this estimator for chaining
   */
  public TopsidesFacilityCostEstimator setProcessSystem(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    return this;
  }

  /**
   * Sets an existing process cost estimate to reuse.
   *
   * @param processCostEstimate process cost estimate
   * @return this estimator for chaining
   */
  public TopsidesFacilityCostEstimator setProcessCostEstimate(ProcessCostEstimate processCostEstimate) {
    this.processCostEstimate = processCostEstimate;
    return this;
  }

  /**
   * Sets the facility type.
   *
   * @param facilityType facility type; {@code null} keeps the current value
   * @return this estimator for chaining
   */
  public TopsidesFacilityCostEstimator setFacilityType(FacilityType facilityType) {
    if (facilityType != null) {
      this.facilityType = facilityType;
    }
    return this;
  }

  /**
   * Gets the facility type.
   *
   * @return facility type
   */
  public FacilityType getFacilityType() {
    return facilityType;
  }

  /**
   * Sets the project context.
   *
   * @param projectContext project context; {@code null} keeps the current value
   * @return this estimator for chaining
   */
  public TopsidesFacilityCostEstimator setProjectContext(ProjectContext projectContext) {
    if (projectContext != null) {
      this.projectContext = projectContext;
    }
    return this;
  }

  /**
   * Gets the project context.
   *
   * @return project context
   */
  public ProjectContext getProjectContext() {
    return projectContext;
  }

  /**
   * Sets the estimate basis.
   *
   * @param estimateBasis estimate basis; {@code null} resets to default Class 4 topsides basis
   * @return this estimator for chaining
   */
  public TopsidesFacilityCostEstimator setEstimateBasis(CostEstimateBasis estimateBasis) {
    this.estimateBasis = estimateBasis == null ? new CostEstimateBasis() : estimateBasis;
    return this;
  }

  /**
   * Gets the estimate basis.
   *
   * @return estimate basis
   */
  public CostEstimateBasis getEstimateBasis() {
    return estimateBasis;
  }

  /**
   * Sets the estimate class.
   *
   * @param estimateClass estimate class; {@code null} resets to Class 4
   * @return this estimator for chaining
   */
  public TopsidesFacilityCostEstimator setEstimateClass(EstimateClass estimateClass) {
    estimateBasis.setEstimateClass(estimateClass);
    return this;
  }

  /**
   * Sets the location factor.
   *
   * @param locationFactor location factor; values less than or equal to zero are ignored
   * @return this estimator for chaining
   */
  public TopsidesFacilityCostEstimator setLocationFactor(double locationFactor) {
    estimateBasis.setLocationFactor(locationFactor);
    return this;
  }

  /**
   * Sets the piping bulk allowance factor.
   *
   * @param pipingBulkFactor piping bulk cost fraction of process module cost
   * @return this estimator for chaining
   */
  public TopsidesFacilityCostEstimator setPipingBulkFactor(double pipingBulkFactor) {
    this.pipingBulkFactor = clampNonNegative(pipingBulkFactor);
    return this;
  }

  /**
   * Sets the electrical and instrumentation allowance factor.
   *
   * @param electricalInstrumentationFactor electrical and instrumentation fraction of process module cost
   * @return this estimator for chaining
   */
  public TopsidesFacilityCostEstimator setElectricalInstrumentationFactor(double electricalInstrumentationFactor) {
    this.electricalInstrumentationFactor = clampNonNegative(electricalInstrumentationFactor);
    return this;
  }

  /**
   * Sets whether utility and offsite allowances are included.
   *
   * @param includeUtilitiesAndOffsites true to include utility and offsite allowances
   * @return this estimator for chaining
   */
  public TopsidesFacilityCostEstimator setIncludeUtilitiesAndOffsites(boolean includeUtilitiesAndOffsites) {
    this.includeUtilitiesAndOffsites = includeUtilitiesAndOffsites;
    return this;
  }

  /**
   * Calculates a detailed topsides facility estimate.
   *
   * @return detailed topsides estimate result
   */
  public CostEstimateResult estimate() {
    ProcessCostEstimate processCost = resolveProcessCostEstimate();
    String name = resolveEstimateName(processCost);
    CostEstimateResult result = new CostEstimateResult().setIdentification(name, name, "TopsidesFacility")
        .setBasis(estimateBasis);

    if (processCost == null) {
      result.addQualityFlag("No ProcessSystem or ProcessCostEstimate was supplied for topsides costing.");
      return result;
    }

    List<EquipmentCostSummary> equipment = processCost.getEquipmentCosts();
    if (equipment.isEmpty()) {
      processCost.calculateAllCosts();
      equipment = processCost.getEquipmentCosts();
    }

    if (equipment.isEmpty()) {
      result.addQualityFlag("No equipment cost summaries were available after process cost calculation.");
      return result;
    }

    Map<String, Double> moduleCosts = new LinkedHashMap<String, Double>();
    double equipmentWeight = 0.0;
    double processModuleCost = 0.0;
    double excludedScopeCost = 0.0;

    for (EquipmentCostSummary summary : equipment) {
      if (isExcludedFromTopsides(summary)) {
        excludedScopeCost += summary.getTotalModuleCost();
        result.addQualityFlag("Excluded non-topsides item from topsides module build-up: " + summary.getName());
        continue;
      }

      double locatedCost = summary.getTotalModuleCost() * estimateBasis.getLocationFactor();
      String moduleName = classifyModule(summary);
      addToMap(moduleCosts, moduleName, locatedCost);
      processModuleCost += locatedCost;
      equipmentWeight += Math.max(summary.getWeight(), 0.0);

      result.addMaterialTakeOff(new MaterialTakeOffItem(summary.getName(), "equipment", summary.getType(),
          summary.getWeight(), "kg", summary.getWeight(), locatedCost, "process-cost-estimate"));

      if (summary.getWeight() <= 0.0 && summary.getTotalModuleCost() > 0.0) {
        result.addQualityFlag("Equipment cost has no positive weight basis: " + summary.getName());
      }
    }

    addModuleCapitalCosts(result, moduleCosts);

    double structuralWeight = equipmentWeight * facilityType.getStructuralWeightFactor();
    double pipingWeight = equipmentWeight * 0.35;
    double structuralSteelCost = structuralWeight * 10.0 * estimateBasis.getLocationFactor();
    double pipingBulkCost = processModuleCost * pipingBulkFactor;
    double electricalInstrumentationCost = processModuleCost * electricalInstrumentationFactor;
    double insulationPaintFireproofingCost = processModuleCost * insulationPaintFireproofingFactor;
    double hvacTelecomSafetyCost = processModuleCost * hvacTelecomSafetyFactor;
    double utilityOffsiteCost = includeUtilitiesAndOffsites ? processModuleCost * facilityType.getUtilityOffsiteFactor()
        : 0.0;
    double moduleIntegrationCost = processModuleCost * facilityType.getModuleIntegrationFactor();
    double bulkMaterialsCost = structuralSteelCost + pipingBulkCost + electricalInstrumentationCost
        + insulationPaintFireproofingCost + hvacTelecomSafetyCost + utilityOffsiteCost;
    double directFabricatedCost = processModuleCost + bulkMaterialsCost + moduleIntegrationCost;
    double offshoreInstallationCost = directFabricatedCost * facilityType.getInstallationFactor()
        * projectContext.getCongestionFactor();
    double hookUpCommissioningCost = directFabricatedCost * facilityType.getHookUpFactor()
        * projectContext.getCongestionFactor();
    double directFieldCost = directFabricatedCost + offshoreInstallationCost + hookUpCommissioningCost;
    double engineeringProcurementCost = directFieldCost * engineeringProcurementFactor;
    double constructionManagementCost = directFieldCost * constructionManagementFactor;
    double contingencyCost = directFieldCost * projectContingencyFactor;
    double ownerCost = directFieldCost * ownerCostFactor;
    double totalTopsidesCapex = directFieldCost + engineeringProcurementCost + constructionManagementCost
        + contingencyCost + ownerCost;

    result.addCapitalCost("processEquipmentModules", processModuleCost)
        .addCapitalCost("structuralSteel", structuralSteelCost).addCapitalCost("pipingBulk", pipingBulkCost)
        .addCapitalCost("electricalAndInstrumentation", electricalInstrumentationCost)
        .addCapitalCost("insulationPaintFireproofing", insulationPaintFireproofingCost)
        .addCapitalCost("hvacTelecomAndSafetySystems", hvacTelecomSafetyCost)
        .addCapitalCost("utilitiesAndOffsites", utilityOffsiteCost)
        .addCapitalCost("moduleFabricationAndIntegration", moduleIntegrationCost)
        .addCapitalCost("offshoreInstallation", offshoreInstallationCost)
        .addCapitalCost("hookUpAndCommissioning", hookUpCommissioningCost)
        .addCapitalCostSummary("directFieldCost", directFieldCost)
        .addCapitalCostSummary("excludedNonTopsidesScope", excludedScopeCost * estimateBasis.getLocationFactor());

    result.addProjectCost("engineeringProcurementProjectManagement", engineeringProcurementCost)
        .addProjectCost("constructionManagement", constructionManagementCost)
        .addProjectCost("projectContingency", contingencyCost).addProjectCost("ownerCost", ownerCost)
        .addProjectCostSummary("totalTopsidesCapex", totalTopsidesCapex);

    result.addWeightBasis("equipmentDryWeight", equipmentWeight)
        .addWeightBasis("structuralSteelWeight", structuralWeight).addWeightBasis("pipingBulkWeight", pipingWeight)
        .addWeightBasis("totalEstimatedDryWeight", equipmentWeight + structuralWeight + pipingWeight);
    result.addMaterialTakeOff(new MaterialTakeOffItem("Structural steel allowance", "structural", "Carbon Steel",
        structuralWeight, "kg", structuralWeight, structuralSteelCost, "topsides-allowance"));
    result.addMaterialTakeOff(new MaterialTakeOffItem("Piping bulk allowance", "piping", "Mixed piping classes",
        pipingWeight, "kg", pipingWeight, pipingBulkCost, "topsides-allowance"));
    result.addMaterialTakeOff(
        new MaterialTakeOffItem("Electrical and instrumentation allowance", "electrical", "Cable, trays, instruments",
            1.0, "allowance", Double.NaN, electricalInstrumentationCost, "topsides-allowance"));
    result.addMaterialTakeOff(new MaterialTakeOffItem("HVAC, telecom, fire and gas allowance", "safety-systems",
        "Package allowance", 1.0, "allowance", Double.NaN, hvacTelecomSafetyCost, "topsides-allowance"));

    addScopeQualityFlags(result, processModuleCost, equipmentWeight);
    return result;
  }

  /**
   * Resolves or calculates the process cost estimate.
   *
   * @return process cost estimate, or {@code null} when no process is available
   */
  private ProcessCostEstimate resolveProcessCostEstimate() {
    ProcessCostEstimate resolved = processCostEstimate;
    if (resolved == null && processSystem != null) {
      resolved = new ProcessCostEstimate(processSystem);
      processCostEstimate = resolved;
    }
    if (resolved != null) {
      resolved.setLocationFactor(estimateBasis.getLocationFactor());
      resolved.calculateAllCosts();
    }
    return resolved;
  }

  /**
   * Resolves a display name for the estimate.
   *
   * @param processCost process cost estimate
   * @return estimate name
   */
  private String resolveEstimateName(ProcessCostEstimate processCost) {
    if (processSystem != null && processSystem.getName() != null && !processSystem.getName().trim().isEmpty()) {
      return processSystem.getName() + " Topsides";
    }
    if (processCost != null) {
      return "Topsides Facility";
    }
    return "Unspecified Topsides Facility";
  }

  /**
   * Adds module costs to the result capital stack.
   *
   * @param result estimate result
   * @param moduleCosts module-cost map
   */
  private void addModuleCapitalCosts(CostEstimateResult result, Map<String, Double> moduleCosts) {
    for (Map.Entry<String, Double> entry : moduleCosts.entrySet()) {
      result.addCapitalCostBreakdown("module." + normalizeKey(entry.getKey()), entry.getValue());
    }
  }

  /**
   * Adds scope and quality flags based on the estimate basis.
   *
   * @param result estimate result
   * @param processModuleCost process module cost in USD
   * @param equipmentWeight equipment dry weight in kg
   */
  private void addScopeQualityFlags(CostEstimateResult result, double processModuleCost, double equipmentWeight) {
    result.addQualityFlag("Facility type factor set: " + facilityType.getDisplayName());
    result.addQualityFlag("Project context factor set: " + projectContext.getDisplayName());
    if (processModuleCost <= 0.0) {
      result.addQualityFlag("No positive topsides process module cost was calculated.");
    }
    if (equipmentWeight <= 0.0) {
      result.addQualityFlag("No positive equipment dry weight was available; bulk MTO weights are allowance based.");
    }
    if (includeUtilitiesAndOffsites) {
      result
          .addQualityFlag("Utilities and offsites are factor allowances unless explicit utility modules are included.");
    }
    if (!ProjectContext.GREENFIELD.equals(projectContext)) {
      result.addQualityFlag(
          "Brownfield or host tie-in congestion factor applied; verify with site-specific survey data.");
    }
    if (estimateBasis.getEstimateClass().getClassNumber() > EstimateClass.CLASS_3.getClassNumber()) {
      result.addQualityFlag("Estimate is not a detailed Class 1-3 vendor or firm-quantity estimate.");
    }
  }

  /**
   * Classifies an equipment summary into a topsides module.
   *
   * @param summary equipment cost summary
   * @return module name
   */
  private String classifyModule(EquipmentCostSummary summary) {
    String type = summary.getType();
    if (type == null) {
      return "Miscellaneous Process Packages";
    }
    if (type.contains("Compressor") || type.contains("Expander")) {
      return "Compression and Rotating Equipment";
    }
    if (type.contains("Pump")) {
      return "Pumping and Utility Packages";
    }
    if (type.contains("Heat Exchanger")) {
      return "Heat Transfer and Cooling";
    }
    if (type.contains("Valve") || type.contains("Piping") || type.contains("Pipeline")) {
      return "Piping and Flow Control";
    }
    if (type.contains("Column") || type.contains("Vessel") || type.contains("Tank")) {
      return "Separation and Treatment Modules";
    }
    return "Miscellaneous Process Packages";
  }

  /**
   * Checks whether an equipment summary is outside the topsides scope.
   *
   * @param summary equipment cost summary
   * @return true if the item should be excluded from topsides module costs
   */
  private boolean isExcludedFromTopsides(EquipmentCostSummary summary) {
    String type = summary.getType();
    return type != null && (type.contains("Well") || type.contains("Reservoir"));
  }

  /**
   * Adds a value to a double map.
   *
   * @param map target map
   * @param key map key
   * @param value value to add
   */
  private void addToMap(Map<String, Double> map, String key, double value) {
    Double current = map.get(key);
    if (current == null) {
      current = 0.0;
    }
    map.put(key, current + value);
  }

  /**
   * Normalizes a label to a simple camel-style map key.
   *
   * @param label input label
   * @return normalized key
   */
  private String normalizeKey(String label) {
    if (label == null || label.trim().isEmpty()) {
      return "unknown";
    }
    String[] parts = label.split("[^A-Za-z0-9]+");
    List<String> cleaned = new ArrayList<String>();
    for (String part : parts) {
      if (part != null && !part.isEmpty()) {
        cleaned.add(part);
      }
    }
    if (cleaned.isEmpty()) {
      return "unknown";
    }
    StringBuilder builder = new StringBuilder(
        cleaned.get(0).substring(0, 1).toLowerCase() + cleaned.get(0).substring(1));
    for (int partIndex = 1; partIndex < cleaned.size(); partIndex++) {
      String part = cleaned.get(partIndex);
      builder.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
    }
    return builder.toString();
  }

  /**
   * Clamps a factor to non-negative values.
   *
   * @param value input value
   * @return non-negative value
   */
  private double clampNonNegative(double value) {
    return Math.max(0.0, value);
  }
}
