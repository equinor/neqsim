package neqsim.process.equipment.electrolyzer;

/**
 * Water-electrolyzer technology selector with typical operating-point defaults.
 *
 * <p>
 * Operating-point estimates originate from IRENA (2022) and Buttler &amp; Spliethoff, Renewable and Sustainable Energy
 * Reviews 82 (2018) 2440-2454. These estimates are not a qualification of current efficiency. The alkaline
 * current-efficiency reference is documented below; PEM, SOEC and AEM retain unqualified legacy values pending issue
 * 4682. Supply measured current efficiency for equipment-specific calculations.
 * </p>
 *
 * <ul>
 * <li>{@link #PEM} - proton-exchange-membrane, 60-80 &deg;C, current density 1.5-2.5 A/cm&sup2;, cell voltage ~1.8 V,
 * with an unqualified legacy current efficiency.</li>
 * <li>{@link #ALKALINE} - liquid KOH, 70-90 &deg;C, current density 0.2-0.5 A/cm&sup2;, cell voltage ~1.85 V, specific
 * energy ~51.32 kWh/kgH2 at fixed voltage, before balance-of-plant losses.</li>
 * <li>{@link #SOEC} - solid-oxide electrolysis cell, 700-850 &deg;C, current density ~1 A/cm&sup2;, cell voltage ~1.3
 * V; external steam and heat requirements are separate from current efficiency.</li>
 * <li>{@link #AEM} - anion-exchange-membrane, 50-70 &deg;C, intermediate properties.</li>
 * </ul>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public enum ElectrolyzerTechnology {

  /** Proton-exchange-membrane electrolysis. */
  PEM(1.8, 2.0, 80.0, 30.0, 0.65),

  /**
   * Liquid-alkaline reference using Ulleberg (2003), Eq. (9), Table 3, PHOEBUS coefficients at 80 degrees C and
   * reported 7 bar: f1 = 250 (mA/cm2)^2 and f2 = 0.96. Evaluating at the existing nominal 400 mA/cm2 extrapolates
   * beyond the current-efficiency measurements in Fig. 6; this is a screening estimate, not a measured nominal
   * efficiency or an off-design correlation. DOI: 10.1016/S0360-3199(02)00033-2.
   */
  ALKALINE(1.85, 0.4, 80.0, 7.0, 0.96 * 400.0 * 400.0 / (250.0 + 400.0 * 400.0)),

  /** Solid-oxide electrolysis cell (high-temperature steam electrolysis). */
  SOEC(1.30, 1.0, 800.0, 1.0, 0.85),

  /** Anion-exchange-membrane electrolysis (emerging). */
  AEM(1.85, 0.8, 60.0, 10.0, 0.60);

  private final double defaultCellVoltage;
  private final double defaultCurrentDensity;
  private final double defaultTemperatureC;
  private final double defaultPressureBara;
  private final double defaultFaradaicEfficiency;

  ElectrolyzerTechnology(double defaultCellVoltage, double defaultCurrentDensity, double defaultTemperatureC,
      double defaultPressureBara, double defaultFaradaicEfficiency) {
    this.defaultCellVoltage = defaultCellVoltage;
    this.defaultCurrentDensity = defaultCurrentDensity;
    this.defaultTemperatureC = defaultTemperatureC;
    this.defaultPressureBara = defaultPressureBara;
    this.defaultFaradaicEfficiency = defaultFaradaicEfficiency;
  }

  /**
   * Get the typical cell voltage for this technology at nominal operating point.
   *
   * @return cell voltage (V)
   */
  public double getDefaultCellVoltage() {
    return defaultCellVoltage;
  }

  /**
   * Get the typical current density for this technology at nominal operating point.
   *
   * @return current density (A/cm2)
   */
  public double getDefaultCurrentDensity() {
    return defaultCurrentDensity;
  }

  /**
   * Get the typical stack temperature for this technology.
   *
   * @return temperature (Celsius)
   */
  public double getDefaultTemperatureC() {
    return defaultTemperatureC;
  }

  /**
   * Get the typical stack pressure for this technology.
   *
   * @return pressure (bara)
   */
  public double getDefaultPressureBara() {
    return defaultPressureBara;
  }

  /**
   * Get the nominal Faradaic (current) efficiency: the fraction of charge producing hydrogen according to
   * {@code etaF = 2 F nH2 / I}. This is distinct from LHV/HHV energy efficiency. Thermal substitution in SOEC does not
   * determine current efficiency. The alkaline reference is a fixed screening estimate; the other technologies retain
   * unqualified legacy values. This getter does not recalculate efficiency with current density, temperature or
   * pressure. See {@link #ALKALINE} for provenance and extrapolation limits.
   *
   * @return faradaic efficiency (dimensionless, 0..1)
   */
  public double getDefaultFaradaicEfficiency() {
    return defaultFaradaicEfficiency;
  }
}
