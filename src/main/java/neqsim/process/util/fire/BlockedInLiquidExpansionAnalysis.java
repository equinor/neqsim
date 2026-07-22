package neqsim.process.util.fire;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Rigorous, equation-of-state based screening tool for the pressure rise generated when a liquid-full piping segment or
 * vessel is blocked in (isolated with no vapor space and no relief path) and subsequently warmed, per API 521 7th Ed.
 * Section 4.4.12 ("Liquid Thermal Expansion").
 *
 * <p>
 * Blocked-in liquid thermal expansion is one of the most severe overpressure scenarios in process plants: because
 * liquids are nearly incompressible, even a modest temperature rise can produce extreme pressure increases unless a
 * thermal relief device is installed. The simplified screening relation
 * </p>
 *
 * <p>
 * {@code dP = (beta / kappa) * dT}
 * </p>
 *
 * <p>
 * (where {@code beta} is the isobaric thermal expansion coefficient and {@code kappa} is the isothermal
 * compressibility) is only strictly valid for an infinitesimal temperature step at fixed reference properties. This
 * class supplements that simplified relation with a rigorous isochoric (constant fluid density, i.e. constant volume
 * for a fixed mass and fixed pipe/vessel volume) pressure march that re-flashes the fluid at each temperature step and
 * searches for the pressure that reproduces the reference density recorded at the initial blocked-in state.
 * </p>
 *
 * <p>
 * This class is a screening tool. It does not replace a thermal relief valve sizing study (see
 * {@link neqsim.process.util.fire.ReliefValveSizing}) and does not account for vapor space, trace heating, insulation
 * credit, or non-uniform heating along a pipe run.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class BlockedInLiquidExpansionAnalysis {

  /** Conversion factor from bara to Pa. */
  private static final double PA_PER_BARA = 1.0e5;

  /** Relative density tolerance used to accept a bisection solution. */
  private static final double DENSITY_RELATIVE_TOLERANCE = 1.0e-6;

  /** Maximum number of bisection iterations per temperature point. */
  private static final int MAX_BISECTION_ITERATIONS = 100;

  /** Maximum number of pressure-bracket expansion steps per temperature point. */
  private static final int MAX_BRACKET_EXPANSIONS = 60;

  /** Lower bound of the pressure search range, in Pa (0.01 bara). */
  private static final double MIN_SEARCH_PRESSURE_PA = 1.0e3;

  /** Upper bound of the pressure search range, in Pa (10 000 bara). */
  private static final double MAX_SEARCH_PRESSURE_PA = 1.0e9;

  /**
   * Private constructor. This is a static utility class and must not be instantiated.
   */
  private BlockedInLiquidExpansionAnalysis() {
  }

  /**
   * Computes the isochoric (constant-density) pressure profile of a blocked-in liquid as it is warmed through a
   * sequence of temperatures.
   *
   * <p>
   * The reference density is taken from the supplied fluid at its current temperature and pressure (the initial
   * blocked-in state). For every requested temperature the method performs a bracket-and-bisection search on pressure,
   * re-flashing a clone of the fluid at each trial pressure, until the resulting mixture density matches the reference
   * density to within a relative tolerance of {@value #DENSITY_RELATIVE_TOLERANCE}.
   * </p>
   *
   * @param fluid the fluid at the initial blocked-in state; its current temperature and pressure are used to compute
   * the reference (constant) density. The fluid itself is not modified; all flashes are performed on internal clones.
   * @param temperaturesK the sequence of temperatures, in Kelvin, at which to evaluate the isochoric pressure (does not
   * need to be sorted, but is typically monotonically increasing for a heating scenario)
   * @return an array, the same length as {@code temperaturesK}, of isochoric pressures in Pa
   * @throws IllegalArgumentException if {@code fluid} is {@code null} or {@code temperaturesK} is {@code null} or empty
   * @throws IllegalStateException if a pressure bracket containing the reference density cannot be found within the
   * configured search bounds (for example because the fluid leaves the single liquid-phase region over the requested
   * temperature range)
   */
  public static double[] computeIsochoricPressureProfile(SystemInterface fluid, double[] temperaturesK) {
    validateFluid(fluid);
    if (temperaturesK == null || temperaturesK.length == 0) {
      throw new IllegalArgumentException("temperaturesK must contain at least one value");
    }

    double referencePressurePa = fluid.getPressure() * PA_PER_BARA;
    double referenceDensity = densityAt(fluid, fluid.getTemperature(), referencePressurePa);

    double[] pressuresPa = new double[temperaturesK.length];
    double previousPressurePa = referencePressurePa;
    for (int i = 0; i < temperaturesK.length; i++) {
      previousPressurePa = solvePressureForDensity(fluid, temperaturesK[i], referenceDensity, previousPressurePa);
      pressuresPa[i] = previousPressurePa;
    }
    return pressuresPa;
  }

  /**
   * Estimates the isobaric thermal expansion coefficient, beta = -(1/rho)*(d rho / dT)_P, of the supplied fluid by
   * central finite differences at constant pressure.
   *
   * @param fluid the fluid at the reference temperature and pressure; not modified
   * @param dT the temperature step used for the central difference, in Kelvin, must be positive
   * @return the estimated thermal expansion coefficient, in 1/K
   * @throws IllegalArgumentException if {@code fluid} is {@code null} or {@code dT} is not positive
   */
  public static double estimateThermalExpansionCoefficient(SystemInterface fluid, double dT) {
    validateFluid(fluid);
    if (dT <= 0.0) {
      throw new IllegalArgumentException("dT must be positive");
    }

    double referenceTemperatureK = fluid.getTemperature();
    double referencePressurePa = fluid.getPressure() * PA_PER_BARA;
    double referenceDensity = densityAt(fluid, referenceTemperatureK, referencePressurePa);
    double densityPlus = densityAt(fluid, referenceTemperatureK + dT, referencePressurePa);
    double densityMinus = densityAt(fluid, referenceTemperatureK - dT, referencePressurePa);

    return -(densityPlus - densityMinus) / (2.0 * dT) / referenceDensity;
  }

  /**
   * Estimates the isothermal compressibility, kappa = (1/rho)*(d rho / dP)_T, of the supplied fluid by central finite
   * differences at constant temperature.
   *
   * @param fluid the fluid at the reference temperature and pressure; not modified
   * @param dP the pressure step used for the central difference, in Pa, must be positive
   * @return the estimated isothermal compressibility, in 1/Pa
   * @throws IllegalArgumentException if {@code fluid} is {@code null} or {@code dP} is not positive
   */
  public static double estimateIsothermalCompressibility(SystemInterface fluid, double dP) {
    validateFluid(fluid);
    if (dP <= 0.0) {
      throw new IllegalArgumentException("dP must be positive");
    }

    double referenceTemperatureK = fluid.getTemperature();
    double referencePressurePa = fluid.getPressure() * PA_PER_BARA;
    double referenceDensity = densityAt(fluid, referenceTemperatureK, referencePressurePa);
    double densityPlus = densityAt(fluid, referenceTemperatureK, referencePressurePa + dP);
    double densityMinus = densityAt(fluid, referenceTemperatureK, referencePressurePa - dP);

    return (densityPlus - densityMinus) / (2.0 * dP) / referenceDensity;
  }

  /**
   * Computes the simplified, constant-property thermal expansion pressure rise, {@code dP = (beta / kappa) * dT}, per
   * API 521 Section 4.4.12.
   *
   * <p>
   * This relation is only accurate for small temperature steps around the reference state at which {@code beta} and
   * {@code kappa} were evaluated; for larger temperature changes prefer
   * {@link #computeIsochoricPressureProfile(SystemInterface, double[])}.
   * </p>
   *
   * @param beta the isobaric thermal expansion coefficient, in 1/K
   * @param kappa the isothermal compressibility, in 1/Pa, must be positive
   * @param deltaT the temperature rise, in K
   * @return the estimated pressure rise, in Pa
   * @throws IllegalArgumentException if {@code kappa} is not positive
   */
  public static double simplifiedPressureRise(double beta, double kappa, double deltaT) {
    if (kappa <= 0.0) {
      throw new IllegalArgumentException("kappa must be positive");
    }
    return (beta / kappa) * deltaT;
  }

  /**
   * Validates that a fluid argument is usable by this class.
   *
   * @param fluid the fluid to validate
   * @throws IllegalArgumentException if {@code fluid} is {@code null}
   */
  private static void validateFluid(SystemInterface fluid) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }
  }

  /**
   * Flashes a clone of the supplied fluid template at the given temperature and pressure and returns the resulting
   * mixture density.
   *
   * @param template the fluid whose composition and model are cloned for the flash
   * @param temperatureK the flash temperature, in Kelvin
   * @param pressurePa the flash pressure, in Pa
   * @return the mixture density at the flashed state, in kg/m3
   */
  private static double densityAt(SystemInterface template, double temperatureK, double pressurePa) {
    SystemInterface state = template.clone();
    state.setTemperature(temperatureK, "K");
    state.setPressure(pressurePa / PA_PER_BARA, "bara");
    ThermodynamicOperations operations = new ThermodynamicOperations(state);
    operations.TPflash();
    state.initProperties();
    return state.getDensity("kg/m3");
  }

  /**
   * Searches for the pressure at the given temperature that reproduces the target density, using bracket expansion
   * followed by bisection.
   *
   * @param template the fluid whose composition and model are cloned for each trial flash
   * @param temperatureK the temperature at which to search, in Kelvin
   * @param targetDensityKgPerM3 the target (reference) density, in kg/m3
   * @param startPressurePa an initial guess for the pressure search, in Pa (typically the previously solved pressure at
   * an adjacent temperature)
   * @return the pressure, in Pa, at which the flashed density matches the target density to within
   * {@value #DENSITY_RELATIVE_TOLERANCE} relative
   * @throws IllegalStateException if a bracket containing the target density cannot be found within the configured
   * search bounds
   */
  private static double solvePressureForDensity(SystemInterface template, double temperatureK,
      double targetDensityKgPerM3, double startPressurePa) {
    double pLow = Math.max(startPressurePa, MIN_SEARCH_PRESSURE_PA);
    double pHigh = pLow;
    double densityAtStart = densityAt(template, temperatureK, pLow);

    if (densityAtStart < targetDensityKgPerM3) {
      int expansions = 0;
      while (densityAt(template, temperatureK, pHigh) < targetDensityKgPerM3) {
        pLow = pHigh;
        pHigh *= 2.0;
        expansions++;
        if (pHigh > MAX_SEARCH_PRESSURE_PA || expansions > MAX_BRACKET_EXPANSIONS) {
          throw new IllegalStateException("Could not bracket the isochoric pressure at " + temperatureK
              + " K within the maximum search pressure of " + MAX_SEARCH_PRESSURE_PA
              + " Pa. Check that the fluid remains a single-phase liquid over the requested temperature range.");
        }
      }
    } else {
      int expansions = 0;
      while (densityAt(template, temperatureK, pLow) > targetDensityKgPerM3) {
        pHigh = pLow;
        pLow = Math.max(pLow * 0.5, MIN_SEARCH_PRESSURE_PA);
        expansions++;
        if (pLow <= MIN_SEARCH_PRESSURE_PA || expansions > MAX_BRACKET_EXPANSIONS) {
          throw new IllegalStateException("Could not bracket the isochoric pressure at " + temperatureK
              + " K within the minimum search pressure of " + MIN_SEARCH_PRESSURE_PA + " Pa.");
        }
      }
    }

    double pMid = 0.5 * (pLow + pHigh);
    for (int iteration = 0; iteration < MAX_BISECTION_ITERATIONS; iteration++) {
      pMid = 0.5 * (pLow + pHigh);
      double density = densityAt(template, temperatureK, pMid);
      double relativeError = (density - targetDensityKgPerM3) / targetDensityKgPerM3;
      if (Math.abs(relativeError) < DENSITY_RELATIVE_TOLERANCE) {
        return pMid;
      }
      if (density < targetDensityKgPerM3) {
        pLow = pMid;
      } else {
        pHigh = pMid;
      }
    }
    return pMid;
  }
}
