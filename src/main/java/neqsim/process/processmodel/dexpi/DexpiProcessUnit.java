package neqsim.process.processmodel.dexpi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

/**
 * Lightweight placeholder for equipment imported from a DEXPI XML file.
 *
 * <p>
 * This class records the original DEXPI class together with the mapped {@link EquipmentEnum}
 * category and contextual information like line numbers or fluid codes.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class DexpiProcessUnit extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1L;

  private final String dexpiClass;
  private final EquipmentEnum mappedEquipment;
  private final String lineNumber;
  private final String fluidCode;
  private final Map<String, String> sizingAttributes = new LinkedHashMap<>();
  private String dexpiId;

  /**
   * Creates a new DEXPI process unit.
   *
   * @param name the equipment tag name
   * @param dexpiClass the original DEXPI component class
   * @param mappedEquipment the mapped NeqSim equipment type
   * @param lineNumber the line number reference (may be null)
   * @param fluidCode the fluid code reference (may be null)
   */
  public DexpiProcessUnit(String name, String dexpiClass, EquipmentEnum mappedEquipment,
      String lineNumber, String fluidCode) {
    super(name);
    this.dexpiClass = dexpiClass;
    this.mappedEquipment = mappedEquipment;
    this.lineNumber = lineNumber;
    this.fluidCode = fluidCode;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    run(UUID.randomUUID());
  }

  /**
   * Gets the original DEXPI component class.
   *
   * @return the DEXPI class name
   */
  public String getDexpiClass() {
    return dexpiClass;
  }

  /**
   * Gets the mapped NeqSim equipment type.
   *
   * @return the equipment enum
   */
  public EquipmentEnum getMappedEquipment() {
    return mappedEquipment;
  }

  /**
   * Gets the line number reference.
   *
   * @return the line number, or null if not set
   */
  public String getLineNumber() {
    return lineNumber;
  }

  /**
   * Gets the fluid code reference.
   *
   * @return the fluid code, or null if not set
   */
  public String getFluidCode() {
    return fluidCode;
  }

  /**
   * Gets the DEXPI element ID (e.g. "CentrifugalPump-1").
   *
   * @return the DEXPI ID, or null if not set
   */
  public String getDexpiId() {
    return dexpiId;
  }

  /**
   * Sets the DEXPI element ID.
   *
   * @param dexpiId the DEXPI element ID
   */
  public void setDexpiId(String dexpiId) {
    this.dexpiId = dexpiId;
  }

  /**
   * Stores a sizing attribute extracted from the DEXPI XML GenericAttributes.
   *
   * @param name the attribute name (e.g. {@link DexpiMetadata#INSIDE_DIAMETER})
   * @param value the attribute value as a string
   */
  public void setSizingAttribute(String name, String value) {
    if (name != null && value != null) {
      sizingAttributes.put(name, value);
    }
  }

  /**
   * Returns the value of a sizing attribute, or {@code null} if not set.
   *
   * @param name the attribute name
   * @return the attribute value, or null
   */
  public String getSizingAttribute(String name) {
    return sizingAttributes.get(name);
  }

  /**
   * Returns the value of a sizing attribute as a double, or the given default if not set or not
   * parseable.
   *
   * @param name the attribute name
   * @param defaultValue the default value
   * @return the parsed double or the default
   */
  public double getSizingAttributeAsDouble(String name, double defaultValue) {
    String value = sizingAttributes.get(name);
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * Returns an unmodifiable view of all sizing attributes.
   *
   * @return map of sizing attribute names to values
   */
  public Map<String, String> getSizingAttributes() {
    return Collections.unmodifiableMap(sizingAttributes);
  }
}
