package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentSpanWagnerEos;
import neqsim.thermo.util.spanwagner.NeqSimSpanWagner;

/**
 * Phase implementation using the Span-Wagner reference equation for CO2.
 *
 * @author esol
 */
public class PhaseSpanWagnerEos extends PhaseEos {
  private static final long serialVersionUID = 1000;

  private double enthalpy; // J/mol
  private double entropy; // J/molK
  private double gibbsEnergy; // J/mol
  private double cp; // J/molK
  private double cv; // J/molK
  private double internalEnergy; // J/mol
  private double soundSpeed; // m/s
  private double molarDensity; // mol/m3
  private double jouleThomson; // K/Pa

  // Caching state for performance optimization
  private transient double cachedTemperature = Double.NaN;
  private transient double cachedPressure = Double.NaN;
  private transient boolean propertiesCalculated = false;

  /**
   * <p>
   * Constructor for PhaseSpanWagnerEos.
   * </p>
   */
  public PhaseSpanWagnerEos() {
    thermoPropertyModelName = "Span-Wagner";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseSpanWagnerEos clone() {
    return (PhaseSpanWagnerEos) super.clone();
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentSpanWagnerEos(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    if (initType >= 1) {
      // Check if we can skip Span-Wagner calculations (state unchanged)
      if (propertiesCalculated && !hasStateChanged()) {
        // State unchanged - skip expensive Span-Wagner calculations
        return;
      }

      // Determine correct phase type before calling Span-Wagner
      // CO2 critical point: Tc = 304.1282 K, Pc = 7.3773 MPa = 73.773 bar
      PhaseType effectiveType = pt;
      if (temperature < 304.1282) {
        // Below critical temperature: determine phase from saturation pressure
        double pSat = NeqSimSpanWagner.getSaturationPressure(temperature);
        if (pressure * 1e5 > pSat) {
          // Above saturation pressure = liquid
          effectiveType = PhaseType.LIQUID;
        } else {
          // Below saturation pressure = gas
          effectiveType = PhaseType.GAS;
        }
      } else {
        // Supercritical: use density-based criterion
        // At critical point, rhoc = 467.6 kg/m3 = 10624.9 mol/m3
        // Use gas-like initial guess and let algorithm find the solution
        effectiveType = pt;
      }

      double[] props = NeqSimSpanWagner.getProperties(temperature, pressure * 1e5, effectiveType);
      molarDensity = props[0];
      Z = props[1];
      enthalpy = props[2];
      entropy = props[3];
      cp = props[4];
      cv = props[5];
      internalEnergy = props[6];
      gibbsEnergy = props[7];
      soundSpeed = props[8];
      getComponent(0).setFugacityCoefficient(props[9]);
      jouleThomson = props[10];
      // Set phase type based on calculated density
      // CO2 critical density: 10624.9 mol/m3
      if (molarDensity > 10624.9 * 0.5) {
        setType(PhaseType.LIQUID);
      } else {
        setType(PhaseType.GAS);
      }

      // Cache current state after successful calculation
      cacheCurrentState();
    }
  }

  /**
   * Check if the thermodynamic state (T, P) has changed since last calculation.
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
    return false;
  }

  /**
   * Cache the current thermodynamic state for change detection.
   */
  private void cacheCurrentState() {
    cachedTemperature = temperature;
    cachedPressure = pressure;
    propertiesCalculated = true;
  }

  /**
   * Invalidate the cached state, forcing recalculation on next init.
   */
  public void invalidateCache() {
    propertiesCalculated = false;
    cachedTemperature = Double.NaN;
    cachedPressure = Double.NaN;
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
    return cp * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    return cv * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getSoundSpeed() {
    return soundSpeed;
  }

  /** {@inheritDoc} */
  @Override
  public double getJouleThomsonCoefficient() {
    return jouleThomson * 1e5;
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity() {
    return molarDensity * getMolarMass();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns the density calculated directly from Span-Wagner equation of state.
   * </p>
   */
  @Override
  public double getDensity(String unit) {
    double refDensity = molarDensity * getMolarMass(); // density in kg/m3
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

  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt) {
    return 1.0 / molarDensity * 1e5;
  }
}
