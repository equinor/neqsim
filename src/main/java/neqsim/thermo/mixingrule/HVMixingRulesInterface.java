/*
 * HVmixingRuleInterface.java
 *
 * Created on 5. mai 2001, 17:48
 */

package neqsim.thermo.mixingrule;

/**
 * HVmixingRuleInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface HVMixingRulesInterface extends EosMixingRulesInterface {
  /**
   * setHVDijParameter.
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setHVDijParameter(int i, int j, double value);

  /**
   * setHVDijTParameter.
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setHVDijTParameter(int i, int j, double value);

  /**
   * getHVDijParameter.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getHVDijParameter(int i, int j);

  /**
   * getHVDijTParameter.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getHVDijTParameter(int i, int j);

  /**
   * getKijWongSandler.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getKijWongSandler(int i, int j);

  /**
   * setKijWongSandler.
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setKijWongSandler(int i, int j, double value);

  /**
   * setHValphaParameter.
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setHValphaParameter(int i, int j, double value);

  /**
   * getHValphaParameter.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getHValphaParameter(int i, int j);

  /**
   * Set the mixing rule type for a specific component pair.
   *
   * <p>
   * This determines whether the pair uses Huron-Vidal ("HV") or classic ("Classic") mixing.
   * </p>
   *
   * @param i first component index
   * @param j second component index
   * @param type "HV" for Huron-Vidal or "Classic" for classic mixing rule
   */
  public void setClassicOrHV(int i, int j, String type);
}
