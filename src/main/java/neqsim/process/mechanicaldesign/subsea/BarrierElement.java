package neqsim.process.mechanicaldesign.subsea;

import java.io.Serializable;

/**
 * Represents a single well barrier element per NORSOK D-010 Section 4.
 *
 * <p>
 * A barrier element is an independent physical component that prevents uncontrolled flow of
 * formation fluids. Examples include casing, cement, packer, DHSV, wellhead, and tubing.
 * </p>
 *
 * <p>
 * Each barrier element has a type, a functional status (intact or degraded), and a verification
 * status indicating the last test result.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see BarrierEnvelope
 * @see WellBarrierSchematic
 */
public class BarrierElement implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Well barrier element types per NORSOK D-010 Figure 3 and Tables 20/36/37.
   */
  public enum ElementType {
    /** Production casing and casing cement. */
    CASING,
    /** Tubing string. */
    TUBING,
    /** Production packer. */
    PACKER,
    /** Downhole safety valve (SSSV/SCSSV). */
    DHSV,
    /** Injection safety valve (ISV) required per NORSOK D-010 Table 36. */
    ISV,
    /** Wellhead and tubing hanger. */
    WELLHEAD,
    /** Christmas tree and master valve. */
    XMAS_TREE,
    /** Annular safety valve (ASV). */
    ASV,
    /** Cement (primary or secondary). */
    CEMENT,
    /** Casing cement behind production casing. */
    CASING_CEMENT,
    /** Formation (competent caprock). */
    FORMATION,
    /** Mechanical plug (bridge plug or cement plug). */
    PLUG,
    /** Wing valve (WV/PWV). */
    WING_VALVE,
    /** Swab valve. */
    SWAB_VALVE,
    /** Chemical injection valve. */
    CHEMICAL_INJECTION_VALVE,
    /** Kill valve. */
    KILL_VALVE,
    /** In-well monitoring gauge. */
    GAUGE,
    /** Annulus access valve. */
    ANNULUS_ACCESS_VALVE
  }

  /**
   * Functional status of the barrier element.
   */
  public enum Status {
    /** Element is intact and functional. */
    INTACT,
    /** Element is degraded but still providing some containment. */
    DEGRADED,
    /** Element has failed and provides no containment. */
    FAILED,
    /** Element status is unknown (not tested). */
    UNKNOWN
  }

  /** The element type. */
  private final ElementType type;

  /** Element name/identifier. */
  private final String name;

  /** Functional status. */
  private Status status;

  /** Whether this element has been verified (tested). */
  private boolean verified;

  /** Depth of element in meters MD (0 for surface elements). */
  private double depthMD;

  /** Description or notes about the element. */
  private String description;

  /**
   * Create a barrier element.
   *
   * @param type element type
   * @param name element identifier
   */
  public BarrierElement(ElementType type, String name) {
    this.type = type;
    this.name = name;
    this.status = Status.INTACT;
    this.verified = false;
    this.depthMD = 0.0;
    this.description = "";
  }

  /**
   * Create a barrier element with depth.
   *
   * @param type element type
   * @param name element identifier
   * @param depthMD depth in meters MD
   */
  public BarrierElement(ElementType type, String name, double depthMD) {
    this(type, name);
    this.depthMD = depthMD;
  }

  /**
   * Get the element type.
   *
   * @return element type
   */
  public ElementType getType() {
    return type;
  }

  /**
   * Get the element name.
   *
   * @return element name
   */
  public String getName() {
    return name;
  }

  /**
   * Get the current status.
   *
   * @return functional status
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Set the functional status.
   *
   * @param status new status
   */
  public void setStatus(Status status) {
    this.status = status;
  }

  /**
   * Check if the element is providing containment (intact or degraded).
   *
   * @return true if element is functional
   */
  public boolean isFunctional() {
    return status == Status.INTACT || status == Status.DEGRADED;
  }

  /**
   * Check if the element has been verified by testing.
   *
   * @return true if verified
   */
  public boolean isVerified() {
    return verified;
  }

  /**
   * Set the verification status.
   *
   * @param verified true if element has been tested
   */
  public void setVerified(boolean verified) {
    this.verified = verified;
  }

  /**
   * Get element depth in meters MD.
   *
   * @return depth in m MD
   */
  public double getDepthMD() {
    return depthMD;
  }

  /**
   * Set element depth in meters MD.
   *
   * @param depthMD depth in m MD
   */
  public void setDepthMD(double depthMD) {
    this.depthMD = depthMD;
  }

  /**
   * Get the element description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Set the element description.
   *
   * @param description description text
   */
  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public String toString() {
    return name + " (" + type.name() + ") - " + status.name();
  }
}
