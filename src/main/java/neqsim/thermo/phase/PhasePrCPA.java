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
    componentArray[compNumber] = new ComponentSrkCPA(name, moles, molesInPhase, compNumber, this);
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i] instanceof ComponentSrkCPA) {
        ((ComponentSrkCPA) componentArray[i]).resizeXsitedni(numberOfComponents);
      }
    }
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

  // calc_hCPA, calc_hCPAdT, calc_hCPAdTdT, calc_g, calc_lngV, calc_lngVV, calc_lngVVV methods
  // are now provided by PhaseCPAInterface default implementation

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

  // getCrossAssosiationScheme method is now provided by PhaseCPAInterface default implementation

  /** {@inheritDoc} */
  @Override
  public int[][][] getSelfAccociationScheme() {
    return selfAccociationScheme;
  }

  /** {@inheritDoc} */
  @Override
  public int[][][][] getCrossAccociationScheme() {
    return crossAccociationScheme;
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
