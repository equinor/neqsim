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
   */
  public TVflash() {}

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
   * calcdQdVV.
   * </p>
   *
   * @return a double
   */
  public double calcdQdVV() {
    double dQdVV = 0.0;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      dQdVV += 1.0 / (system.getPhase(i).getVolume() / system.getVolume()) * 1.0
          / system.getPhase(i).getdPdVTn(); // *system.getPhase(i).getdVdP();system.getPhase(i).getVolume()/system.getVolume()*
    }
    return dQdVV;
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
    do {
      iterations++;
      oldPres = nyPres;
      system.init(3);
      nyPres = oldPres - (iterations) / (iterations + 10.0) * calcdQdV() / calcdQdVV();
      if (nyPres <= 0.0 || Math.abs(oldPres - nyPres) > 10.0) {
        nyPres = Math.abs(oldPres - 1.0);
      }
      system.setPressure(nyPres);
      tpFlash.run();
      // System.out.println(" dQdv " + calcdQdV() + " new pressure " + nyPres + " error " +
      // Math.abs((nyPres-oldPres)/(nyPres)) + " numberofphases "+system.getNumberOfPhases());
    } while (Math.abs((nyPres - oldPres) / (nyPres)) > 1e-9 && iterations < 1000 || iterations < 3);
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
