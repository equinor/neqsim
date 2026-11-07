package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentWater;
import neqsim.thermo.util.steam.Iapws_if97;
import neqsim.thermo.util.steam.SeawaterProperties;

/**
 * Phase implementation using the IAPWS-IF97 reference equations for pure water.
 *
 * @author esol
 * @version 1.1
 */
public class PhaseWaterIAPWS extends PhaseEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private double enthalpy = 0.0; // J/mol
  private double entropy = 0.0; // J/mol/K
  private double cp = 0.0; // J/mol/K
  private double cv = 0.0; // J/mol/K
  private double gibbsEnergy = 0.0; // J/mol
  private double internalEnergy = 0.0; // J/mol
  private double soundSpeed = 0.0; // m/s
  /** Reference-composition salinity [g/kg solution]; zero for ordinary IF97 water. */
  private double salinity = 0.0;

  /**
   * Sets bulk salinity. Use SystemSeawater to construct a complete saline utility fluid.
   *
   * @param salinity reference-composition salinity [g/kg solution]
   * @throws IllegalArgumentException if salinity is non-finite or outside 0-120 g/kg
   */
  public void setSalinity(double salinity) {
    if (!Double.isFinite(salinity) || salinity < 0.0 || salinity > 120.0) {
      throw new IllegalArgumentException("Salinity must be within 0-120 g/kg solution");
    }
    this.salinity = salinity;
    resetPhysicalProperties();
  }

  /**
   * Returns bulk reference-composition salinity.
   *
   * @return salinity [g/kg solution]
   */
  public double getSalinity() {
    return salinity;
  }

  /**
   * Default constructor.
   */
  public PhaseWaterIAPWS() {
    thermoPropertyModelName = "IAPWS-IF97";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseWaterIAPWS clone() {
    PhaseWaterIAPWS cloned = null;
    try {
      cloned = (PhaseWaterIAPWS) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning PhaseWaterIAPWS failed", ex);
    }
    return cloned;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentWater(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt, double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);

    double pMPa = pressure / 10.0; // convert bar to MPa
    double molarMass = getMolarMass();

    double vSpec = Iapws_if97.v_pt(pMPa, temperature); // m3/kg
    double vMolar = vSpec * molarMass; // m3/mol
    // NeqSim stores volume in m3 * 1e5; only unit-bearing volume getters return SI.
    this.molarVolume = vMolar * 1e5;
    this.phaseVolume = molarVolume * numberOfMolesInPhase;

    // Keep phase publication consistent with the Region 1/2 boundary used by
    // Iapws_if97 for all property calculations.
    if (temperature <= Iapws_if97.T4_p(pMPa)) {
      setType(PhaseType.AQUEOUS);
    } else {
      setType(PhaseType.GAS);
    }

    double hSpec = Iapws_if97.h_pt(pMPa, temperature); // kJ/kg
    double sSpec = Iapws_if97.s_pt(pMPa, temperature); // kJ/kg/K
    double cpSpec = Iapws_if97.cp_pt(pMPa, temperature); // kJ/kg/K
    double cvSpec = Iapws_if97.cv_pt(pMPa, temperature); // kJ/kg/K
    double gSpec = hSpec - temperature * sSpec; // kJ/kg

    enthalpy = hSpec * molarMass * 1e3; // J/mol
    entropy = sSpec * molarMass * 1e3; // J/mol/K
    cp = cpSpec * molarMass * 1e3; // J/mol/K
    cv = cvSpec * molarMass * 1e3; // J/mol/K
    gibbsEnergy = gSpec * molarMass * 1e3; // J/mol
    internalEnergy = enthalpy - pressure * 1e5 * vMolar; // J/mol
    soundSpeed = Iapws_if97.w_pt(pMPa, temperature);

    if (salinity > 0.0) {
      double g = SeawaterProperties.gibbsDerivative(temperature, pressure, salinity, 0, 0);
      double gT = SeawaterProperties.gibbsDerivative(temperature, pressure, salinity, 1, 0);
      double gP = SeawaterProperties.gibbsDerivative(temperature, pressure, salinity, 0, 1);
      double gTT = SeawaterProperties.gibbsDerivative(temperature, pressure, salinity, 2, 0);
      double gTP = SeawaterProperties.gibbsDerivative(temperature, pressure, salinity, 1, 1);
      double gPP = SeawaterProperties.gibbsDerivative(temperature, pressure, salinity, 0, 2);
      vMolar = gP * molarMass;
      molarVolume = vMolar * 1e5;
      phaseVolume = molarVolume * numberOfMolesInPhase;
      enthalpy = (g - temperature * gT) * molarMass;
      entropy = -gT * molarMass;
      cp = -temperature * gTT * molarMass;
      cv = (-temperature * gTT + temperature * gTP * gTP / gPP) * molarMass;
      gibbsEnergy = g * molarMass;
      internalEnergy = enthalpy - pressure * 1e5 * vMolar;
      soundSpeed = Math.sqrt(gP * gP / (gTP * gTP / gTT - gPP));
      setType(PhaseType.AQUEOUS);
    }

    Z = pressure * 1e5 * vMolar / (R * temperature);

    for (int i = 0; i < numberOfComponents; i++) {
      getComponent(i).setFugacityCoefficient(1.0);
    }
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
  public double getGibbsEnergy() {
    return gibbsEnergy * numberOfMolesInPhase;
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
  public double getDensity() {
    return getMolarMass() / getMolarVolume() * 1e5;
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity(String unit) {
    double density = getDensity();
    switch (unit) {
    case "kg/m3":
      return density;
    case "mol/m3":
      return density / getMolarMass();
    case "lb/ft3":
      return density * 0.0624279606;
    default:
      throw new IllegalArgumentException("Unit not supported: " + unit);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt) {
    double pMPa = pressure / 10.0;
    double vSpec = Iapws_if97.v_pt(pMPa, temperature);
    if (salinity > 0.0) {
      vSpec = SeawaterProperties.gibbsDerivative(temperature, pressure, salinity, 0, 1);
    }
    return vSpec * getMolarMass() * 1e5;
  }

  /** {@inheritDoc} */
  @Override
  public double calcPressure() {
    // Estimate pressure from current molar volume using iteration
    double molarMass = getMolarMass();
    double vSpec = molarVolume / molarMass / 1e5;
    double pMPa = pressure / 10.0;
    for (int iter = 0; iter < 20; iter++) {
      double vCalc = salinity > 0.0 ? SeawaterProperties.gibbsDerivative(temperature, pMPa * 10.0, salinity, 0, 1)
          : Iapws_if97.v_pt(pMPa, temperature);
      double dvdp = salinity > 0.0 ? SeawaterProperties.gibbsDerivative(temperature, pMPa * 10.0, salinity, 0, 2) * 1e6
          : (Iapws_if97.v_pt(pMPa + 1e-6, temperature) - vCalc) / 1e-6;
      double error = vCalc - vSpec;
      if (Math.abs(error) < Math.abs(vSpec) * 1e-12) {
        break;
      }
      pMPa -= error / dvdp;
    }
    return pMPa * 10.0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcPressuredV() {
    // Numerical derivative of pressure with respect to volume
    double oldVolume = molarVolume;
    double dpdV;
    double dV = oldVolume * 1e-6;
    molarVolume = oldVolume + dV;
    double pPlus = calcPressure();
    molarVolume = oldVolume - dV;
    double pMinus = calcPressure();
    molarVolume = oldVolume;
    dpdV = (pPlus - pMinus) / (2 * dV);
    return dpdV;
  }
}
