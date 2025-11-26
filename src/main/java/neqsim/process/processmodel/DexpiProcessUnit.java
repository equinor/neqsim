package neqsim.process.processmodel;

import java.util.UUID;
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

/**
 * Lightweight placeholder for equipment imported from a DEXPI XML file.
 */
public class DexpiProcessUnit extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1L;

  private final String dexpiClass;
  private final EquipmentEnum mappedEquipment;
  private final String lineNumber;
  private final String fluidCode;

  public DexpiProcessUnit(String name, String dexpiClass, EquipmentEnum mappedEquipment,
      String lineNumber, String fluidCode) {
    super(name);
    this.dexpiClass = dexpiClass;
    this.mappedEquipment = mappedEquipment;
    this.lineNumber = lineNumber;
    this.fluidCode = fluidCode;
  }

  @Override
  public void run(UUID id) {
    setCalculationIdentifier(id);
  }

  @Override
  public void run() {
    run(UUID.randomUUID());
  }

  public String getDexpiClass() {
    return dexpiClass;
  }

  public EquipmentEnum getMappedEquipment() {
    return mappedEquipment;
  }

  public String getLineNumber() {
    return lineNumber;
  }

  public String getFluidCode() {
    return fluidCode;
  }
}
