package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * VHflash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class VHflash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double Hspec = 0;
  double Vspec = 0;
  Flash pHFlash;

  /**
   * <p>
   * Constructor for VHflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Hspec a double
   * @param Vspec a double
   */
  public VHflash(SystemInterface system, double Hspec, double Vspec) {
    this.system = system;
    this.pHFlash = new PHflash(system, Hspec, 0);
    this.Hspec = Hspec;
    this.Vspec = Vspec;
    // System.out.println("enthalpy " + Hspec);
    // System.out.println("volume " + Vspec);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // double oldVol = system.getVolume();
    double newVol = system.getVolume();
    // double pNew = system.getPressure(), pOld = system.getPressure(), pOldOld = 0.0;
    double err = 0.0;
    int iterations = 0;
    // System.out.println("enthalpy start " + system.getEnthalpy());
    // double dPdV = 0.0;
    double wallHeat = 0.0;
    for (int i = 0; i < 1; i++) {
      wallHeat = 0 * i / 20.0 * 400.0 * 1295.0 * 1000.0 * (293.15 - system.getTemperature());
      // System.out.println("Hwall " + wallHeat + " i " + i);
      iterations = 1;
      do {
        iterations++;

        this.pHFlash = new PHflash(system, Hspec + wallHeat, 0);
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
              system.getPressure() - 0.6 * 1.0 / system.getdVdPtn() * (newVol - Vspec));
          // system.getdVdPtn()*(newVol-Vspec));
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
