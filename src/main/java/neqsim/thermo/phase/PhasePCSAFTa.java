package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.component.ComponentPCSAFTa;
import neqsim.thermo.mixingrule.CPAMixingRuleHandler;
import neqsim.thermo.mixingrule.CPAMixingRulesInterface;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;

/**
 * <p>
 * PhasePCSAFTa class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhasePCSAFTa extends PhasePCSAFT implements PhaseCPAInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhasePCSAFTa.class);

  public CPAMixingRuleHandler cpaSelect = new CPAMixingRuleHandler();
  public CPAMixingRulesInterface cpamix;
  double hcpatot = 1.0;
  double hcpatotdT = 0.0;
  double hcpatotdTdT = 0.0;
  int cpaon = 1;
  int totalNumberOfAccociationSites = 0;
  double gcpav = 0.0;
  double lngcpa = 0.0;
  double gcpavv = 1.0;
  double gcpavvv = 0.0;
  double gcpa = 0.0;

  int[][][] selfAccociationScheme = null;
  int[][][][] crossAccociationScheme = null;

  /**
   * <p>
   * Constructor for PhasePCSAFTa.
   * </p>
   */
  public PhasePCSAFTa() {}

  /** {@inheritDoc} */
  @Override
  public PhasePCSAFTa clone() {
    PhasePCSAFTa clonedPhase = null;
    try {
      clonedPhase = (PhasePCSAFTa) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    // clonedPhase.cpaSelect = (CPAMixing) cpaSelect.clone();

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(MixingRuleTypeInterface mr) {
    // NB! Sets EOS mixing rule in parent class PhaseEos
    super.setMixingRule(mr);
    // NB! Ignores input mr, uses CPA
    cpamix = cpaSelect.getMixingRule(3, this);
  }

  /** {@inheritDoc} */
  @Override
  public void volInit() {
    super.volInit();
    gcpa = getGhsSAFT();
    lngcpa = Math.log(getGhsSAFT());
    gcpav = 1.0 / getGhsSAFT() * getDgHSSAFTdN() * getDnSAFTdV();
    gcpavv =
        -1.0 / Math.pow(getGhsSAFT(), 2.0) * dgHSSAFTdN * dgHSSAFTdN * getDnSAFTdV() * getDnSAFTdV()
            + 1.0 / getGhsSAFT() * dgHSSAFTdNdN * getDnSAFTdV() * getDnSAFTdV()
            + 1.0 / getGhsSAFT() * getDgHSSAFTdN() * dnSAFTdVdV;
    gcpavvv = 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
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
    int solveXAttempts = 0;
    do {
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
      solveXAttempts++;
    } while (!solveX() && solveXAttempts < 50);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentPCSAFTa(name, moles, molesInPhase, compNumber);
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
        double xai = ((ComponentPCSAFTa) getComponent(i)).getXsite()[j];
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
    return 1.0 / (2.0 * getTotalVolume()) * (1.0 - getTotalVolume() * gcpav * 1.0E-5) * hcpatot;
  }

  /**
   * <p>
   * dFCPAdVdV.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdVdV() {
    return -1.0 / getTotalVolume() * dFCPAdV() + hcpatot / (2.0 * getTotalVolume())
        * (-gcpav * 1.0E-5 - getTotalVolume() * gcpavv * 1.0E-10);
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
        - hcpatot / (2.0 * Math.pow(getTotalVolume(), 2.0))
            * (-gcpav * 1.0E-5 - getTotalVolume() * gcpavv * 1.0E-10)
        + hcpatot / (2.0 * getTotalVolume())
            * (-gcpavv * 1.0E-10 - getTotalVolume() * gcpavvv * 1.0E-10 - gcpavv * 1.0E-10);
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
        htot += (1.0 - ((ComponentPCSAFTa) getComponent(i)).getXsite()[j]);
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
            htot += ((ComponentPCSAFTa) getComponent(i)).getXsite()[j]
                * ((ComponentPCSAFTa) getComponent(k)).getXsite()[l]
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
            htot += ((ComponentPCSAFTa) getComponent(i)).getXsite()[j]
                * ((ComponentPCSAFTa) getComponent(k)).getXsite()[l]
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
   * solveX.
   * </p>
   *
   * @return a boolean
   */
  public boolean solveX() {
    double err = .0;
    int iter = 0;

    do {
      iter++;
      err = 0.0;
      for (int i = 0; i < numberOfComponents; i++) {
        for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
          double old = ((ComponentPCSAFTa) getComponent(i)).getXsite()[j];
          double neeval = cpamix.calcXi(selfAccociationScheme, crossAccociationScheme, j, i, this,
              temperature, pressure, numberOfComponents);
          ((ComponentCPAInterface) getComponent(i)).setXsite(j, neeval);
          err += Math.abs((old - neeval) / neeval);
        }
      }
      // System.out.println("err " + err);
    } while (Math.abs(err) > 1e-10 && iter < 100);
    // System.out.println("iter " +iter);
    return Math.abs(err) < 1e-10;
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

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double BonV = pt == PhaseType.GAS ? pressure * getB() / (numberOfMolesInPhase * temperature * R)
        : 2.0 / (2.0 + temperature / getPseudoCriticalTemperature());
    BonV = Math.max(1.0e-4, Math.min(1.0 - 1.0e-4, BonV));
    double BonVold = BonV;
    double Btemp = 0;
    double h = 0;
    double dh = 0;
    // double gvvv = 0, fvvv = 0, dhh = 0, d2 = 0;
    double d1 = 0;
    Btemp = getB();
    if (Btemp <= 0) {
      logger.info("b negative in volume calc");
    }
    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    int iterations = 0;
    int maxIterations = 1000;
    // System.out.println("volume " + getVolume());
    do {
      iterations++;
      this.volInit();
      solveX();
      hcpatot = calc_hCPA();

      // System.out.println("saft volume " + getVolumeSAFT());
      BonVold = BonV;
      h = BonV - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      dh = 1.0 + Btemp / Math.pow(BonV, 2.0) * (Btemp / numberOfMolesInPhase * dFdVdV());
      // dhh = -2.0 * Btemp / Math.pow(BonV, 3.0) * (Btemp / numberOfMolesInPhase * dFdVdV())-
      // Math.pow(Btemp, 2.0) / Math.pow(BonV, 4.0) * (Btemp / numberOfMolesInPhase * dFdVdVdV());

      d1 = -h / dh;
      // d2 = -dh / dhh;
      BonV += d1; // (1.0+0.5*-1.0);
      // if(Math.abs(d1/d2)<=1.0){
      // BonV += d1*(1.0+0.5*d1/d2);
      // } else if(d1/d2<-1){
      // BonV += d1*(1.0+0.5*-1.0);
      // } else if(d1/d2>1){
      // BonV += d2;
      // double hnew = h +d2*-h/d1;
      // if(Math.abs(hnew)>Math.abs(h)){
      // System.out.println("volume correction needed....");
      // BonV = phase== 1 ?
      // 2.0/(2.0+temperature/getPseudoCriticalTemperature()):pressure*getB()/(numberOfMolesInPhase*temperature*R);
      // }
      // }

      // if(BonV>1){
      // BonV=1.0-1.0e-6;
      // BonVold=10;
      // }

      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
      // System.out.println("Z " + Z);
    } while (Math.abs((BonV - BonVold) / BonV) > 1.0e-10 && iterations < maxIterations);
    // System.out.println("error BonV " + Math.abs((BonV-BonVold)/BonV));
    // System.out.println("iterations " + iterations);
    if (BonV < 0) {
      BonV = pressure * getB() / (numberOfMolesInPhase * temperature * R);
      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
    }
    if (iterations >= maxIterations) {
      throw new neqsim.util.exception.TooManyIterationsException(this, "molarVolume",
          maxIterations);
    }
    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Molar volume");
      // if(pt==0)
      // System.out.println("density " +
      // getDensity()); //"BonV: " + BonV + "
      // "+" itert: " +
      // iterations +" " + " phase " +
      // pt+ " " + h +
      // " +dh + " B " + Btemp + " D " +
      // Dtemp + " gv" + gV()
      // + " fv " + fv() + " fvv" + fVV());
    }
    return getMolarVolume();
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
