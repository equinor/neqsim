package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * FrictionTheoryViscosityMethod class.
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
  // SRK
  protected double kapac_fconst = -0.165302;
  protected double kaprc_fconst = 6.99574e-3;
  protected double kaprrc_fconst = 1.26358e-3;

  protected double[][] kapa_fconst = {{-0.114804, 0.246622, -3.94638e-2},
      {0.246622, -1.15648e-4, 4.18863e-5}, {-3.94638e-2, 4.18863e-5, -5.91999e-9}};

  protected double[][] kapr_fconst = {{-0.315903, 0.566713, -7.29995e-2},
      {0.566713, -1.0086e-4, 5.17459e-5}, {-7.29995e-2, 5.17459e-5, -5.68708e-9}};

  protected double kaprr_fconst = 1.35994e-8;

  // PR
  // protected double kapac_fconst = -0.140464;
  // protected double kaprc_fconst = 1.19902e-2;
  // protected double kaprrc_fconst = 8.55115e-4;

  // protected double[][] kapa_fconst = {{-4.89197e-2, 0.270572,-4.48111e-2},
  // {0.270572, -1.10473e-4, 4.08972e-5},
  // {-4.48111e-2,4.08972e-5,-5.79765e-9}};

  // protected double[][] kapr_fconst = {{-0.357875, 0.637572 , -7.9024e-2 },
  // {0.637572 , -6.02128e-5, 3.72408e-5},
  // {-7.9024e-2,3.72408e-5,-5.65610e-9}};

  // protected double kaprr_fconst = 1.37290e-8;

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

    if (this.getClass().getName().equals("neqsim.thermo.phase.PhasePrEos")) {
      kapac_fconst = -0.140464;
      kaprc_fconst = 1.19902e-2;
      kaprrc_fconst = 8.55115e-4;

      double[][] kapa_fconst = {{-4.89197e-2, 0.270572, -4.48111e-2},
          {0.270572, -1.10473e-4, 4.08972e-5}, {-4.48111e-2, 4.08972e-5, -5.79765e-9}};
      this.kapa_fconst = kapa_fconst;

      double[][] kapr_fconst = {{-0.357875, 0.637572, -7.9024e-2},
          {0.637572, -6.02128e-5, 3.72408e-5}, {-7.9024e-2, 3.72408e-5, -5.65610e-9}};
      this.kapr_fconst = kapr_fconst;

      kaprr_fconst = 1.37290e-8;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    initChungPureComponentViscosity();

    double visk0 = 0.0;
    double visk1 = 0.0;
    double MM = 0.0;
    for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
      visk0 += phase.getPhase().getComponent(i).getx() * Math.log(getPureComponentViscosity(i));
      MM += phase.getPhase().getComponent(i).getx()
          / Math.pow(phase.getPhase().getComponent(i).getMolarMass(), 0.3);
    }
    visk0 = Math.exp(visk0);
    double Prepulsive = 1;
    double Pattractive = 1;
    try {
      // frictional term
      Prepulsive =
          ((neqsim.thermo.phase.PhaseEosInterface) phase.getPhase()).getPressureRepulsive();
      Pattractive =
          ((neqsim.thermo.phase.PhaseEosInterface) phase.getPhase()).getPressureAttractive();
      // logger.info("P rep " + Prepulsive);
      // logger.info("P atr " + Pattractive);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    double kaprmx = 0.0;
    double kapamx = 0.0;
    double kaprrmx = 0.0;

    for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
      double nci = phase.getPhase().getComponent(i).getCriticalViscosity() * 1e7;
      if (phase.getPhase().getComponent(i).isIsPlusFraction()
          || phase.getPhase().getComponent(i).isIsTBPfraction()) {
        nci *= TBPcorrection;
      }

      double phi = 1e1 * R * phase.getPhase().getComponent(i).getTC()
          / phase.getPhase().getComponent(i).getPC();
      double bigGam = phase.getPhase().getComponent(i).getTC() / phase.getPhase().getTemperature();

      double kapri = getRedKapr(i, phi, bigGam);
      double kaprri = getRedKaprr(i, phi, bigGam);
      double kapai = getRedKapa(i, phi, bigGam);

      double zi = phase.getPhase().getComponent(i).getx()
          / (Math.pow(phase.getPhase().getComponent(i).getMolarMass(), 0.3) * MM);
      kaprmx += zi * nci * kapri / phase.getPhase().getComponent(i).getPC();
      kapamx += zi * nci * kapai / phase.getPhase().getComponent(i).getPC();
      kaprrmx += zi * nci * kaprri
          / (phase.getPhase().getComponent(i).getPC() * phase.getPhase().getComponent(i).getPC());
    }
    visk1 = kaprmx * Prepulsive + kapamx * Pattractive + kaprrmx * Prepulsive * Prepulsive;

    if ((visk0 + visk1) < 1e-20) {
      return 1e-6;
    }
    return (visk0 + visk1) * 1.0e-7;
  }

  /**
   * <p>
   * getRedKapr.
   * </p>
   *
   * @param compNumb a int
   * @param phi a double
   * @param bigGamma a double
   * @return a double
   */
  public double getRedKapr(int compNumb, double phi, double bigGamma) {
    return kaprc_fconst + kapr_fconst[0][0] * (bigGamma - 1.0)
        + (kapr_fconst[1][0] + kapr_fconst[1][1] * phi) * (Math.exp(bigGamma - 1.0) - 1.0)
        + (kapr_fconst[2][0] + kapr_fconst[2][1] * phi + kapr_fconst[2][2] * phi * phi)
            * (Math.exp(2 * bigGamma - 2.0) - 1.0);
  }

  /**
   * <p>
   * getRedKaprr.
   * </p>
   *
   * @param compNumb a int
   * @param phi a double
   * @param bigGamma a double
   * @return a double
   */
  public double getRedKaprr(int compNumb, double phi, double bigGamma) {
    return kaprrc_fconst
        + kaprr_fconst * phi * (Math.exp(2.0 * bigGamma) - 1.0) * Math.pow(bigGamma - 1.0, 2.0);
  }

  /**
   * <p>
   * getRedKapa.
   * </p>
   *
   * @param compNumb a int
   * @param phi a double
   * @param bigGamma a double
   * @return a double
   */
  public double getRedKapa(int compNumb, double phi, double bigGamma) {
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
    double tempVar = 0;
    double A = 1.16145;

    double B = 0.14874;
    double C = 0.52487;
    double D = 0.77320;
    double E = 2.16178;
    double F = 2.43787;
    for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
      Fc[i] = 1.0 - 0.2756 * phase.getPhase().getComponent(i).getAcentricFactor();

      tempVar =
          1.2593 * phase.getPhase().getTemperature() / phase.getPhase().getComponent(i).getTC();
      // eq. 9.4.3 TPoLG
      double varLast = -6.435e-4 * Math.pow(tempVar, 0.14874)
          * Math.sin(18.0323 * Math.pow(tempVar, -0.76830) - 7.27371);

      omegaVisc[i] = A / Math.pow(tempVar, B) + C / Math.exp(D * tempVar)
          + E / Math.exp(F * tempVar) + varLast;

      // double critVol = 0.000235751e6+
      // 1e4*3.4277*(phase.getPhase().getComponent(i).getPC()/(R*phase.getPhase().getComponent(i).getTC()));
      double critVol = phase.getPhase().getPhase().getComponent(i).getCriticalVolume();

      pureComponentViscosity[i] = 40.785
          * Math.sqrt(phase.getPhase().getComponent(i).getMolarMass() * 1000.0
              * phase.getPhase().getTemperature())
          / (Math.pow(critVol, 2.0 / 3.0) * omegaVisc[i]) * Fc[i];
      // logger.info("visk " + pureComponentViscosity[i]);
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
