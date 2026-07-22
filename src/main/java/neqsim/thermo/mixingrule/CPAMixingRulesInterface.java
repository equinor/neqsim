/*
 * CPAMixingRulesInterface.java
 *
 * Created on 26. februar 2001, 19:38
 */

package neqsim.thermo.mixingrule;

import neqsim.thermo.phase.PhaseInterface;

/**
 * CPAMixingRulesInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface CPAMixingRulesInterface extends MixingRulesInterface {
  // public double calcXi(int siteNumber, int compnumb, PhaseInterface phase,
  // double temperature, double pressure, int numbcomp);

  /**
   * calcDeltadT.
   *
   * @param siteNumber1 a int
   * @param siteNumber2 a int
   * @param compnumb1 a int
   * @param compnumb2 a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcDeltadT(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2, PhaseInterface phase,
      double temperature, double pressure, int numbcomp);

  /**
   * calcDeltadTdV.
   *
   * @param siteNumber1 a int
   * @param siteNumber2 a int
   * @param compnumb1 a int
   * @param compnumb2 a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcDeltadTdV(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2, PhaseInterface phase,
      double temperature, double pressure, int numbcomp);

  /**
   * calcDeltadTdT.
   *
   * @param siteNumber1 a int
   * @param siteNumber2 a int
   * @param compnumb1 a int
   * @param compnumb2 a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcDeltadTdT(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2, PhaseInterface phase,
      double temperature, double pressure, int numbcomp);

  /**
   * calcXi.
   *
   * @param assosScheme an array of int objects
   * @param assosScheme2 an array of int objects
   * @param siteNumber a int
   * @param compnumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcXi(int[][][] assosScheme, int[][][][] assosScheme2, int siteNumber, int compnumb,
      PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcDelta.
   *
   * @param siteNumber1 a int
   * @param siteNumber2 a int
   * @param compnumb1 a int
   * @param compnumb2 a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcDelta(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2, PhaseInterface phase,
      double temperature, double pressure, int numbcomp);

  /**
   * calcDeltadN.
   *
   * @param derivativeComp a int
   * @param siteNumber1 a int
   * @param siteNumber2 a int
   * @param compnumb1 a int
   * @param compnumb2 a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcDeltadN(int derivativeComp, int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
      PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcDeltadV.
   *
   * @param siteNumber1 a int
   * @param siteNumber2 a int
   * @param compnumb1 a int
   * @param compnumb2 a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcDeltadV(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2, PhaseInterface phase,
      double temperature, double pressure, int numbcomp);

  /**
   * calcDeltaNog.
   *
   * @param siteNumber1 a int
   * @param siteNumber2 a int
   * @param compnumb1 a int
   * @param compnumb2 a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcDeltaNog(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2, PhaseInterface phase,
      double temperature, double pressure, int numbcomp);
}
