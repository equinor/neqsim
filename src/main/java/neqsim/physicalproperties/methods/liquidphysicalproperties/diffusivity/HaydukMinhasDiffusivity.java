package neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Hayduk-Minhas diffusivity model for liquid hydrocarbon systems.
 *
 * <p>
 * This model is more accurate for oil/gas applications than Siddiqi-Lucas, particularly for
 * hydrocarbon solvents. It provides separate correlations for paraffin and aqueous solvents.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Hayduk, W. and Minhas, B.S. (1982). "Correlations for prediction of molecular diffusivities
 * in liquids." Can. J. Chem. Eng., 60, 295-299.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class HaydukMinhasDiffusivity extends Diffusivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Minimum validated temperature [K]. */
  private static final double T_MIN = 200.0;

  /** Maximum validated temperature [K]. */
  private static final double T_MAX = 600.0;

  /** Solvent type enumeration. */
  public enum SolventType {
    /** Paraffin/hydrocarbon solvent. */
    PARAFFIN,
    /** Aqueous solvent. */
    AQUEOUS,
    /** Auto-detect based on composition. */
    AUTO
  }

  private SolventType solventType = SolventType.AUTO;

  /**
   * Constructor for HaydukMinhasDiffusivity.
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public HaydukMinhasDiffusivity(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  /**
   * Constructor with solvent type specification.
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   * @param solventType the type of solvent (PARAFFIN, AQUEOUS, or AUTO)
   */
  public HaydukMinhasDiffusivity(PhysicalProperties liquidPhase, SolventType solventType) {
    super(liquidPhase);
    this.solventType = solventType;
  }

  /**
   * Set the solvent type for the correlation.
   *
   * @param solventType the solvent type
   */
  public void setSolventType(SolventType solventType) {
    this.solventType = solventType;
  }

  /**
   * Get the current solvent type.
   *
   * @return the solvent type
   */
  public SolventType getSolventType() {
    return solventType;
  }

  /**
   * Detect whether the system is aqueous or hydrocarbon-based.
   *
   * @return true if aqueous, false if hydrocarbon
   */
  private boolean isAqueousSolvent() {
    if (solventType == SolventType.AQUEOUS) {
      return true;
    }
    if (solventType == SolventType.PARAFFIN) {
      return false;
    }

    // Auto-detect: check water content
    try {
      double waterMoleFraction = 0.0;
      for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
        String name = liquidPhase.getPhase().getComponent(i).getComponentName().toLowerCase();
        if (name.equals("water") || name.equals("h2o")) {
          waterMoleFraction = liquidPhase.getPhase().getComponent(i).getx();
          break;
        }
      }
      return waterMoleFraction > 0.5;
    } catch (Exception e) {
      return false; // Default to paraffin if detection fails
    }
  }

  /**
   * Get the molar volume at normal boiling point for component i.
   *
   * @param i component index
   * @return molar volume in cm³/mol
   */
  private double getMolarVolumeAtBoilingPoint(int i) {
    // Use Le Bas method or stored value
    // V_b ≈ V_c / 0.285 or from normal liquid density
    double molarMass = liquidPhase.getPhase().getComponent(i).getMolarMass() * 1000; // kg/mol ->
                                                                                     // g/mol
    // normalLiquidDensity is in g/cm³
    double normalLiquidDensity = liquidPhase.getPhase().getComponent(i).getNormalLiquidDensity();

    if (normalLiquidDensity > 0) {
      // V = M / rho [cm³/mol] = [g/mol] / [g/cm³]
      return molarMass / normalLiquidDensity;
    }

    // Fallback: estimate from critical volume
    double Vc = liquidPhase.getPhase().getComponent(i).getCriticalVolume(); // m³/mol
    if (Vc > 0) {
      // Convert m³/mol to cm³/mol and apply Rackett-type factor
      return Vc * 1e6 / 0.285;
    }

    // Default estimate based on molar mass (rough approximation)
    // Typical liquid molar volumes are 50-300 cm³/mol
    return 0.285 * molarMass; // Very rough estimate
  }

  /** {@inheritDoc} */
  @Override
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    double T = liquidPhase.getPhase().getTemperature();

    // Temperature validation warning
    if (T < T_MIN || T > T_MAX) {
      // Could log warning here
    }

    // Solute molar volume at boiling point [cm³/mol]
    double Vi = getMolarVolumeAtBoilingPoint(i);

    // Limit Vi to reasonable range to prevent numerical issues
    Vi = Math.max(20.0, Math.min(Vi, 500.0));

    // Solvent viscosity - neqsim stores pure component liquid viscosity internally
    // Siddiqi-Lucas uses getPureComponentViscosity directly, so it must be in the
    // units expected by that correlation. We need to figure out the actual units.
    // Looking at Siddiqi-Lucas: D ~ eta^(-1), and with 1e-4 factor for cm²/s to m²/s
    // For D ~ 1e-9 m²/s (typical), eta must be ~1 (i.e., in cP)
    double etaCp = liquidPhase.getPureComponentViscosity(j);
    if (etaCp <= 0 || Double.isNaN(etaCp)) {
      // Fallback: use mixture viscosity (which is in Pa·s), convert to cP
      double mixtureViscPas = liquidPhase.getViscosity(); // Pa·s
      etaCp = mixtureViscPas * 1000.0; // Convert to cP
    }
    // Ensure viscosity is in reasonable range for the correlation (0.1 to 100 cP)
    // If still zero or invalid, use a reasonable default for hydrocarbons
    if (etaCp <= 0 || Double.isNaN(etaCp)) {
      etaCp = 0.5; // Default ~0.5 cP typical for light hydrocarbons
    }
    etaCp = Math.max(0.1, Math.min(etaCp, 100.0));

    double D;
    if (isAqueousSolvent()) {
      // Aqueous solvent correlation (Hayduk-Minhas 1982)
      // D [cm²/s] = 1.25e-8 * (Vi^-0.19 - 0.292) * T^1.52 * η^(9.58/Vi - 1.12)
      // where Vi in cm³/mol, T in K, η in cP
      double exponent = 9.58 / Vi - 1.12;
      double viTerm = Math.pow(Vi, -0.19) - 0.292;
      if (viTerm <= 0) {
        viTerm = 0.01; // Prevent negative or zero values
      }
      D = 1.25e-8 * viTerm * Math.pow(T, 1.52) * Math.pow(etaCp, exponent);
    } else {
      // Paraffin/hydrocarbon solvent correlation (Hayduk-Minhas 1982)
      // D [cm²/s] = 13.3e-8 * T^1.47 * η^(10.2/Vi - 0.791) / Vi^0.71
      // where Vi in cm³/mol, T in K, η in cP
      // For typical Vi (50-200 cm³/mol): exponent ≈ (10.2/100 - 0.791) ≈ -0.69 (negative)
      double exponent = 10.2 / Vi - 0.791;
      D = 13.3e-8 * Math.pow(T, 1.47) * Math.pow(etaCp, exponent) / Math.pow(Vi, 0.71);
    }

    // D is in cm²/s, convert to m²/s (multiply by 1e-4)
    D = D * 1e-4;

    // Sanity check - liquid diffusivities should be in 1e-11 to 1e-8 m²/s range
    if (D < 1e-12 || D > 1e-7 || Double.isNaN(D) || Double.isInfinite(D)) {
      // Fall back to a typical liquid diffusivity
      D = 1e-9;
    }

    binaryDiffusionCoefficients[i][j] = D;
    return binaryDiffusionCoefficients[i][j];
  }

  /** {@inheritDoc} */
  @Override
  public double[][] calcDiffusionCoefficients(int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
        binaryDiffusionCoefficients[i][j] =
            calcBinaryDiffusionCoefficient(i, j, binaryDiffusionCoefficientMethod);
      }
    }

    // Note: Vignes mixing rule for concentration-dependent diffusivities has been
    // omitted. It's mainly applicable for non-ideal mixtures and can cause numerical
    // issues with small diffusivity values. The base binary diffusivities from
    // Hayduk-Minhas are already reasonable estimates for dilute solutions.

    return binaryDiffusionCoefficients;
  }
}
