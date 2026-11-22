package neqsim.thermo.util.steam;

/**
 * Reference properties of pure liquid water from the IAPWS formulations.
 *
 * <p>
 * Thermodynamic properties come from the IAPWS-IF97 Region 1 Gibbs function, evaluated as a liquid at any pressure so
 * that an aqueous phase in a gas mixture is never switched to the steam region. Viscosity uses IAPWS R12-08 and thermal
 * conductivity IAPWS R15-11, both without the critical enhancement term, which is negligible for a dense liquid below
 * 623.15 K. Region 1 is validated for 273.15 to 623.15 K up to 100 MPa; states below 273.15 K or below the saturation
 * pressure are metastable extrapolations of the same equations and are flagged by {@link #isMetastable()}.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class LiquidWaterReference {
  /** Lowest supported temperature [K], including subcooled extrapolation. */
  public static final double MIN_TEMPERATURE = 253.15;
  /** Highest supported temperature [K], the IF97 Region 1 limit. */
  public static final double MAX_TEMPERATURE = 623.15;
  /** Highest supported pressure [bara], the IF97 Region 1 limit. */
  public static final double MAX_PRESSURE = 1000.0;

  private final double temperature;
  private final double pressure;
  private final double density;
  private final double densityPressureDerivative;
  private final double densityTemperatureDerivative;
  private final double enthalpy;
  private final double entropy;
  private final double heatCapacityCp;
  private final double viscosity;
  private final double thermalConductivity;

  /**
   * Evaluates all properties at one state.
   *
   * @param temperature temperature [K]
   * @param pressure absolute pressure [bara]
   */
  private LiquidWaterReference(double temperature, double pressure) {
    this.temperature = temperature;
    this.pressure = pressure;
    double pressureMPa = pressure / 10.0;
    double gibbs = Iapws_if97.liquidGibbsDerivative(pressureMPa, temperature, 0, 0);
    double gibbsT = Iapws_if97.liquidGibbsDerivative(pressureMPa, temperature, 1, 0);
    double gibbsTT = Iapws_if97.liquidGibbsDerivative(pressureMPa, temperature, 2, 0);
    double volume = Iapws_if97.liquidGibbsDerivative(pressureMPa, temperature, 0, 1);
    double volumeT = Iapws_if97.liquidGibbsDerivative(pressureMPa, temperature, 1, 1);
    double volumeP = Iapws_if97.liquidGibbsDerivative(pressureMPa, temperature, 0, 2);
    density = 1.0 / volume;
    densityPressureDerivative = -volumeP / (volume * volume);
    densityTemperatureDerivative = -volumeT / (volume * volume);
    entropy = -gibbsT;
    enthalpy = gibbs - temperature * gibbsT;
    heatCapacityCp = -temperature * gibbsTT;
    viscosity = CoolingWaterTransport.backgroundViscosity(temperature, density);
    thermalConductivity = CoolingWaterTransport.backgroundConductivity(temperature, density);
  }

  /**
   * Checks whether a state is inside the supported liquid range.
   *
   * @param temperature temperature [K]
   * @param pressure absolute pressure [bara]
   * @return true when the reference equations can be evaluated
   */
  public static boolean isInRange(double temperature, double pressure) {
    return Double.isFinite(temperature) && Double.isFinite(pressure) && temperature >= MIN_TEMPERATURE
        && temperature <= MAX_TEMPERATURE && pressure > 0.0 && pressure <= MAX_PRESSURE;
  }

  /**
   * Evaluates liquid water at a state.
   *
   * @param temperature temperature [K]
   * @param pressure absolute pressure [bara]
   * @return reference properties
   * @throws IllegalArgumentException if the state is outside the supported range
   */
  public static LiquidWaterReference at(double temperature, double pressure) {
    if (!isInRange(temperature, pressure)) {
      throw new IllegalArgumentException("Liquid water reference requires " + MIN_TEMPERATURE + " <= T <= "
          + MAX_TEMPERATURE + " K and 0 < P <= " + MAX_PRESSURE + " bara");
    }
    return new LiquidWaterReference(temperature, pressure);
  }

  /**
   * Reports whether the state is an extrapolation below 273.15 K or below the saturation pressure.
   *
   * @return true for a metastable liquid state
   */
  public boolean isMetastable() {
    return temperature < 273.15 || pressure < Iapws_if97.psat_t(temperature) * 10.0;
  }

  /**
   * Returns the density.
   *
   * @return density [kg/m3]
   */
  public double getDensity() {
    return density;
  }

  /**
   * Returns the isothermal pressure derivative of density.
   *
   * @return derivative [kg/(m3 Pa)], equal to s2/m2
   */
  public double getDensityPressureDerivative() {
    return densityPressureDerivative;
  }

  /**
   * Returns the isobaric temperature derivative of density.
   *
   * @return derivative [kg/(m3 K)]
   */
  public double getDensityTemperatureDerivative() {
    return densityTemperatureDerivative;
  }

  /**
   * Returns the specific enthalpy on the IF97 reference state.
   *
   * @return enthalpy [J/kg]
   */
  public double getEnthalpy() {
    return enthalpy;
  }

  /**
   * Returns the specific entropy on the IF97 reference state.
   *
   * @return entropy [J/(kg K)]
   */
  public double getEntropy() {
    return entropy;
  }

  /**
   * Returns the isobaric heat capacity.
   *
   * @return heat capacity [J/(kg K)]
   */
  public double getHeatCapacityCp() {
    return heatCapacityCp;
  }

  /**
   * Returns the dynamic viscosity.
   *
   * @return viscosity [Pa s]
   */
  public double getViscosity() {
    return viscosity;
  }

  /**
   * Returns the thermal conductivity.
   *
   * @return thermal conductivity [W/(m K)]
   */
  public double getThermalConductivity() {
    return thermalConductivity;
  }
}
