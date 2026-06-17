package neqsim.process.processmodel;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents an explicit connection between two process elements identified by name and port. This
 * allows users and interchange formats (DEXPI) to declare connections independently of stream
 * objects, enabling topology-first model construction.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ProcessConnection implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Connection types in a process model.
   */
  public enum ConnectionType {
    /** Material / fluid flow connection. */
    MATERIAL,
    /** Energy stream connection. */
    ENERGY,
    /** Instrument / signal connection. */
    SIGNAL
  }

  private final String sourceEquipment;
  private final String sourcePort;
  private final String targetEquipment;
  private final String targetPort;
  private final ConnectionType type;
  private String sourceReferenceDesignation;
  private String targetReferenceDesignation;

  /**
   * Creates a new process connection.
   *
   * @param sourceEquipment name of the upstream equipment
   * @param sourcePort port name on the source equipment (e.g. "gasOut", "outlet")
   * @param targetEquipment name of the downstream equipment
   * @param targetPort port name on the target equipment (e.g. "inlet")
   * @param type connection type
   */
  public ProcessConnection(String sourceEquipment, String sourcePort, String targetEquipment,
      String targetPort, ConnectionType type) {
    this.sourceEquipment = Objects.requireNonNull(sourceEquipment);
    this.sourcePort = sourcePort != null ? sourcePort : "outlet";
    this.targetEquipment = Objects.requireNonNull(targetEquipment);
    this.targetPort = targetPort != null ? targetPort : "inlet";
    this.type = type != null ? type : ConnectionType.MATERIAL;
  }

  /**
   * Creates a material connection with default port names.
   *
   * @param sourceEquipment name of the upstream equipment
   * @param targetEquipment name of the downstream equipment
   */
  public ProcessConnection(String sourceEquipment, String targetEquipment) {
    this(sourceEquipment, "outlet", targetEquipment, "inlet", ConnectionType.MATERIAL);
  }

  /**
   * Returns the source equipment name.
   *
   * @return source equipment name
   */
  public String getSourceEquipment() {
    return sourceEquipment;
  }

  /**
   * Returns the source port name.
   *
   * @return source port name
   */
  public String getSourcePort() {
    return sourcePort;
  }

  /**
   * Returns the target equipment name.
   *
   * @return target equipment name
   */
  public String getTargetEquipment() {
    return targetEquipment;
  }

  /**
   * Returns the target port name.
   *
   * @return target port name
   */
  public String getTargetPort() {
    return targetPort;
  }

  /**
   * Returns the connection type.
   *
   * @return connection type
   */
  public ConnectionType getType() {
    return type;
  }

  /**
   * Returns the IEC 81346 reference designation of the source equipment, if set.
   *
   * @return source reference designation string, or {@code null}
   */
  public String getSourceReferenceDesignation() {
    return sourceReferenceDesignation;
  }

  /**
   * Sets the IEC 81346 reference designation of the source equipment.
   *
   * @param sourceReferenceDesignation source ref des string (e.g. "=A1.B1")
   */
  public void setSourceReferenceDesignation(String sourceReferenceDesignation) {
    this.sourceReferenceDesignation = sourceReferenceDesignation;
  }

  /**
   * Returns the IEC 81346 reference designation of the target equipment, if set.
   *
   * @return target reference designation string, or {@code null}
   */
  public String getTargetReferenceDesignation() {
    return targetReferenceDesignation;
  }

  /**
   * Sets the IEC 81346 reference designation of the target equipment.
   *
   * @param targetReferenceDesignation target ref des string (e.g. "=A1.K1")
   */
  public void setTargetReferenceDesignation(String targetReferenceDesignation) {
    this.targetReferenceDesignation = targetReferenceDesignation;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(sourceEquipment, sourcePort, targetEquipment, targetPort, type);
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ProcessConnection)) {
      return false;
    }
    ProcessConnection other = (ProcessConnection) obj;
    return Objects.equals(sourceEquipment, other.sourceEquipment)
        && Objects.equals(sourcePort, other.sourcePort)
        && Objects.equals(targetEquipment, other.targetEquipment)
        && Objects.equals(targetPort, other.targetPort) && type == other.type;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return sourceEquipment + "." + sourcePort + " -> " + targetEquipment + "." + targetPort + " ["
        + type + "]";
  }
}
