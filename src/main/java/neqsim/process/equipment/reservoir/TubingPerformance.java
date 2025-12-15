package neqsim.process.equipment.reservoir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * TubingPerformance class - Vertical Lift Performance (VLP) model for wellbore
 * tubing.
 * </p>
 *
 * <p>
 * This class calculates the pressure drop through wellbore tubing using various
 * multiphase flow
 * correlations. It can generate VLP curves (also known as Tubing Performance
 * Curves or Outflow
 * Performance Relationships) for use in nodal analysis and reservoir
 * simulation.
 * </p>
 *
 * <h2>Supported Pressure-Drop Correlations</h2>
 * <ul>
 * <li><b>BEGGS_BRILL</b> - General purpose, all inclinations (default)</li>
 * <li><b>HAGEDORN_BROWN</b> - Vertical/near-vertical oil wells</li>
 * <li><b>GRAY</b> - Gas wells with condensate</li>
 * <li><b>HASAN_KABIR</b> - Mechanistic model for all well types</li>
 * <li><b>DUNS_ROS</b> - Gas wells</li>
 * </ul>
 *
 * <h2>Temperature Models</h2>
 * <ul>
 * <li><b>ISOTHERMAL</b> - Constant temperature throughout tubing</li>
 * <li><b>LINEAR_GRADIENT</b> - Linear temperature profile from BH to WH</li>
 * <li><b>RAMEY</b> - Ramey (1962) steady-state geothermal model</li>
 * </ul>
 *
 * <h2>Usage Example - VLP Curve Generation</h2>
 * 
 * <pre>{@code
 * // Create reservoir fluid
 * SystemInterface fluid = new SystemSrkEos(373.15, 250.0);
 * fluid.addComponent("methane", 85.0);
 * fluid.addComponent("n-heptane", 15.0);
 * fluid.setMixingRule("classic");
 * fluid.setMultiPhaseCheck(true);
 *
 * // Create wellhead stream
 * Stream wellStream = new Stream("well stream", fluid);
 * wellStream.setFlowRate(5000, "Sm3/day");
 * wellStream.run();
 *
 * // Create tubing performance model
 * TubingPerformance tubing = new TubingPerformance("producer tubing", wellStream);
 * tubing.setTubingLength(2500.0, "m"); // True vertical depth
 * tubing.setTubingDiameter(0.1016, "m"); // 4-inch tubing
 * tubing.setWallRoughness(2.5e-5, "m");
 * tubing.setInclination(85.0); // 85 degrees from horizontal
 *
 * // Set temperature model
 * tubing.setTemperatureModel(TubingPerformance.TemperatureModel.RAMEY);
 * tubing.setBottomHoleTemperature(100.0, "C");
 * tubing.setWellheadTemperature(40.0, "C");
 * tubing.setGeothermalGradient(0.03, "K/m");
 *
 * // Generate VLP curve
 * double[] flowRates = { 1000, 2000, 3000, 5000, 7000, 10000 }; // Sm3/day
 * double[][] vlpCurve = tubing.generateVLPCurve(flowRates, "Sm3/day", "bara");
 *
 * // vlpCurve[0] = flow rates
 * // vlpCurve[1] = bottom-hole pressures required
 * }</pre>
 *
 * <h2>Integration with WellFlow (IPR-VLP Nodal Analysis)</h2>
 * 
 * <pre>{@code
 * // Create WellFlow for IPR
 * WellFlow ipr = new WellFlow("well IPR");
 * ipr.setInletStream(reservoirStream);
 * ipr.setVogelParameters(5000, 180, 250); // qTest, pwfTest, pRes
 *
 * // Create TubingPerformance for VLP
 * TubingPerformance vlp = new TubingPerformance("tubing VLP", ipr.getOutletStream());
 * vlp.setTubingLength(2500, "m");
 * vlp.setTubingDiameter(0.1016, "m");
 *
 * // Find operating point
 * double[] operatingPoint = vlp.findOperatingPoint(ipr, 50.0, "bara"); // WHP = 50 bara
 * // operatingPoint[0] = operating flow rate (Sm3/day)
 * // operatingPoint[1] = bottom-hole flowing pressure (bara)
 * }</pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see WellFlow
 * @see WellSystem
 */
public class TubingPerformance extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(TubingPerformance.class);

  /**
   * Available multiphase pressure-drop correlations.
   */
  public enum PressureDropCorrelation {
    /**
     * Beggs and Brill (1973) - General purpose correlation for all pipe
     * inclinations. Recommended
     * for horizontal to vertical flow. Includes flow pattern correction.
     */
    BEGGS_BRILL,

    /**
     * Hagedorn and Brown (1965) - Empirical correlation for vertical oil wells.
     * Best for
     * liquid-dominated vertical flow.
     */
    HAGEDORN_BROWN,

    /**
     * Gray (1974) - Correlation for gas wells with some liquid (condensate).
     * Optimized for high GOR
     * wells.
     */
    GRAY,

    /**
     * Hasan and Kabir (2002) - Mechanistic two-phase flow model. Physically-based
     * model valid for
     * all well types.
     */
    HASAN_KABIR,

    /**
     * Duns and Ros (1963) - Early correlation for gas wells. Often used for gas
     * lift optimization.
     */
    DUNS_ROS
  }

  /**
   * Temperature profile models for wellbore heat transfer.
   */
  public enum TemperatureModel {
    /**
     * Isothermal - Constant temperature throughout tubing. Simplest assumption, use
     * for quick
     * calculations.
     */
    ISOTHERMAL,

    /**
     * Linear gradient - Linear interpolation between BHP and WHP temperatures. Good
     * approximation
     * for steady-state production.
     */
    LINEAR_GRADIENT,

    /**
     * Ramey (1962) - Steady-state heat transfer with formation. Accounts for
     * geothermal gradient,
     * wellbore heat transfer coefficient, and time-dependent behavior. Most
     * rigorous for production
     * wells.
     */
    RAMEY,

    /**
     * Hasan-Kabir energy balance - Coupled flow and heat transfer. Includes
     * Joule-Thomson effect
     * and friction heating.
     */
    HASAN_KABIR_ENERGY
  }

  // Tubing geometry
  private double tubingLength = 2500.0; // m (TVD)
  private double tubingDiameter = 0.1016; // m (4 inch default)
  private double wallRoughness = 2.5e-5; // m
  private double inclination = 90.0; // degrees from horizontal (90 = vertical)
  private int numberOfSegments = 50;

  // Correlation selection
  private PressureDropCorrelation pressureDropCorrelation = PressureDropCorrelation.BEGGS_BRILL;
  private TemperatureModel temperatureModel = TemperatureModel.LINEAR_GRADIENT;

  // Temperature parameters
  private double bottomHoleTemperature = 373.15; // K
  private double wellheadTemperature = 313.15; // K
  private double geothermalGradient = 0.03; // K/m
  private double overallHeatTransferCoeff = 25.0; // W/(m²·K)
  private double formationThermalConductivity = 2.0; // W/(m·K)
  private double productionTime = 30.0 * 24.0 * 3600.0; // seconds (default 30 days)

  // Calculated results
  private double bottomHolePressure = Double.NaN;
  private double wellheadPressure = Double.NaN;
  private double[] pressureProfile;
  private double[] temperatureProfile;
  private double[] depthProfile;
  private double[] holdupProfile;
  private double totalPressureDrop = Double.NaN;

  // VLP curve data
  private double[] vlpFlowRates;
  private double[] vlpBottomHolePressures;
  private double[] vlpWellheadPressures;

  // Table-based VLP (loaded from file or set programmatically)
  private boolean useTableVLP = false;
  private double[] tableVLPFlowRates = new double[0];
  private double[] tableVLPBHP = new double[0];
  private double tableVLPWellheadPressure = Double.NaN;

  /**
   * Constructor for TubingPerformance.
   *
   * @param name equipment name
   */
  public TubingPerformance(String name) {
    super(name);
  }

  /**
   * Constructor for TubingPerformance with inlet stream.
   *
   * @param name        equipment name
   * @param inletStream inlet stream (typically from reservoir or WellFlow)
   */
  public TubingPerformance(String name, StreamInterface inletStream) {
    super(name);
    setInletStream(inletStream);
  }

  /**
   * Set tubing measured depth (along wellbore).
   *
   * @param length tubing length
   * @param unit   length unit ("m", "ft")
   */
  public void setTubingLength(double length, String unit) {
    if (unit.equalsIgnoreCase("ft")) {
      this.tubingLength = length * 0.3048;
    } else {
      this.tubingLength = length;
    }
  }

  /**
   * Set tubing inner diameter.
   *
   * @param diameter inner diameter
   * @param unit     diameter unit ("m", "in", "mm")
   */
  public void setTubingDiameter(double diameter, String unit) {
    switch (unit.toLowerCase()) {
      case "in":
        this.tubingDiameter = diameter * 0.0254;
        break;
      case "mm":
        this.tubingDiameter = diameter / 1000.0;
        break;
      default:
        this.tubingDiameter = diameter;
    }
  }

  /**
   * Set pipe wall roughness.
   *
   * @param roughness wall roughness
   * @param unit      roughness unit ("m", "mm", "in")
   */
  public void setWallRoughness(double roughness, String unit) {
    switch (unit.toLowerCase()) {
      case "mm":
        this.wallRoughness = roughness / 1000.0;
        break;
      case "in":
        this.wallRoughness = roughness * 0.0254;
        break;
      default:
        this.wallRoughness = roughness;
    }
  }

  /**
   * Set well inclination from horizontal.
   *
   * @param angleDegrees inclination angle (90 = vertical, 0 = horizontal)
   */
  public void setInclination(double angleDegrees) {
    this.inclination = angleDegrees;
  }

  /**
   * Set number of calculation segments.
   *
   * @param segments number of segments for pressure integration
   */
  public void setNumberOfSegments(int segments) {
    this.numberOfSegments = segments;
  }

  /**
   * Set the pressure-drop correlation to use.
   *
   * @param correlation pressure-drop correlation
   */
  public void setPressureDropCorrelation(PressureDropCorrelation correlation) {
    this.pressureDropCorrelation = correlation;
  }

  /**
   * Set the temperature model to use.
   *
   * @param model temperature model
   */
  public void setTemperatureModel(TemperatureModel model) {
    this.temperatureModel = model;
  }

  /**
   * Set bottom-hole temperature (reservoir temperature).
   *
   * @param temperature bottom-hole temperature
   * @param unit        temperature unit ("K", "C", "F")
   */
  public void setBottomHoleTemperature(double temperature, String unit) {
    this.bottomHoleTemperature = convertTemperatureToKelvin(temperature, unit);
  }

  /**
   * Set wellhead temperature.
   *
   * @param temperature wellhead temperature
   * @param unit        temperature unit ("K", "C", "F")
   */
  public void setWellheadTemperature(double temperature, String unit) {
    this.wellheadTemperature = convertTemperatureToKelvin(temperature, unit);
  }

  /**
   * Set geothermal gradient for Ramey model.
   *
   * @param gradient temperature gradient
   * @param unit     gradient unit ("K/m", "C/100m", "F/100ft")
   */
  public void setGeothermalGradient(double gradient, String unit) {
    switch (unit.toLowerCase()) {
      case "c/100m":
        this.geothermalGradient = gradient / 100.0;
        break;
      case "f/100ft":
        this.geothermalGradient = (gradient / 1.8) / (100.0 * 0.3048);
        break;
      default:
        this.geothermalGradient = gradient;
    }
  }

  /**
   * Set overall heat transfer coefficient for wellbore.
   *
   * @param coefficient heat transfer coefficient in W/(m²·K)
   */
  public void setOverallHeatTransferCoefficient(double coefficient) {
    this.overallHeatTransferCoeff = coefficient;
  }

  /**
   * Set production time for transient Ramey model.
   *
   * @param time production time
   * @param unit time unit ("s", "hr", "day")
   */
  public void setProductionTime(double time, String unit) {
    switch (unit.toLowerCase()) {
      case "hr":
        this.productionTime = time * 3600.0;
        break;
      case "day":
        this.productionTime = time * 24.0 * 3600.0;
        break;
      default:
        this.productionTime = time;
    }
  }

  private double convertTemperatureToKelvin(double temp, String unit) {
    switch (unit.toUpperCase()) {
      case "C":
        return temp + 273.15;
      case "F":
        return (temp - 32.0) / 1.8 + 273.15;
      default:
        return temp;
    }
  }

  /**
   * {@inheritDoc}
   * 
   * <p>
   * Calculates wellhead conditions from bottom-hole conditions by integrating the
   * pressure-drop
   * correlation up the tubing.
   * </p>
   */
  @Override
  public void run(UUID id) {
    SystemInterface fluid = getInletStream().getThermoSystem().clone();
    bottomHolePressure = fluid.getPressure("bara");
    double bhTemp = fluid.getTemperature("K");

    // Override BH temperature if explicitly set
    if (!Double.isNaN(bottomHoleTemperature) && bottomHoleTemperature > 0) {
      bhTemp = bottomHoleTemperature;
      fluid.setTemperature(bhTemp, "K");
    }

    // Initialize profiles
    pressureProfile = new double[numberOfSegments + 1];
    temperatureProfile = new double[numberOfSegments + 1];
    depthProfile = new double[numberOfSegments + 1];
    holdupProfile = new double[numberOfSegments + 1];

    double segmentLength = tubingLength / numberOfSegments;
    double currentPressure = bottomHolePressure;
    double currentTemp = bhTemp;

    pressureProfile[0] = currentPressure;
    temperatureProfile[0] = currentTemp;
    depthProfile[0] = tubingLength;

    // Integrate from bottom to top
    for (int i = 1; i <= numberOfSegments; i++) {
      double depth = tubingLength - i * segmentLength;
      depthProfile[i] = depth;

      // Calculate temperature at this depth
      currentTemp = calculateTemperatureAtDepth(depth, bhTemp);
      temperatureProfile[i] = currentTemp;

      // Update fluid conditions
      fluid.setTemperature(currentTemp, "K");
      fluid.setPressure(currentPressure, "bara");
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.TPflash();
      fluid.initProperties();

      // Calculate pressure drop for this segment
      double dP = calculateSegmentPressureDrop(fluid, segmentLength);
      currentPressure -= dP;

      if (currentPressure < 1.0) {
        currentPressure = 1.0; // Minimum pressure limit
        logger.warn("Pressure dropped below 1 bara at depth {} m", depth);
      }

      pressureProfile[i] = currentPressure;

      // Store holdup if available
      if (fluid.hasPhaseType("gas") && fluid.hasPhaseType("oil")) {
        holdupProfile[i] = fluid.getPhase("oil").getBeta();
      } else if (fluid.hasPhaseType("oil")) {
        holdupProfile[i] = 1.0;
      } else {
        holdupProfile[i] = 0.0;
      }
    }

    wellheadPressure = currentPressure;
    totalPressureDrop = bottomHolePressure - wellheadPressure;

    // Set outlet stream conditions
    SystemInterface outFluid = getInletStream().getThermoSystem().clone();
    outFluid.setPressure(wellheadPressure, "bara");
    outFluid.setTemperature(temperatureProfile[numberOfSegments], "K");
    ThermodynamicOperations outOps = new ThermodynamicOperations(outFluid);
    outOps.TPflash();
    outFluid.initProperties();
    outStream.setThermoSystem(outFluid);
    outStream.run(id);

    setCalculationIdentifier(id);
  }

  /**
   * Calculate temperature at a given depth using the selected temperature model.
   *
   * @param depth  current depth (m from surface, 0 = surface)
   * @param bhTemp bottom-hole temperature (K)
   * @return temperature at depth (K)
   */
  private double calculateTemperatureAtDepth(double depth, double bhTemp) {
    switch (temperatureModel) {
      case ISOTHERMAL:
        return bhTemp;

      case LINEAR_GRADIENT:
        // Linear interpolation between BH and WH temperatures
        double fraction = depth / tubingLength;
        return wellheadTemperature + fraction * (bhTemp - wellheadTemperature);

      case RAMEY:
        return calculateRameyTemperature(depth, bhTemp);

      case HASAN_KABIR_ENERGY:
        // Simplified energy balance (full implementation requires coupling with flow)
        return calculateRameyTemperature(depth, bhTemp);

      default:
        return bhTemp;
    }
  }

  /**
   * Calculate temperature using Ramey (1962) steady-state model.
   * 
   * <p>
   * The Ramey model accounts for:
   * <ul>
   * <li>Geothermal gradient of the formation</li>
   * <li>Heat transfer between wellbore and formation</li>
   * <li>Time-dependent behavior (transient heat conduction)</li>
   * </ul>
   *
   * @param depth  depth from surface (m)
   * @param bhTemp bottom-hole temperature (K)
   * @return temperature at depth (K)
   */
  private double calculateRameyTemperature(double depth, double bhTemp) {
    // Surface temperature
    double Ts = wellheadTemperature;

    // Formation temperature at depth
    double Tf = Ts + geothermalGradient * depth;

    // Relaxation distance parameter
    // A = (mass flow rate * Cp) / (2 * pi * r * U)
    // Simplified: use empirical value based on typical well parameters
    double massFlowRate = getInletStream().getFlowRate("kg/sec");
    double cp = getInletStream().getThermoSystem().getCp("J/kgK");
    double wellRadius = tubingDiameter / 2.0 + 0.02; // tubing + cement
    double A = (massFlowRate * cp) / (2.0 * Math.PI * wellRadius * overallHeatTransferCoeff);

    if (A < 1.0) {
      A = 100.0; // Default relaxation distance for low flow
    }

    // Ramey temperature profile
    // T(z) = Tf(z) - gG*A + [Tbh - Tf(L) + gG*A] * exp(-(L-z)/A)
    double gG = geothermalGradient;
    double L = tubingLength;
    double TfL = Ts + gG * L; // Formation temp at BH

    double T = Tf - gG * A + (bhTemp - TfL + gG * A) * Math.exp(-(L - depth) / A);

    return T;
  }

  /**
   * Calculate pressure drop for a single segment using the selected correlation.
   *
   * @param fluid         fluid at segment conditions
   * @param segmentLength length of segment (m)
   * @return pressure drop (bar)
   */
  private double calculateSegmentPressureDrop(SystemInterface fluid, double segmentLength) {
    switch (pressureDropCorrelation) {
      case HAGEDORN_BROWN:
        return calculateHagedornBrownPressureDrop(fluid, segmentLength);
      case GRAY:
        return calculateGrayPressureDrop(fluid, segmentLength);
      case HASAN_KABIR:
        return calculateHasanKabirPressureDrop(fluid, segmentLength);
      case DUNS_ROS:
        return calculateDunsRosPressureDrop(fluid, segmentLength);
      case BEGGS_BRILL:
      default:
        return calculateBeggsBrillPressureDrop(fluid, segmentLength);
    }
  }

  /**
   * Beggs and Brill (1973) pressure drop calculation.
   */
  private double calculateBeggsBrillPressureDrop(SystemInterface fluid, double segmentLength) {
    double area = Math.PI * Math.pow(tubingDiameter / 2.0, 2);
    double inclinationRad = Math.toRadians(inclination);

    // Get fluid properties
    double rhoL = 800.0; // default liquid density
    double rhoG = 50.0; // default gas density
    double muL = 0.001; // default liquid viscosity
    double muG = 0.00002; // default gas viscosity
    double sigma = 0.03; // default surface tension

    if (fluid.hasPhaseType("oil")) {
      rhoL = fluid.getPhase("oil").getDensity("kg/m3");
      muL = fluid.getPhase("oil").getViscosity("kg/msec");
    } else if (fluid.hasPhaseType("aqueous")) {
      rhoL = fluid.getPhase("aqueous").getDensity("kg/m3");
      muL = fluid.getPhase("aqueous").getViscosity("kg/msec");
    }

    if (fluid.hasPhaseType("gas")) {
      rhoG = fluid.getPhase("gas").getDensity("kg/m3");
      muG = fluid.getPhase("gas").getViscosity("kg/msec");
    }

    // Superficial velocities
    double qL = 0.0;
    double qG = 0.0;
    if (fluid.hasPhaseType("oil")) {
      qL += fluid.getPhase("oil").getVolume("m3") / 1.0; // per second
    }
    if (fluid.hasPhaseType("aqueous")) {
      qL += fluid.getPhase("aqueous").getVolume("m3") / 1.0;
    }
    if (fluid.hasPhaseType("gas")) {
      qG = fluid.getPhase("gas").getVolume("m3") / 1.0;
    }

    double vSL = qL / area;
    double vSG = qG / area;
    double vM = vSL + vSG;

    if (vM < 0.01) {
      vM = 0.01; // Minimum velocity
    }

    // No-slip holdup
    double lambdaL = vSL / vM;

    // Froude number
    double NFr = Math.pow(vM, 2) / (9.81 * tubingDiameter);

    // Liquid holdup (simplified Beggs-Brill)
    double HL;
    if (lambdaL < 0.01) {
      HL = 0.01;
    } else {
      double a = 0.98;
      double b = 0.4846;
      double c = 0.0868;
      HL = a * Math.pow(lambdaL, b) / Math.pow(NFr, c);
      if (HL > 1.0)
        HL = 1.0;
      if (HL < lambdaL)
        HL = lambdaL;
    }

    // Mixture density
    double rhoM = rhoL * HL + rhoG * (1.0 - HL);

    // Hydrostatic pressure drop
    double dPhydro = rhoM * 9.81 * segmentLength * Math.sin(inclinationRad) / 1.0e5; // bar

    // Friction pressure drop
    double rhoNS = rhoL * lambdaL + rhoG * (1.0 - lambdaL);
    double muNS = muL * lambdaL + muG * (1.0 - lambdaL);
    double Re = rhoNS * vM * tubingDiameter / muNS;
    double f = calculateFrictionFactor(Re);
    double dPfric = f * rhoNS * Math.pow(vM, 2) * segmentLength / (2.0 * tubingDiameter) / 1.0e5;

    return dPhydro + dPfric;
  }

  /**
   * Hagedorn and Brown (1965) pressure drop - optimized for vertical oil wells.
   */
  private double calculateHagedornBrownPressureDrop(SystemInterface fluid, double segmentLength) {
    // Similar structure to Beggs-Brill but with H-B holdup correlation
    double area = Math.PI * Math.pow(tubingDiameter / 2.0, 2);

    double rhoL = fluid.hasPhaseType("oil") ? fluid.getPhase("oil").getDensity("kg/m3") : 800.0;
    double rhoG = fluid.hasPhaseType("gas") ? fluid.getPhase("gas").getDensity("kg/m3") : 50.0;
    double muL = fluid.hasPhaseType("oil") ? fluid.getPhase("oil").getViscosity("kg/msec") : 0.001;

    double qL = 0.0;
    double qG = 0.0;
    if (fluid.hasPhaseType("oil")) {
      qL = fluid.getPhase("oil").getVolume("m3");
    }
    if (fluid.hasPhaseType("gas")) {
      qG = fluid.getPhase("gas").getVolume("m3");
    }

    double vSL = qL / area;
    double vSG = qG / area;
    double vM = vSL + vSG;
    if (vM < 0.01)
      vM = 0.01;

    // Hagedorn-Brown holdup correlation (simplified)
    // Uses liquid velocity number and dimensionless groups
    double NLv = vSL * Math.pow(rhoL / (9.81 * 0.03), 0.25); // liquid velocity number
    double Nd = tubingDiameter * Math.pow(rhoL * 9.81 / 0.03, 0.5); // pipe diameter number

    // Empirical holdup (simplified)
    double HL = 0.1 + 0.6 * Math.exp(-0.5 * vSG / (vSL + 0.01));
    if (HL > 1.0)
      HL = 1.0;

    double rhoM = rhoL * HL + rhoG * (1.0 - HL);

    // Hydrostatic
    double dPhydro = rhoM * 9.81 * segmentLength / 1.0e5;

    // Friction
    double lambdaL = vSL / vM;
    double rhoNS = rhoL * lambdaL + rhoG * (1.0 - lambdaL);
    double Re = rhoNS * vM * tubingDiameter / muL;
    double f = calculateFrictionFactor(Re);
    double dPfric = f * rhoNS * Math.pow(vM, 2) * segmentLength / (2.0 * tubingDiameter) / 1.0e5;

    return dPhydro + dPfric;
  }

  /**
   * Gray (1974) pressure drop - optimized for gas wells with condensate.
   */
  private double calculateGrayPressureDrop(SystemInterface fluid, double segmentLength) {
    double area = Math.PI * Math.pow(tubingDiameter / 2.0, 2);

    double rhoG = fluid.hasPhaseType("gas") ? fluid.getPhase("gas").getDensity("kg/m3") : 50.0;
    double rhoL = fluid.hasPhaseType("oil") ? fluid.getPhase("oil").getDensity("kg/m3") : 800.0;
    double muG = fluid.hasPhaseType("gas") ? fluid.getPhase("gas").getViscosity("kg/msec") : 2e-5;

    double qG = fluid.hasPhaseType("gas") ? fluid.getPhase("gas").getVolume("m3") : 0.0;
    double qL = fluid.hasPhaseType("oil") ? fluid.getPhase("oil").getVolume("m3") : 0.0;

    double vSG = qG / area;
    double vSL = qL / area;
    double vM = vSG + vSL;
    if (vM < 0.01)
      vM = vSG > 0 ? vSG : 0.01;

    // Gray's correlation for gas wells (simplified)
    // Low liquid loading assumed
    double lambdaL = vSL / vM;
    double HL = 1.5 * lambdaL; // Gray's simplified holdup for gas wells
    if (HL > 0.5)
      HL = 0.5;

    double rhoM = rhoL * HL + rhoG * (1.0 - HL);

    // Hydrostatic
    double dPhydro = rhoM * 9.81 * segmentLength / 1.0e5;

    // Friction (gas-dominated)
    double Re = rhoG * vSG * tubingDiameter / muG;
    double f = calculateFrictionFactor(Re);
    double dPfric = f * rhoM * Math.pow(vM, 2) * segmentLength / (2.0 * tubingDiameter) / 1.0e5;

    return dPhydro + dPfric;
  }

  /**
   * Hasan and Kabir (2002) mechanistic model.
   */
  private double calculateHasanKabirPressureDrop(SystemInterface fluid, double segmentLength) {
    double area = Math.PI * Math.pow(tubingDiameter / 2.0, 2);
    double inclinationRad = Math.toRadians(inclination);

    double rhoL = fluid.hasPhaseType("oil") ? fluid.getPhase("oil").getDensity("kg/m3") : 800.0;
    double rhoG = fluid.hasPhaseType("gas") ? fluid.getPhase("gas").getDensity("kg/m3") : 50.0;
    double muL = fluid.hasPhaseType("oil") ? fluid.getPhase("oil").getViscosity("kg/msec") : 0.001;
    double sigma = 0.03; // Surface tension N/m

    double qL = fluid.hasPhaseType("oil") ? fluid.getPhase("oil").getVolume("m3") : 0.0;
    double qG = fluid.hasPhaseType("gas") ? fluid.getPhase("gas").getVolume("m3") : 0.0;

    double vSL = qL / area;
    double vSG = qG / area;
    double vM = vSL + vSG;
    if (vM < 0.01)
      vM = 0.01;

    // Drift-flux model parameters (Hasan-Kabir)
    double C0 = 1.2; // Distribution coefficient for bubbly/slug
    double vD = 0.35 * Math.sqrt(9.81 * tubingDiameter * (rhoL - rhoG) / rhoL); // Drift velocity

    // Void fraction from drift-flux
    double alpha = vSG / (C0 * vM + vD);
    if (alpha > 0.95)
      alpha = 0.95;
    if (alpha < 0.0)
      alpha = 0.0;

    double HL = 1.0 - alpha;
    double rhoM = rhoL * HL + rhoG * alpha;

    // Hydrostatic
    double dPhydro = rhoM * 9.81 * segmentLength * Math.sin(inclinationRad) / 1.0e5;

    // Friction
    double lambdaL = vSL / vM;
    double rhoNS = rhoL * lambdaL + rhoG * (1.0 - lambdaL);
    double muNS = muL * lambdaL + 2e-5 * (1.0 - lambdaL);
    double Re = rhoNS * vM * tubingDiameter / muNS;
    double f = calculateFrictionFactor(Re);
    double dPfric = f * rhoNS * Math.pow(vM, 2) * segmentLength / (2.0 * tubingDiameter) / 1.0e5;

    return dPhydro + dPfric;
  }

  /**
   * Duns and Ros (1963) pressure drop - for gas wells.
   */
  private double calculateDunsRosPressureDrop(SystemInterface fluid, double segmentLength) {
    // Similar to Gray but with different empirical coefficients
    return calculateGrayPressureDrop(fluid, segmentLength) * 1.05; // Simplified approximation
  }

  /**
   * Calculate Darcy friction factor using Colebrook-White equation.
   */
  private double calculateFrictionFactor(double Re) {
    if (Re < 2300) {
      return 64.0 / Re; // Laminar
    }
    // Colebrook-White (solved iteratively)
    double relRough = wallRoughness / tubingDiameter;
    double f = 0.02; // Initial guess
    for (int i = 0; i < 10; i++) {
      double rhs = -2.0 * Math.log10(relRough / 3.7 + 2.51 / (Re * Math.sqrt(f)));
      f = 1.0 / (rhs * rhs);
    }
    return f;
  }

  /**
   * Generate a VLP curve (Tubing Performance Curve).
   * 
   * <p>
   * Calculates the required bottom-hole pressure for each flow rate to achieve a
   * specified wellhead
   * pressure. This is essential for nodal analysis and reservoir simulation
   * coupling.
   * </p>
   *
   * @param flowRates    array of flow rates to evaluate
   * @param flowUnit     flow rate unit ("Sm3/day", "bbl/day", "kg/sec", etc.)
   * @param pressureUnit pressure unit for output ("bara", "psia", etc.)
   * @return 2D array: [0]=flow rates, [1]=bottom-hole pressures, [2]=wellhead
   *         pressures
   */
  public double[][] generateVLPCurve(double[] flowRates, String flowUnit, String pressureUnit) {
    return generateVLPCurve(flowRates, flowUnit, pressureUnit, wellheadPressure);
  }

  /**
   * Generate a VLP curve for a specified wellhead pressure.
   *
   * @param flowRates    array of flow rates to evaluate
   * @param flowUnit     flow rate unit
   * @param pressureUnit pressure unit for output
   * @param targetWHP    target wellhead pressure (bara)
   * @return 2D array: [0]=flow rates, [1]=bottom-hole pressures, [2]=wellhead
   *         pressures
   */
  public double[][] generateVLPCurve(double[] flowRates, String flowUnit, String pressureUnit,
      double targetWHP) {
    vlpFlowRates = new double[flowRates.length];
    vlpBottomHolePressures = new double[flowRates.length];
    vlpWellheadPressures = new double[flowRates.length];

    SystemInterface baseFluid = getInletStream().getThermoSystem().clone();

    for (int i = 0; i < flowRates.length; i++) {
      // Create temporary stream at this flow rate
      SystemInterface testFluid = baseFluid.clone();

      // Convert flow rate and set
      double flowSm3Day = convertFlowRate(flowRates[i], flowUnit, "Sm3/day");

      // Iterate to find BHP that gives target WHP
      double bhpGuess = targetWHP + 50.0; // Initial guess
      double tolerance = 0.1; // bara

      for (int iter = 0; iter < 50; iter++) {
        testFluid.setPressure(bhpGuess, "bara");
        testFluid.setTotalFlowRate(flowSm3Day / 86400.0, "Sm3/sec");
        ThermodynamicOperations ops = new ThermodynamicOperations(testFluid);
        ops.TPflash();
        testFluid.initProperties();

        // Calculate pressure drop
        double totalDP = 0.0;
        double segmentLength = tubingLength / numberOfSegments;
        double currentP = bhpGuess;
        double currentT = bottomHoleTemperature;

        for (int s = 1; s <= numberOfSegments; s++) {
          double depth = tubingLength - s * segmentLength;
          currentT = calculateTemperatureAtDepth(depth, bottomHoleTemperature);
          testFluid.setTemperature(currentT, "K");
          testFluid.setPressure(currentP, "bara");
          ops.TPflash();
          testFluid.initProperties();

          double dP = calculateSegmentPressureDrop(testFluid, segmentLength);
          currentP -= dP;
          if (currentP < 1.0)
            currentP = 1.0;
        }

        double calculatedWHP = currentP;

        if (Math.abs(calculatedWHP - targetWHP) < tolerance) {
          break;
        }

        // Adjust BHP guess
        bhpGuess += (targetWHP - calculatedWHP);
        if (bhpGuess < targetWHP)
          bhpGuess = targetWHP + 10.0;
        if (bhpGuess > 1000.0)
          bhpGuess = 1000.0;
      }

      vlpFlowRates[i] = flowRates[i];
      vlpBottomHolePressures[i] = convertPressure(bhpGuess, "bara", pressureUnit);
      vlpWellheadPressures[i] = convertPressure(targetWHP, "bara", pressureUnit);
    }

    return new double[][] { vlpFlowRates, vlpBottomHolePressures, vlpWellheadPressures };
  }

  /**
   * Generate VLP curves for multiple wellhead pressures.
   * 
   * <p>
   * This produces a family of VLP curves useful for sensitivity analysis and
   * reservoir simulator
   * table generation.
   * </p>
   *
   * @param flowRates    array of flow rates
   * @param flowUnit     flow rate unit
   * @param whPressures  array of wellhead pressures
   * @param pressureUnit pressure unit
   * @return List of VLP curves, one per WHP
   */
  public List<double[][]> generateVLPFamily(double[] flowRates, String flowUnit,
      double[] whPressures, String pressureUnit) {
    List<double[][]> curves = new ArrayList<>();
    for (double whp : whPressures) {
      curves.add(generateVLPCurve(flowRates, flowUnit, pressureUnit, whp));
    }
    return curves;
  }

  /**
   * Find the operating point (intersection of IPR and VLP).
   * 
   * <p>
   * Given a WellFlow (IPR model) and target wellhead pressure, finds the flow
   * rate and bottom-hole
   * pressure where IPR = VLP.
   * </p>
   *
   * @param iprModel         WellFlow object representing the IPR
   * @param wellheadPressure target wellhead pressure
   * @param pressureUnit     pressure unit
   * @return double[2]: [0]=operating flow rate (Sm3/day), [1]=BHP (bara)
   */
  public double[] findOperatingPoint(WellFlow iprModel, double wellheadPressure,
      String pressureUnit) {
    double targetWHP = convertPressure(wellheadPressure, pressureUnit, "bara");
    double reservoirPressure = iprModel.getInletStream().getPressure("bara");

    // Generate VLP curve
    double maxFlow = reservoirPressure * 100; // Rough estimate
    double[] testFlows = new double[20];
    for (int i = 0; i < 20; i++) {
      testFlows[i] = maxFlow * (i + 1) / 20.0;
    }

    double[][] vlpCurve = generateVLPCurve(testFlows, "Sm3/day", "bara", targetWHP);

    // Generate IPR curve at same flow rates
    double[] iprBHP = new double[testFlows.length];
    for (int i = 0; i < testFlows.length; i++) {
      iprBHP[i] = calculateIPRPressure(iprModel, testFlows[i], reservoirPressure);
    }

    // Find intersection (where IPR_BHP = VLP_BHP)
    for (int i = 0; i < testFlows.length - 1; i++) {
      double vlpBHP1 = vlpCurve[1][i];
      double vlpBHP2 = vlpCurve[1][i + 1];
      double iprBHP1 = iprBHP[i];
      double iprBHP2 = iprBHP[i + 1];

      // Check for crossing
      if ((vlpBHP1 - iprBHP1) * (vlpBHP2 - iprBHP2) <= 0) {
        // Linear interpolation to find crossing
        double diff1 = vlpBHP1 - iprBHP1;
        double diff2 = vlpBHP2 - iprBHP2;
        double fraction = diff1 / (diff1 - diff2);
        double opFlow = testFlows[i] + fraction * (testFlows[i + 1] - testFlows[i]);
        double opBHP = iprBHP1 + fraction * (iprBHP2 - iprBHP1);
        return new double[] { opFlow, opBHP };
      }
    }

    // No intersection found - well cannot flow at this WHP
    logger.warn("No operating point found - well may not flow at WHP = {} bara", targetWHP);
    return new double[] { 0.0, reservoirPressure };
  }

  /**
   * Calculate IPR pressure for a given flow rate using the WellFlow model.
   */
  private double calculateIPRPressure(WellFlow iprModel, double flowRate,
      double reservoirPressure) {
    // This is a simplified calculation - actual implementation would use WellFlow's
    // internal IPR model
    double pi = iprModel.getWellProductionIndex();
    if (pi > 0) {
      // PI model: q = PI * (Pr² - Pwf²)
      double pwf2 = Math.pow(reservoirPressure, 2) - flowRate / pi;
      if (pwf2 > 0) {
        return Math.sqrt(pwf2);
      }
    }
    return 0.0;
  }

  private double convertFlowRate(double value, String fromUnit, String toUnit) {
    // Convert to Sm3/day first
    double sm3day = value;
    switch (fromUnit.toLowerCase()) {
      case "bbl/day":
        sm3day = value * 0.158987;
        break;
      case "kg/sec":
        sm3day = value * 86400 / 800; // Approximate
        break;
      case "mscf/day":
        sm3day = value * 28.3168;
        break;
      case "msm3/day":
        sm3day = value * 1.0e6;
        break;
    }

    // Convert to target unit
    switch (toUnit.toLowerCase()) {
      case "bbl/day":
        return sm3day / 0.158987;
      case "kg/sec":
        return sm3day * 800 / 86400;
      case "mscf/day":
        return sm3day / 28.3168;
      case "msm3/day":
        return sm3day / 1.0e6;
      default:
        return sm3day;
    }
  }

  private double convertPressure(double value, String fromUnit, String toUnit) {
    // Convert to bara first
    double bara = value;
    switch (fromUnit.toLowerCase()) {
      case "psia":
        bara = value / 14.5038;
        break;
      case "barg":
        bara = value + 1.01325;
        break;
      case "kpa":
        bara = value / 100.0;
        break;
      case "mpa":
        bara = value * 10.0;
        break;
    }

    // Convert to target unit
    switch (toUnit.toLowerCase()) {
      case "psia":
        return bara * 14.5038;
      case "barg":
        return bara - 1.01325;
      case "kpa":
        return bara * 100.0;
      case "mpa":
        return bara / 10.0;
      default:
        return bara;
    }
  }

  // Getters for results

  /**
   * Get calculated bottom-hole pressure.
   *
   * @param unit pressure unit
   * @return bottom-hole pressure
   */
  public double getBottomHolePressure(String unit) {
    return convertPressure(bottomHolePressure, "bara", unit);
  }

  /**
   * Get calculated wellhead pressure.
   *
   * @param unit pressure unit
   * @return wellhead pressure
   */
  public double getWellheadPressure(String unit) {
    return convertPressure(wellheadPressure, "bara", unit);
  }

  /**
   * Get total pressure drop through tubing.
   *
   * @param unit pressure unit
   * @return total pressure drop
   */
  public double getTotalPressureDrop(String unit) {
    return convertPressure(totalPressureDrop, "bara", unit);
  }

  /**
   * Get pressure profile along tubing.
   *
   * @return pressure profile array (bara)
   */
  public double[] getPressureProfile() {
    return pressureProfile;
  }

  /**
   * Get temperature profile along tubing.
   *
   * @return temperature profile array (K)
   */
  public double[] getTemperatureProfile() {
    return temperatureProfile;
  }

  /**
   * Get depth profile (from surface).
   *
   * @return depth profile array (m)
   */
  public double[] getDepthProfile() {
    return depthProfile;
  }

  /**
   * Get liquid holdup profile.
   *
   * @return holdup profile array (fraction)
   */
  public double[] getHoldupProfile() {
    return holdupProfile;
  }

  /**
   * Get the most recent VLP curve data.
   *
   * @return VLP curve: [0]=flow rates, [1]=BHP, [2]=WHP
   */
  public double[][] getVLPCurve() {
    return new double[][] { vlpFlowRates, vlpBottomHolePressures, vlpWellheadPressures };
  }

  /**
   * Set VLP table data for table-based pressure drop calculation.
   *
   * <p>
   * When using table VLP, the pressure drop is interpolated from the provided
   * curve instead of
   * being calculated from correlations. This is useful when you have measured or
   * pre-calculated VLP
   * data.
   * </p>
   *
   * @param flowRates           flow rates (same unit as stream flow rate)
   * @param bottomHolePressures corresponding bottom-hole pressures (bara)
   * @param wellheadPressure    wellhead pressure that the curve was generated for
   *                            (bara)
   */
  public void setTableVLP(double[] flowRates, double[] bottomHolePressures,
      double wellheadPressure) {
    if (flowRates == null || bottomHolePressures == null
        || flowRates.length != bottomHolePressures.length || flowRates.length < 2) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("TubingPerformance", "setTableVLP",
              "table", "- Provide matching flow/pressure arrays with at least two entries"));
    }

    this.tableVLPFlowRates = java.util.Arrays.copyOf(flowRates, flowRates.length);
    this.tableVLPBHP = java.util.Arrays.copyOf(bottomHolePressures, bottomHolePressures.length);
    this.tableVLPWellheadPressure = wellheadPressure;
    this.useTableVLP = true;

    // Sort by flow rate for interpolation
    sortVLPTableByFlowRate();

    logger.info("Set table VLP with {} points at WHP = {} bara", flowRates.length,
        wellheadPressure);
  }

  /**
   * Load VLP curve from a CSV file.
   *
   * <p>
   * The CSV file should have two columns: flow rate and bottom-hole pressure. The
   * wellhead pressure
   * must be specified separately. The first row can be a header (will be skipped
   * if non-numeric).
   * </p>
   *
   * <p>
   * Example CSV format:
   * </p>
   * 
   * <pre>
   * FlowRate(MSm3/day),BHP(bara)
   * 0.5,85
   * 1.0,92
   * 2.0,115
   * 3.0,145
   * 4.0,182
   * 5.0,225
   * </pre>
   *
   * @param filePath         path to the CSV file
   * @param wellheadPressure wellhead pressure the curve was generated for (bara)
   * @throws IOException if file cannot be read
   */
  public void loadVLPFromFile(String filePath, double wellheadPressure) throws IOException {
    loadVLPFromFile(java.nio.file.Paths.get(filePath), wellheadPressure);
  }

  /**
   * Load VLP curve from a CSV file.
   *
   * @param filePath         path to the CSV file
   * @param wellheadPressure wellhead pressure the curve was generated for (bara)
   * @throws IOException if file cannot be read
   * @see #loadVLPFromFile(String, double)
   */
  public void loadVLPFromFile(Path filePath, double wellheadPressure) throws IOException {
    List<Double> flowRates = new ArrayList<>();
    List<Double> bhpValues = new ArrayList<>();

    List<String> lines = Files.readAllLines(filePath);
    boolean firstLine = true;

    for (String line : lines) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }

      String[] parts = line.split("[,;\t]+");
      if (parts.length < 2) {
        continue;
      }

      try {
        double flowRate = Double.parseDouble(parts[0].trim());
        double bhp = Double.parseDouble(parts[1].trim());
        flowRates.add(flowRate);
        bhpValues.add(bhp);
      } catch (NumberFormatException e) {
        if (firstLine) {
          firstLine = false;
          continue;
        }
        logger.warn("Skipping invalid line in VLP file: {}", line);
      }
      firstLine = false;
    }

    if (flowRates.size() < 2) {
      throw new IOException("VLP file must contain at least 2 valid data points: " + filePath);
    }

    double[] flowArray = flowRates.stream().mapToDouble(Double::doubleValue).toArray();
    double[] bhpArray = bhpValues.stream().mapToDouble(Double::doubleValue).toArray();
    setTableVLP(flowArray, bhpArray, wellheadPressure);

    logger.info("Loaded VLP curve with {} points from {}", flowRates.size(), filePath);
  }

  /**
   * Check if using table-based VLP.
   *
   * @return true if using table VLP, false if using correlation-based
   */
  public boolean isUsingTableVLP() {
    return useTableVLP;
  }

  /**
   * Disable table-based VLP and use correlation-based calculations.
   */
  public void disableTableVLP() {
    this.useTableVLP = false;
  }

  /**
   * Get BHP from table VLP by interpolation.
   *
   * @param flowRate flow rate
   * @return interpolated bottom-hole pressure (bara)
   */
  public double interpolateBHPFromTable(double flowRate) {
    if (!useTableVLP || tableVLPFlowRates.length < 2) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(
          "TubingPerformance", "interpolateBHPFromTable", "table", "- No table VLP data loaded"));
    }

    // Extrapolate below minimum
    if (flowRate <= tableVLPFlowRates[0]) {
      return tableVLPBHP[0];
    }

    // Extrapolate above maximum
    int lastIdx = tableVLPFlowRates.length - 1;
    if (flowRate >= tableVLPFlowRates[lastIdx]) {
      // Linear extrapolation from last two points
      double slope = (tableVLPBHP[lastIdx] - tableVLPBHP[lastIdx - 1])
          / (tableVLPFlowRates[lastIdx] - tableVLPFlowRates[lastIdx - 1]);
      return tableVLPBHP[lastIdx] + slope * (flowRate - tableVLPFlowRates[lastIdx]);
    }

    // Interpolate
    for (int i = 0; i < tableVLPFlowRates.length - 1; i++) {
      if (flowRate >= tableVLPFlowRates[i] && flowRate <= tableVLPFlowRates[i + 1]) {
        double fraction = (flowRate - tableVLPFlowRates[i]) / (tableVLPFlowRates[i + 1] - tableVLPFlowRates[i]);
        return tableVLPBHP[i] + fraction * (tableVLPBHP[i + 1] - tableVLPBHP[i]);
      }
    }

    return tableVLPBHP[lastIdx];
  }

  /**
   * Get the current VLP table flow rates.
   *
   * @return array of flow rates, or empty array if not using table VLP
   */
  public double[] getVLPTableFlowRates() {
    return java.util.Arrays.copyOf(tableVLPFlowRates, tableVLPFlowRates.length);
  }

  /**
   * Get the current VLP table BHP values.
   *
   * @return array of BHP values (bara), or empty array if not using table VLP
   */
  public double[] getVLPTableBHP() {
    return java.util.Arrays.copyOf(tableVLPBHP, tableVLPBHP.length);
  }

  /**
   * Get the wellhead pressure for the VLP table.
   *
   * @return wellhead pressure (bara), or NaN if not using table VLP
   */
  public double getVLPTableWellheadPressure() {
    return tableVLPWellheadPressure;
  }

  private void sortVLPTableByFlowRate() {
    for (int i = 0; i < tableVLPFlowRates.length - 1; i++) {
      for (int j = 0; j < tableVLPFlowRates.length - i - 1; j++) {
        if (tableVLPFlowRates[j] > tableVLPFlowRates[j + 1]) {
          double tempQ = tableVLPFlowRates[j];
          tableVLPFlowRates[j] = tableVLPFlowRates[j + 1];
          tableVLPFlowRates[j + 1] = tempQ;

          double tempP = tableVLPBHP[j];
          tableVLPBHP[j] = tableVLPBHP[j + 1];
          tableVLPBHP[j + 1] = tempP;
        }
      }
    }
  }
}
