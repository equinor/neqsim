package neqsim.process.fielddevelopment.screening;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Artificial lift screening and selection tool.
 *
 * <p>
 * This class provides comprehensive artificial lift method comparison and selection based on
 * reservoir and well conditions. Supported methods include:
 * <ul>
 * <li><b>Natural Flow:</b> Baseline case with no artificial lift</li>
 * <li><b>Gas Lift:</b> Continuous or intermittent gas injection</li>
 * <li><b>ESP (Electrical Submersible Pump):</b> Downhole electric pump</li>
 * <li><b>Rod Pump:</b> Sucker rod pump (beam pump)</li>
 * <li><b>PCP (Progressive Cavity Pump):</b> For viscous fluids</li>
 * <li><b>Jet Pump:</b> Hydraulic power fluid system</li>
 * </ul>
 *
 * <h2>Screening Methodology</h2>
 * <p>
 * The screening process follows industry best practices:
 * </p>
 * <ol>
 * <li><b>Technical Screening:</b> Eliminates infeasible methods based on operating envelope</li>
 * <li><b>Performance Calculation:</b> Estimates production rate for each method</li>
 * <li><b>Economic Ranking:</b> Ranks methods by NPV or cost per barrel lifted</li>
 * <li><b>Risk Assessment:</b> Considers reliability and operational complexity</li>
 * </ol>
 *
 * <h2>Operating Envelope Limits</h2>
 * <table border="1">
 * <caption>Operating Envelope Limits by Method</caption>
 * <tr>
 * <th>Method</th>
 * <th>Max Depth (m)</th>
 * <th>Max Rate (Sm3/d)</th>
 * <th>Max GOR</th>
 * <th>Max Temp (°C)</th>
 * <th>Max Visc (cP)</th>
 * </tr>
 * <tr>
 * <td>Gas Lift</td>
 * <td>5000</td>
 * <td>5000</td>
 * <td>5000</td>
 * <td>200</td>
 * <td>50</td>
 * </tr>
 * <tr>
 * <td>ESP</td>
 * <td>4500</td>
 * <td>15000</td>
 * <td>500</td>
 * <td>180</td>
 * <td>200</td>
 * </tr>
 * <tr>
 * <td>Rod Pump</td>
 * <td>3000</td>
 * <td>500</td>
 * <td>200</td>
 * <td>150</td>
 * <td>1000</td>
 * </tr>
 * <tr>
 * <td>PCP</td>
 * <td>2500</td>
 * <td>800</td>
 * <td>100</td>
 * <td>130</td>
 * <td>10000</td>
 * </tr>
 * <tr>
 * <td>Jet Pump</td>
 * <td>5000</td>
 * <td>2000</td>
 * <td>1000</td>
 * <td>200</td>
 * <td>50</td>
 * </tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * ArtificialLiftScreener screener = new ArtificialLiftScreener();
 * screener.setReservoirPressure(250.0, "bara");
 * screener.setReservoirTemperature(90.0, "C");
 * screener.setWellheadPressure(15.0, "bara");
 * screener.setWellDepth(2800.0, "m");
 * screener.setProductivityIndex(8.0);
 * screener.setOilGravity(32.0, "API");
 * screener.setWaterCut(0.40);
 * screener.setFormationGOR(150.0);
 * screener.setOilViscosity(5.0, "cP");
 * screener.setGasLiftAvailable(true);
 * screener.setElectricityAvailable(true);
 * 
 * ScreeningResult result = screener.screen();
 * 
 * System.out.println("Recommended: " + result.getRecommendedMethod());
 * for (MethodResult method : result.getAllMethods()) {
 *   System.out.println(method);
 * }
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see GasLiftCalculator
 */
public class ArtificialLiftScreener implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Artificial lift method types.
   */
  public enum LiftMethod {
    /** Natural flow - no artificial lift. */
    NATURAL_FLOW("Natural Flow", 0, 0),
    /** Continuous gas lift. */
    GAS_LIFT("Gas Lift", 50000, 200),
    /** Electrical submersible pump. */
    ESP("ESP", 150000, 500),
    /** Sucker rod pump. */
    ROD_PUMP("Rod Pump", 30000, 100),
    /** Progressive cavity pump. */
    PCP("PCP", 40000, 150),
    /** Hydraulic jet pump. */
    JET_PUMP("Jet Pump", 60000, 250);

    private final String displayName;
    private final double typicalCapex; // USD
    private final double typicalOpex; // USD/day

    LiftMethod(String displayName, double typicalCapex, double typicalOpex) {
      this.displayName = displayName;
      this.typicalCapex = typicalCapex;
      this.typicalOpex = typicalOpex;
    }

    /**
     * Gets display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Gets typical CAPEX.
     *
     * @return CAPEX in USD
     */
    public double getTypicalCapex() {
      return typicalCapex;
    }

    /**
     * Gets typical OPEX.
     *
     * @return OPEX in USD/day
     */
    public double getTypicalOpex() {
      return typicalOpex;
    }
  }

  // Well parameters
  private double reservoirPressure = 250.0; // bara
  private double reservoirTemperature = 363.15; // K (90°C)
  private double wellheadPressure = 15.0; // bara
  private double wellheadTemperature = 313.15; // K (40°C)
  private double wellDepth = 2800.0; // m TVD
  private double tubingID = 0.1; // m

  // Reservoir/fluid parameters
  private double productivityIndex = 8.0; // Sm3/day/bar
  private double bubblePointPressure = 150.0; // bara
  private double oilDensityStd = 870.0; // kg/m³
  private double waterDensity = 1020.0; // kg/m³
  private double formationGOR = 150.0; // Sm3/Sm3
  private double waterCut = 0.40; // fraction
  private double oilViscosity = 5.0; // cP at reservoir conditions

  // Infrastructure availability
  private boolean gasLiftAvailable = true;
  private boolean electricityAvailable = true;
  private boolean hydraulicPowerAvailable = false;
  private double gasLiftPressure = 100.0; // bara
  private double electricityPower = 500.0; // kW available

  // Economic parameters
  private double oilPrice = 70.0; // USD/bbl
  private double gasCost = 0.10; // USD/Sm3
  private double electricityCost = 0.10; // USD/kWh
  private double discountRate = 0.10; // 10%
  private int economicLife = 10; // years

  // Target production rate (if specified)
  private double targetRate = 0.0; // Sm3/day (0 = maximize)

  /**
   * Creates a new artificial lift screener.
   */
  public ArtificialLiftScreener() {
    // Default constructor
  }

  /**
   * Performs artificial lift screening.
   *
   * @return screening result with ranked methods
   */
  public ScreeningResult screen() {
    ScreeningResult result = new ScreeningResult();
    result.wellDepth = this.wellDepth;
    result.reservoirPressure = this.reservoirPressure;
    result.reservoirTemperature = this.reservoirTemperature - 273.15; // °C
    result.productivityIndex = this.productivityIndex;
    result.waterCut = this.waterCut;

    // Calculate natural flow baseline
    MethodResult naturalFlow = evaluateNaturalFlow();
    result.addMethod(naturalFlow);
    result.naturalFlowRate = naturalFlow.productionRate;

    // Evaluate each artificial lift method
    if (gasLiftAvailable) {
      MethodResult gasLift = evaluateGasLift();
      if (gasLift != null) {
        result.addMethod(gasLift);
      }
    }

    if (electricityAvailable) {
      MethodResult esp = evaluateESP();
      if (esp != null) {
        result.addMethod(esp);
      }

      MethodResult rodPump = evaluateRodPump();
      if (rodPump != null) {
        result.addMethod(rodPump);
      }

      MethodResult pcp = evaluatePCP();
      if (pcp != null) {
        result.addMethod(pcp);
      }
    }

    if (hydraulicPowerAvailable || gasLiftAvailable) {
      MethodResult jetPump = evaluateJetPump();
      if (jetPump != null) {
        result.addMethod(jetPump);
      }
    }

    // Rank methods
    result.rankMethods();

    return result;
  }

  /**
   * Evaluates natural flow performance.
   *
   * @return method result
   */
  private MethodResult evaluateNaturalFlow() {
    MethodResult result = new MethodResult(LiftMethod.NATURAL_FLOW);

    // Calculate if well can flow naturally
    double avgFluidDensity = calculateAverageFluidDensity(formationGOR);
    double hydrostaticGradient = avgFluidDensity * 9.81 / 1e5; // bar/m

    double minBHP = wellheadPressure + hydrostaticGradient * wellDepth;

    if (minBHP >= reservoirPressure) {
      result.feasible = false;
      result.productionRate = 0;
      result.infeasibilityReason = "Insufficient reservoir pressure for natural flow";
    } else {
      result.feasible = true;
      double drawdown = reservoirPressure - minBHP;
      result.productionRate = productivityIndex * drawdown;
      result.powerConsumption = 0;
      result.capex = 0;
      result.opex = 0;
    }

    result.calculateEconomics(oilPrice, discountRate, economicLife);

    return result;
  }

  /**
   * Evaluates gas lift performance using GasLiftCalculator.
   *
   * @return method result or null if infeasible
   */
  private MethodResult evaluateGasLift() {
    MethodResult result = new MethodResult(LiftMethod.GAS_LIFT);

    // Check operating envelope
    double tempC = reservoirTemperature - 273.15;
    if (wellDepth > 5000) {
      result.feasible = false;
      result.infeasibilityReason = "Depth exceeds gas lift limit (5000m)";
      return result;
    }
    if (tempC > 200) {
      result.feasible = false;
      result.infeasibilityReason = "Temperature exceeds gas lift limit (200°C)";
      return result;
    }
    if (oilViscosity > 50) {
      result.feasible = false;
      result.infeasibilityReason = "Viscosity too high for gas lift (>50 cP)";
      return result;
    }

    // Use GasLiftCalculator
    GasLiftCalculator calc = new GasLiftCalculator();
    calc.setReservoirPressure(reservoirPressure, "bara");
    calc.setReservoirTemperature(reservoirTemperature, "K");
    calc.setWellheadPressure(wellheadPressure, "bara");
    calc.setWellDepth(wellDepth, "m");
    calc.setTubingID(tubingID, "m");
    calc.setProductivityIndex(productivityIndex);
    calc.setOilGravity(oilDensityStd / 1000.0, "SG");
    calc.setWaterCut(waterCut);
    calc.setFormationGOR(formationGOR);
    calc.setInjectionPressure(gasLiftPressure, "bara");

    GasLiftCalculator.GasLiftResult glResult = calc.calculate();

    result.feasible = glResult.feasible;
    result.productionRate = glResult.oilRateAtOptimal;
    result.powerConsumption = glResult.compressionPower;
    result.liftIncrease = glResult.liftIncrease;

    // Economics
    result.capex = LiftMethod.GAS_LIFT.getTypicalCapex() + glResult.getValveCount() * 5000; // $5000
                                                                                            // per
                                                                                            // valve
    double gasConsumption = glResult.injectionRateAtOptimal * 1e6; // Sm3/day
    result.opex = LiftMethod.GAS_LIFT.getTypicalOpex() + gasConsumption * gasCost
        + glResult.compressionPower * 24 * electricityCost;

    result.additionalInfo = String.format("GLR=%.0f Sm3/Sm3, Inj=%.2f MSm3/d, Valves=%d",
        glResult.optimalGLR, glResult.injectionRateAtOptimal, glResult.getValveCount());

    result.calculateEconomics(oilPrice, discountRate, economicLife);

    return result;
  }

  /**
   * Evaluates ESP performance.
   *
   * @return method result or null if infeasible
   */
  private MethodResult evaluateESP() {
    MethodResult result = new MethodResult(LiftMethod.ESP);

    // Check operating envelope
    double tempC = reservoirTemperature - 273.15;
    if (wellDepth > 4500) {
      result.feasible = false;
      result.infeasibilityReason = "Depth exceeds ESP limit (4500m)";
      return result;
    }
    if (tempC > 180) {
      result.feasible = false;
      result.infeasibilityReason = "Temperature exceeds ESP limit (180°C)";
      return result;
    }
    if (oilViscosity > 200) {
      result.feasible = false;
      result.infeasibilityReason = "Viscosity too high for ESP (>200 cP)";
      return result;
    }

    // Calculate GVF at pump intake
    double intakePressure = estimateIntakePressure();
    double gvf = calculateGVF(intakePressure);

    if (gvf > 0.40) {
      result.feasible = false;
      result.infeasibilityReason = String.format("GVF too high for ESP (%.1f%% > 40%%)", gvf * 100);
      return result;
    }

    result.feasible = true;

    // Calculate ESP performance
    // ESP can overcome hydrostatic head - essentially target maximum production
    double maxDrawdown = reservoirPressure - bubblePointPressure * 0.8; // Limit at 80% Pb
    double espRate = productivityIndex * maxDrawdown;

    // Limit by available power
    double hydraulicPower = calculateHydraulicPower(espRate);
    double requiredPower = hydraulicPower / 0.55; // 55% efficiency

    if (requiredPower > electricityPower) {
      espRate = espRate * electricityPower / requiredPower;
      requiredPower = electricityPower;
    }

    result.productionRate = espRate;
    result.powerConsumption = requiredPower;

    // Economics
    result.capex = LiftMethod.ESP.getTypicalCapex();
    if (tempC > 150) {
      result.capex *= 1.5; // High temp ESP premium
    }
    result.opex = LiftMethod.ESP.getTypicalOpex() + requiredPower * 24 * electricityCost;

    result.additionalInfo = String.format("GVF=%.1f%%, Power=%.0f kW", gvf * 100, requiredPower);

    result.calculateEconomics(oilPrice, discountRate, economicLife);

    return result;
  }

  /**
   * Evaluates rod pump (beam pump) performance.
   *
   * @return method result
   */
  private MethodResult evaluateRodPump() {
    MethodResult result = new MethodResult(LiftMethod.ROD_PUMP);

    // Check operating envelope
    double tempC = reservoirTemperature - 273.15;
    if (wellDepth > 3000) {
      result.feasible = false;
      result.infeasibilityReason = "Depth exceeds rod pump limit (3000m)";
      return result;
    }
    if (tempC > 150) {
      result.feasible = false;
      result.infeasibilityReason = "Temperature exceeds rod pump limit (150°C)";
      return result;
    }
    if (formationGOR > 200) {
      result.feasible = false;
      result.infeasibilityReason = "GOR too high for rod pump (>200)";
      return result;
    }

    result.feasible = true;

    // Rod pumps are limited by rate
    double maxRodPumpRate = 500.0; // Sm3/day typical max
    double maxDrawdown = reservoirPressure - wellheadPressure - wellDepth * 0.1; // Approx
    double potentialRate = productivityIndex * maxDrawdown;

    result.productionRate = Math.min(potentialRate, maxRodPumpRate);
    result.powerConsumption = result.productionRate * 0.15; // ~0.15 kW per Sm3/day

    // Economics - lower CAPEX but higher maintenance
    result.capex = LiftMethod.ROD_PUMP.getTypicalCapex();
    result.opex =
        LiftMethod.ROD_PUMP.getTypicalOpex() + result.powerConsumption * 24 * electricityCost;

    result.additionalInfo = String.format("Rate limited to %.0f Sm3/d", result.productionRate);

    result.calculateEconomics(oilPrice, discountRate, economicLife);

    return result;
  }

  /**
   * Evaluates PCP (progressive cavity pump) performance.
   *
   * @return method result
   */
  private MethodResult evaluatePCP() {
    MethodResult result = new MethodResult(LiftMethod.PCP);

    // Check operating envelope - PCP good for high viscosity
    double tempC = reservoirTemperature - 273.15;
    if (wellDepth > 2500) {
      result.feasible = false;
      result.infeasibilityReason = "Depth exceeds PCP limit (2500m)";
      return result;
    }
    if (tempC > 130) {
      result.feasible = false;
      result.infeasibilityReason = "Temperature exceeds PCP elastomer limit (130°C)";
      return result;
    }
    if (formationGOR > 100) {
      result.feasible = false;
      result.infeasibilityReason = "GOR too high for PCP (>100)";
      return result;
    }

    result.feasible = true;

    // PCP handles high viscosity well
    double maxPCPRate = 800.0; // Sm3/day
    double maxDrawdown = reservoirPressure - wellheadPressure - wellDepth * 0.1;
    double potentialRate = productivityIndex * maxDrawdown;

    // PCP efficiency improves with viscosity
    double viscosityBonus = oilViscosity > 50 ? 1.2 : 1.0;

    result.productionRate = Math.min(potentialRate * viscosityBonus, maxPCPRate);
    result.powerConsumption = result.productionRate * 0.20; // ~0.20 kW per Sm3/day

    result.capex = LiftMethod.PCP.getTypicalCapex();
    result.opex = LiftMethod.PCP.getTypicalOpex() + result.powerConsumption * 24 * electricityCost;

    result.additionalInfo = String.format("Good for viscosity %.0f cP", oilViscosity);

    result.calculateEconomics(oilPrice, discountRate, economicLife);

    return result;
  }

  /**
   * Evaluates jet pump performance.
   *
   * @return method result
   */
  private MethodResult evaluateJetPump() {
    MethodResult result = new MethodResult(LiftMethod.JET_PUMP);

    // Check operating envelope
    double tempC = reservoirTemperature - 273.15;
    if (wellDepth > 5000) {
      result.feasible = false;
      result.infeasibilityReason = "Depth exceeds jet pump limit (5000m)";
      return result;
    }
    if (tempC > 200) {
      result.feasible = false;
      result.infeasibilityReason = "Temperature exceeds jet pump limit (200°C)";
      return result;
    }
    if (oilViscosity > 50) {
      result.feasible = false;
      result.infeasibilityReason = "Viscosity too high for jet pump (>50 cP)";
      return result;
    }

    result.feasible = true;

    // Jet pump performance
    double maxJetPumpRate = 2000.0; // Sm3/day
    double maxDrawdown = reservoirPressure - bubblePointPressure * 0.8;
    double potentialRate = productivityIndex * maxDrawdown;

    // Jet pump efficiency is lower (~25-30%)
    result.productionRate = Math.min(potentialRate * 0.7, maxJetPumpRate);
    result.powerConsumption = result.productionRate * 0.35; // Higher power due to low efficiency

    result.capex = LiftMethod.JET_PUMP.getTypicalCapex();
    result.opex =
        LiftMethod.JET_PUMP.getTypicalOpex() + result.powerConsumption * 24 * electricityCost;

    result.additionalInfo = "Good for deviated wells, no moving parts downhole";

    result.calculateEconomics(oilPrice, discountRate, economicLife);

    return result;
  }

  // ===================== Helper Methods =====================

  private double calculateAverageFluidDensity(double glr) {
    double liquidDensity = (1 - waterCut) * oilDensityStd + waterCut * waterDensity;
    double avgPressure = (wellheadPressure + reservoirPressure) / 2.0;

    // Gas density
    double gasDensity = avgPressure * 1e5 * 20.0 / (8314.0 * 350.0); // Approximate

    // Simple mixture
    double gasVolumeRatio = 1.01325 / avgPressure * 350.0 / 288.15;
    double insituGLR = glr * gasVolumeRatio;

    double liquidHoldup = 1.0 / (1.0 + insituGLR * 0.5);
    return liquidHoldup * liquidDensity + (1 - liquidHoldup) * gasDensity;
  }

  private double estimateIntakePressure() {
    // Estimate pump intake pressure at 80% of well depth
    double pumpDepth = wellDepth * 0.8;
    double avgDensity = calculateAverageFluidDensity(formationGOR);
    return wellheadPressure + avgDensity * 9.81 * pumpDepth / 1e5;
  }

  private double calculateGVF(double pressure) {
    // Calculate gas void fraction at pump intake
    double gasVolumeRatio = 1.01325 / pressure * (reservoirTemperature - 50) / 288.15;
    double insituGLR = formationGOR * gasVolumeRatio;

    return insituGLR / (1.0 + insituGLR);
  }

  private double calculateHydraulicPower(double rate) {
    // Hydraulic power = ρ g Q H / 1000 (kW)
    double avgDensity = (1 - waterCut) * oilDensityStd + waterCut * waterDensity;
    double head = wellDepth; // Simplified
    return avgDensity * 9.81 * (rate / 86400.0) * head / 1000.0;
  }

  // ===================== Setters =====================

  /**
   * Sets reservoir pressure.
   *
   * @param pressure pressure value
   * @param unit unit ("bara", "psia")
   * @return this for chaining
   */
  public ArtificialLiftScreener setReservoirPressure(double pressure, String unit) {
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
  public ArtificialLiftScreener setReservoirTemperature(double temperature, String unit) {
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
   * Sets wellhead pressure.
   *
   * @param pressure pressure value
   * @param unit unit
   * @return this for chaining
   */
  public ArtificialLiftScreener setWellheadPressure(double pressure, String unit) {
    if ("psia".equalsIgnoreCase(unit)) {
      this.wellheadPressure = pressure * 0.0689476;
    } else {
      this.wellheadPressure = pressure;
    }
    return this;
  }

  /**
   * Sets well depth (TVD).
   *
   * @param depth depth value
   * @param unit unit ("m", "ft")
   * @return this for chaining
   */
  public ArtificialLiftScreener setWellDepth(double depth, String unit) {
    if ("ft".equalsIgnoreCase(unit)) {
      this.wellDepth = depth * 0.3048;
    } else {
      this.wellDepth = depth;
    }
    return this;
  }

  /**
   * Sets productivity index.
   *
   * @param pi productivity index (Sm3/day/bar)
   * @return this for chaining
   */
  public ArtificialLiftScreener setProductivityIndex(double pi) {
    this.productivityIndex = pi;
    return this;
  }

  /**
   * Sets oil gravity.
   *
   * @param gravity gravity value
   * @param unit unit ("API", "SG")
   * @return this for chaining
   */
  public ArtificialLiftScreener setOilGravity(double gravity, String unit) {
    if ("API".equalsIgnoreCase(unit)) {
      this.oilDensityStd = 141.5 / (gravity + 131.5) * 1000.0;
    } else {
      this.oilDensityStd = gravity * 1000.0;
    }
    return this;
  }

  /**
   * Sets water cut.
   *
   * @param waterCut water cut fraction (0-1)
   * @return this for chaining
   */
  public ArtificialLiftScreener setWaterCut(double waterCut) {
    this.waterCut = Math.max(0, Math.min(waterCut, 1.0));
    return this;
  }

  /**
   * Sets formation GOR.
   *
   * @param gor formation GOR (Sm3/Sm3)
   * @return this for chaining
   */
  public ArtificialLiftScreener setFormationGOR(double gor) {
    this.formationGOR = gor;
    return this;
  }

  /**
   * Sets oil viscosity.
   *
   * @param viscosity viscosity value
   * @param unit unit ("cP", "Pa.s")
   * @return this for chaining
   */
  public ArtificialLiftScreener setOilViscosity(double viscosity, String unit) {
    if ("Pa.s".equalsIgnoreCase(unit) || "Pas".equalsIgnoreCase(unit)) {
      this.oilViscosity = viscosity * 1000.0;
    } else {
      this.oilViscosity = viscosity;
    }
    return this;
  }

  /**
   * Sets gas lift availability.
   *
   * @param available true if gas lift is available
   * @return this for chaining
   */
  public ArtificialLiftScreener setGasLiftAvailable(boolean available) {
    this.gasLiftAvailable = available;
    return this;
  }

  /**
   * Sets electricity availability.
   *
   * @param available true if electricity is available
   * @return this for chaining
   */
  public ArtificialLiftScreener setElectricityAvailable(boolean available) {
    this.electricityAvailable = available;
    return this;
  }

  /**
   * Sets gas lift pressure.
   *
   * @param pressure pressure (bara)
   * @return this for chaining
   */
  public ArtificialLiftScreener setGasLiftPressure(double pressure) {
    this.gasLiftPressure = pressure;
    return this;
  }

  /**
   * Sets available electricity power.
   *
   * @param power power (kW)
   * @return this for chaining
   */
  public ArtificialLiftScreener setElectricityPower(double power) {
    this.electricityPower = power;
    return this;
  }

  /**
   * Sets oil price.
   *
   * @param price price (USD/bbl)
   * @return this for chaining
   */
  public ArtificialLiftScreener setOilPrice(double price) {
    this.oilPrice = price;
    return this;
  }

  /**
   * Sets bubble point pressure.
   *
   * @param pressure bubble point (bara)
   * @return this for chaining
   */
  public ArtificialLiftScreener setBubblePointPressure(double pressure) {
    this.bubblePointPressure = pressure;
    return this;
  }

  /**
   * Sets tubing ID.
   *
   * @param diameter diameter (m)
   * @return this for chaining
   */
  public ArtificialLiftScreener setTubingID(double diameter) {
    this.tubingID = diameter;
    return this;
  }

  // ===================== Result Classes =====================

  /**
   * Screening result containing all evaluated methods.
   */
  public static class ScreeningResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Well depth (m). */
    public double wellDepth;
    /** Reservoir pressure (bara). */
    public double reservoirPressure;
    /** Reservoir temperature (°C). */
    public double reservoirTemperature;
    /** Productivity index (Sm3/day/bar). */
    public double productivityIndex;
    /** Water cut (fraction). */
    public double waterCut;
    /** Natural flow rate (Sm3/day). */
    public double naturalFlowRate;

    private List<MethodResult> methods = new ArrayList<>();
    private MethodResult recommendedMethod;

    /**
     * Adds a method result.
     *
     * @param method method result
     */
    public void addMethod(MethodResult method) {
      methods.add(method);
    }

    /**
     * Ranks methods by production rate and economics.
     */
    public void rankMethods() {
      // Sort by NPV (descending)
      methods.sort(Comparator.comparingDouble((MethodResult m) -> m.npv).reversed());

      // Assign ranks
      int rank = 1;
      for (MethodResult method : methods) {
        if (method.feasible) {
          method.rank = rank++;
        } else {
          method.rank = 0; // Infeasible
        }
      }

      // Recommended is highest ranked feasible method
      for (MethodResult method : methods) {
        if (method.feasible && method.productionRate > 0) {
          recommendedMethod = method;
          break;
        }
      }
    }

    /**
     * Gets recommended method.
     *
     * @return recommended lift method
     */
    public LiftMethod getRecommendedMethod() {
      return recommendedMethod != null ? recommendedMethod.method : LiftMethod.NATURAL_FLOW;
    }

    /**
     * Gets recommended method result.
     *
     * @return method result
     */
    public MethodResult getRecommendedMethodResult() {
      return recommendedMethod;
    }

    /**
     * Gets all methods.
     *
     * @return list of method results
     */
    public List<MethodResult> getAllMethods() {
      return new ArrayList<>(methods);
    }

    /**
     * Gets feasible methods.
     *
     * @return list of feasible method results
     */
    public List<MethodResult> getFeasibleMethods() {
      List<MethodResult> feasible = new ArrayList<>();
      for (MethodResult m : methods) {
        if (m.feasible) {
          feasible.add(m);
        }
      }
      return feasible;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Artificial Lift Screening Results\n");
      sb.append("==================================\n");
      sb.append(String.format("Well depth: %.0f m%n", wellDepth));
      sb.append(String.format("Reservoir pressure: %.0f bara%n", reservoirPressure));
      sb.append(String.format("Reservoir temperature: %.0f °C%n", reservoirTemperature));
      sb.append(String.format("Productivity index: %.1f Sm3/day/bar%n", productivityIndex));
      sb.append(String.format("Water cut: %.0f%%%n", waterCut * 100));
      sb.append(String.format("Natural flow rate: %.0f Sm3/day%n", naturalFlowRate));
      sb.append("\n");
      sb.append(String.format("Recommended: %s%n",
          recommendedMethod != null ? recommendedMethod.method.getDisplayName() : "None"));
      sb.append("\n");
      sb.append("Method Rankings:\n");
      for (MethodResult method : methods) {
        sb.append(String.format("  %s%n", method));
      }
      return sb.toString();
    }
  }

  /**
   * Result for a single artificial lift method.
   */
  public static class MethodResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Lift method. */
    public LiftMethod method;
    /** Whether method is feasible. */
    public boolean feasible = true;
    /** Reason if infeasible. */
    public String infeasibilityReason = "";
    /** Production rate (Sm3/day). */
    public double productionRate;
    /** Power consumption (kW). */
    public double powerConsumption;
    /** Production increase vs. natural flow (%). */
    public double liftIncrease;
    /** CAPEX (USD). */
    public double capex;
    /** OPEX (USD/day). */
    public double opex;
    /** NPV (USD). */
    public double npv;
    /** Ranking (1 = best). */
    public int rank;
    /** Additional information. */
    public String additionalInfo = "";

    /**
     * Creates a method result.
     *
     * @param method lift method
     */
    public MethodResult(LiftMethod method) {
      this.method = method;
    }

    /**
     * Calculates NPV for the method.
     *
     * @param oilPrice oil price (USD/bbl)
     * @param discountRate annual discount rate
     * @param years economic life
     */
    public void calculateEconomics(double oilPrice, double discountRate, int years) {
      if (!feasible || productionRate <= 0) {
        this.npv = Double.NEGATIVE_INFINITY;
        return;
      }

      // Convert Sm3/day to bbl/day (1 Sm3 ≈ 6.29 bbl)
      double bblPerDay = productionRate * 6.29;

      // Annual revenue
      double annualRevenue = bblPerDay * 365 * oilPrice;
      double annualOpex = opex * 365;
      double annualCashFlow = annualRevenue - annualOpex;

      // NPV calculation
      double npvSum = -capex;
      for (int year = 1; year <= years; year++) {
        npvSum += annualCashFlow / Math.pow(1 + discountRate, year);
      }

      this.npv = npvSum;
    }

    /**
     * Gets method name.
     *
     * @return method display name
     */
    public String getMethodName() {
      return method.getDisplayName();
    }

    @Override
    public String toString() {
      if (!feasible) {
        return String.format("%s: INFEASIBLE - %s", method.getDisplayName(), infeasibilityReason);
      }

      return String.format("#%d %s: %.0f Sm3/d, %.0f kW, NPV=%.0f MUSD %s", rank,
          method.getDisplayName(), productionRate, powerConsumption, npv / 1e6, additionalInfo);
    }
  }
}
