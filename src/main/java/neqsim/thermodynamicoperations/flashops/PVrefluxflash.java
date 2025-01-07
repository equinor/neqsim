package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PVrefluxflash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class PVrefluxflash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  Flash tpFlash;
  int refluxPhase = 0;
  double refluxSpec = 0.5;

  /**
   * <p>
   * Constructor for PVrefluxflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param refluxSpec a double
   * @param refluxPhase a int
   */
  public PVrefluxflash(SystemInterface system, double refluxSpec, int refluxPhase) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.refluxSpec = refluxSpec;
    this.refluxPhase = refluxPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    int iter = 0;
    double f_func = 0.0;
    double f_func_old = 0.0;
    double df_func_dt = 0;
    double t_old = 0;
    double t_oldold = 0.0;
    tpFlash.run();
    double dt = 1.0;
    do {
      iter++;

      f_func_old = f_func;
      t_oldold = t_old;
      t_old = system.getTemperature();

      f_func = refluxSpec - (1.0 / system.getBeta(refluxPhase) - 1.0);
      // system.getPhase(refluxPhase).getVolume()
      // / system.getVolume();
      df_func_dt = (f_func - f_func_old) / (t_old - t_oldold);

      // err = Math.abs(f_func);

      if (iter < 4) {
        if (f_func > 0) {
          system.setTemperature(system.getTemperature() + 0.1);
        } else if (f_func < 0) {
          system.setTemperature(system.getTemperature() - 0.1);
        }
      } else {
        dt = f_func / df_func_dt;
        if (Math.abs(dt) > 2.0) {
          dt = Math.signum(dt) * 2.0;
        }

        system.setTemperature(system.getTemperature() - dt * (1.0 * iter) / (iter + 50.0));
      }
      tpFlash.run();

      // System.out.println("temp " + system.getTemperature() + " err " + err + "
      // volfor " + system.getPhase(refluxPhase).getVolume() / system.getVolume());
    } while (Math.abs(dt) > 1e-8 && Math.abs(f_func) > 1e-6 && iter < 1000);
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
