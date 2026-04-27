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
    // First TPflash runs COLD (Wilson K) to avoid bias from stale K-values
    // left by a previous unrelated flash. Warm-start is then enabled for the
    // inner TPflash iterations within the outer reflux loop — safe because
    // the outer loop converges on T via the reflux-fraction residual.
    boolean prevWarm = neqsim.thermo.ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(false);
      int iter = 0;
      double f_func = 0.0;
      double f_func_old = 0.0;
      double df_func_dt = 0;
      double t_old = 0;
      double t_oldold = 0.0;
      tpFlash.run();
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(true);
      double dt = 1.0;
      do {
        iter++;

        f_func_old = f_func;
        t_oldold = t_old;
        t_old = system.getTemperature();

        f_func = refluxSpec - (1.0 / system.getBeta(refluxPhase) - 1.0);
        double dT_meas = t_old - t_oldold;
        df_func_dt = (Math.abs(dT_meas) > 1e-12) ? (f_func - f_func_old) / dT_meas : 0.0;

        // After the first probe iteration we have a usable secant slope; switch to
        // damped Newton/secant immediately. Bootstrap takes one ±0.1 K probe.
        if (iter < 2 || df_func_dt == 0.0 || !Double.isFinite(df_func_dt)) {
          // Direction probe with adaptive magnitude (still small to stay in basin).
          double probe = (f_func > 0) ? 0.1 : (f_func < 0 ? -0.1 : 0.0);
          dt = -probe; // record the step actually taken so convergence test is meaningful
          system.setTemperature(t_old + probe);
        } else {
          dt = f_func / df_func_dt;
          // Hard step cap to stay in basin of attraction.
          if (Math.abs(dt) > 2.0) {
            dt = Math.signum(dt) * 2.0;
          }
          // Mild damping that ramps to full Newton quickly: 0.5 → 1.0 by iter ~10.
          double damping = Math.min(1.0, 0.4 + 0.06 * iter);
          system.setTemperature(t_old - dt * damping);
        }
        tpFlash.run();
      } while (Math.abs(f_func) > 1e-6 && iter < 1000);
    } finally {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(prevWarm);
    }
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
