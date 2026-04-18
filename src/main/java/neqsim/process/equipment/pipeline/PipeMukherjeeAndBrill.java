package neqsim.process.equipment.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Pipeline simulation using the Mukherjee and Brill (1985) correlation for multiphase flow.
 *
 * <p>
 * Implements the Mukherjee-Brill correlation for predicting liquid holdup, pressure drop, and flow
 * pattern in two-phase gas-liquid flow at all pipe inclinations. This correlation was developed
 * from an extensive experimental database covering uphill, downhill, and horizontal flow.
 * </p>
 *
 * <h2>Reference</h2>
 * <p>
 * Mukherjee, H. and Brill, J.P., "Pressure Drop Correlations for Inclined Two-Phase Flow", Journal
 * of Energy Resources Technology, December 1985, Vol. 107, pp. 549-554.
 * </p>
 * <p>
 * Mukherjee, H. and Brill, J.P., "Empirical Equations to Predict Flow Patterns in Two-Phase
 * Inclined Flow", International Journal of Multiphase Flow, 1985, Vol. 11, No. 3, pp. 299-315.
 * </p>
 *
 * <h2>Flow Pattern Determination</h2>
 * <p>
 * The Mukherjee-Brill method classifies flow into four patterns:
 * </p>
 * <ul>
 * <li><b>STRATIFIED</b> — Gas flows over a liquid layer (downhill and horizontal)</li>
 * <li><b>SLUG</b> — Alternating liquid slugs and gas pockets</li>
 * <li><b>ANNULAR</b> — Gas core with liquid film on wall (high gas rates)</li>
 * <li><b>BUBBLE</b> — Small gas bubbles dispersed in liquid (high liquid rates)</li>
 * </ul>
 * <p>
 * Flow pattern boundaries depend on pipe inclination angle (theta), superficial gas and liquid
 * velocities, and fluid properties.
 * </p>
 *
 * <h2>Liquid Holdup Correlation</h2>
 * <p>
 * The Mukherjee-Brill holdup correlation uses the form:
 * </p>
 *
 * <pre>
 * H_L = exp[(C1 + C2*sin(theta) + C3*sin^2(theta) + C4*N_L^2) /
 *           (N_gv^C5 / (1 + N_Lv)^C6)]
 * </pre>
 *
 * <p>
 * where the coefficients C1-C6 depend on the flow pattern and pipe inclination direction (uphill,
 * downhill, or horizontal).
 * </p>
 *
 * <h2>Friction Factor</h2>
 * <p>
 * Uses the Moody friction factor for no-slip Reynolds number, with a two-phase multiplier based on
 * holdup ratio to account for the increased wall shear from multiphase flow.
 * </p>
 *
 * <h2>Applicability</h2>
 * <ul>
 * <li>All pipe inclinations from -90 to +90 degrees</li>
 * <li>Pipe diameters 1 to 6 inches (tested)</li>
 * <li>Gas-liquid and gas-condensate systems</li>
 * <li>Commonly used as alternative to Beggs-Brill for NCS pipeline design</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * SystemInterface fluid = new SystemSrkEos(303.15, 50.0);
 * fluid.addComponent("methane", 0.85);
 * fluid.addComponent("ethane", 0.10);
 * fluid.addComponent("propane", 0.05);
 * fluid.setMixingRule("classic");
 *
 * Stream inlet = new Stream("inlet", fluid);
 * inlet.setFlowRate(30000, "kg/hr");
 * inlet.run();
 *
 * PipeMukherjeeAndBrill pipe = new PipeMukherjeeAndBrill("pipeline", inlet);
 * pipe.setDiameter(0.2032); // 8 inch
 * pipe.setLength(15000.0); // 15 km
 * pipe.setElevation(-200.0); // slightly downhill
 * pipe.setNumberOfIncrements(30);
 * pipe.run();
 *
 * System.out.println("Pressure drop: " + pipe.getPressureDrop() + " bar");
 * System.out.println("Flow pattern: " + pipe.getFlowPattern());
 * }</pre>
 *
 * @author NeqSim
 * @version 1.0
 * @see Pipeline
 * @see PipeBeggsAndBrills
 * @see PipeHagedornBrown
 */
public class PipeMukherjeeAndBrill extends Pipeline {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1002L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PipeMukherjeeAndBrill.class);

  /** Acceleration of gravity in m/s2. */
  private static final double GRAVITY = 9.81;

  /**
   * Flow pattern classification per Mukherjee-Brill.
   *
   * @author NeqSim
   * @version 1.0
   */
  public enum FlowPattern {
    /** Stratified flow (liquid at bottom, gas above). */
    STRATIFIED,
    /** Slug flow (alternating slugs and bubbles). */
    SLUG,
    /** Annular flow (gas core, liquid film). */
    ANNULAR,
    /** Bubble flow (gas bubbles in liquid). */
    BUBBLE,
    /** Single-phase flow. */
    SINGLE_PHASE
  }

  /** Identified flow pattern. */
  private FlowPattern currentFlowPattern = FlowPattern.SLUG;

  /** Total pressure drop in bar. */
  private double totalPressureDrop = 0.0;

  /** Superficial liquid velocity in m/s. */
  private double superficialLiquidVelocity = 0.0;

  /** Superficial gas velocity in m/s. */
  private double superficialGasVelocity = 0.0;

  /** Mixture velocity in m/s. */
  private double mixtureVelocity = 0.0;

  /** Calculated liquid holdup. */
  private double calculatedHoldup = 0.0;

  /** Mixture density in kg/m3. */
  private double mixtureDensity = 0.0;

  /** Pressure profile along the pipe. */
  private List<Double> pressureProfileList = new ArrayList<Double>();

  /** Temperature profile along the pipe. */
  private List<Double> temperatureProfileList = new ArrayList<Double>();

  /** Length profile along the pipe. */
  private List<Double> lengthProfileList = new ArrayList<Double>();

  /** Flow pattern profile along the pipe. */
  private List<String> flowPatternProfile = new ArrayList<String>();

  /**
   * Default constructor for PipeMukherjeeAndBrill.
   */
  public PipeMukherjeeAndBrill() {
    super("PipeMukherjeeAndBrill");
  }

  /**
   * Constructor with name.
   *
   * @param name equipment name
   */
  public PipeMukherjeeAndBrill(String name) {
    super(name);
  }

  /**
   * Constructor with name and inlet stream.
   *
   * @param name equipment name
   * @param inStream inlet stream
   */
  public PipeMukherjeeAndBrill(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (diameter <= 0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(
          "PipeMukherjeeAndBrill", "run", "diameter", "must be positive, got: " + diameter));
    }
    if (numberOfIncrements <= 0) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("PipeMukherjeeAndBrill", "run",
              "numberOfIncrements", "must be positive, got: " + numberOfIncrements));
    }

    pressureProfileList = new ArrayList<Double>();
    temperatureProfileList = new ArrayList<Double>();
    lengthProfileList = new ArrayList<Double>();
    flowPatternProfile = new ArrayList<String>();

    double segmentLength = length / numberOfIncrements;
    double segmentElevation = elevation / numberOfIncrements;

    system = inStream.getThermoSystem().clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double pInlet = system.getPressure();
    pressureProfileList.add(pInlet);
    temperatureProfileList.add(system.getTemperature());
    lengthProfileList.add(0.0);
    double cumulLen = 0.0;

    for (int i = 1; i <= numberOfIncrements; i++) {
      cumulLen += segmentLength;

      double dp = calcSegmentPressureDrop(system, segmentLength, segmentElevation);
      double pOut = system.getPressure() - dp;
      if (pOut < 0.1) {
        logger.warn("Outlet pressure went below 0.1 bar at increment {}", i);
        pOut = 0.1;
      }

      system.setPressure(pOut);
      ops.TPflash();
      system.initProperties();

      pressureProfileList.add(pOut);
      temperatureProfileList.add(system.getTemperature());
      lengthProfileList.add(cumulLen);
      flowPatternProfile.add(currentFlowPattern.name());
    }

    totalPressureDrop = pInlet - system.getPressure();
    pressureDrop = totalPressureDrop;
    flowRegime = currentFlowPattern.name();

    outStream.setThermoSystem(system);
    outStream.setCalculationIdentifier(id);
  }

  /**
   * Calculate pressure drop for a single pipe segment using the Mukherjee-Brill method.
   *
   * @param sys thermodynamic system at segment conditions
   * @param segLength segment length in meters
   * @param segElev segment elevation change in meters (positive = upward)
   * @return pressure drop in bar
   */
  private double calcSegmentPressureDrop(SystemInterface sys, double segLength, double segElev) {
    double area = Math.PI / 4.0 * diameter * diameter;
    double totalMassFlow = sys.getFlowRate("kg/sec");
    if (totalMassFlow < 1e-10) {
      return 0.0;
    }

    double rhoL;
    double rhoG;
    double muL;
    double muG;
    double sigmaL;
    double volFlowL;
    double volFlowG;

    if (sys.hasPhaseType("gas") && sys.hasPhaseType("oil")) {
      rhoL = sys.getPhase("oil").getDensity("kg/m3");
      rhoG = sys.getPhase("gas").getDensity("kg/m3");
      muL = sys.getPhase("oil").getViscosity("kg/msec");
      muG = sys.getPhase("gas").getViscosity("kg/msec");
      sigmaL = sys.getInterphaseProperties().getSurfaceTension(sys.getPhaseNumberOfPhase("oil"),
          sys.getPhaseNumberOfPhase("gas"));
      if (sigmaL < 1e-6) {
        sigmaL = 0.02;
      }
      volFlowL = sys.getPhase("oil").getFlowRate("m3/sec");
      volFlowG = sys.getPhase("gas").getFlowRate("m3/sec");
    } else if (sys.hasPhaseType("gas") && sys.hasPhaseType("aqueous")) {
      rhoL = sys.getPhase("aqueous").getDensity("kg/m3");
      rhoG = sys.getPhase("gas").getDensity("kg/m3");
      muL = sys.getPhase("aqueous").getViscosity("kg/msec");
      muG = sys.getPhase("gas").getViscosity("kg/msec");
      sigmaL = 0.072;
      volFlowL = sys.getPhase("aqueous").getFlowRate("m3/sec");
      volFlowG = sys.getPhase("gas").getFlowRate("m3/sec");
    } else if (sys.hasPhaseType("gas")) {
      rhoG = sys.getPhase("gas").getDensity("kg/m3");
      muG = sys.getPhase("gas").getViscosity("kg/msec");
      double vGas = totalMassFlow / (rhoG * area);
      double reGas = rhoG * vGas * diameter / muG;
      double fGas = calcFrictionFactor(reGas);
      double dpFric = fGas * segLength * rhoG * vGas * vGas / (2.0 * diameter) / 1e5;
      double dpGrav = rhoG * GRAVITY * segElev / 1e5;
      currentFlowPattern = FlowPattern.SINGLE_PHASE;
      return dpFric + dpGrav;
    } else {
      rhoL = sys.getDensity("kg/m3");
      muL = sys.getViscosity("kg/msec");
      double vLiq = totalMassFlow / (rhoL * area);
      double reLiq = rhoL * vLiq * diameter / muL;
      double fLiq = calcFrictionFactor(reLiq);
      double dpFric = fLiq * segLength * rhoL * vLiq * vLiq / (2.0 * diameter) / 1e5;
      double dpGrav = rhoL * GRAVITY * segElev / 1e5;
      currentFlowPattern = FlowPattern.SINGLE_PHASE;
      return dpFric + dpGrav;
    }

    // Two-phase calculation
    superficialLiquidVelocity = volFlowL / area;
    superficialGasVelocity = volFlowG / area;
    mixtureVelocity = superficialLiquidVelocity + superficialGasVelocity;

    if (mixtureVelocity < 1e-10) {
      return 0.0;
    }

    double lambdaL = superficialLiquidVelocity / mixtureVelocity;

    // Inclination angle from elevation/length
    double sinTheta = 0.0;
    if (segLength > 0) {
      sinTheta = segElev / segLength;
      sinTheta = Math.max(-1.0, Math.min(1.0, sinTheta));
    }
    double theta = Math.asin(sinTheta); // radians

    // Determine flow pattern
    currentFlowPattern = determineFlowPattern(superficialLiquidVelocity, superficialGasVelocity,
        rhoL, rhoG, muL, sigmaL, theta);

    // Dimensionless numbers for holdup correlation
    double fourthRoot = Math.pow(rhoL / (GRAVITY * sigmaL), 0.25);
    double nLv = superficialLiquidVelocity * fourthRoot;
    double nGv = superficialGasVelocity * fourthRoot;
    double nL = muL * Math.pow(GRAVITY / (rhoL * sigmaL * sigmaL * sigmaL), 0.25);

    // Liquid holdup
    calculatedHoldup = calcLiquidHoldup(nLv, nGv, nL, theta, lambdaL);
    calculatedHoldup = Math.max(lambdaL, Math.min(1.0, calculatedHoldup));
    liquidHoldup = calculatedHoldup;

    // Mixture density with slip
    mixtureDensity = rhoL * calculatedHoldup + rhoG * (1.0 - calculatedHoldup);

    // No-slip properties
    double rhoNoSlip = rhoL * lambdaL + rhoG * (1.0 - lambdaL);
    double muNoSlip = muL * lambdaL + muG * (1.0 - lambdaL);

    // Reynolds number
    double re = rhoNoSlip * mixtureVelocity * diameter / muNoSlip;
    reynoldsNumber = re;
    double fNoSlip = calcFrictionFactor(re);

    // Two-phase friction factor correction
    double holdupRatio = lambdaL / (calculatedHoldup + 1e-10);
    double fTp = fNoSlip * calcFrictionMultiplier(holdupRatio);
    frictionFactor = fTp;

    // Pressure gradient (Pa/m)
    double dpFriction = fTp * rhoNoSlip * mixtureVelocity * mixtureVelocity / (2.0 * diameter);
    double dpGravity = mixtureDensity * GRAVITY * sinTheta;

    // Pressure drop (bar)
    return (dpFriction + dpGravity) * segLength / 1e5;
  }

  /**
   * Determine flow pattern using Mukherjee-Brill boundaries.
   *
   * <p>
   * The flow pattern is determined from dimensionless gas and liquid velocity numbers, pipe
   * inclination, and fluid properties.
   * </p>
   *
   * @param vSl superficial liquid velocity in m/s
   * @param vSg superficial gas velocity in m/s
   * @param rhoL liquid density in kg/m3
   * @param rhoG gas density in kg/m3
   * @param muL liquid viscosity in Pa.s
   * @param sigma surface tension in N/m
   * @param theta pipe inclination angle in radians
   * @return identified flow pattern
   */
  private FlowPattern determineFlowPattern(double vSl, double vSg, double rhoL, double rhoG,
      double muL, double sigma, double theta) {

    double sinT = Math.sin(theta);
    double cosT = Math.cos(theta);

    // Modified Froude numbers
    double frL = vSl * vSl / (GRAVITY * diameter);
    double frG = vSg * vSg / (GRAVITY * diameter);

    // Boundary 1: Stratified-Slug boundary (applies mainly for downhill/horizontal)
    if (theta <= 0) {
      // Downhill flow
      double b1 = Math.log10(frL + 1e-10);
      double b2 = -0.38 + 3.11 * sinT + 1.45 * sinT * sinT;
      if (b1 < b2) {
        return FlowPattern.STRATIFIED;
      }
    }

    // Boundary 2: Bubble-Slug boundary (high liquid rate)
    double ngv = vSg * Math.pow(rhoL / (GRAVITY * sigma), 0.25);
    double nlv = vSl * Math.pow(rhoL / (GRAVITY * sigma), 0.25);

    if (nlv > 0) {
      double bubbleLimit = 2.0 + 1.5 * Math.abs(sinT);
      if (ngv / nlv < 0.1 && nlv > bubbleLimit) {
        return FlowPattern.BUBBLE;
      }
    }

    // Boundary 3: Slug-Annular boundary (high gas rate)
    double annularLimit = 50.0 + 36.0 * Math.abs(sinT);
    if (frG > annularLimit) {
      return FlowPattern.ANNULAR;
    }

    return FlowPattern.SLUG;
  }

  /**
   * Calculate liquid holdup using the Mukherjee-Brill correlation.
   *
   * <p>
   * The holdup is calculated from flow-pattern-dependent correlations using dimensionless velocity
   * and viscosity numbers and the pipe inclination angle.
   * </p>
   *
   * @param nLv liquid velocity number
   * @param nGv gas velocity number
   * @param nL liquid viscosity number
   * @param theta pipe inclination angle in radians
   * @param lambdaL no-slip liquid fraction
   * @return predicted liquid holdup (0 to 1)
   */
  private double calcLiquidHoldup(double nLv, double nGv, double nL, double theta, double lambdaL) {
    double sinT = Math.sin(theta);
    double sinT2 = sinT * sinT;

    double c1;
    double c2;
    double c3;
    double c4;
    double c5;
    double c6;

    if (theta >= 0) {
      // Uphill flow coefficients
      switch (currentFlowPattern) {
        case STRATIFIED:
          // Stratified rarely occurs uphill, use slug coefficients
          c1 = -0.380113;
          c2 = 0.129875;
          c3 = -0.119788;
          c4 = 2.343227;
          c5 = 0.475686;
          c6 = 0.288657;
          break;
        case ANNULAR:
          c1 = -1.330282;
          c2 = 4.808139;
          c3 = 4.171584;
          c4 = 56.262268;
          c5 = 0.079951;
          c6 = 0.504887;
          break;
        case BUBBLE:
          c1 = -0.516644;
          c2 = 0.789805;
          c3 = 0.551627;
          c4 = 15.519214;
          c5 = 0.371771;
          c6 = 0.393952;
          break;
        default: // SLUG
          c1 = -0.380113;
          c2 = 0.129875;
          c3 = -0.119788;
          c4 = 2.343227;
          c5 = 0.475686;
          c6 = 0.288657;
          break;
      }
    } else {
      // Downhill flow coefficients
      switch (currentFlowPattern) {
        case STRATIFIED:
          c1 = -1.330282;
          c2 = 4.808139;
          c3 = 4.171584;
          c4 = 56.262268;
          c5 = 0.079951;
          c6 = 0.504887;
          break;
        case ANNULAR:
          c1 = -0.516644;
          c2 = 0.789805;
          c3 = 0.551627;
          c4 = 15.519214;
          c5 = 0.371771;
          c6 = 0.393952;
          break;
        default: // SLUG, BUBBLE
          c1 = -0.380113;
          c2 = 0.129875;
          c3 = -0.119788;
          c4 = 2.343227;
          c5 = 0.475686;
          c6 = 0.288657;
          break;
      }
    }

    // Mukherjee-Brill holdup formula
    double numerator = c1 + c2 * sinT + c3 * sinT2 + c4 * nL * nL;
    double denom = Math.pow(nGv + 0.01, c5) / (Math.pow(1.0 + nLv, c6) + 1e-10);
    double exponent = numerator / (denom + 1e-10);

    // Limit exponent for numerical stability
    exponent = Math.max(-5.0, Math.min(5.0, exponent));
    double hl = Math.exp(exponent);

    return Math.max(0.0, Math.min(1.0, hl));
  }

  /**
   * Calculate two-phase friction factor multiplier from holdup ratio.
   *
   * <p>
   * The Mukherjee-Brill friction factor correction accounts for the effect of slip between phases
   * on the wall shear stress. The multiplier is based on the ratio of no-slip to actual liquid
   * holdup.
   * </p>
   *
   * @param holdupRatio ratio of no-slip liquid fraction to actual holdup
   * @return friction factor multiplier
   */
  private double calcFrictionMultiplier(double holdupRatio) {
    if (holdupRatio <= 0 || holdupRatio > 5.0) {
      return 1.0;
    }
    double logRatio = Math.log(holdupRatio);
    double s = logRatio / (-0.0523 + 3.182 * logRatio - 0.8725 * logRatio * logRatio
        + 0.01853 * logRatio * logRatio * logRatio * logRatio);
    return Math.exp(s);
  }

  /**
   * Calculate Moody/Haaland friction factor.
   *
   * @param re Reynolds number
   * @return Darcy friction factor
   */
  private double calcFrictionFactor(double re) {
    if (re < 1.0) {
      return 0.0;
    }
    if (re < 2300) {
      return 64.0 / re;
    }
    double relRoughness = roughness / diameter;
    double term = -1.8 * Math.log10(Math.pow(relRoughness / 3.7, 1.11) + 6.9 / re);
    double fDarcy = 1.0 / (term * term);
    return fDarcy;
  }

  /**
   * Get the total pressure drop across the pipe.
   *
   * @return total pressure drop in bar
   */
  public double getTotalPressureDrop() {
    return totalPressureDrop;
  }

  /** {@inheritDoc} */
  @Override
  public double getPressureDrop() {
    return totalPressureDrop;
  }

  /**
   * Get the calculated liquid holdup at the last segment.
   *
   * @return liquid holdup fraction
   */
  public double getLiquidHoldup() {
    return calculatedHoldup;
  }

  /**
   * Get the mixture density at the last segment.
   *
   * @return mixture density in kg/m3
   */
  public double getMixtureDensity() {
    return mixtureDensity;
  }

  /**
   * Get the identified flow pattern.
   *
   * @return flow pattern string
   */
  public String getFlowPattern() {
    return currentFlowPattern.name();
  }

  /**
   * Get the identified flow pattern enum.
   *
   * @return flow pattern enum value
   */
  public FlowPattern getFlowPatternEnum() {
    return currentFlowPattern;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getPressureProfile() {
    double[] result = new double[pressureProfileList.size()];
    for (int i = 0; i < pressureProfileList.size(); i++) {
      result[i] = pressureProfileList.get(i);
    }
    return result;
  }

  /**
   * Get the pressure profile as a list.
   *
   * @return list of pressures in bar
   */
  public List<Double> getPressureProfileList() {
    return new ArrayList<Double>(pressureProfileList);
  }

  /** {@inheritDoc} */
  @Override
  public double[] getTemperatureProfile() {
    double[] result = new double[temperatureProfileList.size()];
    for (int i = 0; i < temperatureProfileList.size(); i++) {
      result[i] = temperatureProfileList.get(i);
    }
    return result;
  }

  /**
   * Get the temperature profile as a list.
   *
   * @return list of temperatures in Kelvin
   */
  public List<Double> getTemperatureProfileList() {
    return new ArrayList<Double>(temperatureProfileList);
  }

  /**
   * Get the length profile along the pipe.
   *
   * @return list of cumulative lengths in meters
   */
  public List<Double> getLengthProfile() {
    return lengthProfileList;
  }

  /**
   * Get the flow pattern profile along the pipe.
   *
   * @return list of flow pattern names at each segment
   */
  public List<String> getFlowPatternProfile() {
    return flowPatternProfile;
  }
}
