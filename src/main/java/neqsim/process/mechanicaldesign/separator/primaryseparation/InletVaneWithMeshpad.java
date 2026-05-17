package neqsim.process.mechanicaldesign.separator.primaryseparation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Inlet vane with downstream mesh pad for enhanced primary separation.
 *
 * <p>
 * Combines the high-momentum tolerance of an inlet vane with the fine droplet capture of a mesh
 * pad. The mesh pad is placed downstream of the inlet vane to capture small droplets that pass
 * through the vane device. This combination provides better separation performance at higher gas
 * loads.
 * </p>
 *
 * <p>
 * Typical performance: bulk efficiency 90-95%, max inlet momentum 6000 Pa.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class InletVaneWithMeshpad extends PrimarySeparation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(InletVaneWithMeshpad.class);

  /** K-factor for the downstream mesh pad [m/s]. */
  private double meshPadKFactor = 0.107;

  /**
   * Constructs an InletVaneWithMeshpad with default parameters.
   */
  public InletVaneWithMeshpad() {
    super();
    setMaxInletMomentum(6000.0);
    setBulkSeparationEfficiency(0.92);
  }

  /**
   * Constructs an InletVaneWithMeshpad with a name.
   *
   * @param name the name of this inlet device
   */
  public InletVaneWithMeshpad(String name) {
    super(name);
    setMaxInletMomentum(6000.0);
    setBulkSeparationEfficiency(0.92);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Overrides to provide improved carry-over due to the mesh pad. The mesh pad further reduces
   * carry-over by capturing fine droplets.
   * </p>
   */
  @Override
  public double calcLiquidCarryOver(double mixtureDensity, double volumetricFlowRate) {
    double baseCarryOver = super.calcLiquidCarryOver(mixtureDensity, volumetricFlowRate);
    // Mesh pad captures an additional fraction of what passes the vane
    double meshPadEfficiency = 0.90;
    return baseCarryOver * (1.0 - meshPadEfficiency);
  }

  /**
   * Gets the K-factor for the downstream mesh pad.
   *
   * @return mesh pad K-factor [m/s]
   */
  public double getMeshPadKFactor() {
    return meshPadKFactor;
  }

  /**
   * Sets the K-factor for the downstream mesh pad.
   *
   * @param meshPadKFactor mesh pad K-factor [m/s]
   */
  public void setMeshPadKFactor(double meshPadKFactor) {
    this.meshPadKFactor = meshPadKFactor;
  }
}
