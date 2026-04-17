package neqsim.process.mechanicaldesign.separator.internals;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A demisting internal with a drainage section below the main demisting pad.
 *
 * <p>
 * The drainage section captures re-entrained liquid that drips from the demisting pad. This design
 * is common in high-velocity wire mesh and vane pack installations where film drainage alone is
 * insufficient.
 * </p>
 *
 * <p>
 * The effective carry-over is reduced by the drainage efficiency:
 * </p>
 *
 * <p>
 * $$ \text{carry-over}_{eff} = \text{carry-over}_{pad} \times (1 - \eta_{drainage}) $$
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class DemistingInternalWithDrainage extends DemistingInternal {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(DemistingInternalWithDrainage.class);

  /**
   * Drainage efficiency [0..1]. Fraction of re-entrained liquid captured by the drainage section.
   * Typical values: 0.3–0.7 depending on drainage design and liquid load.
   */
  private double drainageEfficiency = 0.5;

  /**
   * Constructs a DemistingInternalWithDrainage with default parameters.
   */
  public DemistingInternalWithDrainage() {
    super();
  }

  /**
   * Constructs a DemistingInternalWithDrainage with a name.
   *
   * @param name the name of this demisting internal
   */
  public DemistingInternalWithDrainage(String name) {
    super(name);
  }

  /**
   * Constructs a DemistingInternalWithDrainage with a name and type.
   *
   * @param name the name of this demisting internal
   * @param type the demister type ("wire_mesh", "vane_pack", or "cyclone")
   */
  public DemistingInternalWithDrainage(String name, String type) {
    super(name, type);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Overrides to account for drainage section. The effective carry-over is the pad carry-over
   * reduced by the drainage efficiency.
   * </p>
   */
  @Override
  public double calcLiquidCarryOver(double gasVelocity, double maxGasVelocity) {
    double padCarryOver = super.calcLiquidCarryOver(gasVelocity, maxGasVelocity);
    return padCarryOver * (1.0 - drainageEfficiency);
  }

  /**
   * Gets the drainage efficiency.
   *
   * @return drainage efficiency [0..1]
   */
  public double getDrainageEfficiency() {
    return drainageEfficiency;
  }

  /**
   * Sets the drainage efficiency.
   *
   * @param drainageEfficiency drainage efficiency [0..1]
   */
  public void setDrainageEfficiency(double drainageEfficiency) {
    this.drainageEfficiency = drainageEfficiency;
  }
}
