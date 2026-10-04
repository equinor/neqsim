package neqsim.process.diagnostics;

import java.io.Serializable;
import com.google.gson.GsonBuilder;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Interprets a field seat-tightness test on a closed isolation valve and turns it into a leak rate, an equivalent
 * leak-gap size, and a screening severity verdict.
 *
 * <p>
 * Offshore isolation tests report a symptom, not a number: "the cavity went to 4.2 barg in 60 seconds", or "there was
 * still 0.3 barg standing with the bleed fully open". This class converts either observation into kg/s and Sm3/h using
 * real-gas properties from the supplied {@link SystemInterface}, and then reports the seat gap that would pass that
 * rate. It answers the question a maintenance engineer actually has - is this seat weepage that sealant injection will
 * cure, or is the seat mechanically damaged?
 * </p>
 *
 * <p>
 * Two independent test modes are supported, and they deliberately depend on different unknowns, so running both on the
 * same valve is a genuine cross-check rather than a repeat:
 * </p>
 *
 * <ul>
 * <li><b>Pressure build-up</b> - the bleed is shut and the pressure rise in the trapped volume is timed. Needs the
 * trapped volume; does <em>not</em> need the upstream pressure, because the seat gap is choked while the cavity is far
 * below the line pressure.</li>
 * <li><b>Standing bleed pressure</b> - the bleed is left fully open and the pressure it cannot clear is read. Needs the
 * bleed bore; does <em>not</em> need the trapped volume, because it is a steady state where leak in equals bleed
 * out.</li>
 * </ul>
 *
 * <p>
 * The severity bands are NeqSim screening bands expressed on equivalent gap diameter, not an acceptance criterion from
 * a standard. Acceptance belongs to {@link neqsim.process.equipment.valve.ValveSeatLeakageClass} and to the valve data
 * sheet.
 * </p>
 *
 * <pre>
 * ValveSeatLeakageAssessment test = new ValveSeatLeakageAssessment("NP-23-0060", gas);
 * test.setTemperature(40.0, "C");
 * test.setUpstreamPressure(325.0, "bara");
 * test.setPressureBuildUpTest(0.027, 4.2, 60.0);
 * ValveSeatLeakageAssessment.Result r = test.calculate();
 * r.getLeakRateSm3PerHour();
 * r.getEquivalentGapDiameterMm();
 * r.getSeverity();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ValveSeatLeakageAssessment implements Serializable {
  /** Serialization version. */
  private static final long serialVersionUID = 1000L;

  /** Pascal per bar. */
  private static final double PA_PER_BAR = 1.0e5;

  /** Standard reference pressure for Sm3, in bara. */
  private static final double STANDARD_PRESSURE_BARA = 1.01325;

  /** Standard reference temperature for Sm3, in Kelvin. */
  private static final double STANDARD_TEMPERATURE_K = 288.15;

  /** Equivalent gap diameter below which the leak is screened as negligible, in mm. */
  private static final double GAP_NEGLIGIBLE_MM = 0.05;

  /** Equivalent gap diameter below which the leak is screened as seat weepage, in mm. */
  private static final double GAP_WEEPAGE_MM = 0.5;

  /** Equivalent gap diameter below which the leak is screened as seat damage, in mm. */
  private static final double GAP_DAMAGE_MM = 2.0;

  /** Screening severity of a measured seat leak, expressed on equivalent gap diameter. */
  public enum Severity {
    /** Equivalent gap below 0.05 mm; at or near the resolution of a field isolation test. */
    NEGLIGIBLE,
    /** Equivalent gap 0.05 to 0.5 mm; marginal seat contact, typical sealant-injection candidate. */
    SEAT_WEEPAGE,
    /** Equivalent gap 0.5 to 2 mm; mechanical seat damage, in-situ lapping or overhaul. */
    SEAT_DAMAGE,
    /** Equivalent gap above 2 mm; gross passing, the valve is not an isolation barrier. */
    GROSS_LEAK
  }

  /** Which field observation the leak rate was derived from. */
  public enum TestMode {
    /** Bleed shut, pressure rise in a trapped volume timed. */
    PRESSURE_BUILD_UP,
    /** Bleed fully open, standing pressure read at steady state. */
    STANDING_BLEED
  }

  /** Valve tag the test belongs to. */
  private final String tagName;

  /** Process fluid on the upstream side of the seat. */
  private final SystemInterface fluid;

  /** Temperature of the trapped cavity, in Kelvin. */
  private double temperatureK = 288.15;

  /** Pressure upstream of the closed seat, in bara. */
  private double upstreamPressureBara = Double.NaN;

  /** Discharge coefficient applied to both the seat gap and the bleed restriction. */
  private double dischargeCoefficient = 0.80;

  /** Selected test mode. */
  private TestMode testMode = null;

  /** Trapped volume between the closed seat and the next closed boundary, in m3. */
  private double trappedVolumeM3 = Double.NaN;

  /** Observed pressure rise, in bar. */
  private double pressureRiseBar = Double.NaN;

  /** Duration over which the pressure rise was observed, in seconds. */
  private double riseDurationSeconds = Double.NaN;

  /** Standing gauge pressure that the fully open bleed could not clear, in barg. */
  private double standingPressureBarg = Double.NaN;

  /** Bore of the open bleed path, in m. */
  private double bleedBoreM = Double.NaN;

  /**
   * Constructs a seat-leakage test interpreter for a valve.
   *
   * @param tagName the valve tag the test belongs to, may be null
   * @param fluid the process fluid upstream of the closed seat, must not be null
   * @throws IllegalArgumentException if {@code fluid} is null
   */
  public ValveSeatLeakageAssessment(String tagName, SystemInterface fluid) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }
    this.tagName = tagName;
    this.fluid = fluid;
  }

  /**
   * Sets the temperature of the trapped cavity.
   *
   * @param value the temperature value, must be above absolute zero in the given unit
   * @param unit the temperature unit, "C" or "K"
   * @return this instance for chaining
   * @throws IllegalArgumentException if the unit is unknown or the temperature is not positive in K
   */
  public ValveSeatLeakageAssessment setTemperature(double value, String unit) {
    double kelvin;
    if ("C".equalsIgnoreCase(unit)) {
      kelvin = value + 273.15;
    } else if ("K".equalsIgnoreCase(unit)) {
      kelvin = value;
    } else {
      throw new IllegalArgumentException("unit must be C or K, was " + unit);
    }
    if (kelvin <= 0.0) {
      throw new IllegalArgumentException("temperature must be above absolute zero");
    }
    this.temperatureK = kelvin;
    return this;
  }

  /**
   * Sets the pressure upstream of the closed seat, i.e. the pressure driving the leak.
   *
   * @param value the pressure value, must be positive
   * @param unit the pressure unit, "bara" or "barg"
   * @return this instance for chaining
   * @throws IllegalArgumentException if the unit is unknown or the absolute pressure is not positive
   */
  public ValveSeatLeakageAssessment setUpstreamPressure(double value, String unit) {
    double bara;
    if ("bara".equalsIgnoreCase(unit)) {
      bara = value;
    } else if ("barg".equalsIgnoreCase(unit)) {
      bara = value + STANDARD_PRESSURE_BARA;
    } else {
      throw new IllegalArgumentException("unit must be bara or barg, was " + unit);
    }
    if (bara <= 0.0) {
      throw new IllegalArgumentException("upstream pressure must be positive");
    }
    this.upstreamPressureBara = bara;
    return this;
  }

  /**
   * Sets the discharge coefficient used for the seat gap and the bleed restriction.
   *
   * @param value the discharge coefficient, must be in the interval (0, 1]
   * @return this instance for chaining
   * @throws IllegalArgumentException if the value is outside (0, 1]
   */
  public ValveSeatLeakageAssessment setDischargeCoefficient(double value) {
    if (value <= 0.0 || value > 1.0) {
      throw new IllegalArgumentException("dischargeCoefficient must be in (0, 1]");
    }
    this.dischargeCoefficient = value;
    return this;
  }

  /**
   * Configures a bleed-shut pressure build-up test.
   *
   * @param trappedVolume the volume between the closed seat and the next closed boundary, in m3, must be positive
   * @param pressureRise the observed pressure rise, in bar, must be positive
   * @param durationSeconds the time over which the rise was observed, in s, must be positive
   * @return this instance for chaining
   * @throws IllegalArgumentException if any argument is not positive
   */
  public ValveSeatLeakageAssessment setPressureBuildUpTest(double trappedVolume, double pressureRise,
      double durationSeconds) {
    if (trappedVolume <= 0.0) {
      throw new IllegalArgumentException("trappedVolume must be positive");
    }
    if (pressureRise <= 0.0) {
      throw new IllegalArgumentException("pressureRise must be positive");
    }
    if (durationSeconds <= 0.0) {
      throw new IllegalArgumentException("durationSeconds must be positive");
    }
    this.trappedVolumeM3 = trappedVolume;
    this.pressureRiseBar = pressureRise;
    this.riseDurationSeconds = durationSeconds;
    this.testMode = TestMode.PRESSURE_BUILD_UP;
    return this;
  }

  /**
   * Configures a fully-open-bleed standing pressure test.
   *
   * @param standingPressure the gauge pressure the open bleed could not clear, in barg, must be positive
   * @param bleedBore the flow bore of the open bleed path, in m, must be positive
   * @return this instance for chaining
   * @throws IllegalArgumentException if any argument is not positive
   */
  public ValveSeatLeakageAssessment setStandingBleedTest(double standingPressure, double bleedBore) {
    if (standingPressure <= 0.0) {
      throw new IllegalArgumentException("standingPressure must be positive");
    }
    if (bleedBore <= 0.0) {
      throw new IllegalArgumentException("bleedBore must be positive");
    }
    this.standingPressureBarg = standingPressure;
    this.bleedBoreM = bleedBore;
    this.testMode = TestMode.STANDING_BLEED;
    return this;
  }

  /**
   * Evaluates the configured field test.
   *
   * @return the interpreted leak rate, equivalent gap and screening severity
   * @throws IllegalStateException if no test mode has been configured, or the upstream pressure is required but was not
   * set
   */
  public Result calculate() {
    if (testMode == null) {
      throw new IllegalStateException(
          "configure setPressureBuildUpTest(..) or setStandingBleedTest(..) before calculate()");
    }
    if (Double.isNaN(upstreamPressureBara)) {
      throw new IllegalStateException("setUpstreamPressure(..) is required to size the seat gap");
    }

    double standardDensity = densityAt(STANDARD_PRESSURE_BARA, STANDARD_TEMPERATURE_K);
    double massFlowKgPerSec;
    boolean bleedChoked = false;

    if (testMode == TestMode.PRESSURE_BUILD_UP) {
      double midPressureBara = STANDARD_PRESSURE_BARA + 0.5 * pressureRiseBar;
      massFlowKgPerSec = trappedVolumeM3 * densityGradient(midPressureBara) * (pressureRiseBar / riseDurationSeconds);
    } else {
      double bleedUpstreamBara = standingPressureBarg + STANDARD_PRESSURE_BARA;
      double[] bleed = restrictionMassFlow(bleedUpstreamBara, STANDARD_PRESSURE_BARA, bleedBoreM);
      massFlowKgPerSec = bleed[0];
      bleedChoked = bleed[1] > 0.5;
    }

    double gapArea = chokedAreaFor(massFlowKgPerSec, upstreamPressureBara);
    double gapDiameterM = Math.sqrt(4.0 * gapArea / Math.PI);
    double gapDiameterMm = gapDiameterM * 1000.0;

    Result result = new Result();
    result.tagName = tagName;
    result.testMode = testMode;
    result.temperatureK = temperatureK;
    result.upstreamPressureBara = upstreamPressureBara;
    result.dischargeCoefficient = dischargeCoefficient;
    result.leakRateKgPerSec = massFlowKgPerSec;
    result.leakRateSm3PerHour = massFlowKgPerSec / standardDensity * 3600.0;
    result.standardDensityKgPerSm3 = standardDensity;
    result.equivalentGapAreaM2 = gapArea;
    result.equivalentGapDiameterMm = gapDiameterMm;
    result.bleedChoked = bleedChoked;
    result.severity = classify(gapDiameterMm);
    result.providesPositiveIsolation = false;
    return result;
  }

  /**
   * Classifies an equivalent gap diameter into a screening severity band.
   *
   * @param gapDiameterMm the equivalent gap diameter, in mm
   * @return the screening severity band
   */
  private static Severity classify(double gapDiameterMm) {
    if (gapDiameterMm < GAP_NEGLIGIBLE_MM) {
      return Severity.NEGLIGIBLE;
    }
    if (gapDiameterMm < GAP_WEEPAGE_MM) {
      return Severity.SEAT_WEEPAGE;
    }
    if (gapDiameterMm < GAP_DAMAGE_MM) {
      return Severity.SEAT_DAMAGE;
    }
    return Severity.GROSS_LEAK;
  }

  /**
   * Flashes the fluid and returns its density at a state.
   *
   * @param pressureBara the pressure, in bara
   * @param tempK the temperature, in K
   * @return the density, in kg/m3
   */
  private double densityAt(double pressureBara, double tempK) {
    SystemInterface local = fluid.clone();
    local.setPressure(pressureBara, "bara");
    local.setTemperature(tempK, "K");
    new ThermodynamicOperations(local).TPflash();
    local.initProperties();
    return local.getDensity("kg/m3");
  }

  /**
   * Evaluates the real-gas density gradient with respect to pressure around a state.
   *
   * @param pressureBara the pressure to evaluate the gradient at, in bara
   * @return the density gradient, in kg/m3 per bar
   */
  private double densityGradient(double pressureBara) {
    double low = Math.max(0.5 * STANDARD_PRESSURE_BARA, pressureBara - 0.5);
    double high = pressureBara + 0.5;
    return (densityAt(high, temperatureK) - densityAt(low, temperatureK)) / (high - low);
  }

  /**
   * Computes compressible mass flow through a restriction, selecting choked or subsonic flow.
   *
   * @param upstreamBara the upstream pressure, in bara
   * @param downstreamBara the downstream pressure, in bara
   * @param diameterM the restriction bore, in m
   * @return a two-element array of the mass flow in kg/s and a choked flag encoded as 1.0 or 0.0
   */
  private double[] restrictionMassFlow(double upstreamBara, double downstreamBara, double diameterM) {
    double area = Math.PI / 4.0 * diameterM * diameterM;
    double[] state = upstreamState(upstreamBara);
    double density = state[0];
    double kappa = state[1];
    double pressurePa = upstreamBara * PA_PER_BAR;
    double criticalRatio = Math.pow(2.0 / (kappa + 1.0), kappa / (kappa - 1.0));
    double ratio = downstreamBara / upstreamBara;
    double flux;
    boolean choked = ratio <= criticalRatio;
    if (choked) {
      flux = Math.sqrt(kappa * density * pressurePa * Math.pow(2.0 / (kappa + 1.0), (kappa + 1.0) / (kappa - 1.0)));
    } else {
      flux = Math.sqrt(2.0 * density * pressurePa * (kappa / (kappa - 1.0))
          * (Math.pow(ratio, 2.0 / kappa) - Math.pow(ratio, (kappa + 1.0) / kappa)));
    }
    double[] out = new double[2];
    out[0] = dischargeCoefficient * area * flux;
    out[1] = choked ? 1.0 : 0.0;
    return out;
  }

  /**
   * Solves for the choked flow area that would pass a given mass flow from the upstream pressure.
   *
   * @param massFlowKgPerSec the mass flow to be passed, in kg/s
   * @param upstreamBara the upstream pressure, in bara
   * @return the required flow area, in m2
   */
  private double chokedAreaFor(double massFlowKgPerSec, double upstreamBara) {
    double[] state = upstreamState(upstreamBara);
    double density = state[0];
    double kappa = state[1];
    double flux = Math.sqrt(
        kappa * density * upstreamBara * PA_PER_BAR * Math.pow(2.0 / (kappa + 1.0), (kappa + 1.0) / (kappa - 1.0)));
    return massFlowKgPerSec / (dischargeCoefficient * flux);
  }

  /**
   * Returns the density and heat-capacity ratio of the fluid at a pressure and the test temperature.
   *
   * @param pressureBara the pressure, in bara
   * @return a two-element array of density in kg/m3 and the ratio of specific heats
   */
  private double[] upstreamState(double pressureBara) {
    SystemInterface local = fluid.clone();
    local.setPressure(pressureBara, "bara");
    local.setTemperature(temperatureK, "K");
    new ThermodynamicOperations(local).TPflash();
    local.initProperties();
    double[] state = new double[2];
    state[0] = local.getDensity("kg/m3");
    state[1] = local.getPhase(0).getCp() / local.getPhase(0).getCv();
    return state;
  }

  /** Interpreted outcome of a field seat-tightness test. */
  public static class Result implements Serializable {
    /** Serialization version. */
    private static final long serialVersionUID = 1000L;

    /** Valve tag the test belongs to. */
    private String tagName;

    /** Field observation the leak rate was derived from. */
    private TestMode testMode;

    /** Cavity temperature used, in K. */
    private double temperatureK;

    /** Pressure driving the leak, in bara. */
    private double upstreamPressureBara;

    /** Discharge coefficient applied. */
    private double dischargeCoefficient;

    /** Interpreted seat leak rate, in kg/s. */
    private double leakRateKgPerSec;

    /** Interpreted seat leak rate, in Sm3/h at 15 degC and 1.01325 bara. */
    private double leakRateSm3PerHour;

    /** Fluid density at standard conditions, in kg/Sm3. */
    private double standardDensityKgPerSm3;

    /** Seat gap area that would pass the interpreted leak rate, in m2. */
    private double equivalentGapAreaM2;

    /** Seat gap diameter that would pass the interpreted leak rate, in mm. */
    private double equivalentGapDiameterMm;

    /** Whether the bleed restriction was choked, for the standing-bleed mode. */
    private boolean bleedChoked;

    /** Screening severity band. */
    private Severity severity;

    /** Whether the valve provides positive isolation; always false when a leak was measured. */
    private boolean providesPositiveIsolation;

    /**
     * Gets the valve tag.
     *
     * @return the valve tag, may be null
     */
    public String getTagName() {
      return tagName;
    }

    /**
     * Gets the field observation the leak rate was derived from.
     *
     * @return the test mode
     */
    public TestMode getTestMode() {
      return testMode;
    }

    /**
     * Gets the cavity temperature used.
     *
     * @return the temperature, in K
     */
    public double getTemperatureK() {
      return temperatureK;
    }

    /**
     * Gets the pressure driving the leak.
     *
     * @return the upstream pressure, in bara
     */
    public double getUpstreamPressureBara() {
      return upstreamPressureBara;
    }

    /**
     * Gets the discharge coefficient applied.
     *
     * @return the discharge coefficient
     */
    public double getDischargeCoefficient() {
      return dischargeCoefficient;
    }

    /**
     * Gets the interpreted seat leak rate.
     *
     * @return the leak rate, in kg/s
     */
    public double getLeakRateKgPerSec() {
      return leakRateKgPerSec;
    }

    /**
     * Gets the interpreted seat leak rate in standard volumetric units.
     *
     * @return the leak rate, in Sm3/h at 15 degC and 1.01325 bara
     */
    public double getLeakRateSm3PerHour() {
      return leakRateSm3PerHour;
    }

    /**
     * Gets the fluid density at standard conditions.
     *
     * @return the standard density, in kg/Sm3
     */
    public double getStandardDensityKgPerSm3() {
      return standardDensityKgPerSm3;
    }

    /**
     * Gets the seat gap area that would pass the interpreted leak rate.
     *
     * @return the equivalent gap area, in m2
     */
    public double getEquivalentGapAreaM2() {
      return equivalentGapAreaM2;
    }

    /**
     * Gets the seat gap diameter that would pass the interpreted leak rate.
     *
     * @return the equivalent gap diameter, in mm
     */
    public double getEquivalentGapDiameterMm() {
      return equivalentGapDiameterMm;
    }

    /**
     * Indicates whether the bleed restriction was choked in the standing-bleed mode.
     *
     * @return true if the bleed was choked, false otherwise
     */
    public boolean isBleedChoked() {
      return bleedChoked;
    }

    /**
     * Gets the screening severity band.
     *
     * @return the severity band
     */
    public Severity getSeverity() {
      return severity;
    }

    /**
     * Indicates whether the valve can be credited with positive isolation.
     *
     * <p>
     * A measurable seat leak disqualifies the valve as a single positive-isolation barrier, so this is false whenever a
     * leak rate was interpreted, regardless of how small it is.
     * </p>
     *
     * @return false whenever a leak was measured
     */
    public boolean providesPositiveIsolation() {
      return providesPositiveIsolation;
    }

    /**
     * Serializes the result to JSON.
     *
     * @return a JSON representation of the result
     */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(this);
    }
  }
}
