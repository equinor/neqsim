package neqsim.process.fielddevelopment.screening;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Gas lift design and optimization calculator.
 *
 * <p>
 * This class implements comprehensive gas lift calculations for artificial lift screening and
 * design, including:
 * <ul>
 * <li><b>GLR optimization:</b> Optimal gas-liquid ratio for maximum production</li>
 * <li><b>Valve spacing:</b> Unloading and operating valve depths</li>
 * <li><b>Performance curves:</b> Production rate vs. injection rate</li>
 * <li><b>Gas lift power:</b> Energy requirements for gas compression</li>
 * <li><b>Economic screening:</b> Cost vs. benefit analysis</li>
 * </ul>
 *
 * <h2>Gas Lift Theory</h2>
 * <p>
 * Gas lift reduces the hydrostatic head in the production tubing by injecting gas to lighten the
 * fluid column. The pressure balance is:
 * </p>
 * <p>
 * <code>P_reservoir = P_wellhead + ΔP_friction + ΔP_hydrostatic</code>
 * </p>
 * <p>
 * where the hydrostatic term depends on the average mixture density, which decreases with
 * increasing GLR.
 * </p>
 *
 * <h2>Optimal GLR</h2>
 * <p>
 * Production increases with gas injection until friction losses dominate. The optimal GLR
 * represents the economic balance between increased production and compression costs.
 * </p>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * GasLiftCalculator calc = new GasLiftCalculator();
 * calc.setReservoirPressure(250.0, "bara");
 * calc.setReservoirTemperature(85.0, "C");
 * calc.setWellheadPressure(20.0, "bara");
 * calc.setWellDepth(3000.0, "m");
 * calc.setTubingID(0.1, "m");
 * calc.setProductivityIndex(5.0); // Sm3/day/bar
 * calc.setOilGravity(35.0, "API");
 * calc.setWaterCut(0.3);
 * calc.setFormationGOR(100.0); // Sm3/Sm3
 * 
 * GasLiftResult result = calc.calculate();
 * System.out.println("Optimal GLR: " + result.getOptimalGLR() + " Sm3/Sm3");
 * System.out.println("Oil rate: " + result.getOilRate() + " Sm3/day");
 * System.out.println("Injection rate: " + result.getInjectionRate() + " MSm3/day");
 * System.out.println("Compression power: " + result.getCompressionPower() + " kW");
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see ArtificialLiftScreener
 * @see ESPCalculator
 */
public class GasLiftCalculator implements Serializable {
  private static final long serialVersionUID = 1000L;

  // Physical constants
  private static final double GRAVITY = 9.81; // m/s²
  private static final double GAS_CONSTANT = 8.314; // J/(mol·K)
  private static final double STANDARD_PRESSURE = 1.01325; // bara
  private static final double STANDARD_TEMPERATURE = 288.15; // K (15°C)

  // Well parameters
  private double reservoirPressure = 250.0; // bara
  private double reservoirTemperature = 358.15; // K
  private double wellheadPressure = 20.0; // bara
  private double wellheadTemperature = 313.15; // K
  private double wellDepth = 3000.0; // m (TVD)
  private double tubingID = 0.1; // m
  private double tubingRoughness = 4.5e-5; // m (relative roughness)

  // Reservoir/fluid parameters
  private double productivityIndex = 5.0; // Sm3/day/bar
  private double bubblePointPressure = 180.0; // bara
  private double oilDensityStd = 850.0; // kg/m³ at standard conditions
  private double waterDensity = 1020.0; // kg/m³
  private double gasMolecularWeight = 20.0; // kg/kmol
  private double formationGOR = 100.0; // Sm3/Sm3
  private double waterCut = 0.3; // fraction

  // Gas lift system parameters
  private double injectionPressure = 100.0; // bara (available at surface)
  private double injectionTemperature = 313.15; // K
  private double valveSpacing = 300.0; // m default
  private double valvePressureDrop = 3.0; // bar per valve
  private int maxValves = 8;
  private double compressorEfficiency = 0.75;
  private double compressionRatio = 3.0; // typical

  // Calculated results
  private double optimalGLR = 0.0;
  private double maxProductionRate = 0.0;
  private double injectionRateAtOptimal = 0.0;
  private double compressionPower = 0.0;
  private List<ValvePosition> valvePositions = new ArrayList<>();
  private List<PerformancePoint> performanceCurve = new ArrayList<>();

  /**
   * Creates a new gas lift calculator with default parameters.
   */
  public GasLiftCalculator() {
    // Default constructor
  }

  /**
   * Performs complete gas lift calculation.
   *
   * @return gas lift result with all calculated values
   */
  public GasLiftResult calculate() {
    // 1. Calculate natural flow conditions (zero gas lift)
    double naturalFlowRate = calculateNaturalFlowRate();

    // 2. Generate performance curve (production vs. GLR)
    generatePerformanceCurve();

    // 3. Find optimal GLR
    findOptimalGLR();

    // 4. Calculate valve positions for unloading
    calculateValvePositions();

    // 5. Calculate compression power
    calculateCompressionPower();

    // Build result
    GasLiftResult result = new GasLiftResult();
    result.naturalFlowRate = naturalFlowRate;
    result.optimalGLR = this.optimalGLR;
    result.oilRateAtOptimal = this.maxProductionRate;
    result.injectionRateAtOptimal = this.injectionRateAtOptimal;
    result.compressionPower = this.compressionPower;
    result.valvePositions = new ArrayList<>(this.valvePositions);
    result.performanceCurve = new ArrayList<>(this.performanceCurve);
    result.liftIncrease = naturalFlowRate > 0 ? (maxProductionRate / naturalFlowRate - 1.0) * 100.0
        : Double.MAX_VALUE;
    result.feasible = maxProductionRate > naturalFlowRate && injectionRateAtOptimal > 0;

    return result;
  }

  /**
   * Calculates natural flow rate without gas lift using inflow performance.
   *
   * @return natural flow rate in Sm3/day
   */
  private double calculateNaturalFlowRate() {
    // Simplified: assume well can flow if Pres > Pwf_min where Pwf_min is hydrostatic
    double avgFluidDensity = calculateAverageFluidDensity(formationGOR);
    double hydrostaticGradient = avgFluidDensity * GRAVITY / 1e5; // bar/m

    double minBHP = wellheadPressure + hydrostaticGradient * wellDepth;

    if (minBHP >= reservoirPressure) {
      return 0.0; // Well cannot flow naturally
    }

    // Use productivity index (simplified linear IPR)
    double drawdown = reservoirPressure - minBHP;
    return productivityIndex * drawdown;
  }

  /**
   * Generates performance curve: production rate vs. total GLR.
   */
  private void generatePerformanceCurve() {
    performanceCurve.clear();

    // Test GLR range from formation GOR to 10x formation GOR
    double minGLR = formationGOR;
    double maxGLR = Math.max(formationGOR * 10, 2000.0);
    int points = 50;

    for (int i = 0; i <= points; i++) {
      double testGLR = minGLR + (maxGLR - minGLR) * i / points;
      double productionRate = calculateProductionAtGLR(testGLR);

      PerformancePoint point = new PerformancePoint();
      point.totalGLR = testGLR;
      point.productionRate = productionRate;
      point.injectionGLR = testGLR - formationGOR;
      point.injectionRate = productionRate * (testGLR - formationGOR) / 1e6; // MSm3/day

      performanceCurve.add(point);
    }
  }

  /**
   * Calculates production rate at a given total GLR.
   *
   * <p>
   * Uses iterative solution of the well flow equation with multiphase flow correlations.
   * </p>
   *
   * @param totalGLR total gas-liquid ratio (Sm3/Sm3)
   * @return production rate (Sm3/day liquid)
   */
  private double calculateProductionAtGLR(double totalGLR) {
    // Iterative solution: find flow rate where IPR and VLP intersect

    double tolerance = 0.01; // 1% tolerance
    int maxIterations = 100;

    double qMin = 0.0;
    double qMax = productivityIndex * reservoirPressure; // Maximum theoretical

    for (int iter = 0; iter < maxIterations; iter++) {
      double qTest = (qMin + qMax) / 2.0;

      // Calculate BHP from IPR
      double pwfIPR = reservoirPressure - qTest / productivityIndex;

      // Calculate BHP from VLP (outflow)
      double pwfVLP = calculateBHPfromVLP(qTest, totalGLR);

      if (Math.abs(pwfIPR - pwfVLP) < tolerance * reservoirPressure) {
        // Check if solution is physical
        if (pwfIPR > wellheadPressure && pwfIPR < reservoirPressure) {
          return qTest;
        }
      }

      if (pwfIPR > pwfVLP) {
        qMin = qTest; // Need higher rate
      } else {
        qMax = qTest; // Need lower rate
      }
    }

    return qMax; // Return best estimate
  }

  /**
   * Calculates bottom-hole pressure from VLP (vertical lift performance).
   *
   * <p>
   * Uses simplified multiphase flow correlation based on Hagedorn-Brown with modifications for gas
   * lift.
   * </p>
   *
   * @param liquidRate liquid rate (Sm3/day)
   * @param totalGLR total GLR (Sm3/Sm3)
   * @return bottom-hole pressure (bara)
   */
  private double calculateBHPfromVLP(double liquidRate, double totalGLR) {
    // Discretize well into segments
    int segments = 20;
    double segmentLength = wellDepth / segments;

    double pressure = wellheadPressure;
    double temperature = wellheadTemperature;
    double tempGradient = (reservoirTemperature - wellheadTemperature) / wellDepth;

    for (int i = 0; i < segments; i++) {
      double depth = (i + 0.5) * segmentLength;
      temperature = wellheadTemperature + tempGradient * depth;

      // Calculate mixture properties at segment conditions
      double avgDensity = calculateMixtureDensity(pressure, temperature, totalGLR);
      double frictionGradient =
          calculateFrictionGradient(liquidRate, totalGLR, pressure, temperature);

      // Pressure increase = hydrostatic + friction
      double dP = (avgDensity * GRAVITY + frictionGradient) * segmentLength / 1e5; // bar
      pressure += dP;
    }

    return pressure;
  }

  /**
   * Calculates mixture density at given conditions.
   *
   * @param pressure pressure (bara)
   * @param temperature temperature (K)
   * @param totalGLR total GLR (Sm3/Sm3)
   * @return mixture density (kg/m³)
   */
  private double calculateMixtureDensity(double pressure, double temperature, double totalGLR) {
    // Oil density (simplified - no pressure correction)
    double oilDensity = oilDensityStd * (1 - 0.0005 * (temperature - STANDARD_TEMPERATURE));

    // Gas density from ideal gas law with Z-factor
    double zFactor = calculateZfactor(pressure, temperature);
    double gasDensity =
        pressure * 1e5 * gasMolecularWeight / (zFactor * GAS_CONSTANT * temperature * 1000.0);

    // In-situ GLR (gas expands at lower pressure)
    double gasVolumeRatio =
        STANDARD_PRESSURE / pressure * temperature / STANDARD_TEMPERATURE * zFactor;
    double insituGLR = totalGLR * gasVolumeRatio;

    // Liquid holdup (simplified correlation)
    double liquidHoldup = calculateLiquidHoldup(insituGLR);

    // Water contribution
    double liquidDensity = (1 - waterCut) * oilDensity + waterCut * waterDensity;

    // Mixture density
    return liquidHoldup * liquidDensity + (1 - liquidHoldup) * gasDensity;
  }

  /**
   * Calculates Z-factor using Standing-Katz correlation (simplified).
   *
   * @param pressure pressure (bara)
   * @param temperature temperature (K)
   * @return Z-factor
   */
  private double calculateZfactor(double pressure, double temperature) {
    // Simplified: assume pseudo-critical properties for natural gas
    double ppc = 46.0; // bara
    double tpc = 190.0; // K

    double ppr = pressure / ppc;
    double tpr = temperature / tpc;

    // Hall-Yarborough correlation (simplified)
    double z = 1.0;
    if (ppr > 0.1) {
      z = 0.27 * ppr / (tpr * calculateHallYarboroughY(ppr, tpr));
      if (z < 0.3 || z > 2.0) {
        z = 1.0 - 0.3 * ppr / tpr; // Fallback to simple correlation
      }
    }

    return Math.max(0.3, Math.min(z, 1.5));
  }

  /**
   * Hall-Yarborough Y function (simplified).
   *
   * @param ppr pseudo-reduced pressure
   * @param tpr pseudo-reduced temperature
   * @return Y parameter
   */
  private double calculateHallYarboroughY(double ppr, double tpr) {
    // Simplified iterative solution
    double t = 1.0 / tpr;
    double y = 0.1;

    for (int i = 0; i < 10; i++) {
      double f = -0.06125 * ppr * t * Math.exp(-1.2 * (1 - t) * (1 - t))
          + (y + y * y + y * y * y - y * y * y * y) / Math.pow(1 - y, 3)
          - (14.76 * t - 9.76 * t * t + 4.58 * t * t * t) * y * y
          + (90.7 * t - 242.2 * t * t + 42.4 * t * t * t) * Math.pow(y, 2.18 + 2.82 * t);

      double df = (1 + 4 * y + 4 * y * y - 4 * y * y * y + y * y * y * y) / Math.pow(1 - y, 4)
          - 2 * (14.76 * t - 9.76 * t * t + 4.58 * t * t * t) * y + (2.18 + 2.82 * t)
              * (90.7 * t - 242.2 * t * t + 42.4 * t * t * t) * Math.pow(y, 1.18 + 2.82 * t);

      if (Math.abs(df) < 1e-10) {
        break;
      }
      y = y - f / df;
      y = Math.max(0.01, Math.min(y, 0.99));
    }

    return Math.max(0.01, y);
  }

  /**
   * Calculates liquid holdup using simplified drift-flux correlation.
   *
   * @param insituGLR in-situ gas-liquid ratio (m³/m³)
   * @return liquid holdup (fraction)
   */
  private double calculateLiquidHoldup(double insituGLR) {
    // Superficial velocity ratio
    double vsg = insituGLR / (1 + insituGLR);

    // Drift-flux correlation (simplified Zuber-Findlay)
    double c0 = 1.2; // Distribution coefficient
    double vd = 0.35; // Drift velocity coefficient

    double holdup = (1 - vsg) / (c0 + vd * (1 - vsg));

    return Math.max(0.05, Math.min(holdup, 0.95));
  }

  /**
   * Calculates friction pressure gradient.
   *
   * @param liquidRate liquid rate (Sm3/day)
   * @param totalGLR total GLR (Sm3/Sm3)
   * @param pressure pressure (bara)
   * @param temperature temperature (K)
   * @return friction gradient (Pa/m)
   */
  private double calculateFrictionGradient(double liquidRate, double totalGLR, double pressure,
      double temperature) {
    // Convert to m³/s
    double qLiquid = liquidRate / 86400.0;

    // Cross-sectional area
    double area = Math.PI * tubingID * tubingID / 4.0;

    // Superficial velocities
    double vsl = qLiquid / area;

    double gasVolumeRatio = STANDARD_PRESSURE / pressure * temperature / STANDARD_TEMPERATURE;
    double qGas = qLiquid * totalGLR * gasVolumeRatio / 86400.0;
    double vsg = qGas / area;

    double vmix = vsl + vsg;

    // Mixture viscosity (simplified)
    double liquidVisc = 0.001; // Pa·s (1 cP)
    double gasVisc = 0.00002; // Pa·s (0.02 cP)
    double liquidHoldup = calculateLiquidHoldup(totalGLR * gasVolumeRatio);
    double mixVisc = liquidHoldup * liquidVisc + (1 - liquidHoldup) * gasVisc;

    // Reynolds number
    double mixDensity = calculateMixtureDensity(pressure, temperature, totalGLR);
    double reynolds = mixDensity * vmix * tubingID / mixVisc;

    // Friction factor (Colebrook-White)
    double frictionFactor = calculateFrictionFactor(reynolds);

    // Friction gradient
    return frictionFactor * mixDensity * vmix * vmix / (2.0 * tubingID);
  }

  /**
   * Calculates Darcy friction factor using Colebrook-White equation.
   *
   * @param reynolds Reynolds number
   * @return friction factor
   */
  private double calculateFrictionFactor(double reynolds) {
    if (reynolds < 2300) {
      // Laminar flow
      return 64.0 / reynolds;
    }

    // Turbulent flow - Colebrook-White (Haaland approximation)
    double relRoughness = tubingRoughness / tubingID;
    double term1 = Math.pow(relRoughness / 3.7, 1.11);
    double term2 = 6.9 / reynolds;

    double f = Math.pow(-1.8 * Math.log10(term1 + term2), -2);

    return Math.max(0.008, Math.min(f, 0.1));
  }

  /**
   * Finds optimal GLR that maximizes production.
   */
  private void findOptimalGLR() {
    double maxRate = 0.0;
    double optGLR = formationGOR;

    for (PerformancePoint point : performanceCurve) {
      if (point.productionRate > maxRate) {
        maxRate = point.productionRate;
        optGLR = point.totalGLR;
      }
    }

    this.optimalGLR = optGLR;
    this.maxProductionRate = maxRate;
    this.injectionRateAtOptimal = maxRate * (optGLR - formationGOR) / 1e6; // MSm3/day
  }

  /**
   * Calculates valve positions for gas lift unloading.
   *
   * <p>
   * Uses the Timmerman method for unloading valve design:
   * <ul>
   * <li>First valve: Positioned to unload kill fluid</li>
   * <li>Subsequent valves: Spaced for progressive unloading</li>
   * <li>Operating valve: Deepest valve, typically near perforations</li>
   * </ul>
   * </p>
   */
  private void calculateValvePositions() {
    valvePositions.clear();

    // Kill fluid gradient (heavier than production fluid)
    double killFluidDensity = 1200.0; // kg/m³
    double killFluidGradient = killFluidDensity * GRAVITY / 1e5; // bar/m

    // Available injection pressure at surface
    double surfaceInjPressure = injectionPressure;

    // Safety factor for valve setting
    double safetyFactor = 0.9;

    double currentDepth = 0.0;
    int valveNumber = 0;

    while (currentDepth < wellDepth && valveNumber < maxValves) {
      valveNumber++;

      // Calculate valve depth based on available injection pressure
      double pressureAtDepth;
      if (valveNumber == 1) {
        // First valve: unload from static conditions
        pressureAtDepth = wellheadPressure + killFluidGradient * currentDepth;
        double availablePressure = surfaceInjPressure * safetyFactor;

        // Find depth where injection can overcome kill fluid
        double valveDepth = (availablePressure - wellheadPressure) / killFluidGradient;
        valveDepth = Math.min(valveDepth, wellDepth);

        currentDepth = valveDepth;
      } else {
        // Subsequent valves: spaced based on production gradient
        double productionGradient = calculateAverageFluidDensity(formationGOR) * GRAVITY / 1e5;
        double availablePressure =
            surfaceInjPressure * safetyFactor - valvePressureDrop * (valveNumber - 1);

        double nextValveDepth = currentDepth
            + (availablePressure - wellheadPressure - productionGradient * currentDepth)
                / productionGradient;

        nextValveDepth = Math.min(nextValveDepth, wellDepth);
        nextValveDepth = Math.max(nextValveDepth, currentDepth + 50); // Min 50m spacing

        currentDepth = nextValveDepth;
      }

      ValvePosition valve = new ValvePosition();
      valve.valveNumber = valveNumber;
      valve.depth = currentDepth;
      valve.openingPressure = surfaceInjPressure * safetyFactor - (valveNumber - 1) * 1.5; // Decrease
                                                                                           // for
                                                                                           // each
                                                                                           // valve
      valve.closingPressure = valve.openingPressure - 0.5;
      valve.isOperatingValve = (currentDepth >= wellDepth * 0.95);

      valvePositions.add(valve);

      if (valve.isOperatingValve) {
        break;
      }
    }
  }

  /**
   * Calculates average fluid density for gradient estimation.
   *
   * @param glr gas-liquid ratio (Sm3/Sm3)
   * @return average density (kg/m³)
   */
  private double calculateAverageFluidDensity(double glr) {
    double avgPressure = (wellheadPressure + reservoirPressure) / 2.0;
    double avgTemperature = (wellheadTemperature + reservoirTemperature) / 2.0;
    return calculateMixtureDensity(avgPressure, avgTemperature, glr);
  }

  /**
   * Calculates compression power for gas lift injection.
   */
  private void calculateCompressionPower() {
    if (injectionRateAtOptimal <= 0) {
      this.compressionPower = 0.0;
      return;
    }

    // Convert MSm3/day to Sm3/s
    double gasRateSm3s = injectionRateAtOptimal * 1e6 / 86400.0;

    // Isentropic compression power
    // P = (n/(n-1)) * P1 * Q * ((P2/P1)^((n-1)/n) - 1)

    double p1 = 5.0 * 1e5; // Suction pressure (Pa) - assume low pressure source
    double p2 = injectionPressure * 1e5; // Discharge pressure (Pa)

    double n = 1.3; // Polytropic exponent for natural gas
    double ratio = Math.pow(p2 / p1, (n - 1) / n) - 1;

    double isentropicPower = (n / (n - 1)) * p1 * gasRateSm3s * ratio;

    // Account for efficiency
    this.compressionPower = isentropicPower / compressorEfficiency / 1000.0; // kW
  }

  // ===================== Setters =====================

  /**
   * Sets reservoir pressure.
   *
   * @param pressure pressure value
   * @param unit pressure unit ("bara", "psia", etc.)
   * @return this for chaining
   */
  public GasLiftCalculator setReservoirPressure(double pressure, String unit) {
    this.reservoirPressure = convertPressure(pressure, unit);
    return this;
  }

  /**
   * Sets reservoir temperature.
   *
   * @param temperature temperature value
   * @param unit temperature unit ("K", "C", "F")
   * @return this for chaining
   */
  public GasLiftCalculator setReservoirTemperature(double temperature, String unit) {
    this.reservoirTemperature = convertTemperature(temperature, unit);
    return this;
  }

  /**
   * Sets wellhead pressure.
   *
   * @param pressure pressure value
   * @param unit pressure unit
   * @return this for chaining
   */
  public GasLiftCalculator setWellheadPressure(double pressure, String unit) {
    this.wellheadPressure = convertPressure(pressure, unit);
    return this;
  }

  /**
   * Sets well depth (true vertical depth).
   *
   * @param depth depth value
   * @param unit depth unit ("m", "ft")
   * @return this for chaining
   */
  public GasLiftCalculator setWellDepth(double depth, String unit) {
    this.wellDepth = convertLength(depth, unit);
    return this;
  }

  /**
   * Sets tubing inner diameter.
   *
   * @param diameter diameter value
   * @param unit diameter unit ("m", "in", "mm")
   * @return this for chaining
   */
  public GasLiftCalculator setTubingID(double diameter, String unit) {
    this.tubingID = convertLength(diameter, unit);
    return this;
  }

  /**
   * Sets productivity index.
   *
   * @param pi productivity index (Sm3/day/bar)
   * @return this for chaining
   */
  public GasLiftCalculator setProductivityIndex(double pi) {
    this.productivityIndex = pi;
    return this;
  }

  /**
   * Sets oil gravity.
   *
   * @param gravity gravity value
   * @param unit gravity unit ("API", "SG")
   * @return this for chaining
   */
  public GasLiftCalculator setOilGravity(double gravity, String unit) {
    if ("API".equalsIgnoreCase(unit)) {
      this.oilDensityStd = 141.5 / (gravity + 131.5) * 1000.0; // kg/m³
    } else {
      this.oilDensityStd = gravity * 1000.0; // SG to kg/m³
    }
    return this;
  }

  /**
   * Sets water cut.
   *
   * @param waterCut water cut fraction (0-1)
   * @return this for chaining
   */
  public GasLiftCalculator setWaterCut(double waterCut) {
    this.waterCut = Math.max(0, Math.min(waterCut, 1.0));
    return this;
  }

  /**
   * Sets formation GOR.
   *
   * @param gor formation GOR (Sm3/Sm3)
   * @return this for chaining
   */
  public GasLiftCalculator setFormationGOR(double gor) {
    this.formationGOR = gor;
    return this;
  }

  /**
   * Sets gas lift injection pressure.
   *
   * @param pressure injection pressure value
   * @param unit pressure unit
   * @return this for chaining
   */
  public GasLiftCalculator setInjectionPressure(double pressure, String unit) {
    this.injectionPressure = convertPressure(pressure, unit);
    return this;
  }

  /**
   * Sets bubble point pressure.
   *
   * @param pressure bubble point pressure value
   * @param unit pressure unit
   * @return this for chaining
   */
  public GasLiftCalculator setBubblePointPressure(double pressure, String unit) {
    this.bubblePointPressure = convertPressure(pressure, unit);
    return this;
  }

  /**
   * Sets compressor efficiency.
   *
   * @param efficiency efficiency (0-1)
   * @return this for chaining
   */
  public GasLiftCalculator setCompressorEfficiency(double efficiency) {
    this.compressorEfficiency = Math.max(0.1, Math.min(efficiency, 1.0));
    return this;
  }

  // ===================== Unit Conversions =====================

  private double convertPressure(double value, String unit) {
    if (unit == null) {
      return value;
    }
    switch (unit.toLowerCase()) {
      case "bara":
      case "bar":
        return value;
      case "psia":
        return value * 0.0689476;
      case "kpa":
        return value / 100.0;
      case "mpa":
        return value * 10.0;
      default:
        return value;
    }
  }

  private double convertTemperature(double value, String unit) {
    if (unit == null) {
      return value;
    }
    switch (unit.toUpperCase()) {
      case "K":
        return value;
      case "C":
        return value + 273.15;
      case "F":
        return (value - 32) * 5.0 / 9.0 + 273.15;
      default:
        return value;
    }
  }

  private double convertLength(double value, String unit) {
    if (unit == null) {
      return value;
    }
    switch (unit.toLowerCase()) {
      case "m":
        return value;
      case "ft":
        return value * 0.3048;
      case "in":
        return value * 0.0254;
      case "mm":
        return value / 1000.0;
      default:
        return value;
    }
  }

  // ===================== Result Classes =====================

  /**
   * Gas lift calculation result.
   */
  public static class GasLiftResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Natural flow rate without gas lift (Sm3/day). */
    public double naturalFlowRate;

    /** Optimal total GLR (Sm3/Sm3). */
    public double optimalGLR;

    /** Oil production rate at optimal GLR (Sm3/day). */
    public double oilRateAtOptimal;

    /** Gas injection rate at optimal (MSm3/day). */
    public double injectionRateAtOptimal;

    /** Compression power required (kW). */
    public double compressionPower;

    /** Production increase percentage. */
    public double liftIncrease;

    /** Whether gas lift is feasible. */
    public boolean feasible;

    /** Valve positions. */
    public List<ValvePosition> valvePositions = new ArrayList<>();

    /** Performance curve data. */
    public List<PerformancePoint> performanceCurve = new ArrayList<>();

    /**
     * Gets optimal GLR.
     *
     * @return optimal GLR (Sm3/Sm3)
     */
    public double getOptimalGLR() {
      return optimalGLR;
    }

    /**
     * Gets oil rate at optimal GLR.
     *
     * @return oil rate (Sm3/day)
     */
    public double getOilRate() {
      return oilRateAtOptimal;
    }

    /**
     * Gets injection rate at optimal GLR.
     *
     * @return injection rate (MSm3/day)
     */
    public double getInjectionRate() {
      return injectionRateAtOptimal;
    }

    /**
     * Gets compression power.
     *
     * @return power (kW)
     */
    public double getCompressionPower() {
      return compressionPower;
    }

    /**
     * Gets number of gas lift valves.
     *
     * @return valve count
     */
    public int getValveCount() {
      return valvePositions.size();
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Gas Lift Design Result\n");
      sb.append("======================\n");
      sb.append(String.format("Natural flow rate: %.1f Sm3/day%n", naturalFlowRate));
      sb.append(String.format("Optimal GLR: %.1f Sm3/Sm3%n", optimalGLR));
      sb.append(String.format("Oil rate at optimal: %.1f Sm3/day%n", oilRateAtOptimal));
      sb.append(String.format("Injection rate: %.3f MSm3/day%n", injectionRateAtOptimal));
      sb.append(String.format("Compression power: %.1f kW%n", compressionPower));
      sb.append(String.format("Lift increase: %.1f%%%n", liftIncrease));
      sb.append(String.format("Valve count: %d%n", valvePositions.size()));
      sb.append(String.format("Feasible: %s%n", feasible));
      return sb.toString();
    }
  }

  /**
   * Gas lift valve position.
   */
  public static class ValvePosition implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Valve number (1 = top). */
    public int valveNumber;

    /** Valve depth (m TVD). */
    public double depth;

    /** Opening pressure (bara). */
    public double openingPressure;

    /** Closing pressure (bara). */
    public double closingPressure;

    /** Whether this is the operating valve. */
    public boolean isOperatingValve;

    @Override
    public String toString() {
      return String.format("Valve %d: %.1f m, Open: %.1f bara, Close: %.1f bara%s", valveNumber,
          depth, openingPressure, closingPressure, isOperatingValve ? " [OPERATING]" : "");
    }
  }

  /**
   * Performance curve point.
   */
  public static class PerformancePoint implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Total GLR (Sm3/Sm3). */
    public double totalGLR;

    /** Injection GLR (Sm3/Sm3). */
    public double injectionGLR;

    /** Production rate (Sm3/day). */
    public double productionRate;

    /** Injection rate (MSm3/day). */
    public double injectionRate;
  }
}
