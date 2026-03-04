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

    return calculateInternalFilmCoefficient(characteristicLength, wallTemperatureK,
        fluidTemperatureK, thermalConductivity, heatCapacity, dynamicViscosity, density, isVertical,
        beta);
  }

  /**
   * Calculates the internal heat transfer coefficient for natural convection inside a vessel.
   *
   * <p>
   * Overload that accepts an explicit thermal expansion coefficient for real-gas accuracy at high
   * pressures. At low pressures the ideal-gas approximation beta = 1/T is adequate, but at 100-350
   * bar the real-gas value (from the equation of state) can differ by 30-300 %, leading to a
   * proportional change in the Grashof and Rayleigh numbers and hence the Nusselt number.
   * </p>
   *
   * @param characteristicLength Characteristic length [m] (height for vertical vessels)
   * @param wallTemperatureK Wall temperature [K]
   * @param fluidTemperatureK Bulk fluid temperature [K]
   * @param thermalConductivity Fluid thermal conductivity [W/(m*K)]
   * @param heatCapacity Fluid heat capacity [J/(kg*K)]
   * @param dynamicViscosity Fluid dynamic viscosity [Pa*s]
   * @param density Fluid density [kg/m^3]
   * @param isVertical true for vertical vessel orientation
   * @param thermalExpansionCoeff Volumetric thermal expansion coefficient [1/K] (real-gas value:
   *        beta = -(1/rho)*(drho/dT)_P)
   * @return Internal film heat transfer coefficient [W/(m^2*K)]
   */
  public static double calculateInternalFilmCoefficient(double characteristicLength,
      double wallTemperatureK, double fluidTemperatureK, double thermalConductivity,
      double heatCapacity, double dynamicViscosity, double density, boolean isVertical,
      double thermalExpansionCoeff) {
    // Kinematic viscosity
    double nu = dynamicViscosity / density;

    // Calculate dimensionless numbers
    double Gr = calculateGrashofNumber(characteristicLength, fluidTemperatureK, wallTemperatureK,
        thermalExpansionCoeff, nu);
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
   * Calculates the Nusselt number for an axisymmetric impinging jet on a flat surface.
   *
   * <p>
   * Uses the Martin correlation for a single round nozzle (VDI Heat Atlas):
   * </p>
   *
   * <pre>
   * Nu = F(Re) * K(H/D, D/r) * Pr^0.42
   * F(Re) = 2 * Re^0.5 * (1 + 0.005 * Re^0.55)^0.5
   * K = (1 - 1.1 * D/r) / (1 + 0.1 * (H/D - 6) * D/r)
   * </pre>
   *
   * <p>
   * Valid for 2000 &lt; Re &lt; 400000, 2 &lt; H/D &lt; 12, 2.5 &lt; r/D &lt; 7.5. Outside these
   * ranges the result is clamped or falls back to Gnielinski.
   * </p>
   *
   * @param reynoldsNumber Reynolds number based on nozzle diameter and exit velocity
   * @param prandtlNumber Prandtl number
   * @param hOverD Nozzle-to-surface distance divided by nozzle diameter (H/D)
   * @param rOverD Radial distance from stagnation point divided by nozzle diameter (r/D); use
   *        vessel diameter / (2 * nozzle diameter) for area-average
   * @return Nusselt number (dimensionless) based on nozzle diameter
   */
  public static double calculateNusseltImpingingJet(double reynoldsNumber, double prandtlNumber,
      double hOverD, double rOverD) {
    // Clamp H/D and r/D to correlation validity range
    double hd = Math.max(2.0, Math.min(hOverD, 12.0));
    double rd = Math.max(2.5, Math.min(rOverD, 7.5));

    // Below Re 2000 fall back to Gnielinski (pipe-flow laminar/turbulent)
    if (reynoldsNumber < 2000.0) {
      return calculateNusseltForcedConvection(reynoldsNumber, prandtlNumber);
    }
    // Above Re 400000 cap at 400000 to avoid extrapolation
    double reClamped = Math.min(reynoldsNumber, 400000.0);

    double fRe = 2.0 * Math.sqrt(reClamped) * Math.sqrt(1.0 + 0.005 * Math.pow(reClamped, 0.55));
    double dOverR = 1.0 / rd;
    double kGeom = (1.0 - 1.1 * dOverR) / (1.0 + 0.1 * (hd - 6.0) * dOverR);
    if (kGeom < 0.1) {
      kGeom = 0.1; // Prevent non-physical negative values at extreme geometry
    }

    return fRe * kGeom * Math.pow(prandtlNumber, 0.42);
  }

  /**
   * Calculates the mixed convection heat transfer coefficient for vessel filling.
   *
   * <p>
   * During filling operations, both forced convection (from inlet jet) and natural convection
   * contribute to heat transfer. This method combines both effects using an asymptotic approach:
   *
   * <pre>
   * h_mixed = (h_forced ^ n + h_natural ^ n) ^ (1 / n)
   * </pre>
   *
   * where n is typically 3-4 for assisting flows.
   *
   * <p>
   * The forced convection component estimates a bulk circulation velocity from the inlet jet
   * momentum (area-ratio scaling) and uses the vessel diameter as the length scale. A 1.5x filling
   * enhancement factor accounts for the stronger mixing produced by an incoming jet compared to
   * discharge outflow.
   * </p>
   *
   * @param characteristicLength Characteristic length [m]
   * @param wallTemperatureK Wall temperature [K]
   * @param fluidTemperatureK Bulk fluid temperature [K]
   * @param massFlowRate Mass flow rate [kg/s]
   * @param inletDiameter Inlet/nozzle diameter [m]
   * @param vesselDiameter Vessel inner diameter [m]
   * @param thermalConductivity Fluid thermal conductivity [W/(m*K)]
   * @param heatCapacity Fluid heat capacity [J/(kg*K)]
   * @param dynamicViscosity Fluid dynamic viscosity [Pa*s]
   * @param density Fluid density [kg/m^3]
   * @param isVertical true for vertical vessel orientation
   * @return Mixed convection film coefficient [W/(m^2*K)]
   */
  public static double calculateMixedConvectionCoefficient(double characteristicLength,
      double wallTemperatureK, double fluidTemperatureK, double massFlowRate, double inletDiameter,
      double vesselDiameter, double thermalConductivity, double heatCapacity,
      double dynamicViscosity, double density, boolean isVertical) {
    // Natural convection component
    double hNatural =
        calculateInternalFilmCoefficient(characteristicLength, wallTemperatureK, fluidTemperatureK,
            thermalConductivity, heatCapacity, dynamicViscosity, density, isVertical);

    // Forced convection via momentum-based circulation velocity
    double inletArea = Math.PI * inletDiameter * inletDiameter / 4.0;
    double vesselArea = Math.PI * vesselDiameter * vesselDiameter / 4.0;
    double orificeVelocity = massFlowRate / (density * inletArea);
    // Filling enhancement: inlet jet creates ~1.5x stronger circulation than discharge
    double fillingEnhancement = 1.5;
    double circulationVelocity = orificeVelocity * inletArea / vesselArea * fillingEnhancement;

    double Re =
        calculateReynoldsNumber(circulationVelocity, vesselDiameter, density, dynamicViscosity);
    double Pr = calculatePrandtlNumber(heatCapacity, dynamicViscosity, thermalConductivity);
    double NuForced = calculateNusseltForcedConvection(Re, Pr);
    double hForced = NuForced * thermalConductivity / vesselDiameter;

    // Combine using asymptotic method (n=3 for assisting flow)
    double n = 3.0;
    double hMixed = Math.pow(Math.pow(hForced, n) + Math.pow(hNatural, n), 1.0 / n);

    return hMixed;
  }

  /**
   * Backward-compatible overload that assumes vesselDiameter equals characteristicLength.
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
   * @deprecated Use the overload that accepts vesselDiameter explicitly.
   */
  @Deprecated
  public static double calculateMixedConvectionCoefficient(double characteristicLength,
      double wallTemperatureK, double fluidTemperatureK, double massFlowRate, double inletDiameter,
      double thermalConductivity, double heatCapacity, double dynamicViscosity, double density,
      boolean isVertical) {
    return calculateMixedConvectionCoefficient(characteristicLength, wallTemperatureK,
        fluidTemperatureK, massFlowRate, inletDiameter, characteristicLength, thermalConductivity,
        heatCapacity, dynamicViscosity, density, isVertical);
  }

  /**
   * Calculates the mixed convection heat transfer coefficient for vessel filling with real-gas
   * thermal expansion coefficient.
   *
   * <p>
   * Same as
   * {@link #calculateMixedConvectionCoefficient(double, double, double, double, double, double, double, double, double, double, boolean)}
   * but uses the provided thermal expansion coefficient instead of the ideal-gas approximation beta
   * = 1/T.
   * </p>
   *
   * @param characteristicLength Characteristic length [m]
   * @param wallTemperatureK Wall temperature [K]
   * @param fluidTemperatureK Bulk fluid temperature [K]
   * @param massFlowRate Mass flow rate [kg/s]
   * @param inletDiameter Inlet/nozzle diameter [m]
   * @param vesselDiameter Vessel inner diameter [m]
   * @param thermalConductivity Fluid thermal conductivity [W/(m*K)]
   * @param heatCapacity Fluid heat capacity [J/(kg*K)]
   * @param dynamicViscosity Fluid dynamic viscosity [Pa*s]
   * @param density Fluid density [kg/m^3]
   * @param isVertical true for vertical vessel orientation
   * @param thermalExpansionCoeff Volumetric thermal expansion coefficient [1/K]
   * @return Mixed convection film coefficient [W/(m^2*K)]
   */
  public static double calculateMixedConvectionCoefficient(double characteristicLength,
      double wallTemperatureK, double fluidTemperatureK, double massFlowRate, double inletDiameter,
      double vesselDiameter, double thermalConductivity, double heatCapacity,
      double dynamicViscosity, double density, boolean isVertical, double thermalExpansionCoeff) {
    // Natural convection component with real-gas beta
    double hNatural = calculateInternalFilmCoefficient(characteristicLength, wallTemperatureK,
        fluidTemperatureK, thermalConductivity, heatCapacity, dynamicViscosity, density, isVertical,
        thermalExpansionCoeff);

    // Forced convection via momentum-based circulation velocity
    double inletArea = Math.PI * inletDiameter * inletDiameter / 4.0;
    double vesselArea = Math.PI * vesselDiameter * vesselDiameter / 4.0;
    double orificeVelocity = massFlowRate / (density * inletArea);
    double fillingEnhancement = 1.5;
    double circulationVelocity = orificeVelocity * inletArea / vesselArea * fillingEnhancement;

    double Re =
        calculateReynoldsNumber(circulationVelocity, vesselDiameter, density, dynamicViscosity);
    double Pr = calculatePrandtlNumber(heatCapacity, dynamicViscosity, thermalConductivity);
    double NuForced = calculateNusseltForcedConvection(Re, Pr);
    double hForced = NuForced * thermalConductivity / vesselDiameter;

    double n = 3.0;
    double hMixed = Math.pow(Math.pow(hForced, n) + Math.pow(hNatural, n), 1.0 / n);

    return hMixed;
  }

  /**
   * Calculates the mixed convection heat transfer coefficient during vessel discharge.
   *
   * <p>
   * During blowdown the outflow jet and resulting internal circulation augment heat transfer beyond
   * pure natural convection. The internal circulation velocity is estimated from the outflow
   * momentum. The forced and natural components are blended asymptotically.
   * </p>
   *
   * @param characteristicLength Characteristic length [m]
   * @param wallTemperatureK Wall temperature [K]
   * @param fluidTemperatureK Bulk fluid temperature [K]
   * @param massFlowRate Discharge mass flow rate (positive) [kg/s]
   * @param orificeDiameter Orifice / outlet nozzle diameter [m]
   * @param vesselDiameter Vessel inner diameter [m]
   * @param thermalConductivity Fluid thermal conductivity [W/(m*K)]
   * @param heatCapacity Fluid heat capacity [J/(kg*K)]
   * @param dynamicViscosity Fluid dynamic viscosity [Pa*s]
   * @param density Fluid density [kg/m^3]
   * @param isVertical true for vertical vessel orientation
   * @return Mixed convection film coefficient [W/(m^2*K)]
   */
  public static double calculateDischargeConvectionCoefficient(double characteristicLength,
      double wallTemperatureK, double fluidTemperatureK, double massFlowRate,
      double orificeDiameter, double vesselDiameter, double thermalConductivity,
      double heatCapacity, double dynamicViscosity, double density, boolean isVertical) {
    // Natural convection component
    double hNatural =
        calculateInternalFilmCoefficient(characteristicLength, wallTemperatureK, fluidTemperatureK,
            thermalConductivity, heatCapacity, dynamicViscosity, density, isVertical);

    // Estimate internal circulation velocity from momentum balance.
    // Orifice velocity:
    double orificeArea = Math.PI * orificeDiameter * orificeDiameter / 4.0;
    double orificeVelocity = Math.abs(massFlowRate) / (density * orificeArea);
    // Bulk circulation in vessel cross-section (momentum conservation, ~area ratio):
    double vesselArea = Math.PI * vesselDiameter * vesselDiameter / 4.0;
    double circulationVelocity = orificeVelocity * orificeArea / vesselArea;

    // Use vessel diameter as length scale for the internal circulation
    double Re =
        calculateReynoldsNumber(circulationVelocity, vesselDiameter, density, dynamicViscosity);
    double Pr = calculatePrandtlNumber(heatCapacity, dynamicViscosity, thermalConductivity);
    double NuForced = calculateNusseltForcedConvection(Re, Pr);
    double hForced = NuForced * thermalConductivity / vesselDiameter;

    // Asymptotic blend (n = 3)
    double n = 3.0;
    double hMixed = Math.pow(Math.pow(hForced, n) + Math.pow(hNatural, n), 1.0 / n);

    return hMixed;
  }

  /**
   * Calculates the mixed convection heat transfer coefficient during vessel discharge with real-gas
   * thermal expansion coefficient.
   *
   * @param characteristicLength Characteristic length [m]
   * @param wallTemperatureK Wall temperature [K]
   * @param fluidTemperatureK Bulk fluid temperature [K]
   * @param massFlowRate Discharge mass flow rate (positive) [kg/s]
   * @param orificeDiameter Orifice / outlet nozzle diameter [m]
   * @param vesselDiameter Vessel inner diameter [m]
   * @param thermalConductivity Fluid thermal conductivity [W/(m*K)]
   * @param heatCapacity Fluid heat capacity [J/(kg*K)]
   * @param dynamicViscosity Fluid dynamic viscosity [Pa*s]
   * @param density Fluid density [kg/m^3]
   * @param isVertical true for vertical vessel orientation
   * @param thermalExpansionCoeff Volumetric thermal expansion coefficient [1/K]
   * @return Mixed convection film coefficient [W/(m^2*K)]
   */
  public static double calculateDischargeConvectionCoefficient(double characteristicLength,
      double wallTemperatureK, double fluidTemperatureK, double massFlowRate,
      double orificeDiameter, double vesselDiameter, double thermalConductivity,
      double heatCapacity, double dynamicViscosity, double density, boolean isVertical,
      double thermalExpansionCoeff) {
    // Natural convection component with real-gas beta
    double hNatural = calculateInternalFilmCoefficient(characteristicLength, wallTemperatureK,
        fluidTemperatureK, thermalConductivity, heatCapacity, dynamicViscosity, density, isVertical,
        thermalExpansionCoeff);

    // Circulation velocity (same as ideal-gas version)
    double orificeArea = Math.PI * orificeDiameter * orificeDiameter / 4.0;
    double orificeVelocity = Math.abs(massFlowRate) / (density * orificeArea);
    double vesselArea = Math.PI * vesselDiameter * vesselDiameter / 4.0;
    double circulationVelocity = orificeVelocity * orificeArea / vesselArea;

    double Re =
        calculateReynoldsNumber(circulationVelocity, vesselDiameter, density, dynamicViscosity);
    double Pr = calculatePrandtlNumber(heatCapacity, dynamicViscosity, thermalConductivity);
    double NuForced = calculateNusseltForcedConvection(Re, Pr);
    double hForced = NuForced * thermalConductivity / vesselDiameter;

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

    // If wall is above saturation, check for nucleate boiling contribution.
    // Compute boiling heat flux via the Rohsenow correlation when enough liquid properties are
    // available; fall back to simplified estimate otherwise.
    double deltaT = wallTemperatureK - saturationTemperatureK;
    double hBoiling = 0.0;
    if (deltaT > 0) {
      // Use Rohsenow correlation. Default surface-fluid combination constants (water-steel).
      // Estimate missing properties from the values already passed in.
      double liquidPrandtl =
          calculatePrandtlNumber(heatCapacity, dynamicViscosity, thermalConductivity);
      // Approximate latent heat from Clausius-Clapeyron if not given; use 2.0 MJ/kg as
      // reasonable fallback for many fluids.
      double latentHeat = 2.0e6;
      // Rough surface tension ~ 0.02 N/m (hydrocarbon liquids / water at moderate T)
      double surfaceTension = 0.02;
      // Assume vapour density is small compared to liquid density
      double vaporDensity = density * 0.01;

      double qBoiling =
          calculateNucleateBoilingHeatFlux(wallTemperatureK, saturationTemperatureK, latentHeat,
              density, vaporDensity, dynamicViscosity, heatCapacity, surfaceTension, liquidPrandtl);

      if (qBoiling > 0.0 && deltaT > 0.0) {
        hBoiling = qBoiling / deltaT;
      }
      // If Rohsenow gives unreasonably small values (missing data), use simplified estimate
      if (hBoiling < 100.0) {
        hBoiling = Math.max(hBoiling, 1000.0 * Math.pow(deltaT, 0.3));
      }
    }

    // Return higher of the two, capped at 5000 W/(m^2*K) (raised from 3000 for stronger boiling)
    return Math.min(Math.max(hConv, hBoiling), 5000.0);
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
    double beta = 1.0 / filmTemperatureK; // ideal-gas fallback
    return calculateCompleteHeatTransfer(characteristicLength, wallTemperatureK, fluidTemperatureK,
        thermalConductivity, heatCapacity, dynamicViscosity, density, isVertical, beta);
  }

  /**
   * Performs a complete internal heat transfer calculation for a vessel with real-gas thermal
   * expansion coefficient.
   *
   * @param characteristicLength Characteristic length [m]
   * @param wallTemperatureK Wall temperature [K]
   * @param fluidTemperatureK Bulk fluid temperature [K]
   * @param thermalConductivity Fluid thermal conductivity [W/(m*K)]
   * @param heatCapacity Fluid heat capacity [J/(kg*K)]
   * @param dynamicViscosity Fluid dynamic viscosity [Pa*s]
   * @param density Fluid density [kg/m^3]
   * @param isVertical true for vertical vessel orientation
   * @param thermalExpansionCoeff Volumetric thermal expansion coefficient [1/K]
   * @return Complete heat transfer calculation result
   */
  public static HeatTransferResult calculateCompleteHeatTransfer(double characteristicLength,
      double wallTemperatureK, double fluidTemperatureK, double thermalConductivity,
      double heatCapacity, double dynamicViscosity, double density, boolean isVertical,
      double thermalExpansionCoeff) {
    double nu = dynamicViscosity / density;

    double Gr = calculateGrashofNumber(characteristicLength, fluidTemperatureK, wallTemperatureK,
        thermalExpansionCoeff, nu);
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
