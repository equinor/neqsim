package neqsim.process.measurementdevice.simpleflowregime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Lumped (three-state) dynamic severe-slugging analyser for a downward-inclined flowline feeding a vertical riser.
 *
 * <p>
 * The model integrates the liquid momentum balance of the slug in the riser and bend, the riser void fraction and the
 * isothermal ideal-gas pressure of the gas trapped in the flowline. The flow regime is classified from the relative
 * pressure swing of the simulated riser-base pressure in the second half of the simulation.
 * </p>
 *
 * <p>
 * Conventions: superficial liquid velocity is at flowing conditions; superficial gas velocity is defined at the gas
 * reference pressure ({@link #setGasReferencePressure(double)}, default 1.0e5 Pa; stream-based constructors use the
 * stream pressure). Pressures are absolute and in Pa, the temperature is in Celsius, and the reported result pressure
 * is in bara. Oil and water are lumped into one liquid by {@link FluidSevereSlug}.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public final class SevereSlugAnalyser extends MeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SevereSlugAnalyser.class);

  transient FluidSevereSlug fluidSevereS;
  transient Pipe pipe;
  SevereSlugAnalyser severeSlug;
  Stream streamS;

  final double gravAcc = neqsim.thermo.ThermodynamicConstantsInterface.gravity;

  // Severe slug problem
  private double simulationTime = 200;
  private double usl = 3.0;
  private double usg = 0.5;
  private double outletPressure = 100000.0;
  private double temperature = 20.0;
  private int numberOfTimeSteps = 20000;

  // These variables should not be changed by the user I guess.
  // But this can be done if the user has advanced knowledge about the problem.
  double alfaRiser = 0.0; // gas fraction in the riser
  double z = 0.0001; // some initial value to start the calculation
  double lambdaStagnant = 0.0;
  double uLevel = 0.0; // some initial value to start the calculation
  double valveConstant = 0.0;
  double normalPressure = 100000.0;
  /** Absolute pressure in Pa at which the superficial gas velocity is defined. */
  private double gasReferencePressure = normalPressure;
  /** Largest internal integration time step in seconds. */
  private static final double MAX_INTERNAL_TIME_STEP = 1.0e-3;
  final double pi = Math.PI;

  double[] resPres;
  double[] resTime;
  double[] resLiqHoldUpRiser;
  double[] resLiqHeight;
  double[] resMixVelocity;
  double[] usgMap;
  double[] uslMap;
  double slugValue;
  double pressureAmplitude;
  double meanPressure;

  // Simulation variables (calculated variables)
  double deltaT;
  double driftVel;
  double flowDistCoeff;
  double mixDensity;
  double pressure;
  double slugLength;
  double transVel;
  double Um;
  double UmOld;
  double UsgL;
  double UslL;
  double UsgR;
  double UslR;
  double U;
  double Re;
  double lambda;
  double friction;
  double frictionStagnant;
  double frictionValve;
  double frictionTot;
  double gravL;
  double gravR;
  double gravity;
  double alfaRiserOld;
  double zOld;
  double Lg;
  double pressureOld;
  double alfaLeft;
  double gasDensity;
  double n;
  double gamma1;
  double gamma2;
  double gamma;
  double holdUp1;
  double holdUp2;
  double holdUp;
  double function2;
  double function1;

  double iter;

  String flowPattern;

  /**
   * Constructor for class SevereSlugAnalyser.
   *
   * @param name Name of object
   */
  public SevereSlugAnalyser(String name) {
    super(name, "m3/sec");
  }

  /**
   * Constructor for class SevereSlugAnalyser.
   *
   * @param name Name of object
   * @param usl Superficial liquid velocity
   * @param usg Superficial gas velocity
   */
  public SevereSlugAnalyser(String name, double usl, double usg) {
    this(name);
    this.setSuperficialLiquidVelocity(usl);
    this.setSuperficialGasVelocity(usg);
  }

  /**
   * Constructor for class SevereSlugAnalyser.
   *
   * @param name Name of object
   * @param outletPressure Pipe Outlet Pressure
   * @param temperature Slug temperature.
   * @param simulationTime Simulation time
   * @param numberOfTimeSteps Number of simulation time steps to calculate
   */
  public SevereSlugAnalyser(String name, double outletPressure, double temperature, double simulationTime,
      int numberOfTimeSteps) {
    this(name);
    this.setOutletPressure(outletPressure);
    this.setTemperature(temperature);
    this.setSimulationTime(simulationTime);
    this.setNumberOfTimeSteps(numberOfTimeSteps);
  }

  /**
   * Constructor for class SevereSlugAnalyser.
   *
   * @param name Name of object
   * @param usl Superficial liquid velocity
   * @param usg Superficial gas velocity
   * @param outletPressure Pipe Outlet Pressure in Pa
   * @param temperature Slug temperature.
   * @param simulationTime Simulation time
   * @param numberOfTimeSteps Number of simulation time steps to calculate
   */
  SevereSlugAnalyser(String name, double usl, double usg, double outletPressure, double temperature,
      double simulationTime, int numberOfTimeSteps) {
    this(name, usl, usg);
    this.setOutletPressure(outletPressure);
    this.setTemperature(temperature);
    this.setSimulationTime(simulationTime);
    this.setNumberOfTimeSteps(numberOfTimeSteps);
  }

  /**
   * Constructor for class SevereSlugAnalyser.
   *
   * @param name Name of object
   * @param fluid Fluid object. Used to calculate usl and usg
   * @param pipe Pipe object
   * @param outletPressure Pipe Outlet Pressure
   * @param temperature Slug temperature.
   * @param simulationTime Simulation time
   * @param numberOfTimeSteps Number of simulation time steps to calculate
   */
  public SevereSlugAnalyser(String name, SystemInterface fluid, Pipe pipe, double outletPressure, double temperature,
      double simulationTime, int numberOfTimeSteps) {
    this(name, outletPressure, temperature, simulationTime, numberOfTimeSteps);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();
    updateSuperficialVelocities(fluid, pipe);
  }

  /**
   * Constructor for class SevereSlugAnalyser.
   *
   * @param name Name of object
   * @param stream Stream object
   * @param internalDiameter pipe internal diameter
   * @param leftLength pipe left length
   * @param rightLength pipe right length
   * @param angle pipe angle
   */
  public SevereSlugAnalyser(String name, Stream stream, double internalDiameter, double leftLength, double rightLength,
      double angle) {
    this(name, stream, internalDiameter, leftLength, rightLength, angle, stream.getPressure("Pa"),
        stream.getTemperature("C"), 500.0, 50000);
  }

  /**
   * Constructor for class SevereSlugAnalyser.
   *
   * @param name Name of object
   * @param stream Stream object
   * @param internalDiameter pipe internal diameter
   * @param leftLength pipe left length
   * @param rightLength pipe right length
   * @param angle pipe angle
   * @param simulationTime Simulation time
   * @param numberOfTimeSteps Number of simulation time steps to calculate
   */
  public SevereSlugAnalyser(String name, Stream stream, double internalDiameter, double leftLength, double rightLength,
      double angle, double simulationTime, int numberOfTimeSteps) {
    this(name, stream, internalDiameter, leftLength, rightLength, angle, stream.getPressure("Pa"),
        stream.getTemperature("C"), simulationTime, numberOfTimeSteps);
  }

  /**
   * Constructor for class SevereSlugAnalyser.
   *
   * @param name Name of object
   * @param stream Stream object
   * @param internalDiameter Pipe internal diameter
   * @param leftLength Pipe left length
   * @param rightLength Pipe right length
   * @param angle Pipe angle
   * @param outletPressure Pipe Outlet Pressure in Pa
   * @param temperature Slug temperature.
   * @param simulationTime Simulation time
   * @param numberOfTimeSteps Number of simulation time steps to calculate
   */
  public SevereSlugAnalyser(String name, Stream stream, double internalDiameter, double leftLength, double rightLength,
      double angle, double outletPressure, double temperature, double simulationTime, int numberOfTimeSteps) {
    this(name, outletPressure, temperature, simulationTime, numberOfTimeSteps);
    pipe = new Pipe("slugpipe", internalDiameter, leftLength, rightLength, angle);
    streamS = stream;
    SystemInterface fluid = stream.getThermoSystem();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();
    fluidSevereS = new FluidSevereSlug(fluid);
    updateSuperficialVelocities(fluid, pipe);
    severeSlug = createWorker(name);
  }

  /**
   * Update the superficial velocities from a flashed fluid. Phases are identified by type, so the result does not
   * depend on phase ordering. Oil and aqueous phases are summed into the liquid velocity. The gas reference pressure is
   * set to the fluid pressure because the gas volume flow is evaluated there.
   *
   * @param fluid flashed fluid with initialised physical properties
   * @param pipe pipe geometry
   */
  private void updateSuperficialVelocities(SystemInterface fluid, Pipe pipe) {
    double liquidFlow = 0.0;
    if (fluid.hasPhaseType("oil")) {
      liquidFlow += fluid.getPhase("oil").getFlowRate("m3/sec");
    }
    if (fluid.hasPhaseType("aqueous")) {
      liquidFlow += fluid.getPhase("aqueous").getFlowRate("m3/sec");
    }
    usl = liquidFlow / pipe.getArea();
    usg = fluid.hasPhaseType("gas") ? fluid.getPhase("gas").getFlowRate("m3/sec") / pipe.getArea() : 0.0;
    gasReferencePressure = fluid.getPressure("Pa");
  }

  /**
   * Create the internal analyser that carries the simulation state for the current operating point.
   *
   * @param workerName name of the internal analyser
   * @return analyser configured with the operating point of this object
   */
  private SevereSlugAnalyser createWorker(String workerName) {
    SevereSlugAnalyser worker = new SevereSlugAnalyser(workerName, usl, usg, outletPressure, temperature,
        simulationTime, numberOfTimeSteps);
    worker.setGasReferencePressure(gasReferencePressure);
    return worker;
  }

  /**
   * Set the absolute pressure at which the superficial gas velocity is defined. The gas mass inflow of the model is
   * proportional to the superficial gas velocity times this pressure.
   *
   * @param gasReferencePressure reference pressure in Pa
   */
  public void setGasReferencePressure(double gasReferencePressure) {
    if (!(gasReferencePressure > 0.0) || Double.isInfinite(gasReferencePressure)) {
      throw new IllegalArgumentException("gasReferencePressure must be finite and > 0");
    }
    this.gasReferencePressure = gasReferencePressure;
  }

  /**
   * Get the absolute pressure at which the superficial gas velocity is defined.
   *
   * @return reference pressure in Pa
   */
  public double getGasReferencePressure() {
    return gasReferencePressure;
  }

  /**
   * setSuperficialLiquidVelocity.
   *
   * @param usl a double
   */
  public void setSuperficialLiquidVelocity(double usl) {
    this.usl = usl;
  }

  /**
   * getSuperficialLiquidVelocity.
   *
   * @return a double
   */
  public double getSuperficialLiquidVelocity() {
    return usl;
  }

  /**
   * setSuperficialGasVelocity.
   *
   * @param usg a double
   */
  public void setSuperficialGasVelocity(double usg) {
    this.usg = usg;
  }

  /**
   * getSuperficialGasVelocity.
   *
   * @return a double
   */
  public double getSuperficialGasVelocity() {
    return usg;
  }

  /**
   * Getter for the field <code>flowPattern</code>.
   *
   * @return a {@link java.lang.String} object
   */
  public String getFlowPattern() {
    return flowPattern;
  }

  /**
   * Get the slug value: half the peak-to-peak riser-base pressure swing divided by the mean pressure, evaluated over
   * the second half of the simulation.
   *
   * @return dimensionless slug value
   */
  public double getSlugValue() {
    return slugValue;
  }

  /**
   * Get the peak-to-peak pressure swing at the riser base in the second half of the last simulation.
   *
   * @return pressure amplitude in bar
   */
  public double getPressureAmplitude() {
    return pressureAmplitude;
  }

  /**
   * Get the mean riser-base pressure in the second half of the last simulation.
   *
   * @return mean pressure in bara
   */
  public double getMeanPressure() {
    return meanPressure;
  }

  /**
   * Get the simulated riser-base pressure trend of the last simulation.
   *
   * @return copy of the pressure trend in bara, or an empty array before the first simulation
   */
  public double[] getPressureTrend() {
    double[] source = severeSlug != null ? severeSlug.resPres : resPres;
    return source == null ? new double[0] : source.clone();
  }

  /**
   * Get the time axis of the last simulation.
   *
   * @return copy of the time axis in seconds, or an empty array before the first simulation
   */
  public double[] getTimeTrend() {
    double[] source = severeSlug != null ? severeSlug.resTime : resTime;
    return source == null ? new double[0] : source.clone();
  }

  // 3. Pipe Outlet Pressure Encapsulation
  /**
   * Setter for the field <code>outletPressure</code>.
   *
   * @param outletPressure Value in engineering unit Pa
   */
  public void setOutletPressure(double outletPressure) {
    this.outletPressure = outletPressure;
  }

  /**
   * Getter for the field <code>outletPressure</code>.
   *
   * @return Get pressure in Pa
   */
  public double getOutletPressure() {
    return outletPressure;
  }

  /**
   * Setter for the field <code>temperature</code>.
   *
   * @param temperature a double
   */
  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }

  /**
   * Getter for the field <code>temperature</code>.
   *
   * @return a double
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * Setter for the field <code>numberOfTimeSteps</code>.
   *
   * @param numberOfTimeSteps a int
   */
  public void setNumberOfTimeSteps(int numberOfTimeSteps) {
    if (numberOfTimeSteps < 2) {
      throw new IllegalArgumentException("numberOfTimeSteps must be at least 2");
    }
    this.numberOfTimeSteps = numberOfTimeSteps;
  }

  /**
   * Getter for the field <code>numberOfTimeSteps</code>.
   *
   * @return a int
   */
  public int getNumberOfTimeSteps() {
    return numberOfTimeSteps;
  }

  /**
   * Setter for the field <code>simulationTime</code>.
   *
   * @param simulationTime a double
   */
  public void setSimulationTime(double simulationTime) {
    if (!(simulationTime > 0.0) || Double.isInfinite(simulationTime)) {
      throw new IllegalArgumentException("simulationTime must be finite and > 0");
    }
    this.simulationTime = simulationTime;
  }

  /**
   * Getter for the field <code>simulationTime</code>.
   *
   * @return a double
   */
  public double getSimulationTime() {
    return simulationTime;
  }

  /**
   * Calculate the product of the specific gas constant and the absolute temperature, R T / M.
   *
   * @param fluid a {@link neqsim.process.measurementdevice.simpleflowregime.FluidSevereSlug} object
   * @return R T / M in J/kg
   */
  public double gasConst(FluidSevereSlug fluid) {
    return ThermodynamicConstantsInterface.R / fluid.getMolecularWeight() * (273.15 + temperature);
  }

  // Declare the variables for results after creating an object Severe slug with required number of
  // steps.

  /**
   * slugHoldUp.
   *
   * @param pipe a {@link neqsim.process.measurementdevice.simpleflowregime.Pipe} object
   * @param severeSlug a {@link neqsim.process.measurementdevice.simpleflowregime.SevereSlugAnalyser} object
   * @return a double
   */
  public double slugHoldUp(Pipe pipe, SevereSlugAnalyser severeSlug) {
    double Udrift;
    double C0 = 1.2;
    double Umix;

    Umix = severeSlug.getSuperficialGasVelocity() + severeSlug.getSuperficialLiquidVelocity();
    // Bendiksen (1984) drift velocity: 0.54 sqrt(gD) horizontal and 0.35 sqrt(gD) vertical contribution
    double beta = Math.abs(pipe.getAngle("Radian"));
    Udrift = (0.54 * Math.cos(beta) + 0.35 * Math.sin(beta)) * Math.sqrt(gravAcc * pipe.getInternalDiameter());
    holdUp = 1 - severeSlug.getSuperficialGasVelocity() / (C0 * Umix + Udrift);
    return holdUp;
  }

  /**
   * stratifiedHoldUp.
   *
   * @param fluid a {@link neqsim.process.measurementdevice.simpleflowregime.FluidSevereSlug} object
   * @param pipe a {@link neqsim.process.measurementdevice.simpleflowregime.Pipe} object
   * @param severeSlug a {@link neqsim.process.measurementdevice.simpleflowregime.SevereSlugAnalyser} object
   * @return a double
   */
  public double stratifiedHoldUp(FluidSevereSlug fluid, Pipe pipe, SevereSlugAnalyser severeSlug) {
    if (pipe.getAngle("Radian") <= 0.0) {
      throw new IllegalArgumentException("stratified holdup needs a downward-inclined flowline (angle > 0)");
    }
    if (!(severeSlug.getSuperficialLiquidVelocity() > 0.0)) {
      holdUp = 0.0;
      return holdUp;
    }
    Re = fluid.getLiqDensity() * severeSlug.getSuperficialLiquidVelocity() * pipe.getInternalDiameter()
        / (fluid.getliqVisc());
    lambda = Math.max(0.34 * Math.pow(Re, -0.25), 64 / Re);
    if (0.34 * Math.pow(Re, -0.25) > 64 / Re) {
      n = 0.25;
    } else {
      n = 1;
    }
    friction = 0.5 * lambda * Math.pow(severeSlug.getSuperficialLiquidVelocity(), 2)
        / (gravAcc * Math.sin(pipe.getAngle("Radian")) * pipe.getInternalDiameter());

    // f(gamma) = H^3 (pi/gamma)^(n+1) - friction increases monotonically with the half wetted angle gamma, so
    // bisection on (0, pi) is robust. A friction number >= 1 means the liquid cannot drain as a stratified layer.
    if (friction >= 1.0) {
      holdUp = 1.0;
      return holdUp;
    }
    gamma1 = 1.0e-6;
    gamma2 = pi;
    iter = 0;
    while (gamma2 - gamma1 > 1e-10 && iter < 200) {
      gamma = 0.5 * (gamma1 + gamma2);
      holdUp1 = (gamma - 0.5 * Math.sin(2 * gamma)) / (pi);
      function1 = Math.pow(holdUp1, 3) * Math.pow((pi / gamma), (n + 1)) - friction;
      if (function1 > 0.0) {
        gamma2 = gamma;
      } else {
        gamma1 = gamma;
      }
      iter = iter + 1;
    }
    gamma = 0.5 * (gamma1 + gamma2);
    holdUp = (gamma - 0.5 * Math.sin(2 * gamma)) / (pi);
    return holdUp;
  }

  // Passing 3 objects as input parameters (fluid, pipe, severeSlug)
  /**
   * runSevereSlug.
   *
   * @param fluid a {@link neqsim.process.measurementdevice.simpleflowregime.FluidSevereSlug} object
   * @param pipe a {@link neqsim.process.measurementdevice.simpleflowregime.Pipe} object
   * @param severeSlug a {@link neqsim.process.measurementdevice.simpleflowregime.SevereSlugAnalyser} object
   */
  public void runSevereSlug(FluidSevereSlug fluid, Pipe pipe, SevereSlugAnalyser severeSlug) {
    int steps = severeSlug.getNumberOfTimeSteps();
    resPres = new double[steps];
    resTime = new double[steps];
    resLiqHoldUpRiser = new double[steps];
    resLiqHeight = new double[steps];
    resMixVelocity = new double[steps];

    // Output interval follows the requested simulation time; the explicit integration uses sub-steps that never
    // exceed MAX_INTERNAL_TIME_STEP so the result does not depend on the output resolution.
    double outputInterval = severeSlug.getSimulationTime() / steps;
    int subSteps = Math.max(1, (int) Math.ceil(outputInterval / MAX_INTERNAL_TIME_STEP));
    deltaT = outputInterval / subSteps;

    double liqDensity = fluid.getLiqDensity();
    double usl = severeSlug.getSuperficialLiquidVelocity();
    double gasInflow = severeSlug.getSuperficialGasVelocity() * severeSlug.getGasReferencePressure();
    double sinBeta = Math.sin(pipe.getAngle("Radian"));
    double absTemperature = 273.15 + severeSlug.getTemperature();

    // Initial condition: riser full of liquid, bend just open. State is reset so repeated runs are reproducible.
    alfaRiser = 0.0;
    z = 0.0001;
    uLevel = 0.0;
    mixDensity = liqDensity;
    pressure = severeSlug.getOutletPressure() + mixDensity * gravAcc * pipe.getRightLength();
    Um = severeSlug.getSuperficialGasVelocity() * severeSlug.getGasReferencePressure() / pressure;
    holdUp = severeSlug.stratifiedHoldUp(fluid, pipe, severeSlug);
    // Drift velocity for the vertical flows
    driftVel = 0.35 * Math.sqrt(gravAcc * pipe.getInternalDiameter());
    // Gas fraction of the stratified flowline; bounded away from zero to keep the gas volume finite
    alfaLeft = Math.max(1 - holdUp, 1.0e-3);
    // The liquid front can at most fill the flowline up to a small remaining gas length
    double minLevel = -0.999 * pipe.getLeftLength();

    for (int i = 0; i < steps; i++) {
      for (int j = 0; j < subSteps; j++) {
        slugLength = -z + pipe.getRightLength() * (1 - alfaRiser); // Slug Length
        // Reynolds number
        Re = liqDensity * Math.abs(Um) * pipe.getInternalDiameter() / fluid.getliqVisc();
        // friction factor; a stagnant slug has no wall friction (avoids 64/Re = infinity times zero)
        lambda = Re > 1.0e-12 ? Math.max(0.34 * Math.pow(Re, -0.25), 64 / Re) : 0.0;
        // frictional pressure loss
        friction = 0.5 * lambda * liqDensity * Um * Math.abs(Um) * slugLength / pipe.getInternalDiameter();
        // Oscillation Friction
        frictionStagnant = 0.5 * lambdaStagnant * liqDensity * uLevel * Math.abs(uLevel) * slugLength
            / pipe.getInternalDiameter();
        // Valve Friction
        frictionValve = valveConstant * liqDensity * Um * Math.abs(Um);
        // Total Friction
        friction = friction + frictionStagnant + frictionValve;
        // Gravity: liquid backed up in the downward flowline lowers the head the gas has to lift
        gravL = -liqDensity * Math.abs(z) * gravAcc * sinBeta;
        gravR = mixDensity * gravAcc * pipe.getRightLength();
        gravity = gravL + gravR;

        // Momentum Balance
        UmOld = Um;
        Um = UmOld + deltaT * ((pressure - severeSlug.getOutletPressure()) - friction - gravity)
            / (-z * liqDensity + pipe.getRightLength() * mixDensity);

        // Slip Relation: Calculate translational velocity
        if (Re < 2300) {
          flowDistCoeff = 2;
        } else {
          flowDistCoeff = 1.2;
        }

        transVel = flowDistCoeff * Um + driftVel;
        // State Equation
        // All cases, Case 1: Open Bend; Case 2: Blocked Bend; Case 3: Backflow
        UsgL = (Um - usl) * ((Um > 0) ? 1 : 0) * ((z > 0) ? 1 : 0);

        UslL = (usl * ((z > 0) ? 1 : 0) + Um * ((z < 0) ? 1 : 0)) * ((Um > 0) ? 1 : 0) + Um * ((Um < 0) ? 1 : 0);

        UsgR = (alfaRiser * transVel * ((z > 0) ? 1 : 0) + Um * ((z < 0) ? 1 : 0)) * ((Um > 0) ? 1 : 0)
            + Um * ((Um < 0) ? 1 : 0);
        UslR = (Um - alfaRiser * transVel) * ((Um > 0) ? 1 : 0) * ((z > 0) ? 1 : 0);

        U = UsgL - UsgR - UslL + UslR;

        // Riser vapour fraction
        alfaRiserOld = alfaRiser;
        alfaRiser = alfaRiserOld + 0.5 * deltaT * U / (pipe.getRightLength());
        alfaRiser = Math.max(0.0, alfaRiser);
        alfaRiser = Math.min(1.0, alfaRiser);

        // Level. uLevel is the superficial liquid volume flux leaving the flowline. The liquid front only has to
        // fill the gas fraction alfaLeft of the stratified flowline, so the front moves at uLevel / alfaLeft.
        uLevel = (-usl + Um);
        zOld = z;
        z = zOld + deltaT * uLevel / alfaLeft;
        if (z >= 0) {
          z = 0.0001;
          uLevel = 0.0;
        } else if (z < minLevel) {
          z = minLevel;
          uLevel = 0.0;
        }

        // Gas mass balance of the flowline gas volume A alfaLeft Lg (isothermal ideal gas)
        Lg = pipe.getLeftLength() + z;
        pressureOld = pressure;
        pressure = pressureOld + deltaT * (gasInflow - UsgL * pressureOld) / (Lg * alfaLeft)
            - deltaT * (pressureOld * uLevel) / (Lg * alfaLeft);

        gasDensity = pressure / (fluid.getGasConstant() * absTemperature);
        mixDensity = alfaRiser * gasDensity + (1 - alfaRiser) * liqDensity;
      }

      resPres[i] = pressure / 100000;
      resTime[i] = (i + 1) * outputInterval;
      resLiqHoldUpRiser[i] = (1 - alfaRiser);
      resLiqHeight[i] = z;
      resMixVelocity[i] = Um;
    }
  }

  /**
   * checkFlowRegime.
   *
   * @param fluid a {@link neqsim.process.measurementdevice.simpleflowregime.FluidSevereSlug} object
   * @param pipe a {@link neqsim.process.measurementdevice.simpleflowregime.Pipe} object
   * @param severeSlug a {@link neqsim.process.measurementdevice.simpleflowregime.SevereSlugAnalyser} object
   * @return a {@link java.lang.String} object
   */
  public String checkFlowRegime(FluidSevereSlug fluid, Pipe pipe, SevereSlugAnalyser severeSlug) {
    severeSlug.runSevereSlug(fluid, pipe, severeSlug);
    // Statistics over the second half of the simulation, after the start-up transient
    int steps = severeSlug.getNumberOfTimeSteps();
    int first = steps / 2;
    double sum = 0;
    double max = Double.NEGATIVE_INFINITY;
    double min = Double.POSITIVE_INFINITY;
    for (int i = first; i < steps; i++) {
      sum = sum + severeSlug.resPres[i];
      max = Math.max(max, severeSlug.resPres[i]);
      min = Math.min(min, severeSlug.resPres[i]);
    }
    double meanValue = sum / (steps - first);
    // Half the relative peak-to-peak swing. For a symmetric oscillation this equals max/mean - 1, but it does not
    // under-report the long build-up / short blow-out cycles that are typical for severe slugging.
    slugValue = 0.5 * (max - min) / meanValue;
    pressureAmplitude = max - min;
    meanPressure = meanValue;
    double stratifiedHoldUp = stratifiedHoldUp(fluid, pipe, severeSlug);
    logger.debug(stratifiedHoldUp);

    double slugHoldUp = slugHoldUp(pipe, severeSlug);
    logger.debug(slugHoldUp);
    logger.debug("The severe slug value is " + slugValue);
    if (slugValue > 0.1 && slugHoldUp > stratifiedHoldUp) {
      flowPattern = "Severe Slug";
    } else if (slugValue > 0.05 && slugHoldUp > stratifiedHoldUp) {
      flowPattern = "Severe Slug 2. Small pressure variations";
    } else if (slugHoldUp <= stratifiedHoldUp) {
      flowPattern = "Slug Flow";
    } else if (stratifiedHoldUp < 0.1) {
      flowPattern = "Liquid droplets flow";
    } else if (stratifiedHoldUp > 0.9) {
      flowPattern = "Gas droplets flow";
    } else {
      flowPattern = "Stratified Flow";
    }
    logger.debug("Simulated flow regime is then: " + flowPattern);
    return flowPattern;
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (!unit.equalsIgnoreCase("m3/sec")) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "getMeasuredValue", "unit",
          "currently only supports \"m3/sec\""));
    }

    SystemInterface fluid = streamS.getThermoSystem();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();
    if (!isSevereSlugGeometryAndFluid(fluid)) {
      slugValue = 0.0;
      pressureAmplitude = 0.0;
      return slugValue;
    }
    fluidSevereS = new FluidSevereSlug(fluid);
    updateSuperficialVelocities(fluid, pipe);
    severeSlug = createWorker("tmp");
    checkFlowRegime(fluidSevereS, pipe, severeSlug);
    return slugValue;
  }

  /**
   * getMeasuredValue.
   *
   * @param fluid a {@link neqsim.process.measurementdevice.simpleflowregime.FluidSevereSlug} object
   * @param pipe a {@link neqsim.process.measurementdevice.simpleflowregime.Pipe} object
   * @param severeSlug a {@link neqsim.process.measurementdevice.simpleflowregime.SevereSlugAnalyser} object
   * @return a double
   */
  public double getMeasuredValue(FluidSevereSlug fluid, Pipe pipe, SevereSlugAnalyser severeSlug) {
    checkFlowRegime(fluid, pipe, severeSlug);
    return slugValue;
  }

  /**
   * getPredictedFlowRegime.
   *
   * @return a {@link java.lang.String} object
   */
  public String getPredictedFlowRegime() {
    SystemInterface fluid = streamS.getThermoSystem();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();
    if (!hasGasAndLiquid(fluid)) {
      flowPattern = "Single Phase";
    } else if (pipe.getAngle("Radian") <= 0.0) {
      flowPattern = "Regime cannot be detected (angle < or = 0.0). Severe slug is not possible";
    } else {
      fluidSevereS = new FluidSevereSlug(fluid);
      updateSuperficialVelocities(fluid, pipe);
      severeSlug = createWorker("tmp");
      checkFlowRegime(fluidSevereS, pipe, severeSlug);
    }
    return flowPattern;
  }

  /**
   * Check that the flashed fluid has a gas phase and at least one liquid phase.
   *
   * @param fluid flashed fluid
   * @return true when gas and liquid are both present
   */
  private static boolean hasGasAndLiquid(SystemInterface fluid) {
    return fluid.hasPhaseType("gas") && (fluid.hasPhaseType("oil") || fluid.hasPhaseType("aqueous"));
  }

  /**
   * Check that the lumped model can be run: gas and liquid present and a downward-inclined flowline.
   *
   * @param fluid flashed fluid
   * @return true when the severe-slug simulation is applicable
   */
  private boolean isSevereSlugGeometryAndFluid(SystemInterface fluid) {
    return hasGasAndLiquid(fluid) && pipe.getAngle("Radian") > 0.0;
  }

  /**
   * getPredictedFlowRegime.
   *
   * @param fluid a {@link neqsim.process.measurementdevice.simpleflowregime.FluidSevereSlug} object
   * @param pipe a {@link neqsim.process.measurementdevice.simpleflowregime.Pipe} object
   * @param severeSlug a {@link neqsim.process.measurementdevice.simpleflowregime.SevereSlugAnalyser} object
   * @return a {@link java.lang.String} object
   */
  public String getPredictedFlowRegime(FluidSevereSlug fluid, Pipe pipe, SevereSlugAnalyser severeSlug) {
    checkFlowRegime(fluid, pipe, severeSlug);
    return flowPattern;
  }

  /**
   * main.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 15.0), 10);
    testSystem.addComponent("methane", 0.015, "MSm^3/day");
    testSystem.addComponent("n-heptane", 0.0055, "MSm^3/day");
    testSystem.setMixingRule(2);
    testSystem.init(0);
    Stream inputStream = new Stream("inputStream", testSystem);
    SevereSlugAnalyser mySevereSlug4 = new SevereSlugAnalyser("tmp", inputStream, 0.05, 167, 7.7, 2, 100000.0, 20.0,
        200.0, 20000);
    logger.debug(inputStream.getFlowRate("kg/sec"));
    mySevereSlug4.getPredictedFlowRegime();
    // inputStream.setFlowRate(0.00001, "MSm^3/day");
    // System.out.println(inputStream.getFlowRate("kg/sec"));
    // mySevereSlug4.getPredictedFlowRegime();
  }

  // To be implemented
  // public void buildFlowMap(double ugmax, double ulmax, int numberOfStepsMap, FluidSevereSlug
  // fluid, Pipe pipe, SevereSlug severeSlug){
  // String stability1;
  // String stability2;
  // for (double usl = 0.01; usl < ulmax; usl = usl + ulmax/numberOfStepsMap) {
  // severeSlug.setSuperficialLiquidVelocity(usl);
  // double usg1 = 0.01;
  // double usg2 = ugmax;
  // double usg_sol;
  // iter = 0;

  // while(Math.abs(usg1 - usg2) > 1e-5 && iter < 200){
  // severeSlug.setSuperficialGasVelocity(usg1);
  // severeSlug.checkFlowRegime(fluid, pipe, severeSlug);
  // function1 = severeSlug.slugValue - 0.05;

  // severeSlug.setSuperficialGasVelocity(usg2);
  // checkFlowRegime(fluid, pipe, severeSlug);
  // function2 = severeSlug.slugValue - 0.05;

  // usg_sol = usg2 - function2*(usg2 - usg1)/(function2 - function1);

  // if (usg_sol < 0){
  // if (usg2 != 0.01)
  // {
  // usg2 = 0.01;
  // }
  // else{
  // usg2 = ugmax/2;
  // }
  // }
  // if (usg_sol > ugmax){
  // if (usg_sol != ugmax-0.01)
  // {
  // usg2 = ugmax-0.01;
  // }
  // else{
  // usg2 = 0.02;
  // }
  // }

  // usg1 = usg2;
  // usg2 = usg;
  // iter = iter + 1;
  // }

  // if (iter == 199){
  // System.out.println("Could not find the border");
  // }
  // else{
  // }

  // severeSlug.setSuperficialLiquidVelocity(usl);
  // severeSlug.setSuperficialGasVelocity(0.01);
  // String flowPattern1 = severeSlug.checkFlowRegime(fluid, pipe, severeSlug);
  // severeSlug.setSuperficialGasVelocity(ugmax);
  // String flowPattern2 = severeSlug.checkFlowRegime(fluid, pipe, severeSlug);
  // if (flowPattern1 == "Severe Slug" || flowPattern1 == "Severe Slug 2. Small pressure
  // variations"){
  // stability1 = "Not stable";
  // }
  // else{
  // stability1 = "Stable";
  // }
  // if (flowPattern2 == "Severe Slug" || flowPattern2 == "Severe Slug 2. Small pressure
  // variations"){
  // stability2 = "Not stable";
  // }
  // else{
  // stability2 = "Stable";
  // }
  // if (flowPattern1 != flowPattern2){
  // }
  // }
  // }
}
