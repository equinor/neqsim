package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import neqsim.thermo.system.SystemInterface;

/**
 * CompressorPropertyProfile class.
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
   * Constructor for CompressorPropertyProfile.
   */
  public CompressorPropertyProfile() {
  }

  /**
   * addFluid.
   *
   * @param inputFLuid a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void addFluid(SystemInterface inputFLuid) {
    inputFLuid.initPhysicalProperties();
    fluid.add(inputFLuid);
  }

  /**
   * isActive.
   *
   * @return a boolean
   */
  public boolean isActive() {
    return isActive;
  }

  /**
   * setActive.
   *
   * @param isActive a boolean
   */
  public void setActive(boolean isActive) {
    this.isActive = isActive;
    fluid.clear();
  }

  /**
   * Getter for the field <code>fluid</code>.
   *
   * @return a {@link java.util.ArrayList} object
   */
  public ArrayList<SystemInterface> getFluid() {
    return fluid;
  }

  /**
   * Setter for the field <code>fluid</code>.
   *
   * @param fluid a {@link java.util.ArrayList} object
   */
  public void setFluid(ArrayList<SystemInterface> fluid) {
    this.fluid = fluid;
  }
}
