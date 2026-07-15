package neqsim.process.equipment.compressor;

import java.io.Serializable;

/** A linear thermal conductance between two compressor temperature nodes. */
public class CompressorThermalLink implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Heat-transfer mechanism represented by a link. */
  public enum Mechanism {
    /** Solid conduction. */
    CONDUCTION,
    /** Fluid-to-surface or surface-to-fluid convection. */
    CONVECTION,
    /** Combined or empirically fitted heat transfer. */
    EFFECTIVE
  }

  private String fromNodeId;
  private String toNodeId;
  private double conductanceWPerK;
  private Mechanism mechanism = Mechanism.EFFECTIVE;

  /** No-argument constructor for JSON deserialization. */
  public CompressorThermalLink() {
  }

  /**
   * Create a thermal link.
   *
   * @param fromNodeId first node
   * @param toNodeId second node
   * @param conductanceWPerK effective conductance in W/K
   */
  public CompressorThermalLink(String fromNodeId, String toNodeId, double conductanceWPerK) {
    this(fromNodeId, toNodeId, conductanceWPerK, Mechanism.EFFECTIVE);
  }

  /**
   * Create a thermal link.
   *
   * @param fromNodeId first node
   * @param toNodeId second node
   * @param conductanceWPerK effective conductance in W/K
   * @param mechanism heat-transfer mechanism
   */
  public CompressorThermalLink(String fromNodeId, String toNodeId, double conductanceWPerK, Mechanism mechanism) {
    setFromNodeId(fromNodeId);
    setToNodeId(toNodeId);
    setConductanceWPerK(conductanceWPerK);
    setMechanism(mechanism);
  }

  /** @return first node identifier */
  public String getFromNodeId() {
    return fromNodeId;
  }

  /** @param fromNodeId first node identifier */
  public void setFromNodeId(String fromNodeId) {
    this.fromNodeId = requireNodeId(fromNodeId);
  }

  /** @return second node identifier */
  public String getToNodeId() {
    return toNodeId;
  }

  /** @param toNodeId second node identifier */
  public void setToNodeId(String toNodeId) {
    this.toNodeId = requireNodeId(toNodeId);
  }

  /** @return effective conductance in W/K */
  public double getConductanceWPerK() {
    return conductanceWPerK;
  }

  /** @param conductanceWPerK effective conductance in W/K */
  public void setConductanceWPerK(double conductanceWPerK) {
    if (!Double.isFinite(conductanceWPerK) || conductanceWPerK <= 0.0) {
      throw new IllegalArgumentException("thermal conductance must be finite and positive");
    }
    this.conductanceWPerK = conductanceWPerK;
  }

  /** @return heat-transfer mechanism */
  public Mechanism getMechanism() {
    return mechanism;
  }

  /** @param mechanism heat-transfer mechanism */
  public void setMechanism(Mechanism mechanism) {
    if (mechanism == null) {
      throw new IllegalArgumentException("thermal link mechanism must not be null");
    }
    this.mechanism = mechanism;
  }

  private static String requireNodeId(String id) {
    if (id == null || id.trim().isEmpty()) {
      throw new IllegalArgumentException("thermal link node id must not be empty");
    }
    return id;
  }
}
