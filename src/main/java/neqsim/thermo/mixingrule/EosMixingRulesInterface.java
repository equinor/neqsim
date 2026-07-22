/*
 * EosMixingRulesInterface.java
 *
 * Created on 4. juni 2000, 12:38
 */

package neqsim.thermo.mixingrule;

import neqsim.thermo.phase.PhaseInterface;

/**
 * EosMixingRulesInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface EosMixingRulesInterface extends MixingRulesInterface {
  /**
   * calcA.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcB.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcB(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcAi.
   *
   * @param compnumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcAi(int compnumb, PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcBi.
   *
   * @param compnumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcBi(int compnumb, PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcBij.
   *
   * @param compnumb a int
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcBij(int compnumb, int j, PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcAij.
   *
   * @param compnumb a int
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcAij(int compnumb, int j, PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * setBinaryInteractionParameterji.
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setBinaryInteractionParameterji(int i, int j, double value);

  /**
   * calcAT.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcAT(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcATT.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcATT(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcAiT.
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * setBinaryInteractionParameter.
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setBinaryInteractionParameter(int i, int j, double value);

  /**
   * getBinaryInteractionParameter.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getBinaryInteractionParameter(int i, int j);

  /**
   * setBinaryInteractionParameterT1.
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setBinaryInteractionParameterT1(int i, int j, double value);

  /**
   * getBinaryInteractionParameterT1.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getBinaryInteractionParameterT1(int i, int j);

  /**
   * setCalcEOSInteractionParameters.
   *
   * @param CalcEOSInteractionParameters a boolean
   */
  public void setCalcEOSInteractionParameters(boolean CalcEOSInteractionParameters);

  /**
   * setnEOSkij.
   *
   * @param n a double
   */
  public void setnEOSkij(double n);

  /**
   * setMixingRuleGEModel.
   *
   * @param GEmodel a {@link java.lang.String} object
   */
  public void setMixingRuleGEModel(java.lang.String GEmodel);

  /**
   * setBinaryInteractionParameterij.
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setBinaryInteractionParameterij(int i, int j, double value);

  /**
   * getBmixType.
   *
   * @return a int
   */
  public int getBmixType();

  /**
   * setBmixType.
   *
   * @param bmixType2 a int
   */
  public void setBmixType(int bmixType2);

  /**
   * getGEPhase.
   *
   * @return a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public PhaseInterface getGEPhase();

  /**
   * getBinaryInteractionParameters.
   *
   * @return an array of type double
   */
  public double[][] getBinaryInteractionParameters();
  // double calcA2(PhaseInterface phase, double temperature, double pressure, int
  // numbcomp);
  // double calcB2(PhaseInterface phase, double temperature, double pressure, int
  // numbcomp);
  // public double calcA(ComponentInterface[] te, double temperature, double
  // pressure, int numberOfComponents);
  // public double calcB(ComponentInterface[] te, double temperature, double
  // pressure, int numberOfComponents);
}
