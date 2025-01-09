/*
 * PhaseEosInterface.java
 *
 * Created on 5. juni 2000, 19:20
 */

package neqsim.thermo.phase;

import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * PhaseEosInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface PhaseEosInterface extends PhaseInterface {
  /** {@inheritDoc} */
  @Override
  double getMolarVolume();

  /**
   * <p>
   * getMixingRule.
   * </p>
   *
   * @return a {@link neqsim.thermo.mixingrule.EosMixingRulesInterface} object
   */
  public EosMixingRulesInterface getEosMixingRule();

  /**
   * <p>
   * getMixingRuleName.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getMixingRuleName();

  /**
   * <p>
   * calcPressure.
   * </p>
   *
   * @return a double
   */
  public double calcPressure();

  /**
   * <p>
   * calcPressuredV.
   * </p>
   *
   * @return a double
   */
  public double calcPressuredV();

  /**
   * <p>
   * getPressureRepulsive.
   * </p>
   *
   * @return a double
   */
  public double getPressureRepulsive();

  /**
   * <p>
   * getPressureAttractive.
   * </p>
   *
   * @return a double
   */
  public double getPressureAttractive();

  /**
   * <p>
   * displayInteractionCoefficients.
   * </p>
   *
   * @param intType a {@link java.lang.String} object
   */
  @ExcludeFromJacocoGeneratedReport
  public void displayInteractionCoefficients(String intType);
  // public double getA();
  // public double getB();
  // double calcA(ComponentEosInterface[] compArray, double temperature, double
  // pressure, int numbcomp);
  // double calcB(ComponentEosInterface[] compArray, double temperature, double
  // pressure, int numbcomp);
  // double calcA(ComponentEosInterface[] compArray, double temperature, double
  // pressure, int numbcomp);
  // double calcB(ComponentEosInterface[] compArray, double temperature, double
  // pressure, int numbcomp);

  /**
   * <p>
   * F.
   * </p>
   *
   * @return a double
   */
  public double F();

  /**
   * <p>
   * dFdN.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double dFdN(int i);

  /**
   * <p>
   * dFdNdN.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double dFdNdN(int i, int j);

  /**
   * <p>
   * dFdNdV.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double dFdNdV(int i);

  /**
   * <p>
   * dFdNdT.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double dFdNdT(int i);

  /**
   * <p>
   * getAresTV.
   * </p>
   *
   * @return a double
   */
  public double getAresTV();

  /**
   * <p>
   * getSresTV.
   * </p>
   *
   * @return a double
   */
  public double getSresTV();
}
