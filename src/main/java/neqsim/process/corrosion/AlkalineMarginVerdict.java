package neqsim.process.corrosion;

/**
 * Screening band for the alkaline margin of a buffered aqueous fluid at operating temperature.
 *
 * <p>
 * The margin is {@code pH(T) - pH_neutral(T)}, i.e. how far above neutrality the fluid actually sits at the temperature
 * where corrosion occurs. This is the meaningful measure for magnetite-film stability, because neutrality itself moves:
 * water is neutral at pH 7.0 at 25 &deg;C but at about pH 5.8 at 150 &deg;C. A raw pH number carries no meaning unless
 * the temperature it refers to is stated.
 * </p>
 *
 * <p>
 * These bands are screening indicators for engineering triage. They are not acceptance criteria from any standard, and
 * they do not replace a system-specific corrosion-control specification.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public enum AlkalineMarginVerdict {
  /** Margin of 2.0 pH units or more above neutrality; comfortable alkaline reserve. */
  ROBUST,
  /** Margin between 1.5 and 2.0 pH units above neutrality. */
  ADEQUATE,
  /** Margin between 1.0 and 1.5 pH units above neutrality; reduced protection reserve. */
  MARGINAL,
  /** Margin below 1.0 pH unit above neutrality; little alkaline reserve at operating temperature. */
  INSUFFICIENT
}
