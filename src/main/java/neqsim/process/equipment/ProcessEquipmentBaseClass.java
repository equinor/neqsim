/*
 * ProcessEquipmentBaseClass.java
 *
 * Created on 6. juni 2006, 15:12
 */

package neqsim.process.equipment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import neqsim.process.SimulationBaseClass;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.failure.EquipmentFailureMode;
import neqsim.process.equipment.iec81346.ReferenceDesignation;
import neqsim.process.equipment.stream.EnergyStream;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.util.report.Report;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Abstract ProcessEquipmentBaseClass class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public abstract class ProcessEquipmentBaseClass extends SimulationBaseClass
    implements ProcessEquipmentInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private ControllerDeviceInterface controller = null;
  ControllerDeviceInterface flowValveController = null;
  public boolean hasController = false;

  /**
   * Map of controller tag name to controller device. Supports multiple controllers per equipment.
   */
  private final Map<String, ControllerDeviceInterface> controllerMap =
      new LinkedHashMap<String, ControllerDeviceInterface>();
  private String specification = "TP";
  public String[][] report = new String[0][0];
  public HashMap<String, String> properties = new HashMap<String, String>();
  public EnergyStream energyStream = new EnergyStream();
  private boolean isSetEnergyStream = false;
  protected boolean isSolved = true;
  private boolean isActive = true;
  private double minimumFlow = 1e-20;

  /**
   * Flag to enable/disable capacity analysis for this equipment. When disabled, this equipment is
   * excluded from bottleneck detection, capacity utilization summaries, and optimization routines.
   */
  private boolean capacityAnalysisEnabled = true;

  /**
   * Current failure mode of the equipment. Null means equipment is operating normally.
   */
  private EquipmentFailureMode failureMode = null;

  /**
   * Flag indicating if the equipment is in a failed state.
   */
  private boolean isFailed = false;

  /**
   * IEC 81346 reference designation for this equipment. Contains the function, product, and
   * location aspects per IEC 81346 standard.
   */
  private ReferenceDesignation referenceDesignation = new ReferenceDesignation();

  /**
   * Capacity constraints for this equipment, keyed by constraint name. Marked transient because
   * {@link CapacityConstraint} instances may hold non-serializable lambda value suppliers. After
   * deserialization, subclasses should call {@link #initializeDefaultConstraints()} to rebuild.
   */
  private transient Map<String, CapacityConstraint> capacityConstraints;

  /**
   * <p>
   * Constructor for ProcessEquipmentBaseClass.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public ProcessEquipmentBaseClass(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {}

  /**
   * Create deep copy.
   *
   * @return a deep copy of the unit operation/process equipment
   */
  public ProcessEquipmentInterface copy() {
    try {
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(byteOut);
      out.writeObject(this);
      out.flush();
      ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
      ObjectInputStream in = new ObjectInputStream(byteIn);
      return (ProcessEquipmentInterface) in.readObject();
    } catch (Exception e) {
      throw new RuntimeException("Failed to copy ProcessEquipmentBaseClass", e);
    }
  }

  /**
   * <p>
   * getProperty.
   * </p>
   *
   * @param propertyName a {@link java.lang.String} object
   * @return a {@link java.lang.Object} object
   */
  public Object getProperty(String propertyName) {
    // if(properties.containsKey(propertyName)) {
    // return properties.get(properties).getValue();
    // }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void setRegulatorOutSignal(double signal) {}

  /** {@inheritDoc} */
  @Override
  public void setController(ControllerDeviceInterface controller) {
    this.controller = controller;
    hasController = controller != null;
    if (controller != null) {
      String tag =
          controller instanceof neqsim.util.NamedInterface ? controller.getName() : "default";
      controllerMap.put(tag, controller);
    }
  }

  /**
   * <p>
   * Setter for the field <code>flowValveController</code>.
   * </p>
   *
   * @param controller a {@link neqsim.process.controllerdevice.ControllerDeviceInterface} object
   */
  public void setFlowValveController(ControllerDeviceInterface controller) {
    this.flowValveController = controller;
    if (controller != null) {
      String tag =
          controller instanceof neqsim.util.NamedInterface ? controller.getName() : "flowValve";
      controllerMap.put(tag, controller);
    }
  }

  /** {@inheritDoc} */
  @Override
  public ControllerDeviceInterface getController() {
    return controller;
  }

  /** {@inheritDoc} */
  @Override
  public void addController(String tag, ControllerDeviceInterface controller) {
    controllerMap.put(tag, controller);
    if (this.controller == null) {
      this.controller = controller;
      hasController = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public ControllerDeviceInterface getController(String tag) {
    return controllerMap.get(tag);
  }

  /** {@inheritDoc} */
  @Override
  public Collection<ControllerDeviceInterface> getControllers() {
    if (controllerMap.isEmpty() && controller != null) {
      return Collections.singletonList(controller);
    }
    return Collections.unmodifiableCollection(controllerMap.values());
  }

  /** {@inheritDoc} */
  @Override
  public MechanicalDesign getMechanicalDesign() {
    return new MechanicalDesign(this);
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {}

  /** {@inheritDoc} */
  @Override
  public void initElectricalDesign() {}

  /** {@inheritDoc} */
  @Override
  public void initInstrumentDesign() {}

  /** {@inheritDoc} */
  @Override
  public String getSpecification() {
    return specification;
  }

  /** {@inheritDoc} */
  @Override
  public void setSpecification(String specification) {
    this.specification = specification;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] reportResults() {
    return report;
  }

  /** {@inheritDoc} */
  @Override
  public boolean solved() {
    return isSolved;
  }

  /**
   * <p>
   * Getter for the field <code>energyStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.EnergyStream} object
   */
  public EnergyStream getEnergyStream() {
    return energyStream;
  }

  /**
   * <p>
   * Setter for the field <code>energyStream</code>.
   * </p>
   *
   * @param energyStream a {@link neqsim.process.equipment.stream.EnergyStream} object
   */
  public void setEnergyStream(EnergyStream energyStream) {
    setEnergyStream(true);
    this.energyStream = energyStream;
  }

  /**
   * <p>
   * Setter for the field <code>energyStream</code>.
   * </p>
   *
   * @param isSetEnergyStream a boolean
   */
  public void setEnergyStream(boolean isSetEnergyStream) {
    this.isSetEnergyStream = isSetEnergyStream;
  }

  /**
   * <p>
   * isSetEnergyStream.
   * </p>
   *
   * @return a boolean
   */
  public boolean isSetEnergyStream() {
    return isSetEnergyStream;
  }

  /** {@inheritDoc} */
  @Override
  public double getPressure() {
    return getFluid().getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public double getPressure(String unit) {
    return getFluid().getPressure(unit);
  }

  /** {@inheritDoc} */
  @Override
  public void setPressure(double pressure) {
    getFluid().setPressure(pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double getTemperature() {
    return getFluid().getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double getTemperature(String unit) {
    return getFluid().getTemperature(unit);
  }

  /** {@inheritDoc} */
  @Override
  public void setTemperature(double temperature) {
    getFluid().setTemperature(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance() {
    return getMassBalance("kg/sec");
  }

  /** {@inheritDoc} */
  @Override
  public double getExergyChange(String unit, double surroundingTemperature) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void runConditionAnalysis(ProcessEquipmentInterface refExchanger) {}

  public String conditionAnalysisMessage = "";

  /** {@inheritDoc} */
  @Override
  public String getConditionAnalysisMessage() {
    return conditionAnalysisMessage;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.deepHashCode(report);
    result = prime * result
        + Objects.hash(conditionAnalysisMessage, controller, controllerMap, energyStream,
            flowValveController, hasController, isSetEnergyStream, name, properties, specification);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ProcessEquipmentBaseClass other = (ProcessEquipmentBaseClass) obj;
    return Objects.equals(conditionAnalysisMessage, other.conditionAnalysisMessage)
        && Objects.equals(controller, other.controller)
        && Objects.equals(controllerMap, other.controllerMap)
        && Objects.equals(energyStream, other.energyStream)
        && Objects.equals(flowValveController, other.flowValveController)
        && hasController == other.hasController && isSetEnergyStream == other.isSetEnergyStream
        && Objects.equals(name, other.name) && Objects.equals(properties, other.properties)
        && Arrays.deepEquals(report, other.report)
        && Objects.equals(specification, other.specification);
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    return toJson();
  }

  /** {@inheritDoc} */
  @Override
  public String getReport_json() {
    return new Report(this).generateJsonReport();
  }

  /** {@inheritDoc} */
  @Override
  public void run_step(UUID id) {}

  /**
   * <p>
   * Getter for the field <code>minimumFlow</code>, e.g., the minimum flow rate for the pump.
   * </p>
   *
   * @return a double
   */
  public double getMinimumFlow() {
    return minimumFlow;
  }

  /**
   * <p>
   * Setter for the field <code>minimumFlow</code>, e.g., the minimum flow rate for the pump.
   * </p>
   *
   * @param minimumFlow a double
   */
  public void setMinimumFlow(double minimumFlow) {
    this.minimumFlow = minimumFlow;
  }

  /**
   * <p>
   * Getter for the field <code>isActive</code>.
   * </p>
   *
   * @return a boolean
   */
  public boolean isActive() {
    return isActive;
  }

  /**
   * <p>
   * Setter for the field <code>isActive</code>.
   * </p>
   *
   * @param isActive a boolean
   */
  public void isActive(boolean isActive) {
    this.isActive = isActive;
  }

  /**
   * Checks if capacity analysis is enabled for this equipment.
   *
   * <p>
   * When disabled, this equipment is excluded from bottleneck detection, capacity utilization
   * summaries, and optimization routines. The equipment still tracks its constraints but doesn't
   * contribute to system-level analysis.
   * </p>
   *
   * @return true if capacity analysis is enabled (default is true)
   */
  public boolean isCapacityAnalysisEnabled() {
    return capacityAnalysisEnabled;
  }

  /**
   * Enables or disables capacity analysis for this equipment.
   *
   * <p>
   * When disabled, this equipment is excluded from:
   * <ul>
   * <li>System bottleneck detection ({@code ProcessSystem.findBottleneck()})</li>
   * <li>Capacity utilization summaries ({@code ProcessSystem.getCapacityUtilizationSummary()})</li>
   * <li>Equipment near capacity lists ({@code ProcessSystem.getEquipmentNearCapacityLimit()})</li>
   * <li>Optimization constraint checking</li>
   * </ul>
   * <p>
   * The equipment still calculates and tracks its constraints internally.
   * </p>
   *
   * @param enabled true to include in capacity analysis, false to exclude
   */
  public void setCapacityAnalysisEnabled(boolean enabled) {
    this.capacityAnalysisEnabled = enabled;
  }

  /**
   * Gets the current failure mode of the equipment.
   *
   * @return the failure mode, or null if equipment is operating normally
   */
  public EquipmentFailureMode getFailureMode() {
    return failureMode;
  }

  /**
   * Sets a failure mode on the equipment.
   *
   * <p>
   * When a failure mode is set, the equipment is marked as failed and its behavior changes
   * according to the failure mode characteristics (capacity factor, etc.). Setting null clears the
   * failure.
   * </p>
   *
   * @param failureMode the failure mode to apply, or null to clear failure
   */
  public void setFailureMode(EquipmentFailureMode failureMode) {
    this.failureMode = failureMode;
    this.isFailed = (failureMode != null);
    if (isFailed && failureMode.isCompleteFailure()) {
      this.isActive = false;
      this.capacityAnalysisEnabled = false;
    }
  }

  /**
   * Checks if the equipment is in a failed state.
   *
   * @return true if the equipment has a failure mode set
   */
  public boolean isFailed() {
    return isFailed;
  }

  /**
   * Simulates a trip (complete failure) on the equipment.
   *
   * <p>
   * Convenience method that applies a standard trip failure mode. The equipment becomes inactive
   * and is excluded from capacity analysis.
   * </p>
   */
  public void simulateTrip() {
    setFailureMode(EquipmentFailureMode.trip(this.getClass().getSimpleName()));
  }

  /**
   * Simulates degraded operation at a specified capacity.
   *
   * @param capacityPercent remaining capacity percentage (0-100)
   */
  public void simulateDegradedOperation(double capacityPercent) {
    setFailureMode(EquipmentFailureMode.degraded(capacityPercent));
  }

  /**
   * Restores the equipment from a failed state.
   *
   * <p>
   * Clears any failure mode and restores the equipment to normal operation.
   * </p>
   */
  public void restoreFromFailure() {
    this.failureMode = null;
    this.isFailed = false;
    this.isActive = true;
    this.capacityAnalysisEnabled = true;
  }

  /** {@inheritDoc} */
  @Override
  public ReferenceDesignation getReferenceDesignation() {
    return referenceDesignation;
  }

  /** {@inheritDoc} */
  @Override
  public void setReferenceDesignation(ReferenceDesignation referenceDesignation) {
    this.referenceDesignation =
        referenceDesignation != null ? referenceDesignation : new ReferenceDesignation();
  }

  /**
   * Gets the effective capacity factor considering any failure mode.
   *
   * @return capacity factor (0.0 to 1.0), where 1.0 is full capacity
   */
  public double getEffectiveCapacityFactor() {
    if (failureMode == null) {
      return 1.0;
    }
    return failureMode.getCapacityFactor();
  }

  // ============================================================
  // Capacity Constraint Support (universal base implementation)
  // ============================================================

  /**
   * Ensures the capacity constraints map is initialized.
   *
   * <p>
   * The map is transient (not serialized) so it may be null after deserialization. This method
   * lazily initializes it and calls {@link #initializeDefaultConstraints()} to let subclasses
   * re-attach their lambda value suppliers.
   * </p>
   */
  private void ensureCapacityConstraintsInitialized() {
    if (capacityConstraints == null) {
      capacityConstraints = new LinkedHashMap<String, CapacityConstraint>();
      initializeDefaultConstraints();
    }
  }

  /**
   * Hook for subclasses to set up default capacity constraints.
   *
   * <p>
   * Called lazily when constraints are first accessed, and after deserialization. Subclasses should
   * override this to add equipment-specific constraints using
   * {@link #addCapacityConstraint(CapacityConstraint)}. The default implementation does nothing.
   * </p>
   */
  protected void initializeDefaultConstraints() {
    // Default no-op — subclasses override to add equipment-specific constraints
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getCapacityConstraints() {
    ensureCapacityConstraintsInitialized();
    return Collections.unmodifiableMap(capacityConstraints);
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(CapacityConstraint constraint) {
    ensureCapacityConstraintsInitialized();
    if (constraint != null) {
      capacityConstraints.put(constraint.getName(), constraint);
    }
  }

  /** {@inheritDoc} */
  @Override
  public CapacityConstraint getBottleneckConstraint() {
    ensureCapacityConstraintsInitialized();
    CapacityConstraint bottleneck = null;
    double maxUtil = -1.0;
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isEnabled()) {
        double util = c.getUtilization();
        if (util > maxUtil) {
          maxUtil = util;
          bottleneck = c;
        }
      }
    }
    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    ensureCapacityConstraintsInitialized();
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isEnabled() && c.isViolated()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    ensureCapacityConstraintsInitialized();
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isEnabled() && c.isHardLimitExceeded()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    ensureCapacityConstraintsInitialized();
    double maxUtil = 0.0;
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isEnabled()) {
        double util = c.getUtilization();
        if (util > maxUtil) {
          maxUtil = util;
        }
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilizationPercent() {
    return getMaxUtilization() * 100.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getAvailableMargin() {
    return 1.0 - getMaxUtilization();
  }

  /** {@inheritDoc} */
  @Override
  public double getAvailableMarginPercent() {
    return getAvailableMargin() * 100.0;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isNearCapacityLimit() {
    ensureCapacityConstraintsInitialized();
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isEnabled() && c.isNearLimit()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Double> getUtilizationSummary() {
    ensureCapacityConstraintsInitialized();
    Map<String, Double> summary = new java.util.LinkedHashMap<String, Double>();
    for (Map.Entry<String, CapacityConstraint> entry : capacityConstraints.entrySet()) {
      CapacityConstraint c = entry.getValue();
      if (c.isEnabled()) {
        summary.put(entry.getKey(), c.getUtilization() * 100.0);
      }
    }
    return summary;
  }

  /**
   * Evaluates all capacity constraints and returns a summary string.
   *
   * <p>
   * Useful for logging and diagnostics. Each enabled constraint is evaluated and its utilization is
   * reported. Constraints that are violated or near their limit are flagged.
   * </p>
   *
   * @return multi-line summary of constraint status
   */
  public String getConstraintEvaluationReport() {
    ensureCapacityConstraintsInitialized();
    StringBuilder sb = new StringBuilder();
    sb.append("Capacity constraints for ").append(getName()).append(":\n");
    for (Map.Entry<String, CapacityConstraint> entry : capacityConstraints.entrySet()) {
      CapacityConstraint c = entry.getValue();
      if (c.isEnabled()) {
        sb.append("  ").append(entry.getKey());
        sb.append(": ").append(String.format("%.1f%%", c.getUtilization() * 100.0));
        if (c.isViolated()) {
          sb.append(" [VIOLATED]");
        } else if (c.isNearLimit()) {
          sb.append(" [WARNING]");
        }
        sb.append("\n");
      }
    }
    return sb.toString();
  }
}
