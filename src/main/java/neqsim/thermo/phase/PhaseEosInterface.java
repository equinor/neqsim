/*
 * PhaseEosInterface.java
 *
 * Created on 5. juni 2000, 19:20
 */

package neqsim.thermo.phase;

import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * PhaseEosInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface PhaseEosInterface extends PhaseInterface {
  /** {@inheritDoc} */
  @Override
  double getMolarVolume();

  /**
   * getEosMixingRule.
   *
   * @return a {@link neqsim.thermo.mixingrule.EosMixingRulesInterface} object
   */
  public EosMixingRulesInterface getEosMixingRule();

  /**
   * getMixingRuleName.
   *
   * @return a {@link java.lang.String} object
   */
  public String getMixingRuleName();

  /**
   * calcPressure.
   *
   * @return a double
   */
  public double calcPressure();

  /**
   * calcPressuredV.
   *
   * @return a double
   */
  public double calcPressuredV();

  /**
   * getPressureRepulsive.
   *
   * @return a double
   */
  public double getPressureRepulsive();

  /**
   * getPressureAttractive.
   *
   * @return a double
   */
  public double getPressureAttractive();

  /**
   * displayInteractionCoefficients.
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
   * F.
   *
   * @return a double
   */
  public double F();

  /**
   * dFdN.
   *
   * @param i a int
   * @return a double
   */
  public double dFdN(int i);

  /**
   * dFdNdN.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double dFdNdN(int i, int j);

  /**
   * dFdNdV.
   *
   * @param i a int
   * @return a double
   */
  public double dFdNdV(int i);

  /**
   * dFdNdT.
   *
   * @param i a int
   * @return a double
   */
  public double dFdNdT(int i);

  /**
   * getAresTV.
   *
   * @return a double
   */
  public double getAresTV();

  /**
   * getSresTV.
   *
   * @return a double
   */
  public double getSresTV();
}
