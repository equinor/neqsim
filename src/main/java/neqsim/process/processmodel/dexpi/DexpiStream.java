package neqsim.process.processmodel.dexpi;

import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;

/**
 * Stream created from DEXPI piping segments while preserving key metadata.
 *
 * <p>
 * This class extends the standard {@link Stream} to carry DEXPI-specific metadata such as line
 * numbers and fluid codes.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class DexpiStream extends Stream {
  private static final long serialVersionUID = 1L;

  private final String dexpiClass;
  private final String lineNumber;
  private final String fluidCode;

  /**
   * Creates a new DEXPI stream.
   *
   * @param name the stream name
   * @param fluid the thermodynamic system
   * @param dexpiClass the original DEXPI component class
   * @param lineNumber the line number reference (may be null)
   * @param fluidCode the fluid code reference (may be null)
   */
  public DexpiStream(String name, SystemInterface fluid, String dexpiClass, String lineNumber,
      String fluidCode) {
    super(name, fluid);
    this.dexpiClass = dexpiClass;
    this.lineNumber = lineNumber;
    this.fluidCode = fluidCode;
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
