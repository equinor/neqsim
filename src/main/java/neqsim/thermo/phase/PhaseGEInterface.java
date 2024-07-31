/*
 * PhaseGEInterface.java
 *
 * Created on 12. juli 2000, 00:26
 */

package neqsim.thermo.phase;

/**
 * <p>
 * PhaseGEInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface PhaseGEInterface {
  /**
   * <p>
   * getExcessGibbsEnergy.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param pt the PhaseType of the phase.
   * @return a double
   */
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure, PhaseType pt);

  /**
   * <p>
   * setAlpha.
   * </p>
   *
   * @param alpha an array of type double
   */
  public void setAlpha(double[][] alpha);

  /**
   * <p>
   * setDij.
   * </p>
   *
   * @param Dij an array of type double
   */
  public void setDij(double[][] Dij);

  /**
   * <p>
   * setDijT.
   * </p>
   *
   * @param DijT an array of type double
   */
  public void setDijT(double[][] DijT);
}
