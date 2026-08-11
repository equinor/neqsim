package neqsim.process.dynamics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.GsonBuilder;
import neqsim.process.ProcessElementInterface;
import neqsim.process.SimulationInterface;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.processmodel.ModuleInterface;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Machine-readable audit of transient semantics in a {@link ProcessSystem} or multi-area {@link ProcessModel}.
 *
 * <p>
 * The report is intentionally diagnostic rather than a readiness certificate. It distinguishes audited stored-state
 * dynamics from algebraic timestep evaluation, reports type-specific runtime activation separately from state
 * ownership, identifies explicitly requested configurations that cannot use the default transient boundary, keeps
 * unaudited custom transient implementations visible as review items, and exposes process-level execution modes whose
 * current numerical/error semantics are not yet strict-professional ready.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.2
 */
public final class DynamicCapabilityReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Schema version for serialized/JSON reports. */
  private static final String SCHEMA_VERSION = "1.2";

  private final String schemaVersion = SCHEMA_VERSION;
  private final List<Entry> entries;
  private final List<String> executionIssues;

  /**
   * Immutable record describing one process element in the capability audit.
   *
   * @author Even Solbraa
   * @version 1.1
   */
  public static final class Entry implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String areaName;
    private final String containerPath;
    private final String category;
    private final String name;
    private final String className;
    private final DynamicCapability capability;
    private final Boolean calculateSteadyState;
    private final DynamicActivationStatus activationStatus;
    private final String activationDiagnostic;

    private Entry(String areaName, String containerPath, ProcessElementInterface element) {
      this.areaName = areaName == null ? "" : areaName;
      this.containerPath = containerPath == null ? "" : containerPath;
      this.category = categoryOf(element);
      this.name = element.getName() == null ? "" : element.getName();
      this.className = element.getClass().getName();
      this.capability = element.getDynamicCapability();
      this.calculateSteadyState = element instanceof SimulationInterface
          ? Boolean.valueOf(((SimulationInterface) element).getCalculateSteadyState())
          : null;
      this.activationStatus = DynamicActivationResolver.resolve(element);
      this.activationDiagnostic = DynamicActivationResolver.diagnostic(element);
    }

    /**
     * Process-area name, empty for a standalone {@link ProcessSystem} report.
     *
     * @return process-area name
     */
    public String getAreaName() {
      return areaName;
    }

    /**
     * Nested module/container path, empty for an element registered directly in the audited process area.
     *
     * @return module path using {@code ::} separators
     */
    public String getContainerPath() {
      return containerPath;
    }

    /**
     * Element category: module, equipment, measurement, controller, or element.
     *
     * @return category string
     */
    public String getCategory() {
      return category;
    }

    /**
     * Element name.
     *
     * @return element name
     */
    public String getName() {
      return name;
    }

    /**
     * Runtime Java class name.
     *
     * @return fully qualified class name
     */
    public String getClassName() {
      return className;
    }

    /**
     * Audited dynamic capability.
     *
     * @return capability
     */
    public DynamicCapability getCapability() {
      return capability;
    }

    /**
     * Runtime steady-state flag for simulation elements, or null for non-simulation control/measurement elements.
     *
     * @return steady-state flag or null
     */
    public Boolean getCalculateSteadyState() {
      return calculateSteadyState;
    }

    /**
     * Type-specific runtime activation status, kept separate from state ownership.
     *
     * @return activation status
     */
    public DynamicActivationStatus getActivationStatus() {
      return activationStatus;
    }

    /**
     * Diagnostic explaining the current runtime activation status.
     *
     * @return activation diagnostic
     */
    public String getActivationDiagnostic() {
      return activationDiagnostic;
    }

    /**
     * Area- and module-qualified name suitable for diagnostics.
     *
     * @return {@code area::module::name} where applicable
     */
    public String getQualifiedName() {
      String localName = containerPath.isEmpty() ? name : containerPath + "::" + name;
      return areaName.isEmpty() ? localName : areaName + "::" + localName;
    }

    /**
     * Whether the runtime configuration requests the element-specific difference-equation path through the generic
     * simulation flag.
     *
     * <p>
     * This is a requested-mode indicator only. It does not prove that a subclass enters its stateful path; use
     * {@link #getActivationStatus()} for type-specific activation evidence where available.
     * </p>
     *
     * @return true when a {@link SimulationInterface} has {@code calculateSteadyState == false}
     */
    public boolean isDynamicModeRequested() {
      return calculateSteadyState != null && !calculateSteadyState.booleanValue();
    }

    /**
     * Whether this element is configured in a way known to be incompatible with its audited capability.
     *
     * <p>
     * An algebraic element is a valid participant in a transient flowsheet while it remains in algebraic mode. It is a
     * blocking configuration error only when its difference-equation mode is explicitly requested even though no
     * audited dynamic implementation exists. A module is a composite container and is therefore not rejected merely
     * because the container itself has no independent state; its nested contents are audited separately.
     * </p>
     *
     * @return true for an explicitly unsupported capability or algebraic non-module element forced into dynamic mode
     */
    public boolean hasUnsupportedDynamicConfiguration() {
      return capability == DynamicCapability.UNSUPPORTED_DYNAMIC
          || (capability == DynamicCapability.ALGEBRAIC && isDynamicModeRequested() && !"module".equals(category));
    }

    /**
     * Whether a type-specific activation audit found a requested/enabled dynamic path with missing prerequisites.
     *
     * @return true when runtime activation configuration is incomplete
     */
    public boolean hasIncompleteDynamicActivation() {
      return activationStatus == DynamicActivationStatus.INCOMPLETE_CONFIGURATION;
    }

    /**
     * Whether a custom transient implementation still needs engineering capability review.
     *
     * @return true for {@link DynamicCapability#UNCLASSIFIED_DYNAMIC}
     */
    public boolean requiresCapabilityReview() {
      return capability == DynamicCapability.UNCLASSIFIED_DYNAMIC;
    }

    /**
     * Whether an audited stored-state dynamic implementation is currently known to be inactive.
     *
     * <p>
     * Type-specific activation evidence takes precedence over the generic steady-state flag. For equipment families
     * whose activation semantics are still unverified, the historical {@code calculateSteadyState} flag remains a
     * conservative engineering review aid rather than a proof of actual runtime activation.
     * </p>
     *
     * @return true when the dynamic state is known or conservatively indicated to be inactive
     */
    public boolean hasInactiveAuditedDynamicState() {
      if (!capability.hasExplicitDynamicState()) {
        return false;
      }
      if (activationStatus == DynamicActivationStatus.INACTIVE) {
        return true;
      }
      if (activationStatus != DynamicActivationStatus.UNVERIFIED) {
        return false;
      }
      return calculateSteadyState != null && calculateSteadyState.booleanValue();
    }
  }

  private DynamicCapabilityReport(List<Entry> entries, List<String> executionIssues) {
    this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
    this.executionIssues = Collections.unmodifiableList(new ArrayList<String>(executionIssues));
  }

  /**
   * Builds a capability report for one process system, recursively including initialized process modules.
   *
   * @param process process system to audit
   * @return capability report
   * @throws IllegalArgumentException if process is null
   */
  public static DynamicCapabilityReport from(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
    List<Entry> collected = new ArrayList<Entry>();
    List<String> executionIssues = new ArrayList<String>();
    collectArea("", process, collected, executionIssues);
    return new DynamicCapabilityReport(collected, executionIssues);
  }

  /**
   * Builds a capability report for every process area in a model, recursively including initialized process modules.
   *
   * @param model multi-area process model to audit
   * @return capability report with area-qualified entries
   * @throws IllegalArgumentException if model is null
   */
  public static DynamicCapabilityReport from(ProcessModel model) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    List<Entry> collected = new ArrayList<Entry>();
    List<String> executionIssues = new ArrayList<String>();
    for (String areaName : model.getProcessSystemNames()) {
      ProcessSystem process = model.get(areaName);
      if (process != null) {
        collectArea(areaName, process, collected, executionIssues);
      }
    }
    return new DynamicCapabilityReport(collected, executionIssues);
  }

  /**
   * Report schema version.
   *
   * @return schema version
   */
  public String getSchemaVersion() {
    return schemaVersion;
  }

  /**
   * All audited entries in deterministic area/registration/module order.
   *
   * @return immutable list of entries
   */
  public List<Entry> getEntries() {
    return entries;
  }

  /**
   * Process-level execution configurations that are not yet safe for strict-professional transient qualification.
   *
   * <p>
   * These diagnostics are intentionally separate from per-element state ownership and activation. They describe a
   * process execution mode whose current solver/error contract is known to be incomplete even if every individual
   * element is otherwise classified.
   * </p>
   *
   * @return immutable area/module-qualified diagnostics
   */
  public List<String> getExecutionIssues() {
    return executionIssues;
  }

  /**
   * Counts entries by capability category.
   *
   * @return enum map containing every capability, including zero-count categories
   */
  public Map<DynamicCapability, Integer> getCapabilityCounts() {
    Map<DynamicCapability, Integer> counts = new EnumMap<DynamicCapability, Integer>(DynamicCapability.class);
    for (DynamicCapability capability : DynamicCapability.values()) {
      counts.put(capability, Integer.valueOf(0));
    }
    for (Entry entry : entries) {
      DynamicCapability capability = entry.getCapability();
      counts.put(capability, Integer.valueOf(counts.get(capability).intValue() + 1));
    }
    return Collections.unmodifiableMap(counts);
  }

  /**
   * Counts entries by runtime activation status.
   *
   * @return enum map containing every activation status, including zero-count categories
   */
  public Map<DynamicActivationStatus, Integer> getActivationCounts() {
    Map<DynamicActivationStatus, Integer> counts = new EnumMap<DynamicActivationStatus, Integer>(
        DynamicActivationStatus.class);
    for (DynamicActivationStatus status : DynamicActivationStatus.values()) {
      counts.put(status, Integer.valueOf(0));
    }
    for (Entry entry : entries) {
      DynamicActivationStatus status = entry.getActivationStatus();
      counts.put(status, Integer.valueOf(counts.get(status).intValue() + 1));
    }
    return Collections.unmodifiableMap(counts);
  }

  /**
   * Returns configuration errors that should block a transient run configured in strict professional-audit mode.
   *
   * @return immutable diagnostic strings
   */
  public List<String> getBlockingIssues() {
    List<String> issues = new ArrayList<String>();
    issues.addAll(executionIssues);
    for (Entry entry : entries) {
      if (entry.hasUnsupportedDynamicConfiguration()) {
        if (entry.getCapability() == DynamicCapability.ALGEBRAIC) {
          issues.add(entry.getQualifiedName() + " requests difference-equation mode but is classified ALGEBRAIC ("
              + entry.getClassName() + ")");
        } else {
          issues.add(entry.getQualifiedName() + " is classified UNSUPPORTED_DYNAMIC (" + entry.getClassName() + ")");
        }
      }
      if (entry.hasIncompleteDynamicActivation()) {
        issues.add(entry.getQualifiedName() + " has incomplete runtime dynamic activation: "
            + entry.getActivationDiagnostic() + " (" + entry.getClassName() + ")");
      }
    }
    return Collections.unmodifiableList(issues);
  }

  /**
   * Returns custom transient implementations whose physical state/equations still require audit.
   *
   * @return immutable diagnostic strings
   */
  public List<String> getReviewItems() {
    List<String> review = new ArrayList<String>();
    for (Entry entry : entries) {
      if (entry.requiresCapabilityReview()) {
        review.add(entry.getQualifiedName() + " has a custom transient implementation that is not yet classified ("
            + entry.getClassName() + ")");
      }
    }
    return Collections.unmodifiableList(review);
  }

  /**
   * Returns elements with explicit dynamic capability whose type-specific runtime activation is still unaudited.
   *
   * <p>
   * These are review aids, not strict-preflight failures. The capability campaign can therefore add activation audits
   * incrementally without making every existing mixed algebraic/dynamic model unusable in the meantime.
   * </p>
   *
   * @return immutable area-qualified element names
   */
  public List<String> getUnverifiedActivationElements() {
    List<String> unverified = new ArrayList<String>();
    for (Entry entry : entries) {
      if (entry.getCapability().hasExplicitDynamicState()
          && entry.getActivationStatus() == DynamicActivationStatus.UNVERIFIED) {
        unverified.add(entry.getQualifiedName());
      }
    }
    return Collections.unmodifiableList(unverified);
  }

  /**
   * Returns all issues that fail the opt-in strict transient preflight.
   *
   * <p>
   * Strict preflight combines known unsupported runtime configurations, process-level execution modes with incomplete
   * solver/error semantics, incomplete type-specific activation, and unaudited custom transient implementations. It
   * deliberately does not reject audited dynamic equipment that is intentionally inactive, nor does it reject families
   * whose activation semantics are still marked UNVERIFIED; those remain visible through dedicated review diagnostics.
   * </p>
   *
   * @return immutable list of strict-preflight issues
   */
  public List<String> getStrictPreflightIssues() {
    List<String> issues = new ArrayList<String>();
    issues.addAll(getBlockingIssues());
    issues.addAll(getReviewItems());
    return Collections.unmodifiableList(issues);
  }

  /**
   * Whether the process/model passes the opt-in strict transient capability preflight.
   *
   * <p>
   * A true result only means that no currently known unsupported configuration, incomplete audited activation,
   * unqualified process execution mode, or unaudited custom transient implementation was found. It is not a
   * quantitative validation, conformance, safety, or professional-readiness certificate.
   * </p>
   *
   * @return true when {@link #getStrictPreflightIssues()} is empty
   */
  public boolean isStrictPreflightReady() {
    return getStrictPreflightIssues().isEmpty();
  }

  /**
   * Fail fast when the opt-in strict transient capability preflight contains issues.
   *
   * @throws IllegalStateException with all current strict-preflight diagnostics when the preflight fails
   */
  public void assertStrictTransientReady() {
    List<String> issues = getStrictPreflightIssues();
    if (issues.isEmpty()) {
      return;
    }
    StringBuilder message = new StringBuilder("Strict transient capability preflight failed");
    for (String issue : issues) {
      message.append("; ").append(issue);
    }
    throw new IllegalStateException(message.toString());
  }

  /**
   * Returns audited dynamic elements that are currently configured or conservatively indicated to use a non-stateful
   * path.
   *
   * <p>
   * These are not errors: mixed algebraic/dynamic flowsheets are legitimate. Type-specific activation status takes
   * precedence over the generic {@code calculateSteadyState} flag where available.
   * </p>
   *
   * @return immutable list of area-qualified element names
   */
  public List<String> getInactiveAuditedDynamicElements() {
    List<String> inactive = new ArrayList<String>();
    for (Entry entry : entries) {
      if (entry.hasInactiveAuditedDynamicState()) {
        inactive.add(entry.getQualifiedName());
      }
    }
    return Collections.unmodifiableList(inactive);
  }

  /**
   * Whether the report contains a known unsupported, incomplete, or unqualified runtime configuration.
   *
   * <p>
   * A false result is not a professional-readiness or validation certificate; review items, activation audits, and
   * quantitative model qualification remain separate requirements.
   * </p>
   *
   * @return true if one or more blocking configuration issues exist
   */
  public boolean hasBlockingIssues() {
    if (!executionIssues.isEmpty()) {
      return true;
    }
    for (Entry entry : entries) {
      if (entry.hasUnsupportedDynamicConfiguration() || entry.hasIncompleteDynamicActivation()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Serializes the report for notebooks, MCP clients, CI qualification, and engineering tooling.
   *
   * @return pretty-printed JSON
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }

  private static void collectArea(String areaName, ProcessSystem process, List<Entry> target,
      List<String> executionIssues) {
    Set<ProcessElementInterface> seenElements = Collections
        .newSetFromMap(new IdentityHashMap<ProcessElementInterface, Boolean>());
    Set<ProcessSystem> seenProcesses = Collections.newSetFromMap(new IdentityHashMap<ProcessSystem, Boolean>());
    collectProcess(areaName, "", process, target, executionIssues, seenElements, seenProcesses);
  }

  private static void collectProcess(String areaName, String containerPath, ProcessSystem process, List<Entry> target,
      List<String> executionIssues, Set<ProcessElementInterface> seenElements, Set<ProcessSystem> seenProcesses) {
    if (process == null || !seenProcesses.add(process)) {
      return;
    }

    addExecutionIssues(areaName, containerPath, process, executionIssues);

    for (ProcessElementInterface element : process.getAllElements()) {
      boolean added = addEntry(areaName, containerPath, element, target, seenElements);
      if (added && element instanceof ModuleInterface) {
        ModuleInterface module = (ModuleInterface) element;
        String nestedPath = appendContainerPath(containerPath, element);
        collectProcess(areaName, nestedPath, module.getOperations(), target, executionIssues, seenElements,
            seenProcesses);
      }
    }

    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      Collection<ControllerDeviceInterface> controllers = equipment.getControllers();
      if (controllers == null) {
        continue;
      }
      for (ControllerDeviceInterface controller : controllers) {
        addEntry(areaName, containerPath, controller, target, seenElements);
      }
    }
  }

  private static void addExecutionIssues(String areaName, String containerPath, ProcessSystem process,
      List<String> executionIssues) {
    String processName = qualifiedProcessName(areaName, containerPath, process);
    if (process.isAdaptiveTimestepEnabled()) {
      executionIssues.add(processName
          + " enables adaptive transient execution, but runTransientAdaptive currently mutates one full step and adjusts a following timestep without rejected-step rollback or full-step/two-half-step error estimation");
    }
  }

  private static String qualifiedProcessName(String areaName, String containerPath, ProcessSystem process) {
    if (containerPath != null && !containerPath.isEmpty()) {
      return areaName == null || areaName.isEmpty() ? containerPath : areaName + "::" + containerPath;
    }
    if (areaName != null && !areaName.isEmpty()) {
      return areaName;
    }
    String name = process.getName();
    return name == null || name.trim().isEmpty() ? "process" : name;
  }

  private static boolean addEntry(String areaName, String containerPath, ProcessElementInterface element,
      List<Entry> target, Set<ProcessElementInterface> seenElements) {
    if (element == null || !seenElements.add(element)) {
      return false;
    }
    target.add(new Entry(areaName, containerPath, element));
    return true;
  }

  private static String appendContainerPath(String containerPath, ProcessElementInterface element) {
    String name = element.getName();
    if (name == null || name.trim().isEmpty()) {
      name = element.getClass().getSimpleName();
    }
    return containerPath == null || containerPath.isEmpty() ? name : containerPath + "::" + name;
  }

  private static String categoryOf(ProcessElementInterface element) {
    if (element instanceof ControllerDeviceInterface) {
      return "controller";
    }
    if (element instanceof MeasurementDeviceInterface) {
      return "measurement";
    }
    if (element instanceof ModuleInterface) {
      return "module";
    }
    if (element instanceof ProcessEquipmentInterface) {
      return "equipment";
    }
    return "element";
  }
}
