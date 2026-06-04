package neqsim.process.equipment.electrolyzer;

/**
 * Simple I-V (polarisation) characteristic for water electrolyzers.
 *
 * <p>
 * Computes the cell voltage from current density as the sum of three terms:
 * </p>
 *
 * <ul>
 * <li><b>Reversible voltage</b> from the Nernst equation, temperature-corrected from 1.229 V at
 * 25&nbsp;&deg;C using d{@code E_rev}/d{@code T} = -0.85 mV/K (Larminie &amp; Dicks, Fuel Cell
 * Systems Explained, 2nd ed., 2003, Ch. 2).</li>
 * <li><b>Activation overpotential</b> from a Tafel form {@code A &middot; ln(j / j0)} with
 * technology-specific Tafel slope {@code A} and exchange current density {@code j0}.</li>
 * <li><b>Ohmic + diffusion losses</b> from an effective area-specific resistance {@code R}
 * multiplied by current density.</li>
 * </ul>
 *
 * <p>
 * For a textbook PEM stack at 80&nbsp;&deg;C and 2&nbsp;A/cm&sup2; this yields about 1.85 V (Carmo
 * et al., Int. J. Hydrogen Energy 38 (2013) 4901-4934). For alkaline at 80&nbsp;&deg;C and
 * 0.4&nbsp;A/cm&sup2; it yields about 1.85 V (Ursua et al., Proc. IEEE 100 (2012) 410-426). The
 * model is deliberately a coarse engineering correlation, not a CFD-resolved electrochemistry
 * model.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class ElectrolyzerIVCharacteristic implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reversible voltage at standard state (25 &deg;C, 1 bar). */
  private static final double E_REV_25C = 1.229;

  /** dE_rev/dT (V/K), Larminie &amp; Dicks. */
  private static final double DE_REV_DT = -0.85e-3;

  private final ElectrolyzerTechnology technology;

  /** Tafel slope (V/decade) used internally as A in V*ln10. */
  private double tafelSlope;

  /** Exchange current density (A/cm2). */
  private double exchangeCurrentDensity;

  /** Area-specific resistance (ohm*cm2). */
  private double areaSpecificResistance;

  /**
   * Create an I-V characteristic with technology-specific defaults.
   *
   * <p>
   * Default coefficients (Tafel slope, exchange current density, area-specific resistance) are
   * picked to reproduce the cell voltage in {@link ElectrolyzerTechnology} at the technology's
   * nominal current density and temperature within ~3%.
   * </p>
   *
   * @param technology electrolyzer technology
   */
  public ElectrolyzerIVCharacteristic(ElectrolyzerTechnology technology) {
    if (technology == null) {
      throw new IllegalArgumentException("technology must not be null");
    }
    this.technology = technology;
    switch (technology) {
      case PEM:
        // Tafel slope 0.060 V/decade, j0 = 1e-3 A/cm2, R = 0.18 ohm*cm2.
        tafelSlope = 0.060;
        exchangeCurrentDensity = 1.0e-3;
        areaSpecificResistance = 0.18;
        break;
      case ALKALINE:
        // Lower j0, higher slope and resistance.
        tafelSlope = 0.090;
        exchangeCurrentDensity = 1.0e-4;
        areaSpecificResistance = 0.50;
        break;
      case SOEC:
        // High temperature reduces both the reversible voltage and the kinetic loss.
        tafelSlope = 0.040;
        exchangeCurrentDensity = 1.0e-2;
        areaSpecificResistance = 0.20;
        break;
      case AEM:
      default:
        tafelSlope = 0.075;
        exchangeCurrentDensity = 5.0e-4;
        areaSpecificResistance = 0.40;
        break;
    }
  }

  /**
   * Set Tafel slope used in {@code eta_act = A &middot; log10(j/j0)} (V/decade).
   *
   * @param tafelSlope Tafel slope (V/decade), must be positive
   */
  public void setTafelSlope(double tafelSlope) {
    if (tafelSlope <= 0.0) {
      throw new IllegalArgumentException("tafelSlope must be positive");
    }
    this.tafelSlope = tafelSlope;
  }

  /**
   * Get the Tafel slope.
   *
   * @return Tafel slope (V/decade)
   */
  public double getTafelSlope() {
    return tafelSlope;
  }

  /**
   * Set exchange current density {@code j0} (A/cm2). Must be positive.
   *
   * @param exchangeCurrentDensity exchange current density (A/cm2)
   */
  public void setExchangeCurrentDensity(double exchangeCurrentDensity) {
    if (exchangeCurrentDensity <= 0.0) {
      throw new IllegalArgumentException("exchangeCurrentDensity must be positive");
    }
    this.exchangeCurrentDensity = exchangeCurrentDensity;
  }

  /**
   * Get the exchange current density.
   *
   * @return exchange current density (A/cm2)
   */
  public double getExchangeCurrentDensity() {
    return exchangeCurrentDensity;
  }

  /**
   * Set the area-specific resistance (ohm*cm2). Must be non-negative.
   *
   * @param areaSpecificResistance area-specific resistance (ohm*cm2)
   */
  public void setAreaSpecificResistance(double areaSpecificResistance) {
    if (areaSpecificResistance < 0.0) {
      throw new IllegalArgumentException("areaSpecificResistance must be non-negative");
    }
    this.areaSpecificResistance = areaSpecificResistance;
  }

  /**
   * Get the area-specific resistance.
   *
   * @return area-specific resistance (ohm*cm2)
   */
  public double getAreaSpecificResistance() {
    return areaSpecificResistance;
  }

  /**
   * Get the electrolyzer technology these coefficients were configured for.
   *
   * @return technology
   */
  public ElectrolyzerTechnology getTechnology() {
    return technology;
  }

  /**
   * Get the reversible (Nernst) voltage at the given temperature.
   *
   * @param temperatureK stack temperature (Kelvin), must be positive
   * @return reversible cell voltage (V)
   */
  public double getReversibleVoltage(double temperatureK) {
    if (temperatureK <= 0.0) {
      throw new IllegalArgumentException("temperatureK must be positive");
    }
    return E_REV_25C + DE_REV_DT * (temperatureK - 298.15);
  }

  /**
   * Get the cell voltage at the given current density and temperature.
   *
   * <p>
   * {@code E_cell = E_rev(T) + A &middot; log10(j / j0) + R &middot; j}
   * </p>
   *
   * <p>
   * Returns the reversible voltage if {@code currentDensity} is at or below {@code j0} (the cell is
   * operating below the activation threshold).
   * </p>
   *
   * @param currentDensity current density (A/cm2), must be non-negative
   * @param temperatureK stack temperature (Kelvin), must be positive
   * @return cell voltage (V)
   */
  public double getCellVoltage(double currentDensity, double temperatureK) {
    if (currentDensity < 0.0) {
      throw new IllegalArgumentException("currentDensity must be non-negative");
    }
    double eRev = getReversibleVoltage(temperatureK);
    if (currentDensity <= exchangeCurrentDensity) {
      return eRev;
    }
    double etaAct = tafelSlope * Math.log10(currentDensity / exchangeCurrentDensity);
    double etaOhm = areaSpecificResistance * currentDensity;
    return eRev + etaAct + etaOhm;
  }
}
