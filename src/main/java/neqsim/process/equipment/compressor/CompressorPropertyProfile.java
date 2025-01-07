package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * CompressorPropertyProfile class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class CompressorPropertyProfile implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private ArrayList<SystemInterface> fluid = new ArrayList<SystemInterface>();
  private boolean isActive = false;

  /**
   * <p>
   * Constructor for CompressorPropertyProfile.
   * </p>
   */
  public CompressorPropertyProfile() {}

  /**
   * <p>
   * addFluid.
   * </p>
   *
   * @param inputFLuid a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void addFluid(SystemInterface inputFLuid) {
    inputFLuid.initPhysicalProperties();
    fluid.add(inputFLuid);
  }

  /**
   * <p>
   * isActive.
   * </p>
   *
   * @return a boolean
   */
  public boolean isActive() {
    return isActive;
  }

  /**
   * <p>
   * setActive.
   * </p>
   *
   * @param isActive a boolean
   */
  public void setActive(boolean isActive) {
    this.isActive = isActive;
    fluid.clear();
  }

  /**
   * <p>
   * Getter for the field <code>fluid</code>.
   * </p>
   *
   * @return a {@link java.util.ArrayList} object
   */
  public ArrayList<SystemInterface> getFluid() {
    return fluid;
  }

  /**
   * <p>
   * Setter for the field <code>fluid</code>.
   * </p>
   *
   * @param fluid a {@link java.util.ArrayList} object
   */
  public void setFluid(ArrayList<SystemInterface> fluid) {
    this.fluid = fluid;
  }
}
