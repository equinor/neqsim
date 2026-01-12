package neqsim.process.equipment.pipeline;

import java.util.UUID;
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Single-phase adiabatic pipe model.
 *
 * <p>
 * This class models a simple adiabatic (no heat transfer) pipe for single-phase flow using basic
 * gas flow equations. It calculates pressure drop from friction and elevation changes.
 * </p>
 *
 * <h2>Calculation Modes</h2>
 * <ul>
 * <li><b>Calculate outlet pressure</b> - Given inlet conditions and flow rate</li>
 * <li><b>Calculate flow rate</b> - Given inlet and outlet pressures (when outlet pressure is
 * set)</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Create gas stream
 * SystemInterface gas = new SystemSrkEos(278.15, 220.0);
 * gas.addComponent("methane", 24.0, "MSm^3/day");
 * gas.setMixingRule("classic");
 *
 * Stream inlet = new Stream("inlet", gas);
 * inlet.run();
 *
 * // Create adiabatic pipe
 * AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inlet);
 * pipe.setLength(700000.0); // 700 km
 * pipe.setDiameter(0.7112); // ~28 inches
 * pipe.setPipeWallRoughness(5e-6);
 * pipe.run();
 *
 * System.out.println("Outlet pressure: " + pipe.getOutletPressure("bara") + " bara");
 * }</pre>
 *
 * @author Even Solbraa
 * @version 2.0
 */
public class AdiabaticPipe extends Pipeline {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double inletPressure = 0;
  boolean setTemperature = false;
  boolean setPressureOut = false;
  protected double temperatureOut = 270;
  protected double pressureOut = 0.0;
  double dH = 0.0;
  String pipeSpecification = "AP02";

  // Use parent's length, diameter, roughness, inletElevation, outletElevation
  // Override with local insideDiameter for backward compatibility
  double insideDiameter = 0.1;
  double pipeWallRoughnessLocal = 1e-5;

  /**
   * Constructor for AdiabaticPipe.
   *
   * @param name name of pipe
   */
  public AdiabaticPipe(String name) {
    super(name);
    this.adiabatic = true;
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

  /** {@inheritDoc} */
  @Override
  public void setLength(double length) {
    super.setLength(length);
  }

  /** {@inheritDoc} */
  @Override
  public double getLength() {
    return super.getLength();
  }

  /** {@inheritDoc} */
  @Override
  public void setDiameter(double diameter) {
    super.setDiameter(diameter);
    this.insideDiameter = diameter;
  }

  /** {@inheritDoc} */
  @Override
  public double getDiameter() {
    return insideDiameter;
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeWallRoughness(double roughness) {
    super.setPipeWallRoughness(roughness);
    this.pipeWallRoughnessLocal = roughness;
  }

  /** {@inheritDoc} */
  @Override
  public double getPipeWallRoughness() {
    return pipeWallRoughnessLocal;
  }

  /** {@inheritDoc} */
  @Override
  public void setInletElevation(double inletElevation) {
    super.setInletElevation(inletElevation);
  }

  /** {@inheritDoc} */
  @Override
  public double getInletElevation() {
    return super.getInletElevation();
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletElevation(double outletElevation) {
    super.setOutletElevation(outletElevation);
  }

  /** {@inheritDoc} */
  @Override
  public double getOutletElevation() {
    return super.getOutletElevation();
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeSpecification(double nominalDiameter, String pipeSec) {
    pipeSpecification = pipeSec;
    insideDiameter = nominalDiameter / 1000.0;
    super.setDiameter(insideDiameter);
  }

  /** {@inheritDoc} */
  @Override
  public void setOutTemperature(double temperature) {
    setTemperature = true;
    this.temperatureOut = temperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setOutPressure(double pressure) {
    setPressureOut = true;
    this.pressureOut = pressure;
  }

  /**
   * Calculate the wall friction factor using the Haaland equation.
   *
   * @param reynoldsNumber the Reynolds number
   * @return the Darcy friction factor
   */
  public double calcWallFrictionFactor(double reynoldsNumber) {
    if (Math.abs(reynoldsNumber) < 1e-10) {
      flowRegime = "no-flow";
      return 0.0;
    }
    double relativeRoughnes = getPipeWallRoughness() / insideDiameter;
    if (Math.abs(reynoldsNumber) < 2300) {
      flowRegime = "laminar";
      return 64.0 / reynoldsNumber;
    } else if (Math.abs(reynoldsNumber) < 4000) {
      // Transition zone - interpolate between laminar and turbulent
      flowRegime = "transition";
      double fLaminar = 64.0 / 2300.0;
      double fTurbulent = Math.pow(
          (1.0 / (-1.8 * Math.log10(6.9 / 4000.0 + Math.pow(relativeRoughnes / 3.7, 1.11)))), 2.0);
      return fLaminar + (fTurbulent - fLaminar) * (reynoldsNumber - 2300.0) / 1700.0;
    } else {
      flowRegime = "turbulent";
      return Math.pow((1.0
          / (-1.8 * Math.log10(6.9 / reynoldsNumber + Math.pow(relativeRoughnes / 3.7, 1.11)))),
          2.0);
    }
  }

  /**
   * Calculate the outlet pressure based on friction and elevation losses.
   *
   * @return the outlet pressure in bara
   */
  public double calcPressureOut() {
    double area = Math.PI / 4.0 * Math.pow(insideDiameter, 2.0);
    velocity = system.getPhase(0).getTotalVolume() / area / 1.0e5;
    reynoldsNumber = velocity * insideDiameter
        / system.getPhase(0).getPhysicalProperties().getKinematicViscosity();
    frictionFactor = calcWallFrictionFactor(reynoldsNumber);
    double dp = Math
        .pow(4.0 * system.getPhase(0).getNumberOfMolesInPhase() * system.getPhase(0).getMolarMass()
            / neqsim.thermo.ThermodynamicConstantsInterface.pi, 2.0)
        * frictionFactor * length * system.getPhase(0).getZ()
        * neqsim.thermo.ThermodynamicConstantsInterface.R / system.getPhase(0).getMolarMass()
        * system.getTemperature() / Math.pow(insideDiameter, 5.0);
    double dp_gravity =
        system.getDensity("kg/m3") * neqsim.thermo.ThermodynamicConstantsInterface.gravity
            * (inletElevation - outletElevation);
    return Math.sqrt(Math.pow(inletPressure * 1e5, 2.0) - dp) / 1.0e5 + dp_gravity / 1.0e5;
  }

  /**
   * Calculate the flow rate required to achieve the specified outlet pressure.
   *
   * <p>
   * Uses bisection iteration to find the flow rate that achieves the target outlet pressure.
   * </p>
   *
   * @return the calculated flow rate in the current system units
   */
  public double calcFlow() {
    double originalFlowRate = system.getFlowRate("kg/sec");
    if (originalFlowRate <= 0) {
      originalFlowRate = 1.0;
    }

    double flowLow = originalFlowRate * 0.001;
    double flowHigh = originalFlowRate * 100.0;

    system.setTotalFlowRate(flowLow, "kg/sec");
    system.init(3);
    system.initPhysicalProperties();
    double pOutLow = calcPressureOut();

    system.setTotalFlowRate(flowHigh, "kg/sec");
    system.init(3);
    system.initPhysicalProperties();
    double pOutHigh = calcPressureOut();

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
        flowLow = flowMid;
      } else {
        flowHigh = flowMid;
      }

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

    // Calculate pressure drop
    pressureDrop = inletPressure - system.getPressure();

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
  public void setInitialFlowPattern(String flowPattern) {
    // Not applicable for single-phase adiabatic pipe
  }

  /** {@inheritDoc} */
  @Override
  public double getPressureDrop() {
    return pressureDrop;
  }

  /** {@inheritDoc} */
  @Override
  public String getFlowRegime() {
    return flowRegime;
  }

  /** {@inheritDoc} */
  @Override
  public double getVelocity() {
    return velocity;
  }

  /** {@inheritDoc} */
  @Override
  public double getReynoldsNumber() {
    return reynoldsNumber;
  }

  /** {@inheritDoc} */
  @Override
  public double getFrictionFactor() {
    return frictionFactor;
  }

  /**
   * Main method for testing.
   *
   * @param name command line arguments
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
