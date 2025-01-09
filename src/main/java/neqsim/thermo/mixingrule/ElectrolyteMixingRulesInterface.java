/*
 * ElectrolyteMixingRulesInterface.java
 *
 * Created on 26. februar 2001, 19:38
 */

package neqsim.thermo.mixingrule;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ElectrolyteMixingRulesInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ElectrolyteMixingRulesInterface extends MixingRulesInterface {
  /**
   * <p>
   * calcWij.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void calcWij(PhaseInterface phase);

  /**
   * <p>
   * calcWij.
   * </p>
   *
   * @param compNumbi a int
   * @param compNumj a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcWij(int compNumbi, int compNumj, PhaseInterface phase, double temperature,
      double pressure, int numbcomp);

  /**
   * <p>
   * setWijParameter.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setWijParameter(int i, int j, double value);

  /**
   * <p>
   * getWijParameter.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getWijParameter(int i, int j);

  /**
   * <p>
   * setWijT1Parameter.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setWijT1Parameter(int i, int j, double value);

  /**
   * <p>
   * gettWijT1Parameter.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double gettWijT1Parameter(int i, int j);

  /**
   * <p>
   * setWijT2Parameter.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setWijT2Parameter(int i, int j, double value);

  /**
   * <p>
   * gettWijT2Parameter.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double gettWijT2Parameter(int i, int j);

  /**
   * <p>
   * getWij.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param temperature a double
   * @return a double
   */
  public double getWij(int i, int j, double temperature);

  /**
   * <p>
   * getWijT.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param temperature a double
   * @return a double
   */
  public double getWijT(int i, int j, double temperature);

  /**
   * <p>
   * getWijTT.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param temperature a double
   * @return a double
   */
  public double getWijTT(int i, int j, double temperature);

  /**
   * <p>
   * calcW.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcW(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * <p>
   * calcWi.
   * </p>
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcWi(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp);

  /**
   * <p>
   * calcWiT.
   * </p>
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcWiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp);

  /**
   * <p>
   * calcWT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcWT(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * <p>
   * calcWTT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcWTT(PhaseInterface phase, double temperature, double pressure, int numbcomp);
}
