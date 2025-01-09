/*
 * EosMixingRulesInterface.java
 *
 * Created on 4. juni 2000, 12:38
 */

package neqsim.thermo.mixingrule;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * EosMixingRulesInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface EosMixingRulesInterface extends MixingRulesInterface {
  /**
   * <p>
   * calcA.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * <p>
   * calcB.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcB(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * <p>
   * calcAi.
   * </p>
   *
   * @param compnumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcAi(int compnumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp);

  /**
   * <p>
   * calcBi.
   * </p>
   *
   * @param compnumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcBi(int compnumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp);

  /**
   * <p>
   * calcBij.
   * </p>
   *
   * @param compnumb a int
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcBij(int compnumb, int j, PhaseInterface phase, double temperature, double pressure,
      int numbcomp);

  /**
   * <p>
   * calcAij.
   * </p>
   *
   * @param compnumb a int
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcAij(int compnumb, int j, PhaseInterface phase, double temperature, double pressure,
      int numbcomp);

  /**
   * <p>
   * setBinaryInteractionParameterji.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setBinaryInteractionParameterji(int i, int j, double value);

  /**
   * <p>
   * calcAT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcAT(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * <p>
   * calcATT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcATT(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * <p>
   * calcAiT.
   * </p>
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp);

  /**
   * <p>
   * setBinaryInteractionParameter.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setBinaryInteractionParameter(int i, int j, double value);

  /**
   * <p>
   * getBinaryInteractionParameter.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getBinaryInteractionParameter(int i, int j);

  /**
   * <p>
   * setBinaryInteractionParameterT1.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setBinaryInteractionParameterT1(int i, int j, double value);

  /**
   * <p>
   * getBinaryInteractionParameterT1.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getBinaryInteractionParameterT1(int i, int j);

  /**
   * <p>
   * setCalcEOSInteractionParameters.
   * </p>
   *
   * @param CalcEOSInteractionParameters a boolean
   */
  public void setCalcEOSInteractionParameters(boolean CalcEOSInteractionParameters);

  /**
   * <p>
   * setnEOSkij.
   * </p>
   *
   * @param n a double
   */
  public void setnEOSkij(double n);

  /**
   * <p>
   * setMixingRuleGEModel.
   * </p>
   *
   * @param GEmodel a {@link java.lang.String} object
   */
  public void setMixingRuleGEModel(java.lang.String GEmodel);

  /**
   * <p>
   * setBinaryInteractionParameterij.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setBinaryInteractionParameterij(int i, int j, double value);

  /**
   * <p>
   * getBmixType.
   * </p>
   *
   * @return a int
   */
  public int getBmixType();

  /**
   * <p>
   * setBmixType.
   * </p>
   *
   * @param bmixType2 a int
   */
  public void setBmixType(int bmixType2);

  /**
   * <p>
   * getGEPhase.
   * </p>
   *
   * @return a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public PhaseInterface getGEPhase();

  /**
   * <p>
   * getBinaryInteractionParameters.
   * </p>
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
