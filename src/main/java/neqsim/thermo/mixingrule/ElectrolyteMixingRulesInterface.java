/*
 * ElectrolyteMixingRulesInterface.java
 *
 * Created on 26. februar 2001, 19:38
 */

package neqsim.thermo.mixingrule;

import neqsim.thermo.phase.PhaseInterface;

/**
 * ElectrolyteMixingRulesInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ElectrolyteMixingRulesInterface extends MixingRulesInterface {
  /**
   * calcWij.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void calcWij(PhaseInterface phase);

  /**
   * calcWij.
   *
   * @param compNumbi a int
   * @param compNumj a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcWij(int compNumbi, int compNumj, PhaseInterface phase, double temperature, double pressure,
      int numbcomp);

  /**
   * setWijParameter.
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setWijParameter(int i, int j, double value);

  /**
   * getWijParameter.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getWijParameter(int i, int j);

  /**
   * setWijT1Parameter.
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setWijT1Parameter(int i, int j, double value);

  /**
   * gettWijT1Parameter.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double gettWijT1Parameter(int i, int j);

  /**
   * setWijT2Parameter.
   *
   * @param i a int
   * @param j a int
   * @param value a double
   */
  public void setWijT2Parameter(int i, int j, double value);

  /**
   * gettWijT2Parameter.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double gettWijT2Parameter(int i, int j);

  /**
   * getWij.
   *
   * @param i a int
   * @param j a int
   * @param temperature a double
   * @return a double
   */
  public double getWij(int i, int j, double temperature);

  /**
   * getWijT.
   *
   * @param i a int
   * @param j a int
   * @param temperature a double
   * @return a double
   */
  public double getWijT(int i, int j, double temperature);

  /**
   * getWijTT.
   *
   * @param i a int
   * @param j a int
   * @param temperature a double
   * @return a double
   */
  public double getWijTT(int i, int j, double temperature);

  /**
   * calcW.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcW(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcWi.
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcWi(int compNumb, PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcWiT.
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcWiT(int compNumb, PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcWT.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcWT(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcWTT.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcWTT(PhaseInterface phase, double temperature, double pressure, int numbcomp);
}
