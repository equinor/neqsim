package neqsim.thermo.phase;

import org.netlib.util.doubleW;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.ComponentVegaEos;

/**
 * <p>
 * PhaseVegaEos class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseVegaEos extends PhaseEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  int IPHASE = 0;
  boolean okVolume = true;
  double enthalpy = 0.0;
  double entropy = 0.0;
  double gibbsEnergy = 0.0;
  double CpVega = 0.0;
  double CvVega = 0.0;
  double internalEnery = 0.0;
  double JTcoef = 0.0;
  doubleW[] a0 = null;
  doubleW[][] ar = null;
  double kappa = 0.0;
  double W = 0.0;

  // State caching for performance optimization
  private transient double cachedTemperature = Double.NaN;
  private transient double cachedPressure = Double.NaN;
  private transient double[] cachedMoleFractions = null;
  private transient boolean propertiesCalculated = false;

  /**
   * <p>
   * Constructor for PhaseVegaEos.
   * </p>
   */
  public PhaseVegaEos() {
    thermoPropertyModelName = "Vega Eos";
  }

  /**
   * Checks if the thermodynamic state has changed since the last calculation.
   *
   * @return true if state has changed
   */
  private boolean hasStateChanged() {
    if (Double.isNaN(cachedTemperature) || Double.isNaN(cachedPressure)) {
      return true;
    }

    if (Math.abs(cachedTemperature - temperature) > 1e-10) {
      return true;
    }

    if (Math.abs(cachedPressure - pressure) > 1e-10) {
      return true;
    }

    if (cachedMoleFractions == null || cachedMoleFractions.length != numberOfComponents) {
      return true;
    }

    for (int i = 0; i < numberOfComponents; i++) {
      if (Math.abs(cachedMoleFractions[i] - getComponent(i).getx()) > 1e-10) {
        return true;
      }
    }

    return false;
  }

  /**
   * Caches the current thermodynamic state.
   */
  private void cacheCurrentState() {
    cachedTemperature = temperature;
    cachedPressure = pressure;

    if (cachedMoleFractions == null || cachedMoleFractions.length != numberOfComponents) {
      cachedMoleFractions = new double[numberOfComponents];
    }

    for (int i = 0; i < numberOfComponents; i++) {
      cachedMoleFractions[i] = getComponent(i).getx();
    }

    propertiesCalculated = true;
  }

  /**
   * Invalidates the cached state, forcing recalculation on next init.
   */
  public void invalidateCache() {
    cachedTemperature = Double.NaN;
    cachedPressure = Double.NaN;
    cachedMoleFractions = null;
    propertiesCalculated = false;
  }

  /** {@inheritDoc} */
  @Override
  public PhaseVegaEos clone() {
    PhaseVegaEos clonedPhase = null;
    try {
      clonedPhase = (PhaseVegaEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    // Invalidate cached state in clone to force fresh calculations
    if (clonedPhase != null) {
      clonedPhase.invalidateCache();
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentVegaEos(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    IPHASE = pt == PhaseType.LIQUID ? -1 : -2;
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);

    if (!okVolume) {
      IPHASE = pt == PhaseType.LIQUID ? -2 : -1;
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    }
    if (initType >= 1) {
      // Check if we can skip Vega calculations (state unchanged)
      if (propertiesCalculated && !hasStateChanged()) {
        // State unchanged - skip expensive Vega calculations
        return;
      }

      double[] temp = new double[18];
      temp = getProperties_Vega();
      a0 = getAlpha0_Vega();
      ar = getAlphares_Vega();

      pressure = temp[0] / 100;
      Z = temp[1];
      W = temp[11];
      JTcoef = temp[13];
      kappa = temp[14];
      gibbsEnergy = temp[12];
      internalEnery = temp[6];
      enthalpy = temp[7];
      entropy = temp[8];
      CpVega = temp[10];
      CvVega = temp[9];

      // Cache the current state after calculations
      cacheCurrentState();

      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    return gibbsEnergy * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getZ() {
    return Z;
  }

  /** {@inheritDoc} */
  @Override
  public double getJouleThomsonCoefficient() {
    return JTcoef * 1e3;
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
    return internalEnery * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getCp() {
    return CpVega * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    return CvVega * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    return getMolarMass() * 1e5 / getDensity_Vega();
  }

  /** {@inheritDoc} */
  @Override
  public double calcPressure() {
    return numberOfMolesInPhase / getVolume() * R * temperature * (1 + ar[0][1].val);
  }

  /** {@inheritDoc} */
  @Override
  public double calcPressuredV() {
    return -Math.pow(getDensity() / getMolarMass(), 2) * R * temperature
        * (1 + 2 * ar[0][1].val + ar[0][2].val) / numberOfMolesInPhase;
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
    return getDensity_Vega();
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdTVn() {
    return (getNumberOfMolesInPhase() / getVolume()) * R * (1 + ar[0][1].val + ar[1][1].val);
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdVTn() {
    return -Math.pow(getNumberOfMolesInPhase() / getVolume(), 2) * R * temperature
        * (1 + 2 * ar[0][1].val + ar[0][2].val) / numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdrho() {
    return R * temperature * (1 + 2 * ar[0][1].val + ar[0][2].val);
  }
}
