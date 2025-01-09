/*
 * HVmixingRuleInterface.java
 *
 * Created on 5. mai 2001, 17:48
 */

package neqsim.thermo.mixingrule;

/**
 * <p>
 * HVmixingRuleInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface HVMixingRulesInterface extends EosMixingRulesInterface {
  /**
   * <p>
   * setHVDijParameter.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setHVDijParameter(int i, int j, double value);

  /**
   * <p>
   * setHVDijTParameter.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setHVDijTParameter(int i, int j, double value);

  /**
   * <p>
   * getHVDijParameter.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getHVDijParameter(int i, int j);

  /**
   * <p>
   * getHVDijTParameter.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getHVDijTParameter(int i, int j);

  /**
   * <p>
   * getKijWongSandler.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getKijWongSandler(int i, int j);

  /**
   * <p>
   * setKijWongSandler.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setKijWongSandler(int i, int j, double value);

  /**
   * <p>
   * setHValphaParameter.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setHValphaParameter(int i, int j, double value);

  /**
   * <p>
   * getHValphaParameter.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getHValphaParameter(int i, int j);
}
