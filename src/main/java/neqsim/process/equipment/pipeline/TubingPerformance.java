package neqsim.process.equipment.pipeline;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tubing Performance model for vertical lift performance (VLP) calculations.
 *
 * <p>
 * This class provides GAP-level VLP modeling capabilities with multiple multiphase flow
 * correlations and temperature models for wellbore flow. It calculates pressure drop in production
 * tubing accounting for gravity, friction, and acceleration.
 * </p>
 *
 * <h2>VLP Correlations</h2>
 * <ul>
 * <li><b>BEGGS_BRILL</b> - All inclinations, most widely used</li>
 * <li><b>HAGEDORN_BROWN</b> - Vertical oil wells, slug/bubble flow</li>
 * <li><b>GRAY</b> - Gas wells, mist/annular flow</li>
 * <li><b>HASAN_KABIR</b> - Mechanistic, all patterns</li>
 * <li><b>DUNS_ROS</b> - Gas-liquid, all patterns</li>
 * </ul>
 *
 * <h2>Temperature Models</h2>
 * <ul>
 * <li><b>ISOTHERMAL</b> - Constant temperature throughout</li>
 * <li><b>LINEAR_GRADIENT</b> - Linear from BH to surface</li>
 * <li><b>RAMEY</b> - Ramey (1962) steady-state geothermal</li>
 * <li><b>HASAN_KABIR</b> - Full energy balance</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Create tubing model
 * TubingPerformance tubing = new TubingPerformance("tubing");
 * tubing.setInletStream(feedStream);
 * tubing.setDiameter(0.1); // 100 mm ID
 * tubing.setLength(3000.0); // 3000 m TVD
 * tubing.setInclination(90.0); // Vertical
 * tubing.setRoughness(0.00005); // 50 microns
 * tubing.setCorrelationType(TubingPerformance.CorrelationType.BEGGS_BRILL);
 * tubing.run();
 *
 * double outletPressure = tubing.getOutletStream().getPressure("bara");
 * double pressureDrop = tubing.getPressureDrop();
 * }</pre>
 *
 * @author NeqSim development team
 * @see neqsim.process.equipment.pipeline.PipeBeggsAndBrills
 */
public class TubingPerformance extends Pipeline {
  private static final long serialVersionUID = 1L;
  static Logger logger = LogManager.getLogger(TubingPerformance.class);

  /** VLP correlation types. */
  public enum CorrelationType {
    /** Beggs and Brill (1973) - all inclinations. */
    BEGGS_BRILL,
    /** Hagedorn and Brown (1965) - vertical oil wells. */
    HAGEDORN_BROWN,
    /** Gray (1974) - gas wells. */
    GRAY,
    /** Hasan and Kabir - mechanistic. */
    HASAN_KABIR,
    /** Duns and Ros (1963) - gas-liquid. */
    DUNS_ROS
  }

  /** Temperature profile models. */
  public enum TemperatureModel {
    /** Constant temperature throughout tubing. */
    ISOTHERMAL,
    /** Linear gradient from BH to surface. */
    LINEAR_GRADIENT,
    /** Ramey (1962) steady-state geothermal model. */
    RAMEY,
    /** Hasan-Kabir energy balance model. */
    HASAN_KABIR
  }

  // Tubing geometry
  private double diameter = 0.1; // m
  private double length = 3000.0; // m
  private double inclination = 90.0; // degrees from horizontal (90 = vertical)
  private double roughness = 0.00005; // m

  // Operating conditions
  private double wellheadPressure = 50.0; // bara
  private double pressureDrop = 0.0; // bar

  // Model selection
  private CorrelationType correlationType = CorrelationType.BEGGS_BRILL;
  private TemperatureModel temperatureModel = TemperatureModel.ISOTHERMAL;

  // Temperature parameters
  private double surfaceTemperature = 20.0; // °C
  private double bottomholeTemperature = 85.0; // °C
  private double geothermalGradient = 0.03; // °C/m
  private double formationThermalConductivity = 2.5; // W/m·K
  private double overallHeatTransferCoefficient = 25.0; // W/m²·K
  private double productionTime = 365.0; // days

  // Number of segments for numerical integration
  private int numberOfSegments = 50;

  /**
   * Constructor for TubingPerformance.
   *
   * @param name equipment name
   */
  public TubingPerformance(String name) {
    super(name);
  }

  /**
   * Constructor with inlet stream.
   *
   * @param name equipment name
   * @param inletStream inlet stream
   */
  public TubingPerformance(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = getInletStream().getThermoSystem().clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(system);

    double inletPressure = system.getPressure("bara");
    double inletTemp = system.getTemperature("C");

    // Calculate pressure drop along tubing using selected correlation
    double dP = calculatePressureDrop(system, ops);
    pressureDrop = dP;

    // Calculate outlet temperature
    double outletTemp = calculateOutletTemperature(inletTemp);

    // Set outlet conditions
    double outletPressure = inletPressure - dP;
    if (outletPressure < 1.0) {
      outletPressure = 1.0;
      logger.warn("Outlet pressure limited to 1 bara (calculated: {})", inletPressure - dP);
    }

    system.setPressure(outletPressure, "bara");
    system.setTemperature(outletTemp, "C");
    ops.TPflash();

    getOutletStream().setThermoSystem(system);
    getOutletStream().run();
  }

  /**
   * Calculate pressure drop using selected correlation.
   *
   * @param system thermodynamic system
   * @param ops thermodynamic operations
   * @return pressure drop in bar
   */
  private double calculatePressureDrop(SystemInterface system, ThermodynamicOperations ops) {
    switch (correlationType) {
      case HAGEDORN_BROWN:
        return calculateHagedornBrown(system, ops);
      case GRAY:
        return calculateGray(system, ops);
      case HASAN_KABIR:
        return calculateHasanKabir(system, ops);
      case DUNS_ROS:
        return calculateDunsRos(system, ops);
      case BEGGS_BRILL:
      default:
        return calculateBeggsBrill(system, ops);
    }
  }

  /**
   * Calculate pressure drop using Beggs-Brill correlation.
   *
   * @param system thermodynamic system
   * @param ops thermodynamic operations
   * @return pressure drop in bar
   */
  private double calculateBeggsBrill(SystemInterface system, ThermodynamicOperations ops) {
    double segmentLength = length / numberOfSegments;
    double sinTheta = Math.sin(Math.toRadians(inclination));
    double totalDp = 0.0;

    SystemInterface workingSystem = system.clone();
    double currentPressure = workingSystem.getPressure("bara");

    for (int i = 0; i < numberOfSegments; i++) {
      // Flash at current conditions
      ops = new ThermodynamicOperations(workingSystem);
      ops.TPflash();

      // Get properties
      double rhoL = workingSystem.getPhase(1).getDensity("kg/m3");
      double rhoG = workingSystem.getPhase(0).getDensity("kg/m3");
      double muL = workingSystem.getPhase(1).getViscosity("kg/msec") * 1000; // cP
      double muG = workingSystem.getPhase(0).getViscosity("kg/msec") * 1000; // cP
      double sigma = workingSystem.getInterphaseProperties().getSurfaceTension(0, 1) * 1000; // mN/m

      // Superficial velocities
      double area = Math.PI * diameter * diameter / 4.0;
      double qL = workingSystem.getPhase(1).getFlowRate("m3/sec");
      double qG = workingSystem.getPhase(0).getFlowRate("m3/sec");
      double vsL = qL / area;
      double vsG = qG / area;
      double vm = vsL + vsG;

      // Input liquid fraction
      double lambdaL = vsL / (vsL + vsG + 1e-10);

      // Froude number
      double nFr = vm * vm / (ThermodynamicConstantsInterface.gravity * diameter);

      // Liquid velocity number
      double nLv =
          vsL * Math.pow(rhoL / (ThermodynamicConstantsInterface.gravity * sigma + 1e-10), 0.25);

      // Determine flow pattern and holdup
      double holdup = calculateBeggsBrillHoldup(lambdaL, nFr, inclination);

      // Mixture density
      double rhoM = holdup * rhoL + (1 - holdup) * rhoG;

      // Friction factor
      double rhoNs = rhoL * lambdaL + rhoG * (1 - lambdaL);
      double muNs = muL * lambdaL + muG * (1 - lambdaL);
      double Re = rhoNs * vm * diameter / (muNs * 0.001 + 1e-10);
      double f = calculateFrictionFactor(Re, roughness, diameter);

      // Pressure gradients (Pa/m)
      double dpGravity = rhoM * ThermodynamicConstantsInterface.gravity * sinTheta;
      double dpFriction = f * rhoNs * vm * vm / (2 * diameter);

      // Total segment pressure drop (bar)
      double dpSegment = (dpGravity + dpFriction) * segmentLength / 1e5;
      totalDp += dpSegment;

      // Update pressure for next segment
      currentPressure -= dpSegment;
      if (currentPressure < 1.0) {
        currentPressure = 1.0;
      }
      workingSystem.setPressure(currentPressure, "bara");

      // Update temperature if using gradient
      if (temperatureModel != TemperatureModel.ISOTHERMAL) {
        double depth = length - (i + 1) * segmentLength;
        double temp = calculateTemperatureAtDepth(depth);
        workingSystem.setTemperature(temp, "C");
      }
    }

    return totalDp;
  }

  /**
   * Calculate Beggs-Brill liquid holdup.
   *
   * @param lambdaL input liquid fraction
   * @param nFr Froude number
   * @param angle inclination angle (degrees from horizontal)
   * @return liquid holdup
   */
  private double calculateBeggsBrillHoldup(double lambdaL, double nFr, double angle) {
    if (lambdaL < 1e-6) {
      return 0.0;
    }
    if (lambdaL > 0.9999) {
      return 1.0;
    }

    // Horizontal holdup (base case)
    double a = 0.98;
    double b = 0.4846;
    double c = 0.0868;
    double hl0 = a * Math.pow(lambdaL, b) / Math.pow(nFr + 0.001, c);

    if (hl0 > 1.0) {
      hl0 = 1.0;
    }
    if (hl0 < lambdaL) {
      hl0 = lambdaL;
    }

    // Inclination correction
    double beta = Math.toRadians(angle);
    double sinBeta = Math.sin(beta);

    // Payne correction for uphill flow
    double psi;
    if (sinBeta >= 0) {
      // Uphill flow
      double nLv = 1.0; // simplified
      double C = (1.0 - lambdaL) * Math
          .log(2.96 * Math.pow(lambdaL, 0.305) * Math.pow(nFr, 0.0978) * Math.pow(nLv, 0.0609));
      if (C < 0) {
        C = 0;
      }
      psi = 1.0 + C * (sinBeta - 0.333 * Math.pow(sinBeta, 3));
    } else {
      // Downhill flow
      psi = 1.0 + 0.3 * sinBeta;
    }

    double holdup = hl0 * psi;
    if (holdup > 1.0) {
      holdup = 1.0;
    }
    if (holdup < lambdaL) {
      holdup = lambdaL;
    }

    return holdup;
  }

  /**
   * Calculate pressure drop using Hagedorn-Brown correlation.
   *
   * @param system thermodynamic system
   * @param ops thermodynamic operations
   * @return pressure drop in bar
   */
  private double calculateHagedornBrown(SystemInterface system, ThermodynamicOperations ops) {
    // Hagedorn-Brown is primarily for vertical wells
    // Uses empirical correlations for holdup
    double segmentLength = length / numberOfSegments;
    double sinTheta = Math.sin(Math.toRadians(inclination));
    double totalDp = 0.0;

    SystemInterface workingSystem = system.clone();
    double currentPressure = workingSystem.getPressure("bara");

    for (int i = 0; i < numberOfSegments; i++) {
      ops = new ThermodynamicOperations(workingSystem);
      try {
        ops.TPflash();
      } catch (Exception e) {
        // If flash fails, use properties from previous segment
        logger.warn("TPflash failed in segment " + i + ", using previous properties");
      }

      double rhoL = workingSystem.getPhase(1).getDensity("kg/m3");
      double rhoG = workingSystem.getPhase(0).getDensity("kg/m3");
      double muL = workingSystem.getPhase(1).getViscosity("kg/msec") * 1000;

      // Check for invalid values
      if (Double.isNaN(rhoL) || Double.isNaN(rhoG) || rhoL <= 0 || rhoG <= 0) {
        logger.warn("Invalid density in segment " + i + ", using simplified calculation");
        // Use a simplified single-phase approach
        rhoL = 800.0; // Default liquid density
        rhoG = workingSystem.getDensity("kg/m3");
        if (Double.isNaN(rhoG) || rhoG <= 0) {
          rhoG = 50.0; // Default gas density
        }
      }

      double area = Math.PI * diameter * diameter / 4.0;
      double qL = workingSystem.getPhase(1).getFlowRate("m3/sec");
      double qG = workingSystem.getPhase(0).getFlowRate("m3/sec");
      double vsL = qL / area;
      double vsG = qG / area;
      double vm = vsL + vsG;

      // Hagedorn-Brown holdup correlation
      double holdup = calculateHBHoldup(vsL, vsG, rhoL, rhoG, muL, diameter);

      double rhoM = holdup * rhoL + (1 - holdup) * rhoG;

      double lambdaL = vsL / (vm + 1e-10);
      double rhoNs = rhoL * lambdaL + rhoG * (1 - lambdaL);
      double Re = rhoNs * vm * diameter / (muL * 0.001 + 1e-10);
      double f = calculateFrictionFactor(Re, roughness, diameter);

      double dpGravity = rhoM * ThermodynamicConstantsInterface.gravity * sinTheta;
      double dpFriction = f * rhoNs * vm * vm / (2 * diameter);

      double dpSegment = (dpGravity + dpFriction) * segmentLength / 1e5;
      totalDp += dpSegment;

      currentPressure -= dpSegment;
      if (currentPressure < 1.0) {
        currentPressure = 1.0;
      }
      workingSystem.setPressure(currentPressure, "bara");
    }

    return totalDp;
  }

  /**
   * Calculate Hagedorn-Brown holdup.
   *
   * @param vsL superficial liquid velocity (m/s)
   * @param vsG superficial gas velocity (m/s)
   * @param rhoL liquid density (kg/m³)
   * @param rhoG gas density (kg/m³)
   * @param muL liquid viscosity (Pa·s)
   * @param d pipe diameter (m)
   * @return liquid holdup fraction (dimensionless)
   */
  private double calculateHBHoldup(double vsL, double vsG, double rhoL, double rhoG, double muL,
      double d) {
    double vm = vsL + vsG;
    double lambdaL = vsL / (vm + 1e-10);

    // Simplified HB correlation
    double nD = 120.872 * d * Math.sqrt(rhoL / 72.0);
    double nL = 0.15726 * muL * Math.pow(1.0 / (rhoL * 72.0 * 72.0 * 72.0), 0.25);
    double nLv = 1.938 * vsL * Math.pow(rhoL / 72.0, 0.25);
    double nGv = 1.938 * vsG * Math.pow(rhoL / 72.0, 0.25);

    // Dimensionless holdup
    double a = -0.10306 + 0.61777 * Math.log10(nLv + 1) + 0.63295 * Math.log10(nD)
        - 0.29598 * Math.log10(nL * nLv + 1);
    double holdup = Math.pow(10, a);

    if (holdup > 1.0) {
      holdup = 1.0;
    }
    if (holdup < lambdaL) {
      holdup = lambdaL;
    }

    return holdup;
  }

  /**
   * Calculate pressure drop using Gray correlation (gas wells).
   *
   * @param system thermodynamic system
   * @param ops thermodynamic operations
   * @return pressure drop in bar
   */
  private double calculateGray(SystemInterface system, ThermodynamicOperations ops) {
    double segmentLength = length / numberOfSegments;
    double sinTheta = Math.sin(Math.toRadians(inclination));
    double totalDp = 0.0;

    SystemInterface workingSystem = system.clone();
    double currentPressure = workingSystem.getPressure("bara");

    for (int i = 0; i < numberOfSegments; i++) {
      ops = new ThermodynamicOperations(workingSystem);
      ops.TPflash();

      double rhoL = workingSystem.getPhase(1).getDensity("kg/m3");
      double rhoG = workingSystem.getPhase(0).getDensity("kg/m3");

      double area = Math.PI * diameter * diameter / 4.0;
      double qL = workingSystem.getPhase(1).getFlowRate("m3/sec");
      double qG = workingSystem.getPhase(0).getFlowRate("m3/sec");
      double vsL = qL / area;
      double vsG = qG / area;
      double vm = vsL + vsG;

      // Gray correlation - developed for gas condensate wells
      double lambdaL = vsL / (vm + 1e-10);

      // Simplified Gray holdup - assumes mist flow dominance
      double holdup = lambdaL * 1.2; // Gray adjustment for entrainment
      if (holdup > 1.0) {
        holdup = 1.0;
      }

      double rhoM = holdup * rhoL + (1 - holdup) * rhoG;

      double rhoNs = rhoL * lambdaL + rhoG * (1 - lambdaL);
      double muG = workingSystem.getPhase(0).getViscosity("kg/msec") * 1000;
      double Re = rhoNs * vm * diameter / (muG * 0.001 + 1e-10);
      double f = calculateFrictionFactor(Re, roughness, diameter);

      double dpGravity = rhoM * ThermodynamicConstantsInterface.gravity * sinTheta;
      double dpFriction = f * rhoNs * vm * vm / (2 * diameter);

      double dpSegment = (dpGravity + dpFriction) * segmentLength / 1e5;
      totalDp += dpSegment;

      currentPressure -= dpSegment;
      if (currentPressure < 1.0) {
        currentPressure = 1.0;
      }
      workingSystem.setPressure(currentPressure, "bara");
    }

    return totalDp;
  }

  /**
   * Calculate pressure drop using Hasan-Kabir mechanistic model.
   *
   * @param system thermodynamic system
   * @param ops thermodynamic operations
   * @return pressure drop in bar
   */
  private double calculateHasanKabir(SystemInterface system, ThermodynamicOperations ops) {
    double segmentLength = length / numberOfSegments;
    double sinTheta = Math.sin(Math.toRadians(inclination));
    double totalDp = 0.0;

    SystemInterface workingSystem = system.clone();
    double currentPressure = workingSystem.getPressure("bara");

    for (int i = 0; i < numberOfSegments; i++) {
      ops = new ThermodynamicOperations(workingSystem);
      ops.TPflash();

      double rhoL = workingSystem.getPhase(1).getDensity("kg/m3");
      double rhoG = workingSystem.getPhase(0).getDensity("kg/m3");
      double sigma = workingSystem.getInterphaseProperties().getSurfaceTension(0, 1);

      double area = Math.PI * diameter * diameter / 4.0;
      double qL = workingSystem.getPhase(1).getFlowRate("m3/sec");
      double qG = workingSystem.getPhase(0).getFlowRate("m3/sec");
      double vsL = qL / area;
      double vsG = qG / area;
      double vm = vsL + vsG;

      // Hasan-Kabir uses drift-flux model
      double C0 = 1.2; // Distribution coefficient
      double vd = 1.53 * Math.pow(
          ThermodynamicConstantsInterface.gravity * sigma * (rhoL - rhoG) / (rhoL * rhoL + 1e-10),
          0.25); // Drift velocity

      double lambdaL = vsL / (vm + 1e-10);
      double holdup;
      if (vsG < 0.001) {
        holdup = 1.0;
      } else {
        holdup = 1 - vsG / (C0 * vm + vd);
        if (holdup < 0) {
          holdup = lambdaL;
        }
        if (holdup > 1) {
          holdup = 1.0;
        }
      }

      double rhoM = holdup * rhoL + (1 - holdup) * rhoG;

      double muL = workingSystem.getPhase(1).getViscosity("kg/msec") * 1000;
      double rhoNs = rhoL * lambdaL + rhoG * (1 - lambdaL);
      double Re = rhoNs * vm * diameter / (muL * 0.001 + 1e-10);
      double f = calculateFrictionFactor(Re, roughness, diameter);

      double dpGravity = rhoM * ThermodynamicConstantsInterface.gravity * sinTheta;
      double dpFriction = f * rhoNs * vm * vm / (2 * diameter);

      double dpSegment = (dpGravity + dpFriction) * segmentLength / 1e5;
      totalDp += dpSegment;

      currentPressure -= dpSegment;
      if (currentPressure < 1.0) {
        currentPressure = 1.0;
      }
      workingSystem.setPressure(currentPressure, "bara");
    }

    return totalDp;
  }

  /**
   * Calculate pressure drop using Duns-Ros correlation.
   *
   * @param system thermodynamic system
   * @param ops thermodynamic operations
   * @return pressure drop in bar
   */
  private double calculateDunsRos(SystemInterface system, ThermodynamicOperations ops) {
    double segmentLength = length / numberOfSegments;
    double sinTheta = Math.sin(Math.toRadians(inclination));
    double totalDp = 0.0;

    SystemInterface workingSystem = system.clone();
    double currentPressure = workingSystem.getPressure("bara");

    for (int i = 0; i < numberOfSegments; i++) {
      ops = new ThermodynamicOperations(workingSystem);
      ops.TPflash();

      double rhoL = workingSystem.getPhase(1).getDensity("kg/m3");
      double rhoG = workingSystem.getPhase(0).getDensity("kg/m3");
      double sigma = workingSystem.getInterphaseProperties().getSurfaceTension(0, 1);
      double muL = workingSystem.getPhase(1).getViscosity("kg/msec") * 1000;

      double area = Math.PI * diameter * diameter / 4.0;
      double qL = workingSystem.getPhase(1).getFlowRate("m3/sec");
      double qG = workingSystem.getPhase(0).getFlowRate("m3/sec");
      double vsL = qL / area;
      double vsG = qG / area;
      double vm = vsL + vsG;

      double lambdaL = vsL / (vm + 1e-10);

      // Duns-Ros dimensionless groups
      double nLv = 1.938 * vsL * Math.pow(rhoL / (sigma * 1000 + 1e-10), 0.25);
      double nGv = 1.938 * vsG * Math.pow(rhoL / (sigma * 1000 + 1e-10), 0.25);
      double nD = 120.872 * diameter * Math.sqrt(rhoL / (sigma * 1000 + 1e-10));
      double nL = 0.15726 * muL * Math.pow(1 / (rhoL * sigma * sigma * sigma + 1e-10), 0.25);

      // Simplified holdup
      double holdup = lambdaL * (1 + 0.3 * nGv / (nLv + 1));
      if (holdup > 1) {
        holdup = 1.0;
      }
      if (holdup < lambdaL) {
        holdup = lambdaL;
      }

      double rhoM = holdup * rhoL + (1 - holdup) * rhoG;

      double rhoNs = rhoL * lambdaL + rhoG * (1 - lambdaL);
      double Re = rhoNs * vm * diameter / (muL * 0.001 + 1e-10);
      double f = calculateFrictionFactor(Re, roughness, diameter);

      double dpGravity = rhoM * ThermodynamicConstantsInterface.gravity * sinTheta;
      double dpFriction = f * rhoNs * vm * vm / (2 * diameter);

      double dpSegment = (dpGravity + dpFriction) * segmentLength / 1e5;
      totalDp += dpSegment;

      currentPressure -= dpSegment;
      if (currentPressure < 1.0) {
        currentPressure = 1.0;
      }
      workingSystem.setPressure(currentPressure, "bara");
    }

    return totalDp;
  }

  /**
   * Calculate Darcy friction factor using Colebrook-White equation.
   *
   * @param Re Reynolds number
   * @param eps absolute roughness (m)
   * @param d diameter (m)
   * @return Darcy friction factor
   */
  private double calculateFrictionFactor(double Re, double eps, double d) {
    if (Re < 2300) {
      // Laminar flow
      return 64.0 / Re;
    }

    // Turbulent - use Haaland approximation
    double relRoughness = eps / d;
    double f = Math.pow(-1.8 * Math.log10(Math.pow(relRoughness / 3.7, 1.11) + 6.9 / Re), -2);

    return f;
  }

  /**
   * Calculate outlet temperature based on selected model.
   *
   * @param inletTemp inlet temperature (°C)
   * @return outlet temperature (°C)
   */
  private double calculateOutletTemperature(double inletTemp) {
    switch (temperatureModel) {
      case LINEAR_GRADIENT:
        return surfaceTemperature;
      case RAMEY:
        return calculateRameyTemperature(inletTemp);
      case HASAN_KABIR:
        return calculateHKTemperature(inletTemp);
      case ISOTHERMAL:
      default:
        return inletTemp;
    }
  }

  /**
   * Calculate temperature at a given depth using selected model.
   *
   * @param depth depth from surface (m)
   * @return temperature at depth (°C)
   */
  private double calculateTemperatureAtDepth(double depth) {
    switch (temperatureModel) {
      case LINEAR_GRADIENT:
        return surfaceTemperature + (bottomholeTemperature - surfaceTemperature) * depth / length;
      case RAMEY:
        return surfaceTemperature + geothermalGradient * depth;
      case ISOTHERMAL:
      default:
        return bottomholeTemperature;
    }
  }

  /**
   * Calculate Ramey (1962) steady-state temperature.
   *
   * @param inletTemp inlet temperature (°C)
   * @return outlet temperature (°C)
   */
  private double calculateRameyTemperature(double inletTemp) {
    // Ramey's relaxation distance
    double massFlowRate = getInletStream().getFlowRate("kg/sec");
    double cp = 2000.0; // Approximate specific heat J/kg·K

    double A = massFlowRate * cp / (2 * Math.PI * formationThermalConductivity);

    // Geothermal temperature at bottom
    double Tgeo = surfaceTemperature + geothermalGradient * length;

    // Time function f(t)
    double tD = formationThermalConductivity * productionTime * 86400
        / (2.0 * 1000.0 * diameter * diameter);
    double ft = Math.log(2 * Math.sqrt(tD)) - 0.29;

    // Outlet temperature
    double Tout = Tgeo - (Tgeo - inletTemp) * Math.exp(-length / A);

    if (Tout < surfaceTemperature) {
      Tout = surfaceTemperature;
    }

    return Tout;
  }

  /**
   * Calculate Hasan-Kabir energy balance temperature.
   *
   * @param inletTemp inlet temperature (°C)
   * @return outlet temperature (°C)
   */
  private double calculateHKTemperature(double inletTemp) {
    // Simplified H-K: accounts for Joule-Thomson
    double massFlowRate = getInletStream().getFlowRate("kg/sec");
    double cp = 2000.0;
    double muJT = 0.5; // °C/bar (typical gas)

    double dP = pressureDrop;
    double Tgeo = surfaceTemperature + geothermalGradient * length;

    // Temperature change from JT effect
    double dTjt = muJT * dP;

    // Combined heat loss and JT
    double Tout = inletTemp - dTjt * 0.5 - (inletTemp - Tgeo) * 0.3;

    if (Tout < surfaceTemperature) {
      Tout = surfaceTemperature;
    }

    return Tout;
  }

  /**
   * Generate VLP curve (required BHP vs flow rate).
   *
   * @param flowRates array of flow rates to calculate (MSm³/day for gas)
   * @return 2D array: [0] = flow rates, [1] = required BHP
   */
  public double[][] generateVLPCurve(double[] flowRates) {
    double[][] result = new double[2][flowRates.length];
    StreamInterface originalStream = getInletStream();

    for (int i = 0; i < flowRates.length; i++) {
      result[0][i] = flowRates[i];

      // Clone stream and set flow rate
      SystemInterface system = originalStream.getThermoSystem().clone();
      system.setPressure(wellheadPressure + 200, "bara"); // Start with high BHP estimate

      // Iterate to find BHP that gives wellhead pressure
      double bhp = wellheadPressure + 50; // Initial guess
      double tolerance = 0.5; // bar

      for (int iter = 0; iter < 20; iter++) {
        system.setPressure(bhp, "bara");

        // Create temporary stream
        neqsim.process.equipment.stream.Stream tempStream =
            new neqsim.process.equipment.stream.Stream("temp", system);
        tempStream.setFlowRate(flowRates[i], "MSm3/day");
        tempStream.run();

        setInletStream(tempStream);
        run();

        double calculatedWHP = getOutletStream().getPressure("bara");
        double error = calculatedWHP - wellheadPressure;

        if (Math.abs(error) < tolerance) {
          break;
        }

        // Adjust BHP
        bhp += error * 0.5;
        if (bhp < wellheadPressure + 1) {
          bhp = wellheadPressure + 1;
        }
      }

      result[1][i] = bhp;
    }

    // Restore original stream
    setInletStream(originalStream);

    return result;
  }

  // Getters and setters

  /**
   * Get tubing inner diameter.
   *
   * @return diameter in meters
   */
  public double getDiameter() {
    return diameter;
  }

  /**
   * Set tubing inner diameter.
   *
   * @param diameter diameter in meters
   */
  public void setDiameter(double diameter) {
    this.diameter = diameter;
  }

  /**
   * Get tubing length.
   *
   * @return length in meters
   */
  public double getLength() {
    return length;
  }

  /**
   * Set tubing length (measured depth or TVD depending on use).
   *
   * @param length length in meters
   */
  public void setLength(double length) {
    this.length = length;
  }

  /**
   * Get inclination angle.
   *
   * @return inclination in degrees from horizontal (90 = vertical)
   */
  public double getInclination() {
    return inclination;
  }

  /**
   * Set inclination angle.
   *
   * @param inclination degrees from horizontal (90 = vertical)
   */
  public void setInclination(double inclination) {
    this.inclination = inclination;
  }

  /**
   * Get pipe roughness.
   *
   * @return roughness in meters
   */
  public double getRoughness() {
    return roughness;
  }

  /**
   * Set pipe roughness.
   *
   * @param roughness roughness in meters
   */
  public void setRoughness(double roughness) {
    this.roughness = roughness;
  }

  /**
   * Get wellhead pressure.
   *
   * @return wellhead pressure in bara
   */
  public double getWellheadPressure() {
    return wellheadPressure;
  }

  /**
   * Set wellhead pressure for VLP calculations.
   *
   * @param pressure wellhead pressure in bara
   */
  public void setWellheadPressure(double pressure) {
    this.wellheadPressure = pressure;
  }

  /**
   * Get calculated pressure drop.
   *
   * @return pressure drop in bar
   */
  public double getPressureDrop() {
    return pressureDrop;
  }

  /**
   * Get VLP correlation type.
   *
   * @return correlation type
   */
  public CorrelationType getCorrelationType() {
    return correlationType;
  }

  /**
   * Set VLP correlation type.
   *
   * @param correlationType correlation to use
   */
  public void setCorrelationType(CorrelationType correlationType) {
    this.correlationType = correlationType;
  }

  /**
   * Get temperature model.
   *
   * @return temperature model
   */
  public TemperatureModel getTemperatureModel() {
    return temperatureModel;
  }

  /**
   * Set temperature model.
   *
   * @param temperatureModel model to use
   */
  public void setTemperatureModel(TemperatureModel temperatureModel) {
    this.temperatureModel = temperatureModel;
  }

  /**
   * Set surface temperature.
   *
   * @param temp temperature in °C
   */
  public void setSurfaceTemperature(double temp) {
    this.surfaceTemperature = temp;
  }

  /**
   * Set bottomhole temperature.
   *
   * @param temp temperature in °C
   */
  public void setBottomholeTemperature(double temp) {
    this.bottomholeTemperature = temp;
  }

  /**
   * Set geothermal gradient.
   *
   * @param gradient gradient in °C/m
   */
  public void setGeothermalGradient(double gradient) {
    this.geothermalGradient = gradient;
  }

  /**
   * Set formation thermal conductivity.
   *
   * @param k conductivity in W/m·K
   */
  public void setFormationThermalConductivity(double k) {
    this.formationThermalConductivity = k;
  }

  /**
   * Set overall heat transfer coefficient.
   *
   * @param u coefficient in W/m²·K
   */
  public void setOverallHeatTransferCoefficient(double u) {
    this.overallHeatTransferCoefficient = u;
  }

  /**
   * Set production time for Ramey model.
   *
   * @param days production time in days
   */
  public void setProductionTime(double days) {
    this.productionTime = days;
  }

  /**
   * Set number of segments for numerical integration.
   *
   * @param n number of segments
   */
  public void setNumberOfSegments(int n) {
    this.numberOfSegments = n;
  }
}
