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
 * Water hammer transient pipe model using Method of Characteristics (MOC).
 *
 * <p>
 * This class simulates fast transient pressure waves (water hammer / hydraulic shock) in pipelines.
 * Unlike the advection-based transient model in {@link PipeBeggsAndBrills}, this model propagates
 * pressure waves at the speed of sound, enabling accurate simulation of:
 * </p>
 * <ul>
 * <li>Rapid valve closures (emergency shutdown)</li>
 * <li>Pump trips</li>
 * <li>Check valve slam</li>
 * <li>Pressure surge analysis for pipe stress design</li>
 * </ul>
 *
 * <p>
 * The Method of Characteristics transforms the hyperbolic partial differential equations for
 * one-dimensional transient pipe flow into ordinary differential equations along characteristic
 * lines dx/dt = ±c (speed of sound).
 * </p>
 *
 * <p>
 * <b>Governing Equations:</b>
 * </p>
 * <ul>
 * <li>Continuity: ∂H/∂t + (c²/gA) ∂Q/∂x = 0</li>
 * <li>Momentum: ∂Q/∂t + gA ∂H/∂x + f/(2DA) Q|Q| = 0</li>
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
 * double maxP = pipe.getMaxPressureEnvelope();
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
    CONSTANT_FLOW
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
  private double[] headOld; // Previous time step
  private double[] flowOld; // Previous time step

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
  private double valveCv = -1; // Valve coefficient (-1 = auto from steady state)

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
  public void setLength(double length) {
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
        this.length = length;
        break;
      case "km":
        this.length = length * 1000;
        break;
      case "ft":
        this.length = length * 0.3048;
        break;
      default:
        this.length = length;
    }
  }

  /**
   * Set the pipe inner diameter.
   *
   * @param diameter Diameter in meters
   */
  public void setDiameter(double diameter) {
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
        this.diameter = diameter;
        break;
      case "mm":
        this.diameter = diameter / 1000;
        break;
      case "in":
        this.diameter = diameter * 0.0254;
        break;
      default:
        this.diameter = diameter;
    }
  }

  /**
   * Set the pipe wall thickness.
   *
   * @param thickness Wall thickness in meters
   */
  public void setWallThickness(double thickness) {
    this.wallThickness = thickness;
  }

  /**
   * Set the pipe wall roughness.
   *
   * @param roughness Roughness in meters
   */
  public void setRoughness(double roughness) {
    this.roughness = roughness;
  }

  /**
   * Set the number of computational nodes.
   *
   * @param nodes Number of nodes (minimum 10)
   */
  public void setNumberOfNodes(int nodes) {
    this.numberOfNodes = Math.max(10, nodes);
  }

  /**
   * Set the pipe elastic modulus.
   *
   * @param modulus Elastic modulus in Pa
   */
  public void setPipeElasticModulus(double modulus) {
    this.pipeElasticModulus = modulus;
  }

  /**
   * Set the elevation change (outlet - inlet).
   *
   * @param elevation Elevation change in meters
   */
  public void setElevationChange(double elevation) {
    this.elevationChange = elevation;
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
   * Set the downstream boundary condition type.
   *
   * @param type Boundary type
   */
  public void setDownstreamBoundary(BoundaryType type) {
    this.downstreamBoundary = type;
  }

  /**
   * Set the valve opening fraction (0 = closed, 1 = fully open).
   *
   * @param opening Valve opening (0-1)
   */
  public void setValveOpening(double opening) {
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
   * Set the Courant number for time step control.
   *
   * @param cn Courant number (typically 1.0 for stability)
   */
  public void setCourantNumber(double cn) {
    this.courantNumber = cn;
  }

  /**
   * Override the automatically calculated wave speed.
   *
   * @param speed Wave speed in m/s
   */
  public void setWaveSpeed(double speed) {
    this.waveSpeed = speed;
  }

  /**
   * Calculate the effective wave speed including pipe elasticity (Korteweg formula).
   *
   * @return Effective wave speed in m/s
   */
  public double calcEffectiveWaveSpeed() {
    if (fluidDensity <= 0 || fluidSoundSpeed <= 0) {
      return 1200; // Default for water
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
    // Calculate area and segment length
    area = Math.PI * diameter * diameter / 4.0;
    segmentLength = length / (numberOfNodes - 1);

    // Initialize arrays
    head = new double[numberOfNodes];
    flow = new double[numberOfNodes];
    headOld = new double[numberOfNodes];
    flowOld = new double[numberOfNodes];
    pressureProfile = new double[numberOfNodes];
    velocityProfile = new double[numberOfNodes];
    maxPressureEnvelope = new double[numberOfNodes];
    minPressureEnvelope = new double[numberOfNodes];
    pressureHistory = new ArrayList<>();
    timeHistory = new ArrayList<>();

    // Get fluid properties from inlet stream
    SystemInterface system = inStream.getThermoSystem().clone();
    system.initPhysicalProperties();

    fluidDensity = system.getDensity("kg/m3");
    fluidViscosity = system.getViscosity("kg/msec");

    // Get speed of sound from thermodynamics
    try {
      fluidSoundSpeed = system.getPhase(0).getSoundSpeed();
    } catch (Exception e) {
      // Default to water if calculation fails
      fluidSoundSpeed = 1200;
      logger.warn("Could not calculate speed of sound, using default: " + fluidSoundSpeed);
    }

    // Calculate effective wave speed (with pipe elasticity)
    if (waveSpeed < 0) {
      waveSpeed = calcEffectiveWaveSpeed();
    }

    // Characteristic impedance
    characteristicImpedance = waveSpeed / (GRAVITY * area);

    // Calculate initial steady-state conditions
    double inletPressure = inStream.getPressure() * 1e5; // Pa
    double massFlow = inStream.getFlowRate("kg/sec");
    double volumeFlow = massFlow / fluidDensity; // m³/s
    double velocity = volumeFlow / area;

    // Calculate Reynolds number and friction factor
    double reynoldsNumber = fluidDensity * velocity * diameter / fluidViscosity;
    frictionFactor = calcFrictionFactor(reynoldsNumber);

    // Calculate steady-state head and pressure profile
    upstreamHead = inletPressure / (fluidDensity * GRAVITY);

    // Friction head loss per unit length
    double headLossPerMeter =
        frictionFactor * velocity * Math.abs(velocity) / (2 * GRAVITY * diameter);

    // Elevation head change per unit length
    double elevationPerMeter = elevationChange / length;

    for (int i = 0; i < numberOfNodes; i++) {
      double x = i * segmentLength;
      head[i] = upstreamHead - headLossPerMeter * x - elevationPerMeter * x;
      flow[i] = volumeFlow;
      headOld[i] = head[i];
      flowOld[i] = flow[i];

      pressureProfile[i] = head[i] * fluidDensity * GRAVITY;
      velocityProfile[i] = velocity;
      maxPressureEnvelope[i] = pressureProfile[i];
      minPressureEnvelope[i] = pressureProfile[i];
    }

    // Store downstream conditions for valve BC
    downstreamHead = head[numberOfNodes - 1];

    // Calculate valve coefficient from steady state
    if (valveCv < 0 && valveOpening > 0) {
      // Q = Cv * tau * sqrt(delta_H)
      // Cv = Q / (tau * sqrt(delta_H))
      double deltaH = head[numberOfNodes - 2] - downstreamHead;
      if (deltaH > 0) {
        valveCv = volumeFlow / (valveOpening * Math.sqrt(deltaH));
      } else {
        valveCv = volumeFlow; // Simple coefficient
      }
    }

    currentTime = 0.0;
    initialized = true;

    logger.info("WaterHammerPipe initialized: wave speed = " + waveSpeed + " m/s, "
        + "max time step = " + getMaxStableTimeStep() + " s");
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
    if (!initialized) {
      initialize();
    }

    // Check Courant condition
    double maxDt = getMaxStableTimeStep();
    if (dt > maxDt) {
      logger.warn(
          "Time step " + dt + " exceeds Courant limit " + maxDt + ". Results may be unstable.");
    }

    // Store old values
    System.arraycopy(head, 0, headOld, 0, numberOfNodes);
    System.arraycopy(flow, 0, flowOld, 0, numberOfNodes);

    // Calculate friction term coefficient
    // R = f * dx / (2 * g * D * A^2)
    double R = frictionFactor * segmentLength / (2 * GRAVITY * diameter * area * area);

    // Interior points using Method of Characteristics
    for (int i = 1; i < numberOfNodes - 1; i++) {
      // C+ characteristic from upstream (point A)
      double HA = headOld[i - 1];
      double QA = flowOld[i - 1];

      // C- characteristic from downstream (point B)
      double HB = headOld[i + 1];
      double QB = flowOld[i + 1];

      // Friction terms
      double frictionA = R * QA * Math.abs(QA);
      double frictionB = R * QB * Math.abs(QB);

      // Compatibility equations
      // C+: Hp - HA + B*(Qp - QA) + friction = 0
      // C-: Hp - HB - B*(Qp - QB) - friction = 0
      double Cp = HA + characteristicImpedance * QA - frictionA;
      double Cm = HB - characteristicImpedance * QB + frictionB;

      // Solve for point P
      head[i] = (Cp + Cm) / 2.0;
      flow[i] = (Cp - Cm) / (2.0 * characteristicImpedance);
    }

    // Apply boundary conditions
    applyUpstreamBC();
    applyDownstreamBC();

    // Update pressure and velocity profiles
    for (int i = 0; i < numberOfNodes; i++) {
      pressureProfile[i] = head[i] * fluidDensity * GRAVITY;
      velocityProfile[i] = flow[i] / area;

      // Update envelopes
      maxPressureEnvelope[i] = Math.max(maxPressureEnvelope[i], pressureProfile[i]);
      minPressureEnvelope[i] = Math.min(minPressureEnvelope[i], pressureProfile[i]);
    }

    // Store history
    currentTime += dt;
    pressureHistory.add(pressureProfile[numberOfNodes - 1]);
    timeHistory.add(currentTime);

    // Update outlet stream
    updateOutletStream();

    setCalculationIdentifier(id);
  }

  /**
   * Apply upstream boundary condition.
   */
  private void applyUpstreamBC() {
    int i = 0;

    switch (upstreamBoundary) {
      case RESERVOIR:
        // Constant head reservoir
        head[i] = upstreamHead;
        // Use C- from interior point
        double HB = headOld[i + 1];
        double QB = flowOld[i + 1];
        double R = frictionFactor * segmentLength / (2 * GRAVITY * diameter * area * area);
        double frictionB = R * QB * Math.abs(QB);
        double Cm = HB - characteristicImpedance * QB + frictionB;
        flow[i] = (head[i] - Cm) / characteristicImpedance;
        break;

      case CONSTANT_FLOW:
        // Constant flow rate
        flow[i] = flowOld[i];
        HB = headOld[i + 1];
        QB = flowOld[i + 1];
        R = frictionFactor * segmentLength / (2 * GRAVITY * diameter * area * area);
        frictionB = R * QB * Math.abs(QB);
        Cm = HB - characteristicImpedance * QB + frictionB;
        head[i] = Cm + characteristicImpedance * flow[i];
        break;

      case CLOSED_END:
        // No flow
        flow[i] = 0;
        HB = headOld[i + 1];
        QB = flowOld[i + 1];
        R = frictionFactor * segmentLength / (2 * GRAVITY * diameter * area * area);
        frictionB = R * QB * Math.abs(QB);
        Cm = HB - characteristicImpedance * QB + frictionB;
        head[i] = Cm;
        break;

      default:
        head[i] = upstreamHead;
        flow[i] = flowOld[i];
    }
  }

  /**
   * Apply downstream boundary condition.
   */
  private void applyDownstreamBC() {
    int i = numberOfNodes - 1;

    switch (downstreamBoundary) {
      case RESERVOIR:
        // Constant head reservoir
        head[i] = downstreamHead;
        // Use C+ from interior point
        double HA = headOld[i - 1];
        double QA = flowOld[i - 1];
        double R = frictionFactor * segmentLength / (2 * GRAVITY * diameter * area * area);
        double frictionA = R * QA * Math.abs(QA);
        double Cp = HA + characteristicImpedance * QA - frictionA;
        flow[i] = (Cp - head[i]) / characteristicImpedance;
        break;

      case VALVE:
        // Valve with variable opening
        // Q = Cv * tau * sqrt(H_upstream - H_downstream)
        HA = headOld[i - 1];
        QA = flowOld[i - 1];
        R = frictionFactor * segmentLength / (2 * GRAVITY * diameter * area * area);
        frictionA = R * QA * Math.abs(QA);
        Cp = HA + characteristicImpedance * QA - frictionA;

        if (valveOpening < 0.001) {
          // Valve closed - no flow
          flow[i] = 0;
          head[i] = Cp;
        } else {
          // Solve valve equation with C+ characteristic
          // Q = Cv * tau * sqrt(H - Hd) and H = Cp - B*Q
          // Quadratic: B*Q^2 + 2*Cv^2*tau^2*Q + Cv^2*tau^2*(Hd-Cp) = 0
          double tau = valveOpening;
          double CvTau2 = valveCv * valveCv * tau * tau;
          double a = characteristicImpedance;
          double b = CvTau2;
          double c = CvTau2 * (downstreamHead - Cp);

          // Solve quadratic: a*Q^2 + b*Q + c = 0
          double discriminant = b * b - 4 * a * c;
          if (discriminant < 0) {
            // No real solution - use previous flow
            flow[i] = flowOld[i] * valveOpening;
          } else {
            flow[i] = (-b + Math.sqrt(discriminant)) / (2 * a);
            if (flow[i] < 0) {
              flow[i] = (-b - Math.sqrt(discriminant)) / (2 * a);
            }
          }
          head[i] = Cp - characteristicImpedance * flow[i];
        }
        break;

      case CLOSED_END:
        // No flow
        flow[i] = 0;
        HA = headOld[i - 1];
        QA = flowOld[i - 1];
        R = frictionFactor * segmentLength / (2 * GRAVITY * diameter * area * area);
        frictionA = R * QA * Math.abs(QA);
        Cp = HA + characteristicImpedance * QA - frictionA;
        head[i] = Cp;
        break;

      case CONSTANT_FLOW:
        // Constant flow rate
        flow[i] = flowOld[i];
        HA = headOld[i - 1];
        QA = flowOld[i - 1];
        R = frictionFactor * segmentLength / (2 * GRAVITY * diameter * area * area);
        frictionA = R * QA * Math.abs(QA);
        Cp = HA + characteristicImpedance * QA - frictionA;
        head[i] = Cp - characteristicImpedance * flow[i];
        break;

      default:
        head[i] = downstreamHead;
        flow[i] = flowOld[i];
    }
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
   * Get the pressure profile along the pipe.
   *
   * @return Pressure profile in Pa
   */
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
   * @return Flow profile in m³/s
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
   * Get the pipe length.
   *
   * @return Length in meters
   */
  public double getLength() {
    return length;
  }

  /**
   * Get the pipe diameter.
   *
   * @return Diameter in meters
   */
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
    valveOpening = 1.0;
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
