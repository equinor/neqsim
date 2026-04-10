package neqsim.blackoil;

import java.io.Serializable;

/**
 * DTO for Black-Oil flash results at (P,T).
 *
 * @author esol
 * @version 1.0
 */
public final class BlackOilFlashResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // Standard-condition amounts (Sm3)
  public double O_std; // oil remaining at std (after vaporization)
  public double Gf_std; // free gas at std
  public double W_std; // water at std

  public double V_o;
  public double V_g;
  public double V_w;
  public double rho_o;
  public double rho_g;
  public double rho_w;
  public double mu_o;
  public double mu_g;
  public double mu_w;
  public double Rs;
  public double Rv;
  public double Bo;
  public double Bg;
  public double Bw;
}
