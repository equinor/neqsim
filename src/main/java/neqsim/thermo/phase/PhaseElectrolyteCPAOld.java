package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.component.ComponentElectrolyteCPA;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.mixingrule.CPAMixingRuleHandler;
import neqsim.thermo.mixingrule.CPAMixingRulesInterface;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;

/**
 * <p>
 * PhaseElectrolyteCPAOld class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseElectrolyteCPAOld extends PhaseModifiedFurstElectrolyteEos
    implements PhaseCPAInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseElectrolyteCPAOld.class);

  public CPAMixingRuleHandler cpaSelect = new CPAMixingRuleHandler();

  int totalNumberOfAccociationSites = 0;
  public CPAMixingRulesInterface cpamix;
  double hcpatot = 1.0;
  double hcpatotdT = 0.0;
  double hcpatotdTdT = 0.0;
  double gcpav = 0.0;
  double lngcpa = 0.0;
  double lngcpav = 0.0;
  double gcpavv = 1.0;
  double gcpavvv = 0.0;
  double gcpa = 0.0;

  int cpaon = 1;
  int[][][] selfAccociationScheme = null;
  int[][][][] crossAccociationScheme = null;
  double dFdVdXdXdVtotal = 0.0;
  double dFCPAdXdXdTtotal = 0.0;
  double dFCPAdTdT = 0.0;

  /**
   * <p>
   * Constructor for PhaseElectrolyteCPAOld.
   * </p>
   */
  public PhaseElectrolyteCPAOld() {}

  /** {@inheritDoc} */
  @Override
  public PhaseElectrolyteCPAOld clone() {
    PhaseElectrolyteCPAOld clonedPhase = null;
    try {
      clonedPhase = (PhaseElectrolyteCPAOld) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    // clonedPhase.cpaSelect = (CPAMixing) cpaSelect.clone();

    return clonedPhase;
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
    do {
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    } while (!solveX());

    // System.out.println("test1 " + dFCPAdT());
    if (initType > 1) {
      // calcXsitedT();
      // System.out.println("test2 " + dFCPAdT());
      hcpatotdT = calc_hCPAdT();
      hcpatotdTdT = calc_hCPAdTdT();
    }

    // System.out.println("tot iter " + totiter);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(MixingRuleTypeInterface mr) {
    // NB! Sets EOS mixing rule in parent class PhaseEos
    super.setMixingRule(mr);
    // NB! Ignores input mr, uses CPA
    cpamix = cpaSelect.getMixingRule(1, this);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentElectrolyteCPA(name, moles, molesInPhase, compNumber);
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
        double xai = ((ComponentElectrolyteCPA) getComponent(i)).getXsite()[j];
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
    return 1.0 / (2.0 * getTotalVolume()) * (1.0 - getTotalVolume() * getGcpav()) * hcpatot;
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
        + hcpatot / (2.0 * getTotalVolume()) * (-getGcpav() - getTotalVolume() * gcpavv)
        + getdFdVdXdXdVtotal();
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
            * (-getGcpav() - getTotalVolume() * gcpavv)
        + hcpatot / (2.0 * getTotalVolume()) * (-gcpavv - getTotalVolume() * gcpavvv - gcpavv);
  }

  /**
   * <p>
   * dFCPAdT.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdT() {
    // System.out.println("dFCPAdXdXdTtotal " + dFCPAdXdXdTtotal);
    return dFCPAdXdXdTtotal;
    // -1.0 / 2.0 * hcpatotdT;
  }

  /**
   * <p>
   * dFCPAdTdT.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdTdT() {
    return dFCPAdTdT; // -1.0 / 2.0 * hcpatotdTdT;
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
        htot += (1.0 - ((ComponentElectrolyteCPA) getComponent(i)).getXsite()[j]);
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
            htot += ((ComponentElectrolyteCPA) getComponent(i)).getXsite()[j]
                * ((ComponentElectrolyteCPA) getComponent(k)).getXsite()[l]
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
            htot += ((ComponentElectrolyteCPA) getComponent(i)).getXsite()[j]
                * ((ComponentElectrolyteCPA) getComponent(k)).getXsite()[l]
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
    double x = 1.9 / 4.0 * getB() / getTotalVolume();
    double g = 1.0 / (1.0 - x);
    // System.out.println("ratio " + getMolarVolume()/getb());
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
    double nbet = getb() / 4.0 / getMolarVolume();
    double dlngdb = 1.9 / (1.0 - 1.9 * nbet);
    double nbeti = nbet / getb() * ((ComponentEosInterface) getComponent(comp)).getBi();
    return dlngdb * nbeti;
  }

  /**
   * <p>
   * calc_lngV.
   * </p>
   *
   * @return a double
   */
  public double calc_lngV() {
    double x = 1.9 / 4.0 * getB() / getTotalVolume();
    double gv = (x / getTotalVolume()) / (1.0 - x);
    return -gv;
  }

  /**
   * <p>
   * calc_lngVV.
   * </p>
   *
   * @return a double
   */
  public double calc_lngVV() {
    double x = 1.9 / 4.0 * getB() / getTotalVolume();
    double xV = -1.9 / 4.0 * getB() / Math.pow(getTotalVolume(), 2.0);
    double u = 1.0 - x;

    double val = -x / (Math.pow(getTotalVolume(), 2.0) * u) + xV / (getTotalVolume() * u)
        - x / (getTotalVolume() * u * u) * (-1.0) * xV;
    return -val;

    // double gvv
    // =0.225625/Math.pow(1.0-0.475*getB()/getTotalVolume(),2.0)*Math.pow(getB(),2.0)/(Math.pow(getTotalVolume(),4.0))+0.95/(1.0-0.475*getB()/getTotalVolume())*getB()/(Math.pow(getTotalVolume(),3.0));
    // System.out.println("val2 " + gvv);
    // return gvv;
  }

  /**
   * <p>
   * calc_lngVVV.
   * </p>
   *
   * @return a double
   */
  public double calc_lngVVV() {
    double gvv = -0.21434375 / Math.pow(1.0 - 0.475 * getB() / getTotalVolume(), 3.0)
        * Math.pow(getB(), 3.0) / (Math.pow(getTotalVolume(), 6.0))
        - 0.135375E1 / Math.pow(1.0 - 0.475 * getB() / getTotalVolume(), 2.0)
            * Math.pow(getB(), 2.0) / (Math.pow(getTotalVolume(), 5.0))
        - 0.285E1 / (1.0 - 0.475 * getB() / getTotalVolume()) * getB()
            / (Math.pow(getTotalVolume(), 4.0));
    return gvv;
  }

  /**
   * <p>
   * setXsiteOld.
   * </p>
   */
  public void setXsiteOld() {
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
        ((ComponentCPAInterface) getComponent(i)).setXsiteOld(j,
            ((ComponentCPAInterface) getComponent(i)).getXsite()[j]);
      }
    }
  }

  /**
   * <p>
   * setXsitedV.
   * </p>
   *
   * @param dV a double
   */
  public void setXsitedV(double dV) {
    dFdVdXdXdVtotal = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
        double XdV = (((ComponentCPAInterface) getComponent(i)).getXsite()[j]
            - ((ComponentCPAInterface) getComponent(i)).getXsiteOld()[j]) / dV;
        ((ComponentCPAInterface) getComponent(i)).setXsitedV(j, XdV);
        dFdVdXdXdVtotal += XdV * ((ComponentCPAInterface) getComponent(i)).dFCPAdVdXi(j, this);
        // System.out.println("xidv " + XdV);
      }
    }
  }

  /**
   * <p>
   * calcXsitedT.
   * </p>
   */
  public void calcXsitedT() {
    double dt = 0.01;
    double XdT = 0.0;
    setXsiteOld();
    setTemperature(temperature + dt);
    solveX();
    dFCPAdXdXdTtotal = 0.0;
    dFCPAdTdT = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
        XdT = (((ComponentCPAInterface) getComponent(i)).getXsite()[j]
            - ((ComponentCPAInterface) getComponent(i)).getXsiteOld()[j]) / dt;
        ((ComponentCPAInterface) getComponent(i)).setXsitedT(j, XdT);
        dFCPAdXdXdTtotal += XdT * ((ComponentCPAInterface) getComponent(i)).dFCPAdXi(j, this);
      }
    }
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
        for (int k = 0; k < numberOfComponents; k++) {
          for (int j2 = 0; j2 < getComponent(k).getNumberOfAssociationSites(); j2++) {
            dFCPAdTdT += ((ComponentCPAInterface) getComponent(i)).dFCPAdXidXj(j, j2, k, this)
                * ((ComponentCPAInterface) getComponent(i)).getXsitedT()[j]
                * ((ComponentCPAInterface) getComponent(k)).getXsitedT()[j2];
          }
        }
      }
    }
    setTemperature(temperature - dt);
    solveX();
  }

  /**
   * <p>
   * Getter for the field <code>dFdVdXdXdVtotal</code>.
   * </p>
   *
   * @return a double
   */
  public double getdFdVdXdXdVtotal() {
    return dFdVdXdXdVtotal;
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
          double old = ((ComponentElectrolyteCPA) getComponent(i)).getXsite()[j];
          double neeval = getCpaMixingRule().calcXi(selfAccociationScheme, crossAccociationScheme,
              j, i, this, temperature, pressure, numberOfComponents);
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
   * molarVolume3.
   * </p>
   *
   * @param pressure a double
   * @param temperature a double
   * @param A a double
   * @param B a double
   * @param pt the PhaseType of the phase
   * @return a double
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public double molarVolume3(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double BonV =
        pt == PhaseType.LIQUID ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
            : pressure * getB() / (numberOfMolesInPhase * temperature * R);

    if (BonV < 0) {
      BonV = 1.0e-8;
    }
    if (BonV > 1.0) {
      BonV = 1.0 - 1.0e-8;
    }
    double BonVold = BonV;
    double Btemp = 0;
    double h = 0;
    double dh = 0;
    double dhh = 0;
    // double Dtemp = 0, gvvv = 0, fvvv = 0;
    double d1 = 0;
    double d2 = 0;
    Btemp = getB();
    // Dtemp = getA();
    if (Btemp < 0) {
      logger.info("b negative in volume calc");
    }
    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    int iterations = 0;
    int maxIterations = 101;
    do {
      this.volInit();
      gcpa = calc_g();
      lngcpa = Math.log(gcpa);
      gcpav = calc_lngV();
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();
      solveX();
      hcpatot = calc_hCPA();

      iterations++;
      BonVold = BonV;
      h = BonV - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      dh = 1.0 + Btemp / Math.pow(BonV, 2.0) * (Btemp / numberOfMolesInPhase * dFdVdV());
      dhh = -2.0 * Btemp / Math.pow(BonV, 3.0) * (Btemp / numberOfMolesInPhase * dFdVdV())
          - Math.pow(Btemp, 2.0) / Math.pow(BonV, 4.0)
              * (Btemp / numberOfMolesInPhase * dFdVdVdV());

      d1 = -h / dh;
      d2 = -dh / dhh;

      if (Math.abs(d1 / d2) <= 1.0) {
        BonV += d1 * (1.0 + 0.5 * d1 / d2);
      } else if (d1 / d2 < -1) {
        BonV += d1 * (1.0 + 0.5 * -1.0);
      } else if (d1 / d2 > 1) {
        BonV += d2;
        double hnew = h + d2 * -h / d1;
        if (Math.abs(hnew) > Math.abs(h)) {
          BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
              : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        }
      }

      if (BonV > 1) {
        BonV = 1.0 - 1.0e-8;
        BonVold = 10;
      }
      if (BonV < 0) {
        BonV = 1.0e-8;
        BonVold = 10;
      }

      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
      // System.out.println("Z" + Z);
    } while (Math.abs((BonV - BonVold) / BonV) > 1.0e-10 && iterations < maxIterations);
    // System.out.println("Z" + Z + " iterations " + iterations);
    // System.out.println("pressure " + Z*R*temperature/molarVolume);
    // if(iterations>=100) throw new util.exception.TooManyIterationsException();
    // System.out.println("error in volume " +
    // (-pressure+R*temperature/molarVolume-R*temperature*dFdV()) + " firstterm " +
    // (R*temperature/molarVolume) + " second " + R*temperature*dFdV());
    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume3", "Molar volume");
      // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
      // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
      // + fVV());
    }
    return getMolarVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double BonV = pt == PhaseType.GAS ? pressure * getB() / (numberOfMolesInPhase * temperature * R)
        : 2.0 / (2.0 + temperature / getPseudoCriticalTemperature());
    BonV = Math.max(1.0e-8, Math.min(1.0 - 1.0e-8, BonV));

    if (BonV < 0) {
      BonV = 1.0e-8;
    }
    if (BonV > 1.0) {
      BonV = 1.0 - 1.0e-8;
    }
    double BonVold = BonV;
    double Btemp = 0;
    double h = 0;
    double dh = 0;
    double dhh = 0;
    // double Dtemp = 0, fvvv = 0, gvvv = 0;
    double d1 = 0;
    double d2 = 0;
    Btemp = getB();
    // Dtemp = getA();
    if (Btemp < 0) {
      logger.info("b negative in volume calc");
    }
    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    int iterations = 0;
    int maxIterations = 1000;
    double oldVolume = getVolume();
    do {
      this.volInit();
      gcpa = calc_g();
      lngcpa = Math.log(gcpa);
      setGcpav(calc_lngV());
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();

      solveX();
      hcpatot = calc_hCPA();

      double dV = getVolume() - oldVolume;
      if (iterations > 0) {
        setXsitedV(dV);
      }
      oldVolume = getVolume();
      setXsiteOld();

      iterations++;
      BonVold = BonV;
      h = BonV - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      dh = 1.0 + Btemp / Math.pow(BonV, 2.0) * (Btemp / numberOfMolesInPhase * dFdVdV());
      dhh = -2.0 * Btemp / Math.pow(BonV, 3.0) * (Btemp / numberOfMolesInPhase * dFdVdV())
          - Math.pow(Btemp, 2.0) / Math.pow(BonV, 4.0)
              * (Btemp / numberOfMolesInPhase * dFdVdVdV());

      d1 = -h / dh;
      d2 = -dh / dhh;

      if (Math.abs(d1 / d2) <= 1.0) {
        BonV += d1 * (1.0 + 0.5 * d1 / d2);
      } else if (d1 / d2 < -1) {
        BonV += d1 * (1.0 + 0.5 * -1.0);
      } else if (d1 / d2 > 1) {
        BonV += d2;
        double hnew = h + d2 * -h / d1;
        if (Math.abs(hnew) > Math.abs(h)) {
          BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
              : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        }
      }

      if (BonV > 1) {
        BonV = 1.0 - 1.0e-8;
        BonVold = 10;
      }
      if (BonV < 0) {
        BonV = 1.0e-8;
        BonVold = 10;
      }

      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);

      // System.out.println("Z" + Z);
    } while (Math.abs((BonV - BonVold) / BonV) > 1.0e-10 && iterations < maxIterations);
    // System.out.println("Z" + Z + " iterations " + iterations + " h " + h);
    // System.out.println("pressure " + Z*R*temperature/getMolarVolume());
    // if(iterations>=100) throw new util.exception.TooManyIterationsException();
    // System.out.println("error in volume " +
    // (-pressure+R*temperature/getMolarVolume()-R*temperature*dFdV())); // + "
    // firstterm " + (R*temperature/molarVolume) + " second " +
    // R*temperature*dFdV());
    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Molar volume");
      // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
      // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
      // + fVV());
    }
    return getMolarVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume2(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    Z = pt == PhaseType.LIQUID ? 1.0 : 1.0e-5;
    setMolarVolume(Z * R * temperature / pressure);
    // super.molarVolume(pressure,temperature, A, B, phase);
    int iterations = 0;
    double err = 0.0;
    double dErrdV = 0.0;
    double deltaV = 0;

    do {
      iterations = iterations + 1;
      A = calcA(this, temperature, pressure, numberOfComponents);
      B = calcB(this, temperature, pressure, numberOfComponents);
      double dFdV = dFdV();
      double dFdVdV = dFdVdV();
      // double dFdVdVdV = dFdVdVdV();
      // double factor1 = 1.0e0, factor2 = 1.0e0;
      err = -R * temperature * dFdV + R * temperature / getMolarVolume() - pressure;

      logger.info("pressure " + -R * temperature * dFdV + " " + R * temperature / getMolarVolume());
      // -pressure;
      dErrdV = -R * temperature * dFdVdV
          - R * temperature * numberOfMolesInPhase / Math.pow(getVolume(), 2.0);

      logger.info("errdV " + dErrdV);
      logger.info("err " + err);

      deltaV = -err / dErrdV;

      setMolarVolume(getMolarVolume() + deltaV / numberOfMolesInPhase);

      Z = pressure * getMolarVolume() / (R * temperature);
      if (Z < 0) {
        Z = 1e-6;
        setMolarVolume(Z * R * temperature / pressure);
      }
      // System.out.println("Z " + Z);
    } while (Math.abs(err) > 1.0e-8 || iterations < 100);
    return getMolarVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double getGcpav() {
    return gcpav;
  }

  /**
   * <p>
   * Setter for the field <code>gcpav</code>.
   * </p>
   *
   * @param gcpav a double
   */
  public void setGcpav(double gcpav) {
    this.gcpav = gcpav;
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
