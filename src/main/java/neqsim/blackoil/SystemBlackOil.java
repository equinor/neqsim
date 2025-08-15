package neqsim.blackoil;

/**
 * Lightweight Black-Oil "system/stream" with standard totals and P/T.
 * Not a full NeqSim SystemInterface (by design, to keep it minimal).
 */
public class SystemBlackOil {
  private final BlackOilPVTTable pvt;
  private final BlackOilFlash flash;
  private double P, T;
  private double Otot_std, Gtot_std, W_std;  // Sm3
  private BlackOilFlashResult last;

  public SystemBlackOil(BlackOilPVTTable pvt,
                        double rho_o_sc, double rho_g_sc, double rho_w_sc) {
    this.pvt = pvt;
    this.flash = new BlackOilFlash(pvt, rho_o_sc, rho_g_sc, rho_w_sc);
  }

  public SystemBlackOil copyShallow() {
    SystemBlackOil s = new SystemBlackOil(this.pvt, 800.0, 1.2, 1000.0);
    s.P = this.P; s.T = this.T;
    s.Otot_std = this.Otot_std; s.Gtot_std = this.Gtot_std; s.W_std = this.W_std;
    s.last = null;
    return s;
  }

  public void setPressure(double P) { this.P = P; this.last = null; }
  public void setTemperature(double T) { this.T = T; this.last = null; }
  public void setStdTotals(double Otot_std, double Gtot_std, double W_std) {
    this.Otot_std = Math.max(0.0, Otot_std);
    this.Gtot_std = Math.max(0.0, Gtot_std);
    this.W_std = Math.max(0.0, W_std);
    this.last = null;
  }

  public double getPressure() { return P; }
  public double getTemperature() { return T; }
  public double getOilStdTotal() { return Otot_std; }
  public double getGasStdTotal() { return Gtot_std; }
  public double getWaterStd() { return W_std; }

  public BlackOilFlashResult flash() {
    if (last == null) last = flash.flash(P, T, Otot_std, Gtot_std, W_std);
    return last;
  }

  public double getBo() { return pvt.Bo(P); }
  public double getBg() { return pvt.Bg(P); }
  public double getBw() { return pvt.Bw(P); }
  public double getRs() { return pvt.RsEffective(P); }
  public double getRv() { return pvt.Rv(P); }

  public double getOilDensity()   { return flash().rho_o; }
  public double getGasDensity()   { return flash().rho_g; }
  public double getWaterDensity() { return flash().rho_w; }

  public double getOilViscosity()   { return flash().mu_o; }
  public double getGasViscosity()   { return flash().mu_g; }
  public double getWaterViscosity() { return flash().mu_w; }

  public double getOilReservoirVolume()   { return flash().V_o; }
  public double getGasReservoirVolume()   { return flash().V_g; }
  public double getWaterReservoirVolume() { return flash().V_w; }
}
