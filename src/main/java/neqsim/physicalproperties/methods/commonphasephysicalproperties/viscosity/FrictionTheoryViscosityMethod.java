package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePrEos;

/**
 * Implementation of the friction-theory viscosity model of Quiñones-Cisneros and
 * Firoozabadi for mixtures. A Chung correlation provides the pure-component baseline while
 * excess friction is obtained from repulsive and attractive EOS pressures.
 *
 * <p>References:
 * Quiñones-Cisneros, R., & Firoozabadi, A. (2000). Friction theory for the viscosity of fluids.
 * AIChE Journal, 46(4), 16–23.
 * </p>
 *
 * @author esol
 * @version Method was checked on 2.8.2001 - seems to be correct - Even Solbraa
 */
public class FrictionTheoryViscosityMethod extends Viscosity
    implements neqsim.thermo.ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(FrictionTheoryViscosityMethod.class);

  public double[] pureComponentViscosity;
  public double[] Fc;
  public double[] omegaVisc;
  protected double[] chungE = new double[10];
  static double TBPcorrection = 1.0;
  private static final double CHUNG_A = 1.16145;
  private static final double CHUNG_B = 0.14874;
  private static final double CHUNG_C = 0.52487;
  private static final double CHUNG_D = 0.77320;
  private static final double CHUNG_E = 2.16178;
  private static final double CHUNG_F = 2.43787;
  // SRK constants
  // Base attractive, repulsive and cross constants
  protected double kapac_fconst = -0.165302;
  protected double kaprc_fconst = 6.99574e-3;
  protected double kaprrc_fconst = 1.26358e-3;

  // Temperature dependent polynomials for attractive and repulsive contributions
  protected double[][] kapa_fconst = {{-0.114804, 0.246622, -3.94638e-2},
      {0.246622, -1.15648e-4, 4.18863e-5}, {-3.94638e-2, 4.18863e-5, -5.91999e-9}};

  protected double[][] kapr_fconst = {{-0.315903, 0.566713, -7.29995e-2},
      {0.566713, -1.0086e-4, 5.17459e-5}, {-7.29995e-2, 5.17459e-5, -5.68708e-9}};

  // Second-order repulsive term constant
  protected double kaprr_fconst = 1.35994e-8;

  /**
   * <p>
   * Constructor for FrictionTheoryViscosityMethod.
   * </p>
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public FrictionTheoryViscosityMethod(PhysicalProperties phase) {
    super(phase);
    pureComponentViscosity = new double[phase.getPhase().getNumberOfComponents()];
    Fc = new double[phase.getPhase().getNumberOfComponents()];
    omegaVisc = new double[phase.getPhase().getNumberOfComponents()];

    setEosDependentConstants();
  }

  /**
   * Select EOS specific friction-theory constants.
   */
  private void setEosDependentConstants() {
    if (phase.getPhase() instanceof PhasePrEos) {
      setPengRobinsonConstants();
    } else {
      setSrkConstants();
    }
  }

  private void setSrkConstants() {
    kapac_fconst = -0.165302;
    kaprc_fconst = 6.99574e-3;
    kaprrc_fconst = 1.26358e-3;
    kapa_fconst = new double[][] {{-0.114804, 0.246622, -3.94638e-2},
        {0.246622, -1.15648e-4, 4.18863e-5}, {-3.94638e-2, 4.18863e-5, -5.91999e-9}};
    kapr_fconst = new double[][] {{-0.315903, 0.566713, -7.29995e-2},
        {0.566713, -1.0086e-4, 5.17459e-5}, {-7.29995e-2, 5.17459e-5, -5.68708e-9}};
    kaprr_fconst = 1.35994e-8;
  }

  private void setPengRobinsonConstants() {
    kapac_fconst = -0.140464;
    kaprc_fconst = 1.19902e-2;
    kaprrc_fconst = 8.55115e-4;
    kapa_fconst = new double[][] {{-4.89197e-2, 0.270572, -4.48111e-2},
        {0.270572, -1.10473e-4, 4.08972e-5}, {-4.48111e-2, 4.08972e-5, -5.79765e-9}};
    kapr_fconst = new double[][] {{-0.357875, 0.637572, -7.9024e-2},
        {0.637572, -6.02128e-5, 3.72408e-5}, {-7.9024e-2, 3.72408e-5, -5.65610e-9}};
    kaprr_fconst = 1.37290e-8;
  }

  /**
   * Allow user supplied friction-theory constants for other EOS models.
   *
   * @param kapac first constant set
   * @param kaprc second constant set
   * @param kaprrc third constant set
   * @param kapa matrix for attractive term
   * @param kapr matrix for repulsive term
   * @param kaprr constant for repulsive-repulsive term
   */
  public void setFrictionTheoryConstants(double kapac, double kaprc, double kaprrc,
      double[][] kapa, double[][] kapr, double kaprr) {
    kapac_fconst = kapac;
    kaprc_fconst = kaprc;
    kaprrc_fconst = kaprrc;
    kapa_fconst = kapa;
    kapr_fconst = kapr;
    kaprr_fconst = kaprr;
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    initChungPureComponentViscosity();

    PhaseInterface localPhase = phase.getPhase();
    int numComp = localPhase.getNumberOfComponents();
    double[] molarMassPow = new double[numComp];
    double visk0 = 0.0;
    double MM = 0.0;
    for (int i = 0; i < numComp; i++) {
      ComponentInterface comp = localPhase.getComponent(i);
      molarMassPow[i] = Math.pow(comp.getMolarMass(), 0.3);
      visk0 += comp.getx() * Math.log(getPureComponentViscosity(i));
      MM += comp.getx() / molarMassPow[i];
    }
    visk0 = Math.exp(visk0);
    double Prepulsive = 1.0;
    double Pattractive = 1.0;
    try {
      Prepulsive = ((neqsim.thermo.phase.PhaseEosInterface) localPhase).getPressureRepulsive();
      Pattractive = ((neqsim.thermo.phase.PhaseEosInterface) localPhase).getPressureAttractive();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    double kaprmx = 0.0;
    double kapamx = 0.0;
    double kaprrmx = 0.0;

    double temperature = localPhase.getTemperature();
    for (int i = 0; i < numComp; i++) {
      ComponentInterface comp = localPhase.getComponent(i);
      double nci = comp.getCriticalViscosity() * comp.getViscosityFrictionK();
      if (comp.isIsPlusFraction() || comp.isIsTBPfraction()) {
        nci *= TBPcorrection;
      }

      double pc = comp.getPC();
      double phi = 1e1 * R * comp.getTC() / pc;
      double bigGam = comp.getTC() / temperature;

      double kapri = getRedKapr(phi, bigGam);
      double kaprri = getRedKaprr(phi, bigGam);
      double kapai = getRedKapa(phi, bigGam);

      double zi = comp.getx() / (molarMassPow[i] * MM);
      kaprmx += zi * nci * kapri / pc;
      kapamx += zi * nci * kapai / pc;
      kaprrmx += zi * nci * kaprri / (pc * pc);
    }
    double visk1 = kaprmx * Prepulsive + kapamx * Pattractive + kaprrmx * Prepulsive * Prepulsive;

    if ((visk0 + visk1) < 1e-20) {
      return 1e-6;
    }
    return visk0 + visk1;
  }

  /**
   * Reduced repulsive constant.
   *
   * @param phi a double
   * @param bigGamma a double
   * @return a double
   */
  public double getRedKapr(double phi, double bigGamma) {
    return kaprc_fconst + kapr_fconst[0][0] * (bigGamma - 1.0)
        + (kapr_fconst[1][0] + kapr_fconst[1][1] * phi) * (Math.exp(bigGamma - 1.0) - 1.0)
        + (kapr_fconst[2][0] + kapr_fconst[2][1] * phi + kapr_fconst[2][2] * phi * phi)
            * (Math.exp(2 * bigGamma - 2.0) - 1.0);
  }

  /**
   * Reduced repulsive-repulsive constant.
   *
   * @param phi a double
   * @param bigGamma a double
   * @return a double
   */
  public double getRedKaprr(double phi, double bigGamma) {
    return kaprrc_fconst
        + kaprr_fconst * phi * (Math.exp(2.0 * bigGamma) - 1.0) * Math.pow(bigGamma - 1.0, 2.0);
  }

  /**
   * Reduced attractive constant.
   *
   * @param phi a double
   * @param bigGamma a double
   * @return a double
   */
  public double getRedKapa(double phi, double bigGamma) {
    return kapac_fconst + kapa_fconst[0][0] * (bigGamma - 1.0)
        + (kapa_fconst[1][0] + kapa_fconst[1][1] * phi) * (Math.exp(bigGamma - 1.0) - 1.0)
        + (kapa_fconst[2][0] + kapa_fconst[2][1] * phi + kapa_fconst[2][2] * phi * phi)
            * (Math.exp(2.0 * bigGamma - 2.0) - 1.0);
  }

  /** {@inheritDoc} */
  @Override
  public double getPureComponentViscosity(int i) {
    return pureComponentViscosity[i];
  }

  /**
   * <p>
   * initChungPureComponentViscosity.
   * </p>
   */
  public void initChungPureComponentViscosity() {
    PhaseInterface localPhase = phase.getPhase();
    double temperature = localPhase.getTemperature();
    for (int i = 0; i < localPhase.getNumberOfComponents(); i++) {
      ComponentInterface comp = localPhase.getComponent(i);
      Fc[i] = 1.0 - 0.2756 * comp.getAcentricFactor();

      double tempVar = 1.2593 * temperature / comp.getTC();
      double varLast = -6.435e-4 * Math.pow(tempVar, 0.14874)
          * Math.sin(18.0323 * Math.pow(tempVar, -0.76830) - 7.27371);

      omegaVisc[i] = CHUNG_A / Math.pow(tempVar, CHUNG_B)
          + CHUNG_C / Math.exp(CHUNG_D * tempVar) + CHUNG_E / Math.exp(CHUNG_F * tempVar)
          + varLast;

      double critVol = comp.getCriticalVolume();
      pureComponentViscosity[i] = 40.785
          * Math.sqrt(comp.getMolarMass() * 1000.0 * temperature)
          / (Math.pow(critVol, 2.0 / 3.0) * omegaVisc[i]) * Fc[i] * 1.0e-7;
      // convert from micropoise to Pa.s
    }
  }

  /** {@inheritDoc} */
  @Override
  public void tuneModel(double val, double temperature, double pressure) {
    double calcVisc = 0;
    int iter = 0;
    double err = 0.0;
    double oldTemp = phase.getPhase().getTemperature();
    double oldPres = phase.getPhase().getPressure();
    phase.getPhase().setTemperature(temperature);
    phase.getPhase().setPressure(pressure);
    phase.getPhase().init();
    do {
      iter++;
      phase.getPhase().initPhysicalProperties();
      calcVisc = calcViscosity();
      logger.info("visc " + calcVisc);
      err = ((calcVisc - val) / calcVisc);
      logger.info("err " + err);

      if (phase.getPhase().hasPlusFraction() || phase.getPhase().hasTBPFraction()) {
        logger.info("has plus fraction ");
        for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
          if (phase.getPhase().getComponent(i).isIsPlusFraction()
              || phase.getPhase().getComponent(i).isIsTBPfraction()) {
            phase.getPhase().getComponent(i).setCriticalViscosity(
                phase.getPhase().getComponent(i).getCriticalViscosity() * (1 - err));
          }
        }
      } else {
        logger.info("no plus fraction ");
        for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
          phase.getPhase().getComponent(i).setCriticalViscosity(
              phase.getPhase().getComponent(i).getCriticalViscosity() * (1 - err));
        }
      }
    } while (Math.abs(err) > 1e-4 && iter < 500);

    phase.getPhase().setTemperature(oldTemp);
    phase.getPhase().setPressure(oldPres);
    phase.getPhase().init();
  }

  /**
   * <p>
   * setTBPviscosityCorrection.
   * </p>
   *
   * @param correction a double
   */
  public void setTBPviscosityCorrection(double correction) {
    TBPcorrection = correction;
  }

  /**
   * <p>
   * getTBPviscosityCorrection.
   * </p>
   *
   * @return a double
   */
  public double getTBPviscosityCorrection() {
    return TBPcorrection;
  }
}
