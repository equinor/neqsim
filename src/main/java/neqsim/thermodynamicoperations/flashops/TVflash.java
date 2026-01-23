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
public class TVflash extends Flash {
  /** Serialization version UID. */
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
    int iterations = 1;
    double error = 100.0;
    double numericdQdVdP = 0.0;
    double dQdV = 0.0;
    double olddQdV = 0.0;
    double pressureStep = 1.0;
    do {
      iterations++;
      oldPres = nyPres;
      system.init(3);
      double dQDVdP = calcdQdVdP();

      numericdQdVdP = (calcdQdV() - olddQdV) / pressureStep;

      if (iterations < 5) {
        nyPres = oldPres - 1.0 / 10.0 * calcdQdV() / dQDVdP;
      } else {
        nyPres = oldPres - 1.0 * calcdQdV() / numericdQdVdP;
      }
      if (nyPres <= 0.0) {
        nyPres = oldPres * 0.9;
      }
      if (nyPres >= oldPres * 2) {
        nyPres = oldPres * 2.0;
      }
      pressureStep = nyPres - oldPres;

      olddQdV = calcdQdV();
      system.setPressure(nyPres);
      tpFlash.run();
      error = Math.abs(calcdQdV()) / system.getVolume();
      // System.out.println("error " + error + "iteration " + iterations + " dQdv " + calcdQdV()
      // + " new pressure " + nyPres + " error " + Math.abs((nyPres - oldPres) / (nyPres))
      // + " numberofphases " + system.getNumberOfPhases() + " dQDVdP " + dQDVdP + " dQDVdPnumeric"
      // + numericdQdVdP);
    } while ((error > 1e-9 && iterations < 200) || iterations < 3);
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
