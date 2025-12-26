package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.mixingrule.CPAMixingRulesInterface;

/**
 * <p>
 * PhaseCPAInterface interface.
 * </p>
 * 
 * <p>
 * This interface defines the contract for CPA (Cubic Plus Association) phase implementations. It
 * provides default implementations for the radial distribution function and its derivatives, which
 * are the same for all cubic equations of state (SRK, PR, UMR) based on the simplified
 * Carnahan-Starling hard-sphere model.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface PhaseCPAInterface extends PhaseEosInterface {
  /**
   * <p>
   * Getter for property hcpatot.
   * </p>
   *
   * @return a double
   */
  double getHcpatot();

  /**
   * <p>
   * getCrossAssosiationScheme.
   * </p>
   *
   * @param comp1 a int
   * @param comp2 a int
   * @param site1 a int
   * @param site2 a int
   * @return a int
   */
  int getCrossAssosiationScheme(int comp1, int comp2, int site1, int site2);

  /**
   * <p>
   * getGcpa.
   * </p>
   *
   * @return a double
   */
  public double getGcpa();

  /**
   * <p>
   * getGcpav.
   * </p>
   *
   * @return a double
   */
  public double getGcpav();

  /**
   * <p>
   * getTotalNumberOfAccociationSites.
   * </p>
   *
   * @return a int
   */
  public int getTotalNumberOfAccociationSites();

  /**
   * <p>
   * setTotalNumberOfAccociationSites.
   * </p>
   *
   * @param totalNumberOfAccociationSites a int
   */
  public void setTotalNumberOfAccociationSites(int totalNumberOfAccociationSites);

  /**
   * <p>
   * getCpaMixingRule.
   * </p>
   *
   * @return a {@link neqsim.thermo.mixingrule.CPAMixingRulesInterface} object
   */
  public CPAMixingRulesInterface getCpaMixingRule();

  // ==================== Default CPA Calculation Methods ====================
  // These radial distribution functions are the same for all cubic EOS (SRK, PR, UMR)
  // based on the simplified Carnahan-Starling hard-sphere model.

  /**
   * Calculate the sum of (1 - X_i) over all association sites, weighted by moles. This is used in
   * the CPA contribution to Helmholtz energy.
   *
   * @return hCPA value
   */
  default double calc_hCPA() {
    double htot = 0.0;
    double tot = 0.0;
    for (int i = 0; i < getNumberOfComponents(); i++) {
      htot = 0.0;
      for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
        htot += (1.0 - ((ComponentCPAInterface) getComponent(i)).getXsite()[j]);
      }
      tot += getComponent(i).getNumberOfMolesInPhase() * htot;
    }
    return tot;
  }

  /**
   * Calculate radial distribution function g at contact using simplified Carnahan-Starling.
   * 
   * <p>
   * g = (2 - b/4V) / (2 * (1 - b/4V)³)
   * </p>
   * 
   * <p>
   * This formula is the same for all cubic EOS (SRK, PR, UMR) since it depends only on the
   * co-volume parameter b, not on the attraction parameter a.
   * </p>
   *
   * @return g value
   */
  default double calc_g() {
    double molarVolume = getMolarVolume();
    // Use B / numberOfMolesInPhase to get molar co-volume b
    double b = getB() / getNumberOfMolesInPhase();
    double eta = b / (4.0 * molarVolume);
    double temp = 1.0 - eta;
    return (2.0 - eta) / (2.0 * temp * temp * temp);
  }

  /**
   * Calculate first volume derivative of ln(g).
   * 
   * <p>
   * d(ln g)/dV
   * </p>
   *
   * @return d(ln g)/dV
   */
  default double calc_lngV() {
    double totalVolume = getTotalVolume();
    double B = getB();
    double t = totalVolume;
    double t2 = t * t;
    double bOver4t = B / (4.0 * t);
    double factor = B / (4.0 * t2);
    return factor / (2.0 - bOver4t) - 3.0 * factor / (1.0 - bOver4t);
  }

  /**
   * Calculate second volume derivative of ln(g).
   * 
   * <p>
   * d²(ln g)/dV²
   * </p>
   *
   * @return d²(ln g)/dV²
   */
  default double calc_lngVV() {
    double totalVolume = getTotalVolume();
    double B = getB();
    double t = totalVolume;
    double t2 = t * t;
    double t3 = t2 * t;
    double b2 = B * B;
    double b3 = b2 * B;
    double denom1 = 8.0 * t - B;
    double denom1Sq = denom1 * denom1;
    double denom2 = 4.0 * t - B;
    double denom2Sq = denom2 * denom2;
    double term = 640.0 * t3 - 216.0 * B * t2 + 24.0 * b2 * t - b3;
    return 2.0 * term * B / t2 / denom1Sq / denom2Sq;
  }

  /**
   * Calculate third volume derivative of ln(g).
   * 
   * <p>
   * d³(ln g)/dV³
   * </p>
   *
   * @return d³(ln g)/dV³
   */
  default double calc_lngVVV() {
    double totalVolume = getTotalVolume();
    double B = getB();
    double t = totalVolume;
    double t2 = t * t;
    double t3 = t2 * t;
    double t4 = t3 * t;
    double t5 = t4 * t;
    double b2 = B * B;
    double b3 = b2 * B;
    double b4 = b3 * B;
    double b5 = b4 * B;
    double term =
        b5 + 17664.0 * t4 * B - 4192.0 * t3 * b2 + 528.0 * b3 * t2 - 36.0 * t * b4 - 30720.0 * t5;
    double denom1 = B - 8.0 * t;
    double denom1Cubed = denom1 * denom1 * denom1;
    double denom2 = B - 4.0 * t;
    double denom2Cubed = denom2 * denom2 * denom2;
    return 4.0 * term * B / t3 / denom1Cubed / denom2Cubed;
  }
}
