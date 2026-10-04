package neqsim.thermo.phase;

import org.netlib.util.doubleW;
import neqsim.thermo.component.ComponentAmmoniaEos;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.util.referenceequations.Ammonia2023;

/**
 * Phase implementation for the Ammonia2023 reference equation of state based on a multiparameter Helmholtz energy
 * formulation. Thermodynamic properties are evaluated from ideal and residual Helmholtz energy derivatives provided by
 * {@link neqsim.thermo.util.referenceequations.Ammonia2023}.
 *
 * @author esol
 */
public class PhaseAmmoniaEos extends PhaseEos {
  private static final long serialVersionUID = 1000L;

  private transient Ammonia2023 ammoniaUtil = new Ammonia2023(this);

  private double enthalpy;
  private double entropy;
  private double gibbsEnergy;
  private double Cp;
  private double Cv;
  private double internalEnergy;
  private double JTcoef;
  private double kappa;
  private double W;
  private transient doubleW[] a0;
  private transient doubleW[][] ar;

  /**
   * Constructor for PhaseAmmoniaEos.
   */
  public PhaseAmmoniaEos() {
    thermoPropertyModelName = "Ammonia Reference Eos";
  }

  private Ammonia2023 ammoniaUtil() {
    if (ammoniaUtil == null) {
      ammoniaUtil = new Ammonia2023(this);
    } else {
      ammoniaUtil.setPhase(this);
    }
    return ammoniaUtil;
  }

  /** {@inheritDoc} */
  @Override
  public PhaseAmmoniaEos clone() {
    PhaseAmmoniaEos cloned = null;
    try {
      cloned = (PhaseAmmoniaEos) super.clone();
      cloned.ammoniaUtil = new Ammonia2023(cloned);
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return cloned;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentAmmoniaEos(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt, double beta) {
    setType(pt);
    double[] props = ammoniaUtil().properties();
    pressure = props[0] / 100.0; // convert from kPa to bar
    Z = props[1];
    internalEnergy = props[6];
    enthalpy = props[7];
    entropy = props[8];
    Cv = props[9];
    Cp = props[10];
    W = props[11];
    gibbsEnergy = props[12];
    JTcoef = props[13];
    kappa = props[14];
    a0 = ammoniaUtil.getAlpha0();
    ar = ammoniaUtil.getAlphaRes();
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    pressure = props[0] / 100.0;
    Z = props[1];
    setType(pt);
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    return gibbsEnergy * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getJouleThomsonCoefficient() {
    return JTcoef * 100.0; // convert from K/kPa to K/bar
  }

  /** {@inheritDoc} */
  @Override
  public double getEnthalpy() {
    return enthalpy * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropy() {
    return entropy * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getInternalEnergy() {
    return internalEnergy * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getCp() {
    return Cp * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    return Cv * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getViscosity() {
    return ammoniaUtil().getViscosity();
  }

  /** {@inheritDoc} */
  @Override
  public double getThermalConductivity() {
    return ammoniaUtil().getThermalConductivity();
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt) {
    // Convert mol/m3 directly; the database molecular weight can differ from the reference model.
    return 1.0e5 / ammoniaUtil().getMolarDensity();
  }

  /** {@inheritDoc} */
  @Override
  public double calcPressure() {
    return ammoniaUtil().pressureFromDensity(1.0e5 / getMolarVolume(), temperature) / 1.0e5;
  }

  /** {@inheritDoc} */
  @Override
  public double calcPressuredV() {
    double density = 1.0e5 / getMolarVolume();
    // P is in bar and the independent total volume is V_SI * 1e5.
    return -ammoniaUtil().pressureDerivativeDensity(density, temperature) * density * density
        / (numberOfMolesInPhase * 1.0e10);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(int i) {
    return ((ComponentEosInterface) getComponent(i)).dFdN(this, this.getNumberOfComponents(), temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int i, int j) {
    return ((ComponentEosInterface) getComponent(i)).dFdNdN(j, this, this.getNumberOfComponents(), temperature,
        pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(int i) {
    return ((ComponentEosInterface) getComponent(i)).dFdNdV(this, this.getNumberOfComponents(), temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(int i) {
    return ((ComponentEosInterface) getComponent(i)).dFdNdT(this, this.getNumberOfComponents(), temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity() {
    return ammoniaUtil().getDensity();
  }

  /**
   * getAlpha0.
   *
   * @return an array of {@link org.netlib.util.doubleW} objects
   */
  public doubleW[] getAlpha0() {
    return a0;
  }

  /**
   * getAlphares.
   *
   * @return an array of {@link org.netlib.util.doubleW} objects
   */
  public doubleW[][] getAlphares() {
    return ar;
  }

  /**
   * getHresTP.
   *
   * @return a double
   */
  @Override
  public double getHresTP() {
    return numberOfMolesInPhase * R * temperature * (ar[1][0].val + ar[0][1].val);
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdTVn() {
    return (getNumberOfMolesInPhase() / getVolume()) * R;
  }

  /** {@inheritDoc} */
  @Override
  public double getSoundSpeed() {
    return W;
  }

  /**
   * Return the isothermal compressibility in 1/Pa as evaluated by the
   * {@link neqsim.thermo.util.referenceequations.Ammonia2023} helper.
   *
   * @return isothermal compressibility (1/Pa)
   */
  @Override
  public double getIsothermalCompressibility() {
    return kappa;
  }
}
