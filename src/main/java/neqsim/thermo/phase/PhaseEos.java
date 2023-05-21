/*
 * PhaseEos.java
 *
 * Created on 3. juni 2000, 14:38
 */

package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.mixingRule.EosMixingRules;
import neqsim.thermo.mixingRule.EosMixingRulesInterface;

/**
 *
 * @author Even Solbraa
 */
abstract class PhaseEos extends Phase implements PhaseEosInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(PhaseEos.class);

  private double loc_A;
  private double loc_AT;
  private double loc_ATT;
  private double loc_B;
  private double f_loc = 0;
  private double g = 0;
  public double delta1 = 0;
  public double delta2 = 0;
  protected EosMixingRules mixSelect = null;
  protected EosMixingRulesInterface mixRule = null;
  double uEOS = 0;
  double wEOS = 0;
  // Class methods

  /** {@inheritDoc} */
  @Override
  public PhaseEos clone() {
    PhaseEos clonedPhase = null;
    try {
      clonedPhase = (PhaseEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    // clonedPhase.mixSelect = (EosMixingRules) mixSelect.clone();
    // clonedPhase.mixRule = (EosMixingRulesInterface) mixRule.clone();
    return clonedPhase;
  }

  /**
   * <p>
   * Constructor for PhaseEos.
   * </p>
   */
  public PhaseEos() {
    super();
    mixSelect = new EosMixingRules();
    componentArray = new ComponentEosInterface[MAX_NUMBER_OF_COMPONENTS];
    mixRule = mixSelect.getMixingRule(1);
    // solver = new newtonRhapson();
  }

  /** {@inheritDoc} */
  @Override
  public EosMixingRulesInterface getMixingRule() {
    return mixRule;
  }

  /** {@inheritDoc} */
  @Override
  public void displayInteractionCoefficients(String intType) {
    mixSelect.displayInteractionCoefficients(intType, this);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase,
      double beta) {
    if (phase > 1) {
      phase = 0;
    }
    if (!mixingRuleDefined) {
      setMixingRule(1);
    }

    super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);

    if (type != 0) {
      loc_B = calcB(this, temperature, pressure, numberOfComponents);
      loc_A = calcA(this, temperature, pressure, numberOfComponents);
    }

    if (isConstantPhaseVolume()) {
      setMolarVolume(getTotalVolume() / getNumberOfMolesInPhase());
      pressure = calcPressure();
    }

    if (type != 0) {
      try {
        if (calcMolarVolume) {
          molarVolume = molarVolume(pressure, temperature,
              getA() / numberOfMolesInPhase / numberOfMolesInPhase, getB() / numberOfMolesInPhase,
              phase);
        }
      } catch (Exception ex) {
        logger.error("Failed to solve for molarVolume within the iteration limit.");
        throw new RuntimeException(ex);
        // logger.error("too many iterations in volume calc!", ex);
        // logger.info("moles " + numberOfMolesInPhase);
        // logger.info("molarVolume " + getMolarVolume());
        // logger.info("setting molar volume to ideal gas molar volume.............");
        // setMolarVolume((R * temperature) / pressure);
        // System.exit(0);
      }

      Z = pressure * getMolarVolume() / (R * temperature);
      for (int i = 0; i < numberOfComponents; i++) {
        componentArray[i].Finit(this, temperature, pressure, totalNumberOfMoles, beta,
            numberOfComponents, type);
      }

      f_loc = calcf();
      g = calcg();

      if (type >= 2) {
        loc_AT = calcAT(this, temperature, pressure, numberOfComponents);
        loc_ATT = calcATT(this, temperature, pressure, numberOfComponents);
      }

      double sumHydrocarbons = 0.0;
      double sumAqueous = 0.0;
      for (int i = 0; i < numberOfComponents; i++) {
        if (getComponent(i).isHydrocarbon() || getComponent(i).isInert()
            || getComponent(i).isIsTBPfraction()) {
          sumHydrocarbons += getComponent(i).getx();
        } else {
          sumAqueous += getComponent(i).getx();
        }
      }

      if (getVolume() / getB() > 1.75) {
        setType(PhaseType.GAS);
      } else if (sumHydrocarbons > sumAqueous) {
        setType(PhaseType.OIL);
      } else {
        setType(PhaseType.AQUEOUS);
      }

      // if ((hasComponent("water") && getVolume() / getB() < 1.75 &&
      // getComponent("water").getx() > 0.1) || (hasComponent("MEG") && getVolume() /
      // getB() < 1.75 && getComponent("MEG").getx() > 0.1) || (hasComponent("TEG") &&
      // getComponent("TEG").getx() > 0.1) || (hasComponent("DEG") &&
      // getComponent("DEG").getx() > 0.1) || (hasComponent("methanol") &&
      // getComponent("methanol").getx() > 0.5 || (hasComponent("ethanol") &&
      // getComponent("ethanol").getx() > 0.5))) {
      // phaseTypeName = "aqueous";
      // }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(int type) {
    mixingRuleDefined = true;
    super.setMixingRule(type);
    mixRule = mixSelect.getMixingRule(type, this);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRuleGEModel(String name) {
    mixRule.setMixingRuleGEModel(name);
    mixSelect.setMixingRuleGEModel(name);
  }

  /** {@inheritDoc} */
  @Override
  public void resetMixingRule(int type) {
    mixingRuleDefined = true;
    super.setMixingRule(type);
    mixRule = mixSelect.resetMixingRule(type, this);
  }

  /**
   * <p>
   * molarVolume2.
   * </p>
   *
   * @param pressure a double
   * @param temperature a double
   * @param A a double
   * @param B a double
   * @param phase a int
   * @return a double
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public double molarVolume2(double pressure, double temperature, double A, double B, int phase)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double BonV = phase == 0 ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
        : pressure * getB() / (numberOfMolesInPhase * temperature * R);
    if (BonV < 0) {
      BonV = 0.0;
    }
    if (BonV > 1.0) {
      BonV = 1.0;
    }
    double BonVold = BonV;
    double Btemp = getB();
    double Dtemp = getA();

    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    int iterations = 0;
    int maxIterations = 1000;
    do {
      iterations++;
      BonVold = BonV;
      double h = BonV + Btemp * gV() + Btemp * Dtemp / (numberOfMolesInPhase * temperature) * fv()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      double dh = 1.0 - Btemp / (BonV * BonV)
          * (Btemp * gVV() + Btemp * Dtemp * fVV() / (numberOfMolesInPhase * temperature));
      double fvvv = 1.0 / (R * Btemp * (delta1 - delta2))
          * (2.0 / Math.pow(numberOfMolesInPhase * getMolarVolume() + Btemp * delta1, 3.0)
              - 2.0 / Math.pow(numberOfMolesInPhase * getMolarVolume() + Btemp * delta2, 3.0));
      double gvvv = 2.0 / Math.pow(numberOfMolesInPhase * getMolarVolume() - Btemp, 3.0)
          - 2.0 / Math.pow(numberOfMolesInPhase * getMolarVolume(), 3.0);
      double dhh = 2.0 * Btemp / Math.pow(BonV, 3.0)
          * (Btemp * gVV() + Btemp * Dtemp / (numberOfMolesInPhase * temperature) * fVV())
          + Btemp * Btemp / Math.pow(BonV, 4.0)
              * (Btemp * gvvv + Btemp * Dtemp / (numberOfMolesInPhase * temperature) * fvvv);

      double d1 = -h / dh;
      double d2 = -dh / dhh;

      if (Math.abs(d1 / d2) <= 1.0) {
        BonV += d1 * (1.0 + 0.5 * d1 / d2);
      } else if (d1 / d2 < -1) {
        BonV += d1 * (1.0 + 0.5 * -1.0);
      } else if (d1 / d2 > 1) {
        BonV += d2;
        double hnew = h + d2 * dh;
        if (Math.abs(hnew) > Math.abs(h)) {
          BonV += 0;
        }
      }

      if (BonV > 1) {
        BonV = 1.0 - 1.0e-16;
        BonVold = 10;
      }
      if (BonV < 0) {
        BonV = 1.0e-16;
        BonVold = 10;
      }

      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
    } while (Math.abs(BonV - BonVold) > 1.0e-9 && iterations < maxIterations);
    // molarVolume = 1.0/BonV*Btemp/numberOfMolesInPhase;
    // Z = pressure*molarVolume/(R*temperature);
    // logger.info("BonV: " + BonV + " " + h + " " +dh + " B " + Btemp + " D " +
    // Dtemp + " gv" + gV() + " fv " + fv() + " fvv" + fVV());
    // logger.info("BonV: " + BonV + " "+" itert: " + iterations +" " +h + " " +dh +
    // " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv" +
    // fVV());
    if (iterations >= maxIterations) {
      throw new neqsim.util.exception.TooManyIterationsException(this, "molarVolume2",
          maxIterations);
    }
    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume2", "Molar volume");
      // logger.info("BonV: " + BonV + " "+" itert: " + iterations +" " +h + " " +dh +
      // " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv" +
      // fVV());
    }
    return getMolarVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, int phase)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double BonV = phase == 0 ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
        : pressure * getB() / (numberOfMolesInPhase * temperature * R);

    if (BonV < 0) {
      BonV = 1.0e-4;
    }
    if (BonV > 1.0) {
      BonV = 1.0 - 1.0e-4;
    }

    double BonVold = BonV;

    double Btemp = getB();
    double h;
    double dh;
    double dhh;
    double d1;
    double d2;
    double BonV2;
    if (Btemp < 0) {
      logger.info("b negative in volume calc");
    }
    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    boolean changeFase = false;
    double error = 1.0;
    double errorOld = 1.0e10;
    int iterations = 0;
    int maxIterations = 300;
    do {
      errorOld = error;
      iterations++;
      BonVold = BonV;
      BonV2 = BonV * BonV;
      h = BonV - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      dh = 1.0 + Btemp / (BonV2) * (Btemp / numberOfMolesInPhase * dFdVdV());
      dhh = -2.0 * Btemp / (BonV2 * BonV) * (Btemp / numberOfMolesInPhase * dFdVdV())
          - Btemp * Btemp / (BonV2 * BonV2) * (Btemp / numberOfMolesInPhase * dFdVdVdV());

      d1 = -h / dh;
      d2 = -dh / dhh;

      if (Math.abs(d1 / d2) <= 1.0) {
        BonV += d1 * (1.0 + 0.5 * d1 / d2);
      } else if (d1 / d2 < -1) {
        BonV += d1 * (1.0 + 0.5 * -1.0);
      } else if (d1 > d2) {
        BonV += d2;
        double hnew = h + d2 * dh;
        if (Math.abs(hnew) > Math.abs(h)) {
          BonV = phase == 1 ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
              : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        }
      } else {
        BonV += d1 * (0.1);
      }

      if (BonV > 1) {
        BonV = 1.0 - 1.0e-6;
        BonVold = 100;
      }
      if (BonV < 0) {
        // BonV = Math.abs(BonV);
        BonV = 1.0e-10;
        BonVold = 10;
      }

      error = Math.abs((BonV - BonVold) / BonVold);
      // logger.info("error " + error);

      if (iterations > 150 && error > errorOld && !changeFase) {
        changeFase = true;
        BonVold = 10.0;
        BonV = phase == 1 ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
            : pressure * getB() / (numberOfMolesInPhase * temperature * R);
      }

      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
      // logger.info("Math.abs((BonV - BonVold)) " + Math.abs((BonV - BonVold)));
    } while (Math.abs((BonV - BonVold) / BonVold) > 1.0e-10 && iterations < maxIterations);
    // logger.info("pressure " + Z*R*temperature/molarVolume);
    // logger.info("error in volume " +
    // (-pressure+R*temperature/molarVolume-R*temperature*dFdV()) + " firstterm " +
    // (R*temperature/molarVolume) + " second " + R*temperature*dFdV());
    if (iterations >= maxIterations) {
      throw new neqsim.util.exception.TooManyIterationsException(this, "molarVolume",
          maxIterations);
    }
    if (Double.isNaN(getMolarVolume())) {
      // A = calcA(this, temperature, pressure, numberOfComponents);
      // molarVolume(pressure, temperature, A, B, phase);
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Molar volume");
      // logger.info("BonV: " + BonV + " "+" itert: " + iterations +" " +h + " " +dh +
      // " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv" +
      // fVV());
    }
    return getMolarVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double getPressureRepulsive() {
    double presrep = R * temperature / (getMolarVolume() - getb());
    return presrep;
  }

  /** {@inheritDoc} */
  @Override
  public double getPressureAttractive() {
    double presrep = R * temperature / (getMolarVolume() - getb());
    double presatr = pressure - presrep;
    // presatr = getaT()/((molarVolume+delta1)*(molarVolume+delta2));
    // double prestot = Z*R*temperature/molarVolume;
    return presatr;
  }

  /** {@inheritDoc} */
  @Override
  public java.lang.String getMixingRuleName() {
    return mixRule.getMixingRuleName();
  }

  /** {@inheritDoc} */
  @Override
  public double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    loc_A = mixRule.calcA(phase, temperature, pressure, numbcomp);
    return loc_A;
  }

  /** {@inheritDoc} */
  @Override
  public double calcB(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    loc_B = mixRule.calcB(phase, temperature, pressure, numbcomp);
    return loc_B;
  }

  /** {@inheritDoc} */
  @Override
  public double calcAi(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    return mixRule.calcAi(compNumb, phase, temperature, pressure, numbcomp);
  }

  /**
   * <p>
   * calcAT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcAT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    loc_AT = mixRule.calcAT(phase, temperature, pressure, numbcomp);
    return loc_AT;
  }

  /**
   * <p>
   * calcATT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcATT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    loc_ATT = mixRule.calcATT(phase, temperature, pressure, numbcomp);
    return loc_ATT;
  }

  /** {@inheritDoc} */
  @Override
  public double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    return mixRule.calcAiT(compNumb, phase, temperature, pressure, numbcomp);
  }

  /** {@inheritDoc} */
  @Override
  public double calcAij(int compNumb, int j, PhaseInterface phase, double temperature,
      double pressure, int numbcomp) {
    return mixRule.calcAij(compNumb, j, phase, temperature, pressure, numbcomp);
  }

  /** {@inheritDoc} */
  @Override
  public double calcBij(int compNumb, int j, PhaseInterface phase, double temperature,
      double pressure, int numbcomp) {
    return mixRule.calcBij(compNumb, j, phase, temperature, pressure, numbcomp);
  }

  /** {@inheritDoc} */
  @Override
  public double calcBi(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    return mixRule.calcBi(compNumb, phase, temperature, pressure, numbcomp);
  }

  /** {@inheritDoc} */
  @Override
  public double geta(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    return calcA(phase, temperature, pressure, numbcomp) / numberOfMolesInPhase
        / numberOfMolesInPhase;
  }

  /**
   * Get a.
   *
   * @return double
   */
  double geta() {
    return loc_A / numberOfMolesInPhase / numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getA() {
    return loc_A;
  }

  /**
   * getb.
   *
   * @return double
   */
  double getb() {
    return loc_B / numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getb(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    return calcB(phase, temperature, pressure, numbcomp) / numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getB() {
    return loc_B;
  }

  /** {@inheritDoc} */
  @Override
  public double getAT() {
    return loc_AT;
  }

  /** {@inheritDoc} */
  @Override
  public double getATT() {
    return loc_ATT;
  }

  /** {@inheritDoc} */
  @Override
  public double getAresTV() {
    return getF() * R * temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double getGresTP() {
    return getAresTV() + pressure * numberOfMolesInPhase * getMolarVolume()
        - numberOfMolesInPhase * R * temperature * (1.0 + Math.log(Z));
  }

  /** {@inheritDoc} */
  @Override
  public double getSresTV() {
    return (-temperature * dFdT() - getF()) * R;
  }

  /** {@inheritDoc} */
  @Override
  public double getSresTP() {
    return getSresTV() + numberOfMolesInPhase * R * Math.log(Z);
  }

  /** {@inheritDoc} */
  @Override
  public double getHresTP() {
    return getAresTV() + temperature * getSresTV()
        + pressure * numberOfMolesInPhase * getMolarVolume()
        - numberOfMolesInPhase * R * temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double getHresdP() {
    return getVolume() + temperature * getdPdTVn() / getdPdVTn();
  }

  /** {@inheritDoc} */
  @Override
  public double getCvres() {
    return (-temperature * temperature * dFdTdT() - 2.0 * temperature * dFdT()) * R;
  }

  /** {@inheritDoc} */
  @Override
  public double getCpres() {
    return getCvres()
        + R * (-temperature / R * Math.pow(getdPdTVn(), 2.0) / getdPdVTn() - numberOfMolesInPhase);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * method to return real gas isentropic exponent (kappa = - Cp/Cv*(v/p)*dp/dv
   * </p>
   */
  @Override
  public double getKappa() {
    return -getCp() / getCv() * getVolume() / pressure * getdPdVTn();
  }

  /** {@inheritDoc} */
  @Override
  public double getJouleThomsonCoefficient() {
    return -1.0 / getCp()
        * (getMolarVolume() * numberOfMolesInPhase + temperature * getdPdTVn() / getdPdVTn());
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdTVn() {
    return -R * temperature * dFdTdV() + pressure / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdVTn() {
    return -R * temperature * dFdVdV() - numberOfMolesInPhase * R * temperature
        / Math.pow(numberOfMolesInPhase * getMolarVolume(), 2.0);
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdrho() {
    return getdPdVTn() * getdVdrho() * 1e5;
  }

  /** {@inheritDoc} */
  @Override
  public double getdrhodP() {
    return 1.0 / getdPdrho();
  }

  /** {@inheritDoc} */
  @Override
  public double getdrhodT() {
    return -getdPdTVn() / getdPdrho();
  }

  /** {@inheritDoc} */
  @Override
  public double getdrhodN() {
    return this.getMolarMass();
  }

  /**
   * <p>
   * getdVdrho.
   * </p>
   *
   * @return a double
   */
  public double getdVdrho() {
    return -1.0 * numberOfMolesInPhase * this.getMolarMass() / Math.pow(this.getDensity(), 2.0);
  }

  /** {@inheritDoc} */
  @Override
  public double getg() {
    return g;
  }

  /**
   * <p>
   * Getter for the field <code>f_loc</code>.
   * </p>
   *
   * @return a double
   */
  public double getf_loc() {
    return f_loc;
  }

  /**
   * <p>
   * calcg.
   * </p>
   *
   * @return a double
   */
  public double calcg() {
    return Math.log(1.0 - getb() / molarVolume);
  }

  /**
   * <p>
   * calcf.
   * </p>
   *
   * @return a double
   */
  public double calcf() {
    return (1.0 / (R * loc_B * (delta1 - delta2)) * Math
        .log((1.0 + delta1 * getb() / molarVolume) / (1.0 + delta2 * getb() / (molarVolume))));
  }

  /**
   * <p>
   * getF.
   * </p>
   *
   * @return a double
   */
  public double getF() {
    return -numberOfMolesInPhase * getg() - getA() / temperature * getf_loc();
  }

  /** {@inheritDoc} */
  @Override
  public double F() {
    return getF();
  }

  /** {@inheritDoc} */
  @Override
  public double Fn() {
    return -getg();
  }

  /** {@inheritDoc} */
  @Override
  public double FT() {
    return getA() * getf_loc() / (temperature * temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double FV() {
    return -numberOfMolesInPhase * gV() - getA() / temperature * fv();
  }

  /** {@inheritDoc} */
  @Override
  public double FD() {
    return -getf_loc() / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double FB() {
    return -numberOfMolesInPhase * gb() - getA() / temperature * fb();
  }

  /** {@inheritDoc} */
  @Override
  public double gb() {
    return -1.0 / (numberOfMolesInPhase * molarVolume - loc_B);
  }

  /** {@inheritDoc} */
  @Override
  public double fb() {
    return -(f_loc + numberOfMolesInPhase * molarVolume * fv()) / loc_B;
  }

  /** {@inheritDoc} */
  @Override
  public double gV() {
    return getb() / (molarVolume * (numberOfMolesInPhase * molarVolume - loc_B));
    // 1/(numberOfMolesInPhase*getMolarVolume()-getB())-1/(numberOfMolesInPhase*getMolarVolume());
  }

  /** {@inheritDoc} */
  @Override
  public double fv() {
    return -1.0 / (R * (numberOfMolesInPhase * molarVolume + delta1 * loc_B)
        * (numberOfMolesInPhase * molarVolume + delta2 * loc_B));
  }

  // NYE metoder fredag 25.08.public double dFdN(PhaseInterface phase, int
  // numberOfComponents, double temperature, double pressure, int phasetype){
  /** {@inheritDoc} */
  @Override
  public double FnV() {
    return -gV();
  }

  /** {@inheritDoc} */
  @Override
  public double FnB() {
    return -gb();
  }

  /** {@inheritDoc} */
  @Override
  public double FTT() {
    return -2.0 / temperature * FT();
  }

  /** {@inheritDoc} */
  @Override
  public double FBT() {
    return getA() * fb() / temperature / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double FDT() {
    return getf_loc() / temperature / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double FBV() {
    return -numberOfMolesInPhase * gBV() - getA() * fBV() / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double FBB() {
    return -numberOfMolesInPhase * gBB() - getA() * fBB() / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double FDV() {
    return -fv() / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double FBD() {
    return -fb() / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double FTV() {
    return getA() * fv() / temperature / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double FVV() {
    return -numberOfMolesInPhase * gVV() - getA() * fVV() / temperature;
  }

  /**
   * <p>
   * FVVV.
   * </p>
   *
   * @return a double
   */
  public double FVVV() {
    return -numberOfMolesInPhase * gVVV() - getA() * fVVV() / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double gVV() {
    double val1 = numberOfMolesInPhase * getMolarVolume();
    double val2 = val1 - getB();
    return -1.0 / (val2 * val2) + 1.0 / (val1 * val1);
  }

  /**
   * <p>
   * gVVV.
   * </p>
   *
   * @return a double
   */
  public double gVVV() {
    double val1 = numberOfMolesInPhase * getMolarVolume();
    double val2 = val1 - getB();
    return 2.0 / (val2 * val2 * val2) - 2.0 / (val1 * val1 * val1);
  }

  /** {@inheritDoc} */
  @Override
  public double gBV() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB();
    return 1.0 / (val * val);
  }

  /** {@inheritDoc} */
  @Override
  public double gBB() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB();
    return -1.0 / (val * val);
  }

  /** {@inheritDoc} */
  @Override
  public double fVV() {
    double val1 = (numberOfMolesInPhase * molarVolume + delta1 * loc_B);
    double val2 = (numberOfMolesInPhase * molarVolume + delta2 * loc_B);
    return 1.0 / (R * loc_B * (delta1 - delta2)) * (-1.0 / (val1 * val1) + 1.0 / (val2 * val2));
  }

  /**
   * <p>
   * fVVV.
   * </p>
   *
   * @return a double
   */
  public double fVVV() {
    double val1 = numberOfMolesInPhase * molarVolume + getB() * delta1;
    double val2 = numberOfMolesInPhase * molarVolume + getB() * delta2;
    return 1.0 / (R * getB() * (delta1 - delta2))
        * (2.0 / (val1 * val1 * val1) - 2.0 / (val2 * val2 * val2));
  }

  /** {@inheritDoc} */
  @Override
  public double fBV() {
    return -(2.0 * fv() + numberOfMolesInPhase * molarVolume * fVV()) / getB();
  }

  /** {@inheritDoc} */
  @Override
  public double fBB() {
    return -(2.0 * fb() + numberOfMolesInPhase * molarVolume * fBV()) / getB();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdT() {
    return FT() + FD() * getAT();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
    return FV();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdV() {
    return FTV() + FDV() * getAT();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    return FVV();
  }

  /**
   * <p>
   * dFdVdVdV.
   * </p>
   *
   * @return a double
   */
  public double dFdVdVdV() {
    return FVVV();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdT() {
    return FTT() + 2.0 * FDT() * getAT() + FD() * getATT();
  }

  /** {@inheritDoc} */
  @Override
  public double calcPressure() {
    return -R * temperature * dFdV()
        + getNumberOfMolesInPhase() * R * temperature / getTotalVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double calcPressuredV() {
    return -R * temperature * dFdVdV()
        - getNumberOfMolesInPhase() * R * temperature / Math.pow(getTotalVolume(), 2.0);
  }

  /** {@inheritDoc} */
  @Override
  public double getSoundSpeed() {
    double bs = -1.0 / getVolume() * getCv() / getCp() / getdPdVTn();
    double Mw = getNumberOfMolesInPhase() * getMolarMass();
    return Math.sqrt(getVolume() / Mw / bs);
  }

  /**
   * <p>
   * getdUdSVn.
   * </p>
   *
   * @return a double
   */
  public double getdUdSVn() {
    return getTemperature();
  }

  /**
   * <p>
   * getdUdVSn.
   * </p>
   *
   * @return a double
   */
  public double getdUdVSn() {
    return -getPressure();
  }

  /**
   * <p>
   * getdUdSdSVn.
   * </p>
   *
   * @return a double
   */
  public double getdUdSdSVn() {
    return 1.0 / (FTT() * R * getTemperature()); // noe feil her
  }

  /**
   * <p>
   * getdUdVdVSn.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getdUdVdVSn(PhaseInterface phase) {
    return -FVV() * 1.0 / FTT();
  }

  /**
   * <p>
   * getdUdSdVn.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getdUdSdVn(PhaseInterface phase) {
    return -1.0 / FTT() * FTV();
  }

  // getdTVndSVn() needs to be implemented
  /**
   * <p>
   * getdTVndSVnJaobiMatrix.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[][] getdTVndSVnJaobiMatrix() {
    double[][] jacobiMatrix = new double[2 + numberOfComponents][2 + numberOfComponents];

    jacobiMatrix[0][0] = FTT();
    jacobiMatrix[1][0] = FTT();
    jacobiMatrix[2][0] = FTT();

    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        jacobiMatrix[2][0] = FTT();
      }
    }

    return jacobiMatrix;
  }

  /**
   * <p>
   * getGradientVector.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] getGradientVector() {
    double[] gradientVector = new double[2 + numberOfComponents];
    return gradientVector;
  }

  // getdTVndSVn() needs to be implemented
  // symetrisk matrise
  /**
   * <p>
   * getUSVHessianMatrix.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[][] getUSVHessianMatrix() {
    double[][] jacobiMatrix = new double[2 + numberOfComponents][2 + numberOfComponents];

    jacobiMatrix[0][0] = FTT();
    jacobiMatrix[1][0] = FTT();
    jacobiMatrix[2][0] = FTT();

    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        jacobiMatrix[2][0] = FTT();
      }
    }

    return jacobiMatrix;
  }

  /**
   * <p>
   * dFdxMatrixSimple.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] dFdxMatrixSimple() {
    double[] matrix = new double[numberOfComponents + 2];
    double Fn = Fn();
    double FB = FB();
    double FD = FD();
    double[] Bi = new double[numberOfComponents];
    double[] Ai = new double[numberOfComponents];
    ComponentEosInterface[] componentArray = (ComponentEosInterface[]) this.componentArray;

    for (int i = 0; i < numberOfComponents; i++) {
      Bi[i] = componentArray[i].getBi();
      Ai[i] = componentArray[i].getAi();
    }

    for (int i = 0; i < numberOfComponents; i++) {
      matrix[i] = Fn + FB * Bi[i] + FD * Ai[i];
    }

    matrix[numberOfComponents] = dFdT();
    matrix[numberOfComponents + 1] = dFdV();

    return matrix;
  }

  /**
   * <p>
   * dFdxMatrix.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] dFdxMatrix() {
    double[] matrix = new double[numberOfComponents + 2];

    matrix[0] = dFdT();
    matrix[1] = dFdV();

    for (int i = 0; i < numberOfComponents; i++) {
      matrix[i + 2] = dFdN(i);
    }
    return matrix;
  }

  /**
   * <p>
   * dFdxdxMatrixSimple.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[][] dFdxdxMatrixSimple() {
    double[][] matrix = new double[numberOfComponents + 2][numberOfComponents + 2];

    double FDV = FDV();
    double FBV = FBV();
    double FnV = FnV();
    double FnB = FnB();
    double FBD = FBD();
    double FB = FB();
    double FBB = FBB();
    double FD = FD();
    double FBT = FBT();
    double AT = getAT();
    double FDT = FDT();
    ComponentEosInterface[] componentArray = (ComponentEosInterface[]) this.componentArray;

    double[] Bi = new double[numberOfComponents];
    double[] Ai = new double[numberOfComponents];
    for (int i = 0; i < numberOfComponents; i++) {
      Bi[i] = componentArray[i].getBi();
      Ai[i] = componentArray[i].getAi();
    }

    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = i; j < numberOfComponents; j++) {
        matrix[i][j] = FnB * (Bi[i] + Bi[j]) + FBD * (Bi[i] * Ai[j] + Bi[j] * Ai[i])
            + FB * componentArray[i].getBij(j) + FBB * Bi[i] * Bi[j]
            + FD * componentArray[i].getAij(j);
        matrix[j][i] = matrix[i][j];
      }
    }

    for (int i = 0; i < numberOfComponents; i++) {
      matrix[i][numberOfComponents] =
          (FBT + FBD * AT) * Bi[i] + FDT * Ai[i] + FD * componentArray[i].getAiT(); // dFdndT
      matrix[numberOfComponents][i] = matrix[i][numberOfComponents];

      matrix[i][numberOfComponents + 1] = FnV + FBV * Bi[i] + FDV * Ai[i]; // dFdndV
      matrix[numberOfComponents + 1][i] = matrix[i][numberOfComponents + 1];
    }
    return matrix;
  }

  /**
   * <p>
   * dFdxdxMatrix.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[][] dFdxdxMatrix() {
    double[][] matrix = new double[numberOfComponents + 2][numberOfComponents + 2];

    matrix[0][0] = dFdTdT();
    matrix[1][0] = dFdTdV();
    matrix[0][1] = matrix[1][0];
    matrix[1][1] = dFdVdV();

    for (int i = 0; i < numberOfComponents; i++) {
      matrix[i + 2][0] = dFdNdT(i);
      matrix[0][i + 2] = matrix[i + 2][0];
    }

    for (int i = 0; i < numberOfComponents; i++) {
      matrix[i + 2][1] = dFdNdV(i);
      matrix[1][i + 2] = matrix[i + 2][1];
    }

    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = i; j < numberOfComponents; j++) {
        matrix[i + 2][j + 2] = dFdNdN(i, j);
        matrix[j + 2][i + 2] = matrix[i + 2][j + 2];
      }
    }
    return matrix;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(int i) {
    return ((ComponentEosInterface) getComponent(i)).dFdN(this, this.getNumberOfComponents(),
        temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int i, int j) {
    return ((ComponentEosInterface) getComponent(i)).dFdNdN(j, this, this.getNumberOfComponents(),
        temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(int i) {
    return ((ComponentEosInterface) getComponent(i)).dFdNdV(this, this.getNumberOfComponents(),
        temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(int i) {
    return ((ComponentEosInterface) getComponent(i)).dFdNdT(this, this.getNumberOfComponents(),
        temperature, pressure);
  }
}
