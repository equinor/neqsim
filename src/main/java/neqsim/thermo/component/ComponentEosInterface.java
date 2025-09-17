/*
 * ComponentEosInterface.java
 *
 * Created on 4. juni 2000, 13:35
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentEosInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ComponentEosInterface extends ComponentInterface {
  /**
   * <p>
   * aT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  double aT(double temperature);

  /**
   * <p>
   * diffaT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double diffaT(double temperature);

  /**
   * <p>
   * diffdiffaT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double diffdiffaT(double temperature);

  /**
   * <p>
   * getb.
   * </p>
   *
   * @return a double
   */
  public double getb();

  /**
   * <p>
   * getAiT.
   * </p>
   *
   * @return a double
   */
  public double getAiT();

  /**
   * <p>
   * geta.
   * </p>
   *
   * @return a double
   */
  public double geta();

  /**
   * <p>
   * getaDiffT.
   * </p>
   *
   * @return a double
   */
  public double getaDiffT();

  /**
   * <p>
   * getaDiffDiffT.
   * </p>
   *
   * @return a double
   */
  public double getaDiffDiffT();

  /**
   * <p>
   * getaT.
   * </p>
   *
   * @return a double
   */
  public double getaT();

  /**
   * <p>
   * getSqrtAt.
   * </p>
   *
   * @return a double
   */
  double getSqrtAt();

  /**
   * <p>
   * getBij.
   * </p>
   *
   * @param j a int
   * @return a double
   */
  public double getBij(int j);

  /**
   * <p>
   * getAij.
   * </p>
   *
   * @param j a int
   * @return a double
   */
  public double getAij(int j);

  /**
   * <p>
   * getBi.
   * </p>
   *
   * @return a double
   */
  public double getBi();

  /**
   * <p>
   * getAi.
   * </p>
   *
   * @return a double
   */
  public double getAi();

  /**
   * <p>
   * calca.
   * </p>
   *
   * @return a double
   */
  public double calca();

  /**
   * <p>
   * calcb.
   * </p>
   *
   * @return a double
   */
  public double calcb();

  /**
   * <p>
   * getAder.
   * </p>
   *
   * @return a double
   */
  public double getAder();

  /**
   * <p>
   * setAder.
   * </p>
   *
   * @param val a double
   */
  public void setAder(double val);

  /**
   * <p>
   * getDeltaEosParameters.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getDeltaEosParameters();

  /**
   * <p>
   * getdAdndn.
   * </p>
   *
   * @param j a int
   * @return a double
   */
  public double getdAdndn(int j);

  /**
   * <p>
   * setdAdndn.
   * </p>
   *
   * @param jComp a int
   * @param val a double
   */
  public void setdAdndn(int jComp, double val);

  /**
   * <p>
   * getdAdT.
   * </p>
   *
   * @return a double
   */
  public double getdAdT();

  /**
   * <p>
   * setdAdT.
   * </p>
   *
   * @param val a double
   */
  public void setdAdT(double val);

  /**
   * <p>
   * setdAdTdT.
   * </p>
   *
   * @param val a double
   */
  public void setdAdTdT(double val);

  /**
   * <p>
   * getBder.
   * </p>
   *
   * @return a double
   */
  public double getBder();

  /**
   * <p>
   * setBder.
   * </p>
   *
   * @param val a double
   */
  public void setBder(double val);

  /**
   * <p>
   * getdBdndn.
   * </p>
   *
   * @param j a int
   * @return a double
   */
  public double getdBdndn(int j);

  /**
   * <p>
   * setdBdndn.
   * </p>
   *
   * @param jComp a int
   * @param val a double
   */
  public void setdBdndn(int jComp, double val);

  /**
   * <p>
   * getdBdT.
   * </p>
   *
   * @return a double
   */
  public double getdBdT();

  /**
   * <p>
   * setdBdTdT.
   * </p>
   *
   * @param val a double
   */
  public void setdBdTdT(double val);

  /**
   * <p>
   * getdBdndT.
   * </p>
   *
   * @return a double
   */
  public double getdBdndT();

  /**
   * <p>
   * setdBdndT.
   * </p>
   *
   * @param val a double
   */
  public void setdBdndT(double val);

  /**
   * <p>
   * setdAdTdn.
   * </p>
   *
   * @param val a double
   */
  public void setdAdTdn(double val);

  /**
   * <p>
   * getdAdTdn.
   * </p>
   *
   * @return a double
   */
  public double getdAdTdn();

  /**
   * <p>
   * dFdN.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure);

  /**
   * <p>
   * dFdNdT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure);

  /**
   * <p>
   * dFdNdV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure);

  /**
   * <p>
   * dFdNdN.
   * </p>
   *
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure);
}
