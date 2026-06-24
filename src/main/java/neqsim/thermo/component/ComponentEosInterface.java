/*
 * ComponentEosInterface.java
 *
 * Created on 4. juni 2000, 13:35
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * ComponentEosInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ComponentEosInterface extends ComponentInterface {
  /**
   * aT.
   *
   * @param temperature a double
   * @return a double
   */
  double aT(double temperature);

  /**
   * diffaT.
   *
   * @param temperature a double
   * @return a double
   */
  public double diffaT(double temperature);

  /**
   * diffdiffaT.
   *
   * @param temperature a double
   * @return a double
   */
  public double diffdiffaT(double temperature);

  /**
   * getb.
   *
   * @return a double
   */
  public double getb();

  /**
   * getAiT.
   *
   * @return a double
   */
  public double getAiT();

  /**
   * geta.
   *
   * @return a double
   */
  public double geta();

  /**
   * getaDiffT.
   *
   * @return a double
   */
  public double getaDiffT();

  /**
   * getaDiffDiffT.
   *
   * @return a double
   */
  public double getaDiffDiffT();

  /**
   * getaT.
   *
   * @return a double
   */
  public double getaT();

  /**
   * getBij.
   *
   * @param j a int
   * @return a double
   */
  public double getBij(int j);

  /**
   * getAij.
   *
   * @param j a int
   * @return a double
   */
  public double getAij(int j);

  /**
   * getBi.
   *
   * @return a double
   */
  public double getBi();

  /**
   * getAi.
   *
   * @return a double
   */
  public double getAi();

  /**
   * calca.
   *
   * @return a double
   */
  public double calca();

  /**
   * calcb.
   *
   * @return a double
   */
  public double calcb();

  /**
   * getAder.
   *
   * @return a double
   */
  public double getAder();

  /**
   * setAder.
   *
   * @param val a double
   */
  public void setAder(double val);

  /**
   * getDeltaEosParameters.
   *
   * @return an array of type double
   */
  public double[] getDeltaEosParameters();

  /**
   * getdAdndn.
   *
   * @param j a int
   * @return a double
   */
  public double getdAdndn(int j);

  /**
   * setdAdndn.
   *
   * @param jComp a int
   * @param val a double
   */
  public void setdAdndn(int jComp, double val);

  /**
   * getdAdT.
   *
   * @return a double
   */
  public double getdAdT();

  /**
   * setdAdT.
   *
   * @param val a double
   */
  public void setdAdT(double val);

  /**
   * setdAdTdT.
   *
   * @param val a double
   */
  public void setdAdTdT(double val);

  /**
   * getBder.
   *
   * @return a double
   */
  public double getBder();

  /**
   * setBder.
   *
   * @param val a double
   */
  public void setBder(double val);

  /**
   * getdBdndn.
   *
   * @param j a int
   * @return a double
   */
  public double getdBdndn(int j);

  /**
   * setdBdndn.
   *
   * @param jComp a int
   * @param val a double
   */
  public void setdBdndn(int jComp, double val);

  /**
   * getdBdT.
   *
   * @return a double
   */
  public double getdBdT();

  /**
   * setdBdTdT.
   *
   * @param val a double
   */
  public void setdBdTdT(double val);

  /**
   * getdBdndT.
   *
   * @return a double
   */
  public double getdBdndT();

  /**
   * setdBdndT.
   *
   * @param val a double
   */
  public void setdBdndT(double val);

  /**
   * setdAdTdn.
   *
   * @param val a double
   */
  public void setdAdTdn(double val);

  /**
   * getdAdTdn.
   *
   * @return a double
   */
  public double getdAdTdn();

  /**
   * dFdN.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature, double pressure);

  /**
   * dFdNdT.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature, double pressure);

  /**
   * dFdNdV.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature, double pressure);

  /**
   * dFdNdN.
   *
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature, double pressure);
}
