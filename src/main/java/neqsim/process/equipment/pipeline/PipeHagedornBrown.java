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
 * Pipeline simulation using Hagedorn and Brown empirical correlation for multiphase vertical flow.
 *
 * <p>
 * Implements the Hagedorn and Brown (1965) method for predicting pressure traverses in vertical and
 * near-vertical wells. This correlation is widely used for production tubing and vertical riser
 * calculations, particularly when the liquid phase is continuous.
 * </p>
 *
 * <h2>Reference</h2>
 * <p>
 * Hagedorn, A.R. and Brown, K.E., "Experimental Study of Pressure Gradients Occurring During
 * Continuous Two-Phase Flow in Small-Diameter Vertical Conduits", Journal of Petroleum Technology,
 * April 1965, pp. 475-484. SPE-940-PA.
 * </p>
 *
 * <h2>Method Overview</h2>
 * <p>
 * The Hagedorn-Brown correlation calculates pressure gradient from:
 * </p>
 *
 * <pre>
 * dP/dz = (rho_m * g + f * rho_m * v_m^2 / (2 * D)) / (1 - rho_m * v_m * v_sg / P)
 * </pre>
 *
 * <p>
 * where rho_m is the mixture density accounting for slip between phases via the empirical liquid
 * holdup correlation. The denominator accounts for the acceleration pressure gradient (kinetic
 * energy changes).
 * </p>
 *
 * <h2>Liquid Holdup Determination</h2>
 * <p>
 * Liquid holdup (H_L) is determined from three dimensionless groups using empirical charts
 * (correlations fitted to Hagedorn-Brown experimental data):
 * </p>
 * <ul>
 * <li>Liquid velocity number: N_vL = v_sL * (rho_L / (g * sigma))^0.25</li>
 * <li>Gas velocity number: N_vG = v_sG * (rho_L / (g * sigma))^0.25</li>
 * <li>Pipe diameter number: N_D = D * (rho_L * g / sigma)^0.5</li>
 * <li>Liquid viscosity number: N_L = mu_L * (g / (rho_L * sigma^3))^0.25</li>
 * </ul>
 *
 * <h2>Applicability</h2>
 * <ul>
 * <li>Best suited for vertical or near-vertical flow (inclination 75-90 degrees)</li>
 * <li>Wide range of gas-liquid ratios</li>
 * <li>Pipe diameters from 1 to 4 inches (tested range)</li>
 * <li>Not recommended for horizontal or slightly inclined flow</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * SystemInterface fluid = new SystemSrkEos(350.0, 200.0);
 * fluid.addComponent("methane", 0.8);
 * fluid.addComponent("nC10", 0.2);
 * fluid.setMixingRule("classic");
 *
 * Stream inlet = new Stream("well_inlet", fluid);
 * inlet.setFlowRate(50000, "kg/hr");
 * inlet.run();
 *
 * PipeHagedornBrown tubing = new PipeHagedornBrown("production_tubing", inlet);
 * tubing.setDiameter(0.0762); // 3 inch tubing
 * tubing.setLength(3000.0); // 3000 m TVD
 * tubing.setElevation(-3000.0); // vertical downward (production)
 * tubing.setNumberOfIncrements(30);
 * tubing.run();
 * }</pre>
 *
 * @author NeqSim
 * @version 1.0
 * @see Pipeline
 * @see PipeBeggsAndBrills
 */
public class PipeHagedornBrown extends Pipeline {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PipeHagedornBrown.class);

  /** Acceleration of gravity in m/s2. */
  private static final double GRAVITY = 9.81;

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

  /**
   * Default constructor for PipeHagedornBrown.
   */
  public PipeHagedornBrown() {
    super("PipeHagedornBrown");
  }

  /**
   * Constructor with name.
   *
   * @param name equipment name
   */
  public PipeHagedornBrown(String name) {
    super(name);
  }

  /**
   * Constructor with name and inlet stream.
   *
   * @param name equipment name
   * @param inStream inlet stream
   */
  public PipeHagedornBrown(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (diameter <= 0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(
          "PipeHagedornBrown", "run", "diameter", "must be positive, got: " + diameter));
    }
    if (numberOfIncrements <= 0) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("PipeHagedornBrown", "run",
              "numberOfIncrements", "must be positive, got: " + numberOfIncrements));
    }

    pressureProfileList = new ArrayList<Double>();
    temperatureProfileList = new ArrayList<Double>();
    lengthProfileList = new ArrayList<Double>();

    double segmentLength = length / numberOfIncrements;
    double segmentElevation = elevation / numberOfIncrements;

    system = inStream.getThermoSystem().clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double pInlet = system.getPressure(); // bar
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
    }

    totalPressureDrop = pInlet - system.getPressure();
    pressureDrop = totalPressureDrop;

    outStream.setThermoSystem(system);
    outStream.setCalculationIdentifier(id);
  }

  /**
   * Calculate pressure drop for a single pipe segment using the Hagedorn-Brown method.
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

    // Get phase properties
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
        sigmaL = 0.02; // Default 20 mN/m
      }
      volFlowL = sys.getPhase("oil").getFlowRate("m3/sec");
      volFlowG = sys.getPhase("gas").getFlowRate("m3/sec");
    } else if (sys.hasPhaseType("gas") && sys.hasPhaseType("aqueous")) {
      rhoL = sys.getPhase("aqueous").getDensity("kg/m3");
      rhoG = sys.getPhase("gas").getDensity("kg/m3");
      muL = sys.getPhase("aqueous").getViscosity("kg/msec");
      muG = sys.getPhase("gas").getViscosity("kg/msec");
      sigmaL = 0.072; // Water-gas surface tension default
      volFlowL = sys.getPhase("aqueous").getFlowRate("m3/sec");
      volFlowG = sys.getPhase("gas").getFlowRate("m3/sec");
    } else if (sys.hasPhaseType("gas")) {
      // Single-phase gas
      rhoG = sys.getPhase("gas").getDensity("kg/m3");
      muG = sys.getPhase("gas").getViscosity("kg/msec");
      double vGas = totalMassFlow / (rhoG * area);
      double reGas = rhoG * vGas * diameter / muG;
      double fGas = calcFrictionFactor(reGas);
      double dpFric = fGas * segLength * rhoG * vGas * vGas / (2.0 * diameter) / 1e5;
      double dpGrav = rhoG * GRAVITY * segElev / 1e5;
      return dpFric + dpGrav;
    } else {
      // Single-phase liquid
      rhoL = sys.getDensity("kg/m3");
      muL = sys.getViscosity("kg/msec");
      double vLiq = totalMassFlow / (rhoL * area);
      double reLiq = rhoL * vLiq * diameter / muL;
      double fLiq = calcFrictionFactor(reLiq);
      double dpFric = fLiq * segLength * rhoL * vLiq * vLiq / (2.0 * diameter) / 1e5;
      double dpGrav = rhoL * GRAVITY * segElev / 1e5;
      return dpFric + dpGrav;
    }

    // Two-phase calculation
    superficialLiquidVelocity = volFlowL / area;
    superficialGasVelocity = volFlowG / area;
    mixtureVelocity = superficialLiquidVelocity + superficialGasVelocity;

    if (mixtureVelocity < 1e-10) {
      return 0.0;
    }

    double lambdaL = superficialLiquidVelocity / mixtureVelocity; // No-slip liquid fraction

    // Dimensionless groups
    double fourthRoot = Math.pow(rhoL / (GRAVITY * sigmaL), 0.25);
    double nVl = superficialLiquidVelocity * fourthRoot;
    double nVg = superficialGasVelocity * fourthRoot;
    double nD = diameter * Math.sqrt(rhoL * GRAVITY / sigmaL);
    double nL = muL * Math.pow(GRAVITY / (rhoL * sigmaL * sigmaL * sigmaL), 0.25);

    // Liquid holdup from Hagedorn-Brown correlation (curve-fit approximation)
    calculatedHoldup = calcLiquidHoldup(nVl, nVg, nD, nL, lambdaL);

    // Ensure holdup is between no-slip fraction and 1.0
    calculatedHoldup = Math.max(calculatedHoldup, lambdaL);
    calculatedHoldup = Math.min(calculatedHoldup, 1.0);
    liquidHoldup = calculatedHoldup;

    // Mixture density with slip
    mixtureDensity = rhoL * calculatedHoldup + rhoG * (1.0 - calculatedHoldup);

    // No-slip mixture properties for friction
    double rhoNoSlip = rhoL * lambdaL + rhoG * (1.0 - lambdaL);
    double muNoSlip = muL * lambdaL + muG * (1.0 - lambdaL);

    // Reynolds number (no-slip)
    double re = rhoNoSlip * mixtureVelocity * diameter / muNoSlip;
    reynoldsNumber = re;
    double f = calcFrictionFactor(re);
    frictionFactor = f;

    // Pressure gradient components (Pa/m)
    double dpFriction = f * rhoNoSlip * mixtureVelocity * mixtureVelocity / (2.0 * diameter);
    double dpGravity = mixtureDensity * GRAVITY * (segElev / segLength);
    double dpAccel =
        mixtureDensity * mixtureVelocity * superficialGasVelocity / (system.getPressure() * 1e5);

    // Total pressure gradient (Pa/m)
    double dpdzTotal = (dpFriction + dpGravity) / (1.0 - dpAccel);

    // Pressure drop for segment (bar)
    return dpdzTotal * segLength / 1e5;
  }

  /**
   * Calculate liquid holdup using the Hagedorn-Brown empirical correlation.
   *
   * <p>
   * Uses curve-fit approximations to the original Hagedorn-Brown charts. The holdup is determined
   * from a correlation function of dimensionless velocity, diameter, and viscosity numbers.
   * </p>
   *
   * @param nVl liquid velocity number
   * @param nVg gas velocity number
   * @param nD pipe diameter number
   * @param nL liquid viscosity number
   * @param lambdaL no-slip liquid fraction
   * @return predicted liquid holdup (0 to 1)
   */
  private double calcLiquidHoldup(double nVl, double nVg, double nD, double nL, double lambdaL) {
    // Griffith-Wallis bubble flow check: if v_sg < 0.15 m/s, use bubble flow holdup
    if (superficialGasVelocity < 0.15 && lambdaL > 0.5) {
      // Bubble flow regime: use Griffith correction
      double vSlip = 0.244; // m/s (Taylor bubble rise velocity)
      double hl = 1.0 - 0.5 * (1.0 + mixtureVelocity / vSlip - Math
          .sqrt(Math.pow(1.0 + mixtureVelocity / vSlip, 2) - 4.0 * superficialGasVelocity / vSlip));
      return Math.max(lambdaL, Math.min(1.0, hl));
    }

    // Hagedorn-Brown correlation (curve-fit to charts)
    // Step 1: Get CNL from liquid viscosity number
    double cNl = calcCNL(nL);

    // Step 2: Calculate holdup parameter
    // (NvL * P^0.1 * CNL) / (NvG^0.575 * P_atm^0.1 * ND)
    double pBara = system.getPressure();
    double pAtm = 1.01325;
    double holdupParam = (nVl * Math.pow(pBara, 0.1) * cNl)
        / (Math.pow(nVg, 0.575) * Math.pow(pAtm, 0.1) * nD + 1e-10);

    // Step 3: Get holdup from correlation curve
    double hl = calcHoldupFromParam(holdupParam);

    // Step 4: Secondary correction factor psi
    double secondParam = nVg * Math.pow(nL, 0.38) / Math.pow(nD, 2.14);
    double psi = calcSecondaryCorrection(secondParam);

    return hl * psi;
  }

  /**
   * Calculate CNL coefficient from liquid viscosity number.
   *
   * @param nL liquid viscosity number
   * @return CNL coefficient
   */
  private double calcCNL(double nL) {
    // Curve fit to Hagedorn-Brown CNL chart
    if (nL <= 0.002) {
      return 0.0019;
    } else if (nL <= 0.4) {
      return 0.0019 + 0.0322 * Math.log10(nL / 0.002) / Math.log10(0.4 / 0.002);
    } else {
      return 0.0322 + 0.01 * Math.log10(nL / 0.4);
    }
  }

  /**
   * Calculate liquid holdup from the holdup parameter using curve-fit to chart.
   *
   * @param param holdup parameter
   * @return liquid holdup fraction
   */
  private double calcHoldupFromParam(double param) {
    // Curve-fit approximation to Hagedorn-Brown holdup chart
    if (param <= 0) {
      return 0.0;
    }
    // Logistic-type fit
    double logParam = Math.log10(Math.max(param, 1e-10));
    double hl = -0.10307 + 0.61777 * (logParam + 6.0) / (logParam + 6.0 + 0.5);
    return Math.max(0.0, Math.min(1.0, hl));
  }

  /**
   * Calculate secondary correction factor psi.
   *
   * @param param secondary parameter
   * @return correction factor psi
   */
  private double calcSecondaryCorrection(double param) {
    // psi is close to 1.0 for most conditions
    if (param < 0.01) {
      return 1.0;
    }
    double logParam = Math.log10(param);
    double psi = 1.0 + 0.3 * logParam;
    return Math.max(0.5, Math.min(1.5, psi));
  }

  /**
   * Calculate Moody/Haaland friction factor.
   *
   * @param re Reynolds number
   * @return Fanning friction factor
   */
  private double calcFrictionFactor(double re) {
    if (re < 1.0) {
      return 0.0;
    }
    if (re < 2300) {
      return 64.0 / re; // Laminar (Darcy)
    }
    // Haaland equation (explicit Moody-chart approximation)
    double relRoughness = roughness / diameter;
    double term = -1.8 * Math.log10(Math.pow(relRoughness / 3.7, 1.11) + 6.9 / re);
    double fDarcy = 1.0 / (term * term);
    return fDarcy; // Darcy friction factor
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
}
