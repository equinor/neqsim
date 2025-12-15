package neqsim.process.equipment.pipeline;

import java.util.UUID;
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * AdiabaticTwoPhasePipe class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class AdiabaticTwoPhasePipe extends Pipeline {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double inletPressure = 0;
  boolean setTemperature = false;

  boolean setPressureOut = false;

  protected double temperatureOut = 270;

  protected double pressureOut = 0.0;

  private double pressureOutLimit = 0.0;
  double length = 100.0;

  double flowLimit = 1e20;

  String maxflowunit = "kg/hr";
  double insideDiameter = 0.1;
  double velocity = 1.0;
  double pipeWallRoughness = 1e-5;

  /** Elevation at pipe inlet in meters. */
  private double inletElevation = 0;
  /** Elevation at pipe outlet in meters. */
  private double outletElevation = 0;
  double dH = 0.0;
  String flowPattern = "unknown";
  String pipeSpecification = "AP02";

  /**
   * Constructor for AdiabaticTwoPhasePipe.
   *
   * @param name name of pipe
   */
  public AdiabaticTwoPhasePipe(String name) {
    super(name);
  }

  /**
   * Constructor for AdiabaticTwoPhasePipe.
   *
   * @param name name of pipe
   * @param inStream input stream
   */
  public AdiabaticTwoPhasePipe(String name, StreamInterface inStream) {
    super(name, inStream);
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

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return outStream.getThermoSystem();
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
    velocity = system.getFlowRate("m3/sec") / area;

    double reynoldsNumber = velocity * insideDiameter / system.getKinematicViscosity("m2/sec");
    double frictionFactor = calcWallFrictionFactor(reynoldsNumber);
    // double dp = 2.0 * frictionFactor * length * Math.pow(system.getFlowRate("kg/sec"), 2.0)
    // / insideDiameter / system.getDensity("kg/m3"); // * () *
    // neqsim.thermo.ThermodynamicConstantsInterface.R
    double dp = 6253000 * Math.pow(system.getFlowRate("kg/hr"), 2.0) * frictionFactor
        / Math.pow(insideDiameter * 1000, 5.0) / system.getDensity("kg/m3") / 100.0 * 1000.0
        * length;

    // / system.getMolarMass() * system.getTemperature() / Math.pow(insideDiameter,
    // 5.0);
    // \\System.out.println("friction fact" + frictionFactor + " velocity " +
    // velocity + " reynolds number " + reynoldsNumber);
    // System.out.println("dp gravity " +
    // system.getDensity("kg/m3")*neqsim.thermo.ThermodynamicConstantsInterface.gravity*(inletElevation-outletElevation)/1.0e5);
    double dp_gravity =
        system.getDensity("kg/m3") * neqsim.thermo.ThermodynamicConstantsInterface.gravity
            * (inletElevation - outletElevation);
    return (inletPressure * 1e5 - dp) / 1.0e5 + dp_gravity / 1.0e5;
  }

  /**
   * <p>
   * calcFlow.
   * </p>
   * 
   * <p>
   * Calculates the flow rate required to achieve the specified outlet pressure using bisection
   * iteration. This method iteratively adjusts the flow rate until the calculated outlet pressure
   * matches the target outlet pressure.
   * </p>
   *
   * @param pressureOut target outlet pressure in bara
   * @return the calculated flow rate in the current system units
   */
  public double calcFlow(double pressureOut) {
    // Use bisection method to find flow rate that gives target outlet pressure
    // At low flow, pressure drop is small -> outlet pressure high
    // At high flow, pressure drop is large -> outlet pressure low

    // CRITICAL: Set inletPressure from the system's pressure - calcPressureOut() uses this field
    inletPressure = system.getPressure();

    double originalFlowRate = system.getFlowRate("kg/sec");
    if (originalFlowRate <= 0) {
      originalFlowRate = 1.0; // Default starting point
    }

    ThermodynamicOperations testOps = new ThermodynamicOperations(system);

    // Set up bounds for bisection
    double flowLow = originalFlowRate * 0.001;
    double flowHigh = originalFlowRate * 100.0;

    // Find valid bounds
    // At low flow, check if outlet pressure > target (we need more flow)
    system.setTotalFlowRate(flowLow, "kg/sec");
    testOps.TPflash();
    system.initProperties();
    double pOutLow = calcPressureOut();

    // At high flow, check if outlet pressure < target (we need less flow)
    system.setTotalFlowRate(flowHigh, "kg/sec");
    testOps.TPflash();
    system.initProperties();
    double pOutHigh = calcPressureOut();

    // Expand bounds if needed
    int boundIter = 0;
    while (pOutLow < pressureOut && boundIter < 20) {
      flowLow /= 10.0;
      system.setTotalFlowRate(flowLow, "kg/sec");
      testOps.TPflash();
      system.initProperties();
      pOutLow = calcPressureOut();
      boundIter++;
    }

    boundIter = 0;
    while (pOutHigh > pressureOut && !Double.isNaN(pOutHigh) && boundIter < 20) {
      flowHigh *= 10.0;
      system.setTotalFlowRate(flowHigh, "kg/sec");
      testOps.TPflash();
      system.initProperties();
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
      testOps.TPflash();
      system.initProperties();
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

    ThermodynamicOperations testOps = new ThermodynamicOperations(system);
    testOps.TPflash();

    double oldPressure = 0.0;
    int iter = 0;
    if (!setPressureOut) {
      if (system.getFlowRate(maxflowunit) > flowLimit) {
        system.setTotalFlowRate(flowLimit, maxflowunit);
        testOps = new ThermodynamicOperations(system);
        testOps.TPflash();
      }

      system.initProperties();
      double outP = calcPressureOut();
      if (outP < 1e-10 || Double.isNaN(outP)) {
        system.setPressure(0.001);
        logger.debug("pressure too low in pipe....");
      }
      system.setPressure(outP);
      testOps = new ThermodynamicOperations(system);
      testOps.TPflash();

      if (system.getPressure() < pressureOutLimit) {
        iter = 0;
        outP = system.getPressure();
        do {
          iter++;
          oldPressure = system.getNumberOfMoles();
          system.setTotalNumberOfMoles(system.getNumberOfMoles() * outP / pressureOutLimit);

          // System.out.println("new moles " +
          // system.getNumberOfMoles() + " outP "+ outP);
          // outP = calcPressureOut();
          // System.out.println("out P " + outP + " oldP " + oldPressure);
          testOps = new ThermodynamicOperations(system);
          testOps.TPflash();
          system.initProperties();

          outP = calcPressureOut();
          system.setPressure(outP);

          if (outP < 1e-10 || Double.isNaN(outP)) {
            break;
          }
        } while (Math.abs(system.getNumberOfMoles() - oldPressure) / oldPressure > 1e-3
            && iter < 3);
        // calcFlow(pressureOutLimit);
        // System.out.println("new moles " +
        // system.getNumberOfMoles()*system.getPressure()/pressureOutLimit);
        // System.out.println("flow " + system.getFlowRate(maxflowunit));
      }
      if (system.getFlowRate(maxflowunit) > flowLimit) {
        system.setTotalFlowRate(flowLimit, maxflowunit);
        system.init(1);
      }
      // SetTotalFlowRate resets beta factors, but they are fixed in run below
      inStream.getThermoSystem().setTotalFlowRate(system.getFlowRate(maxflowunit), maxflowunit);
      inStream.run(id);
    } else {
      calcFlow(pressureOut);
      inStream.getThermoSystem().setTotalFlowRate(system.getFlowRate("kg/sec"), "kg/sec");
      system.setPressure(pressureOut);
      system.init(3);
      if (system.getFlowRate(maxflowunit) > flowLimit) {
        system.setTotalFlowRate(flowLimit, maxflowunit);
        inStream.getThermoSystem().setTotalFlowRate(flowLimit, maxflowunit);
        iter = 0;
        do {
          iter++;
          oldPressure = system.getPressure();
          system.init(3);
          system.initPhysicalProperties();
          system.setPressure(calcPressureOut());
          testOps = new ThermodynamicOperations(system);
          testOps.TPflash();
        } while (Math.abs(system.getPressure() - oldPressure) > 1e-2 && iter < 25);
      }
    }
    testOps = new ThermodynamicOperations(system);
    testOps.TPflash();
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

  /**
   * <p>
   * getSuperficialVelocity.
   * </p>
   *
   * @return a double
   */
  public double getSuperficialVelocity() {
    return getInletStream().getThermoSystem().getFlowRate("kg/sec")
        / getInletStream().getThermoSystem().getDensity("kg/m3")
        / (Math.PI / 4.0 * Math.pow(insideDiameter, 2.0));
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
    insideDiameter = diameter;
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
   * Getter for the field <code>pressureOutLimit</code>.
   * </p>
   *
   * @return a double
   */
  public double getPressureOutLimit() {
    return pressureOutLimit;
  }

  /**
   * <p>
   * Setter for the field <code>pressureOutLimit</code>.
   * </p>
   *
   * @param pressureOutLimit a double
   */
  public void setPressureOutLimit(double pressureOutLimit) {
    this.pressureOutLimit = pressureOutLimit;
  }

  /**
   * <p>
   * Setter for the field <code>flowLimit</code>.
   * </p>
   *
   * @param flowLimit a double
   * @param unit a {@link java.lang.String} object
   */
  public void setFlowLimit(double flowLimit, String unit) {
    this.flowLimit = flowLimit;
    maxflowunit = unit;
  }
}
