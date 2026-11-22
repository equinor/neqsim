package neqsim.process.safety.release;

import java.io.Serializable;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Calculates source terms for leak and rupture release scenarios.
 *
 * <p>
 * This class provides methods for calculating time-dependent release rates from pressurized vessels or pipes. The
 * calculations use NeqSim thermodynamics for accurate real-gas properties and phase behavior.
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
 * LeakModel leak = LeakModel.builder().fluid(gas).holeDiameter(0.02) // 20mm hole
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

  /**
   * Calculates an explicit release model using this opening's geometry and back pressure. This opt-in path does not
   * change the legacy scalar or blowdown calculations.
   *
   * @param system current upstream stagnation state
   * @param model selected instantaneous release model
   * @return model-explicit stations and diagnostics; inspect status before use
   * @throws IllegalArgumentException if model or request geometry is invalid
   */
  public ReleaseFlowResult calculateReleaseFlow(SystemInterface system, ReleaseFlowModel model) {
    if (model == null) {
      throw new IllegalArgumentException("Release model is required");
    }
    return model.calculate(new ReleaseFlowRequest(system, holeDiameter, dischargeCoefficient, backPressure));
  }

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

    return screeningMassFlowRate(P, T, rho, gamma, MW);
  }

  private double screeningMassFlowRate(double P, double T, double rho, double gamma, double MW) {
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
   * Calculates a rigid, adiabatic, well-mixed gas inventory blowdown using the existing screening orifice law.
   *
   * <p>
   * The supplied composition is scaled to the vessel volume. Each Euler substep removes bulk-composition mass and
   * upstream enthalpy, and a volume/internal-energy flash determines the remaining state. Substeps remove at most one
   * percent of inventory. Sampling includes zero and exactly the requested duration; the final sample causes no
   * additional release. Receiving-pressure crossings are located conservatively. Liquid or multiphase states,
   * condensation, forced phases, reactions, solids and hydrates are unsupported and fail explicitly, as do flash or
   * property failures. No ideal-gas property fallback is used by this time integration.
   * </p>
   *
   * @param duration finite, nonnegative simulation duration [s]
   * @param timeStep finite, positive reporting interval and maximum integration step [s]
   * @return source term result with instantaneous samples and integrated mass/energy accounting
   * @throws IllegalArgumentException for invalid configuration or unsupported phase/reaction options
   * @throws IllegalStateException for a nongas state, phase boundary, failed flash, or failed conservation check
   */
  public SourceTermResult calculateSourceTerm(double duration, double timeStep) {
    if (!Double.isFinite(duration) || duration < 0.0 || !Double.isFinite(timeStep) || timeStep <= 0.0
        || !Double.isFinite(vesselVolume) || vesselVolume <= 0.0 || !Double.isFinite(holeDiameter)
        || holeDiameter <= 0.0 || !Double.isFinite(dischargeCoefficient) || dischargeCoefficient <= 0.0
        || dischargeCoefficient > 1.0 || !Double.isFinite(backPressure) || backPressure <= 0.0) {
      throw new IllegalArgumentException(
          "Finite duration >= 0, positive timestep/volume/opening/back pressure and Cd in (0,1] required");
    }
    double intervals = Math.ceil(duration / timeStep);
    if (intervals > Integer.MAX_VALUE - 1.0) {
      throw new IllegalArgumentException("Too many source-term sample points");
    }
    int numPoints = (int) intervals + 1;
    SourceTermResult result = new SourceTermResult(scenarioName, holeDiameter, orientation, numPoints);
    SystemInterface system = fluid.clone();
    if (system.isChemicalSystem() || system.isForcePhaseTypes() || system.doSolidPhaseCheck()
        || system.getHydrateCheck()) {
      throw new IllegalArgumentException("BLOWDOWN_REGIME_UNSUPPORTED: unforced nonreacting gas required");
    }
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    system.initPhysicalProperties();
    requireBlowdownGas(system);
    system.setTotalNumberOfMoles(system.getTotalNumberOfMoles() * vesselVolume / system.getVolume("m3"));
    system.init(3);
    system.initPhysicalProperties();
    requireBlowdownGas(system);
    SystemInterface initial = system.clone();
    double totalReleased = 0.0;
    double releasedEnergy = 0.0;
    double peakRate = 0.0;
    double elapsed = 0.0;
    boolean stopped = system.getPressure() * 1e5 <= backPressure;
    int substeps = 0;
    for (int i = 0; i < numPoints; i++) {
      double sampleTime = i == numPoints - 1 ? duration : i * timeStep;
      while (!stopped && elapsed < sampleTime) {
        if (++substeps > 1000000 || Thread.currentThread().isInterrupted()) {
          throw new IllegalStateException("BLOWDOWN_SUBSTEP_LIMIT_OR_INTERRUPTED");
        }
        double rate = blowdownMassFlowRate(system);
        if (rate == 0.0) {
          stopped = true;
          break;
        }
        double step = Math.min(sampleTime - elapsed, 0.01 * system.getMass("kg") / rate);
        if (!(step > 0.0) || elapsed + step <= elapsed) {
          throw new IllegalStateException("BLOWDOWN_TIMESTEP_UNREPRESENTABLE");
        }
        SystemInterface next = advanceBlowdown(system, rate * step);
        double pressureTolerance = Math.max(1e-3, backPressure * 1e-9);
        if (next.getPressure() * 1e5 < backPressure) {
          // Locate the event by reducing mass/enthalpy withdrawal, never by clamping pressure alone.
          double lower = 0.0;
          double upper = step;
          boolean located = false;
          for (int iteration = 0; iteration < 64; iteration++) {
            step = 0.5 * (lower + upper);
            next = advanceBlowdown(system, rate * step);
            double difference = next.getPressure() * 1e5 - backPressure;
            if (difference >= 0.0 && difference <= pressureTolerance) {
              located = true;
              break;
            }
            if (difference >= 0.0) {
              lower = step;
            } else {
              upper = step;
            }
          }
          if (!located) {
            throw new IllegalStateException("BLOWDOWN_RECEIVING_PRESSURE_EVENT_FAILED");
          }
          stopped = true;
        }
        double removed = rate * step;
        totalReleased += removed;
        releasedEnergy += removed * system.getEnthalpy("J/kg");
        elapsed += step;
        system = next;
        stopped |= system.getPressure() * 1e5 <= backPressure + pressureTolerance;
      }
      elapsed = sampleTime;
      double rate = stopped ? 0.0 : blowdownMassFlowRate(system);
      double area = Math.PI * holeDiameter * holeDiameter / 4.0;
      double velocity = rate == 0.0 ? 0.0
          : Math.min(rate / (system.getDensity("kg/m3") * area * dischargeCoefficient),
              1.2 * Math.sqrt(system.getGamma() * 8.314 * system.getTemperature() / system.getMolarMass()));
      double momentum = rate == 0.0 ? 0.0 : rate * velocity + (system.getPressure() * 1e5 - backPressure) * area;
      result.setDataPoint(i, sampleTime, rate, system.getTemperature(), system.getPressure() * 1e5, 1.0, velocity,
          momentum, 0.0);
      peakRate = Math.max(peakRate, rate);
    }
    checkBlowdownClosure(system.getMass("kg") + totalReleased, initial.getMass("kg"), initial.getMass("kg"), "mass");
    checkBlowdownClosure(system.getInternalEnergy("J") + releasedEnergy, initial.getInternalEnergy("J"),
        Math.max(Math.abs(initial.getInternalEnergy("J")), Math.abs(releasedEnergy)), "cumulative energy");
    for (int c = 0; c < initial.getNumberOfComponents(); c++) {
      double initialComponentMass = initial.getComponent(c).getNumberOfmoles() * initial.getComponent(c).getMolarMass();
      double finalComponentMass = system.getComponent(c).getNumberOfmoles() * system.getComponent(c).getMolarMass();
      checkBlowdownClosure(finalComponentMass + totalReleased * initialComponentMass / initial.getMass("kg"),
          initialComponentMass, initialComponentMass, "component mass");
    }
    result.setTotalMassReleased(totalReleased);
    result.setPeakMassFlowRate(peakRate);
    result.setTimeToEmpty(peakRate > 0 ? totalReleased / (peakRate * 0.5) : 0); // Legacy screening estimate
    result.setInventoryAccounting(initial, system, releasedEnergy);
    return result;
  }

  private double blowdownMassFlowRate(SystemInterface system) {
    requireBlowdownGas(system);
    return screeningMassFlowRate(system.getPressure() * 1e5, system.getTemperature(), system.getDensity("kg/m3"),
        system.getGamma(), system.getMolarMass() * 1000.0);
  }

  private SystemInterface advanceBlowdown(SystemInterface before, double removedMass) {
    SystemInterface next = before.clone();
    double energyBefore = before.getInternalEnergy("J");
    double outflowEnergy = removedMass * before.getEnthalpy("J/kg");
    double targetEnergy = energyBefore - outflowEnergy;
    double energyScale = Math.max(Math.abs(energyBefore), Math.abs(outflowEnergy));
    next.setTotalNumberOfMoles(before.getTotalNumberOfMoles() * (1.0 - removedMass / before.getMass("kg")));
    boolean converged = false;
    for (int solve = 0; solve < 32; solve++) {
      new ThermodynamicOperations(next).VUflash(vesselVolume, targetEnergy, "m3", "J");
      next.init(3);
      next.initPhysicalProperties();
      requireBlowdownGas(next);
      if (Math.abs(next.getVolume("m3") - vesselVolume) <= 1e-11 * vesselVolume
          && Math.abs(next.getInternalEnergy("J") - targetEnergy) <= 1e-11 * Math.max(1e-10, energyScale)) {
        converged = true;
        break;
      }
    }
    if (!converged) {
      throw new IllegalStateException("BLOWDOWN_VU_CLOSURE_FAILED");
    }
    return next;
  }

  private static void requireBlowdownGas(SystemInterface system) {
    if (system.getNumberOfPhases() != 1 || system.getPhase(0).getType() != PhaseType.GAS) {
      throw new IllegalStateException(
          "BLOWDOWN_PHASE_BOUNDARY: single gas phase required; condensation is unsupported");
    }
    double gamma = system.getGamma();
    if (!Double.isFinite(gamma) || gamma <= 1.0 || gamma > 2.0 || !Double.isFinite(system.getInternalEnergy("J"))
        || !Double.isFinite(system.getEnthalpy("J/kg"))) {
      throw new IllegalStateException("BLOWDOWN_PROPERTIES_INVALID: no screening property fallback permitted");
    }
    for (double value : new double[] {system.getMass("kg"), system.getTemperature(), system.getPressure(),
        system.getVolume("m3"), system.getDensity("kg/m3"), system.getMolarMass()}) {
      if (!Double.isFinite(value) || value <= 0.0) {
        throw new IllegalStateException("BLOWDOWN_PROPERTIES_INVALID: finite positive properties required");
      }
    }
  }

  private static void checkBlowdownClosure(double actual, double expected, double scale, String quantity) {
    if (!Double.isFinite(actual) || !Double.isFinite(expected)
        || Math.abs(actual - expected) > 1e-7 * Math.max(1e-10, Math.abs(scale))) {
      throw new IllegalStateException("BLOWDOWN_CLOSURE_FAILED: " + quantity);
    }
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
