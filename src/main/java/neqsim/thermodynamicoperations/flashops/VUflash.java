/*
 * VUflash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * VUflash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class VUflash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double Uspec = 0;
  double Vspec = 0;
  Flash pHFlash;

  /**
   * <p>
   * Constructor for VUflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Vspec a double
   * @param Uspec a double
   */
  public VUflash(SystemInterface system, double Vspec, double Uspec) {
    this.system = system;
    this.pHFlash = new PHflash(system, Uspec, 0);
    this.Uspec = Uspec;
    this.Vspec = Vspec;
    // System.out.println("enthalpy " + Hspec);
    // System.out.println("volume " + Vspec);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // double oldVol = system.getVolume();
    double newVol = system.getVolume();
    // double pNew = system.getPressure();
    // double pOld = system.getPressure();
    // double pOldOld = 0.0;
    double err = 0.0;
    int iterations = 0;
    // System.out.println("enthalpy start " + system.getEnthalpy());
    // double dPdV = 0.0;
    double wallHeat = 0.0;
    for (int i = 0; i < 21; i++) {
      wallHeat = 0 * i / 20.0 * 400.0 * 1295.0 * 1000.0 * (293.15 - system.getTemperature());
      // System.out.println("Hwall " + wallHeat + " i " + i);
      iterations = 1;
      do {
        iterations++;

        if (system.getNumberOfPhases() == 1) {
          this.pHFlash = new PHflashSingleComp(system,
              Uspec + wallHeat + system.getPressure() * system.getVolume(), 0);
        } else {
          this.pHFlash =
              new PHflash(system, Uspec + wallHeat + system.getPressure() * system.getVolume(), 0);
        }
        // System.out.println("Hspec " + Hspec);
        this.pHFlash.run();
        // pOldOld = pOld;
        // pOld = system.getPressure();
        // oldVol = newVol;
        newVol = system.getVolume();

        err = (newVol - Vspec) / Vspec;

        // System.out.println("err................................................................................
        // " + err);
        if (iterations < -5) {
          system.setPressure(system.getPressure() + err / 10.0);
        } else {
          // System.out.println("pres " + (system.getPressure()+0.1*dPdV*(newVol-Vspec)));
          system.setPressure(
              system.getPressure() - 0.6 * 1.0 / system.getdVdPtn() * (newVol - Vspec)); // system.getdVdPtn()*(newVol-Vspec));
                                                                                         // //dPdV*(newVol-Vspec));
        }
        // pNew = system.getPressure();
        // dPdV = (pOld - pOldOld) / (newVol - oldVol);
        // System.out.println("pressure " + system.getPressure());
      } while ((Math.abs(err) > 1e-10 && iterations < 1000) || iterations < 7);
    }
    // System.out.println("enthalpy end " + system.getEnthalpy());
    // System.out.println("iterations " + iterations);
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
