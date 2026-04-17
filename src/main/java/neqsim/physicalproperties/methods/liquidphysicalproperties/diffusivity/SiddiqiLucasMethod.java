package neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * SiddiqiLucasMethod class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SiddiqiLucasMethod extends Diffusivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SiddiqiLucasMethod.
   * </p>
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public SiddiqiLucasMethod(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  /**
   * Detect whether the solvent component j is water.
   *
   * @param j solvent component index
   * @return true if the solvent is water
   */
  private boolean isAqueousSolvent(int j) {
    String name = liquidPhase.getPhase().getComponent(j).getComponentName().toLowerCase();
    return name.equals("water") || name.equals("h2o");
  }

  /**
   * Get the molar volume for a component in cm^3/mol. Falls back to critical volume estimate if
   * normal liquid density is unavailable.
   *
   * @param k component index
   * @return molar volume in cm^3/mol
   */
  private double getMolarVolume(int k) {
    double normalLiquidDensity = liquidPhase.getPhase().getComponent(k).getNormalLiquidDensity();
    double molarMassGperMol = liquidPhase.getPhase().getComponent(k).getMolarMass() * 1000.0;

    if (normalLiquidDensity > 0.01 && !Double.isNaN(normalLiquidDensity)
        && !Double.isInfinite(normalLiquidDensity)) {
      return molarMassGperMol / normalLiquidDensity;
    }
    // Fallback: use critical volume directly
    // getCriticalVolume() returns cm^3/mol
    double Vc = liquidPhase.getPhase().getComponent(k).getCriticalVolume();
    if (Vc > 0) {
      return Vc;
    }
    return Math.max(20.0, 0.285 * molarMassGperMol);
  }

  // aqueous correlation: D = 2.98e-7 * eta_B^(-1.026) * V_A^(-0.5473) * T
  /** {@inheritDoc} */
  @Override
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    double VA = getMolarVolume(i);
    double etaCp = liquidPhase.getPureComponentViscosity(j);
    if (etaCp <= 0 || Double.isNaN(etaCp) || Double.isInfinite(etaCp)) {
      etaCp = liquidPhase.getViscosity() * 1000.0;
    }
    etaCp = Math.max(0.01, etaCp);

    if (isAqueousSolvent(j)) {
      // Siddiqi-Lucas aqueous correlation
      binaryDiffusionCoefficients[i][j] = 1.0e-4 * 2.98e-7 * Math.pow(etaCp, -1.026)
          * Math.pow(VA, -0.5473) * liquidPhase.getPhase().getTemperature();
    } else {
      // Siddiqi-Lucas non-aqueous (organic solvent) correlation
      double VB = getMolarVolume(j);
      binaryDiffusionCoefficients[i][j] = 1.0e-4 * 9.89e-8 * Math.pow(etaCp, -0.907)
          * Math.pow(VA, -0.45) * Math.pow(VB, 0.265) * liquidPhase.getPhase().getTemperature();
    }
    return binaryDiffusionCoefficients[i][j];
  }
}
