package neqsim.blackoil;

import java.io.Serializable;

/**
 * Lightweight Black-Oil "system/stream" with standard totals and P/T. Not a full NeqSim SystemInterface (by design, to
 * keep it minimal).
 *
 * @author esol
 * @version 1.0
 */
public class SystemBlackOil implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private final BlackOilPVTTable pvt;
  private final BlackOilFlash flash;
  private double P;
  private double T;
  private double Otot_std;
  private double Gtot_std;
  private double W_std;
  private BlackOilFlashResult last;

  /**
   * Constructor for SystemBlackOil.
   *
   * @param pvt a {@link neqsim.blackoil.BlackOilPVTTable} object
   * @param rho_o_sc a double
   * @param rho_g_sc a double
   * @param rho_w_sc a double
   */
  public SystemBlackOil(BlackOilPVTTable pvt, double rho_o_sc, double rho_g_sc, double rho_w_sc) {
    this.pvt = pvt;
    this.flash = new BlackOilFlash(pvt, rho_o_sc, rho_g_sc, rho_w_sc);
  }

  /**
   * copyShallow.
   *
   * @return a {@link neqsim.blackoil.SystemBlackOil} object
   */
  public SystemBlackOil copyShallow() {
    SystemBlackOil s = new SystemBlackOil(this.pvt, 800.0, 1.2, 1000.0);
    s.P = this.P;
    s.T = this.T;
    s.Otot_std = this.Otot_std;
    s.Gtot_std = this.Gtot_std;
    s.W_std = this.W_std;
    s.last = null;
    return s;
  }

  /**
   * setPressure.
   *
   * @param P a double
   */
  public void setPressure(double P) {
    this.P = P;
    this.last = null;
  }

  /**
   * setTemperature.
   *
   * @param T a double
   */
  public void setTemperature(double T) {
    this.T = T;
    this.last = null;
  }

  /**
   * setStdTotals.
   *
   * @param Otot_std a double
   * @param Gtot_std a double
   * @param W_std a double
   */
  public void setStdTotals(double Otot_std, double Gtot_std, double W_std) {
    this.Otot_std = Math.max(0.0, Otot_std);
    this.Gtot_std = Math.max(0.0, Gtot_std);
    this.W_std = Math.max(0.0, W_std);
    this.last = null;
  }

  /**
   * getPressure.
   *
   * @return a double
   */
  public double getPressure() {
    return P;
  }

  /**
   * getTemperature.
   *
   * @return a double
   */
  public double getTemperature() {
    return T;
  }

  /**
   * getOilStdTotal.
   *
   * @return a double
   */
  public double getOilStdTotal() {
    return Otot_std;
  }

  /**
   * getGasStdTotal.
   *
   * @return a double
   */
  public double getGasStdTotal() {
    return Gtot_std;
  }

  /**
   * getWaterStd.
   *
   * @return a double
   */
  public double getWaterStd() {
    return W_std;
  }

  /**
   * flash.
   *
   * @return a {@link neqsim.blackoil.BlackOilFlashResult} object
   */
  public BlackOilFlashResult flash() {
    if (last == null) {
      last = flash.flash(P, T, Otot_std, Gtot_std, W_std);
    }
    return last;
  }

  /**
   * getBo.
   *
   * @return a double
   */
  public double getBo() {
    return pvt.Bo(P);
  }

  /**
   * getBg.
   *
   * @return a double
   */
  public double getBg() {
    return pvt.Bg(P);
  }

  /**
   * getBw.
   *
   * @return a double
   */
  public double getBw() {
    return pvt.Bw(P);
  }

  /**
   * getRs.
   *
   * @return a double
   */
  public double getRs() {
    return pvt.RsEffective(P);
  }

  /**
   * getRv.
   *
   * @return a double
   */
  public double getRv() {
    return pvt.Rv(P);
  }

  /**
   * getOilDensity.
   *
   * @return a double
   */
  public double getOilDensity() {
    return flash().rho_o;
  }

  /**
   * getGasDensity.
   *
   * @return a double
   */
  public double getGasDensity() {
    return flash().rho_g;
  }

  /**
   * getWaterDensity.
   *
   * @return a double
   */
  public double getWaterDensity() {
    return flash().rho_w;
  }

  /**
   * getOilViscosity.
   *
   * @return a double
   */
  public double getOilViscosity() {
    return flash().mu_o;
  }

  /**
   * getGasViscosity.
   *
   * @return a double
   */
  public double getGasViscosity() {
    return flash().mu_g;
  }

  /**
   * getWaterViscosity.
   *
   * @return a double
   */
  public double getWaterViscosity() {
    return flash().mu_w;
  }

  /**
   * getOilReservoirVolume.
   *
   * @return a double
   */
  public double getOilReservoirVolume() {
    return flash().V_o;
  }

  /**
   * getGasReservoirVolume.
   *
   * @return a double
   */
  public double getGasReservoirVolume() {
    return flash().V_g;
  }

  /**
   * getWaterReservoirVolume.
   *
   * @return a double
   */
  public double getWaterReservoirVolume() {
    return flash().V_w;
  }
}
