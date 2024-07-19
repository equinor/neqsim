/*
 * TVflash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicOperations.flashOps;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * TVflash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class TVflash extends Flash {
  private static final long serialVersionUID = 1000;

  double Vspec = 0;
  Flash tpFlash;

  /**
   * <p>
   * Constructor for TVflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Vspec a double
   */
  public TVflash(SystemInterface system, double Vspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Vspec = Vspec;
  }

  /**
   * <p>
   * calcdQdVP.
   * </p>
   *
   * @return a double
   */
  public double calcdQdVdP() {
    double dQdVP = 0.0;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      dQdVP += 1.0 / system.getPhase(i).getdPdVTn();
    }
    return dQdVP;
  }

  /**
   * <p>
   * calcdQdV.
   * </p>
   *
   * @return a double
   */
  public double calcdQdV() {
    double dQ = system.getVolume() - Vspec;
    return dQ;
  }

  /**
   * <p>
   * solveQ.
   * </p>
   *
   * @return a double
   */
  public double solveQ() {
    double oldPres = system.getPressure();
    double nyPres = system.getPressure();
    double iterations = 1;
    double error = 100.0, errorOld = 1000.0;
    do {

      iterations++;
      oldPres = nyPres;
      system.init(3);
      nyPres = oldPres - 1.0 / 10.0 * calcdQdV() / calcdQdVdP();
      if (nyPres <= 0.0) {
        nyPres = oldPres * 0.9;
      }
      if (nyPres >= oldPres * 2) {
        nyPres = oldPres * 2.0;
      }
      system.setPressure(nyPres);
      tpFlash.run();
      // System.out.println(" dQdv " + calcdQdV() + " new pressure " + nyPres + " error "
      // + Math.abs((nyPres - oldPres) / (nyPres)) + " numberofphases "
      // + system.getNumberOfPhases());
      error = Math.abs(calcdQdV());
    } while (Math.abs(error) > 1e-9 && iterations < 200 && error < errorOld || iterations < 3);
    return nyPres;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();
    // System.out.println("enthalpy: " + system.getEnthalpy());
    solveQ();

    // System.out.println("volume: " + system.getVolume());
    // System.out.println("Temperature: " + system.getTemperature());
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
