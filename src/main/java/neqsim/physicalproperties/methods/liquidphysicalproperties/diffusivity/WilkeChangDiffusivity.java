package neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity;

import java.util.HashMap;
import java.util.Map;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Wilke-Chang method for liquid-phase binary diffusion coefficients at infinite dilution.
 *
 * <p>
 * This is the most widely used and well-validated correlation for estimating liquid diffusion
 * coefficients. It is based on the Stokes-Einstein equation with an empirical association parameter
 * for the solvent.
 * </p>
 *
 * <p>
 * The correlation is:
 * </p>
 *
 * <pre>
 * D_AB^0 = 7.4e-8 * (phi_B * M_B)^0.5 * T / (eta_B * V_A^0.6)
 * </pre>
 *
 * <p>
 * where D_AB^0 is diffusivity at infinite dilution in cm^2/s, phi_B is the association parameter of
 * solvent B, M_B is the molecular weight of solvent in g/mol, T is temperature in K, eta_B is
 * solvent viscosity in cP, and V_A is the molar volume of solute at its normal boiling point in
 * cm^3/mol.
 * </p>
 *
 * <p>
 * The association parameter phi accounts for hydrogen bonding:
 * </p>
 * <ul>
 * <li>Water: 2.26</li>
 * <li>Methanol: 1.9</li>
 * <li>Ethanol: 1.5</li>
 * <li>Non-associated solvents (hydrocarbons, etc.): 1.0</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Wilke, C.R. and Chang, P. (1955). "Correlation of diffusion coefficients in dilute
 * solutions." AIChE J., 1, 264-270.</li>
 * <li>Poling, B.E., Prausnitz, J.M. and O'Connell, J.P. (2001). "The Properties of Gases and
 * Liquids." 5th ed., McGraw-Hill, Section 11-4.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class WilkeChangDiffusivity extends Diffusivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Association parameters for common solvents. These account for the degree of hydrogen bonding or
   * association in the solvent.
   */
  private static final Map<String, Double> ASSOCIATION_PARAMS = createAssociationParams();

  /**
   * Create association parameter table.
   *
   * @return map of solvent name (lowercase) to association parameter
   */
  private static Map<String, Double> createAssociationParams() {
    Map<String, Double> phi = new HashMap<String, Double>();
    // Water and heavy water
    phi.put("water", 2.26);
    phi.put("h2o", 2.26);
    phi.put("d2o", 2.26);
    // Alcohols
    phi.put("methanol", 1.9);
    phi.put("ethanol", 1.5);
    phi.put("1-propanol", 1.2);
    phi.put("2-propanol", 1.2);
    phi.put("1-butanol", 1.0);
    phi.put("n-butanol", 1.0);
    // Glycols
    phi.put("MEG", 1.5);
    phi.put("DEG", 1.4);
    phi.put("TEG", 1.3);
    // Amines
    phi.put("MDEA", 1.5);
    phi.put("MEA", 1.7);
    phi.put("DEA", 1.5);
    // Carboxylic acids
    phi.put("acetic acid", 1.3);
    phi.put("formic acid", 1.6);
    return phi;
  }

  /**
   * Constructor for WilkeChangDiffusivity.
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public WilkeChangDiffusivity(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  /**
   * Get the association parameter for a solvent component.
   *
   * @param j the solvent component index
   * @return the association parameter phi (dimensionless)
   */
  private double getAssociationParameter(int j) {
    String name = liquidPhase.getPhase().getComponent(j).getComponentName().toLowerCase();
    Double phi = ASSOCIATION_PARAMS.get(name);
    if (phi != null) {
      return phi;
    }
    // Default for non-associated solvents (most hydrocarbons)
    return 1.0;
  }

  /**
   * Get the molar volume at normal boiling point for component i in cm^3/mol.
   *
   * <p>
   * Uses normal liquid density if available, otherwise estimates from critical volume using the
   * Tyn-Calus method: V_b = 0.285 * Vc^1.048.
   * </p>
   *
   * @param i the component index
   * @return molar volume at boiling point in cm^3/mol
   */
  private double getMolarVolumeAtBoilingPoint(int i) {
    double molarMassGperMol = liquidPhase.getPhase().getComponent(i).getMolarMass() * 1000.0; // g/mol
    double normalLiquidDensity = liquidPhase.getPhase().getComponent(i).getNormalLiquidDensity(); // g/cm^3

    if (normalLiquidDensity > 0 && !Double.isNaN(normalLiquidDensity)) {
      return molarMassGperMol / normalLiquidDensity;
    }

    // Estimate from critical volume using Tyn-Calus method
    // getCriticalVolume() returns cm^3/mol
    double Vc = liquidPhase.getPhase().getComponent(i).getCriticalVolume();
    if (Vc > 0) {
      return 0.285 * Math.pow(Vc, 1.048);
    }

    // Last resort: rough estimate from molar mass
    return Math.max(20.0, 0.285 * molarMassGperMol);
  }

  /** {@inheritDoc} */
  @Override
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    double T = liquidPhase.getPhase().getTemperature(); // K

    // Solvent molar mass in g/mol
    double MB = liquidPhase.getPhase().getComponent(j).getMolarMass() * 1000.0;

    // Association parameter for solvent
    double phi = getAssociationParameter(j);

    // Solute molar volume at normal boiling point [cm^3/mol]
    double VA = getMolarVolumeAtBoilingPoint(i);
    VA = Math.max(20.0, Math.min(VA, 600.0)); // Reasonable limits

    // Solvent viscosity in cP
    double etaCp = liquidPhase.getPureComponentViscosity(j);
    if (etaCp <= 0 || Double.isNaN(etaCp)) {
      // Fallback to mixture viscosity (Pa.s -> cP)
      double mixVisc = liquidPhase.getViscosity();
      etaCp = mixVisc * 1000.0;
    }
    etaCp = Math.max(0.01, Math.min(etaCp, 500.0));

    // Wilke-Chang equation:
    // D [cm^2/s] = 7.4e-8 * (phi * M_B)^0.5 * T / (eta_B * V_A^0.6)
    double D = 7.4e-8 * Math.pow(phi * MB, 0.5) * T / (etaCp * Math.pow(VA, 0.6));

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
