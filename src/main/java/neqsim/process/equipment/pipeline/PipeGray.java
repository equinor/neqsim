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
 * Pipeline simulation using the Gray (1974) correlation for multiphase vertical flow in gas and gas-condensate wells.
 *
 * <p>
 * The Gray correlation was developed for the API 14B subsurface safety-valve program (Gray, H.E., 1974) and is the
 * industry standard for gas-dominated vertical wells producing condensate and/or water. It predicts the in-situ liquid
 * holdup and an effective pipe roughness that accounts for the liquid film, and is best applied when the superficial
 * gas velocity exceeds roughly 15 ft/s (about 4.6 m/s), the pipe diameter is below about 3.5 in, and the condensate
 * loading is below about 50 bbl/MMscf.
 * </p>
 *
 * <h2>Liquid holdup</h2>
 *
 * <p>
 * With the no-slip density $\rho_{ns} = \rho_L \lambda_L + \rho_g (1 - \lambda_L)$, the mixture (superficial) velocity
 * $v_m$, and the velocity ratio $R = v_{sL}/v_{sg}$, the dimensionless groups are
 * </p>
 *
 * <p>
 * $$ N_v = \frac{\rho_{ns}^2 v_m^4}{\sigma\,g\,(\rho_L - \rho_g)}, \qquad N_D = \frac{g\,(\rho_L -
 * \rho_g)\,D^2}{\sigma} $$
 * </p>
 *
 * <p>
 * $$ B = 0.0814\left[1 - 0.0554\,\ln\!\left(1 + \frac{730 R}{R + 1}\right)\right] $$
 * </p>
 *
 * <p>
 * $$ H_L = 1 - \frac{1 - \exp\!\left[-2.314\,(N_v(1 + 205/N_D))^{B}\right]}{R + 1} $$
 * </p>
 *
 * <h2>Effective roughness</h2>
 *
 * <p>
 * Gray defines a condensate-film effective roughness $k_e = 28.5\,\sigma/(\rho_{ns} v_m^2)$ (used directly when $R \ge
 * 0.007$, and blended toward the bare-pipe roughness for lower liquid loading), which is applied in the friction
 * factor.
 * </p>
 *
 * <p>
 * An alternative holdup closure using the Woldesemayat-Ghajar (2007) void-fraction correlation is available via
 * {@link #setHoldupMethod(HoldupMethod)}.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class PipeGray extends Pipeline {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PipeGray.class);

  /** Acceleration of gravity in m/s2. */
  private static final double GRAVITY = 9.81;

  /**
   * Selectable liquid-holdup closure for {@link PipeGray}.
   *
   * @author NeqSim
   * @version 1.0
   */
  public enum HoldupMethod {
    /** Gray (1974) holdup correlation (default). */
    GRAY,
    /** Woldesemayat-Ghajar (2007) void-fraction correlation. */
    WOLDESEMAYAT_GHAJAR
  }

  /** Selected holdup closure. */
  private HoldupMethod holdupMethod = HoldupMethod.GRAY;

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

  /** Effective (film) roughness from the Gray model, m. */
  private double effectiveRoughness = 0.0;

  /** Pressure profile along the pipe. */
  private List<Double> pressureProfileList = new ArrayList<Double>();

  /** Temperature profile along the pipe. */
  private List<Double> temperatureProfileList = new ArrayList<Double>();

  /** Length profile along the pipe. */
  private List<Double> lengthProfileList = new ArrayList<Double>();

  /**
   * Default constructor for PipeGray.
   */
  public PipeGray() {
    super("PipeGray");
  }

  /**
   * Constructor with name.
   *
   * @param name equipment name
   */
  public PipeGray(String name) {
    super(name);
  }

  /**
   * Constructor with name and inlet stream.
   *
   * @param name equipment name
   * @param inStream inlet stream
   */
  public PipeGray(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * Set the liquid-holdup closure used by the model.
   *
   * @param holdupMethod the holdup method (Gray or Woldesemayat-Ghajar)
   */
  public void setHoldupMethod(HoldupMethod holdupMethod) {
    this.holdupMethod = holdupMethod;
  }

  /**
   * Get the liquid-holdup closure used by the model.
   *
   * @return the selected holdup method
   */
  public HoldupMethod getHoldupMethod() {
    return holdupMethod;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (diameter <= 0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException("PipeGray", "run", "diameter",
          "must be positive, got: " + diameter));
    }
    if (numberOfIncrements <= 0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException("PipeGray", "run",
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
   * Calculate pressure drop for a single pipe segment using the Gray method.
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
      // Single-phase gas
      rhoG = sys.getPhase("gas").getDensity("kg/m3");
      muG = sys.getPhase("gas").getViscosity("kg/msec");
      double vGas = totalMassFlow / (rhoG * area);
      double reGas = rhoG * vGas * diameter / muG;
      double fGas = calcFrictionFactor(reGas, roughness / diameter);
      double dpFric = fGas * segLength * rhoG * vGas * vGas / (2.0 * diameter) / 1e5;
      double dpGrav = rhoG * GRAVITY * segElev / 1e5;
      return dpFric + dpGrav;
    } else {
      // Single-phase liquid
      rhoL = sys.getDensity("kg/m3");
      muL = sys.getViscosity("kg/msec");
      double vLiq = totalMassFlow / (rhoL * area);
      double reLiq = rhoL * vLiq * diameter / muL;
      double fLiq = calcFrictionFactor(reLiq, roughness / diameter);
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
    double rhoNoSlip = rhoL * lambdaL + rhoG * (1.0 - lambdaL);
    double muNoSlip = muL * lambdaL + muG * (1.0 - lambdaL);

    // Liquid holdup
    if (holdupMethod == HoldupMethod.WOLDESEMAYAT_GHAJAR) {
      double alpha = VoidFractionCorrelations.woldesemayatGhajar(superficialGasVelocity, superficialLiquidVelocity,
          rhoL, rhoG, sigmaL, diameter, computeInclinationDeg(segElev, segLength), system.getPressure());
      calculatedHoldup = 1.0 - alpha;
    } else {
      calculatedHoldup = calcGrayHoldup(rhoL, rhoG, sigmaL, rhoNoSlip, lambdaL);
    }
    calculatedHoldup = Math.max(calculatedHoldup, lambdaL);
    calculatedHoldup = Math.min(calculatedHoldup, 1.0);
    liquidHoldup = calculatedHoldup;

    // Mixture density with slip (for the gravity term)
    mixtureDensity = rhoL * calculatedHoldup + rhoG * (1.0 - calculatedHoldup);

    // Gray effective roughness
    double r = superficialGasVelocity > 1e-12 ? superficialLiquidVelocity / superficialGasVelocity : 0.0;
    effectiveRoughness = calcEffectiveRoughness(sigmaL, rhoNoSlip, mixtureVelocity, r);
    double relRoughness = effectiveRoughness / diameter;

    // Reynolds number (no-slip)
    double re = rhoNoSlip * mixtureVelocity * diameter / muNoSlip;
    reynoldsNumber = re;
    double f = calcFrictionFactor(re, relRoughness);
    frictionFactor = f;

    // Pressure gradient components (Pa/m)
    double dpFriction = f * rhoNoSlip * mixtureVelocity * mixtureVelocity / (2.0 * diameter);
    double dpGravity = mixtureDensity * GRAVITY * (segElev / segLength);
    double dpAccel = mixtureDensity * mixtureVelocity * superficialGasVelocity / (system.getPressure() * 1e5);

    double dpdzTotal = (dpFriction + dpGravity) / (1.0 - dpAccel);
    return dpdzTotal * segLength / 1e5;
  }

  /**
   * Gray (1974) liquid holdup correlation.
   *
   * @param rhoL liquid density (kg/m3)
   * @param rhoG gas density (kg/m3)
   * @param sigmaL gas-liquid interfacial tension (N/m)
   * @param rhoNoSlip no-slip mixture density (kg/m3)
   * @param lambdaL no-slip liquid fraction
   * @return predicted liquid holdup (0 to 1)
   */
  private double calcGrayHoldup(double rhoL, double rhoG, double sigmaL, double rhoNoSlip, double lambdaL) {
    double dRho = rhoL - rhoG;
    if (dRho < 1e-6 || sigmaL < 1e-9) {
      return lambdaL;
    }
    double r = superficialGasVelocity > 1e-12 ? superficialLiquidVelocity / superficialGasVelocity : 0.0;
    double nV = rhoNoSlip * rhoNoSlip * Math.pow(mixtureVelocity, 4) / (sigmaL * GRAVITY * dRho);
    double nD = GRAVITY * dRho * diameter * diameter / sigmaL;
    double b = 0.0814 * (1.0 - 0.0554 * Math.log(1.0 + 730.0 * r / (r + 1.0)));
    double arg = nV * (1.0 + 205.0 / nD);
    double hl = 1.0 - (1.0 - Math.exp(-2.314 * Math.pow(arg, b))) / (r + 1.0);
    return Math.max(0.0, Math.min(1.0, hl));
  }

  /**
   * Gray (1974) effective (condensate-film) roughness.
   *
   * @param sigmaL gas-liquid interfacial tension (N/m)
   * @param rhoNoSlip no-slip mixture density (kg/m3)
   * @param vm mixture (superficial) velocity (m/s)
   * @param r liquid-to-gas superficial velocity ratio
   * @return effective roughness (m), not less than the bare-pipe roughness
   */
  private double calcEffectiveRoughness(double sigmaL, double rhoNoSlip, double vm, double r) {
    if (rhoNoSlip < 1e-9 || vm < 1e-9) {
      return roughness;
    }
    double kFilm = 28.5 * sigmaL / (rhoNoSlip * vm * vm);
    double ke;
    if (r >= 0.007) {
      ke = kFilm;
    } else {
      ke = roughness + r * (kFilm - roughness) / 0.007;
    }
    return Math.max(ke, roughness);
  }

  /**
   * Inclination from horizontal in degrees for a segment.
   *
   * @param segElev segment elevation change (m)
   * @param segLength segment length (m)
   * @return inclination in degrees (0 = horizontal, 90 = vertical)
   */
  private double computeInclinationDeg(double segElev, double segLength) {
    if (segLength < 1e-12) {
      return 90.0;
    }
    double ratio = Math.max(-1.0, Math.min(1.0, segElev / segLength));
    return Math.toDegrees(Math.asin(ratio));
  }

  /**
   * Calculate Darcy friction factor using the Haaland explicit approximation.
   *
   * @param re Reynolds number
   * @param relRoughness relative roughness (roughness/diameter)
   * @return Darcy friction factor
   */
  private double calcFrictionFactor(double re, double relRoughness) {
    if (re < 1.0) {
      return 0.0;
    }
    if (re < 2300) {
      return 64.0 / re;
    }
    double term = -1.8 * Math.log10(Math.pow(relRoughness / 3.7, 1.11) + 6.9 / re);
    return 1.0 / (term * term);
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
  @Override
  public double getLiquidHoldup() {
    return calculatedHoldup;
  }

  /**
   * Get the Gray effective (condensate-film) roughness at the last segment.
   *
   * @return effective roughness in meters
   */
  public double getEffectiveRoughness() {
    return effectiveRoughness;
  }

  /**
   * Get the superficial gas velocity at the last segment.
   *
   * @return superficial gas velocity in m/s
   */
  public double getSuperficialGasVelocity() {
    return superficialGasVelocity;
  }

  /**
   * Get the superficial liquid velocity at the last segment.
   *
   * @return superficial liquid velocity in m/s
   */
  public double getSuperficialLiquidVelocity() {
    return superficialLiquidVelocity;
  }
}
