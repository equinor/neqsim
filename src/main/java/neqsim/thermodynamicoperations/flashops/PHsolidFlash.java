/*
 * PHsolidFlash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PHsolidFlash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class PHsolidFlash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PHsolidFlash.class);

  Flash tpFlash;
  int refluxPhase = 0;
  double enthalpyspec = 0.5;

  /**
   * <p>
   * Constructor for PHsolidFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param ent a double
   */
  public PHsolidFlash(SystemInterface system, double ent) {
    this.system = system;
    this.tpFlash = new TPflash(this.system, true);
    this.enthalpyspec = ent;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // logger.info("enthalpy: " + system.getEnthalpy());
    double err = 0;
    int iter = 0;
    double f_func = 0.0;
    double f_func_old = 0.0;
    double df_func_dt = 0;
    double t_old = 0;
    double t_oldold = 0.0;
    // ThermodynamicOperations ops = new ThermodynamicOperations(system);
    tpFlash.run();
    double dt = 10;
    do {
      iter++;

      f_func_old = f_func;
      t_oldold = t_old;
      t_old = system.getTemperature();
      system.init(3);
      f_func = enthalpyspec - system.getEnthalpy();
      logger.info("entalp diff " + f_func);
      df_func_dt = (f_func - f_func_old) / (t_old - t_oldold);

      err = Math.abs(f_func);

      if (iter < 2) {
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
        system.setTemperature(system.getTemperature() - 0.8 * dt);
      }
      tpFlash.run();

      logger.info("temp " + system.getTemperature() + " err " + err);
    } while (Math.abs(dt) > 1e-8 && iter < 200);
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
