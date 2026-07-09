package neqsim.process.corrosion;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Robust in-situ aqueous pH estimator for corrosion and scaling investigations.
 *
 * <p>
 * The rigorous electrolyte in-situ pH from {@link SystemInterface#getpH()} is the preferred value, but it is not robust
 * across all pressure and temperature conditions (for example it can return NaN at low pressure, and it can return an
 * unphysical basic value if the chemical-reaction equilibrium was not solved). An investigation that blindly consumes
 * that value can silently propagate a wrong pH into a corrosion or scale calculation.
 * </p>
 *
 * <p>
 * This helper returns a value that is always finite and physically bounded: it takes the electrolyte pH when that value
 * is finite and inside a plausible band, and otherwise falls back to a CO2-water carbonic-acid correlation (the same
 * form used by {@link NorsokM506CorrosionRate}). The result records which source was used so the caller can flag
 * fallbacks in a report.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see NorsokM506CorrosionRate
 */
public final class RobustAqueousPH {

  /** Lower plausible pH bound for a produced-water / CO2 brine. */
  private static final double MIN_PLAUSIBLE_PH = 2.0;

  /** Upper plausible pH bound for a produced-water / CO2 brine. */
  private static final double MAX_PLAUSIBLE_PH = 9.0;

  private RobustAqueousPH() {
  }

  /**
   * Immutable result of a robust pH estimate.
   */
  public static final class Result {
    private final double pH;
    private final String source;
    private final boolean fellBack;

    /**
     * Creates a robust-pH result.
     *
     * @param pH the estimated pH
     * @param source the source of the value ("electrolyte" or "correlation")
     * @param fellBack whether the correlation fallback was used
     */
    public Result(double pH, String source, boolean fellBack) {
      this.pH = pH;
      this.source = source;
      this.fellBack = fellBack;
    }

    /**
     * Gets the estimated pH.
     *
     * @return the pH value (always finite)
     */
    public double getPH() {
      return pH;
    }

    /**
     * Gets the source of the value.
     *
     * @return "electrolyte" if the rigorous value was used, "correlation" if the fallback was used
     */
    public String getSource() {
      return source;
    }

    /**
     * Indicates whether the correlation fallback was used.
     *
     * @return true if the rigorous electrolyte pH was rejected and the correlation was used
     */
    public boolean isFellBack() {
      return fellBack;
    }
  }

  /**
   * Estimates a robust in-situ aqueous pH from an electrolyte fluid, falling back to a CO2-water correlation.
   *
   * @param fluid a flashed electrolyte fluid (should have an aqueous phase); must not be null
   * @param co2PartialPressureBar the CO2 partial pressure in bar, used only for the correlation fallback
   * @return a finite, physically bounded pH result
   */
  public static Result estimate(SystemInterface fluid, double co2PartialPressureBar) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }

    double temperatureC = fluid.getTemperature() - 273.15;

    if (fluid.hasPhaseType(PhaseType.AQUEOUS)) {
      double rigorous = fluid.getPhase(PhaseType.AQUEOUS).getpH();
      if (!Double.isNaN(rigorous) && !Double.isInfinite(rigorous) && rigorous >= MIN_PLAUSIBLE_PH
          && rigorous <= MAX_PLAUSIBLE_PH) {
        return new Result(rigorous, "electrolyte", false);
      }
    }

    double fallback = correlationPH(temperatureC, co2PartialPressureBar);
    return new Result(fallback, "correlation", true);
  }

  /**
   * CO2-water carbonic-acid pH correlation used as the robust fallback.
   *
   * <p>
   * Uses the combined temperature-dependent function pH = 0.5 * (pKa1' + pKH) - 0.5 * log10(fCO2), consistent with
   * {@link NorsokM506CorrosionRate#calculateEquilibriumPH()}.
   * </p>
   *
   * @param temperatureC temperature in degrees Celsius
   * @param co2PartialPressureBar CO2 partial pressure in bar
   * @return the correlated CO2-water pH (clamped to the plausible band)
   */
  public static double correlationPH(double temperatureC, double co2PartialPressureBar) {
    if (co2PartialPressureBar <= 0.0) {
      return 7.0;
    }
    double pKH = 1.12 + 0.01623 * temperatureC - 8.93e-5 * temperatureC * temperatureC;
    double pKa1 = 6.41 + 0.0004 * temperatureC + 5.0e-6 * temperatureC * temperatureC;
    double pH = 0.5 * (pKa1 + pKH) - 0.5 * Math.log10(co2PartialPressureBar);
    return Math.max(MIN_PLAUSIBLE_PH, Math.min(MAX_PLAUSIBLE_PH, pH));
  }
}
