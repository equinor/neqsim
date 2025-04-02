package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGERG2008;

/**
 * PhaseGERG2008Eos class upgraded to use GERG2008.
 * 
 * <p>This class upgrades the old GERG2004Eos by using the NeqSimGERG2008 routines to
 * calculate thermodynamic properties. In addition, extensive properties are returned by
 * multiplying the molar values by the number of moles in the phase. This version mimics the
 * structure of the Leachman EOS implementation.</p>
 * 
 * @author YourName
 * @version 1.1
 */
public class PhaseGERG2008Eos extends PhaseEos {
  private static final long serialVersionUID = 1000;

  int IPHASE = 0;
  boolean okVolume = true;
  double enthalpy = 0.0;
  double entropy = 0.0;
  double gibbsEnergy = 0.0;
  double CpGERG = 0.0;
  double CvGERG = 0.0;
  double internalEnergy = 0.0;
  double JTcoef = 0.0;

  // Helper for property calculations using GERG2008
  //NeqSimGERG2008 gerg2008 = new NeqSimGERG2008();

  /**
   * Constructor for PhaseGERG2008Eos.
   */
  public PhaseGERG2008Eos() {
      thermoPropertyModelName = "GERG-EoS 2008";
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

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    // Use the GERG2008-specific component.
    componentArray[compNumber] = new ComponentGERG2008(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt, double beta) {
    IPHASE = pt == PhaseType.LIQUID ? -1 : -2;
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);

    if (!okVolume) {
      IPHASE = pt == PhaseType.LIQUID ? -2 : -1;
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    }
    if (initType >= 1) {
      // Let the GERG2008 helper map the phase composition
      double[] properties = new double[18];
      properties = getProperties_GERG2008();
      // Retrieve the molar thermodynamic properties from GERG2008.
      // Assumed mapping:
      // properties[6]: internal energy (J/mol)
      // properties[7]: enthalpy (J/mol)
      // properties[8]: entropy (J/mol·K)
      // properties[9]: Cv (J/mol·K)
      // properties[10]: Cp (J/mol·K)
      // properties[12]: Gibbs energy (J/mol)
      // properties[13]: Joule-Thomson coefficient (K/kPa)
      internalEnergy = properties[6];
      enthalpy = properties[7];
      entropy = properties[8];
      CvGERG = properties[9];
      CpGERG = properties[10];
      gibbsEnergy = properties[12];
      JTcoef = properties[13];
      }
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
  }

  @Override
  public double getGibbsEnergy() {
      return gibbsEnergy * numberOfMolesInPhase;
  }

  @Override
  public double getJouleThomsonCoefficient() {
      return JTcoef * 1e3; // e.g., converting from K/kPa to K/bar
  }

  @Override
  public double getEnthalpy() {
      return enthalpy * numberOfMolesInPhase;
  }

  @Override
  public double getEntropy() {
      return entropy * numberOfMolesInPhase;
  }

  @Override
  public double getInternalEnergy() {
    return internalEnergy * numberOfMolesInPhase;
  }

  @Override
  public double getCp() {
    return CpGERG * numberOfMolesInPhase;
  }

  @Override
  public double getCv() {
    return CvGERG * numberOfMolesInPhase;
  }

  /**
   * Computes the molar volume using the GERG2008 density routine.
   * <p>
   * We assume that gerg2008.getMolarDensity(this) returns the molar density in [mol/m³].
   * Then, for consistency with the Leachman approach, we define:
   * 
   * molarVolume = 1e5 / molarDensity.
   * </p>
   */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
          throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {
    return getMolarMass() * 1e5 / getDensity_GERG2008();
  }
}
