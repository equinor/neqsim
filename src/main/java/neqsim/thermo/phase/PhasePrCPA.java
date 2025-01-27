package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.mixingrule.CPAMixingRuleHandler;
import neqsim.thermo.mixingrule.CPAMixingRulesInterface;

/**
 * <p>
 * PhasePrCPA class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhasePrCPA extends PhasePrEos implements PhaseCPAInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  int totalNumberOfAccociationSites = 0;
  public CPAMixingRuleHandler cpaSelect = new CPAMixingRuleHandler();
  public CPAMixingRulesInterface cpamix;
  double hcpatot = 1.0;
  double hcpatotdT = 0.0;
  double hcpatotdTdT = 0.0;
  double gcpav = 1.0;
  double lngcpa = 0.0;
  double lngcpav = 1.0;
  double gcpavv = 1.0;
  double gcpavvv = 1.0;
  double gcpa = 1.0;

  int cpaon = 1;
  int[][][] selfAccociationScheme = null;
  int[][][][] crossAccociationScheme = null;

  /**
   * <p>
   * Constructor for PhasePrCPA.
   * </p>
   */
  public PhasePrCPA() {
    cpamix = cpaSelect.getMixingRule(1);
  }

  /** {@inheritDoc} */
  @Override
  public PhasePrCPA clone() {
    PhasePrCPA clonedPhase = null;
    try {
      clonedPhase = (PhasePrCPA) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    boolean Xsolved = true;
    int totiter = 0;
    do {
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
      // if(getPhaseType()==1) cpaon=0;
      totiter++;
      if (cpaon == 1) {
        Xsolved = solveX();
        hcpatot = calc_hCPA();
        gcpa = calc_g();
        lngcpa = Math.log(gcpa);

        gcpav = calc_lngV();
        gcpavv = calc_lngVV();
        gcpavvv = calc_lngVVV();
      }
    } while (!Xsolved && totiter < 5);

    if (initType > 1) {
      hcpatotdT = calc_hCPAdT();
      hcpatotdTdT = calc_hCPAdTdT();
    }
    // System.out.println("tot iter " + totiter);
    if (initType == 0) {
      selfAccociationScheme = new int[numberOfComponents][0][0];
      crossAccociationScheme = new int[numberOfComponents][numberOfComponents][0][0];
      for (int i = 0; i < numberOfComponents; i++) {
        selfAccociationScheme[i] = cpaSelect.setAssociationScheme(i, this);
        for (int j = 0; j < numberOfComponents; j++) {
          crossAccociationScheme[i][j] = cpaSelect.setCrossAssociationScheme(i, j, this);
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentSrkCPA(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public double getF() {
    return super.getF() + cpaon * FCPA();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdT() {
    return super.dFdT() + cpaon * dFCPAdT();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdV() {
    return super.dFdTdV();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
    // double dv = super.dFdV();
    double dv2 = dFCPAdV();
    // System.out.println("dv " + dv + " dvcpa " + dv2);
    return super.dFdV() + cpaon * dv2;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    return super.dFdVdV() + cpaon * dFCPAdVdV();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdVdV() {
    return super.dFdVdVdV() + cpaon * dFCPAdVdVdV();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdT() {
    return super.dFdTdT() + cpaon * dFCPAdTdT();
  }

  /**
   * <p>
   * FCPA.
   * </p>
   *
   * @return a double
   */
  public double FCPA() {
    double tot = 0.0;
    double ans = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      tot = 0.0;
      for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
        double xai = ((ComponentSrkCPA) getComponent(i)).getXsite()[j];
        tot += (Math.log(xai) - 1.0 / 2.0 * xai + 1.0 / 2.0);
      }
      ans += getComponent(i).getNumberOfMolesInPhase() * tot;
    }
    return ans;
  }

  /**
   * <p>
   * dFCPAdV.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdV() {
    return 1.0 / (2.0 * getTotalVolume()) * (1.0 - getTotalVolume() * gcpav) * hcpatot;
  }

  /**
   * <p>
   * dFCPAdVdV.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdVdV() {
    return -1.0 / getTotalVolume() * dFCPAdV()
        + hcpatot / (2.0 * getTotalVolume()) * (-gcpav - getTotalVolume() * gcpavv);
  }

  /**
   * <p>
   * dFCPAdVdVdV.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdVdVdV() {
    return -1.0 / getTotalVolume() * dFCPAdVdV() + 1.0 / Math.pow(getTotalVolume(), 2.0) * dFCPAdV()
        - hcpatot / (2.0 * Math.pow(getTotalVolume(), 2.0)) * (-gcpav - getTotalVolume() * gcpavv)
        + hcpatot / (2.0 * getTotalVolume()) * (-2.0 * gcpavv - getTotalVolume() * gcpavvv);
  }

  /**
   * <p>
   * dFCPAdT.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdT() {
    return -1.0 / 2.0 * hcpatotdT;
  }

  /**
   * <p>
   * dFCPAdTdT.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdTdT() {
    return -1.0 / 2.0 * hcpatotdTdT;
  }

  /**
   * <p>
   * calc_hCPA.
   * </p>
   *
   * @return a double
   */
  public double calc_hCPA() {
    double htot = 0.0;
    double tot = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      htot = 0.0;
      for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
        htot += (1.0 - ((ComponentSrkCPA) getComponent(i)).getXsite()[j]);
      }
      tot += getComponent(i).getNumberOfMolesInPhase() * htot;
    }
    // System.out.println("tot " +tot );
    return tot;
  }

  /**
   * <p>
   * calc_hCPAdT.
   * </p>
   *
   * @return a double
   */
  public double calc_hCPAdT() {
    double htot = 0.0;
    double tot = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int k = 0; k < numberOfComponents; k++) {
        htot = 0.0;
        for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
          for (int l = 0; l < getComponent(k).getNumberOfAssociationSites(); l++) {
            htot += ((ComponentSrkCPA) getComponent(i)).getXsite()[j]
                * ((ComponentSrkCPA) getComponent(k)).getXsite()[l]
                * cpamix.calcDeltadT(j, l, i, k, this, temperature, pressure, numberOfComponents);
          }
        }

        tot += getComponent(i).getNumberOfMolesInPhase() * getComponent(k).getNumberOfMolesInPhase()
            * htot;
      }
    }
    // System.out.println("tot " +tot );
    return tot / getTotalVolume();
  }

  /**
   * <p>
   * calc_hCPAdTdT.
   * </p>
   *
   * @return a double
   */
  public double calc_hCPAdTdT() {
    double htot = 0.0;
    double tot = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int k = 0; k < numberOfComponents; k++) {
        htot = 0.0;
        for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
          for (int l = 0; l < getComponent(k).getNumberOfAssociationSites(); l++) {
            htot += ((ComponentSrkCPA) getComponent(i)).getXsite()[j]
                * ((ComponentSrkCPA) getComponent(k)).getXsite()[l]
                * cpamix.calcDeltadTdT(j, l, i, k, this, temperature, pressure, numberOfComponents);
          }
        }

        tot += getComponent(i).getNumberOfMolesInPhase() * getComponent(k).getNumberOfMolesInPhase()
            * htot;
      }
    }
    // System.out.println("tot " +tot );
    return tot / getTotalVolume();
  }

  /**
   * <p>
   * calc_g.
   * </p>
   *
   * @return a double
   */
  public double calc_g() {
    double g = (2.0 - getb() / 4.0 / getMolarVolume())
        / (2.0 * Math.pow(1.0 - getb() / 4.0 / getMolarVolume(), 3.0));
    return g;
  }

  /**
   * <p>
   * calc_lngni.
   * </p>
   *
   * @param comp a int
   * @return a double
   */
  public double calc_lngni(int comp) {
    return 0;
  }

  /**
   * <p>
   * calc_lngV.
   * </p>
   *
   * @return a double
   */
  public double calc_lngV() {
    double gv = 0.0;
    gv = -2.0 * getB() * (10.0 * getTotalVolume() - getB()) / getTotalVolume()
        / ((8.0 * getTotalVolume() - getB()) * (4.0 * getTotalVolume() - getB()));

    // double gv2 =
    // 1.0/(2.0-getB()/(4.0*getTotalVolume()))*getB()/(4.0*Math.pow(getTotalVolume()
    // ,2.0))
    // - 3.0/(1.0-getB()/(4.0*getTotalVolume()))*getB()/(4.0*Math.pow(getTotalVolume() ,2.0));

    // System.out.println("err gv " + (100.0-gv/gv2*100));
    // -2.0*getB()*(10.0*getTotalVolume()-getB())/getTotalVolume()/((8.0*getTotalVolume()-getB())*(4.0*getTotalVolume()-getB()));
    // System.out.println("gv " + gv);

    return gv;
  }

  /**
   * <p>
   * calc_lngVV.
   * </p>
   *
   * @return a double
   */
  public double calc_lngVV() {
    double gvv = 0.0;
    gvv = 2.0
        * (640.0 * Math.pow(getTotalVolume(), 3.0)
            - 216.0 * getB() * getTotalVolume() * getTotalVolume()
            + 24.0 * Math.pow(getB(), 2.0) * getTotalVolume() - Math.pow(getB(), 3.0))
        * getB() / (getTotalVolume() * getTotalVolume())
        / Math.pow(8.0 * getTotalVolume() - getB(), 2.0)
        / Math.pow(4.0 * getTotalVolume() - getB(), 2.0);
    return gvv;
  }

  /**
   * <p>
   * calc_lngVVV.
   * </p>
   *
   * @return a double
   */
  public double calc_lngVVV() {
    double gvvv = 0.0;
    gvvv = 4.0
        * (Math.pow(getB(), 5.0) + 17664.0 * Math.pow(getTotalVolume(), 4.0) * getB()
            - 4192.0 * Math.pow(getTotalVolume(), 3.0) * Math.pow(getB(), 2.0)
            + 528.0 * Math.pow(getB(), 3.0) * getTotalVolume() * getTotalVolume()
            - 36.0 * getTotalVolume() * Math.pow(getB(), 4.0)
            - 30720.0 * Math.pow(getTotalVolume(), 5.0))
        * getB() / (Math.pow(getTotalVolume(), 3.0))
        / Math.pow(-8.0 * getTotalVolume() + getB(), 3.0)
        / Math.pow(-4.0 * getTotalVolume() + getB(), 3.0);
    return gvvv;
  }

  /**
   * <p>
   * solveX.
   * </p>
   *
   * @return a boolean
   */
  public boolean solveX() {
    double err = .0;
    int iter = 0;
    try {
      molarVolume(pressure, temperature, getA(), getB(), pt);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    do {
      iter++;
      err = 0.0;
      for (int i = 0; i < numberOfComponents; i++) {
        for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
          double old = ((ComponentSrkCPA) getComponent(i)).getXsite()[j];
          double neeval = cpamix.calcXi(selfAccociationScheme, crossAccociationScheme, j, i, this,
              temperature, pressure, numberOfComponents);

          ((ComponentCPAInterface) getComponent(i)).setXsite(j, neeval);
          err += Math.abs((old - neeval) / neeval);
        }
      }
      // System.out.println("err " + err);
    } while (Math.abs(err) > 1e-10 && iter < 100);
    // System.out.println("iter " +iter);
    return iter < 3;
  }

  /** {@inheritDoc} */
  @Override
  public double getHcpatot() {
    return hcpatot;
  }

  /**
   * Setter for property hcpatot.
   *
   * @param hcpatot New value of property hcpatot.
   */
  public void setHcpatot(double hcpatot) {
    this.hcpatot = hcpatot;
  }

  /** {@inheritDoc} */
  @Override
  public double getGcpa() {
    return gcpa;
  }

  /** {@inheritDoc} */
  @Override
  public double getGcpav() {
    return gcpav;
  }

  /** {@inheritDoc} */
  @Override
  public CPAMixingRulesInterface getCpaMixingRule() {
    return cpamix;
  }

  /** {@inheritDoc} */
  @Override
  public int getCrossAssosiationScheme(int comp1, int comp2, int site1, int site2) {
    if (comp1 == comp2) {
      return selfAccociationScheme[comp1][site1][site2];
    }
    return crossAccociationScheme[comp1][comp2][site1][site2];
  }

  /** {@inheritDoc} */
  @Override
  public int getTotalNumberOfAccociationSites() {
    return totalNumberOfAccociationSites;
  }

  /** {@inheritDoc} */
  @Override
  public void setTotalNumberOfAccociationSites(int totalNumberOfAccociationSites) {
    this.totalNumberOfAccociationSites = totalNumberOfAccociationSites;
  }
}
