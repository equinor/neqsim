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
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Machine-readable audit of transient semantics in a {@link ProcessSystem} or multi-area {@link ProcessModel}.
 *
 * <p>
 * The report is intentionally diagnostic rather than a readiness certificate. It distinguishes audited stored-state
 * dynamics from algebraic timestep evaluation, identifies explicitly requested configurations that cannot use the
 * default transient boundary, and keeps unaudited custom transient implementations visible as review items.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class DynamicCapabilityReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Schema version for serialized/JSON reports. */
  private static final String SCHEMA_VERSION = "1.0";

  private final String schemaVersion = SCHEMA_VERSION;
  private final List<Entry> entries;

  /**
   * Immutable record describing one process element in the capability audit.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Entry implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String areaName;
    private final String category;
    private final String name;
    private final String className;
    private final DynamicCapability capability;
    private final Boolean calculateSteadyState;

    private Entry(String areaName, ProcessElementInterface element) {
      this.areaName = areaName == null ? "" : areaName;
      this.category = categoryOf(element);
      this.name = element.getName() == null ? "" : element.getName();
      this.className = element.getClass().getName();
      this.capability = element.getDynamicCapability();
      this.calculateSteadyState = element instanceof SimulationInterface
          ? Boolean.valueOf(((SimulationInterface) element).getCalculateSteadyState())
          : null;
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
     * Element category: equipment, measurement, controller, or element.
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
     * Area-qualified name suitable for diagnostics.
     *
     * @return {@code area::name} for a model report, otherwise the element name
     */
    public String getQualifiedName() {
      return areaName.isEmpty() ? name : areaName + "::" + name;
    }

    /**
     * Whether the runtime configuration requests the element-specific difference-equation path.
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
     * audited dynamic implementation exists.
     * </p>
     *
     * @return true for an explicitly unsupported capability or algebraic element forced into dynamic mode
     */
    public boolean hasUnsupportedDynamicConfiguration() {
      return capability == DynamicCapability.UNSUPPORTED_DYNAMIC
          || (capability == DynamicCapability.ALGEBRAIC && isDynamicModeRequested());
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
     * Whether an audited stored-state dynamic implementation exists but is currently evaluated in steady-state mode.
     *
     * @return true for known physical dynamic equipment with {@code calculateSteadyState == true}
     */
    public boolean hasInactiveAuditedDynamicState() {
      return calculateSteadyState != null && calculateSteadyState.booleanValue()
          && capability.hasExplicitDynamicState();
    }
  }

  private DynamicCapabilityReport(List<Entry> entries) {
    this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
  }

  /**
   * Builds a capability report for one process system.
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
    collectArea("", process, collected);
    return new DynamicCapabilityReport(collected);
  }

  /**
   * Builds a capability report for every process area in a model.
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
    for (String areaName : model.getProcessSystemNames()) {
      ProcessSystem process = model.get(areaName);
      if (process != null) {
        collectArea(areaName, process, collected);
      }
    }
    return new DynamicCapabilityReport(collected);
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
   * All audited entries in deterministic area/registration order.
   *
   * @return immutable list of entries
   */
  public List<Entry> getEntries() {
    return entries;
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
   * Returns configuration errors that should block a transient run configured in strict professional-audit mode.
   *
   * @return immutable diagnostic strings
   */
  public List<String> getBlockingIssues() {
    List<String> issues = new ArrayList<String>();
    for (Entry entry : entries) {
      if (entry.hasUnsupportedDynamicConfiguration()) {
        if (entry.getCapability() == DynamicCapability.ALGEBRAIC) {
          issues.add(entry.getQualifiedName() + " requests difference-equation mode but is classified ALGEBRAIC ("
              + entry.getClassName() + ")");
        } else {
          issues.add(entry.getQualifiedName() + " is classified UNSUPPORTED_DYNAMIC (" + entry.getClassName() + ")");
        }
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
   * Returns audited dynamic elements that are currently configured to use their steady-state/algebraic path.
   *
   * <p>
   * These are not errors: mixed algebraic/dynamic flowsheets are legitimate. The list is exposed so an engineer can
   * verify that a unit expected to carry inventory or inertia was actually switched into its dynamic mode.
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
   * Whether the report contains a known unsupported runtime configuration.
   *
   * <p>
   * A false result is not a professional-readiness or validation certificate; review items and quantitative model
   * qualification remain separate requirements.
   * </p>
   *
   * @return true if one or more blocking configuration issues exist
   */
  public boolean hasBlockingIssues() {
    for (Entry entry : entries) {
      if (entry.hasUnsupportedDynamicConfiguration()) {
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

  private static void collectArea(String areaName, ProcessSystem process, List<Entry> target) {
    Set<ProcessElementInterface> seen = Collections
        .newSetFromMap(new IdentityHashMap<ProcessElementInterface, Boolean>());

    for (ProcessElementInterface element : process.getAllElements()) {
      addEntry(areaName, element, target, seen);
    }

    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      Collection<ControllerDeviceInterface> controllers = equipment.getControllers();
      if (controllers == null) {
        continue;
      }
      for (ControllerDeviceInterface controller : controllers) {
        addEntry(areaName, controller, target, seen);
      }
    }
  }

  private static void addEntry(String areaName, ProcessElementInterface element, List<Entry> target,
      Set<ProcessElementInterface> seen) {
    if (element == null || !seen.add(element)) {
      return;
    }
    target.add(new Entry(areaName, element));
  }

  private static String categoryOf(ProcessElementInterface element) {
    if (element instanceof ControllerDeviceInterface) {
      return "controller";
    }
    if (element instanceof MeasurementDeviceInterface) {
      return "measurement";
    }
    if (element instanceof ProcessEquipmentInterface) {
      return "equipment";
    }
    return "element";
  }
}
