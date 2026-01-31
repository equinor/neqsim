package neqsim.process.equipment.pipeline;

import java.util.UUID;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Incompressible pipe flow model for liquid flow with fittings.
 *
 * <p>
 * This class models incompressible (liquid) flow through pipes using the Darcy-Weisbach equation.
 * It extends {@link AdiabaticPipe} and inherits fitting support from the {@link Pipeline} base
 * class.
 * </p>
 *
 * <h2>Pressure Drop Calculation</h2>
 * <p>
 * The total pressure drop consists of friction loss and elevation change:
 * </p>
 * 
 * <pre>
 * ΔP_total = ΔP_friction + ΔP_elevation
 * 
 * ΔP_friction = f × (L_eff / D) × (ρV² / 2)
 * ΔP_elevation = ρg(z_in - z_out)
 * </pre>
 * <p>
 * where:
 * </p>
 * <ul>
 * <li>f = Darcy friction factor</li>
 * <li>L_eff = effective length (physical + fittings equivalent)</li>
 * <li>D = pipe internal diameter</li>
 * <li>ρ = fluid density</li>
 * <li>V = fluid velocity</li>
 * <li>g = gravitational acceleration</li>
 * <li>z = elevation</li>
 * </ul>
 *
 * <h2>Equivalent Length Method for Fittings</h2>
 * <p>
 * Pipe fittings (bends, valves, tees, etc.) add pressure loss that is accounted for using the
 * equivalent length method. Each fitting is assigned an L/D ratio representing the length of
 * straight pipe (in diameters) that would produce the same pressure drop.
 * </p>
 * 
 * <pre>
 * L_eff = L_physical + Σ(L/D)_i × D
 * </pre>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * {@code
 * // Create water stream
 * SystemInterface water = new SystemSrkEos(298.15, 5.0);
 * water.addComponent("water", 100.0, "kg/hr");
 * Stream feed = new Stream("Water", water);
 * feed.run();
 *
 * // Create pipe with fittings
 * IncompressiblePipeFlow pipe = new IncompressiblePipeFlow("Process Line", feed);
 * pipe.setLength(100.0); // 100 m physical length
 * pipe.setDiameter(0.05); // 50 mm diameter
 * pipe.setPipeWallRoughness(4.5e-5); // Commercial steel
 *
 * // Add fittings
 * pipe.addFitting("90-degree elbow", 30.0); // L/D = 30
 * pipe.addFitting("90-degree elbow", 30.0); // Another elbow
 * pipe.addFittingFromDatabase("Gate valve, fully open"); // From database
 *
 * // Set elevation change
 * pipe.setInletElevation(10);
 * pipe.setOutletElevation(0);
 *
 * pipe.run();
 *
 * // Results
 * double pressureDrop = pipe.getInletPressure() - pipe.getOutletPressure();
 * double effectiveLength = pipe.getEffectiveLength(); // Physical + fittings
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 2.0
 * @see Pipeline
 * @see AdiabaticPipe
 * @see Fittings
 */
public class IncompressiblePipeFlow extends AdiabaticPipe {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001;

  /** Momentum term for pressure drop calculation (ρV²). */
  double momentum = 0;

  /**
   * Constructor for IncompressiblePipeFlow.
   *
   * @param name name of pipeline
   */
  public IncompressiblePipeFlow(String name) {
    super(name);
  }

  /**
   * Constructor for IncompressiblePipeFlow with inlet stream.
   *
   * @param name name of pipe
   * @param inStream input stream
   */
  public IncompressiblePipeFlow(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * Calculate the outlet pressure based on friction and elevation losses.
   *
   * <p>
   * Uses the Darcy-Weisbach equation with effective length (physical + fittings):
   * </p>
   * 
   * <pre>
   * ΔP_friction = f × (L_eff / D) × (ρV² / 2)
   * </pre>
   *
   * @return the outlet pressure in bara
   */
  @Override
  public double calcPressureOut() {
    // Get effective length including fittings equivalent length
    double effectiveLength = getEffectiveLength();

    double area = Math.PI / 4.0 * Math.pow(insideDiameter, 2.0);
    double density = system.getPhase(0).getPhysicalProperties().getDensity();
    double volumetricFlowRate = system.getPhase(0).getFlowRate("m3/sec");
    double velocity = volumetricFlowRate / area;

    // Dynamic pressure (momentum term)
    momentum = density * velocity * velocity;

    // Calculate Reynolds number and friction factor
    double reynoldsNumber = velocity * insideDiameter
        / system.getPhase(0).getPhysicalProperties().getKinematicViscosity();
    double frictionFactor = calcWallFrictionFactor(reynoldsNumber);

    // Friction pressure drop using Darcy-Weisbach with effective length
    // ΔP = f × (L/D) × (ρV²/2)
    double dp = -momentum * frictionFactor * effectiveLength / (2.0 * insideDiameter);

    // Elevation pressure change: ΔP = ρg(z_in - z_out)
    dp += (getInletElevation() - getOutletElevation()) * density
        * neqsim.thermo.ThermodynamicConstantsInterface.gravity;

    return (system.getPressure() * 1e5 + dp) / 1.0e5;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    system = inStream.getThermoSystem().clone();
    if (setTemperature) {
      system.setTemperature(this.temperatureOut);
    }
    system.init(3);
    system.initPhysicalProperties();
    calcPressureOut();
    system.setPressure(calcPressureOut());

    ThermodynamicOperations testOps = new ThermodynamicOperations(system);
    testOps.TPflash();
    system.initProperties();
    outStream.setThermoSystem(system);
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /**
   * Example usage demonstrating pipe flow with fittings.
   *
   * @param name command line arguments (not used)
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] name) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem.addComponent("water", 100.0 * 1e3, "kg/hr");
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.initPhysicalProperties();
    Stream stream_1 = new Stream("Stream1", testSystem);

    IncompressiblePipeFlow pipe = new IncompressiblePipeFlow("pipe1", stream_1);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.25);
    pipe.setPipeWallRoughness(2e-5);

    // Add fittings using inherited methods from Pipeline
    pipe.addFittingFromDatabase("Standard elbow (R=1.5D), 90deg");
    pipe.addFitting("gate valve", 8.0);

    IncompressiblePipeFlow pipe2 = new IncompressiblePipeFlow("pipe2", pipe.getOutletStream());
    pipe2.setLength(1000.0);
    pipe2.setDiameter(0.25);
    pipe2.setPipeWallRoughness(2e-5);
    pipe2.setInletElevation(10);
    pipe2.setOutletElevation(0);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(pipe);
    operations.add(pipe2);
    operations.run();

    // Print fittings summary
    System.out.println("Pipe 1 Effective Length: " + pipe.getEffectiveLength() + " m");
    pipe.printFittingsSummary();
    pipe.displayResult();
  }
}
