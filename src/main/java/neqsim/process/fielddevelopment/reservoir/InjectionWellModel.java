package neqsim.process.fielddevelopment.reservoir;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Injection well performance model for water and gas injection.
 *
 * <p>
 * This class provides comprehensive injection well modeling including:
 * <ul>
 * <li><b>Injectivity Index:</b> Relationship between injection rate and pressure</li>
 * <li><b>Hall Plot Analysis:</b> Injection performance monitoring</li>
 * <li><b>Wellbore Hydraulics:</b> Pressure losses in injection tubing</li>
 * <li><b>Fracture Pressure:</b> Maximum safe injection pressure</li>
 * <li><b>Skin Factor Effects:</b> Near-wellbore damage or stimulation</li>
 * </ul>
 *
 * <h2>Injectivity Index</h2>
 * <p>
 * The injectivity index (II) relates injection rate to bottomhole pressure:
 * </p>
 * <p>
 * <code>q = II × (P_wf - P_res)</code>
 * </p>
 * <p>
 * where q is injection rate, P_wf is flowing bottomhole pressure, and P_res is average reservoir
 * pressure. Injectivity depends on:
 * </p>
 * <ul>
 * <li>Permeability and thickness (kh)</li>
 * <li>Fluid viscosity at reservoir conditions</li>
 * <li>Near-wellbore skin factor</li>
 * <li>Wellbore radius and drainage radius</li>
 * </ul>
 *
 * <h2>Hall Plot</h2>
 * <p>
 * The Hall plot is used to monitor injection well performance:
 * </p>
 * <p>
 * <code>∫(P_wf - P_res)dt vs. Cumulative Injection</code>
 * </p>
 * <p>
 * Slope changes indicate:
 * </p>
 * <ul>
 * <li>Increasing slope: Formation damage (skin increase)</li>
 * <li>Decreasing slope: Fracturing or channeling</li>
 * <li>Constant slope: Stable injection</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * InjectionWellModel well = new InjectionWellModel();
 * well.setWellType(InjectionType.WATER_INJECTOR);
 * well.setReservoirPressure(250.0, "bara");
 * well.setFormationPermeability(100.0, "mD");
 * well.setFormationThickness(30.0, "m");
 * well.setSkinFactor(2.0);
 * well.setWellDepth(3000.0, "m");
 * well.setTubingID(0.1, "m");
 * well.setMaxBHP(350.0, "bara"); // Below fracture pressure
 * 
 * InjectionWellResult result = well.calculate(10000.0); // Target 10000 Sm3/day
 * 
 * System.out.println("Achievable rate: " + result.getAchievableRate() + " Sm3/day");
 * System.out.println("Required WHI pressure: " + result.getWellheadPressure() + " bara");
 * System.out.println("BHP: " + result.getBottomholePressure() + " bara");
 * System.out.println("Pump power: " + result.getPumpPower() + " kW");
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see InjectionStrategy
 */
public class InjectionWellModel implements Serializable {
  private static final long serialVersionUID = 1000L;

  // Physical constants
  private static final double GRAVITY = 9.81; // m/s²

  /**
   * Injection well types.
   */
  public enum InjectionType {
    /** Water injection well. */
    WATER_INJECTOR,
    /** Gas injection well. */
    GAS_INJECTOR,
    /** CO2 injection well. */
    CO2_INJECTOR,
    /** WAG injection well. */
    WAG_INJECTOR
  }

  // Well type
  private InjectionType injectionType = InjectionType.WATER_INJECTOR;

  // Reservoir parameters
  private double reservoirPressure = 250.0; // bara
  private double reservoirTemperature = 363.15; // K
  private double formationPermeability = 100.0; // mD
  private double formationThickness = 30.0; // m
  private double drainageRadius = 500.0; // m
  private double wellboreRadius = 0.1; // m
  private double skinFactor = 0.0; // Skin factor

  // Well parameters
  private double wellDepth = 3000.0; // m TVD
  private double tubingID = 0.1; // m
  private double tubingRoughness = 4.5e-5; // m

  // Pressure limits
  private double fracturePressure = 400.0; // bara (formation fracture pressure)
  private double maxBHP = 350.0; // bara (operating limit below fracture)
  private double minWHP = 10.0; // bara (minimum wellhead pressure)
  private double surfaceInjectionPressure = 50.0; // bara (available at surface)

  // Fluid properties
  private double waterDensity = 1020.0; // kg/m³
  private double waterViscosity = 0.5; // cP at reservoir conditions
  private double gasMolecularWeight = 20.0; // kg/kmol
  private double gasViscosity = 0.02; // cP

  // Formation water properties (for water injection)
  private double formationWaterViscosity = 0.3; // cP at reservoir conditions

  // Pump efficiency
  private double pumpEfficiency = 0.75;

  /**
   * Creates a new injection well model with default parameters.
   */
  public InjectionWellModel() {
    // Default constructor
  }

  /**
   * Creates a new injection well model.
   *
   * @param injectionType type of injection well
   */
  public InjectionWellModel(InjectionType injectionType) {
    this.injectionType = injectionType;
  }

  /**
   * Calculates injection well performance for target rate.
   *
   * @param targetRateSm3d target injection rate (Sm3/day)
   * @return injection well result
   */
  public InjectionWellResult calculate(double targetRateSm3d) {
    InjectionWellResult result = new InjectionWellResult();
    result.targetRate = targetRateSm3d;
    result.injectionType = this.injectionType;

    // Calculate injectivity index
    double ii = calculateInjectivityIndex();
    result.injectivityIndex = ii;

    // Calculate required BHP for target rate
    double requiredBHP = reservoirPressure + targetRateSm3d / ii;
    result.requiredBHP = requiredBHP;

    // Check against fracture pressure
    if (requiredBHP > maxBHP) {
      // Rate limited by pressure constraint
      result.limitedByPressure = true;
      result.achievableRate = ii * (maxBHP - reservoirPressure);
      result.bottomholePressure = maxBHP;
    } else {
      result.limitedByPressure = false;
      result.achievableRate = targetRateSm3d;
      result.bottomholePressure = requiredBHP;
    }

    // Calculate wellhead pressure
    result.wellheadPressure =
        calculateWellheadPressure(result.achievableRate, result.bottomholePressure);

    // Check if surface pressure is sufficient
    if (result.wellheadPressure > surfaceInjectionPressure) {
      // Need pump to boost pressure
      result.needsPump = true;
      result.requiredPumpDeltaP = result.wellheadPressure - surfaceInjectionPressure;
      result.pumpPower = calculatePumpPower(result.achievableRate, result.requiredPumpDeltaP);
    } else {
      result.needsPump = false;
    }

    // Calculate Hall plot parameters
    calculateHallPlotParameters(result);

    return result;
  }

  /**
   * Calculates maximum injection rate at given constraints.
   *
   * @return maximum injection result
   */
  public InjectionWellResult calculateMaximumRate() {
    return calculate(Double.MAX_VALUE);
  }

  /**
   * Calculates injectivity index using Darcy's law.
   *
   * <p>
   * For radial flow: II = 2π × k × h / (μ × B × (ln(re/rw) + S))
   * </p>
   *
   * @return injectivity index (Sm3/day/bar)
   */
  private double calculateInjectivityIndex() {
    // Convert permeability from mD to m²
    double kSI = formationPermeability * 9.869e-16; // mD to m²

    // Get fluid properties based on injection type
    double viscosity;
    double formationVolumeFactor;

    if (injectionType == InjectionType.WATER_INJECTOR) {
      viscosity = waterViscosity * 1e-3; // cP to Pa·s
      formationVolumeFactor = 1.02; // Bw ≈ 1.02 for water
    } else {
      // Gas injection
      viscosity = gasViscosity * 1e-3; // cP to Pa·s
      // Calculate Bg at reservoir conditions
      formationVolumeFactor = 1.01325 / reservoirPressure * reservoirTemperature / 288.15 * 0.9;
    }

    // Radial flow equation (Darcy units)
    double lnRatio = Math.log(drainageRadius / wellboreRadius);
    double denominator = viscosity * formationVolumeFactor * (lnRatio + skinFactor);

    if (denominator <= 0) {
      return 1000.0; // Return high default if calculation fails
    }

    // II in m³/s/Pa, convert to Sm3/day/bar
    double iiSI = 2.0 * Math.PI * kSI * formationThickness / denominator;
    double iiField = iiSI * 86400.0 * 1e5; // m³/s/Pa to Sm3/day/bar

    return iiField;
  }

  /**
   * Calculates wellhead injection pressure from BHP.
   *
   * @param rateSm3d injection rate (Sm3/day)
   * @param bhp bottomhole pressure (bara)
   * @return wellhead pressure (bara)
   */
  private double calculateWellheadPressure(double rateSm3d, double bhp) {
    // For injection: WHP = BHP - hydrostatic + friction
    // (Water flows down, so we subtract hydrostatic head)

    double density;
    if (injectionType == InjectionType.WATER_INJECTOR) {
      density = waterDensity;
    } else {
      // Gas density at average conditions
      double avgPressure = (bhp + 20.0) / 2.0; // Approximate
      density = avgPressure * 1e5 * gasMolecularWeight
          / (8314.0 * ((reservoirTemperature + 288.15) / 2.0));
    }

    // Hydrostatic pressure (for injection, this reduces required WHP)
    double hydrostaticBar = density * GRAVITY * wellDepth / 1e5;

    // Friction pressure drop
    double frictionBar = calculateFrictionPressureDrop(rateSm3d, density);

    // WHP = BHP - hydrostatic + friction (injection direction)
    double whp = bhp - hydrostaticBar + frictionBar;

    return Math.max(whp, minWHP);
  }

  /**
   * Calculates friction pressure drop in tubing.
   *
   * @param rateSm3d rate (Sm3/day)
   * @param density fluid density (kg/m³)
   * @return friction pressure drop (bar)
   */
  private double calculateFrictionPressureDrop(double rateSm3d, double density) {
    // Volume flow rate at average conditions (simplified)
    double qm3s = rateSm3d / 86400.0;

    // Cross-sectional area
    double area = Math.PI * tubingID * tubingID / 4.0;

    // Velocity
    double velocity = qm3s / area;

    // Reynolds number
    double viscosity =
        (injectionType == InjectionType.WATER_INJECTOR ? waterViscosity : gasViscosity) * 1e-3;
    double reynolds = density * velocity * tubingID / viscosity;

    // Friction factor (Colebrook-White approximation)
    double frictionFactor = calculateFrictionFactor(reynolds);

    // Darcy-Weisbach
    double frictionGradient = frictionFactor * density * velocity * velocity / (2.0 * tubingID); // Pa/m
    double frictionPa = frictionGradient * wellDepth;

    return frictionPa / 1e5; // Pa to bar
  }

  /**
   * Calculates Darcy friction factor.
   *
   * @param reynolds Reynolds number
   * @return friction factor
   */
  private double calculateFrictionFactor(double reynolds) {
    if (reynolds < 2300) {
      return 64.0 / Math.max(reynolds, 1.0);
    }

    // Turbulent - Haaland equation
    double relRoughness = tubingRoughness / tubingID;
    double term = Math.pow(relRoughness / 3.7, 1.11) + 6.9 / reynolds;
    return Math.pow(-1.8 * Math.log10(term), -2);
  }

  /**
   * Calculates pump power requirement.
   *
   * @param rateSm3d injection rate (Sm3/day)
   * @param deltaPbar pressure boost (bar)
   * @return pump power (kW)
   */
  private double calculatePumpPower(double rateSm3d, double deltaPbar) {
    // Hydraulic power = Q × ΔP
    double qm3s = rateSm3d / 86400.0;
    double deltaPa = deltaPbar * 1e5;

    double hydraulicPowerW = qm3s * deltaPa;
    double shaftPowerW = hydraulicPowerW / pumpEfficiency;

    return shaftPowerW / 1000.0; // kW
  }

  /**
   * Calculates Hall plot parameters for performance monitoring.
   *
   * @param result injection result to populate
   */
  private void calculateHallPlotParameters(InjectionWellResult result) {
    // Hall plot slope = (BHP - Pres) / q = 1 / II
    result.hallSlope = 1.0 / result.injectivityIndex;

    // Expected cumulative pressure-time integral rate
    // For constant injection: d(∫ΔP dt)/d(cum inj) = ΔP/q = Hall slope
    result.expectedHallSlope = result.hallSlope;

    // Skin factor contribution to Hall slope
    double lnRatio = Math.log(drainageRadius / wellboreRadius);
    result.skinContribution = skinFactor / (lnRatio + skinFactor);
  }

  /**
   * Calculates injection with pressure interference from nearby producers.
   *
   * @param targetRate target rate (Sm3/day)
   * @param producerDistances distances to nearby producers (m)
   * @param producerRates production rates of nearby wells (Sm3/day)
   * @return adjusted injection result
   */
  public InjectionWellResult calculateWithInterference(double targetRate,
      double[] producerDistances, double[] producerRates) {

    // Calculate base injection
    InjectionWellResult result = calculate(targetRate);

    // Calculate interference pressure from producers
    // Using superposition principle
    double interferencePressure = 0.0;

    for (int i = 0; i < producerDistances.length; i++) {
      if (i < producerRates.length && producerDistances[i] > 0) {
        // Simplified line source solution
        double distance = producerDistances[i];
        double rate = producerRates[i];

        // Pressure drawdown at injector due to producer
        // Δp ∝ q × ln(re/r) / (2π k h)
        double kSI = formationPermeability * 9.869e-16;
        double viscosity = formationWaterViscosity * 1e-3;

        double deltaPSI = rate / 86400.0 * viscosity * Math.log(drainageRadius / distance)
            / (2.0 * Math.PI * kSI * formationThickness);
        interferencePressure += deltaPSI / 1e5; // Pa to bar
      }
    }

    // Adjust required BHP for interference
    result.interferencePressure = interferencePressure;
    result.effectiveReservoirPressure = reservoirPressure - interferencePressure;

    // Recalculate achievable rate with interference
    double ii = result.injectivityIndex;
    double availableDeltaP = maxBHP - result.effectiveReservoirPressure;
    result.achievableRateWithInterference = ii * availableDeltaP;

    return result;
  }

  // ===================== Setters =====================

  /**
   * Sets injection well type.
   *
   * @param type injection type
   * @return this for chaining
   */
  public InjectionWellModel setWellType(InjectionType type) {
    this.injectionType = type;
    return this;
  }

  /**
   * Sets reservoir pressure.
   *
   * @param pressure pressure value
   * @param unit unit ("bara", "psia")
   * @return this for chaining
   */
  public InjectionWellModel setReservoirPressure(double pressure, String unit) {
    if ("psia".equalsIgnoreCase(unit)) {
      this.reservoirPressure = pressure * 0.0689476;
    } else {
      this.reservoirPressure = pressure;
    }
    return this;
  }

  /**
   * Sets reservoir temperature.
   *
   * @param temperature temperature value
   * @param unit unit ("K", "C", "F")
   * @return this for chaining
   */
  public InjectionWellModel setReservoirTemperature(double temperature, String unit) {
    if ("C".equalsIgnoreCase(unit)) {
      this.reservoirTemperature = temperature + 273.15;
    } else if ("F".equalsIgnoreCase(unit)) {
      this.reservoirTemperature = (temperature - 32) * 5.0 / 9.0 + 273.15;
    } else {
      this.reservoirTemperature = temperature;
    }
    return this;
  }

  /**
   * Sets formation permeability.
   *
   * @param permeability permeability value
   * @param unit unit ("mD", "D")
   * @return this for chaining
   */
  public InjectionWellModel setFormationPermeability(double permeability, String unit) {
    if ("D".equalsIgnoreCase(unit)) {
      this.formationPermeability = permeability * 1000.0;
    } else {
      this.formationPermeability = permeability;
    }
    return this;
  }

  /**
   * Sets formation thickness.
   *
   * @param thickness thickness value
   * @param unit unit ("m", "ft")
   * @return this for chaining
   */
  public InjectionWellModel setFormationThickness(double thickness, String unit) {
    if ("ft".equalsIgnoreCase(unit)) {
      this.formationThickness = thickness * 0.3048;
    } else {
      this.formationThickness = thickness;
    }
    return this;
  }

  /**
   * Sets skin factor.
   *
   * @param skin skin factor (dimensionless)
   * @return this for chaining
   */
  public InjectionWellModel setSkinFactor(double skin) {
    this.skinFactor = skin;
    return this;
  }

  /**
   * Sets well depth.
   *
   * @param depth depth value
   * @param unit unit ("m", "ft")
   * @return this for chaining
   */
  public InjectionWellModel setWellDepth(double depth, String unit) {
    if ("ft".equalsIgnoreCase(unit)) {
      this.wellDepth = depth * 0.3048;
    } else {
      this.wellDepth = depth;
    }
    return this;
  }

  /**
   * Sets tubing ID.
   *
   * @param diameter diameter value
   * @param unit unit ("m", "in", "mm")
   * @return this for chaining
   */
  public InjectionWellModel setTubingID(double diameter, String unit) {
    if ("in".equalsIgnoreCase(unit)) {
      this.tubingID = diameter * 0.0254;
    } else if ("mm".equalsIgnoreCase(unit)) {
      this.tubingID = diameter / 1000.0;
    } else {
      this.tubingID = diameter;
    }
    return this;
  }

  /**
   * Sets maximum BHP (operating limit below fracture pressure).
   *
   * @param pressure pressure value
   * @param unit unit ("bara", "psia")
   * @return this for chaining
   */
  public InjectionWellModel setMaxBHP(double pressure, String unit) {
    if ("psia".equalsIgnoreCase(unit)) {
      this.maxBHP = pressure * 0.0689476;
    } else {
      this.maxBHP = pressure;
    }
    return this;
  }

  /**
   * Sets fracture pressure.
   *
   * @param pressure pressure value
   * @param unit unit ("bara", "psia")
   * @return this for chaining
   */
  public InjectionWellModel setFracturePressure(double pressure, String unit) {
    if ("psia".equalsIgnoreCase(unit)) {
      this.fracturePressure = pressure * 0.0689476;
    } else {
      this.fracturePressure = pressure;
    }
    return this;
  }

  /**
   * Sets surface injection pressure available.
   *
   * @param pressure pressure value
   * @param unit unit ("bara", "psia")
   * @return this for chaining
   */
  public InjectionWellModel setSurfaceInjectionPressure(double pressure, String unit) {
    if ("psia".equalsIgnoreCase(unit)) {
      this.surfaceInjectionPressure = pressure * 0.0689476;
    } else {
      this.surfaceInjectionPressure = pressure;
    }
    return this;
  }

  /**
   * Sets water viscosity at reservoir conditions.
   *
   * @param viscosity viscosity (cP)
   * @return this for chaining
   */
  public InjectionWellModel setWaterViscosity(double viscosity) {
    this.waterViscosity = viscosity;
    return this;
  }

  /**
   * Sets drainage radius.
   *
   * @param radius radius (m)
   * @return this for chaining
   */
  public InjectionWellModel setDrainageRadius(double radius) {
    this.drainageRadius = radius;
    return this;
  }

  /**
   * Sets wellbore radius.
   *
   * @param radius radius (m)
   * @return this for chaining
   */
  public InjectionWellModel setWellboreRadius(double radius) {
    this.wellboreRadius = radius;
    return this;
  }

  /**
   * Sets pump efficiency.
   *
   * @param efficiency efficiency (0-1)
   * @return this for chaining
   */
  public InjectionWellModel setPumpEfficiency(double efficiency) {
    this.pumpEfficiency = Math.max(0.1, Math.min(efficiency, 1.0));
    return this;
  }

  // ===================== Result Class =====================

  /**
   * Injection well calculation result.
   */
  public static class InjectionWellResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Injection type. */
    public InjectionType injectionType;

    /** Target injection rate (Sm3/day). */
    public double targetRate;

    /** Achievable injection rate (Sm3/day). */
    public double achievableRate;

    /** Injectivity index (Sm3/day/bar). */
    public double injectivityIndex;

    /** Required BHP for target rate (bara). */
    public double requiredBHP;

    /** Actual BHP (bara). */
    public double bottomholePressure;

    /** Wellhead injection pressure (bara). */
    public double wellheadPressure;

    /** Whether rate is limited by pressure. */
    public boolean limitedByPressure;

    /** Whether pump is needed. */
    public boolean needsPump;

    /** Required pump pressure boost (bar). */
    public double requiredPumpDeltaP;

    /** Pump power (kW). */
    public double pumpPower;

    /** Hall plot slope (bar·day/Sm3). */
    public double hallSlope;

    /** Expected Hall slope. */
    public double expectedHallSlope;

    /** Skin contribution to Hall slope. */
    public double skinContribution;

    /** Interference pressure from nearby wells (bar). */
    public double interferencePressure;

    /** Effective reservoir pressure with interference (bara). */
    public double effectiveReservoirPressure;

    /** Achievable rate with interference (Sm3/day). */
    public double achievableRateWithInterference;

    /**
     * Gets achievable rate.
     *
     * @return rate (Sm3/day)
     */
    public double getAchievableRate() {
      return achievableRate;
    }

    /**
     * Gets wellhead pressure.
     *
     * @return pressure (bara)
     */
    public double getWellheadPressure() {
      return wellheadPressure;
    }

    /**
     * Gets bottomhole pressure.
     *
     * @return pressure (bara)
     */
    public double getBottomholePressure() {
      return bottomholePressure;
    }

    /**
     * Gets pump power.
     *
     * @return power (kW)
     */
    public double getPumpPower() {
      return pumpPower;
    }

    /**
     * Gets injectivity index.
     *
     * @return II (Sm3/day/bar)
     */
    public double getInjectivityIndex() {
      return injectivityIndex;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Injection Well Result\n");
      sb.append("=====================\n");
      sb.append(String.format("Target rate: %.0f Sm3/day%n", targetRate));
      sb.append(String.format("Achievable rate: %.0f Sm3/day%n", achievableRate));
      sb.append(String.format("Injectivity index: %.1f Sm3/day/bar%n", injectivityIndex));
      sb.append(String.format("BHP: %.1f bara%n", bottomholePressure));
      sb.append(String.format("WHP: %.1f bara%n", wellheadPressure));
      sb.append(String.format("Limited by pressure: %s%n", limitedByPressure));
      if (needsPump) {
        sb.append(String.format("Pump required: ΔP=%.1f bar, Power=%.0f kW%n", requiredPumpDeltaP,
            pumpPower));
      }
      return sb.toString();
    }
  }

  /**
   * Pattern class for configuring multiple injection wells.
   */
  public static class InjectionPattern implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Pattern type. */
    public enum PatternType {
      /** Five-spot pattern. */
      FIVE_SPOT,
      /** Inverted five-spot. */
      INVERTED_FIVE_SPOT,
      /** Line drive. */
      LINE_DRIVE,
      /** Staggered line drive. */
      STAGGERED_LINE_DRIVE,
      /** Seven-spot. */
      SEVEN_SPOT,
      /** Nine-spot. */
      NINE_SPOT,
      /** Peripheral. */
      PERIPHERAL
    }

    private PatternType patternType = PatternType.FIVE_SPOT;
    private double wellSpacing = 500.0; // m
    private int injectorCount = 1;
    private int producerCount = 4;

    /**
     * Creates a new injection pattern.
     *
     * @param type pattern type
     */
    public InjectionPattern(PatternType type) {
      this.patternType = type;
      setDefaultCounts();
    }

    private void setDefaultCounts() {
      switch (patternType) {
        case FIVE_SPOT:
          injectorCount = 1;
          producerCount = 4;
          break;
        case INVERTED_FIVE_SPOT:
          injectorCount = 4;
          producerCount = 1;
          break;
        case LINE_DRIVE:
          injectorCount = 1;
          producerCount = 1;
          break;
        case SEVEN_SPOT:
          injectorCount = 1;
          producerCount = 6;
          break;
        case NINE_SPOT:
          injectorCount = 1;
          producerCount = 8;
          break;
        default:
          injectorCount = 1;
          producerCount = 4;
      }
    }

    /**
     * Gets typical areal sweep efficiency for pattern.
     *
     * @param mobilityRatio mobility ratio (λw/λo)
     * @return sweep efficiency (0-1)
     */
    public double getArealSweepEfficiency(double mobilityRatio) {
      // Simplified correlation based on pattern type
      double baseSweep = 0.7;

      switch (patternType) {
        case FIVE_SPOT:
          baseSweep = 0.72;
          break;
        case LINE_DRIVE:
          baseSweep = 0.57;
          break;
        case STAGGERED_LINE_DRIVE:
          baseSweep = 0.80;
          break;
        default:
          baseSweep = 0.70;
      }

      // Mobility ratio correction
      if (mobilityRatio > 1.0) {
        baseSweep *= Math.pow(mobilityRatio, -0.2);
      }

      return Math.max(0.3, Math.min(baseSweep, 0.95));
    }

    /**
     * Gets well spacing.
     *
     * @return spacing (m)
     */
    public double getWellSpacing() {
      return wellSpacing;
    }

    /**
     * Sets well spacing.
     *
     * @param spacing spacing (m)
     * @return this for chaining
     */
    public InjectionPattern setWellSpacing(double spacing) {
      this.wellSpacing = spacing;
      return this;
    }
  }
}
