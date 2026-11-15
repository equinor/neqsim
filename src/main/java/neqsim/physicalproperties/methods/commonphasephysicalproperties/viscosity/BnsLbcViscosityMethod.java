package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.phase.PhaseBNS;

/**
 * Lohrenz-Bray-Clark viscosity using the Burgoyne-Nielsen-Stanko fitted parameters (SPE-229932-MS and the 2026 helium
 * extension).
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class BnsLbcViscosityMethod extends LBCViscosityMethod {
  private static final long serialVersionUID = 1L;
  private static final int HYDROCARBON_INDEX = 4;
  private static final double METHANE_MOLAR_MASS = 16.0425; // g/mol
  // CO2, H2S, N2, H2, HC (calculated), He. Values are in ft3/lb-mol.
  private static final double[] CRITICAL_VOLUMES = {1.46352, 1.46808, 1.35526, 0.68473, 0.0, 0.76778};

  /**
   * Creates a BNS viscosity method for a gas or liquid BNS phase.
   *
   * @param properties owning physical properties
   */
  public BnsLbcViscosityMethod(PhysicalProperties properties) {
    super(properties);
    setDenseContributionParameters(new double[] {0.1023, 0.023364, 0.058533, -0.0392852, 0.00926279});
  }

  /** {@inheritDoc} */
  @Override
  public double getCriticalVolume(int componentIndex) {
    double volume = CRITICAL_VOLUMES[componentIndex];
    if (componentIndex == HYDROCARBON_INDEX) {
      double molarMass = phase.getPhase().getComponent(componentIndex).getMolarMass() * 1000.0;
      volume = 0.0576710 * (molarMass - METHANE_MOLAR_MASS) + 1.44383;
    }
    return volume * FT3_PER_LBMOL_TO_CM3_PER_MOL;
  }

  /** {@inheritDoc} */
  @Override
  protected boolean useLowTemperatureBranch(double reducedTemperature) {
    return reducedTemperature <= 1.5;
  }

  /** {@inheritDoc} */
  @Override
  protected double getMolarDensity() {
    PhaseBNS bns = (PhaseBNS) phase.getPhase();
    double molarVolume = bns.getMolarVolume() - bns.getMolarVolumeTranslation();
    return 0.1 / molarVolume; // NeqSim volume units (1e-5 m3/mol) to mol/cm3.
  }
}
