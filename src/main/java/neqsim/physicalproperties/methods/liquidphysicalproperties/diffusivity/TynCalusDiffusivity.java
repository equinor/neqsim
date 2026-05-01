package neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Tyn-Calus method for liquid-phase binary diffusion coefficients at infinite dilution.
 *
 * <p>
 * This correlation is particularly accurate for non-polar / hydrocarbon systems. It avoids the need
 * for an association parameter (unlike Wilke-Chang) by using molar volumes of both solute and
 * solvent at their normal boiling points.
 * </p>
 *
 * <p>
 * The correlation is:
 * </p>
 *
 * <pre>
 * D_AB^0 = 8.93e-8 * V_B^0.267 * T / (eta_B * V_A^0.433)
 * </pre>
 *
 * <p>
 * where D_AB^0 is diffusivity at infinite dilution in cm^2/s, V_A and V_B are molar volumes at
 * normal boiling point in cm^3/mol, T is temperature in K, and eta_B is solvent viscosity in cP.
 * </p>
 *
 * <p>
 * For systems involving water or other associated solvents, the Wilke-Chang method may be more
 * appropriate. The Tyn-Calus method is recommended for oil/gas applications involving hydrocarbon
 * solvents.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Tyn, M.T. and Calus, W.F. (1975). "Diffusion coefficients in dilute binary liquid mixtures."
 * J. Chem. Eng. Data, 20, 106-109.</li>
 * <li>Poling, B.E., Prausnitz, J.M. and O'Connell, J.P. (2001). "The Properties of Gases and
 * Liquids." 5th ed., McGraw-Hill, Section 11-9.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class TynCalusDiffusivity extends Diffusivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for TynCalusDiffusivity.
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public TynCalusDiffusivity(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  /**
   * Get the molar volume at normal boiling point for component k in cm^3/mol.
   *
   * <p>
   * Uses normal liquid density if available, otherwise estimates from critical volume: V_b = 0.285
   * * Vc^1.048 (Tyn-Calus correlation).
   * </p>
   *
   * @param k the component index
   * @return molar volume at boiling point in cm^3/mol
   */
  private double getMolarVolumeAtBoilingPoint(int k) {
    double molarMassGperMol = liquidPhase.getPhase().getComponent(k).getMolarMass() * 1000.0; // g/mol
    double normalLiquidDensity = liquidPhase.getPhase().getComponent(k).getNormalLiquidDensity(); // g/cm^3

    if (normalLiquidDensity > 0 && !Double.isNaN(normalLiquidDensity)) {
      return molarMassGperMol / normalLiquidDensity;
    }

    // Estimate from critical volume using Tyn-Calus method
    // getCriticalVolume() returns cm^3/mol
    double Vc = liquidPhase.getPhase().getComponent(k).getCriticalVolume();
    if (Vc > 0) {
      return 0.285 * Math.pow(Vc, 1.048);
    }

    // Fallback estimate from molar mass
    return Math.max(20.0, 0.285 * molarMassGperMol);
  }

  /** {@inheritDoc} */
  @Override
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    double T = liquidPhase.getPhase().getTemperature(); // K

    // Molar volumes at normal boiling point [cm^3/mol]
    double VA = getMolarVolumeAtBoilingPoint(i); // solute
    double VB = getMolarVolumeAtBoilingPoint(j); // solvent

    // Apply reasonable limits
    VA = Math.max(20.0, Math.min(VA, 600.0));
    VB = Math.max(20.0, Math.min(VB, 600.0));

    // Solvent viscosity in cP
    double etaCp = liquidPhase.getPureComponentViscosity(j);
    if (etaCp <= 0 || Double.isNaN(etaCp)) {
      // Fallback to mixture viscosity (Pa.s -> cP)
      double mixVisc = liquidPhase.getViscosity();
      etaCp = mixVisc * 1000.0;
    }
    etaCp = Math.max(0.01, Math.min(etaCp, 500.0));

    // Tyn-Calus equation:
    // D [cm^2/s] = 8.93e-8 * V_B^0.267 * T / (eta_B * V_A^0.433)
    double D = 8.93e-8 * Math.pow(VB, 0.267) * T / (etaCp * Math.pow(VA, 0.433));

    // Convert from cm^2/s to m^2/s
    D *= 1e-4;

    // Sanity check: liquid diffusivities should be 1e-12 to 1e-7 m^2/s
    if (D < 1e-13 || D > 1e-6 || Double.isNaN(D) || Double.isInfinite(D)) {
      D = 1e-9; // Reasonable fallback
    }

    binaryDiffusionCoefficients[i][j] = D;
    return binaryDiffusionCoefficients[i][j];
  }
}
