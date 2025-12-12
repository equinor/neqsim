package neqsim.process.mechanicaldesign.separator.primaryseparation;

/**
 * <p>
 * InletVaneWithMeshpad class.
 * </p>
 * 
 * Represents an inlet vane primary separation device combined with a meshpad. This configuration
 * provides enhanced separation through both initial vane deflection and subsequent coalescence in
 * the meshpad.
 * 
 * Requires specification of the vertical distance between the vane and meshpad, and the free
 * distance above the meshpad.
 *
 * @author User
 * @version 1.0
 */
public class InletVaneWithMeshpad extends InletVane {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Vertical distance between inlet vane and meshpad in m. */
  private double vaneToMeshpadDistance;

  /** Free distance above the meshpad in m. */
  private double freeDistanceAboveMeshpad;

  /**
   * Constructor for InletVaneWithMeshpad.
   *
   * @param name the name of the device
   * @param inletNozzleDiameter the inlet nozzle diameter in m
   * @param vaneToMeshpadDistance vertical distance between vane and meshpad in m
   * @param freeDistanceAboveMeshpad free distance above the meshpad in m
   */
  public InletVaneWithMeshpad(String name, double inletNozzleDiameter, double vaneToMeshpadDistance,
      double freeDistanceAboveMeshpad) {
    super(name, inletNozzleDiameter, 4.0);
    this.vaneToMeshpadDistance = vaneToMeshpadDistance;
    this.freeDistanceAboveMeshpad = freeDistanceAboveMeshpad;
  }

  /**
   * Get the vertical distance between vane and meshpad.
   *
   * @return distance in m
   */
  public double getVaneToMeshpadDistance() {
    return vaneToMeshpadDistance;
  }

  /**
   * Set the vertical distance between vane and meshpad.
   *
   * @param vaneToMeshpadDistance the distance in m
   */
  public void setVaneToMeshpadDistance(double vaneToMeshpadDistance) {
    this.vaneToMeshpadDistance = vaneToMeshpadDistance;
  }

  /**
   * Get the free distance above the meshpad.
   *
   * @return distance in m
   */
  public double getFreeDistanceAboveMeshpad() {
    return freeDistanceAboveMeshpad;
  }

  /**
   * Set the free distance above the meshpad.
   *
   * @param freeDistanceAboveMeshpad the distance in m
   */
  public void setFreeDistanceAboveMeshpad(double freeDistanceAboveMeshpad) {
    this.freeDistanceAboveMeshpad = freeDistanceAboveMeshpad;
  }

  /**
   * Calculate liquid carry-over for inlet vane with meshpad configuration.
   * 
   * This uses a specific carry-over equation for the vane-meshpad configuration. The equation
   * accounts for: - Vane deflection efficiency - Inlet velocity effects - Vertical distance between
   * vane and meshpad (settling time) - Free space above meshpad (coalescence volume)
   * 
   * Carry-over is reduced by both the vane deflection and the meshpad coalescence.
   *
   * @return liquid carry-over (mass fraction)
   */
  @Override
  public double calcLiquidCarryOver() {
    if (separator == null) {
      return 0.0;
    }

    double inletVelocity = separator.getInletGasVelocity();
    double inletLiquidContent = separator.getInletLiquidContent();

    // Vane efficiency (deflection angle effect)
    double vaneEfficiency = 0.95; // Assuming 90 degree deflection gives high efficiency

    // Velocity effect (higher velocity increases carry-over)
    double velocityFactor = Math.min(1.0, inletVelocity / 10.0);

    // Vane carry-over contribution
    double vaneCarryOver = velocityFactor * (1.0 - 0.5 * vaneEfficiency);

    // Meshpad coalescence efficiency based on free distance above it
    // More free distance allows better coalescence and settling
    double meshpadCoalescenceEfficiency = Math.min(freeDistanceAboveMeshpad / 0.5, // 0.5m is
                                                                                   // reference
                                                                                   // height
        1.0);

    // Settling efficiency based on vane-to-meshpad distance
    // More distance allows droplets to settle before reaching meshpad
    double settlingEfficiency = Math.min(vaneToMeshpadDistance / 0.3, // 0.3m is reference distance
        1.0);

    // Combined carry-over: reduced by both meshpad coalescence and settling
    double totalCarryOverFactor = vaneCarryOver * (1.0 - 0.4 * meshpadCoalescenceEfficiency)
        * (1.0 - 0.3 * settlingEfficiency);

    return totalCarryOverFactor * inletLiquidContent;
  }

  @Override
  public String toString() {
    return "InletVaneWithMeshpad [name=" + name + ", nozzleDiameter=" + inletNozzleDiameter + " m, "
        + "vaneToMeshpadDistance=" + vaneToMeshpadDistance + " m, " + "freeDistanceAboveMeshpad="
        + freeDistanceAboveMeshpad + " m]";
  }
}
