package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentBWRS;

/**
 * <p>
 * PhaseBWRSEos class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseBWRSEos extends PhaseSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseBWRSEos.class);

  int OP = 9;
  int OE = 6;

  /**
   * <p>
   * Constructor for PhaseBWRSEos.
   * </p>
   */
  public PhaseBWRSEos() {}

  /** {@inheritDoc} */
  @Override
  public PhaseBWRSEos clone() {
    PhaseBWRSEos clonedPhase = null;
    try {
      clonedPhase = (PhaseBWRSEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentBWRS(name, moles, molesInPhase, compNumber);
    ((ComponentBWRS) componentArray[compNumber]).setRefPhaseBWRS(this);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    double oldMolDens = 0;
    if (initType == 0) {
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
      super.init(totalNumberOfMoles, numberOfComponents, 3, pt, beta);
      return;
    }
    do {
      oldMolDens = getMolarDensity();
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    } while (Math.abs((getMolarDensity() - oldMolDens) / oldMolDens) > 1e-10);
    getF();
    // calcPVT();
  }

  /**
   * <p>
   * getMolarDensity.
   * </p>
   *
   * @return a double
   */
  public double getMolarDensity() {
    return getNumberOfMolesInPhase() / getTotalVolume() * 1.0e2;
  }

  /**
   * <p>
   * getdRhodV.
   * </p>
   *
   * @return a double
   */
  public double getdRhodV() {
    return -getMolarDensity() / (getTotalVolume() * 1e-5);
  }

  /**
   * <p>
   * getdRhodVdV.
   * </p>
   *
   * @return a double
   */
  public double getdRhodVdV() {
    return 2.0 * getMolarDensity() / (Math.pow(getTotalVolume() * 1e-5, 2));
  }

  /**
   * <p>
   * getdRhodVdVdV.
   * </p>
   *
   * @return a double
   */
  public double getdRhodVdVdV() {
    return -6.0 * getMolarDensity() / (Math.pow(getTotalVolume() * 1e-5, 3));
  }

  /**
   * <p>
   * getGammadRho.
   * </p>
   *
   * @return a double
   */
  public double getGammadRho() {
    return 0.0; // -2.0/Math.pow(((ComponentBWRS)componentArray[0]).getRhoc(),3.0);
                // //-1.0/(rhoc*rhoc);
  }

  /**
   * <p>
   * getFpol.
   * </p>
   *
   * @return a double
   */
  public double getFpol() {
    double temp = 0.0;
    for (int i = 1; i < OP; i++) {
      temp +=
          ((ComponentBWRS) componentArray[0]).getBP(i) / (i + 0.0) * Math.pow(getMolarDensity(), i);
    }
    return numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * <p>
   * getFpoldV.
   * </p>
   *
   * @return a double
   */
  public double getFpoldV() {
    double temp = 0.0;
    for (int i = 1; i < OP; i++) {
      temp += (i) * ((ComponentBWRS) componentArray[0]).getBP(i) / (i - 0.0)
          * Math.pow(getMolarDensity(), i - 1.0) * getdRhodV();
    }
    return numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * Derivative of the polynomial F-term with respect to molar density.
   *
   * @return dF<sub>pol</sub>/dρ
   */
  public double getFpoldRho() {
    double temp = 0.0;
    for (int i = 1; i < OP; i++) {
      temp += ((ComponentBWRS) componentArray[0]).getBP(i) * Math.pow(getMolarDensity(), i - 1.0);
    }
    return numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * <p>
   * getFpoldVdV.
   * </p>
   *
   * @return a double
   */
  public double getFpoldVdV() {
    double temp = 0.0;
    double temp2 = 0.0;
    for (int i = 1; i < OP; i++) {
      temp += (i - 1) * (i) * ((ComponentBWRS) componentArray[0]).getBP(i) / (i - 0.0)
          * Math.pow(getMolarDensity(), i - 2.0);
      temp2 += (i) * ((ComponentBWRS) componentArray[0]).getBP(i) / (i - 0.0)
          * Math.pow(getMolarDensity(), i - 1.0);
    }
    return numberOfMolesInPhase / (R * temperature) * temp * Math.pow(getdRhodV(), 2)
        + numberOfMolesInPhase / (R * temperature) * temp2 * getdRhodVdV();
  }

  /**
   * <p>
   * getFpoldVdVdV.
   * </p>
   *
   * @return a double
   */
  public double getFpoldVdVdV() {
    double temp = 0.0;
    double temp2 = 0.0;
    // double temp3 = 0.0, temp4 = 0.0;
    for (int i = 1; i < OP; i++) {
      temp += (i - 2) * (i - 1) * (i) * ((ComponentBWRS) componentArray[0]).getBP(i) / (i - 0.0)
          * Math.pow(getMolarDensity(), i - 3);
      temp2 += (i - 1) * (i) * ((ComponentBWRS) componentArray[0]).getBP(i) / (i - 0.0)
          * Math.pow(getMolarDensity(), i - 2);
      // temp3 += (i) * ((ComponentBWRS) componentArray[0]).getBP(i) / (i - 0.0) *
      // Math.pow(getMolarDensity(), i - 1);
    }
    return numberOfMolesInPhase / (R * temperature) * temp * Math.pow(getdRhodV(), 3)
        + 2 * numberOfMolesInPhase / (R * temperature) * temp2 * Math.pow(getdRhodV(), 1)
            * getdRhodVdV()
        + numberOfMolesInPhase / (R * temperature) * temp2 * Math.pow(getdRhodV(), 1)
            * getdRhodVdV()
        + numberOfMolesInPhase / (R * temperature) * temp2 * getdRhodVdVdV();
  }

  /**
   * <p>
   * getFpoldT.
   * </p>
   *
   * @return a double
   */
  public double getFpoldT() {
    double temp = 0.0;
    double temp2 = 0.0;
    for (int i = 1; i < OP; i++) {
      temp +=
          ((ComponentBWRS) componentArray[0]).getBP(i) / (i + 0.0) * Math.pow(getMolarDensity(), i);
      temp2 += ((ComponentBWRS) componentArray[0]).getBPdT(i) / (i + 0.0)
          * Math.pow(getMolarDensity(), i);
    }
    return -numberOfMolesInPhase / (R * temperature * temperature) * temp
        + numberOfMolesInPhase / (R * temperature) * temp2;
  }

  /**
   * Total derivative of F with respect to molar density.
   *
   * @return dF/dρ
   */
  public double getFdRho() {
    return getFpoldRho() + getFexpdRho();
  }

  /**
   * <p>
   * getEL.
   * </p>
   *
   * @return a double
   */
  public double getEL() {
    return Math.exp(
        -((ComponentBWRS) componentArray[0]).getGammaBWRS() * Math.pow(getMolarDensity(), 2.0));
  }

  /**
   * <p>
   * getELdRho.
   * </p>
   *
   * @return a double
   */
  public double getELdRho() {
    return -2.0 * getMolarDensity() * ((ComponentBWRS) componentArray[0]).getGammaBWRS() * Math.exp(
        -((ComponentBWRS) componentArray[0]).getGammaBWRS() * Math.pow(getMolarDensity(), 2.0));
  }

  /**
   * Second derivative of the exponential EL term with respect to molar density.
   *
   * @return d<sup>2</sup>EL/dρ<sup>2</sup>
   */
  public double getELdRhodedRho() {
    double gamma = ((ComponentBWRS) componentArray[0]).getGammaBWRS();
    double rho = getMolarDensity();
    return (-2.0 * gamma + 4.0 * gamma * gamma * rho * rho) * getEL();
  }

  /**
   * Third derivative of the exponential EL term with respect to molar density.
   *
   * @return d<sup>3</sup>EL/dρ<sup>3</sup>
   */
  public double getELdRhodedRhodedRho() {
    double gamma = ((ComponentBWRS) componentArray[0]).getGammaBWRS();
    double rho = getMolarDensity();
    return (12.0 * gamma * gamma * rho - 8.0 * Math.pow(gamma, 3.0) * Math.pow(rho, 3.0)) * getEL();
  }

  /**
   * <p>
   * getFexp.
   * </p>
   *
   * @return a double
   */
  public double getFexp() {
    double oldTemp = 0.0;
    double temp = 0.0;
    oldTemp = -((ComponentBWRS) componentArray[0]).getBE(0)
        / (2.0 * ((ComponentBWRS) componentArray[0]).getGammaBWRS()) * (getEL() - 1.0);
    temp += oldTemp;
    for (int i = 1; i < OE; i++) {
      oldTemp = -((ComponentBWRS) componentArray[0]).getBE(i)
          / (2.0 * ((ComponentBWRS) componentArray[0]).getGammaBWRS())
          * (getEL() * Math.pow(getMolarDensity(), 2.0 * i)
              - (2.0 * i) / ((ComponentBWRS) componentArray[0]).getBE(i - 1) * oldTemp);
      temp += oldTemp;
    }
    return numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * First derivative of Fexp with respect to molar density.
   *
   * @return dFexp/dρ
   */
  private double getFexpdRho() {
    double gamma = ((ComponentBWRS) componentArray[0]).getGammaBWRS();
    double rho = getMolarDensity();
    double oldTemp = -((ComponentBWRS) componentArray[0]).getBE(0) / (2.0 * gamma) * getELdRho();
    double temp = oldTemp;
    for (int i = 1; i < OE; i++) {
      double prev = oldTemp;
      oldTemp = -((ComponentBWRS) componentArray[0]).getBE(i) / (2.0 * gamma)
          * (getELdRho() * Math.pow(rho, 2 * i)
              + getEL() * (2.0 * i) * Math.pow(rho, 2 * i - 1)
              - (2.0 * i) / ((ComponentBWRS) componentArray[0]).getBE(i - 1) * prev);
      temp += oldTemp;
    }
    return numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * Second derivative of Fexp with respect to molar density.
   *
   * @return d<sup>2</sup>Fexp/dρ<sup>2</sup>
   */
  private double getFexpdRhodRho() {
    double gamma = ((ComponentBWRS) componentArray[0]).getGammaBWRS();
    double rho = getMolarDensity();
    double oldTemp = -((ComponentBWRS) componentArray[0]).getBE(0) / (2.0 * gamma) * getELdRhodedRho();
    double temp = oldTemp;
    for (int i = 1; i < OE; i++) {
      double prev = oldTemp;
      oldTemp = -((ComponentBWRS) componentArray[0]).getBE(i) / (2.0 * gamma)
          * (getELdRhodedRho() * Math.pow(rho, 2 * i)
              + 2.0 * getELdRho() * (2.0 * i) * Math.pow(rho, 2 * i - 1)
              + getEL() * (2.0 * i) * (2.0 * i - 1) * Math.pow(rho, 2 * i - 2)
              - (2.0 * i) / ((ComponentBWRS) componentArray[0]).getBE(i - 1) * prev);
      temp += oldTemp;
    }
    return numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * Third derivative of Fexp with respect to molar density.
   *
   * @return d<sup>3</sup>Fexp/dρ<sup>3</sup>
   */
  private double getFexpdRhodRhodRho() {
    double gamma = ((ComponentBWRS) componentArray[0]).getGammaBWRS();
    double rho = getMolarDensity();
    double oldTemp = -((ComponentBWRS) componentArray[0]).getBE(0) / (2.0 * gamma)
        * getELdRhodedRhodedRho();
    double temp = oldTemp;
    for (int i = 1; i < OE; i++) {
      double prev = oldTemp;
      oldTemp = -((ComponentBWRS) componentArray[0]).getBE(i) / (2.0 * gamma)
          * (getELdRhodedRhodedRho() * Math.pow(rho, 2 * i)
              + 3.0 * getELdRhodedRho() * (2.0 * i) * Math.pow(rho, 2 * i - 1)
              + 3.0 * getELdRho() * (2.0 * i) * (2.0 * i - 1) * Math.pow(rho, 2 * i - 2)
              + getEL() * (2.0 * i) * (2.0 * i - 1) * (2.0 * i - 2)
                  * Math.pow(rho, 2 * i - 3)
              - (2.0 * i) / ((ComponentBWRS) componentArray[0]).getBE(i - 1) * prev);
      temp += oldTemp;
    }
    return numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * First derivative of Fexp with respect to molar volume.
   *
   * @return dFexp/dV
   */
  public double getFexpdV() {
    return getFexpdRho() * getdRhodV();
  }

  /**
   * Second derivative of Fexp with respect to molar volume.
   *
   * @return d<sup>2</sup>Fexp/dV<sup>2</sup>
   */
  public double getFexpdVdV() {
    return getFexpdRhodRho() * Math.pow(getdRhodV(), 2.0)
        + getFexpdRho() * getdRhodVdV();
  }

  /**
   * Third derivative of Fexp with respect to molar volume.
   *
   * @return d<sup>3</sup>Fexp/dV<sup>3</sup>
   */
  public double getFexpdVdVdV() {
    return getFexpdRhodRhodRho() * Math.pow(getdRhodV(), 3.0)
        + 3.0 * getFexpdRhodRho() * getdRhodV() * getdRhodVdV()
        + getFexpdRho() * getdRhodVdVdV();
  }

  /**
   * <p>
   * getFexpdT.
   * </p>
   *
   * @return a double
   */
  public double getFexpdT() {
    double oldTemp = 0.0;
    double temp = 0.0;
    double oldTemp2 = 0;
    oldTemp = -((ComponentBWRS) componentArray[0]).getBEdT(0)
        / (2.0 * ((ComponentBWRS) componentArray[0]).getGammaBWRS()) * (getEL() - 1.0);
    oldTemp2 = -((ComponentBWRS) componentArray[0]).getBE(0)
        / (2.0 * ((ComponentBWRS) componentArray[0]).getGammaBWRS()) * (getEL() - 1.0);
    temp += oldTemp;
    for (int i = 1; i < OE; i++) {
      oldTemp = -((ComponentBWRS) componentArray[0]).getBEdT(i)
          / (2.0 * ((ComponentBWRS) componentArray[0]).getGammaBWRS())
          * (getEL() * Math.pow(getMolarDensity(), 2.0 * i)
              - (2.0 * i) / ((ComponentBWRS) componentArray[0]).getBE(i - 1) * oldTemp2)

          +

          -((ComponentBWRS) componentArray[0]).getBE(i)
              / (2.0 * ((ComponentBWRS) componentArray[0]).getGammaBWRS())
              * ((2.0 * i) / Math.pow(((ComponentBWRS) componentArray[0]).getBE(i - 1), 2.0)
                  * oldTemp2)
              * ((ComponentBWRS) componentArray[0]).getBEdT(i - 1)

          +

          ((ComponentBWRS) componentArray[0]).getBE(i)
              / (2.0 * ((ComponentBWRS) componentArray[0]).getGammaBWRS())
              * ((2.0 * i) / ((ComponentBWRS) componentArray[0]).getBE(i - 1) * oldTemp);

      oldTemp2 = -((ComponentBWRS) componentArray[0]).getBE(i)
          / (2.0 * ((ComponentBWRS) componentArray[0]).getGammaBWRS())
          * (getEL() * Math.pow(getMolarDensity(), 2.0 * i)
              - (2.0 * i) / ((ComponentBWRS) componentArray[0]).getBE(i - 1) * oldTemp2);

      temp += oldTemp;
    }
    return -getFexp() / temperature + numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * <p>
   * calcPressure2.
   * </p>
   *
   * @return a double
   */
  public double calcPressure2() {
    // System.out.println("here............");
    double temp = 0.0;
    logger.info("molar density " + getMolarDensity());
    for (int i = 0; i < OP; i++) {
      temp += ((ComponentBWRS) componentArray[0]).getBP(i) * Math.pow(getMolarDensity(), 1.0 + i);
    }
    for (int i = 0; i < OE; i++) {
      temp += getEL() * ((ComponentBWRS) componentArray[0]).getBE(i)
          * Math.pow(getMolarDensity(), 3.0 + 2.0 * i);
    }
    calcPVT();
    return temp / 100.0;
  }

  /**
   * <p>
   * calcPVT.
   * </p>
   */
  public void calcPVT() {
    double[] moldens = new double[300];
    double[] pres = new double[300];
    for (int j = 0; j < 300; j++) {
      moldens[j] = 30 - j * 0.1;
      double temp = 0.0;
      for (int i = 0; i < OP; i++) {
        temp += ((ComponentBWRS) componentArray[0]).getBP(i) * Math.pow(moldens[j], 1.0 + i);
      }
      for (int i = 0; i < OE; i++) {
        temp += Math
            .exp(-((ComponentBWRS) componentArray[0]).getGammaBWRS() * Math.pow(moldens[j], 2.0))
            * ((ComponentBWRS) componentArray[0]).getBE(i) * Math.pow(moldens[j], 3.0 + 2.0 * i);
      }
      pres[j] = temp / 100.0;
      logger.info("moldens " + moldens[j] * 16.01 + "  pres " + pres[j]);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getF() {
    // System.out.println("F " + getFpol()*1e3+ " "+ getFexp()*1e3 + " super " +
    // super.getF() + " pt " +getType());
    return (getFpol() + getFexp()) * 1e3;
  }

  /**
   * <p>
   * getdFdN.
   * </p>
   *
   * @return a double
   */
  public double getdFdN() {
    double dn = numberOfMolesInPhase / 100.0;
    getComponent(0).addMoles(dn);
    numberOfMolesInPhase += dn;

    init(numberOfMolesInPhase, numberOfComponents, 3, 1.0);
    double fold = getF();
    numberOfMolesInPhase -= 2 * dn;
    getComponent(0).addMoles(-2 * dn);
    init(numberOfMolesInPhase, numberOfComponents, 3, 1.0);
    double fnew = getF();
    numberOfMolesInPhase += dn;
    getComponent(0).addMoles(dn);
    init(numberOfMolesInPhase, numberOfComponents, 3, 1.0);
    // System.out.println("F " + getFpol()*1e3+ " "+ getFexp()*1e3 + " super " +
    // super.getF() + " pt " +getType());
    return (fold - fnew) / (2 * dn);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdT() {
    // double dv = temperature/1000.0;
    // temperature = temperature + dv;
    // init(numberOfMolesInPhase, numberOfComponents, 3, pt.getValue(), 1.0);
    // double fold = getF();
    // temperature = temperature - 2*dv;
    // init(numberOfMolesInPhase, numberOfComponents, 3, pt.getValue(), 1.0);
    // double fnew = getF();
    // temperature = temperature + dv;
    // init(numberOfMolesInPhase, numberOfComponents, 3, pt.getValue(), 1.0);
    // System.out.println("dFdT " + ((fold-fnew)/(2*dv)) + " super " +
    // (getFpoldT()+getFexpdT())*1e3+ " pt " +getType());
    return (getFpoldT() + getFexpdT()) * 1e3; // (fold-fnew)/(2*dv);

    // // System.out.println("FT " + getFpoldT()*1e3+ " "+ getFexpdT()*1e3 + " super
    // " + super.dFdT() + " pt " +getType());
    // return (getFpoldT()+getFexpdT())*1e3;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdT() {
    double dv = temperature / 1000.0;
    temperature += dv;
    init(numberOfMolesInPhase, numberOfComponents, 3, 1.0);
    double fold = dFdT();
    temperature -= 2 * dv;
    init(numberOfMolesInPhase, numberOfComponents, 3, 1.0);
    double fnew = dFdT();
    temperature += dv;
    init(numberOfMolesInPhase, numberOfComponents, 3, 1.0);
    return (fold - fnew) / (2 * dv);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdV() {
    double dv = getMolarVolume() / 1000.0;
    setMolarVolume(getMolarVolume() + dv);
    double fold = dFdT();
    setMolarVolume(getMolarVolume() - 2 * dv);
    double fnew = dFdT();
    setMolarVolume(getMolarVolume() + dv);
    return (fold - fnew) / (2 * dv);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
    // double dv = molarVolume/1000.0;

    // molarVolume = molarVolume + dv;
    // double fold = getF();
    // molarVolume = molarVolume - 2*dv;
    // double fnew = getF();
    // molarVolume = molarVolume + dv;

    // System.out.println("dFdV " + ((fold-fnew)/(2*dv)) + " super " + super.dFdV()+
    // " pt " +getType());
    // // return (fold-fnew)/(2*dv);
    // System.out.println("dFdV " + ((getFpoldV()+getFexpdV()))*1e3*1e-5 + " super "
    // + super.dFdV()+ " pt " +getType());
    // System.out.println("dFdV " + getFpoldV()+getFexpdV()*1e3*1e-5);
    return (getFpoldV() + getFexpdV()) * 1e3 * 1e-5;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    return (getFpoldVdV() + getFexpdVdV()) * 1e3 * 1e-10;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdVdV() {
    return (getFpoldVdVdV() + getFexpdVdVdV()) * 1e3 * 1e-15;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume2(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double BonV =
        pt == PhaseType.LIQUID ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
            : pressure * getB() / (numberOfMolesInPhase * temperature * R);
    double Btemp = getB();
    setMolarVolume(1.0 / BonV * Btemp); // numberOfMolesInPhase;
    int iterations = 0;
    int maxIterations = 10000;
    double guesPres = pressure;
    double guesPresdV = 0.0;
    do {
      iterations++;
      guesPres = -R * temperature * dFdV() + R * temperature / getMolarVolume();
      guesPresdV = -R * temperature * dFdVdV()
          - getNumberOfMolesInPhase() * R * temperature / Math.pow(getTotalVolume(), 2.0);
      logger.info("gues pres " + guesPres);
      setMolarVolume(getMolarVolume()
          - 1.0 / (guesPresdV * getNumberOfMolesInPhase()) * (guesPres - pressure) / 50.0);
      Z = pressure * getMolarVolume() / (R * temperature);
    } while (Math.abs((guesPres - pressure) / pressure) > 1.0e-10 && iterations < maxIterations);
    // System.out.println("gues pres " + guesPres);
    if (iterations >= maxIterations) {
      throw new neqsim.util.exception.TooManyIterationsException(this, "molarVolume2",
          maxIterations);
    }
    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume2", "Molar Volume");
    }
    // System.out.println("Z: " + Z + " "+" itert: " +iterations);
    // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
    // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
    // + fVV());

    return getMolarVolume();
  }
}
