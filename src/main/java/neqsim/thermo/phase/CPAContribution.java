package neqsim.thermo.phase;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * CPAContribution provides utility methods for CPA (Cubic Plus Association) calculations that are
 * common across different cubic EOS implementations (SRK-CPA, PR-CPA, UMR-CPA, etc.).
 * 
 * <p>
 * In CPA theory, the radial distribution function at contact is based on the Carnahan-Starling
 * hard-sphere model and is the same for all cubic equations of state. The formulas depend only on
 * the co-volume parameter (b) and volume, not on the attraction parameter (a) which differs between
 * SRK and PR.
 * </p>
 * 
 * <p>
 * This class provides methods for calculating the radial distribution function and its volume
 * derivatives, which can be used for verification and comparison between different CPA
 * implementations.
 * </p>
 * 
 * @author Even Solbraa
 * @version 1.0
 */
public class CPAContribution implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CPAContribution.class);

  /** Reference to the parent phase. Must be a PhaseEos subclass. */
  private final PhaseEos phase;

  /** Temporary molar volume storage. */
  double tempTotVol = 0;

  /**
   * Constructor for CPAContribution.
   *
   * @param phase the parent EOS phase that this CPA contribution belongs to
   */
  public CPAContribution(PhaseEos phase) {
    this.phase = phase;
  }

  // ==================== Radial Distribution Function Methods ====================
  // These are the same for all cubic EOS in CPA (SRK, PR, UMR, etc.)
  // Based on simplified Carnahan-Starling hard-sphere model

  /**
   * Calculate radial distribution function g at contact.
   * 
   * <p>
   * Uses the simplified Carnahan-Starling expression: g = (2 - η/2) / (2 * (1 - η/2)³) where η =
   * b/(4V) is the packing fraction.
   * </p>
   *
   * @return g value
   */
  public double calc_g() {
    tempTotVol = phase.getMolarVolume();
    double b = phase.getb();
    double temp = 1.0 - b / 4.0 / tempTotVol;
    return (2.0 - b / 4.0 / tempTotVol) / (2.0 * temp * temp * temp);
  }

  /**
   * Calculate first volume derivative of ln(g).
   *
   * @return d(ln g)/dV
   */
  public double calc_lngV() {
    tempTotVol = phase.getTotalVolume();
    double b = phase.getB();
    double t = tempTotVol;
    double t2 = t * t;
    double bOver4t = b / (4.0 * t);
    double factor = b / (4.0 * t2);
    return factor / (2.0 - bOver4t) - 3.0 * factor / (1.0 - bOver4t);
  }

  /**
   * Calculate second volume derivative of ln(g).
   *
   * @return d²(ln g)/dV²
   */
  public double calc_lngVV() {
    tempTotVol = phase.getTotalVolume();
    double b = phase.getB();
    double t = tempTotVol;
    double t2 = t * t;
    double t3 = t2 * t;
    double b2 = b * b;
    double b3 = b2 * b;
    double denom1 = 8.0 * t - b;
    double denom1Sq = denom1 * denom1;
    double denom2 = 4.0 * t - b;
    double denom2Sq = denom2 * denom2;
    double term = 640.0 * t3 - 216.0 * b * t2 + 24.0 * b2 * t - b3;
    return 2.0 * term * b / t2 / denom1Sq / denom2Sq;
  }

  /**
   * Calculate third volume derivative of ln(g).
   *
   * @return d³(ln g)/dV³
   */
  public double calc_lngVVV() {
    tempTotVol = phase.getTotalVolume();
    double b = phase.getB();
    double t = tempTotVol;
    double t2 = t * t;
    double t3 = t2 * t;
    double t4 = t3 * t;
    double t5 = t4 * t;
    double b2 = b * b;
    double b3 = b2 * b;
    double b4 = b3 * b;
    double b5 = b4 * b;
    double term =
        b5 + 17664.0 * t4 * b - 4192.0 * t3 * b2 + 528.0 * b3 * t2 - 36.0 * t * b4 - 30720.0 * t5;
    double denom1 = b - 8.0 * t;
    double denom1Cubed = denom1 * denom1 * denom1;
    double denom2 = b - 4.0 * t;
    double denom2Cubed = denom2 * denom2 * denom2;
    return 4.0 * term * b / t3 / denom1Cubed / denom2Cubed;
  }

  // ==================== Static Utility Methods ====================

  /**
   * Calculate radial distribution function g given molar volume and molar b parameter.
   * 
   * <p>
   * This is a static utility method for standalone calculations.
   * </p>
   *
   * @param molarVolume molar volume in m³/mol
   * @param molarB molar co-volume parameter b in m³/mol
   * @return g value
   */
  public static double calcG(double molarVolume, double molarB) {
    double eta = molarB / (4.0 * molarVolume);
    double temp = 1.0 - eta;
    return (2.0 - eta) / (2.0 * temp * temp * temp);
  }

  /**
   * Calculate first volume derivative of ln(g).
   *
   * @param totalVolume total volume in m³
   * @param totalB total co-volume parameter B in m³
   * @return d(ln g)/dV
   */
  public static double calcLngV(double totalVolume, double totalB) {
    double t = totalVolume;
    double t2 = t * t;
    double bOver4t = totalB / (4.0 * t);
    double factor = totalB / (4.0 * t2);
    return factor / (2.0 - bOver4t) - 3.0 * factor / (1.0 - bOver4t);
  }
}
