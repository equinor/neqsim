package neqsim.process.processmodel.dexpi;

import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Stream created from DEXPI piping segments while preserving key metadata.
 *
 * <p>
 * This class extends the standard {@link Stream} to carry DEXPI-specific metadata such as line numbers and fluid codes.
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
  private String nominalDiameterRepresentation;
  private String pipingClassCode;
  private String insulationType;
  private String flowInNominalDiameterRepresentation;
  private String flowOutNominalDiameterRepresentation;
  private String flowInPipingClassCode;
  private String flowOutPipingClassCode;
  private String flowInInsulationType;
  private String flowOutInsulationType;

  /**
   * Creates a new DEXPI stream.
   *
   * @param name the stream name
   * @param fluid the thermodynamic system
   * @param dexpiClass the original DEXPI component class
   * @param lineNumber the line number reference (may be null)
   * @param fluidCode the fluid code reference (may be null)
   */
  public DexpiStream(String name, SystemInterface fluid, String dexpiClass, String lineNumber, String fluidCode) {
    super(name, fluid);
    this.dexpiClass = dexpiClass;
    this.lineNumber = lineNumber;
    this.fluidCode = fluidCode;
  }

  /**
   * Wraps an existing process stream while adding source DEXPI line metadata.
   *
   * <p>
   * The wrapper delegates to the source stream instead of capturing its current thermodynamic-system object. This
   * preserves process topology when upstream equipment replaces its outlet fluid during a later simulation run.
   * </p>
   *
   * @param name the stream name
   * @param stream source stream to wrap
   * @param dexpiClass the original DEXPI component class
   * @param lineNumber the line number reference (may be null)
   * @param fluidCode the fluid code reference (may be null)
   */
  public DexpiStream(String name, StreamInterface stream, String dexpiClass, String lineNumber, String fluidCode) {
    super(name, stream);
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

  /**
   * Gets the source nominal-diameter representation, for example {@code DN 150} or {@code NPS 6}.
   *
   * @return nominal-diameter representation, or null if absent from the source model
   */
  public String getNominalDiameterRepresentation() {
    return nominalDiameterRepresentation;
  }

  /**
   * Sets the source nominal-diameter representation used in P&amp;ID line annotations.
   *
   * @param nominalDiameterRepresentation source representation such as {@code DN 150} or {@code NPS 6}
   */
  public void setNominalDiameterRepresentation(String nominalDiameterRepresentation) {
    this.nominalDiameterRepresentation = trimToNull(nominalDiameterRepresentation);
  }

  /**
   * Gets the piping-class code.
   *
   * @return piping-class code, or null if absent
   */
  public String getPipingClassCode() {
    return pipingClassCode;
  }

  /**
   * Sets the piping-class code used in P&amp;ID line annotations.
   *
   * @param pipingClassCode project piping-class code
   */
  public void setPipingClassCode(String pipingClassCode) {
    this.pipingClassCode = trimToNull(pipingClassCode);
  }

  /**
   * Gets the insulation-type code.
   *
   * @return insulation-type code, or null if absent
   */
  public String getInsulationType() {
    return insulationType;
  }

  /**
   * Sets the insulation-type code used in P&amp;ID line annotations.
   *
   * @param insulationType project insulation-type code
   */
  public void setInsulationType(String insulationType) {
    this.insulationType = trimToNull(insulationType);
  }

  /** @return nominal diameter at the flow-in end, or null when not explicitly supplied */
  public String getFlowInNominalDiameterRepresentation() {
    return flowInNominalDiameterRepresentation;
  }

  /** @param value nominal diameter at the flow-in end */
  public void setFlowInNominalDiameterRepresentation(String value) {
    flowInNominalDiameterRepresentation = trimToNull(value);
  }

  /** @return nominal diameter at the flow-out end, or null when not explicitly supplied */
  public String getFlowOutNominalDiameterRepresentation() {
    return flowOutNominalDiameterRepresentation;
  }

  /** @param value nominal diameter at the flow-out end */
  public void setFlowOutNominalDiameterRepresentation(String value) {
    flowOutNominalDiameterRepresentation = trimToNull(value);
  }

  /** @return piping class at the flow-in end, or null when not explicitly supplied */
  public String getFlowInPipingClassCode() {
    return flowInPipingClassCode;
  }

  /** @param value piping class at the flow-in end */
  public void setFlowInPipingClassCode(String value) {
    flowInPipingClassCode = trimToNull(value);
  }

  /** @return piping class at the flow-out end, or null when not explicitly supplied */
  public String getFlowOutPipingClassCode() {
    return flowOutPipingClassCode;
  }

  /** @param value piping class at the flow-out end */
  public void setFlowOutPipingClassCode(String value) {
    flowOutPipingClassCode = trimToNull(value);
  }

  /** @return insulation type at the flow-in end, or null when not explicitly supplied */
  public String getFlowInInsulationType() {
    return flowInInsulationType;
  }

  /** @param value insulation type at the flow-in end */
  public void setFlowInInsulationType(String value) {
    flowInInsulationType = trimToNull(value);
  }

  /** @return insulation type at the flow-out end, or null when not explicitly supplied */
  public String getFlowOutInsulationType() {
    return flowOutInsulationType;
  }

  /** @param value insulation type at the flow-out end */
  public void setFlowOutInsulationType(String value) {
    flowOutInsulationType = trimToNull(value);
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
