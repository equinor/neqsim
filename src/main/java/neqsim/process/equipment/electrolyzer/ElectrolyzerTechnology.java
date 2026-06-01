package neqsim.process.equipment.electrolyzer;

/**
 * Water-electrolyzer technology selector with typical operating-point defaults.
 *
 * <p>
 * Defaults are taken from the IRENA Hydrogen Decarbonisation Pathways report (2022) and Buttler
 * &amp; Spliethoff, Renewable and Sustainable Energy Reviews 82 (2018) 2440-2454. They cover the
 * commercially relevant water-electrolysis technologies and the SOEC research demonstrators.
 * </p>
 *
 * <ul>
 * <li>{@link #PEM} - proton-exchange-membrane, 60-80 &deg;C, current density 1.5-2.5 A/cm&sup2;,
 * cell voltage ~1.8 V, specific energy ~52 kWh/kgH2.</li>
 * <li>{@link #ALKALINE} - liquid KOH, 70-90 &deg;C, current density 0.2-0.5 A/cm&sup2;, cell
 * voltage ~1.85 V, specific energy ~50 kWh/kgH2.</li>
 * <li>{@link #SOEC} - solid-oxide electrolysis cell, 700-850 &deg;C, current density ~1 A/cm&sup2;,
 * cell voltage ~1.3 V, specific energy ~40 kWh/kgH2 (electricity only).</li>
 * <li>{@link #AEM} - anion-exchange-membrane, 50-70 &deg;C, intermediate properties.</li>
 * </ul>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public enum ElectrolyzerTechnology {

  /** Proton-exchange-membrane electrolysis. */
  PEM(1.8, 2.0, 80.0, 30.0, 0.65),

  /** Liquid-alkaline (KOH) electrolysis. */
  ALKALINE(1.85, 0.4, 80.0, 7.0, 0.62),

  /** Solid-oxide electrolysis cell (high-temperature steam electrolysis). */
  SOEC(1.30, 1.0, 800.0, 1.0, 0.85),

  /** Anion-exchange-membrane electrolysis (emerging). */
  AEM(1.85, 0.8, 60.0, 10.0, 0.60);

  private final double defaultCellVoltage;
  private final double defaultCurrentDensity;
  private final double defaultTemperatureC;
  private final double defaultPressureBara;
  private final double defaultFaradaicEfficiency;

  ElectrolyzerTechnology(double defaultCellVoltage, double defaultCurrentDensity,
      double defaultTemperatureC, double defaultPressureBara, double defaultFaradaicEfficiency) {
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
   * Get the typical faradaic (current) efficiency for this technology. Equals the fraction of the
   * stack current that actually splits water (the rest is lost to leakage, crossover, parasitic
   * reactions). PEM and alkaline typically 0.6-0.7; SOEC approaches 0.9 because thermal energy
   * partially substitutes for electricity.
   *
   * @return faradaic efficiency (dimensionless, 0..1)
   */
  public double getDefaultFaradaicEfficiency() {
    return defaultFaradaicEfficiency;
  }
}
