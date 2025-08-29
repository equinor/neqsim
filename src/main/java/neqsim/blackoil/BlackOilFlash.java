package neqsim.blackoil;

/**
 * Core Black-Oil flash calculator. Temperature dependence is assumed to be captured by the PVT
 * table (single Tref) unless you extend it.
 *
 * @author esol
 */
public final class BlackOilFlash {
  private final BlackOilPVTTable pvt;
  private final double rho_o_sc;
  private final double rho_g_sc;
  private final double rho_w_sc;

  /**
   * <p>
   * Constructor for BlackOilFlash.
   * </p>
   *
   * @param pvt a {@link neqsim.blackoil.BlackOilPVTTable} object
   * @param rho_o_sc a double
   * @param rho_g_sc a double
   * @param rho_w_sc a double
   */
  public BlackOilFlash(BlackOilPVTTable pvt, double rho_o_sc, double rho_g_sc, double rho_w_sc) {
    this.pvt = pvt;
    this.rho_o_sc = rho_o_sc;
    this.rho_g_sc = rho_g_sc;
    this.rho_w_sc = rho_w_sc;
  }

  /**
   * <p>
   * flash.
   * </p>
   *
   * @param P a double
   * @param T a double
   * @param Otot_std a double
   * @param Gtot_std a double
   * @param W_std a double
   * @return a {@link neqsim.blackoil.BlackOilFlashResult} object
   */
  public BlackOilFlashResult flash(double P, double T, double Otot_std, double Gtot_std,
      double W_std) {
    BlackOilFlashResult r = new BlackOilFlashResult();
    double Rs = pvt.RsEffective(P);
    double Rv = pvt.Rv(P); // set to 0.0 if you don't model oil-in-gas

    double Gf_std;
    double Ostd_liq;
    if (Gtot_std <= Rs * Otot_std) {
      Gf_std = 0.0;
      Ostd_liq = Otot_std;
    } else {
      double denom = 1.0 - Rs * Rv;
      if (Math.abs(denom) < 1e-12) {
        denom = 1e-12;
      }
      Gf_std = (Gtot_std - Rs * Otot_std) / denom;
      if (Gf_std < 0) {
        Gf_std = 0.0;
      }
      Ostd_liq = Otot_std - Rv * Gf_std;
      if (Ostd_liq < 0) {
        Ostd_liq = 0.0;
      }
    }

    double Bo = pvt.Bo(P);

    double Bg = pvt.Bg(P);
    double Bw = pvt.Bw(P);
    r.O_std = Ostd_liq;
    r.Gf_std = Gf_std;
    r.W_std = W_std;
    r.V_o = Bo * Ostd_liq;
    r.V_g = Bg * Gf_std;
    r.V_w = Bw * W_std;

    r.mu_o = pvt.mu_o(P);
    r.mu_g = pvt.mu_g(P);
    r.mu_w = pvt.mu_w(P);

    r.rho_o = (rho_o_sc + Rs * rho_g_sc) / Math.max(Bo, 1e-12);
    r.rho_g = (rho_g_sc) / Math.max(Bg, 1e-12);
    r.rho_w = (rho_w_sc) / Math.max(Bw, 1e-12);

    r.Rs = Rs;
    r.Rv = Rv;
    r.Bo = Bo;
    r.Bg = Bg;
    r.Bw = Bw;
    return r;
  }
}
