package neqsim.thermo.phase;

import org.netlib.util.doubleW;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.ComponentGERG2008Eos;
import neqsim.thermo.util.gerg.GERG2008Type;
import neqsim.thermo.util.gerg.NeqSimGERG2008;

/**
 * <p>
 * PhaseGERG2008Eos class.
 * </p>
 *
 * @author victorigi
 * @version $Id: $Id
 */

// --- DISCLAIMER BEGIN ---
// This class is not yet done
// Some of the properties releated to the helmholtz free energy and its derivatives
// are not yet implemented
// --- DISCLAIMER END ---
public class PhaseGERG2008Eos extends PhaseEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  int IPHASE = 0;
  boolean okVolume = true;
  double enthalpy = 0.0;
  double entropy = 0.0;
  double gibbsEnergy = 0.0;
  double CpGERG2008 = 0.0;
  double CvGERG2008 = 0.0;
  double internalEnery = 0.0;
  double JTcoef = 0.0;
  doubleW[] a0 = null;
  doubleW[][] ar = null;
  double kappa = 0.0;
  double W = 0.0;

  /** The GERG-2008 model variant to use. Default is STANDARD. */
  private GERG2008Type gergModelType = GERG2008Type.STANDARD;

  // Caching state for performance optimization
  private transient double cachedTemperature = Double.NaN;
  private transient double cachedPressure = Double.NaN;
  private transient double[] cachedMoleFractions = null;
  private transient boolean propertiesCalculated = false;

  /**
   * <p>
   * Constructor for PhaseGERG2008Eos.
   * </p>
   */
  public PhaseGERG2008Eos() {
    thermoPropertyModelName = "GERG2008 Eos";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseGERG2008Eos clone() {
    PhaseGERG2008Eos clonedPhase = null;
    try {
      clonedPhase = (PhaseGERG2008Eos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /**
   * Get the GERG-2008 model type used by this phase.
   *
   * @return the GERG model type
   */
  public GERG2008Type getGergModelType() {
    return gergModelType;
  }

  /**
   * Set the GERG-2008 model type for this phase.
   *
   * @param modelType the GERG model type to use
   */
  public void setGergModelType(GERG2008Type modelType) {
    this.gergModelType = modelType;
    if (modelType == GERG2008Type.HYDROGEN_ENHANCED) {
      thermoPropertyModelName = "GERG2008-H2 Eos";
    } else {
      thermoPropertyModelName = "GERG2008 Eos";
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentGERG2008Eos(name, moles, molesInPhase, compNumber);
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

    // For initType >= 1, check if state has changed since last calculation
    // The temperature and pressure are now updated by super.init()
    if (initType >= 1) {
      // Check if we can skip GERG calculations (state unchanged)
      if (propertiesCalculated && !hasStateChanged()) {
        // State unchanged - skip expensive GERG-2008 calculations
        return;
      }

      double[] temp = new double[18];
      temp = getProperties_GERG2008();
      a0 = getAlpha0_GERG2008();
      ar = getAlphares_GERG2008();

      pressure = temp[0] / 100;

      Z = temp[1];

      W = temp[11];

      JTcoef = temp[13];

      kappa = temp[14];
      gibbsEnergy = temp[12];
      internalEnery = temp[6];
      enthalpy = temp[7];
      entropy = temp[8];
      CpGERG2008 = temp[10];
      CvGERG2008 = temp[9];
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);

      // Cache current state after successful calculation
      cacheCurrentState();
    }
  }

  /**
   * Check if the thermodynamic state (T, P, composition) has changed since last calculation.
   *
   * @return true if state has changed, false if unchanged
   */
  private boolean hasStateChanged() {
    // Check temperature
    if (Math.abs(temperature - cachedTemperature) > 1e-10) {
      return true;
    }
    // Check pressure
    if (Math.abs(pressure - cachedPressure) > 1e-10) {
      return true;
    }
    // Check composition
    if (cachedMoleFractions == null || cachedMoleFractions.length != numberOfComponents) {
      return true;
    }
    for (int i = 0; i < numberOfComponents; i++) {
      if (Math.abs(getComponent(i).getx() - cachedMoleFractions[i]) > 1e-10) {
        return true;
      }
    }
    return false;
  }

  /**
   * Cache the current thermodynamic state for change detection.
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
   * Invalidate the cached state, forcing recalculation on next init.
   */
  public void invalidateCache() {
    propertiesCalculated = false;
    cachedTemperature = Double.NaN;
    cachedPressure = Double.NaN;
    cachedMoleFractions = null;
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
    return JTcoef * 100.0;
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
    return CpGERG2008 * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    return CvGERG2008 * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    return getMolarMass() * 1e5 / getDensity_GERG2008();
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
  public double dFdTdV() {
    // d/dT d/dV (F/RT) = -(1/(R T)) * (dP/dT)_V + P/(R T^2)
    if (ar == null) {
      return super.dFdTdV();
    }
    double dPdT = getdPdTVn();
    double p = calcPressure();
    return -dPdT / (R * temperature) + p / (R * temperature * temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    // During early initialisation the Helmholtz derivative array may not yet be
    // populated. In that case fall back to the default implementation.
    if (ar == null) {
      return super.dFdVdV();
    }
    // d^2(F/RT)/dV^2 = -(1/(R T)) * (dP/dV)_T
    double dPdV = calcPressuredV();
    return -dPdV / (R * temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity() {
    return getDensity_GERG2008();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns the density calculated directly from GERG-2008 equation of state without requiring
   * physical property initialization.
   * </p>
   */
  @Override
  public double getDensity(String unit) {
    double refDensity = getDensity_GERG2008(); // density in kg/m3
    double conversionFactor = 1.0;
    switch (unit) {
      case "kg/m3":
        conversionFactor = 1.0;
        break;
      case "mol/m3":
        conversionFactor = 1.0 / getMolarMass();
        break;
      case "lb/ft3":
        conversionFactor = 0.0624279606;
        break;
      default:
        throw new RuntimeException("unit not supported " + unit);
    }
    return refDensity * conversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdTVn() {
    return (getNumberOfMolesInPhase() / getVolume()) * R * (1 + ar[0][1].val - ar[1][1].val);
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

  /** {@inheritDoc} */
  @Override
  public double getGresTP() {
    double ar00 = ar != null ? ar[0][0].val : 0.0;
    double ar01 = ar != null ? ar[0][1].val : 0.0;
    return numberOfMolesInPhase * R * temperature * (ar00 + ar01);
  }

  /** {@inheritDoc} */
  @Override
  public double getHresTP() {
    double ar10 = ar != null ? ar[1][0].val : 0.0;
    double ar01 = ar != null ? ar[0][1].val : 0.0;
    return numberOfMolesInPhase * R * temperature * (ar10 + ar01);
  }

  /**
   * Get cached ideal Helmholtz energy derivatives.
   *
   * @return array of {@link org.netlib.util.doubleW} containing \u03b1₀ and its derivatives
   */
  public doubleW[] getAlpha0() {
    return a0;
  }

  /**
   * Get cached residual Helmholtz energy derivatives.
   *
   * @return matrix of {@link org.netlib.util.doubleW} containing \u03b1ᵣ and its derivatives
   */
  public doubleW[][] getAlphaRes() {
    return ar;
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity_GERG2008() {
    NeqSimGERG2008 gerg = new NeqSimGERG2008(this, gergModelType);
    return gerg.getDensity();
  }

  /** {@inheritDoc} */
  @Override
  public double[] getProperties_GERG2008() {
    NeqSimGERG2008 gerg = new NeqSimGERG2008(this, gergModelType);
    return gerg.propertiesGERG();
  }

  /** {@inheritDoc} */
  @Override
  public doubleW[] getAlpha0_GERG2008() {
    NeqSimGERG2008 gerg = new NeqSimGERG2008(this, gergModelType);
    return gerg.getAlpha0_GERG2008();
  }

  /** {@inheritDoc} */
  @Override
  public doubleW[][] getAlphares_GERG2008() {
    NeqSimGERG2008 gerg = new NeqSimGERG2008(this, gergModelType);
    return gerg.getAlphares_GERG2008();
  }
}
