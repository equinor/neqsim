/*
 * TVflash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * TVflash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class TVfractionFlash extends Flash {
  private static final long serialVersionUID = 1000;

  double Vfractionspec = 0;
  Flash tpFlash;

  /**
   * <p>
   * Constructor for TVflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Vfractionspec a double
   */
  public TVfractionFlash(SystemInterface system, double Vfractionspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Vfractionspec = Vfractionspec;
  }

  /**
   * <p>
   * calcdQdVP.
   * </p>
   *
   * @return a double
   */
  public double calcdQdVdP() {
    double dQdVP = 1.0 / system.getPhase(0).getdPdVTn() / system.getVolume()
        + system.getPhase(0).getVolume() / Math.pow(system.getVolume(), 2.0) * system.getdVdPtn();
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
    double dQ = system.getPhase(0).getVolume() / system.getVolume() - Vfractionspec;
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
    int iterations = 0;
    double error = 100.0;
    double pressureStep = 1.0;
    do {
      iterations++;
      system.init(3);
      oldPres = nyPres;
      double dqdv = calcdQdV();
      double dqdvdp = calcdQdVdP();
      nyPres = oldPres - iterations / (iterations + 100.0) * dqdv / dqdvdp;
      pressureStep = nyPres - oldPres;

      if (nyPres <= 0.0) {
        nyPres = oldPres * 0.9;
      }
      system.setPressure(nyPres);
      tpFlash.run();

      error = Math.abs(dqdv / Vfractionspec);
      // System.out.println("error " + error + "iteration " + iterations + " dQdv " + calcdQdV()
      // + " new pressure " + nyPres + " error " + Math.abs((nyPres - oldPres) / (nyPres))
      // + " numberofphases " + system.getNumberOfPhases());
    } while ((error > 1e-6 && Math.abs(pressureStep) > 1e-6 && iterations < 200) || iterations < 6);
    return nyPres;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();
    if (system.getNumberOfPhases() == 1) {
      do {
        system.setPressure(system.getPressure() * 0.9);
        tpFlash.run();
      } while (system.getNumberOfPhases() == 1);
    }

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
