package neqsim.process.safety.release;

import java.io.Serializable;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Calculates source terms for leak and rupture release scenarios.
 *
 * <p>
 * This class provides methods for calculating time-dependent release rates from pressurized vessels
 * or pipes. The calculations use NeqSim thermodynamics for accurate real-gas properties and phase
 * behavior.
 * </p>
 *
 * <p>
 * Key capabilities:
 * <ul>
 * <li>Choked (sonic) and subsonic flow</li>
 * <li>Two-phase flashing releases</li>
 * <li>Time-dependent blowdown from vessels</li>
 * <li>Jet properties (velocity, momentum)</li>
 * <li>Droplet size estimation for liquid releases</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * 
 * <pre>
 * SystemInterface gas = new SystemSrkEos(300.0, 50.0);
 * gas.addComponent("methane", 1.0);
 * gas.setMixingRule("classic");
 * 
 * LeakModel leak = new LeakModel.builder().fluid(gas).holeDiameter(0.02) // 20mm hole
 *     .orientation(ReleaseOrientation.HORIZONTAL).vesselVolume(10.0) // 10 m³
 *     .dischargeCoefficient(0.62).build();
 * 
 * SourceTermResult result = leak.calculateSourceTerm(300.0); // 5 minutes
 * result.exportToPHAST("release.csv");
 * </pre>
 *
 * <p>
 * References:
 * <ul>
 * <li>API 520 - Sizing and Selection of Pressure-Relieving Devices</li>
 * <li>CCPS - Guidelines for Consequence Analysis</li>
 * <li>Yellow Book (TNO) - Methods for calculation of physical effects</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class LeakModel implements Serializable {
  private static final long serialVersionUID = 1L;

  private final SystemInterface fluid;
  private final double holeDiameter; // m
  private final ReleaseOrientation orientation;
  private final double vesselVolume; // m³
  private final double dischargeCoefficient;
  private final double backPressure; // Pa
  private final String scenarioName;

  private LeakModel(Builder builder) {
    this.fluid = builder.fluid.clone();
    this.holeDiameter = builder.holeDiameter;
    this.orientation = builder.orientation;
    this.vesselVolume = builder.vesselVolume;
    this.dischargeCoefficient = builder.dischargeCoefficient;
    this.backPressure = builder.backPressure;
    this.scenarioName = builder.scenarioName;
  }

  /**
   * Calculates the instantaneous mass flow rate through the orifice.
   *
   * <p>
   * Uses choked or subsonic flow equations depending on pressure ratio.
   * </p>
   *
   * @param system thermodynamic system at current conditions
   * @return mass flow rate [kg/s]
   */
  public double calculateMassFlowRate(SystemInterface system) {
    // Clone and flash to ensure proper state
    SystemInterface flashedSystem = system.clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(flashedSystem);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // If flash fails, try init
      flashedSystem.init(3);
    }

    double P = flashedSystem.getPressure() * 1e5; // Pa
    double T = flashedSystem.getTemperature(); // K

    // Safety check for pressure
    if (P <= backPressure) {
      return 0.0;
    }

    double rho = flashedSystem.getDensity("kg/m3");
    if (Double.isNaN(rho) || rho <= 0) {
      // Estimate density from ideal gas law as fallback
      double MW = flashedSystem.getMolarMass();
      if (Double.isNaN(MW) || MW <= 0) {
        MW = 0.016; // Methane MW in kg/mol
      }
      rho = (P * MW) / (8.314 * T);
    }

    // Get gamma (Cp/Cv) - use approximation if not available
    double gamma;
    try {
      gamma = flashedSystem.getGamma();
      if (Double.isNaN(gamma) || gamma <= 1.0 || gamma > 2.0) {
        gamma = 1.3; // Default for hydrocarbon gases
      }
    } catch (Exception e) {
      gamma = 1.3;
    }

    double MW = flashedSystem.getMolarMass() * 1000; // kg/kmol
    if (Double.isNaN(MW) || MW <= 0) {
      MW = 16.0; // Default to methane MW
    }

    // Hole area
    double A = Math.PI * Math.pow(holeDiameter / 2, 2);

    // Critical pressure ratio for choked flow
    double criticalRatio = Math.pow(2.0 / (gamma + 1), gamma / (gamma - 1));
    double pressureRatio = backPressure / P;

    double massFlowRate;

    if (pressureRatio <= criticalRatio) {
      // Choked (sonic) flow - simplified formula
      // mdot = Cd * A * P * sqrt(gamma * MW / (R * T)) * (2/(gamma+1))^((gamma+1)/(2*(gamma-1)))
      double R = 8314.0; // J/(kmol*K)
      double flowFactor = Math.pow(2.0 / (gamma + 1), (gamma + 1) / (2 * (gamma - 1)));
      massFlowRate = dischargeCoefficient * A * P * Math.sqrt(gamma * MW / (R * T)) * flowFactor;
    } else {
      // Subsonic flow
      double dP = P - backPressure;
      if (dP <= 0) {
        return 0.0;
      }
      massFlowRate = dischargeCoefficient * A * Math.sqrt(2 * rho * dP);
    }

    // Sanity check
    if (Double.isNaN(massFlowRate) || massFlowRate < 0) {
      return 0.0;
    }

    return massFlowRate;
  }

  /**
   * Calculates the jet velocity at the orifice exit.
   *
   * @param system thermodynamic system at current conditions
   * @return jet velocity [m/s]
   */
  public double calculateJetVelocity(SystemInterface system) {
    double mdot = calculateMassFlowRate(system);
    if (mdot <= 0) {
      return 0.0;
    }

    // Clone and flash to get proper density
    SystemInterface flashedSystem = system.clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(flashedSystem);
    ops.TPflash();

    double rho = flashedSystem.getDensity("kg/m3");
    if (rho <= 0) {
      rho = 1.0; // Fallback
    }

    double A = Math.PI * Math.pow(holeDiameter / 2, 2);

    // Get gamma for speed of sound calculation
    double T = flashedSystem.getTemperature();
    double gamma;
    try {
      gamma = flashedSystem.getGamma();
      if (Double.isNaN(gamma) || gamma <= 1.0 || gamma > 2.0) {
        gamma = 1.3;
      }
    } catch (Exception e) {
      gamma = 1.3;
    }

    double MW = flashedSystem.getMolarMass() * 1000;
    if (MW <= 0) {
      MW = 16.0;
    }

    double speedOfSound = Math.sqrt(gamma * 8314.0 * T / MW);

    double velocity = mdot / (rho * A * dischargeCoefficient);

    // Cap at speed of sound for sonic orifice
    return Math.min(velocity, speedOfSound * 1.2); // Allow slight overshoot for numerical reasons
  }

  /**
   * Calculates the jet momentum (reaction force).
   *
   * @param system thermodynamic system at current conditions
   * @return jet momentum / reaction force [N]
   */
  public double calculateJetMomentum(SystemInterface system) {
    double mdot = calculateMassFlowRate(system);
    double velocity = calculateJetVelocity(system);

    // F = mdot * v + (P - Pback) * A
    double P = system.getPressure() * 1e5;
    double A = Math.PI * Math.pow(holeDiameter / 2, 2);

    return mdot * velocity + (P - backPressure) * A;
  }

  /**
   * Estimates liquid droplet Sauter Mean Diameter (SMD) for two-phase releases.
   *
   * <p>
   * Uses correlation from CCPS Guidelines for Consequence Analysis.
   * </p>
   *
   * @param system thermodynamic system at current conditions
   * @return droplet SMD [m], or 0 if all vapor
   */
  public double calculateDropletSMD(SystemInterface system) {
    if (system.getNumberOfPhases() < 2) {
      return 0.0; // All vapor or all liquid
    }

    // Get liquid properties
    double liquidFraction = 1.0 - system.getBeta();
    if (liquidFraction < 0.001) {
      return 0.0;
    }

    double surfaceTension = 0.02; // N/m, approximate
    double velocity = calculateJetVelocity(system);
    double rhoLiquid = system.getPhase(1).getDensity("kg/m3");
    double rhoVapor = system.getPhase(0).getDensity("kg/m3");

    // Modified Weber number correlation
    double We = rhoVapor * Math.pow(velocity, 2) * holeDiameter / surfaceTension;

    // SMD correlation (simplified Nukiyama-Tanasawa)
    double smd = 585.0 * holeDiameter / Math.sqrt(We) * Math.sqrt(rhoLiquid / rhoVapor)
        * Math.pow(liquidFraction, 0.45);

    return Math.min(smd, holeDiameter / 2); // SMD cannot exceed half hole diameter
  }

  /**
   * Calculates time-dependent source term for vessel blowdown.
   *
   * @param duration simulation duration [s]
   * @return source term result with time series
   */
  public SourceTermResult calculateSourceTerm(double duration) {
    return calculateSourceTerm(duration, 1.0);
  }

  /**
   * Calculates time-dependent source term for vessel blowdown.
   *
   * @param duration simulation duration [s]
   * @param timeStep time step [s]
   * @return source term result with time series
   */
  public SourceTermResult calculateSourceTerm(double duration, double timeStep) {
    int numPoints = (int) Math.ceil(duration / timeStep) + 1;
    SourceTermResult result =
        new SourceTermResult(scenarioName, holeDiameter, orientation, numPoints);

    SystemInterface system = fluid.clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // Initialize if flash fails
      system.init(3);
    }

    // Initialize physical properties if needed
    system.initPhysicalProperties();

    double rho = system.getDensity("kg/m3");
    if (Double.isNaN(rho) || rho <= 0) {
      // Force proper initialization
      system.init(3);
      rho = system.getDensity("kg/m3");
    }

    double initialMass = rho * vesselVolume;
    double mass = initialMass;
    double totalReleased = 0.0;
    double peakRate = 0.0;

    for (int i = 0; i < numPoints && mass > 0.01 * initialMass; i++) {
      double t = i * timeStep;

      // Calculate current properties
      double mdot = calculateMassFlowRate(system);
      double velocity = calculateJetVelocity(system);
      double momentum = calculateJetMomentum(system);
      double smd = calculateDropletSMD(system);
      double vaporFraction = system.getBeta();

      // Record data point
      result.setDataPoint(i, t, mdot, system.getTemperature(), system.getPressure() * 1e5,
          vaporFraction, velocity, momentum, smd);

      peakRate = Math.max(peakRate, mdot);

      // Update mass and pressure for next step
      double massLost = mdot * timeStep;
      mass -= massLost;
      totalReleased += massLost;

      if (mass > 0 && mass > 0.01 * initialMass) {
        // Save current pressure before modifying
        double currentPressure = system.getPressure();

        // Update pressure assuming isentropic expansion
        double volumeFraction = mass / initialMass;

        // Get gamma with default fallback
        double gamma;
        try {
          gamma = system.getGamma();
          if (Double.isNaN(gamma) || gamma <= 1.0 || gamma > 2.0) {
            gamma = 1.3;
          }
        } catch (Exception e) {
          gamma = 1.3;
        }

        // New pressure from isentropic relation: P * V^gamma = const
        // P2 = P1 * (V1/V2)^gamma = P1 * (m1/m2)^gamma (since V is constant)
        double newPressure = currentPressure * Math.pow(volumeFraction, gamma);

        // Ensure pressure doesn't go below back pressure (in bar)
        newPressure = Math.max(newPressure, backPressure / 1e5);

        // Adiabatic temperature drop: T2 = T1 * (P2/P1)^((gamma-1)/gamma)
        double newTemp =
            system.getTemperature() * Math.pow(newPressure / currentPressure, (gamma - 1) / gamma);

        // Apply new conditions
        system.setPressure(newPressure);
        system.setTemperature(newTemp);

        ops.TPflash();
      }
    }

    result.setTotalMassReleased(totalReleased);
    result.setPeakMassFlowRate(peakRate);
    result.setTimeToEmpty(peakRate > 0 ? totalReleased / (peakRate * 0.5) : 0); // Approximate

    return result;
  }

  /**
   * Creates a new builder for LeakModel.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for LeakModel.
   */
  public static final class Builder {
    private SystemInterface fluid;
    private double holeDiameter = 0.01; // 10mm default
    private ReleaseOrientation orientation = ReleaseOrientation.HORIZONTAL;
    private double vesselVolume = 1.0; // 1 m³ default
    private double dischargeCoefficient = 0.62; // Sharp-edged orifice
    private double backPressure = 101325.0; // Atmospheric
    private String scenarioName = "Release";

    /**
     * Sets the fluid system.
     *
     * @param fluid thermodynamic system
     * @return this builder
     */
    public Builder fluid(SystemInterface fluid) {
      this.fluid = fluid;
      return this;
    }

    /**
     * Sets the hole diameter [m].
     *
     * @param diameter hole diameter in meters
     * @return this builder
     */
    public Builder holeDiameter(double diameter) {
      this.holeDiameter = diameter;
      return this;
    }

    /**
     * Sets the hole diameter with unit.
     *
     * @param diameter hole diameter value
     * @param unit diameter unit ("m", "mm", "in")
     * @return this builder
     */
    public Builder holeDiameter(double diameter, String unit) {
      if ("mm".equalsIgnoreCase(unit)) {
        this.holeDiameter = diameter / 1000.0;
      } else if ("in".equalsIgnoreCase(unit)) {
        this.holeDiameter = diameter * 0.0254;
      } else {
        this.holeDiameter = diameter;
      }
      return this;
    }

    /**
     * Sets the release orientation.
     *
     * @param orientation release direction
     * @return this builder
     */
    public Builder orientation(ReleaseOrientation orientation) {
      this.orientation = orientation;
      return this;
    }

    /**
     * Sets the vessel volume [m³].
     *
     * @param volume vessel volume in cubic meters
     * @return this builder
     */
    public Builder vesselVolume(double volume) {
      this.vesselVolume = volume;
      return this;
    }

    /**
     * Sets the orifice discharge coefficient.
     *
     * <p>
     * Typical values:
     * <ul>
     * <li>0.61-0.65 - Sharp-edged orifice</li>
     * <li>0.80-0.85 - Rounded entrance</li>
     * <li>0.95-0.99 - Smooth nozzle</li>
     * </ul>
     *
     * @param cd discharge coefficient
     * @return this builder
     */
    public Builder dischargeCoefficient(double cd) {
      this.dischargeCoefficient = cd;
      return this;
    }

    /**
     * Sets the back pressure [Pa].
     *
     * @param pressure back pressure in Pascals
     * @return this builder
     */
    public Builder backPressure(double pressure) {
      this.backPressure = pressure;
      return this;
    }

    /**
     * Sets the back pressure with unit.
     *
     * @param pressure back pressure value
     * @param unit pressure unit ("Pa", "bar", "psi")
     * @return this builder
     */
    public Builder backPressure(double pressure, String unit) {
      if ("bar".equalsIgnoreCase(unit)) {
        this.backPressure = pressure * 1e5;
      } else if ("psi".equalsIgnoreCase(unit)) {
        this.backPressure = pressure * 6894.76;
      } else {
        this.backPressure = pressure;
      }
      return this;
    }

    /**
     * Sets the scenario name.
     *
     * @param name scenario name
     * @return this builder
     */
    public Builder scenarioName(String name) {
      this.scenarioName = name;
      return this;
    }

    /**
     * Builds the LeakModel.
     *
     * @return new LeakModel instance
     */
    public LeakModel build() {
      if (fluid == null) {
        throw new IllegalStateException("Fluid system must be specified");
      }
      return new LeakModel(this);
    }
  }
}
