package neqsim.blackoil;

/** DTO for Black-Oil flash results at (P,T). */
public final class BlackOilFlashResult {
  // Standard-condition amounts (Sm3)
  public double O_std;   // oil remaining at std (after vaporization)
  public double Gf_std;  // free gas at std
  public double W_std;   // water at std

  // Reservoir volumes at P,T (m3)
  public double V_o, V_g, V_w;

  // Phase properties at P (T-ref Black-Oil)
  public double rho_o, rho_g, rho_w;  // kg/m3
  public double mu_o,  mu_g,  mu_w;   // Pa·s

  // Convenience: Rs,Rv,Bo,Bg,Bw used in this flash
  public double Rs, Rv, Bo, Bg, Bw;
}
