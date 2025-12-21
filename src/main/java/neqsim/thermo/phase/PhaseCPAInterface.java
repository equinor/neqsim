package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.mixingrule.CPAMixingRulesInterface;

/**
 * <p>
 * PhaseCPAInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface PhaseCPAInterface extends PhaseEosInterface {

  /**
   * Get the molecule number array mapping association sites to component indices.
   *
   * @return the moleculeNumber array
   */
  int[] getMoleculeNumber();

  /**
   * Get the association site number array.
   *
   * @return the assSiteNumber array
   */
  int[] getAssSiteNumber();

  /**
   * Get the association strength (delta) matrix.
   *
   * @return the delta matrix
   */
  double[][] getCpaDelta();

  /**
   * Solve for association site fractions (X) using successive substitution.
   *
   * @param maxIter maximum number of iterations
   * @return true if converged, false otherwise
   */
  default boolean solveX2(int maxIter) {
    double err = 0.0;
    int iter = 0;
    double old = 0.0;
    double neeval = 0.0;
    double totalVolume = getTotalVolume();
    int[] moleculeNumber = getMoleculeNumber();
    int[] assSiteNumber = getAssSiteNumber();
    double[][] delta = getCpaDelta();
    int totalNumberOfAccociationSites = getTotalNumberOfAccociationSites();

    do {
      iter++;
      err = 0.0;
      for (int i = 0; i < totalNumberOfAccociationSites; i++) {
        old =
            ((ComponentCPAInterface) getComponent(moleculeNumber[i])).getXsite()[assSiteNumber[i]];
        neeval = 0.0;
        for (int j = 0; j < totalNumberOfAccociationSites; j++) {
          neeval += getComponent(moleculeNumber[j]).getNumberOfMolesInPhase() * delta[i][j]
              * ((ComponentCPAInterface) getComponent(moleculeNumber[j]))
                  .getXsite()[assSiteNumber[j]];
        }
        neeval = 1.0 / (1.0 + 1.0 / totalVolume * neeval);
        ((ComponentCPAInterface) getComponent(moleculeNumber[i])).setXsite(assSiteNumber[i],
            neeval);
        err += Math.abs((old - neeval) / neeval);
      }
    } while (Math.abs(err) > 1e-12 && iter < maxIter);

    return Math.abs(err) < 1e-12;
  }

  /**
   * Calculate the total association contribution to the Helmholtz energy (hCPA). This is the sum
   * over all components and association sites of (1 - X_Ai), where X_Ai is the fraction of
   * molecules of component i not bonded at site A.
   *
   * @return the total association contribution
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
   * Calculate the temperature derivative of hCPA.
   *
   * @return the temperature derivative of the association contribution
   */
  default double calc_hCPAdT() {
    double htot = 0.0;
    double tot = 0.0;
    for (int i = 0; i < getNumberOfComponents(); i++) {
      for (int k = 0; k < getNumberOfComponents(); k++) {
        htot = 0.0;
        for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
          for (int l = 0; l < getComponent(k).getNumberOfAssociationSites(); l++) {
            htot += ((ComponentCPAInterface) getComponent(i)).getXsite()[j]
                * ((ComponentCPAInterface) getComponent(k)).getXsite()[l]
                * getCpaMixingRule().calcDeltadT(j, l, i, k, this, getTemperature(), getPressure(),
                    getNumberOfComponents());
          }
        }
        tot += getComponent(i).getNumberOfMolesInPhase() * getComponent(k).getNumberOfMolesInPhase()
            * htot;
      }
    }
    return tot / getTotalVolume();
  }

  /**
   * Calculate the second temperature derivative of hCPA.
   *
   * @return the second temperature derivative of the association contribution
   */
  default double calc_hCPAdTdT() {
    double htot = 0.0;
    double tot = 0.0;
    for (int i = 0; i < getNumberOfComponents(); i++) {
      for (int k = 0; k < getNumberOfComponents(); k++) {
        htot = 0.0;
        for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
          for (int l = 0; l < getComponent(k).getNumberOfAssociationSites(); l++) {
            htot += ((ComponentCPAInterface) getComponent(i)).getXsite()[j]
                * ((ComponentCPAInterface) getComponent(k)).getXsite()[l]
                * getCpaMixingRule().calcDeltadTdT(j, l, i, k, this, getTemperature(),
                    getPressure(), getNumberOfComponents());
          }
        }
        tot += getComponent(i).getNumberOfMolesInPhase() * getComponent(k).getNumberOfMolesInPhase()
            * htot;
      }
    }
    return tot / getTotalVolume();
  }

  /**
   * <p>
   * Getter for property hcpatot.
   * </p>
   *
   * @return a double
   */
  double getHcpatot();

  /**
   * Get the self-association scheme array.
   *
   * @return the selfAccociationScheme array [component][site1][site2]
   */
  int[][][] getSelfAccociationScheme();

  /**
   * Get the cross-association scheme array.
   *
   * @return the crossAccociationScheme array [comp1][comp2][site1][site2]
   */
  int[][][][] getCrossAccociationScheme();

  /**
   * Get the cross-association scheme value for given components and sites. Default implementation
   * uses the self and cross association scheme arrays.
   *
   * @param comp1 component 1 index
   * @param comp2 component 2 index
   * @param site1 site 1 index
   * @param site2 site 2 index
   * @return the association scheme value
   */
  default int getCrossAssosiationScheme(int comp1, int comp2, int site1, int site2) {
    if (comp1 == comp2) {
      return getSelfAccociationScheme()[comp1][site1][site2];
    } else {
      return getCrossAccociationScheme()[comp1][comp2][site1][site2];
    }
  }

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

  /**
   * Calculate the radial distribution function g for CPA. Default implementation uses the SRK-type
   * formula: g = (2 - b/4V) / (2 * (1 - b/4V)^3). Electrolyte classes may override with their own
   * formula.
   *
   * @return the radial distribution function value
   */
  default double calc_g() {
    double b = getB() / getNumberOfMolesInPhase();
    double bOver4V = b / 4.0 / getMolarVolume();
    double temp = 1.0 - bOver4V;
    return (2.0 - bOver4V) / (2.0 * temp * temp * temp);
  }

  /**
   * Calculate the volume derivative of ln(g) for CPA. Default implementation uses the SRK-type
   * formula.
   *
   * @return d(ln g)/dV
   */
  default double calc_lngV() {
    double b = getB();
    double t = getTotalVolume();
    double t2 = t * t;
    double bOver4t = b / (4.0 * t);
    double factor = b / (4.0 * t2);
    return factor / (2.0 - bOver4t) - 3.0 * factor / (1.0 - bOver4t);
  }

  /**
   * Calculate the second volume derivative of ln(g) for CPA. Default implementation uses the
   * SRK-type formula.
   *
   * @return d2(ln g)/dV2
   */
  default double calc_lngVV() {
    double b = getB();
    double t = getTotalVolume();
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
   * Calculate the third volume derivative of ln(g) for CPA. Default implementation uses the
   * SRK-type formula.
   *
   * @return d3(ln g)/dV3
   */
  default double calc_lngVVV() {
    double b = getB();
    double t = getTotalVolume();
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
}
