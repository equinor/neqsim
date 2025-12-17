package neqsim.process.measurementdevice.vfm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.measurementdevice.StreamMeasurementDeviceBaseClass;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Virtual Flow Meter for calculating multiphase flow rates from pressure and temperature
 * measurements.
 *
 * <p>
 * This class implements a physics-based virtual flow meter using NeqSim's thermodynamic models to
 * estimate oil, gas, and water flow rates. It is designed for integration with AI-powered
 * production optimization platforms that require accurate flow estimates with uncertainty
 * quantification.
 * </p>
 *
 * <p>
 * Key features:
 * </p>
 * <ul>
 * <li>Thermodynamic flash calculations for phase split</li>
 * <li>Uncertainty propagation from input measurements</li>
 * <li>Online calibration with well test data</li>
 * <li>Quality indicators for result confidence</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class VirtualFlowMeter extends StreamMeasurementDeviceBaseClass {
  private static final long serialVersionUID = 1000L;

  private double upstreamPressure;
  private double downstreamPressure;
  private double temperature;
  private double chokeOpening = 100.0; // percent

  private double pressureUncertainty = 0.01; // 1% relative uncertainty
  private double temperatureUncertainty = 0.5; // 0.5 K absolute uncertainty

  private double flowCoefficient = 1.0;
  private double calibrationFactor = 1.0;

  private List<WellTestData> calibrationHistory = new ArrayList<>();
  private Instant lastCalibration;

  private VFMResult lastResult;

  /**
   * Well test data for VFM calibration.
   */
  public static class WellTestData {
    private final Instant timestamp;
    private final double oilRate;
    private final double gasRate;
    private final double waterRate;
    private final double pressure;
    private final double temperature;
    private final double chokeOpening;

    public WellTestData(Instant timestamp, double oilRate, double gasRate, double waterRate,
        double pressure, double temperature, double chokeOpening) {
      this.timestamp = timestamp;
      this.oilRate = oilRate;
      this.gasRate = gasRate;
      this.waterRate = waterRate;
      this.pressure = pressure;
      this.temperature = temperature;
      this.chokeOpening = chokeOpening;
    }

    public Instant getTimestamp() {
      return timestamp;
    }

    public double getOilRate() {
      return oilRate;
    }

    public double getGasRate() {
      return gasRate;
    }

    public double getWaterRate() {
      return waterRate;
    }

    public double getPressure() {
      return pressure;
    }

    public double getTemperature() {
      return temperature;
    }

    public double getChokeOpening() {
      return chokeOpening;
    }
  }

  /**
   * Creates a new Virtual Flow Meter.
   *
   * @param name the meter name/tag
   * @param stream the stream to measure
   */
  public VirtualFlowMeter(String name, StreamInterface stream) {
    super(name, "Sm3/d", stream);
  }

  /**
   * Sets the upstream pressure measurement.
   *
   * @param pressure pressure in bara
   */
  public void setUpstreamPressure(double pressure) {
    this.upstreamPressure = pressure;
  }

  /**
   * Sets the downstream pressure measurement.
   *
   * @param pressure pressure in bara
   */
  public void setDownstreamPressure(double pressure) {
    this.downstreamPressure = pressure;
  }

  /**
   * Sets the temperature measurement.
   *
   * @param temperature temperature in K
   */
  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }

  /**
   * Sets the choke opening.
   *
   * @param opening choke opening in percent (0-100)
   */
  public void setChokeOpening(double opening) {
    this.chokeOpening = opening;
  }

  /**
   * Sets the measurement uncertainties.
   *
   * @param pressureRelative relative pressure uncertainty (e.g., 0.01 for 1%)
   * @param temperatureAbsolute absolute temperature uncertainty in K
   */
  public void setMeasurementUncertainties(double pressureRelative, double temperatureAbsolute) {
    this.pressureUncertainty = pressureRelative;
    this.temperatureUncertainty = temperatureAbsolute;
  }

  /**
   * Calculates flow rates from current measurements.
   *
   * @return VFM result with flow rates and uncertainties
   */
  public VFMResult calculateFlowRates() {
    return calculateFlowRates(upstreamPressure, downstreamPressure, temperature);
  }

  /**
   * Calculates flow rates from specified P, T, dP conditions.
   *
   * @param pressure upstream pressure in bara
   * @param temperature temperature in K
   * @param differentialPressure pressure drop in bar
   * @return VFM result with flow rates and uncertainties
   */
  public VFMResult calculateFlowRates(double pressure, double differentialPressure,
      double temperature) {

    StreamInterface str = getStream();
    if (str == null || str.getFluid() == null) {
      return VFMResult.builder().quality(VFMResult.Quality.INVALID).build();
    }

    // Clone the fluid and set conditions
    SystemInterface fluid = str.getFluid().clone();
    fluid.setTemperature(temperature);
    fluid.setPressure(pressure);

    // Flash calculation
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    try {
      ops.TPflash();
    } catch (Exception e) {
      return VFMResult.builder().quality(VFMResult.Quality.INVALID).build();
    }

    // Calculate phase flow rates based on differential pressure
    double dpRatio = Math.sqrt(Math.abs(differentialPressure));
    double totalMolarFlow = fluid.getTotalNumberOfMoles();

    // Apply flow coefficient and calibration
    double flowMultiplier = flowCoefficient * calibrationFactor * dpRatio * (chokeOpening / 100.0);

    // Get phase fractions and calculate rates
    double oilRate = 0;
    double gasRate = 0;
    double waterRate = 0;

    if (fluid.hasPhaseType("oil")) {
      oilRate = fluid.getPhase("oil").getFlowRate("Sm3/day") * flowMultiplier;
    }
    if (fluid.hasPhaseType("gas")) {
      gasRate = fluid.getPhase("gas").getFlowRate("Sm3/day") * flowMultiplier;
    }
    if (fluid.hasPhaseType("aqueous")) {
      waterRate = fluid.getPhase("aqueous").getFlowRate("Sm3/day") * flowMultiplier;
    }

    // Calculate uncertainties using error propagation
    double oilStdDev = calculateUncertainty(oilRate, pressureUncertainty, temperatureUncertainty);
    double gasStdDev = calculateUncertainty(gasRate, pressureUncertainty, temperatureUncertainty);
    double waterStdDev =
        calculateUncertainty(waterRate, pressureUncertainty, temperatureUncertainty);

    // Determine quality based on calibration recency and conditions
    VFMResult.Quality quality = determineQuality(pressure, temperature);

    lastResult = VFMResult.builder().timestamp(Instant.now())
        .oilFlowRate(oilRate, oilStdDev, "Sm3/d").gasFlowRate(gasRate, gasStdDev, "Sm3/d")
        .waterFlowRate(waterRate, waterStdDev, "Sm3/d").quality(quality)
        .addProperty("pressure", pressure).addProperty("temperature", temperature)
        .addProperty("chokeOpening", chokeOpening).build();

    return lastResult;
  }

  /**
   * Calculates uncertainty using simplified error propagation.
   *
   * @param rate phase flow rate in Sm3/d
   * @param pressureUncert relative pressure uncertainty (fraction)
   * @param tempUncert absolute temperature uncertainty (K)
   * @return one standard deviation of the flow-rate estimate
   */
  private double calculateUncertainty(double rate, double pressureUncert, double tempUncert) {
    // Simplified uncertainty model: combine relative uncertainties
    double relativeUncert = Math.sqrt(pressureUncert * pressureUncert + 0.02 * 0.02); // 2% model
                                                                                      // uncertainty
    return rate * relativeUncert;
  }

  /**
   * Determines result quality based on operating conditions and calibration.
   */
  private VFMResult.Quality determineQuality(double pressure, double temperature) {
    if (calibrationHistory.isEmpty()) {
      return VFMResult.Quality.LOW;
    }

    // Check if within calibration range
    double minP = Double.MAX_VALUE, maxP = Double.MIN_VALUE;
    double minT = Double.MAX_VALUE, maxT = Double.MIN_VALUE;

    for (WellTestData test : calibrationHistory) {
      minP = Math.min(minP, test.getPressure());
      maxP = Math.max(maxP, test.getPressure());
      minT = Math.min(minT, test.getTemperature());
      maxT = Math.max(maxT, test.getTemperature());
    }

    if (pressure < minP * 0.9 || pressure > maxP * 1.1 || temperature < minT - 10
        || temperature > maxT + 10) {
      return VFMResult.Quality.EXTRAPOLATED;
    }

    // Check calibration age
    if (lastCalibration != null) {
      long daysSinceCalibration =
          java.time.Duration.between(lastCalibration, Instant.now()).toDays();
      if (daysSinceCalibration < 7) {
        return VFMResult.Quality.HIGH;
      } else if (daysSinceCalibration < 30) {
        return VFMResult.Quality.NORMAL;
      }
    }

    return VFMResult.Quality.LOW;
  }

  /**
   * Calibrates the VFM using well test data.
   *
   * @param wellTests list of well test measurements
   */
  public void calibrate(List<WellTestData> wellTests) {
    if (wellTests == null || wellTests.isEmpty()) {
      return;
    }

    calibrationHistory.addAll(wellTests);

    // Simple calibration: adjust calibration factor based on average error
    double sumError = 0;
    int count = 0;

    for (WellTestData test : wellTests) {
      VFMResult calculated =
          calculateFlowRates(test.getPressure(), test.getPressure() * 0.1, test.getTemperature());

      if (calculated.isUsable() && calculated.getOilFlowRate() > 0 && test.getOilRate() > 0) {
        sumError += test.getOilRate() / calculated.getOilFlowRate();
        count++;
      }
    }

    if (count > 0) {
      calibrationFactor = sumError / count;
      lastCalibration = Instant.now();
    }
  }

  /**
   * Gets the last calculated result.
   *
   * @return the last VFM result
   */
  public VFMResult getLastResult() {
    return lastResult;
  }

  /**
   * Gets uncertainty bounds for the current measurement.
   *
   * @return uncertainty bounds for total flow rate
   */
  public UncertaintyBounds getUncertaintyBounds() {
    if (lastResult == null) {
      return null;
    }

    double total = lastResult.getOilFlowRate() + lastResult.getWaterFlowRate();
    double stdDev = 0;

    if (lastResult.getOilUncertainty() != null) {
      stdDev += Math.pow(lastResult.getOilUncertainty().getStandardDeviation(), 2);
    }
    if (lastResult.getWaterUncertainty() != null) {
      stdDev += Math.pow(lastResult.getWaterUncertainty().getStandardDeviation(), 2);
    }

    return new UncertaintyBounds(total, Math.sqrt(stdDev), "Sm3/d");
  }

  @Override
  public double getMeasuredValue(String unit) {
    VFMResult result = calculateFlowRates();
    double totalLiquid = result.getTotalLiquidFlowRate();

    // Unit conversion
    if ("m3/hr".equalsIgnoreCase(unit)) {
      return totalLiquid / 24.0;
    } else if ("bbl/d".equalsIgnoreCase(unit) || "bpd".equalsIgnoreCase(unit)) {
      return totalLiquid * 6.2898; // Sm3/d to bbl/d
    }

    return totalLiquid;
  }

  /**
   * Sets the flow coefficient (Cv-like parameter).
   *
   * @param coefficient the flow coefficient
   */
  public void setFlowCoefficient(double coefficient) {
    this.flowCoefficient = coefficient;
  }

  /**
   * Gets the calibration factor.
   *
   * @return the calibration factor
   */
  public double getCalibrationFactor() {
    return calibrationFactor;
  }

  /**
   * Gets the last calibration timestamp.
   *
   * @return the last calibration time or null
   */
  public Instant getLastCalibrationTime() {
    return lastCalibration;
  }
}
