package neqsim.process.measurementdevice.simpleflowregime;

import java.util.Arrays;
import java.util.Collections;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SevereSlugAnalyser class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class SevereSlugAnalyser extends MeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SevereSlugAnalyser.class);

  FluidSevereSlug fluidSevereS;
  Pipe pipe;
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
  final double pi = neqsim.thermo.ThermodynamicConstantsInterface.pi;

  double[] resPres;
  double[] resTime;
  double[] resLiqHoldUpRiser;
  double[] resLiqHeight;
  double[] resMixVelocity;
  double[] usgMap;
  double[] uslMap;
  double slugValue;

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
  public SevereSlugAnalyser(String name, double outletPressure, double temperature,
      double simulationTime, int numberOfTimeSteps) {
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
  public SevereSlugAnalyser(String name, SystemInterface fluid, Pipe pipe, double outletPressure,
      double temperature, double simulationTime, int numberOfTimeSteps) {
    this(name, outletPressure, temperature, simulationTime, numberOfTimeSteps);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();

    if (fluid.getNumberOfPhases() == 2) {
      usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea();
    } else {
      usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea()
          + fluid.getPhase(2).getFlowRate("m3/sec") / pipe.getArea();
    }
    usg = fluid.getPhase(0).getFlowRate("m3/sec") / pipe.getArea();
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
  public SevereSlugAnalyser(String name, Stream stream, double internalDiameter, double leftLength,
      double rightLength, double angle) {
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
  public SevereSlugAnalyser(String name, Stream stream, double internalDiameter, double leftLength,
      double rightLength, double angle, double simulationTime, int numberOfTimeSteps) {
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
  public SevereSlugAnalyser(String name, Stream stream, double internalDiameter, double leftLength,
      double rightLength, double angle, double outletPressure, double temperature,
      double simulationTime, int numberOfTimeSteps) {
    this(name);
    pipe = new Pipe("slugpipe", internalDiameter, leftLength, rightLength, angle);
    streamS = stream;
    SystemInterface fluid = stream.getThermoSystem();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();
    if (fluid.getNumberOfPhases() == 2) {
      usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea();
    } else {
      usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea()
          + fluid.getPhase(2).getFlowRate("m3/sec") / pipe.getArea();
    }
    fluidSevereS = new FluidSevereSlug(fluid);
    usg = fluid.getPhase(0).getFlowRate("m3/sec") / pipe.getArea();

    severeSlug = new SevereSlugAnalyser(name, usl, usg, outletPressure, temperature, simulationTime,
        numberOfTimeSteps);
  }

  /**
   * <p>
   * setSuperficialLiquidVelocity.
   * </p>
   *
   * @param usl a double
   */
  public void setSuperficialLiquidVelocity(double usl) {
    this.usl = usl;
  }

  /**
   * <p>
   * getSuperficialLiquidVelocity.
   * </p>
   *
   * @return a double
   */
  public double getSuperficialLiquidVelocity() {
    return usl;
  }

  /**
   * <p>
   * setSuperficialGasVelocity.
   * </p>
   *
   * @param usg a double
   */
  public void setSuperficialGasVelocity(double usg) {
    this.usg = usg;
  }

  /**
   * <p>
   * getSuperficialGasVelocity.
   * </p>
   *
   * @return a double
   */
  public double getSuperficialGasVelocity() {
    return usg;
  }

  /**
   * <p>
   * Getter for the field <code>flowPattern</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getFlowPattern() {
    return flowPattern;
  }

  /**
   * <p>
   * Getter for the field <code>slugValue</code>.
   * </p>
   *
   * @return a double
   */
  public double getSlugValue() {
    return slugValue;
  }

  // 3. Pipe Outlet Pressure Encapsulation
  /**
   * <p>
   * Setter for the field <code>outletPressure</code>.
   * </p>
   *
   * @param outletPressure Value in engineering unit Pa
   */
  public void setOutletPressure(double outletPressure) {
    this.outletPressure = outletPressure;
  }

  /**
   * <p>
   * Getter for the field <code>outletPressure</code>.
   * </p>
   *
   * @return Get pressure in Pa
   */
  public double getOutletPressure() {
    return outletPressure;
  }

  /**
   * <p>
   * Setter for the field <code>temperature</code>.
   * </p>
   *
   * @param temperature a double
   */
  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }

  /**
   * <p>
   * Getter for the field <code>temperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * <p>
   * Setter for the field <code>numberOfTimeSteps</code>.
   * </p>
   *
   * @param numberOfTimeSteps a int
   */
  public void setNumberOfTimeSteps(int numberOfTimeSteps) {
    this.numberOfTimeSteps = numberOfTimeSteps;
  }

  /**
   * <p>
   * Getter for the field <code>numberOfTimeSteps</code>.
   * </p>
   *
   * @return a int
   */
  public int getNumberOfTimeSteps() {
    return numberOfTimeSteps;
  }

  /**
   * <p>
   * Setter for the field <code>simulationTime</code>.
   * </p>
   *
   * @param simulationTime a double
   */
  public void setSimulationTime(double simulationTime) {
    this.simulationTime = simulationTime;
  }

  /**
   * <p>
   * Getter for the field <code>simulationTime</code>.
   * </p>
   *
   * @return a double
   */
  public double getSimulationTime() {
    return simulationTime;
  }

  /**
   * <p>
   * Calculating the universal gas constant.
   * </p>
   *
   * @param fluid a {@link neqsim.process.measurementdevice.simpleflowregime.FluidSevereSlug} object
   * @return a double
   */
  public double gasConst(FluidSevereSlug fluid) {
    return ThermodynamicConstantsInterface.R / fluid.getMolecularWeight() * (273.15 + temperature);
  }

  // Declare the variables for results after creating an object Severe slug with required number of
  // steps.

  /**
   * <p>
   * slugHoldUp.
   * </p>
   *
   * @param pipe a {@link neqsim.process.measurementdevice.simpleflowregime.Pipe} object
   * @param severeSlug a
   *        {@link neqsim.process.measurementdevice.simpleflowregime.SevereSlugAnalyser} object
   * @return a double
   */
  public double slugHoldUp(Pipe pipe, SevereSlugAnalyser severeSlug) {
    double Udrift;
    double C0 = 1.2;
    double Umix;

    Umix = severeSlug.getSuperficialGasVelocity() + severeSlug.getSuperficialLiquidVelocity();
    Udrift = Math.sqrt(gravAcc * pipe.getInternalDiameter());
    holdUp = 1 - severeSlug.getSuperficialGasVelocity() / (C0 * Umix + Udrift);
    return holdUp;
  }

  /**
   * <p>
   * stratifiedHoldUp.
   * </p>
   *
   * @param fluid a {@link neqsim.process.measurementdevice.simpleflowregime.FluidSevereSlug} object
   * @param pipe a {@link neqsim.process.measurementdevice.simpleflowregime.Pipe} object
   * @param severeSlug a
   *        {@link neqsim.process.measurementdevice.simpleflowregime.SevereSlugAnalyser} object
   * @return a double
   */
  public double stratifiedHoldUp(FluidSevereSlug fluid, Pipe pipe, SevereSlugAnalyser severeSlug) {
    Re = fluid.getLiqDensity() * severeSlug.getSuperficialLiquidVelocity()
        * pipe.getInternalDiameter() / (fluid.getliqVisc());
    lambda = Math.max(0.34 * Math.pow(Re, -0.25), 64 / Re);
    if (0.34 * Math.pow(Re, -0.25) > 64 / Re) {
      n = 0.25;
    } else {
      n = 1;
    }
    friction = 0.5 * lambda * Math.pow(severeSlug.getSuperficialLiquidVelocity(), 2)
        / (gravAcc * Math.sin(pipe.getAngle("Radian")) * pipe.getInternalDiameter());

    gamma1 = 0.1;
    gamma2 = 2.2;
    iter = 0;
    while (Math.abs(gamma2 - gamma1) > 1e-5 && iter < 200) {
      holdUp2 = (gamma2 - 0.5 * Math.sin(2 * gamma2)) / (pi);
      function2 = Math.pow(holdUp2, 3) * Math.pow((pi / gamma2), (n + 1)) - friction;

      holdUp1 = (gamma1 - 0.5 * Math.sin(2 * gamma1)) / (pi);
      function1 = Math.pow(holdUp1, 3) * Math.pow((pi / gamma1), (n + 1)) - friction;

      gamma = gamma2 - function2 * (gamma2 - gamma1) / (function2 - function1);
      if (gamma < 0) {
        if (gamma2 != 0.1) {
          gamma = 0.1;
        } else {
          gamma = 0.2;
        }
      }
      if (gamma > 3.00) {
        if (gamma2 != 2.99) {
          gamma = 2.99;
        } else {
          gamma = 2.97;
        }
      }

      gamma1 = gamma2;
      gamma2 = gamma;
      iter = iter + 1;
    }

    if (iter == 199) {
      logger.debug("Could not find solution for stratified flow holdup");
    } else {
      holdUp = (gamma - 0.5 * Math.sin(2 * gamma)) / (pi);
    }
    return holdUp;
  }

  // Passing 3 objects as input parameters (fluid, pipe, severeSlug)
  /**
   * <p>
   * runSevereSlug.
   * </p>
   *
   * @param fluid a {@link neqsim.process.measurementdevice.simpleflowregime.FluidSevereSlug} object
   * @param pipe a {@link neqsim.process.measurementdevice.simpleflowregime.Pipe} object
   * @param severeSlug a
   *        {@link neqsim.process.measurementdevice.simpleflowregime.SevereSlugAnalyser} object
   */
  public void runSevereSlug(FluidSevereSlug fluid, Pipe pipe, SevereSlugAnalyser severeSlug) {
    resPres = new double[severeSlug.getNumberOfTimeSteps()];
    resTime = new double[severeSlug.getNumberOfTimeSteps()];
    resLiqHoldUpRiser = new double[severeSlug.getNumberOfTimeSteps()];
    resLiqHeight = new double[severeSlug.getNumberOfTimeSteps()];
    resMixVelocity = new double[severeSlug.getNumberOfTimeSteps()];

    deltaT = 0.001; // severeSlug.getSimulationTime() / severeSlug.getNumberOfTimeSteps();
    mixDensity = fluid.getLiqDensity();
    // Initial condition
    pressure =
        severeSlug.getOutletPressure() + mixDensity * severeSlug.gravAcc * pipe.getRightLength();
    Um = severeSlug.getSuperficialGasVelocity();
    holdUp = severeSlug.stratifiedHoldUp(fluid, pipe, severeSlug);
    // Drift velocity for the vertical flows
    driftVel = 0.35 * Math.sqrt(gravAcc * pipe.getInternalDiameter());
    alfaLeft = 1 - holdUp;

    for (int i = 0; i < severeSlug.numberOfTimeSteps; i++) {
      slugLength = -z + pipe.getRightLength() * (1 - alfaRiser); // Slug Length
      // Reynolds number
      Re = fluid.getLiqDensity() * Math.abs(Um) * pipe.getInternalDiameter() / fluid.getliqVisc();
      lambda = Math.max(0.34 * Math.pow(Re, -0.25), 64 / Re); // friction factor
      friction = 0.5 * lambda * fluid.getLiqDensity() * Um * Math.abs(Um) * slugLength
          / pipe.getInternalDiameter(); // frictional pressure loss
      // Oscillation Friction
      frictionStagnant = 0.5 * lambdaStagnant * fluid.getLiqDensity() * uLevel * Math.abs(uLevel)
          * slugLength / pipe.getInternalDiameter();
      // Valve Friction
      frictionValve = valveConstant * fluid.getLiqDensity() * Um * Math.abs(Um);
      // Total Friction
      friction = friction + frictionStagnant + frictionValve;
      // Gravity
      gravL = -fluid.getLiqDensity() * Math.abs(z) * gravAcc * Math.sin((pipe.getAngle("Radian")));
      gravR = mixDensity * gravAcc * pipe.getRightLength();
      gravity = gravL + gravR;

      // Momentum Balance
      UmOld = Um;
      Um = UmOld + deltaT * ((pressure - severeSlug.outletPressure) - friction - gravity)
          / (-z * fluid.getLiqDensity() + pipe.getRightLength() * mixDensity);

      // Slip Relation: Calculate translational velocity
      if (Re < 2300) {
        flowDistCoeff = 2;
      } else {
        flowDistCoeff = 1.2;
      }

      transVel = flowDistCoeff * Um + driftVel;
      // State Equation
      // All cases, Case 1: Open Bend; Case 2: Blocked Bend; Case 3: Backflow
      UsgL =
          (Um - severeSlug.getSuperficialLiquidVelocity()) * ((Um > 0) ? 1 : 0) * ((z > 0) ? 1 : 0);

      UslL =
          (severeSlug.getSuperficialLiquidVelocity() * ((z > 0) ? 1 : 0) + Um * ((z < 0) ? 1 : 0))
              * ((Um > 0) ? 1 : 0) + Um * ((Um < 0) ? 1 : 0);

      UsgR =
          (alfaRiser * transVel * ((z > 0) ? 1 : 0) + Um * ((z < 0) ? 1 : 0)) * ((Um > 0) ? 1 : 0)
              + Um * ((Um < 0) ? 1 : 0);
      UslR = (Um - alfaRiser * transVel) * ((Um > 0) ? 1 : 0) * ((z > 0) ? 1 : 0);

      U = UsgL - UsgR - UslL + UslR;

      // Riser vapour fraction
      alfaRiserOld = alfaRiser;
      alfaRiser = alfaRiserOld + 0.5 * deltaT * U / (pipe.getRightLength());
      alfaRiser = Math.max(0.0, alfaRiser);
      alfaRiser = Math.min(1.0, alfaRiser);

      // Level
      uLevel = (-severeSlug.getSuperficialLiquidVelocity() + Um);
      zOld = z;
      z = zOld + deltaT * uLevel;
      z = ((z < 0) ? 1 : 0) * z + ((z > 0) ? 1 : 0) * 0.0001;
      uLevel = uLevel * ((z < 0) ? 1 : 0);

      Lg = pipe.getLeftLength() + z;
      pressureOld = pressure;
      pressure = pressureOld
          + deltaT * (severeSlug.getSuperficialGasVelocity() * normalPressure - UsgL * pressureOld)
              / (Lg * alfaLeft)
          - deltaT * (pressureOld * uLevel) / (Lg * alfaLeft);

      gasDensity = pressure / (fluid.getGasConstant() * (273.15 + severeSlug.getTemperature()));
      mixDensity = alfaRiser * gasDensity + (1 - alfaRiser) * fluid.getLiqDensity();

      resPres[i] = pressure / 100000;
      resTime[i] = i * deltaT;
      resLiqHoldUpRiser[i] = (1 - alfaRiser);
      resLiqHeight[i] = z;
      resMixVelocity[i] = Um;
    }
  }

  /**
   * <p>
   * checkFlowRegime.
   * </p>
   *
   * @param fluid a {@link neqsim.process.measurementdevice.simpleflowregime.FluidSevereSlug} object
   * @param pipe a {@link neqsim.process.measurementdevice.simpleflowregime.Pipe} object
   * @param severeSlug a
   *        {@link neqsim.process.measurementdevice.simpleflowregime.SevereSlugAnalyser} object
   * @return a {@link java.lang.String} object
   */
  public String checkFlowRegime(FluidSevereSlug fluid, Pipe pipe, SevereSlugAnalyser severeSlug) {
    Double[] halfRes = new Double[severeSlug.getNumberOfTimeSteps() / 2];
    severeSlug.runSevereSlug(fluid, pipe, severeSlug);
    double sum = 0;
    for (int i = severeSlug.numberOfTimeSteps / 2; i < severeSlug.numberOfTimeSteps; i++) {
      sum = sum + severeSlug.resPres[i];
      halfRes[i - severeSlug.numberOfTimeSteps / 2] = severeSlug.resPres[i];
    }
    double meanValue = sum / ((double) numberOfTimeSteps / 2);
    double max = Collections.max(Arrays.asList(halfRes));
    slugValue = (max / meanValue) - 1;
    double stratifiedHoldUp = stratifiedHoldUp(fluid, pipe, severeSlug);
    logger.debug(stratifiedHoldUp);

    double slugHoldUp = slugHoldUp(pipe, severeSlug);
    logger.debug(slugHoldUp);
    logger.debug("The severe slug value is " + slugValue);
    if (slugValue > 0.1 && slugHoldUp > stratifiedHoldUp) {
      flowPattern = "Severe Slug";
    } else if (slugValue > 0.05 && slugHoldUp > stratifiedHoldUp) {
      flowPattern = "Severe Slug 2. Small pressure variations";
    } else {
      if (slugHoldUp < stratifiedHoldUp) {
        flowPattern = "Slug Flow";
      } else {
        if (stratifiedHoldUp < 0.1) {
          flowPattern = "Liquid droplets flow";
        }
        if (stratifiedHoldUp > 0.9) {
          flowPattern = "Gas droplets flow";
        }
        if (stratifiedHoldUp > 0.1 && stratifiedHoldUp < 0.9) {
          flowPattern = "Stratified Flow";
        }
      }
    }
    logger.debug("Simulated flow regime is then: " + flowPattern);
    return flowPattern;
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (!unit.equalsIgnoreCase("m3/sec")) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "getMeasuredValue", "unit", "currently only supports \"m3/sec\""));
    }

    SystemInterface fluid = streamS.getThermoSystem();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();
    if (fluid.getNumberOfPhases() == 2) {
      usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea();
    } else {
      usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea()
          + fluid.getPhase(2).getFlowRate("m3/sec") / pipe.getArea();
    }
    fluidSevereS = new FluidSevereSlug(fluid);
    usg = fluid.getPhase(0).getFlowRate("m3/sec") / pipe.getArea();
    severeSlug = new SevereSlugAnalyser("tmp", usl, usg, outletPressure, temperature,
        simulationTime, numberOfTimeSteps);
    checkFlowRegime(fluidSevereS, pipe, severeSlug);
    return slugValue;
  }

  /**
   * <p>
   * getMeasuredValue.
   * </p>
   *
   * @param fluid a {@link neqsim.process.measurementdevice.simpleflowregime.FluidSevereSlug} object
   * @param pipe a {@link neqsim.process.measurementdevice.simpleflowregime.Pipe} object
   * @param severeSlug a
   *        {@link neqsim.process.measurementdevice.simpleflowregime.SevereSlugAnalyser} object
   * @return a double
   */
  public double getMeasuredValue(FluidSevereSlug fluid, Pipe pipe, SevereSlugAnalyser severeSlug) {
    checkFlowRegime(fluid, pipe, severeSlug);
    return slugValue;
  }

  /**
   * <p>
   * getPredictedFlowRegime.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getPredictedFlowRegime() {
    SystemInterface fluid = streamS.getThermoSystem();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();
    if (fluid.getNumberOfPhases() == 1) {
      flowPattern = "Single Phase";
    } else if (pipe.getAngle("Radian") <= 0.0) {
      flowPattern = "Regime cannot be detected (angle < or = 0.0). Severe slug is not possible";
    } else {
      if (fluid.getNumberOfPhases() == 2) {
        usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea();
      } else {
        usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea()
            + fluid.getPhase(2).getFlowRate("m3/sec") / pipe.getArea();
      }
      fluidSevereS = new FluidSevereSlug(fluid);
      usg = fluid.getPhase(0).getFlowRate("m3/sec") / pipe.getArea();
      severeSlug = new SevereSlugAnalyser("tmp", usl, usg, outletPressure, temperature,
          simulationTime, numberOfTimeSteps);
      checkFlowRegime(fluidSevereS, pipe, severeSlug);
    }
    return flowPattern;
  }

  /**
   * <p>
   * getPredictedFlowRegime.
   * </p>
   *
   * @param fluid a {@link neqsim.process.measurementdevice.simpleflowregime.FluidSevereSlug} object
   * @param pipe a {@link neqsim.process.measurementdevice.simpleflowregime.Pipe} object
   * @param severeSlug a
   *        {@link neqsim.process.measurementdevice.simpleflowregime.SevereSlugAnalyser} object
   * @return a {@link java.lang.String} object
   */
  public String getPredictedFlowRegime(FluidSevereSlug fluid, Pipe pipe,
      SevereSlugAnalyser severeSlug) {
    checkFlowRegime(fluid, pipe, severeSlug);
    return flowPattern;
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 15.0), 10);
    testSystem.addComponent("methane", 0.015, "MSm^3/day");
    testSystem.addComponent("n-heptane", 0.0055, "MSm^3/day");
    testSystem.setMixingRule(2);
    testSystem.init(0);
    Stream inputStream = new Stream("inputStream", testSystem);
    SevereSlugAnalyser mySevereSlug4 =
        new SevereSlugAnalyser("tmp", inputStream, 0.05, 167, 7.7, 2, 100000.0, 20.0, 200.0, 20000);
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
