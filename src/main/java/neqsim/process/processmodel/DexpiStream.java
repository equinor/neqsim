package neqsim.process.processmodel;

import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;

/**
 * Stream created from DEXPI piping segments while preserving key metadata.
 */
public class DexpiStream extends Stream {
  private static final long serialVersionUID = 1L;

  private final String dexpiClass;
  private final String lineNumber;
  private final String fluidCode;

  public DexpiStream(String name, SystemInterface fluid, String dexpiClass, String lineNumber,
      String fluidCode) {
    super(name, fluid);
    this.dexpiClass = dexpiClass;
    this.lineNumber = lineNumber;
    this.fluidCode = fluidCode;
  }

  public String getDexpiClass() {
    return dexpiClass;
  }

  public String getLineNumber() {
    return lineNumber;
  }

  public String getFluidCode() {
    return fluidCode;
  }
}
