package neqsim.thermo.characterization;

import neqsim.thermo.system.SystemInterface;

/**
 * Recombine class.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Recombine {
  SystemInterface gas, oil;
  private SystemInterface recombinedSystem = null;
  private double GOR = 1000.0;
  private double oilDesnity = 0.8;

  /**
   * Constructor for Recombine.
   *
   * @param gas a {@link neqsim.thermo.system.SystemInterface} object
   * @param oil a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Recombine(SystemInterface gas, SystemInterface oil) {
  }

  /**
   * runRecombination.
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface runRecombination() {
    return getRecombinedSystem();
  }

  /**
   * getGOR.
   *
   * @return the GOR
   */
  public double getGOR() {
    return GOR;
  }

  /**
   * setGOR.
   *
   * @param GOR the GOR to set
   */
  public void setGOR(double GOR) {
    this.GOR = GOR;
  }

  /**
   * Getter for the field <code>oilDesnity</code>.
   *
   * @return the oilDesnity
   */
  public double getOilDesnity() {
    return oilDesnity;
  }

  /**
   * Setter for the field <code>oilDesnity</code>.
   *
   * @param oilDesnity the oilDesnity to set
   */
  public void setOilDesnity(double oilDesnity) {
    this.oilDesnity = oilDesnity;
  }

  /**
   * Getter for the field <code>recombinedSystem</code>.
   *
   * @return the recombinedSystem
   */
  public SystemInterface getRecombinedSystem() {
    return recombinedSystem;
  }
}
