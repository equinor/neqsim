package neqsim.thermo.phase;

import org.netlib.util.doubleW;
import neqsim.thermo.component.ComponentAmmoniaEos;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.util.referenceequations.Ammonia2023;

/**
 * Phase implementation for the Ammonia2023 reference equation of state based on a multiparameter
 * Helmholtz energy formulation. Thermodynamic properties are evaluated from ideal and residual
 * Helmholtz energy derivatives provided by
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
  private doubleW[] a0;
  private doubleW[][] ar;

  /**
   * <p>
   * Constructor for PhaseAmmoniaEos.
   * </p>
   */
  public PhaseAmmoniaEos() {
    thermoPropertyModelName = "Ammonia Reference Eos";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseAmmoniaEos clone() {
    PhaseAmmoniaEos cloned = null;
    try {
      cloned = (PhaseAmmoniaEos) super.clone();
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
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    setType(pt);
    ammoniaUtil.setPhase(this);
    double[] props = ammoniaUtil.properties();
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
    ammoniaUtil.setPhase(this);
    return ammoniaUtil.getViscosity();
  }

  /** {@inheritDoc} */
  @Override
  public double getThermalConductivity() {
    ammoniaUtil.setPhase(this);
    return ammoniaUtil.getThermalConductivity();
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt) {
    // The base EOS implementation expects molar volume in units of m^3/bar·mol
    // (i.e. actual molar volume multiplied by 1e5) since pressures are handled
    // in bar. The reference ammonia model calculates density directly in kg/m3,
    // so convert the density back to this expanded molar volume representation
    // to maintain consistency with the rest of the framework.
    return getMolarMass() / getDensity() * 1.0e5;
  }

  /** {@inheritDoc} */
  @Override
  public double calcPressure() {
    return pressure;
  }

  /** {@inheritDoc} */
  @Override
  public double calcPressuredV() {
    return 0.0;
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

  /** {@inheritDoc} */
  @Override
  public double getDensity() {
    ammoniaUtil.setPhase(this);
    return ammoniaUtil.getDensity();
  }

  /**
   * <p>
   * getAlpha0.
   * </p>
   *
   * @return an array of {@link org.netlib.util.doubleW} objects
   */
  public doubleW[] getAlpha0() {
    return a0;
  }

  /**
   * <p>
   * getAlphares.
   * </p>
   *
   * @return an array of {@link org.netlib.util.doubleW} objects
   */
  public doubleW[][] getAlphares() {
    return ar;
  }

  /**
   * <p>
   * getHresTP.
   * </p>
   *
   * @return a double
   */
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
  public double getIsothermalCompressibility() {
    return kappa;
  }
}

