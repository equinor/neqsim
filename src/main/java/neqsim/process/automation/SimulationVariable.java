package neqsim.process.automation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Describes a single variable exposed by a unit operation in a process simulation. Each variable
 * has a stable string address that can be used with
 * {@link ProcessAutomation#getVariableValue(String, String)} and
 * {@link ProcessAutomation#setVariableValue(String, double, String)}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SimulationVariable implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Variable access type.
   */
  public enum VariableType {
    /** A calculated output (read-only). */
    OUTPUT,
    /** A user-settable input (read-write). */
    INPUT
  }

  private final String address;
  private final String name;
  private final VariableType type;
  private final String defaultUnit;
  private final String description;
  private final String category;
  private final String source;
  private final String unitFamily;
  private final Double minimumValue;
  private final Double maximumValue;
  private final List<String> allowedValues;
  private final boolean writable;
  private final boolean invalidatesProcess;
  private final String applicability;

  /**
   * Creates a new simulation variable descriptor.
   *
   * @param address the stable dot-separated address, e.g. "separator-1.gasOutStream.temperature"
   * @param name the short property name, e.g. "temperature"
   * @param type whether the variable is INPUT (read-write) or OUTPUT (read-only)
   * @param defaultUnit the default unit of measure, e.g. "K", "bara", "kg/hr"
   * @param description a human-readable description
   */
  public SimulationVariable(String address, String name, VariableType type, String defaultUnit,
      String description) {
    this(address, name, type, defaultUnit, description, "general", "process-automation",
        inferUnitFamily(defaultUnit), null, null, new ArrayList<String>(),
        type == VariableType.INPUT, type == VariableType.INPUT,
        "Available when the owning unit operation is present");
  }

  /**
   * Creates a fully specified simulation variable descriptor.
   *
   * @param address the stable dot-separated address
   * @param name the short property name
   * @param type whether the variable is INPUT or OUTPUT
   * @param defaultUnit the default unit of measure
   * @param description a human-readable description
   * @param category variable category such as stream, equipment, or tuning
   * @param source metadata source identifier
   * @param unitFamily physical unit family such as temperature or pressure
   * @param minimumValue optional minimum physically meaningful value
   * @param maximumValue optional maximum physically meaningful value
   * @param allowedValues optional allowed symbolic values
   * @param writable true if the variable can be written through automation
   * @param invalidatesProcess true if writes require the process to be rerun
   * @param applicability short note describing when the variable applies
   */
  private SimulationVariable(String address, String name, VariableType type, String defaultUnit,
      String description, String category, String source, String unitFamily, Double minimumValue,
      Double maximumValue, List<String> allowedValues, boolean writable, boolean invalidatesProcess,
      String applicability) {
    this.address = address;
    this.name = name;
    this.type = type;
    this.defaultUnit = defaultUnit;
    this.description = description;
    this.category = category;
    this.source = source;
    this.unitFamily = unitFamily;
    this.minimumValue = minimumValue;
    this.maximumValue = maximumValue;
    this.allowedValues = Collections.unmodifiableList(new ArrayList<String>(allowedValues));
    this.writable = writable;
    this.invalidatesProcess = invalidatesProcess;
    this.applicability = applicability;
  }

  /**
   * Infers a unit family from a default unit string.
   *
   * @param defaultUnit default unit string
   * @return unit family name
   */
  private static String inferUnitFamily(String defaultUnit) {
    if (defaultUnit == null || defaultUnit.trim().isEmpty()) {
      return "dimensionless";
    }
    if ("K".equals(defaultUnit) || "C".equals(defaultUnit)) {
      return "temperature";
    }
    if ("bara".equals(defaultUnit)) {
      return "pressure";
    }
    if ("kg/hr".equals(defaultUnit) || "mol/sec".equals(defaultUnit)) {
      return "flow";
    }
    if ("kg/m3".equals(defaultUnit)) {
      return "density";
    }
    if ("W".equals(defaultUnit) || "kW".equals(defaultUnit)) {
      return "power";
    }
    if ("m".equals(defaultUnit) || "m3".equals(defaultUnit)) {
      return "geometry";
    }
    if ("rpm".equals(defaultUnit)) {
      return "rotational_speed";
    }
    return "other";
  }

  /**
   * Returns a copy with category metadata.
   *
   * @param category variable category
   * @return copied variable descriptor
   */
  public SimulationVariable withCategory(String category) {
    return copy(category, source, unitFamily, minimumValue, maximumValue, allowedValues, writable,
        invalidatesProcess, applicability);
  }

  /**
   * Returns a copy with value bounds.
   *
   * @param minimumValue optional minimum value
   * @param maximumValue optional maximum value
   * @return copied variable descriptor
   */
  public SimulationVariable withBounds(Double minimumValue, Double maximumValue) {
    return copy(category, source, unitFamily, minimumValue, maximumValue, allowedValues, writable,
        invalidatesProcess, applicability);
  }

  /**
   * Returns a copy with allowed symbolic values.
   *
   * @param allowedValues allowed values
   * @return copied variable descriptor
   */
  public SimulationVariable withAllowedValues(String... allowedValues) {
    return copy(category, source, unitFamily, minimumValue, maximumValue,
        Arrays.asList(allowedValues), writable, invalidatesProcess, applicability);
  }

  /**
   * Returns a copy with write-safety metadata.
   *
   * @param writable true if writable
   * @param invalidatesProcess true if writes require rerunning the process
   * @return copied variable descriptor
   */
  public SimulationVariable withWritableSafety(boolean writable, boolean invalidatesProcess) {
    return copy(category, source, unitFamily, minimumValue, maximumValue, allowedValues, writable,
        invalidatesProcess, applicability);
  }

  /**
   * Returns a copy with applicability text.
   *
   * @param applicability applicability note
   * @return copied variable descriptor
   */
  public SimulationVariable withApplicability(String applicability) {
    return copy(category, source, unitFamily, minimumValue, maximumValue, allowedValues, writable,
        invalidatesProcess, applicability);
  }

  /**
   * Returns a copy with unit family metadata.
   *
   * @param unitFamily physical unit family
   * @return copied variable descriptor
   */
  public SimulationVariable withUnitFamily(String unitFamily) {
    return copy(category, source, unitFamily, minimumValue, maximumValue, allowedValues, writable,
        invalidatesProcess, applicability);
  }

  /**
   * Returns a copy with source metadata.
   *
   * @param source metadata source identifier
   * @return copied variable descriptor
   */
  public SimulationVariable withSource(String source) {
    return copy(category, source, unitFamily, minimumValue, maximumValue, allowedValues, writable,
        invalidatesProcess, applicability);
  }

  /**
   * Creates a copied descriptor with updated metadata.
   *
   * @param category variable category
   * @param source metadata source identifier
   * @param unitFamily physical unit family
   * @param minimumValue optional minimum value
   * @param maximumValue optional maximum value
   * @param allowedValues allowed symbolic values
   * @param writable true if writable
   * @param invalidatesProcess true if writes require process rerun
   * @param applicability applicability note
   * @return copied variable descriptor
   */
  private SimulationVariable copy(String category, String source, String unitFamily,
      Double minimumValue, Double maximumValue, List<String> allowedValues, boolean writable,
      boolean invalidatesProcess, String applicability) {
    return new SimulationVariable(address, name, type, defaultUnit, description, category, source,
        unitFamily, minimumValue, maximumValue, allowedValues, writable, invalidatesProcess,
        applicability);
  }

  /**
   * Returns the stable dot-notation address for this variable.
   *
   * @return the variable address
   */
  public String getAddress() {
    return address;
  }

  /**
   * Returns the short property name.
   *
   * @return the property name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns whether this variable is an input or output.
   *
   * @return the variable type
   */
  public VariableType getType() {
    return type;
  }

  /**
   * Returns the default unit of measure.
   *
   * @return the default unit string
   */
  public String getDefaultUnit() {
    return defaultUnit;
  }

  /**
   * Returns a human-readable description of the variable.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Returns the variable category.
   *
   * @return variable category
   */
  public String getCategory() {
    return category;
  }

  /**
   * Returns the metadata source.
   *
   * @return metadata source
   */
  public String getSource() {
    return source;
  }

  /**
   * Returns the physical unit family.
   *
   * @return unit family
   */
  public String getUnitFamily() {
    return unitFamily;
  }

  /**
   * Returns the minimum meaningful value.
   *
   * @return minimum value, or null if unrestricted
   */
  public Double getMinimumValue() {
    return minimumValue;
  }

  /**
   * Returns the maximum meaningful value.
   *
   * @return maximum value, or null if unrestricted
   */
  public Double getMaximumValue() {
    return maximumValue;
  }

  /**
   * Returns allowed symbolic values.
   *
   * @return unmodifiable allowed values list
   */
  public List<String> getAllowedValues() {
    return allowedValues;
  }

  /**
   * Returns whether the variable is writable.
   *
   * @return true if writable through automation
   */
  public boolean isWritable() {
    return writable;
  }

  /**
   * Returns whether writes require rerunning the process.
   *
   * @return true if writes invalidate current process results
   */
  public boolean isInvalidatesProcess() {
    return invalidatesProcess;
  }

  /**
   * Returns the applicability note.
   *
   * @return applicability note
   */
  public String getApplicability() {
    return applicability;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return address + " [" + type + ", " + defaultUnit + "] " + description;
  }
}
