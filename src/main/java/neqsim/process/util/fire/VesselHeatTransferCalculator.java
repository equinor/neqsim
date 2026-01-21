package neqsim.process.util.fire;

/**
 * Calculates internal heat transfer coefficients for vessel filling and depressurization.
 *
 * <p>
 * This class provides natural convection, forced convection, and mixed convection correlations for
 * calculating heat transfer between gas/liquid and vessel walls during dynamic operations. The
 * correlations are based on experimental validation from hydrogen vessel studies and are applicable
 * to various gases.
 * </p>
 *
 * <p>
 * The implementation follows the approach used in HydDown and validated against experimental data
 * from Woodfield et al. for hydrogen vessel filling and depressurization.
 * </p>
 *
 * <p>
 * Reference: Andreasen, A. (2021). HydDown: A Python package for calculation of hydrogen (or other
 * gas) pressure vessel filling and discharge. Journal of Open Source Software, 6(66), 3695.
 * </p>
 *
 * @author ESOL
 * @see <a href="https://doi.org/10.21105/joss.03695">HydDown - JOSS Paper</a>
 * @see <a href="https://doi.org/10.1299/jtst.3.241">Woodfield et al. - Heat Transfer in H2
 *      Vessels</a>
 */
public final class VesselHeatTransferCalculator {

  private VesselHeatTransferCalculator() {}

  /** Gravitational acceleration [m/s^2]. */
  public static final double GRAVITY = 9.81;

  /**
   * Calculates the Grashof number for natural convection.
   *
   * <p>
   * The Grashof number represents the ratio of buoyancy to viscous forces:
   * 
   * <pre>
   * Gr = g * beta * |Twall - Tfluid| * L^3 / nu^2
   * </pre>
   *
   * @param characteristicLength Characteristic length [m] (height for vertical, diameter for
   *        horizontal)
   * @param fluidTemperatureK Bulk fluid temperature [K]
   * @param wallTemperatureK Wall surface temperature [K]
   * @param thermalExpansionCoeff Thermal expansion coefficient [1/K], typically 1/T for ideal gas
   * @param kinematicViscosity Kinematic viscosity [m^2/s]
   * @return Grashof number (dimensionless)
   */
  public static double calculateGrashofNumber(double characteristicLength, double fluidTemperatureK,
      double wallTemperatureK, double thermalExpansionCoeff, double kinematicViscosity) {
    if (characteristicLength <= 0.0 || kinematicViscosity <= 0.0) {
      throw new IllegalArgumentException(
          "Characteristic length and kinematic viscosity must be positive");
    }
    if (fluidTemperatureK <= 0.0 || wallTemperatureK <= 0.0) {
      throw new IllegalArgumentException("Temperatures must be positive Kelvin values");
    }

    double deltaT = Math.abs(wallTemperatureK - fluidTemperatureK);
    double L3 = characteristicLength * characteristicLength * characteristicLength;
    double nu2 = kinematicViscosity * kinematicViscosity;

    return GRAVITY * thermalExpansionCoeff * deltaT * L3 / nu2;
  }

  /**
   * Calculates the Prandtl number.
   *
   * <p>
   * The Prandtl number represents the ratio of momentum diffusivity to thermal diffusivity:
   * 
   * <pre>
   * Pr = Cp * mu / k = nu / alpha
   * </pre>
   *
   * @param heatCapacity Specific heat capacity at constant pressure [J/(kg*K)]
   * @param dynamicViscosity Dynamic viscosity [Pa*s]
   * @param thermalConductivity Thermal conductivity [W/(m*K)]
   * @return Prandtl number (dimensionless)
   */
  public static double calculatePrandtlNumber(double heatCapacity, double dynamicViscosity,
      double thermalConductivity) {
    if (heatCapacity <= 0.0 || dynamicViscosity <= 0.0 || thermalConductivity <= 0.0) {
      throw new IllegalArgumentException(
          "Heat capacity, viscosity, and thermal conductivity must be positive");
    }
    return heatCapacity * dynamicViscosity / thermalConductivity;
  }

  /**
   * Calculates the Rayleigh number.
   *
   * <p>
   * The Rayleigh number is the product of Grashof and Prandtl numbers:
   * 
   * <pre>
   * Ra = Gr * Pr
   * </pre>
   *
   * @param grashofNumber Grashof number (dimensionless)
   * @param prandtlNumber Prandtl number (dimensionless)
   * @return Rayleigh number (dimensionless)
   */
  public static double calculateRayleighNumber(double grashofNumber, double prandtlNumber) {
    return grashofNumber * prandtlNumber;
  }

  /**
   * Calculates the Nusselt number for natural convection from a vertical surface.
   *
   * <p>
   * Uses the Churchill-Chu correlation valid for all Ra:
   * 
   * <pre>
   * Nu = {0.825 + 0.387 * Ra^(1/6) / [1 + (0.492/Pr)^(9/16)]^(8/27)}^2
   * </pre>
   *
   * This correlation is valid for the full range of Rayleigh numbers and is suitable for vessel
   * walls.
   *
   * @param rayleighNumber Rayleigh number (dimensionless)
   * @param prandtlNumber Prandtl number (dimensionless)
   * @return Nusselt number (dimensionless)
   */
  public static double calculateNusseltVerticalSurface(double rayleighNumber,
      double prandtlNumber) {
    if (rayleighNumber < 0.0 || prandtlNumber <= 0.0) {
      throw new IllegalArgumentException(
          "Rayleigh number must be non-negative and Prandtl number must be positive");
    }

    // Churchill-Chu correlation for vertical surfaces
    double prTerm = Math.pow(0.492 / prandtlNumber, 9.0 / 16.0);
    double denominator = Math.pow(1.0 + prTerm, 8.0 / 27.0);
    double raTerm = 0.387 * Math.pow(rayleighNumber, 1.0 / 6.0) / denominator;
    double nuBase = 0.825 + raTerm;

    return nuBase * nuBase;
  }

  /**
   * Calculates the Nusselt number for natural convection from a horizontal cylinder.
   *
   * <p>
   * Uses the Churchill-Chu correlation for horizontal cylinders:
   * 
   * <pre>
   * Nu = {0.60 + 0.387 * Ra^(1/6) / [1 + (0.559/Pr)^(9/16)]^(8/27)}^2
   * </pre>
   *
   * Valid for Ra &lt; 10^12.
   *
   * @param rayleighNumber Rayleigh number based on diameter (dimensionless)
   * @param prandtlNumber Prandtl number (dimensionless)
   * @return Nusselt number (dimensionless)
   */
  public static double calculateNusseltHorizontalCylinder(double rayleighNumber,
      double prandtlNumber) {
    if (rayleighNumber < 0.0 || prandtlNumber <= 0.0) {
      throw new IllegalArgumentException(
          "Rayleigh number must be non-negative and Prandtl number must be positive");
    }

    // Churchill-Chu correlation for horizontal cylinders
    double prTerm = Math.pow(0.559 / prandtlNumber, 9.0 / 16.0);
    double denominator = Math.pow(1.0 + prTerm, 8.0 / 27.0);
    double raTerm = 0.387 * Math.pow(rayleighNumber, 1.0 / 6.0) / denominator;
    double nuBase = 0.60 + raTerm;

    return nuBase * nuBase;
  }

  /**
   * Calculates the internal heat transfer coefficient for natural convection inside a vessel.
   *
   * <p>
   * This method calculates the film coefficient based on natural convection for gas-phase heat
   * transfer during depressurization. Uses fluid properties at the film temperature (average of
   * wall and bulk temperatures).
   *
   * @param characteristicLength Characteristic length [m] (height for vertical vessels)
   * @param wallTemperatureK Wall temperature [K]
   * @param fluidTemperatureK Bulk fluid temperature [K]
   * @param thermalConductivity Fluid thermal conductivity [W/(m*K)]
   * @param heatCapacity Fluid heat capacity [J/(kg*K)]
   * @param dynamicViscosity Fluid dynamic viscosity [Pa*s]
   * @param density Fluid density [kg/m^3]
   * @param isVertical true for vertical vessel orientation
   * @return Internal film heat transfer coefficient [W/(m^2*K)]
   */
  public static double calculateInternalFilmCoefficient(double characteristicLength,
      double wallTemperatureK, double fluidTemperatureK, double thermalConductivity,
      double heatCapacity, double dynamicViscosity, double density, boolean isVertical) {
    // Use film temperature for property evaluation
    double filmTemperatureK = (wallTemperatureK + fluidTemperatureK) / 2.0;

    // For ideal gas, beta = 1/T
    double beta = 1.0 / filmTemperatureK;

    // Kinematic viscosity
    double nu = dynamicViscosity / density;

    // Calculate dimensionless numbers
    double Gr =
        calculateGrashofNumber(characteristicLength, fluidTemperatureK, wallTemperatureK, beta, nu);
    double Pr = calculatePrandtlNumber(heatCapacity, dynamicViscosity, thermalConductivity);
    double Ra = calculateRayleighNumber(Gr, Pr);

    // Calculate Nusselt number based on orientation
    double Nu;
    if (isVertical) {
      Nu = calculateNusseltVerticalSurface(Ra, Pr);
    } else {
      Nu = calculateNusseltHorizontalCylinder(Ra, Pr);
    }

    // Heat transfer coefficient: h = Nu * k / L
    return Nu * thermalConductivity / characteristicLength;
  }

  /**
   * Calculates the Reynolds number for forced convection.
   *
   * <p>
   * Re = rho * v * D / mu = v * D / nu
   *
   * @param velocity Flow velocity [m/s]
   * @param characteristicLength Characteristic length (diameter) [m]
   * @param density Fluid density [kg/m^3]
   * @param dynamicViscosity Dynamic viscosity [Pa*s]
   * @return Reynolds number (dimensionless)
   */
  public static double calculateReynoldsNumber(double velocity, double characteristicLength,
      double density, double dynamicViscosity) {
    if (characteristicLength <= 0.0 || density <= 0.0 || dynamicViscosity <= 0.0) {
      throw new IllegalArgumentException("Length, density, and viscosity must be positive");
    }
    return density * Math.abs(velocity) * characteristicLength / dynamicViscosity;
  }

  /**
   * Calculates Nusselt number for forced convection in a pipe/vessel entry region.
   *
   * <p>
   * Uses the Gnielinski correlation for turbulent flow (Re &gt; 2300):
   * 
   * <pre>
   * Nu = (f/8) * (Re - 1000) * Pr / [1 + 12.7 * sqrt(f/8) * (Pr^(2/3) - 1)]
   * </pre>
   *
   * where f is the Darcy friction factor.
   *
   * @param reynoldsNumber Reynolds number (dimensionless)
   * @param prandtlNumber Prandtl number (dimensionless)
   * @return Nusselt number (dimensionless)
   */
  public static double calculateNusseltForcedConvection(double reynoldsNumber,
      double prandtlNumber) {
    if (reynoldsNumber < 0.0 || prandtlNumber <= 0.0) {
      throw new IllegalArgumentException(
          "Reynolds must be non-negative and Prandtl must be positive");
    }

    // Laminar flow
    if (reynoldsNumber < 2300) {
      return 3.66; // Fully developed laminar flow in a tube
    }

    // Darcy friction factor for smooth tubes (Petukhov correlation)
    double f = Math.pow(0.790 * Math.log(reynoldsNumber) - 1.64, -2.0);

    // Gnielinski correlation
    double numerator = (f / 8.0) * (reynoldsNumber - 1000.0) * prandtlNumber;
    double denominator =
        1.0 + 12.7 * Math.sqrt(f / 8.0) * (Math.pow(prandtlNumber, 2.0 / 3.0) - 1.0);

    return numerator / denominator;
  }

  /**
   * Calculates the mixed convection heat transfer coefficient for vessel filling.
   *
   * <p>
   * During filling operations, both forced convection (from inlet jet) and natural convection
   * contribute to heat transfer. This method combines both effects using an asymptotic approach:
   * 
   * <pre>
   * Nu_mixed = (Nu_forced ^ n + Nu_natural ^ n) ^ (1 / n)
   * </pre>
   *
   * where n is typically 3-4 for assisting flows.
   *
   * @param characteristicLength Characteristic length [m]
   * @param wallTemperatureK Wall temperature [K]
   * @param fluidTemperatureK Bulk fluid temperature [K]
   * @param massFlowRate Mass flow rate [kg/s]
   * @param inletDiameter Inlet/nozzle diameter [m]
   * @param thermalConductivity Fluid thermal conductivity [W/(m*K)]
   * @param heatCapacity Fluid heat capacity [J/(kg*K)]
   * @param dynamicViscosity Fluid dynamic viscosity [Pa*s]
   * @param density Fluid density [kg/m^3]
   * @param isVertical true for vertical vessel orientation
   * @return Mixed convection film coefficient [W/(m^2*K)]
   */
  public static double calculateMixedConvectionCoefficient(double characteristicLength,
      double wallTemperatureK, double fluidTemperatureK, double massFlowRate, double inletDiameter,
      double thermalConductivity, double heatCapacity, double dynamicViscosity, double density,
      boolean isVertical) {
    // Natural convection component
    double hNatural =
        calculateInternalFilmCoefficient(characteristicLength, wallTemperatureK, fluidTemperatureK,
            thermalConductivity, heatCapacity, dynamicViscosity, density, isVertical);

    // Forced convection component
    double inletArea = Math.PI * inletDiameter * inletDiameter / 4.0;
    double velocity = massFlowRate / (density * inletArea);

    double Re = calculateReynoldsNumber(velocity, inletDiameter, density, dynamicViscosity);
    double Pr = calculatePrandtlNumber(heatCapacity, dynamicViscosity, thermalConductivity);
    double NuForced = calculateNusseltForcedConvection(Re, Pr);
    double hForced = NuForced * thermalConductivity / inletDiameter;

    // Combine using asymptotic method (n=3 for assisting flow)
    double n = 3.0;
    double hMixed = Math.pow(Math.pow(hForced, n) + Math.pow(hNatural, n), 1.0 / n);

    return hMixed;
  }

  /**
   * Calculates the heat transfer coefficient for nucleate boiling (wetted wall with liquid).
   *
   * <p>
   * Uses the Rohsenow correlation for nucleate pool boiling:
   * 
   * <pre>
   * q = mu_l * h_fg * [g * (rho_l - rho_v) / sigma]^0.5 * [Cp_l * (Twall - Tsat) / (Csf * h_fg * Pr^n)]^3
   * </pre>
   *
   * @param wallTemperatureK Wall temperature [K]
   * @param saturationTemperatureK Saturation temperature [K]
   * @param latentHeat Latent heat of vaporization [J/kg]
   * @param liquidDensity Liquid density [kg/m^3]
   * @param vaporDensity Vapor density [kg/m^3]
   * @param liquidViscosity Liquid dynamic viscosity [Pa*s]
   * @param liquidCp Liquid heat capacity [J/(kg*K)]
   * @param surfaceTension Surface tension [N/m]
   * @param liquidPrandtl Liquid Prandtl number
   * @return Heat flux [W/m^2] or 0 if wall is below saturation temperature
   */
  public static double calculateNucleateBoilingHeatFlux(double wallTemperatureK,
      double saturationTemperatureK, double latentHeat, double liquidDensity, double vaporDensity,
      double liquidViscosity, double liquidCp, double surfaceTension, double liquidPrandtl) {
    double deltaT = wallTemperatureK - saturationTemperatureK;
    if (deltaT <= 0) {
      return 0.0; // No boiling if wall is at or below saturation
    }

    // Csf and n for water-steel interface (typical values)
    double Csf = 0.013;
    double n = 1.7;

    double densityDiff = liquidDensity - vaporDensity;
    if (densityDiff <= 0) {
      return 0.0;
    }

    double sqrtTerm = Math.sqrt(GRAVITY * densityDiff / surfaceTension);
    double prTerm = Math.pow(liquidPrandtl, n);
    double cpTerm = liquidCp * deltaT / (Csf * latentHeat * prTerm);
    double cpTerm3 = cpTerm * cpTerm * cpTerm;

    return liquidViscosity * latentHeat * sqrtTerm * cpTerm3;
  }

  /**
   * Estimates the internal film coefficient for wetted (liquid contact) vessel wall.
   *
   * <p>
   * For wetted walls with boiling liquid, this method combines natural convection and nucleate
   * boiling effects to give an effective heat transfer coefficient.
   *
   * @param wallTemperatureK Wall temperature [K]
   * @param fluidTemperatureK Bulk liquid temperature [K]
   * @param saturationTemperatureK Saturation temperature [K]
   * @param characteristicLength Characteristic length [m]
   * @param thermalConductivity Liquid thermal conductivity [W/(m*K)]
   * @param heatCapacity Liquid heat capacity [J/(kg*K)]
   * @param dynamicViscosity Liquid dynamic viscosity [Pa*s]
   * @param density Liquid density [kg/m^3]
   * @param isVertical true for vertical vessel orientation
   * @return Internal film coefficient for wetted wall [W/(m^2*K)]
   */
  public static double calculateWettedWallFilmCoefficient(double wallTemperatureK,
      double fluidTemperatureK, double saturationTemperatureK, double characteristicLength,
      double thermalConductivity, double heatCapacity, double dynamicViscosity, double density,
      boolean isVertical) {
    // Natural convection in liquid
    double hConv =
        calculateInternalFilmCoefficient(characteristicLength, wallTemperatureK, fluidTemperatureK,
            thermalConductivity, heatCapacity, dynamicViscosity, density, isVertical);

    // If wall is significantly above saturation, boiling dominates
    // Use higher of convection or an estimated boiling coefficient
    // For simplicity, wetted walls typically have h = 500-3000 W/(m^2*K) for boiling
    double deltaT = wallTemperatureK - saturationTemperatureK;
    double hBoiling = 0.0;
    if (deltaT > 0) {
      // Simplified boiling estimate: h_boil ~ 1000 * deltaT^0.3 for moderate superheat
      hBoiling = 1000.0 * Math.pow(deltaT, 0.3);
    }

    // Return higher of the two, capped at 3000 W/(m^2*K) for safety
    return Math.min(Math.max(hConv, hBoiling), 3000.0);
  }

  /**
   * Container for complete heat transfer calculation results.
   */
  public static final class HeatTransferResult {
    private final double grashofNumber;
    private final double prandtlNumber;
    private final double rayleighNumber;
    private final double nusseltNumber;
    private final double filmCoefficient;
    private final double heatFlux;

    /**
     * Creates a heat transfer result container.
     *
     * @param grashofNumber Grashof number
     * @param prandtlNumber Prandtl number
     * @param rayleighNumber Rayleigh number
     * @param nusseltNumber Nusselt number
     * @param filmCoefficient Film coefficient [W/(m^2*K)]
     * @param heatFlux Heat flux [W/m^2]
     */
    public HeatTransferResult(double grashofNumber, double prandtlNumber, double rayleighNumber,
        double nusseltNumber, double filmCoefficient, double heatFlux) {
      this.grashofNumber = grashofNumber;
      this.prandtlNumber = prandtlNumber;
      this.rayleighNumber = rayleighNumber;
      this.nusseltNumber = nusseltNumber;
      this.filmCoefficient = filmCoefficient;
      this.heatFlux = heatFlux;
    }

    public double getGrashofNumber() {
      return grashofNumber;
    }

    public double getPrandtlNumber() {
      return prandtlNumber;
    }

    public double getRayleighNumber() {
      return rayleighNumber;
    }

    public double getNusseltNumber() {
      return nusseltNumber;
    }

    public double getFilmCoefficient() {
      return filmCoefficient;
    }

    public double getHeatFlux() {
      return heatFlux;
    }
  }

  /**
   * Performs a complete internal heat transfer calculation for a vessel.
   *
   * @param characteristicLength Characteristic length [m]
   * @param wallTemperatureK Wall temperature [K]
   * @param fluidTemperatureK Bulk fluid temperature [K]
   * @param thermalConductivity Fluid thermal conductivity [W/(m*K)]
   * @param heatCapacity Fluid heat capacity [J/(kg*K)]
   * @param dynamicViscosity Fluid dynamic viscosity [Pa*s]
   * @param density Fluid density [kg/m^3]
   * @param isVertical true for vertical vessel orientation
   * @return Complete heat transfer calculation result
   */
  public static HeatTransferResult calculateCompleteHeatTransfer(double characteristicLength,
      double wallTemperatureK, double fluidTemperatureK, double thermalConductivity,
      double heatCapacity, double dynamicViscosity, double density, boolean isVertical) {
    double filmTemperatureK = (wallTemperatureK + fluidTemperatureK) / 2.0;
    double beta = 1.0 / filmTemperatureK;
    double nu = dynamicViscosity / density;

    double Gr =
        calculateGrashofNumber(characteristicLength, fluidTemperatureK, wallTemperatureK, beta, nu);
    double Pr = calculatePrandtlNumber(heatCapacity, dynamicViscosity, thermalConductivity);
    double Ra = calculateRayleighNumber(Gr, Pr);

    double Nu;
    if (isVertical) {
      Nu = calculateNusseltVerticalSurface(Ra, Pr);
    } else {
      Nu = calculateNusseltHorizontalCylinder(Ra, Pr);
    }

    double h = Nu * thermalConductivity / characteristicLength;
    double q = h * (wallTemperatureK - fluidTemperatureK);

    return new HeatTransferResult(Gr, Pr, Ra, Nu, h, q);
  }
}
