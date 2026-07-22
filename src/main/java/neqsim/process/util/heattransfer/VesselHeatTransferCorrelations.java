package neqsim.process.util.heattransfer;

/**
 * Internal heat-transfer correlations for the gas-to-wall film coefficient inside pressure vessels.
 *
 * <p>
 * The class provides dimensionless correlations used by transient vessel models:
 * <ul>
 * <li><b>Natural convection</b> (Churchill-Chu) for the quiescent gas inside a vessel during blowdown /
 * depressurization, where the internal flow is buoyancy driven.</li>
 * <li><b>Mixed convection during filling</b> (Woodfield et al.) for a vessel being charged through an inlet jet, where
 * the jet-induced forced convection and the buoyancy both contribute.</li>
 * </ul>
 *
 * <p>
 * All methods are static and operate purely on dimensionless groups and SI fluid properties, so they are decoupled from
 * the NeqSim thermodynamic objects and can be reused by any transient model.
 *
 * <p>
 * <b>References:</b> Churchill and Chu (1975), Int. J. Heat Mass Transfer 18, 1323; Woodfield, Monde and Mitsutake
 * (2007), "Measurement of averaged heat transfer coefficients in high-pressure vessel during charging with hydrogen,
 * nitrogen or argon gas", J. Therm. Sci. Tech. 2(2), 180.
 *
 * @author ESOL
 * @version 1.0
 */
public final class VesselHeatTransferCorrelations {

  /** Standard acceleration of gravity in m/s^2. */
  public static final double GRAVITY = 9.80665;

  private VesselHeatTransferCorrelations() {
  }

  /**
   * Rayleigh number for natural convection.
   *
   * <p>
   * {@code Ra = g * beta * |dT| * L^3 / (nu * alpha)}
   *
   * @param thermalExpansionCoeff isobaric thermal expansion coefficient beta in 1/K; for an ideal gas this is 1/T
   * @param temperatureDifferenceK absolute temperature difference between surface and bulk in K
   * @param characteristicLengthM characteristic length L in m
   * @param kinematicViscosity kinematic viscosity nu in m^2/s; must be positive
   * @param thermalDiffusivity thermal diffusivity alpha in m^2/s; must be positive
   * @return Rayleigh number (dimensionless, non-negative)
   * @throws IllegalArgumentException if a transport property is not positive
   */
  public static double rayleigh(double thermalExpansionCoeff, double temperatureDifferenceK,
      double characteristicLengthM, double kinematicViscosity, double thermalDiffusivity) {
    if (kinematicViscosity <= 0.0 || thermalDiffusivity <= 0.0) {
      throw new IllegalArgumentException("kinematicViscosity and thermalDiffusivity must be positive");
    }
    if (characteristicLengthM <= 0.0) {
      throw new IllegalArgumentException("characteristicLengthM must be positive");
    }
    double ra = GRAVITY * thermalExpansionCoeff * Math.abs(temperatureDifferenceK)
        * Math.pow(characteristicLengthM, 3.0) / (kinematicViscosity * thermalDiffusivity);
    return ra > 0.0 ? ra : 0.0;
  }

  /**
   * Reynolds number of the inlet jet during vessel filling.
   *
   * <p>
   * {@code Re = rho * v * d / mu}
   *
   * @param density gas density in kg/m^3; must be positive
   * @param velocity inlet jet velocity in m/s
   * @param diameterM inlet nozzle diameter in m; must be positive
   * @param dynamicViscosity dynamic viscosity mu in Pa.s; must be positive
   * @return Reynolds number (dimensionless, non-negative)
   * @throws IllegalArgumentException if a property is not positive
   */
  public static double reynolds(double density, double velocity, double diameterM, double dynamicViscosity) {
    if (density <= 0.0 || diameterM <= 0.0 || dynamicViscosity <= 0.0) {
      throw new IllegalArgumentException("density, diameterM and dynamicViscosity must be positive");
    }
    double re = density * Math.abs(velocity) * diameterM / dynamicViscosity;
    return re > 0.0 ? re : 0.0;
  }

  /**
   * Churchill-Chu Nusselt number for natural convection on a vertical surface, valid over the full Rayleigh range.
   *
   * <p>
   * {@code Nu = (0.825 + 0.387 * Ra^(1/6) / [1 + (0.492/Pr)^(9/16)]^(8/27))^2}
   *
   * @param rayleigh Rayleigh number; must be non-negative
   * @param prandtl Prandtl number; must be positive
   * @return Nusselt number (dimensionless)
   * @throws IllegalArgumentException if inputs are out of range
   */
  public static double churchillChuNusselt(double rayleigh, double prandtl) {
    if (rayleigh < 0.0) {
      throw new IllegalArgumentException("rayleigh must be non-negative");
    }
    if (prandtl <= 0.0) {
      throw new IllegalArgumentException("prandtl must be positive");
    }
    double denom = Math.pow(1.0 + Math.pow(0.492 / prandtl, 9.0 / 16.0), 8.0 / 27.0);
    double base = 0.825 + 0.387 * Math.pow(rayleigh, 1.0 / 6.0) / denom;
    return base * base;
  }

  /**
   * Woodfield mixed-convection Nusselt number for a vessel being charged (filled) with gas.
   *
   * <p>
   * {@code Nu = 0.56 * Re_d^0.67 + 0.104 * Ra_H^0.352}
   *
   * <p>
   * where {@code Re_d} is the inlet-jet Reynolds number based on the nozzle diameter and {@code Ra_H} is the Rayleigh
   * number based on the vessel internal height. The correlation was derived from charging experiments with hydrogen,
   * nitrogen and argon.
   *
   * @param reynoldsInletJet inlet-jet Reynolds number based on nozzle diameter; must be non-negative
   * @param rayleighVesselHeight Rayleigh number based on vessel internal height; must be non-negative
   * @return Nusselt number (dimensionless)
   * @throws IllegalArgumentException if an input is negative
   */
  public static double woodfieldFillingNusselt(double reynoldsInletJet, double rayleighVesselHeight) {
    if (reynoldsInletJet < 0.0 || rayleighVesselHeight < 0.0) {
      throw new IllegalArgumentException("Reynolds and Rayleigh numbers must be non-negative");
    }
    return 0.56 * Math.pow(reynoldsInletJet, 0.67) + 0.104 * Math.pow(rayleighVesselHeight, 0.352);
  }

  /**
   * Convective heat-transfer coefficient from a Nusselt number.
   *
   * <p>
   * {@code h = Nu * k / L}
   *
   * @param nusselt Nusselt number; must be non-negative
   * @param thermalConductivity gas thermal conductivity k in W/(m.K); must be positive
   * @param characteristicLengthM characteristic length L in m; must be positive
   * @return heat-transfer coefficient in W/(m^2.K)
   * @throws IllegalArgumentException if inputs are out of range
   */
  public static double heatTransferCoefficient(double nusselt, double thermalConductivity,
      double characteristicLengthM) {
    if (nusselt < 0.0) {
      throw new IllegalArgumentException("nusselt must be non-negative");
    }
    if (thermalConductivity <= 0.0 || characteristicLengthM <= 0.0) {
      throw new IllegalArgumentException("thermalConductivity and characteristicLengthM must be positive");
    }
    return nusselt * thermalConductivity / characteristicLengthM;
  }

  /**
   * Rohsenow nucleate pool-boiling heat flux at a wetted, fire-heated wall.
   *
   * <p>
   * {@code q'' = mu_l * h_fg * sqrt(g * (rho_l - rho_v) / sigma) * [ cp_l * dTe / (Csf * h_fg * Pr_l^s) ]^3 } where
   * {@code dTe = T_wall - T_sat} is the wall superheat. A wall superheat of zero or below returns zero flux. The
   * Prandtl exponent {@code s} is 1.0 for water and 1.7 for other fluids per Rohsenow's original recommendation.
   * </p>
   *
   * @param liquidDynamicViscosity liquid dynamic viscosity in Pa·s; must be positive
   * @param latentHeat latent heat of vaporization in J/kg; must be positive
   * @param liquidDensity saturated-liquid density in kg/m³; must be positive
   * @param vaporDensity saturated-vapor density in kg/m³; must be non-negative and below the liquid density
   * @param surfaceTension liquid-vapor surface tension in N/m; must be positive
   * @param liquidSpecificHeat liquid specific heat at constant pressure in J/(kg·K); must be positive
   * @param wallSuperheatK wall superheat {@code T_wall - T_sat} in K
   * @param prandtlLiquid liquid Prandtl number; must be positive
   * @param surfaceFluidConstant Rohsenow surface-fluid constant Csf (typically 0.006 to 0.013); must be positive
   * @param prandtlExponent Prandtl exponent s (1.0 for water, 1.7 otherwise); must be positive
   * @return nucleate boiling heat flux in W/m²
   * @throws IllegalArgumentException if any argument is out of range
   */
  public static double rohsenowNucleateBoilingHeatFlux(double liquidDynamicViscosity, double latentHeat,
      double liquidDensity, double vaporDensity, double surfaceTension, double liquidSpecificHeat,
      double wallSuperheatK, double prandtlLiquid, double surfaceFluidConstant, double prandtlExponent) {
    if (liquidDynamicViscosity <= 0.0 || latentHeat <= 0.0 || liquidDensity <= 0.0 || surfaceTension <= 0.0
        || liquidSpecificHeat <= 0.0 || prandtlLiquid <= 0.0 || surfaceFluidConstant <= 0.0 || prandtlExponent <= 0.0) {
      throw new IllegalArgumentException("Rohsenow inputs must be positive");
    }
    if (vaporDensity < 0.0 || vaporDensity >= liquidDensity) {
      throw new IllegalArgumentException("vaporDensity must be non-negative and below liquidDensity");
    }
    if (wallSuperheatK <= 0.0) {
      return 0.0;
    }
    double buoyancy = Math.sqrt(GRAVITY * (liquidDensity - vaporDensity) / surfaceTension);
    double bracket = liquidSpecificHeat * wallSuperheatK
        / (surfaceFluidConstant * latentHeat * Math.pow(prandtlLiquid, prandtlExponent));
    return liquidDynamicViscosity * latentHeat * buoyancy * Math.pow(bracket, 3.0);
  }
}
