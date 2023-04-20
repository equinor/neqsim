package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentCPAInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ComponentCPAInterface extends ComponentEosInterface {
  /**
   * <p>
   * getXsite.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] getXsite();

  /**
   * <p>
   * getXsiteOld.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] getXsiteOld();

  /**
   * <p>
   * getXsitedT.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] getXsitedT();

  /**
   * <p>
   * getXsitedTdT.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] getXsitedTdT();

  /**
   * <p>
   * setXsitedTdT.
   * </p>
   *
   * @param i a int
   * @param xsitedTdT a double
   */
  public void setXsitedTdT(int i, double xsitedTdT);

  /**
   * <p>
   * setXsitedT.
   * </p>
   *
   * @param i a int
   * @param xsitedT a double
   */
  public void setXsitedT(int i, double xsitedT);

  /**
   * <p>
   * dFCPAdXi.
   * </p>
   *
   * @param site a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double dFCPAdXi(int site, PhaseInterface phase);

  /**
   * <p>
   * getXsitedV.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] getXsitedV();

  /**
   * <p>
   * dFCPAdXidXj.
   * </p>
   *
   * @param sitei a int
   * @param sitej a int
   * @param compj a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double dFCPAdXidXj(int sitei, int sitej, int compj, PhaseInterface phase);

  /**
   * <p>
   * setXsite.
   * </p>
   *
   * @param i a int
   * @param xsite a double
   */
  public void setXsite(int i, double xsite);

  /**
   * <p>
   * setXsiteOld.
   * </p>
   *
   * @param i a int
   * @param xsite a double
   */
  public void setXsiteOld(int i, double xsite);

  /**
   * <p>
   * setXsitedV.
   * </p>
   *
   * @param i a int
   * @param xsite a double
   */
  public void setXsitedV(int i, double xsite);

  /**
   * <p>
   * dFCPAdNdXi.
   * </p>
   *
   * @param site a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double dFCPAdNdXi(int site, PhaseInterface phase);

  /**
   * <p>
   * dFCPAdVdXi.
   * </p>
   *
   * @param site a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double dFCPAdVdXi(int site, PhaseInterface phase);

  /**
   * <p>
   * setXsitedni.
   * </p>
   *
   * @param xnumb a int
   * @param compnumb a int
   * @param val a double
   */
  public void setXsitedni(int xnumb, int compnumb, double val);
}
