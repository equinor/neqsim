package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentWater;
import neqsim.thermo.util.steam.Iapws_if97;

/**
 * Phase implementation using the IAPWS-IF97 reference equations for pure water.
 *
 * @author esol
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
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);

    double pMPa = pressure / 10.0; // convert bar to MPa
    double molarMass = getMolarMass();

    double vSpec = Iapws_if97.v_pt(pMPa, temperature); // m3/kg
    double vMolar = vSpec * molarMass; // m3/mol
    this.molarVolume = vMolar;
    this.phaseVolume = vMolar * numberOfMolesInPhase;

    // Set phase type based on density
    if (vSpec < 0.01) {
      setType(PhaseType.AQUEOUS);
    } else {
      setType(PhaseType.GAS);
    }

    double hSpec = Iapws_if97.h_pt(pMPa, temperature); // kJ/kg
    double sSpec = Iapws_if97.s_pt(pMPa, temperature); // kJ/kg/K
    double cpSpec = Iapws_if97.cp_pt(pMPa, temperature); // kJ/kg/K
    double cvSpec = cpSpec - 0.461526; // kJ/kg/K
    double gSpec = hSpec - temperature * sSpec; // kJ/kg

    enthalpy = hSpec * molarMass * 1e3; // J/mol
    entropy = sSpec * molarMass * 1e3; // J/mol/K
    cp = cpSpec * molarMass * 1e3; // J/mol/K
    cv = cvSpec * molarMass * 1e3; // J/mol/K
    gibbsEnergy = gSpec * molarMass * 1e3; // J/mol
    internalEnergy = enthalpy - pressure * 1e5 * vMolar; // J/mol
    soundSpeed = Iapws_if97.w_pt(pMPa, temperature);

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
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt) {
    double pMPa = pressure / 10.0;
    double vSpec = Iapws_if97.v_pt(pMPa, temperature);
    return vSpec * getMolarMass();
  }

  /** {@inheritDoc} */
  @Override
  public double calcPressure() {
    // Estimate pressure from current molar volume using iteration
    double molarMass = getMolarMass();
    double vSpec = molarVolume / molarMass;
    double pMPa = pressure / 10.0;
    for (int iter = 0; iter < 20; iter++) {
      double vCalc = Iapws_if97.v_pt(pMPa, temperature);
      double dvdp = (Iapws_if97.v_pt(pMPa + 1e-6, temperature) - vCalc) / 1e-6;
      double error = vCalc - vSpec;
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
