package neqsim.process.processmodel.dexpi;

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
}
