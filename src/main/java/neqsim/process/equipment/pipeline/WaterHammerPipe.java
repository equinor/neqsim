package neqsim.process.equipment.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Water hammer transient pipe model using conservative cell-integrated acoustic characteristics.
 *
 * <p>
 * This class simulates fast transient pressure waves (water hammer / hydraulic shock) in pipelines. Unlike the
 * advection-based transient model in {@link PipeBeggsAndBrills}, this model propagates pressure waves at the speed of
 * sound. Current analytical evidence is bounded to single-line, frozen-property acoustics:
 * </p>
 * <ul>
 * <li>Rapid Safety Shutdown valve closures</li>
 * <li>Prescribed actual valve travel and absolute liquid capacity</li>
 * <li>Optional forward pump curve and actual-speed history through {@link AcousticPumpBoundary}</li>
 * <li>Pressure-wave screening, subject to first-order numerical diffusion</li>
 * </ul>
 * <p>
 * Pump/disc dynamics, cavitation, structural support/stress loads and plant-wide coupling are not qualified by this
 * model. The N configured nodes bound N-1 physical cells. Cell means carry inventory; nodal profiles are physical face
 * traces, which must not be integrated as cell means. Boundary forcing uses a right-endpoint hold over each internal
 * interval. Refine both space and time to assess waveform and continuous-boundary quadrature errors.
 * </p>
 *
 * <p>
 * The Method of Characteristics transforms the hyperbolic partial differential equations for one-dimensional transient
 * pipe flow into ordinary differential equations along characteristic lines dx/dt = ±c (speed of sound).
 * </p>
 *
 * <p>
 * <b>Governing Equations:</b>
 * </p>
 * <ul>
 * <li>Continuity: ∂H/∂t + (c²/gA) ∂Q/∂x = 0</li>
 * <li>Momentum: ∂Q/∂t + gA (∂H/∂x + ∂z/∂x) + f/(2DA) Q|Q| = 0, with H=p/(ρg)</li>
 * </ul>
 *
 * <p>
 * <b>Example Usage:</b>
 * </p>
 *
 * <pre>
 * {@code
 * // Create fluid
 * SystemInterface water = new SystemSrkEos(298.15, 10.0);
 * water.addComponent("water", 1.0);
 * water.setMixingRule("classic");
 *
 * Stream feed = new Stream("feed", water);
 * feed.setFlowRate(100, "kg/hr");
 * feed.run();
 *
 * // Create water hammer pipe
 * WaterHammerPipe pipe = new WaterHammerPipe("pipe", feed);
 * pipe.setLength(1000); // 1 km
 * pipe.setDiameter(0.2); // 200 mm
 * pipe.setNumberOfNodes(100); // Grid resolution
 * pipe.run(); // Initialize steady state
 *
 * // Transient simulation with valve closure
 * pipe.setDownstreamBoundary(BoundaryType.VALVE);
 * UUID id = UUID.randomUUID();
 *
 * for (int step = 0; step < 1000; step++) {
 *   double t = step * 0.001; // 1 ms time step
 *
 *   // Close valve from t=0.1s to t=0.2s
 *   if (t >= 0.1 && t <= 0.2) {
 *     double tau = (t - 0.1) / 0.1;
 *     pipe.setValveOpening(1.0 - tau); // 100% -> 0%
 *   }
 *
 *   pipe.runTransient(0.001, id);
 * }
 *
 * // Get maximum pressure surge
 * double maxP = pipe.getMaxPressure();
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see PipeBeggsAndBrills
 */
public class WaterHammerPipe extends Pipeline {
  private static final long serialVersionUID = 1001;
  static Logger logger = LogManager.getLogger(WaterHammerPipe.class);

  /** Gravitational acceleration (m/s²). */
  private static final double GRAVITY = 9.81;

  /** Boundary condition types. */
  public enum BoundaryType {
    /** Constant pressure reservoir. */
    RESERVOIR,
    /** Valve with adjustable opening. */
    VALVE,
    /** Closed end (dead end). */
    CLOSED_END,
    /** Constant flow rate. */
    CONSTANT_FLOW,
    /** Declared forward single-liquid pump curve, upstream only. */
    PUMP
  }

  // Pipe geometry
  private double length = 1000.0; // m
  private double diameter = 0.2; // m
  private double wallThickness = 0.01; // m
  private double roughness = 4.6e-5; // m
  private double elevationChange = 0.0; // m (outlet - inlet)

  // Pipe material properties
  private double pipeElasticModulus = 200e9; // Pa (steel)
  private double pipePoissonsRatio = 0.3;

  // Grid and time parameters
  private int numberOfNodes = 50;
  private double waveSpeed = -1; // m/s (-1 = auto-calculate)
  private double courantNumber = 1.0; // Courant number for stability
  private double currentTime = 0.0; // Current simulation time

  // State arrays (head in meters, flow in m³/s)
  private double[] head; // Piezometric head at nodes
  private double[] flow; // Volumetric flow rate at nodes
  /** Mean pressure head in the physical cells between the configured nodes, metres. */
  private double[] cellHead;
  /** Mean actual volumetric flow in each physical cell, m3/s. */
  private double[] cellFlow;
  /** Initialized nodal pressure heads defining the exactly balanced linear reference state. */
  private double[] referenceHead;
  /** Initialized uniform actual volumetric flow, m3/s. */
  private double referenceFlow;
  /** Trial means, committed only after boundary and finite-state validation. */
  private double[] trialHead;
  /** Trial mean flows, m3/s. */
  private double[] trialFlow;
  /** Shared physical face pressure-head flux states, metres. */
  private double[] faceHead;
  /** Shared physical face flow flux states, m3/s. */
  private double[] faceFlow;
  /** Trial endpoint traces, distinct from interval-integrated flux states. */
  private double[] traceHead;
  /** Trial endpoint actual flow traces, m3/s. */
  private double[] traceFlow;
  /** Accumulated signed actual inlet exchange, m3, positive into the line. */
  private double inletVolume;
  /** Accumulated signed actual outlet exchange, m3, positive out of the line. */
  private double outletVolume;
  /** Accumulated net physical pressure impulse, kg m/s. */
  private double pressureImpulse;
  /** Accumulated terrain gravity impulse in the positive route direction, kg m/s. */
  private double gravityImpulse;
  /** Accumulated signed Darcy resistance impulse, kg m/s. */
  private double frictionImpulse;

  // Pressure and velocity for output
  private double[] pressureProfile; // Pa
  private double[] velocityProfile; // m/s

  // Envelope tracking
  private double[] maxPressureEnvelope;
  private double[] minPressureEnvelope;
  private List<Double> pressureHistory; // At outlet
  private List<Double> timeHistory;

  // Boundary conditions
  private BoundaryType upstreamBoundary = BoundaryType.RESERVOIR;
  private BoundaryType downstreamBoundary = BoundaryType.VALVE;
  private double upstreamHead; // m (for reservoir BC)
  private double downstreamHead; // m (for reservoir BC)
  private double valveOpening = 1.0; // 0-1 (for valve BC)
  private double valveBackPressureBara = 1.0;
  private double valveCv = -1; // Valve coefficient (-1 = auto from steady state)
  /** Optional absolute-capacity and prescribed-travel boundary; null preserves calibrated legacy mode. */
  private AcousticValveBoundary valveBoundary;
  /** Optional detached forward pump characteristic. */
  private AcousticPumpBoundary pumpBoundary;
  /** Optional ideal translating check-valve parameters. */
  private AcousticLiftCheckValve liftCheckValve;
  /** Accepted translating position, m. */
  private double liftPosition;
  /** Accepted translating velocity, m/s. */
  private double liftVelocity;
  /** Initial mechanical storage, J. */
  private double initialLiftEnergy;
  /** Accepted work, orifice, damping, numerical and impact integrals, J. */
  private double[] liftEnergy = new double[5];
  /** Accepted contact count. */
  private long liftContactCount;
  /** Last accepted contact time, s, or NaN before contact. */
  private double lastLiftContactTime = Double.NaN;
  /** Deep-owned internal histories: time, lift, velocity, work/losses, jet and swept flow. */
  private List<double[]> liftHistory = new ArrayList<>();
  /** Accepted hydraulic pump work, J; excludes shaft and thermal energy. */
  private double pumpHydraulicWork;

  // Fluid properties (cached)
  private double fluidDensity; // kg/m³
  private double fluidViscosity; // Pa·s
  private double fluidSoundSpeed; // m/s (from thermodynamics)

  // Derived quantities
  private double area; // m²
  private double segmentLength; // m
  private double characteristicImpedance; // B = c/(gA)
  private double frictionFactor; // Darcy friction factor

  // Initialization flag
  private boolean initialized = false;
  /** Revision of static geometry, material, grid and acoustic inputs. */
  private long configurationVersion;
  /** Static revision represented by the current hydraulic arrays and coefficients. */
  private long initializedConfigurationVersion;
  /** Whether wave speed is an explicit measured/user override rather than EOS-derived. */
  private boolean waveSpeedOverride;

  /**
   * Constructor for WaterHammerPipe.
   *
   * @param name Equipment name
   */
  public WaterHammerPipe(String name) {
    super(name);
  }

  /**
   * Constructor for WaterHammerPipe with inlet stream.
   *
   * @param name Equipment name
   * @param inStream Inlet stream
   */
  public WaterHammerPipe(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * Set the pipe length.
   *
   * @param length Length in meters
   */
  @Override
  public void setLength(double length) {
    super.setLength(length);
    if (this.length != length) {
      configurationVersion++;
    }
    this.length = length;
  }

  /**
   * Set the pipe length with unit.
   *
   * @param length Length value
   * @param unit Unit ("m", "km", "ft")
   */
  public void setLength(double length, String unit) {
    switch (unit.toLowerCase()) {
    case "m":
      super.setLength(length);
      if (this.length != length) {
        configurationVersion++;
      }
      this.length = length;
      break;
    case "km":
      super.setLength(length * 1000);
      if (this.length != length * 1000) {
        configurationVersion++;
      }
      this.length = length * 1000;
      break;
    case "ft":
      super.setLength(length * 0.3048);
      if (this.length != length * 0.3048) {
        configurationVersion++;
      }
      this.length = length * 0.3048;
      break;
    default:
      super.setLength(length);
      if (this.length != length) {
        configurationVersion++;
      }
      this.length = length;
    }
  }

  /**
   * Set the pipe inner diameter.
   *
   * @param diameter Diameter in meters
   */
  @Override
  public void setDiameter(double diameter) {
    super.setDiameter(diameter);
    if (this.diameter != diameter) {
      configurationVersion++;
    }
    this.diameter = diameter;
  }

  /**
   * Set the pipe inner diameter with unit.
   *
   * @param diameter Diameter value
   * @param unit Unit ("m", "mm", "in")
   */
  public void setDiameter(double diameter, String unit) {
    switch (unit.toLowerCase()) {
    case "m":
      super.setDiameter(diameter);
      if (this.diameter != diameter) {
        configurationVersion++;
      }
      this.diameter = diameter;
      break;
    case "mm":
      super.setDiameter(diameter / 1000);
      if (this.diameter != diameter / 1000) {
        configurationVersion++;
      }
      this.diameter = diameter / 1000;
      break;
    case "in":
    case "inch":
      super.setDiameter(diameter * 0.0254);
      if (this.diameter != diameter * 0.0254) {
        configurationVersion++;
      }
      this.diameter = diameter * 0.0254;
      break;
    default:
      super.setDiameter(diameter);
      if (this.diameter != diameter) {
        configurationVersion++;
      }
      this.diameter = diameter;
    }
  }

  /**
   * Set the pipe wall thickness.
   *
   * @param thickness Wall thickness in meters
   */
  @Override
  public void setWallThickness(double thickness) {
    super.setWallThickness(thickness);
    if (this.wallThickness != thickness) {
      configurationVersion++;
    }
    this.wallThickness = thickness;
  }

  /**
   * Get the pipe wall thickness used by the hydraulic transient model.
   *
   * @return wall thickness in meters
   */
  @Override
  public double getWallThickness() {
    return wallThickness;
  }

  /**
   * Set the pipe wall roughness.
   *
   * @param roughness Roughness in meters
   */
  public void setRoughness(double roughness) {
    super.setPipeWallRoughness(roughness);
    if (this.roughness != roughness) {
      configurationVersion++;
    }
    this.roughness = roughness;
  }

  /**
   * Set the pipe wall roughness using the generic pipeline property name.
   *
   * @param roughness roughness in meters, must be non-negative
   */
  @Override
  public void setPipeWallRoughness(double roughness) {
    setRoughness(roughness);
  }

  /**
   * Get the pipe wall roughness used by the transient friction calculation.
   *
   * @return pipe wall roughness in meters
   */
  @Override
  public double getPipeWallRoughness() {
    return roughness;
  }

  /**
   * Set the number of computational nodes.
   *
   * @param nodes Number of nodes (minimum 10)
   */
  public void setNumberOfNodes(int nodes) {
    if (this.numberOfNodes != Math.max(10, nodes)) {
      configurationVersion++;
    }
    this.numberOfNodes = Math.max(10, nodes);
  }

  /**
   * Sets the generic number of increments as the water-hammer node count.
   *
   * @param numberOfIncrements requested number of computational nodes, minimum 10
   */
  @Override
  public void setNumberOfIncrements(int numberOfIncrements) {
    super.setNumberOfIncrements(numberOfIncrements);
    setNumberOfNodes(numberOfIncrements);
  }

  /**
   * Gets the generic number of increments as the water-hammer node count.
   *
   * @return number of computational nodes
   */
  @Override
  public int getNumberOfIncrements() {
    return numberOfNodes;
  }

  /**
   * Set the pipe elastic modulus.
   *
   * @param modulus Elastic modulus in Pa
   */
  public void setPipeElasticModulus(double modulus) {
    if (this.pipeElasticModulus != modulus) {
      configurationVersion++;
    }
    this.pipeElasticModulus = modulus;
  }

  /**
   * Set the elevation change (outlet - inlet).
   *
   * @param elevation Elevation change in meters
   */
  public void setElevationChange(double elevation) {
    super.setElevation(elevation);
    if (this.elevationChange != elevation) {
      configurationVersion++;
    }
    this.elevationChange = elevation;
  }

  /**
   * Set the generic elevation change used by the transient model.
   *
   * @param elevation elevation change from inlet to outlet in meters
   */
  @Override
  public void setElevation(double elevation) {
    setElevationChange(elevation);
  }

  /**
   * Get the elevation change used by the transient model.
   *
   * @return elevation change from inlet to outlet in meters
   */
  @Override
  public double getElevation() {
    return elevationChange;
  }

  /**
   * Get the elevation change using the transient property name.
   *
   * @return elevation change from inlet to outlet in meters
   */
  public double getElevationChange() {
    return elevationChange;
  }

  /**
   * Set the upstream boundary condition type.
   *
   * @param type Boundary type
   */
  public void setUpstreamBoundary(BoundaryType type) {
    this.upstreamBoundary = type;
  }

  /**
   * Set the upstream boundary condition type from a JSON-friendly string.
   *
   * @param type boundary type name, one of RESERVOIR, VALVE, CLOSED_END, or CONSTANT_FLOW
   * @throws IllegalArgumentException if the boundary type is unsupported
   */
  public void setUpstreamBoundary(String type) {
    this.upstreamBoundary = parseBoundaryType(type);
  }

  /**
   * Get the upstream boundary condition type.
   *
   * @return upstream boundary condition type
   */
  public BoundaryType getUpstreamBoundary() {
    return upstreamBoundary;
  }

  /**
   * Get the upstream boundary condition name for automation and JSON reports.
   *
   * @return upstream boundary condition enum name
   */
  public String getUpstreamBoundaryName() {
    return upstreamBoundary.name();
  }

  /**
   * Set the downstream boundary condition type.
   *
   * @param type Boundary type
   */
  public void setDownstreamBoundary(BoundaryType type) {
    this.downstreamBoundary = type;
  }

  /**
   * Set the downstream boundary condition type from a JSON-friendly string.
   *
   * @param type boundary type name, one of RESERVOIR, VALVE, CLOSED_END, or CONSTANT_FLOW
   * @throws IllegalArgumentException if the boundary type is unsupported
   */
  public void setDownstreamBoundary(String type) {
    this.downstreamBoundary = parseBoundaryType(type);
  }

  /**
   * Get the downstream boundary condition type.
   *
   * @return downstream boundary condition type
   */
  public BoundaryType getDownstreamBoundary() {
    return downstreamBoundary;
  }

  /**
   * Get the downstream boundary condition name for automation and JSON reports.
   *
   * @return downstream boundary condition enum name
   */
  public String getDownstreamBoundaryName() {
    return downstreamBoundary.name();
  }

  /**
   * Parses a boundary condition name into a {@link BoundaryType}.
   *
   * @param type boundary type name, must not be null or blank
   * @return parsed boundary condition type
   * @throws IllegalArgumentException if the boundary type is missing or unsupported
   */
  private BoundaryType parseBoundaryType(String type) {
    if (type == null || type.trim().isEmpty()) {
      throw new IllegalArgumentException("Boundary type cannot be null or empty");
    }
    String normalized = type.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    return BoundaryType.valueOf(normalized);
  }

  /**
   * Set the external valve receiver pressure; default is 1 bara.
   *
   * @param pressureBara positive absolute receiver pressure in bara
   */
  public void setValveBackPressure(double pressureBara) {
    requirePositive(pressureBara, "valve receiver pressure");
    valveBackPressureBara = pressureBara;
  }

  /**
   * Get the external valve receiver pressure.
   *
   * @return absolute pressure in bara
   */
  public double getValveBackPressure() {
    return valveBackPressureBara;
  }

  /**
   * Require a finite strictly positive model input.
   *
   * @param value input value
   * @param name diagnostic name
   */
  private static void requirePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  /**
   * Set the valve opening fraction (0 = closed, 1 = fully open).
   *
   * @param opening Valve opening (0-1)
   */
  public void setValveOpening(double opening) {
    if (valveBoundary != null || liftCheckValve != null) {
      throw new IllegalStateException("Actual travel belongs to the configured acoustic valve boundary");
    }
    if (!Double.isFinite(opening)) {
      throw new IllegalArgumentException("Valve opening must be finite");
    }
    this.valveOpening = Math.max(0.0, Math.min(1.0, opening));
  }

  /**
   * Get the current valve opening.
   *
   * @return Valve opening (0-1)
   */
  public double getValveOpening() {
    return valveOpening;
  }

  /**
   * Install prescribed physical travel and absolute liquid capacity. Call run() to initialize or reinitialize.
   *
   * @param boundary immutable boundary, or null to restore calibrated linear legacy mode
   */
  public void setValveBoundary(AcousticValveBoundary boundary) {
    if (boundary != null && liftCheckValve != null) {
      throw new IllegalArgumentException("Prescribed travel and self-actuating lift are mutually exclusive");
    }
    if (valveBoundary != boundary) {
      valveBoundary = boundary;
      configurationVersion++;
      if (boundary != null) {
        valveOpening = boundary.getOpening(0.0);
      }
    }
  }

  /**
   * Get the prescribed boundary without mutable actuator or clock state.
   *
   * @return immutable prescribed boundary, or null in legacy mode
   */
  public AcousticValveBoundary getValveBoundary() {
    return valveBoundary;
  }

  /**
   * Configure an optional forward pump inlet. Changing it requires explicit reinitialization with run().
   *
   * @param boundary detached pump curve and actual-speed history, or null to remove
   */
  public void setPumpBoundary(AcousticPumpBoundary boundary) {
    if (pumpBoundary != boundary) {
      pumpBoundary = boundary;
      configurationVersion++;
    }
  }

  /**
   * Get the immutable configured pump boundary.
   *
   * @return pump boundary, or null for legacy inlet modes
   */
  public AcousticPumpBoundary getPumpBoundary() {
    return pumpBoundary;
  }

  /**
   * Get hydraulic work supplied by the pump using accepted interval fluxes and endpoint-held forcing.
   *
   * @return hydraulic work, J; not shaft work or full EOS energy closure
   */
  public double getPumpHydraulicWork() {
    return pumpHydraulicWork;
  }

  /**
   * Install an ideal translating/inelastic check valve; call run() to initialize static force/flow balance.
   *
   * @param boundary immutable declared parameters, or null to restore legacy downstream mode
   */
  public void setLiftCheckValve(AcousticLiftCheckValve boundary) {
    if (boundary != null && valveBoundary != null) {
      throw new IllegalArgumentException("Prescribed travel and self-actuating lift are mutually exclusive");
    }
    if (boundary != liftCheckValve) {
      liftCheckValve = boundary;
      configurationVersion++;
      if (boundary != null) {
        valveOpening = boundary.getInitialLift() / boundary.getMaximumLift();
      }
    }
  }

  /**
   * Get the configured translating parameters.
   *
   * @return immutable parameters, or null in other modes
   */
  public AcousticLiftCheckValve getLiftCheckValve() {
    return liftCheckValve;
  }

  /**
   * Get accepted translating position.
   *
   * @return lift, m
   */
  public double getLiftPosition() {
    return liftPosition;
  }

  /**
   * Get accepted translating velocity after any inelastic contact.
   *
   * @return velocity, m/s
   */
  public double getLiftVelocity() {
    return liftVelocity;
  }

  /**
   * Get accepted mechanical storage, zero at the stationary seat.
   *
   * @return kinetic plus spring/preload energy, J
   */
  public double getLiftMechanicalEnergy() {
    return liftCheckValve == null ? 0.0 : liftCheckValve.energy(liftPosition, liftVelocity);
  }

  /**
   * Get pressure-difference work including jet and swept flow, not full EOS/shaft work.
   *
   * @return cumulative signed pressure work, J
   */
  public double getLiftPressureWork() {
    return liftCheckValve == null ? 0.0 : liftEnergy[0];
  }

  /**
   * Get passive signed-orifice dissipation.
   *
   * @return cumulative orifice loss, J
   */
  public double getLiftOrificeLoss() {
    return liftCheckValve == null ? 0.0 : liftEnergy[1];
  }

  /**
   * Get physical viscous damping loss.
   *
   * @return cumulative damping loss, J
   */
  public double getLiftDampingLoss() {
    return liftCheckValve == null ? 0.0 : liftEnergy[2];
  }

  /**
   * Get explicit backward-Euler algorithmic loss, separate from physical damping.
   *
   * @return cumulative numerical loss, J
   */
  public double getLiftNumericalLoss() {
    return liftCheckValve == null ? 0.0 : liftEnergy[3];
  }

  /**
   * Get perfectly inelastic kinetic-energy loss at seat and upper stop.
   *
   * @return cumulative impact loss, J
   */
  public double getLiftImpactLoss() {
    return liftCheckValve == null ? 0.0 : liftEnergy[4];
  }

  /**
   * Get pressure work less orifice loss, storage change and all declared losses.
   *
   * @return discrete boundary/mechanical energy residual, J, not full pipe EOS energy
   */
  public double getLiftEnergyResidual() {
    if (liftCheckValve == null) {
      return 0.0;
    }
    return liftEnergy[0] - liftEnergy[1] - (getLiftMechanicalEnergy() - initialLiftEnergy) - liftEnergy[2]
        - liftEnergy[3] - liftEnergy[4];
  }

  /**
   * Get the accepted perfectly inelastic contact count.
   *
   * @return seat and upper-stop contacts
   */
  public long getLiftContactCount() {
    return liftContactCount;
  }

  /**
   * Get the last accepted discrete contact time.
   *
   * @return time, s, or NaN before any contact
   */
  public double getLastLiftContactTime() {
    return liftCheckValve == null ? Double.NaN : lastLiftContactTime;
  }

  /**
   * Get defensive internal lift histories synchronized with accepted pipe intervals. Columns are time_s, lift_m,
   * velocity_m_s, pressureWork_J, orificeLoss_J, dampingLoss_J, numericalLoss_J, impactLoss_J, jetFlow_m3_s,
   * sweptFlow_m3_s, pressureDifference_Pa, contactEvent (one for contact, zero otherwise). Contact/restart can add
   * samples between external requests.
   *
   * @return deep copy of accepted rows, excluding initialization
   */
  public List<double[]> getLiftHistory() {
    List<double[]> copy = new ArrayList<>();
    if (liftHistory != null) {
      for (double[] row : liftHistory) {
        copy.add(row.clone());
      }
    }
    return copy;
  }

  /**
   * Advance a complete request with Courant limits, existing pump knots and localized discrete lift contact.
   *
   * @param requestedEnd exact requested physical endpoint, s
   */
  private void advanceLiftRequest(double requestedEnd) {
    int intervals = 0;
    while (currentTime < requestedEnd) {
      if (++intervals > 1000000) {
        throw new IllegalStateException("Lift interval count exceeds bounded acoustic substepping");
      }
      double end = Math.min(requestedEnd, currentTime + getMaxStableTimeStep());
      if (pumpBoundary != null) {
        end = Math.min(end, pumpBoundary.nextKnotAfter(currentTime));
      }
      double drive = fluidDensity * GRAVITY
          * cellCharacteristic(cellHead, cellFlow, cellHead.length - 1, numberOfNodes - 1, 1)
          - valveBackPressureBara * 1e5;
      try {
        AcousticLiftCheckValve.Step tentative = liftCheckValve.step(drive,
            fluidDensity * GRAVITY * characteristicImpedance, fluidDensity, liftPosition, liftVelocity,
            end - currentTime);
        if (tentative.duration < end - currentTime) {
          end = currentTime + tentative.duration;
        }
        if (end <= currentTime) {
          throw new IllegalStateException("Lift contact cannot advance representable physical time");
        }
        advanceAcoustics(tentative.duration, end, tentative);
      } catch (IllegalArgumentException | IllegalStateException failure) {
        throw new IllegalStateException(
            "Acoustic lift interval [" + currentTime + ", " + end + "] s not accepted: " + failure.getMessage(),
            failure);
      }
      currentTime = end;
    }
  }

  /**
   * Evaluate a compatible instantaneous lift trace without changing the disc state.
   *
   * @param cp incoming pressure-head characteristic, m
   * @param position accepted position, m
   * @param velocity accepted velocity, m/s
   * @param heads destination face heads, m
   * @param flows destination face flows, m3/s
   */
  private void applyLiftTrace(double cp, double position, double velocity, double[] heads, double[] flows) {
    double receiver = valveBackPressureBara * 1e5;
    double pressure = liftCheckValve.tracePressure(fluidDensity * GRAVITY * cp - receiver,
        fluidDensity * GRAVITY * characteristicImpedance, fluidDensity, position, velocity);
    int last = numberOfNodes - 1;
    heads[last] = (receiver + pressure) / (fluidDensity * GRAVITY);
    flows[last] = liftCheckValve.jetFlow(position, pressure, fluidDensity) + liftCheckValve.sweptFlow(velocity);
  }

  /**
   * Validate candidate boundary energy/work before committing any hydraulic or disc state.
   *
   * @param step tentative physical interval
   * @return candidate cumulative work/loss integrals, J
   */
  private double[] tentativeLiftEnergy(AcousticLiftCheckValve.Step step) {
    double work = step.duration * step.pressure * (step.jetFlow + step.sweptFlow);
    double orifice = step.duration * step.pressure * step.jetFlow;
    double[] increments = {work, orifice, step.dampingLoss, step.numericalLoss, step.impactLoss};
    double[] candidate = new double[5];
    for (int i = 0; i < candidate.length; i++) {
      candidate[i] = liftEnergy[i] + increments[i];
      if (!Double.isFinite(candidate[i]) || (i > 0 && increments[i] < 0.0)) {
        throw new IllegalStateException("Invalid lift work/loss; interval not accepted");
      }
    }
    double change = liftCheckValve.energy(step.lift, step.velocity) - getLiftMechanicalEnergy();
    double residual = work - orifice - change - step.dampingLoss - step.numericalLoss - step.impactLoss;
    double scale = Math.abs(work) + Math.abs(orifice) + Math.abs(change) + step.dampingLoss + step.numericalLoss
        + step.impactLoss;
    if (!Double.isFinite(residual) || Math.abs(residual) > 1e-10 + 1e-12 * scale) {
      throw new IllegalStateException("Lift discrete work identity unresolved; interval not accepted");
    }
    return candidate;
  }

  /**
   * Get position-adjusted capacity in physical mode.
   *
   * @return metric Kv at current physical time, or NaN in calibrated legacy mode
   */
  public double getEffectiveValveKv() {
    return valveBoundary == null ? Double.NaN : valveBoundary.getKv(currentTime);
  }

  /**
   * Set the valve opening as percent open.
   *
   * @param percentOpening valve opening in percent, where 0 is closed and 100 is fully open
   */
  public void setValveOpeningPercent(double percentOpening) {
    setValveOpening(percentOpening / 100.0);
  }

  /**
   * Get the valve opening as percent open.
   *
   * @return valve opening in percent
   */
  public double getValveOpeningPercent() {
    return valveOpening * 100.0;
  }

  /**
   * Set the Courant number for time step control.
   *
   * @param cn Courant number (typically 1.0 for stability)
   */
  public void setCourantNumber(double cn) {
    requirePositive(cn, "Courant number");
    if (cn > 1.0) {
      throw new IllegalArgumentException("Courant number cannot exceed one");
    }
    this.courantNumber = cn;
  }

  /**
   * Get the Courant number used for stability guidance.
   *
   * @return Courant number
   */
  public double getCourantNumber() {
    return courantNumber;
  }

  /**
   * Override the automatically calculated wave speed.
   *
   * Static edits require an explicit {@link #run(UUID)} before further transient execution.
   *
   * @param speed finite positive wave speed in m/s, or -1 for EOS/elasticity calculation
   */
  public void setWaveSpeed(double speed) {
    if (speed != -1.0) {
      requirePositive(speed, "wave speed override");
    }
    waveSpeedOverride = speed > 0.0;
    if (this.waveSpeed != speed) {
      configurationVersion++;
    }
    this.waveSpeed = speed;
  }

  /**
   * Calculate the effective wave speed including pipe elasticity (Korteweg formula).
   *
   * @return Effective wave speed in m/s
   */
  public double calcEffectiveWaveSpeed() {
    if (fluidDensity <= 0 || fluidSoundSpeed <= 0) {
      throw new IllegalStateException("Fluid acoustic properties are unavailable");
    }

    // Fluid bulk modulus: K = rho * c^2
    double fluidBulkModulus = fluidDensity * fluidSoundSpeed * fluidSoundSpeed;

    // Korteweg-Joukowsky formula for wave speed in elastic pipe
    // c = c_fluid / sqrt(1 + K*D / (E*e))
    // where E = pipe modulus, e = wall thickness
    double alpha = (fluidBulkModulus * diameter) / (pipeElasticModulus * wallThickness);
    double effectiveSpeed = fluidSoundSpeed / Math.sqrt(1 + alpha);

    return effectiveSpeed;
  }

  /**
   * Calculate Joukowsky pressure surge for instantaneous velocity change.
   *
   * @param velocityChange Change in velocity (m/s)
   * @return Pressure surge (Pa)
   */
  public double calcJoukowskyPressureSurge(double velocityChange) {
    return fluidDensity * waveSpeed * Math.abs(velocityChange);
  }

  /**
   * Calculate Joukowsky pressure surge for instantaneous velocity change.
   *
   * @param velocityChange Change in velocity (m/s)
   * @param unit Output unit ("Pa", "bar", "psi")
   * @return Pressure surge in specified unit
   */
  public double calcJoukowskyPressureSurge(double velocityChange, String unit) {
    double surgePa = calcJoukowskyPressureSurge(velocityChange);
    switch (unit.toLowerCase()) {
    case "bar":
      return surgePa / 1e5;
    case "psi":
      return surgePa / 6894.76;
    default:
      return surgePa;
    }
  }

  /**
   * Calculate the maximum stable time step based on Courant condition.
   *
   * @return Maximum stable time step in seconds
   */
  public double getMaxStableTimeStep() {
    if (waveSpeed <= 0 || segmentLength <= 0) {
      return 0.001;
    }
    return courantNumber * segmentLength / waveSpeed;
  }

  /**
   * Calculate friction factor using Haaland equation.
   *
   * @param reynoldsNumber Reynolds number
   * @return Darcy friction factor
   */
  private double calcFrictionFactor(double reynoldsNumber) {
    if (reynoldsNumber == 0.0) {
      return 0.0;
    }
    if (reynoldsNumber < 2300) {
      // Laminar flow
      return 64.0 / reynoldsNumber;
    } else {
      // Turbulent - Haaland equation
      double relRoughness = roughness / diameter;
      double term1 = relRoughness / 3.7;
      double term2 = 6.9 / reynoldsNumber;
      double denom = -1.8 * Math.log10(Math.pow(term1, 1.11) + term2);
      return 1.0 / (denom * denom);
    }
  }

  /**
   * Initialize the simulation arrays and calculate initial conditions.
   */
  private void initialize() {
    requirePositive(length, "length");
    requirePositive(diameter, "diameter");
    requirePositive(wallThickness, "wall thickness");
    requirePositive(pipeElasticModulus, "elastic modulus");
    if (!Double.isFinite(roughness) || roughness < 0.0 || !Double.isFinite(elevationChange)) {
      throw new IllegalArgumentException("Invalid roughness or elevation");
    }
    if (!Double.isFinite(courantNumber) || courantNumber <= 0.0 || courantNumber > 1.0) {
      throw new IllegalArgumentException("Courant number must be in (0, 1]");
    }
    // Calculate area and segment length
    area = Math.PI * diameter * diameter / 4.0;
    segmentLength = length / (numberOfNodes - 1);
    requirePositive(area, "pipe area");
    requirePositive(segmentLength, "cell length");

    // Initialize arrays
    head = new double[numberOfNodes];
    flow = new double[numberOfNodes];
    cellHead = new double[numberOfNodes - 1];
    cellFlow = new double[numberOfNodes - 1];
    referenceHead = new double[numberOfNodes];
    trialHead = new double[numberOfNodes - 1];
    trialFlow = new double[numberOfNodes - 1];
    faceHead = new double[numberOfNodes];
    faceFlow = new double[numberOfNodes];
    traceHead = new double[numberOfNodes];
    traceFlow = new double[numberOfNodes];
    inletVolume = 0.0;
    pumpHydraulicWork = 0.0;
    outletVolume = 0.0;
    pressureImpulse = 0.0;
    gravityImpulse = 0.0;
    frictionImpulse = 0.0;
    pressureProfile = new double[numberOfNodes];
    velocityProfile = new double[numberOfNodes];
    maxPressureEnvelope = new double[numberOfNodes];
    minPressureEnvelope = new double[numberOfNodes];
    pressureHistory = new ArrayList<>();
    timeHistory = new ArrayList<>();
    liftPosition = liftCheckValve == null ? 0.0 : liftCheckValve.getInitialLift();
    liftVelocity = 0.0;
    initialLiftEnergy = liftCheckValve == null ? 0.0 : liftCheckValve.energy(liftPosition, 0.0);
    liftEnergy = new double[5];
    liftHistory = new ArrayList<>();
    liftContactCount = 0L;
    lastLiftContactTime = Double.NaN;

    // Get fluid properties from inlet stream
    SystemInterface system = inStream.getThermoSystem().clone();
    system.initProperties();
    if (system.getNumberOfPhases() != 1) {
      throw new IllegalArgumentException("WaterHammerPipe requires a single-phase inlet");
    }
    if ((valveBoundary != null || pumpBoundary != null || liftCheckValve != null)
        && !neqsim.thermo.phase.StateOfMatter.isLiquid(system.getPhase(0).getType())) {
      throw new IllegalArgumentException("Physical acoustic boundary requires a single liquid phase");
    }

    fluidDensity = system.getDensity("kg/m3");
    fluidViscosity = system.getViscosity("kg/msec");

    requirePositive(fluidDensity, "fluid density");
    requirePositive(fluidViscosity, "fluid viscosity");
    fluidSoundSpeed = system.getPhase(0).getSoundSpeed();
    requirePositive(fluidSoundSpeed, "fluid sound speed");

    // Calculate effective wave speed (with pipe elasticity)
    if (!waveSpeedOverride) {
      waveSpeed = calcEffectiveWaveSpeed();
    }

    requirePositive(waveSpeed, "wave speed");

    // Characteristic impedance
    characteristicImpedance = waveSpeed / (GRAVITY * area);

    // Calculate initial steady-state conditions
    double inletPressure = inStream.getPressure() * 1e5; // Pa
    double massFlow = inStream.getFlowRate("kg/sec");
    requirePositive(inletPressure, "absolute inlet pressure");
    if (!Double.isFinite(massFlow)) {
      throw new IllegalArgumentException("Initial acoustic mass flow must be finite");
    }
    double volumeFlow = massFlow / fluidDensity; // m³/s
    double velocity = volumeFlow / area;
    if (!Double.isFinite(volumeFlow) || !Double.isFinite(velocity)) {
      throw new IllegalArgumentException("Initial acoustic actual flow and velocity must be finite");
    }

    // Calculate Reynolds number and friction factor
    double reynoldsNumber = fluidDensity * Math.abs(velocity) * diameter / fluidViscosity;
    frictionFactor = calcFrictionFactor(reynoldsNumber);

    // Calculate steady-state head and pressure profile
    upstreamHead = inletPressure / (fluidDensity * GRAVITY);
    validatePumpMode();
    if (pumpBoundary != null) {
      double pumpHead = pumpBoundary.getHead(volumeFlow, 0.0);
      double suppliedHead = pumpBoundary.getSuctionPressure() / (fluidDensity * GRAVITY) + pumpHead;
      if (Math.abs(suppliedHead - upstreamHead) > 1.0e-8) {
        throw new IllegalArgumentException(
            "Initial feed pressure/flow is inconsistent with declared pump curve/suction");
      }
    }

    // Friction head loss per unit length
    double headLossPerMeter = frictionFactor * velocity * Math.abs(velocity) / (2 * GRAVITY * diameter);

    // Elevation head change per unit length
    double elevationPerMeter = elevationChange / length;

    for (int i = 0; i < numberOfNodes; i++) {
      double x = i * segmentLength;
      head[i] = upstreamHead - headLossPerMeter * x - elevationPerMeter * x;
      if (!Double.isFinite(head[i]) || head[i] <= 0.0) {
        throw new IllegalArgumentException(
            "Initial absolute acoustic pressure must be positive and finite along the line");
      }
      flow[i] = volumeFlow;
      referenceHead[i] = head[i];

      pressureProfile[i] = head[i] * fluidDensity * GRAVITY;
      velocityProfile[i] = velocity;
      maxPressureEnvelope[i] = pressureProfile[i];
      minPressureEnvelope[i] = pressureProfile[i];
    }
    referenceFlow = volumeFlow;
    for (int i = 0; i < numberOfNodes - 1; i++) {
      cellHead[i] = 0.5 * (head[i] + head[i + 1]);
      cellFlow[i] = volumeFlow;
    }

    pressureDrop = (pressureProfile[0] - pressureProfile[numberOfNodes - 1]) / 1e5;

    // Store downstream conditions for valve BC
    downstreamHead = head[numberOfNodes - 1];

    // Calibrate the hydraulic coefficient against the actual external receiver.
    if ((valveBoundary != null || liftCheckValve != null) && downstreamBoundary != BoundaryType.VALVE) {
      throw new IllegalArgumentException("Configured physical acoustic valve requires downstream boundary VALVE");
    }
    if (downstreamBoundary == BoundaryType.VALVE) {
      if (valveBoundary != null) {
        valveOpening = valveBoundary.getOpening(0.0);
      }
      double deltaH = downstreamHead - valveBackPressureBara * 1.0e5 / (fluidDensity * GRAVITY);
      if (volumeFlow <= 0.0 || deltaH <= 0.0) {
        throw new IllegalArgumentException("Initialize a forward-flow open valve above receiver pressure");
      }
      if (liftCheckValve != null) {
        liftCheckValve.validateInitial(deltaH * fluidDensity * GRAVITY, volumeFlow, fluidDensity);
        valveOpening = liftPosition / liftCheckValve.getMaximumLift();
      } else if (valveBoundary == null) {
        if (valveOpening <= 0.0) {
          throw new IllegalArgumentException("Initialize an open legacy valve");
        }
        valveCv = volumeFlow / (valveOpening * Math.sqrt(deltaH));
      } else {
        double predicted = hydraulicCoefficient(valveBoundary.getKv(0.0)) * Math.sqrt(deltaH);
        if (!Double.isFinite(predicted) || Math.abs(predicted - volumeFlow) > 1.0e-9 + 1.0e-6 * Math.abs(volumeFlow)) {
          throw new IllegalArgumentException("Initial flow/pressure is inconsistent with declared valve capacity; "
              + "provide a consistent feed, receiver pressure and actual initial position");
        }
      }
    }

    currentTime = 0.0;
    initializedConfigurationVersion = configurationVersion;
    initialized = true;

    logger.info("WaterHammerPipe initialized: wave speed = " + waveSpeed + " m/s, " + "max time step = "
        + getMaxStableTimeStep() + " s");
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Initialize and run steady-state
    initialize();

    // Update outlet stream
    updateOutletStream();

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    requirePositive(dt, "timestep");
    validatePumpMode();
    if ((valveBoundary != null || liftCheckValve != null) && downstreamBoundary != BoundaryType.VALVE) {
      throw new IllegalStateException("Configured physical acoustic valve requires downstream boundary VALVE");
    }
    if (!initialized) {
      initialize();
    } else if (configurationVersion != initializedConfigurationVersion) {
      throw new IllegalStateException("Static acoustic configuration changed; call run() explicitly to reinitialize");
    }
    if (cellHead == null) {
      throw new IllegalStateException("Nodal acoustic checkpoint has no cell inventory; call run() to reinitialize");
    }
    double requestedEnd = currentTime + dt;
    double count = Math.ceil(dt / getMaxStableTimeStep());
    if (!Double.isFinite(requestedEnd) || requestedEnd <= currentTime || count > 1000000.0) {
      throw new IllegalArgumentException("Requested interval exceeds bounded acoustic substepping");
    }
    if (liftCheckValve != null) {
      advanceLiftRequest(requestedEnd);
      updateOutletStream();
      setCalculationIdentifier(id);
      return;
    }
    while (currentTime < requestedEnd) {
      boolean prescribed = valveBoundary != null || pumpBoundary != null;
      double end = valveBoundary == null ? requestedEnd
          : Math.min(requestedEnd, valveBoundary.nextKnotAfter(currentTime));
      if (pumpBoundary != null) {
        end = Math.min(end, pumpBoundary.nextKnotAfter(currentTime));
      }
      double interval = prescribed ? end - currentTime : dt;
      int steps = Math.max(1, (int) (prescribed ? Math.ceil(interval / getMaxStableTimeStep()) : count));
      double start = currentTime;
      for (int substep = 1; substep <= steps; substep++) {
        double endpoint = substep == steps ? end : start + interval * substep / steps;
        try {
          advanceAcoustics(prescribed ? endpoint - currentTime : interval / steps, endpoint);
        } catch (IllegalStateException exception) {
          throw new IllegalStateException(
              "Acoustic interval [" + currentTime + ", " + endpoint + "] s not accepted: " + exception.getMessage(),
              exception);
        }
        currentTime = endpoint;
      }
    }
    currentTime = requestedEnd;
    updateOutletStream();
    setCalculationIdentifier(id);
  }

  /**
   * Advance one Courant-limited characteristic interval, preserving its physical duration.
   *
   * @param dt internal timestep in seconds
   * @param endpoint exact physical endpoint for prescribed boundary evaluation, seconds
   */
  private void advanceAcoustics(double dt, double endpoint) {
    advanceAcoustics(dt, endpoint, null);
  }

  /**
   * Advance cells and an optional tentative lift state as one locally accepted physical interval.
   *
   * @param dt accepted interval duration, s
   * @param endpoint accepted endpoint, s
   * @param lift tentative translating state, or null for legacy modes
   */
  private void advanceAcoustics(double dt, double endpoint, AcousticLiftCheckValve.Step lift) {
    // Each shared Riemann trace is the flux for both adjoining physical cells.
    acousticFaces(cellHead, cellFlow, endpoint, faceHead, faceFlow);
    if (lift != null) {
      int last = numberOfNodes - 1;
      faceHead[last] = (valveBackPressureBara * 1e5 + lift.pressure) / (fluidDensity * GRAVITY);
      faceFlow[last] = lift.jetFlow + lift.sweptFlow;
      if (!Double.isFinite(faceHead[last]) || faceHead[last] <= 0.0) {
        throw new IllegalStateException("Invalid lift interval pressure; interval not accepted");
      }
    }
    double source = dt * frictionFactor / (2.0 * diameter * area);
    double signedReference = referenceFlow * Math.abs(referenceFlow);
    double frictionSum = 0.0;
    for (int i = 0; i < numberOfNodes - 1; i++) {
      trialHead[i] = cellHead[i]
          - waveSpeed * waveSpeed * dt / (GRAVITY * area * segmentLength) * (faceFlow[i + 1] - faceFlow[i]);
      // Subtract the initialized linear gradient analytically, including terrain.
      double deltaHead = (faceHead[i + 1] - referenceHead[i + 1]) - (faceHead[i] - referenceHead[i]);
      double forced = cellFlow[i] - GRAVITY * area * dt / segmentLength * deltaHead + source * signedReference;
      double rootArgument = 1.0 + 4.0 * source * Math.abs(forced);
      if (!Double.isFinite(forced) || !Double.isFinite(rootArgument)) {
        throw new IllegalStateException("Nonfinite acoustic momentum forcing; interval not accepted");
      }
      // Signed backward Darcy solve q + source*q*abs(q) = forced; no clipping.
      trialFlow[i] = forced / (0.5 + 0.5 * Math.sqrt(rootArgument));
      if (!Double.isFinite(trialHead[i]) || trialHead[i] <= 0.0 || !Double.isFinite(trialFlow[i])) {
        throw new IllegalStateException("Invalid acoustic cell state; interval not accepted");
      }
      frictionSum += trialFlow[i] * Math.abs(trialFlow[i]);
    }
    // Validate the new endpoint before committing any state, inventory or history.
    acousticFaces(trialHead, trialFlow, endpoint, traceHead, traceFlow);
    if (lift != null) {
      applyLiftTrace(cellCharacteristic(trialHead, trialFlow, trialHead.length - 1, numberOfNodes - 1, 1), lift.lift,
          lift.velocity, traceHead, traceFlow);
    }
    for (int i = 0; i < numberOfNodes; i++) {
      if (!Double.isFinite(traceHead[i]) || traceHead[i] <= 0.0 || !Double.isFinite(traceFlow[i])) {
        throw new IllegalStateException("Invalid acoustic endpoint trace; interval not accepted");
      }
    }
    double pumpWork = pumpBoundary == null ? 0.0
        : dt * fluidDensity * GRAVITY * faceFlow[0] * pumpBoundary.getHead(faceFlow[0], endpoint);
    if (!Double.isFinite(pumpWork) || !Double.isFinite(pumpHydraulicWork + pumpWork)) {
      throw new IllegalStateException("Nonfinite pump hydraulic work; interval not accepted");
    }
    double[] nextLiftEnergy = lift == null ? null : tentativeLiftEnergy(lift);
    System.arraycopy(trialHead, 0, cellHead, 0, cellHead.length);
    System.arraycopy(trialFlow, 0, cellFlow, 0, cellFlow.length);
    System.arraycopy(traceHead, 0, head, 0, numberOfNodes);
    System.arraycopy(traceFlow, 0, flow, 0, numberOfNodes);
    inletVolume += dt * faceFlow[0];
    pumpHydraulicWork += pumpWork;
    outletVolume += dt * faceFlow[numberOfNodes - 1];
    pressureImpulse += dt * fluidDensity * GRAVITY * area * (faceHead[0] - faceHead[numberOfNodes - 1]);
    gravityImpulse -= dt * fluidDensity * GRAVITY * area * elevationChange;
    frictionImpulse -= dt * fluidDensity * frictionFactor * segmentLength / (2.0 * diameter * area) * frictionSum;
    if (lift != null) {
      liftPosition = lift.lift;
      liftVelocity = lift.velocity;
      liftEnergy = nextLiftEnergy;
      valveOpening = liftPosition / liftCheckValve.getMaximumLift();
      if (lift.contact) {
        liftContactCount++;
        lastLiftContactTime = endpoint;
      }
      liftHistory.add(new double[] {endpoint, liftPosition, liftVelocity, liftEnergy[0], liftEnergy[1], liftEnergy[2],
          liftEnergy[3], liftEnergy[4], lift.jetFlow, lift.sweptFlow, lift.pressure, lift.contact ? 1.0 : 0.0});
    } else if (valveBoundary != null) {
      valveOpening = valveBoundary.getOpening(endpoint);
    }

    // Update pressure and velocity profiles
    for (int i = 0; i < numberOfNodes; i++) {
      pressureProfile[i] = head[i] * fluidDensity * GRAVITY;
      velocityProfile[i] = flow[i] / area;

      // Update envelopes
      maxPressureEnvelope[i] = Math.max(maxPressureEnvelope[i], pressureProfile[i]);
      minPressureEnvelope[i] = Math.min(minPressureEnvelope[i], pressureProfile[i]);
    }

    // Store history
    pressureHistory.add(pressureProfile[numberOfNodes - 1]);
    timeHistory.add(endpoint);

    pressureDrop = (pressureProfile[0] - pressureProfile[numberOfNodes - 1]) / 1e5;

  }

  /**
   * Solve shared face traces from the two cell-mean acoustic characteristics. Perturbations about the initialized
   * linear steady state make the pressure-gradient, terrain and Darcy sources exactly balanced at equilibrium.
   *
   * @param h cell mean pressure heads in metres
   * @param q cell mean actual flows in m3/s
   * @param endpoint boundary forcing time in seconds, held over the internal interval
   * @param heads destination face pressure heads
   * @param flows destination face actual flows
   */
  private void acousticFaces(double[] h, double[] q, double endpoint, double[] heads, double[] flows) {
    for (int i = 1; i < numberOfNodes - 1; i++) {
      double cp = cellCharacteristic(h, q, i - 1, i, 1);
      double cm = cellCharacteristic(h, q, i, i, -1);
      heads[i] = 0.5 * (cp + cm);
      flows[i] = (cp - cm) / (2.0 * characteristicImpedance);
    }
    applyUpstreamBC(cellCharacteristic(h, q, 0, 0, -1), endpoint, heads, flows);
    applyDownstreamBC(cellCharacteristic(h, q, h.length - 1, numberOfNodes - 1, 1), endpoint, heads, flows);
  }

  /**
   * Evaluate an incoming characteristic perturbation at a physical face.
   *
   * @param h mean pressure heads
   * @param q mean actual flows
   * @param cell donating cell
   * @param face destination spatial node
   * @param sign plus one for C-plus, minus one for C-minus
   * @return pressure-head characteristic in metres
   */
  private double cellCharacteristic(double[] h, double[] q, int cell, int face, int sign) {
    double reference = 0.5 * (referenceHead[cell] + referenceHead[cell + 1]);
    return referenceHead[face] + h[cell] - reference + sign * characteristicImpedance * q[cell];
  }

  /**
   * Apply the upstream physical boundary.
   *
   * @param cm incoming C-minus pressure-head characteristic
   * @param endpoint physical boundary evaluation time, seconds
   * @param heads destination pressure-head traces
   * @param flows destination actual flow traces
   */
  private void applyUpstreamBC(double cm, double endpoint, double[] heads, double[] flows) {
    if (upstreamBoundary == BoundaryType.PUMP) {
      flows[0] = pumpBoundary.solveFlow(cm, characteristicImpedance, fluidDensity, endpoint);
      heads[0] = cm + characteristicImpedance * flows[0];
    } else if (upstreamBoundary == BoundaryType.RESERVOIR) {
      heads[0] = upstreamHead;
      flows[0] = (heads[0] - cm) / characteristicImpedance;
    } else if (upstreamBoundary == BoundaryType.CLOSED_END || upstreamBoundary == BoundaryType.CONSTANT_FLOW) {
      flows[0] = upstreamBoundary == BoundaryType.CLOSED_END ? 0.0 : flow[0];
      heads[0] = cm + characteristicImpedance * flows[0];
    } else {
      throw new IllegalArgumentException("Upstream valve boundary is unsupported");
    }
  }

  /**
   * Reject inconsistent optional pump configuration before transient state changes.
   */
  private void validatePumpMode() {
    if ((pumpBoundary != null) != (upstreamBoundary == BoundaryType.PUMP)) {
      throw new IllegalStateException("Pump boundary data requires upstream PUMP, and PUMP requires boundary data");
    }
    if (downstreamBoundary == BoundaryType.PUMP) {
      throw new IllegalArgumentException("Pump acoustic boundary is upstream only");
    }
  }

  /**
   * Apply the downstream physical boundary and forward-only valve equation.
   *
   * @param cp incoming C-plus pressure-head characteristic
   * @param endpoint exact physical endpoint in seconds
   * @param heads destination pressure-head traces
   * @param flows destination actual flow traces
   */
  private void applyDownstreamBC(double cp, double endpoint, double[] heads, double[] flows) {
    int i = numberOfNodes - 1;
    switch (downstreamBoundary) {
    case RESERVOIR:
      heads[i] = downstreamHead;
      flows[i] = (cp - heads[i]) / characteristicImpedance;
      break;
    case CONSTANT_FLOW:
    case CLOSED_END:
      flows[i] = downstreamBoundary == BoundaryType.CLOSED_END ? 0.0 : flow[i];
      heads[i] = cp - characteristicImpedance * flows[i];
      break;
    case VALVE:
      if (liftCheckValve != null) {
        applyLiftTrace(cp, liftPosition, liftVelocity, heads, flows);
        break;
      }
      if (valveBoundary == null && valveCv < 0.0) {
        throw new IllegalStateException("Initialize the valve boundary before transient execution");
      }
      double receiverHead = valveBackPressureBara * 1.0e5 / (fluidDensity * GRAVITY);
      double coefficient = valveBoundary == null ? valveCv * valveOpening
          : hydraulicCoefficient(valveBoundary.getKv(endpoint));
      if (coefficient > 0.0 && cp < receiverHead) {
        throw new IllegalStateException("Valve reverse flow requires a signed receiver model");
      }
      double c2 = coefficient * coefficient;
      // Q^2 + B*C^2*Q - C^2*(Cp-Hreceiver) = 0, rationalized positive root.
      double linear = characteristicImpedance * c2;
      double forcing = c2 * (cp - receiverHead);
      if (!Double.isFinite(linear) || !Double.isFinite(forcing)) {
        throw new IllegalStateException("Nonfinite acoustic valve coefficient or characteristic");
      }
      flows[i] = forcing == 0.0 ? 0.0 : forcing / (0.5 * linear + 0.5 * Math.hypot(linear, 2.0 * Math.sqrt(forcing)));
      heads[i] = cp - characteristicImpedance * flows[i];
      break;
    default:
      throw new IllegalArgumentException("Unsupported downstream boundary");
    }
  }

  /**
   * Convert metric Kv to the square-root-pressure-head coefficient. Reference water density is 1000 kg/m3.
   *
   * @param kv metric capacity including actual position
   * @return coefficient in m3/s per square root metre
   */
  private static double hydraulicCoefficient(double kv) {
    return kv / 3600.0 * Math.sqrt(GRAVITY / 100.0);
  }

  /**
   * Update outlet stream properties.
   */
  private void updateOutletStream() {
    SystemInterface outSystem = inStream.getThermoSystem().clone();

    // Set outlet pressure
    double outletPressureBar = pressureProfile[numberOfNodes - 1] / 1e5;
    outSystem.setPressure(outletPressureBar);

    // Set outlet flow rate
    double outletMassFlow = flow[numberOfNodes - 1] * fluidDensity;
    outSystem.setTotalFlowRate(outletMassFlow, "kg/sec");

    ThermodynamicOperations ops = new ThermodynamicOperations(outSystem);
    ops.TPflash();

    outStream.setThermoSystem(outSystem);
  }

  /**
   * Get the pressure means over the N-1 physical cells. These, rather than face traces, carry acoustic inventory.
   *
   * @return defensive cell mean absolute pressures in Pa
   */
  public double[] getCellPressureProfile() {
    double[] values = cellHead.clone();
    for (int i = 0; i < values.length; i++) {
      values[i] *= fluidDensity * GRAVITY;
    }
    return values;
  }

  /**
   * Get the actual flow means over physical cells.
   *
   * @return defensive cell mean flows in m3/s, positive along the route
   */
  public double[] getCellFlowProfile() {
    return cellFlow.clone();
  }

  /**
   * Get the physical cell centres associated with the mean pressure and flow arrays.
   *
   * @return distances from the inlet in metres
   */
  public double[] getCellPositions() {
    double[] values = new double[numberOfNodes - 1];
    for (int i = 0; i < values.length; i++) {
      values[i] = (i + 0.5) * segmentLength;
    }
    return values;
  }

  /**
   * Get linear acoustic storage relative to initialization by independent summation of cell mean heads. Pipe elasticity
   * is included through the declared effective wave speed; this is not a full EOS mass ledger.
   *
   * @return actual equivalent stored volume change in m3
   */
  public double getAcousticStoredVolume() {
    double sum = 0.0;
    for (int i = 0; i < cellHead.length; i++) {
      sum += cellHead[i] - 0.5 * (referenceHead[i] + referenceHead[i + 1]);
    }
    return area * GRAVITY * segmentLength / (waveSpeed * waveSpeed) * sum;
  }

  /**
   * Get integrated inlet exchange from accepted physical boundary fluxes, including hidden substeps.
   *
   * @return signed actual volume in m3, positive into the line
   */
  public double getIntegratedInletVolume() {
    return inletVolume;
  }

  /**
   * Get integrated outlet exchange from accepted physical boundary fluxes, including hidden substeps.
   *
   * @return signed actual volume in m3, positive out of the line
   */
  public double getIntegratedOutletVolume() {
    return outletVolume;
  }

  /**
   * Get the independently summed acoustic storage less signed net boundary exchange.
   *
   * @return volume balance residual in m3
   */
  public double getAcousticVolumeResidual() {
    return getAcousticStoredVolume() - (inletVolume - outletVolume);
  }

  /**
   * Get signed line momentum change by summing cell flow means with frozen density.
   *
   * @return momentum inventory change in kg m/s
   */
  public double getMomentumChange() {
    double sum = 0.0;
    for (double q : cellFlow) {
      sum += q - referenceFlow;
    }
    return fluidDensity * segmentLength * sum;
  }

  /**
   * Get the physical pressure force impulse, using the actual flux pressure traces.
   *
   * @return pressure impulse in kg m/s, positive along the route
   */
  public double getIntegratedPressureImpulse() {
    return pressureImpulse;
  }

  /**
   * Get the terrain gravity impulse.
   *
   * @return gravity impulse in kg m/s, positive along the route
   */
  public double getIntegratedGravityImpulse() {
    return gravityImpulse;
  }

  /**
   * Get signed Darcy resistance impulse using the implicit accepted cell flows.
   *
   * @return friction impulse in kg m/s, positive along the route
   */
  public double getIntegratedFrictionImpulse() {
    return frictionImpulse;
  }

  /**
   * Get momentum change less accumulated pressure, terrain and friction impulses.
   *
   * @return momentum balance residual in kg m/s
   */
  public double getMomentumResidual() {
    return getMomentumChange() - pressureImpulse - gravityImpulse - frictionImpulse;
  }

  /**
   * Get the pressure profile along the pipe.
   *
   * @return absolute face pressure traces in Pa at x=i*length/(N-1), not cell averages
   */
  @Override
  public double[] getPressureProfile() {
    return pressureProfile.clone();
  }

  /**
   * Get the pressure profile along the pipe in specified unit.
   *
   * @param unit Unit ("Pa", "bar", "psi")
   * @return Pressure profile
   */
  public double[] getPressureProfile(String unit) {
    double[] profile = new double[numberOfNodes];
    double factor = 1.0;
    switch (unit.toLowerCase()) {
    case "bar":
      factor = 1e-5;
      break;
    case "psi":
      factor = 1.0 / 6894.76;
      break;
    }
    for (int i = 0; i < numberOfNodes; i++) {
      profile[i] = pressureProfile[i] * factor;
    }
    return profile;
  }

  /**
   * Get the velocity profile along the pipe.
   *
   * @return Velocity profile in m/s
   */
  public double[] getVelocityProfile() {
    return velocityProfile.clone();
  }

  /**
   * Get the piezometric head profile.
   *
   * @return Head profile in meters
   */
  public double[] getHeadProfile() {
    return head.clone();
  }

  /**
   * Get the flow rate profile.
   *
   * @return actual face flow traces in m3/s, distinct from the held interval fluxes and cell means
   */
  public double[] getFlowProfile() {
    return flow.clone();
  }

  /**
   * Get the maximum pressure envelope.
   *
   * @return Maximum pressure at each node in Pa
   */
  public double[] getMaxPressureEnvelope() {
    return maxPressureEnvelope.clone();
  }

  /**
   * Get the minimum pressure envelope.
   *
   * @return Minimum pressure at each node in Pa
   */
  public double[] getMinPressureEnvelope() {
    return minPressureEnvelope.clone();
  }

  /**
   * Get the maximum pressure envelope in a specified unit.
   *
   * @param unit unit name, for example Pa, bar, or psi
   * @return maximum pressure envelope in the requested unit
   */
  public double[] getMaxPressureEnvelope(String unit) {
    return convertPressureArray(maxPressureEnvelope, unit);
  }

  /**
   * Get the minimum pressure envelope in a specified unit.
   *
   * @param unit unit name, for example Pa, bar, or psi
   * @return minimum pressure envelope in the requested unit
   */
  public double[] getMinPressureEnvelope(String unit) {
    return convertPressureArray(minPressureEnvelope, unit);
  }

  /**
   * Converts a pressure array to the requested unit.
   *
   * @param pressures pressure values in Pa
   * @param unit requested unit, for example Pa, bar, or psi
   * @return converted pressure values
   */
  private double[] convertPressureArray(double[] pressures, String unit) {
    double[] profile = new double[pressures.length];
    double factor = getPressureConversionFactor(unit);
    for (int i = 0; i < pressures.length; i++) {
      profile[i] = pressures[i] * factor;
    }
    return profile;
  }

  /**
   * Gets the conversion factor from Pa to a requested pressure unit.
   *
   * @param unit requested unit, for example Pa, bar, or psi
   * @return conversion factor from Pa to the requested unit
   */
  private double getPressureConversionFactor(String unit) {
    if (unit == null) {
      return 1.0;
    }
    switch (unit.toLowerCase(Locale.ROOT)) {
    case "bar":
    case "bara":
      return 1.0e-5;
    case "psi":
      return 1.0 / 6894.76;
    default:
      return 1.0;
    }
  }

  /**
   * Get the overall maximum pressure.
   *
   * @return Maximum pressure in Pa
   */
  public double getMaxPressure() {
    double max = Double.NEGATIVE_INFINITY;
    for (double p : maxPressureEnvelope) {
      max = Math.max(max, p);
    }
    return max;
  }

  /**
   * Get the overall maximum pressure in specified unit.
   *
   * @param unit Unit ("Pa", "bar", "psi")
   * @return Maximum pressure
   */
  public double getMaxPressure(String unit) {
    double maxPa = getMaxPressure();
    switch (unit.toLowerCase()) {
    case "bar":
      return maxPa / 1e5;
    case "psi":
      return maxPa / 6894.76;
    default:
      return maxPa;
    }
  }

  /**
   * Get the overall minimum pressure.
   *
   * @return Minimum pressure in Pa
   */
  public double getMinPressure() {
    double min = Double.POSITIVE_INFINITY;
    for (double p : minPressureEnvelope) {
      min = Math.min(min, p);
    }
    return min;
  }

  /**
   * Get the overall minimum pressure in specified unit.
   *
   * @param unit Unit ("Pa", "bar", "psi")
   * @return Minimum pressure
   */
  public double getMinPressure(String unit) {
    double minPa = getMinPressure();
    switch (unit.toLowerCase()) {
    case "bar":
      return minPa / 1e5;
    case "psi":
      return minPa / 6894.76;
    default:
      return minPa;
    }
  }

  /**
   * Get the pressure history at the outlet.
   *
   * @return List of pressures in Pa
   */
  public List<Double> getPressureHistory() {
    return new ArrayList<>(pressureHistory);
  }

  /**
   * Get the time history.
   *
   * @return List of times in seconds
   */
  public List<Double> getTimeHistory() {
    return new ArrayList<>(timeHistory);
  }

  /**
   * Get the current simulation time.
   *
   * @return Time in seconds
   */
  public double getCurrentTime() {
    return currentTime;
  }

  /**
   * Get the calculated wave speed.
   *
   * @return Wave speed in m/s
   */
  public double getWaveSpeed() {
    return waveSpeed;
  }

  /**
   * Get the last calculated pressure drop from inlet to outlet.
   *
   * @return pressure drop in bar
   */
  @Override
  public double getPressureDrop() {
    if (pressureProfile == null || pressureProfile.length == 0) {
      return pressureDrop;
    }
    return (pressureProfile[0] - pressureProfile[pressureProfile.length - 1]) / 1e5;
  }

  /**
   * Get the pipe length.
   *
   * @return Length in meters
   */
  @Override
  public double getLength() {
    return length;
  }

  /**
   * Get the pipe diameter.
   *
   * @return Diameter in meters
   */
  @Override
  public double getDiameter() {
    return diameter;
  }

  /**
   * Get the number of nodes.
   *
   * @return Number of computational nodes
   */
  public int getNumberOfNodes() {
    return numberOfNodes;
  }

  /**
   * Get the wave round-trip time.
   *
   * @return Time for wave to travel L and back in seconds
   */
  public double getWaveRoundTripTime() {
    return 2 * length / waveSpeed;
  }

  /**
   * Reset the simulation to initial steady state.
   */
  public void reset() {
    initialized = false;
    currentTime = 0.0;
    valveOpening = valveBoundary == null ? 1.0 : valveBoundary.getOpening(0.0);
    if (pressureHistory != null) {
      pressureHistory.clear();
    }
    if (timeHistory != null) {
      timeHistory.clear();
    }
  }

  /**
   * Reset the pressure envelopes.
   */
  public void resetEnvelopes() {
    if (maxPressureEnvelope != null && minPressureEnvelope != null) {
      for (int i = 0; i < numberOfNodes; i++) {
        maxPressureEnvelope[i] = pressureProfile[i];
        minPressureEnvelope[i] = pressureProfile[i];
      }
    }
  }
}
