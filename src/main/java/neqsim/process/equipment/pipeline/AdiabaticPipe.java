package neqsim.process.equipment.pipeline;

import java.util.UUID;
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * AdiabaticPipe class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class AdiabaticPipe extends Pipeline {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double inletPressure = 0;
  boolean setTemperature = false;

  boolean setPressureOut = false;

  protected double temperatureOut = 270;

  protected double pressureOut = 0.0;

  double length = 100.0;
  double insideDiameter = 0.1;
  double velocity = 1.0;
  double pipeWallRoughness = 1e-5;
  private double inletElevation = 0;
  private double outletElevation = 0;
  double dH = 0.0;
  String flowPattern = "unknown";
  String pipeSpecification = "AP02";

  /**
   * Constructor for AdiabaticPipe.
   *
   * @param name name of pipe
   */
  public AdiabaticPipe(String name) {
    super(name);
  }

  /**
   * Constructor for AdiabaticPipe.
   *
   * @param name name of pipe
   * @param inStream input stream
   */
  public AdiabaticPipe(String name, StreamInterface inStream) {
    this(name);
    this.inStream = inStream;
    outStream = inStream.clone();
  }

  /**
   * <p>
   * Setter for the field <code>pipeSpecification</code>.
   * </p>
   *
   * @param nominalDiameter a double
   * @param pipeSec a {@link java.lang.String} object
   */
  public void setPipeSpecification(double nominalDiameter, String pipeSec) {
    pipeSpecification = pipeSec;
    insideDiameter = nominalDiameter / 1000.0;
  }

  /**
   * <p>
   * setOutTemperature.
   * </p>
   *
   * @param temperature a double
   */
  public void setOutTemperature(double temperature) {
    setTemperature = true;
    this.temperatureOut = temperature;
  }

  /**
   * <p>
   * setOutPressure.
   * </p>
   *
   * @param pressure a double
   */
  public void setOutPressure(double pressure) {
    setPressureOut = true;
    this.pressureOut = pressure;
  }

  /**
   * <p>
   * calcWallFrictionFactor.
   * </p>
   *
   * @param reynoldsNumber a double
   * @return a double
   */
  public double calcWallFrictionFactor(double reynoldsNumber) {
    if (Math.abs(reynoldsNumber) < 1e-10) {
      flowPattern = "no-flow";
      return 0.0;
    }
    double relativeRoughnes = getPipeWallRoughness() / insideDiameter;
    if (Math.abs(reynoldsNumber) < 2300) {
      flowPattern = "laminar";
      return 64.0 / reynoldsNumber;
    } else if (Math.abs(reynoldsNumber) < 4000) {
      // Transition zone - interpolate between laminar and turbulent
      flowPattern = "transition";
      double fLaminar = 64.0 / 2300.0;
      double fTurbulent = Math.pow(
          (1.0 / (-1.8 * Math.log10(6.9 / 4000.0 + Math.pow(relativeRoughnes / 3.7, 1.11)))), 2.0);
      return fLaminar + (fTurbulent - fLaminar) * (reynoldsNumber - 2300.0) / 1700.0;
    } else {
      flowPattern = "turbulent";
      return Math.pow((1.0
          / (-1.8 * Math.log10(6.9 / reynoldsNumber + Math.pow(relativeRoughnes / 3.7, 1.11)))),
          2.0);
    }
  }

  /**
   * <p>
   * calcPressureOut.
   * </p>
   *
   * @return a double
   */
  public double calcPressureOut() {
    double area = Math.PI / 4.0 * Math.pow(insideDiameter, 2.0);
    velocity = system.getPhase(0).getTotalVolume() / area / 1.0e5;
    double reynoldsNumber = velocity * insideDiameter
        / system.getPhase(0).getPhysicalProperties().getKinematicViscosity();
    double frictionFactor = calcWallFrictionFactor(reynoldsNumber);
    double dp = Math
        .pow(4.0 * system.getPhase(0).getNumberOfMolesInPhase() * system.getPhase(0).getMolarMass()
            / neqsim.thermo.ThermodynamicConstantsInterface.pi, 2.0)
        * frictionFactor * length * system.getPhase(0).getZ()
        * neqsim.thermo.ThermodynamicConstantsInterface.R / system.getPhase(0).getMolarMass()
        * system.getTemperature() / Math.pow(insideDiameter, 5.0);
    // \\System.out.println("friction fact" + frictionFactor + " velocity " +
    // velocity + " reynolds number " + reynoldsNumber);
    // System.out.println("dp gravity "
    // + system.getDensity("kg/m3") * neqsim.thermo.ThermodynamicConstantsInterface.gravity
    // * (inletElevation - outletElevation) / 1.0e5);
    double dp_gravity =
        system.getDensity("kg/m3") * neqsim.thermo.ThermodynamicConstantsInterface.gravity
            * (inletElevation - outletElevation);
    return Math.sqrt(Math.pow(inletPressure * 1e5, 2.0) - dp) / 1.0e5 + dp_gravity / 1.0e5;
  }

  /**
   * <p>
   * calcFlow.
   * </p>
   * 
   * <p>
   * Calculates the flow rate required to achieve the specified outlet pressure using bisection
   * iteration. This method iteratively adjusts the flow rate until the calculated outlet pressure
   * matches the target outlet pressure (pressureOut).
   * </p>
   *
   * @return the calculated flow rate in the current system units
   */
  public double calcFlow() {
    // Use bisection method to find flow rate that gives target outlet pressure
    // At low flow, pressure drop is small -> outlet pressure high
    // At high flow, pressure drop is large -> outlet pressure low

    double originalFlowRate = system.getFlowRate("kg/sec");
    if (originalFlowRate <= 0) {
      originalFlowRate = 1.0; // Default starting point
    }

    // Set up bounds for bisection
    double flowLow = originalFlowRate * 0.001;
    double flowHigh = originalFlowRate * 100.0;

    // Find valid bounds
    // At low flow, check if outlet pressure > target (we need more flow)
    system.setTotalFlowRate(flowLow, "kg/sec");
    system.init(3);
    system.initPhysicalProperties();
    double pOutLow = calcPressureOut();

    // At high flow, check if outlet pressure < target (we need less flow)
    system.setTotalFlowRate(flowHigh, "kg/sec");
    system.init(3);
    system.initPhysicalProperties();
    double pOutHigh = calcPressureOut();

    // Expand bounds if needed
    int boundIter = 0;
    while (pOutLow < pressureOut && boundIter < 20) {
      flowLow /= 10.0;
      system.setTotalFlowRate(flowLow, "kg/sec");
      system.init(3);
      system.initPhysicalProperties();
      pOutLow = calcPressureOut();
      boundIter++;
    }

    boundIter = 0;
    while (pOutHigh > pressureOut && boundIter < 20) {
      flowHigh *= 10.0;
      system.setTotalFlowRate(flowHigh, "kg/sec");
      system.init(3);
      system.initPhysicalProperties();
      pOutHigh = calcPressureOut();
      boundIter++;
    }

    // Bisection iteration
    double flowMid = 0;
    double pOutMid = 0;
    int maxIter = 50;
    double tolerance = 1e-4;

    for (int i = 0; i < maxIter; i++) {
      flowMid = (flowLow + flowHigh) / 2.0;
      system.setTotalFlowRate(flowMid, "kg/sec");
      system.init(3);
      system.initPhysicalProperties();
      pOutMid = calcPressureOut();

      double relError = Math.abs(pOutMid - pressureOut) / pressureOut;
      if (relError < tolerance) {
        break;
      }

      if (pOutMid > pressureOut) {
        // Need more pressure drop -> increase flow
        flowLow = flowMid;
      } else {
        // Need less pressure drop -> decrease flow
        flowHigh = flowMid;
      }

      // Check convergence of bounds
      if (Math.abs(flowHigh - flowLow) / flowMid < tolerance) {
        break;
      }
    }

    return system.getFlowRate("kg/sec");
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    system = inStream.getThermoSystem().clone();
    inletPressure = system.getPressure();
    // system.setMultiPhaseCheck(true);
    if (setTemperature) {
      system.setTemperature(this.temperatureOut);
    }

    double oldPressure = 0.0;
    int iter = 0;
    if (!setPressureOut) {
      do {
        iter++;
        oldPressure = system.getPressure();
        system.init(3);
        system.initPhysicalProperties();
        system.setPressure(calcPressureOut());
      } while (Math.abs(system.getPressure() - oldPressure) > 1e-2 && iter < 25);
    } else {
      calcFlow();
      system.setPressure(pressureOut);
      system.init(3);
    }
    ThermodynamicOperations testOps = new ThermodynamicOperations(system);
    testOps.TPflash();
    if (setPressureOut) {
      inStream.getThermoSystem().setTotalFlowRate(system.getFlowRate("kg/sec"), "kg/sec");
    }
    // system.setMultiPhaseCheck(false);
    outStream.setThermoSystem(system);
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    system.display();
  }

  /** {@inheritDoc} */
  @Override
  public FlowSystemInterface getPipe() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void setInitialFlowPattern(String flowPattern) {}

  /**
   * <p>
   * Getter for the field <code>length</code>.
   * </p>
   *
   * @return the length
   */
  public double getLength() {
    return length;
  }

  /**
   * <p>
   * Setter for the field <code>length</code>.
   * </p>
   *
   * @param length the length to set
   */
  public void setLength(double length) {
    this.length = length;
  }

  /**
   * <p>
   * getDiameter.
   * </p>
   *
   * @return the diameter
   */
  public double getDiameter() {
    return insideDiameter;
  }

  /**
   * <p>
   * setDiameter.
   * </p>
   *
   * @param diameter the diameter to set
   */
  public void setDiameter(double diameter) {
    this.insideDiameter = diameter;
  }

  /**
   * <p>
   * Getter for the field <code>pipeWallRoughness</code>.
   * </p>
   *
   * @return the pipeWallRoughness
   */
  public double getPipeWallRoughness() {
    return pipeWallRoughness;
  }

  /**
   * <p>
   * Setter for the field <code>pipeWallRoughness</code>.
   * </p>
   *
   * @param pipeWallRoughness the pipeWallRoughness to set
   */
  public void setPipeWallRoughness(double pipeWallRoughness) {
    this.pipeWallRoughness = pipeWallRoughness;
  }

  /**
   * <p>
   * Getter for the field <code>inletElevation</code>.
   * </p>
   *
   * @return the inletElevation
   */
  public double getInletElevation() {
    return inletElevation;
  }

  /**
   * <p>
   * Setter for the field <code>inletElevation</code>.
   * </p>
   *
   * @param inletElevation the inletElevation to set
   */
  public void setInletElevation(double inletElevation) {
    this.inletElevation = inletElevation;
  }

  /**
   * <p>
   * Getter for the field <code>outletElevation</code>.
   * </p>
   *
   * @return the outletElevation
   */
  public double getOutletElevation() {
    return outletElevation;
  }

  /**
   * <p>
   * Setter for the field <code>outletElevation</code>.
   * </p>
   *
   * @param outletElevation the outletElevation to set
   */
  public void setOutletElevation(double outletElevation) {
    this.outletElevation = outletElevation;
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param name an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] name) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 5.0), 220.00);
    testSystem.addComponent("methane", 24.0, "MSm^3/day");
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init(0);

    Stream stream_1 = new Stream("Stream1", testSystem);

    AdiabaticPipe pipe = new AdiabaticPipe("pipe1", stream_1);
    pipe.setLength(700000.0);
    pipe.setDiameter(0.7112);
    pipe.setPipeWallRoughness(5e-6);
    pipe.setOutPressure(112.0);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(pipe);
    operations.run();
    pipe.displayResult();
  }
}
