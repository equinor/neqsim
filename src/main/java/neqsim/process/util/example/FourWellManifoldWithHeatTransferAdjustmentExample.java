package neqsim.process.util.example;

import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills.HeatTransferMode;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Example demonstrating a production network with four wells routing to HP/LP manifolds.
 *
 * <p>
 * This example shows:
 * </p>
 * <ul>
 * <li>Four hydrocarbon gas streams representing four wells</li>
 * <li>Each well stream flows through a Beggs and Brill pipe with heat transfer to sea (5°C)</li>
 * <li>Well temperature is 70°C for all wells</li>
 * <li>Each pipe output goes to a splitter for routing control</li>
 * <li>Splitters route to either High Pressure (HP) or Low Pressure (LP) manifold</li>
 * <li>Split factors (0 or 1) control the routing</li>
 * <li>Two wells route to HP manifold, two to LP manifold</li>
 * <li>Adjusters on HP and LP streams adjust heat transfer coefficients to match measured
 * temperatures (50°C)</li>
 * </ul>
 *
 * <pre>
 *                        +--------+
 *    Well 1 --> Pipe1 -->|Splitter|---> HP Manifold ----+
 *                        +--------+---> LP Manifold -+  |
 *                                                    |  |
 *                        +--------+                  |  |
 *    Well 2 --> Pipe2 -->|Splitter|---> HP Manifold -|--+---> HP Outlet Stream
 *                        +--------+---> LP Manifold -|--+
 *                                                    |  |
 *                        +--------+                  |  |
 *    Well 3 --> Pipe3 -->|Splitter|---> HP Manifold -|--+
 *                        +--------+---> LP Manifold -+--+---> LP Outlet Stream
 *                                                       |
 *                        +--------+                     |
 *    Well 4 --> Pipe4 -->|Splitter|---> HP Manifold ----+
 *                        +--------+---> LP Manifold ----+
 * </pre>
 */
public class FourWellManifoldWithHeatTransferAdjustmentExample {

  /**
   * Creates a hydrocarbon gas system for a well.
   *
   * @param wellName the name of the well
   * @param temperature the well temperature in Celsius
   * @param pressure the well pressure in bara
   * @return configured fluid system
   */
  private static SystemInterface createWellFluid(String wellName, double temperature,
      double pressure) {
    SystemInterface fluid = new SystemSrkEos(273.15 + temperature, pressure);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-pentane", 0.02);
    fluid.addComponent("nC10", 0.02);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Main method to run the example.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
    System.out.println("║  FOUR WELL MANIFOLD WITH HEAT TRANSFER ADJUSTMENT EXAMPLE            ║");
    System.out.println("╚══════════════════════════════════════════════════════════════════════╝");

    // ========================================
    // CONFIGURATION PARAMETERS
    // ========================================
    double wellTemperature = 70.0; // °C - Well temperature for all wells
    double seaTemperature = 15.0; // °C - Sea/ambient temperature for heat transfer
    // Target temperatures (measured at manifolds) - adjust these to match field measurements
    // Note: With gas at high pressure, heat transfer is limited. The adjuster finds the
    // heat transfer coefficient that achieves the measured outlet temperature.
    // Setting targets slightly below inlet temperature (achievable by heat transfer)
    double measuredHPTemp = 40.0; // °C - Target temperature for HP stream
    double measuredLPTemp = 35.0; // °C - Target temperature for LP stream

    // Well pressures (bara)
    double wellPressure1 = 100.0;
    double wellPressure2 = 95.0;
    double wellPressure3 = 90.0;
    double wellPressure4 = 85.0;

    // Flow rates (kg/hr) - Higher flow rates for more realistic heat transfer
    double flowRate1 = 50000.0;
    double flowRate2 = 45000.0;
    double flowRate3 = 55000.0;
    double flowRate4 = 40000.0;

    // Pipe parameters
    double pipeLength = 10000.0; // m - 10km subsea pipeline
    double pipeElevation = 0.0; // m - horizontal pipe (subsea pipelines are nearly horizontal)
    double pipeDiameter = 0.1524; // 6 inch in meters
    double initialHeatTransferCoeff = 15.0; // W/(m²·K) - initial overall U-value guess

    // Heat transfer mode: SPECIFIED_U uses the setHeatTransferCoefficient value directly
    // as the overall heat transfer coefficient. The adjuster will modify this value
    // to match the target outlet temperature.
    // Other modes available:
    // - ADIABATIC: No heat transfer (Q=0)
    // - ISOTHERMAL: Constant temperature along the pipe
    // - ESTIMATED_INNER_H: Calculate h from flow (Gnielinski), use as U
    // - DETAILED_U: Full U including wall, insulation, outer convection
    HeatTransferMode heatTransferMode = HeatTransferMode.SPECIFIED_U;

    // Routing configuration: 0 = route to LP, 1 = route to HP
    // Well 1 and Well 2 go to HP manifold
    // Well 3 and Well 4 go to LP manifold
    double[] well1SplitFactors = {1.0, 0.0}; // 100% HP, 0% LP
    double[] well2SplitFactors = {1.0, 0.0}; // 100% HP, 0% LP
    double[] well3SplitFactors = {0.0, 1.0}; // 0% HP, 100% LP
    double[] well4SplitFactors = {0.0, 1.0}; // 0% HP, 100% LP

    // ========================================
    // CREATE WELL STREAMS
    // ========================================
    System.out.println("\n=== Creating Well Streams ===");

    // Well 1
    SystemInterface wellFluid1 = createWellFluid("Well1", wellTemperature, wellPressure1);
    Stream well1Stream = new Stream("Well 1 Stream", wellFluid1);
    well1Stream.setFlowRate(flowRate1, "kg/hr");

    // Well 2
    SystemInterface wellFluid2 = createWellFluid("Well2", wellTemperature, wellPressure2);
    Stream well2Stream = new Stream("Well 2 Stream", wellFluid2);
    well2Stream.setFlowRate(flowRate2, "kg/hr");

    // Well 3
    SystemInterface wellFluid3 = createWellFluid("Well3", wellTemperature, wellPressure3);
    Stream well3Stream = new Stream("Well 3 Stream", wellFluid3);
    well3Stream.setFlowRate(flowRate3, "kg/hr");

    // Well 4
    SystemInterface wellFluid4 = createWellFluid("Well4", wellTemperature, wellPressure4);
    Stream well4Stream = new Stream("Well 4 Stream", wellFluid4);
    well4Stream.setFlowRate(flowRate4, "kg/hr");

    System.out.println("Created 4 well streams at " + wellTemperature + " °C");

    // ========================================
    // CREATE BEGGS AND BRILL PIPES (WELL PIPES)
    // ========================================
    System.out.println("\n=== Creating Beggs and Brill Pipes (Heat Transfer to Sea) ===");

    // Pipe 1 - Well 1 pipe
    PipeBeggsAndBrills pipe1 = new PipeBeggsAndBrills("Well 1 Pipe", well1Stream);
    pipe1.setLength(pipeLength);
    pipe1.setDiameter(pipeDiameter);
    pipe1.setElevation(pipeElevation);
    pipe1.setRunIsothermal(false);
    pipe1.setNumberOfIncrements(20);
    pipe1.setConstantSurfaceTemperature(seaTemperature, "C");
    // Set overall heat transfer coefficient directly (SPECIFIED_U mode)
    // The adjuster will modify this value to match target temperature
    pipe1.setHeatTransferCoefficient(initialHeatTransferCoeff);

    // Pipe 2 - Well 2 pipe
    PipeBeggsAndBrills pipe2 = new PipeBeggsAndBrills("Well 2 Pipe", well2Stream);
    pipe2.setLength(pipeLength);
    pipe2.setDiameter(pipeDiameter);
    pipe2.setElevation(pipeElevation);
    pipe2.setRunIsothermal(false);
    pipe2.setNumberOfIncrements(20);
    pipe2.setConstantSurfaceTemperature(seaTemperature, "C");
    // Set overall heat transfer coefficient directly (SPECIFIED_U mode)
    pipe2.setHeatTransferCoefficient(initialHeatTransferCoeff);

    // Pipe 3 - Well 3 pipe
    PipeBeggsAndBrills pipe3 = new PipeBeggsAndBrills("Well 3 Pipe", well3Stream);
    pipe3.setLength(pipeLength);
    pipe3.setDiameter(pipeDiameter);
    pipe3.setElevation(pipeElevation);
    pipe3.setRunIsothermal(false);
    pipe3.setNumberOfIncrements(20);
    pipe3.setConstantSurfaceTemperature(seaTemperature, "C");
    // Set overall heat transfer coefficient directly (SPECIFIED_U mode)
    pipe3.setHeatTransferCoefficient(initialHeatTransferCoeff);

    // Pipe 4 - Well 4 pipe
    PipeBeggsAndBrills pipe4 = new PipeBeggsAndBrills("Well 4 Pipe", well4Stream);
    pipe4.setLength(pipeLength);
    pipe4.setDiameter(pipeDiameter);
    pipe4.setElevation(pipeElevation);
    pipe4.setRunIsothermal(false);
    pipe4.setNumberOfIncrements(20);
    pipe4.setConstantSurfaceTemperature(seaTemperature, "C");
    // Set overall heat transfer coefficient directly (SPECIFIED_U mode)
    pipe4.setHeatTransferCoefficient(initialHeatTransferCoeff);

    System.out.printf("Created 4 pipes, length: %.0f m (%.0f km), diameter: %.4f m%n", pipeLength,
        pipeLength / 1000.0, pipeDiameter);
    System.out.printf("Sea temperature: %.1f °C, initial U-value: %.1f W/(m²·K)%n", seaTemperature,
        initialHeatTransferCoeff);
    System.out.printf(
        "Heat transfer mode: %s (adjuster will modify U-value to match target temp)%n",
        heatTransferMode);

    // ========================================
    // CREATE SPLITTERS FOR ROUTING
    // ========================================
    System.out.println("\n=== Creating Splitters for HP/LP Routing ===");

    // Splitter 1 - routes Well 1 output
    Splitter splitter1 = new Splitter("Well 1 Splitter", pipe1.getOutletStream(), 2);
    splitter1.setSplitFactors(well1SplitFactors);

    // Splitter 2 - routes Well 2 output
    Splitter splitter2 = new Splitter("Well 2 Splitter", pipe2.getOutletStream(), 2);
    splitter2.setSplitFactors(well2SplitFactors);

    // Splitter 3 - routes Well 3 output
    Splitter splitter3 = new Splitter("Well 3 Splitter", pipe3.getOutletStream(), 2);
    splitter3.setSplitFactors(well3SplitFactors);

    // Splitter 4 - routes Well 4 output
    Splitter splitter4 = new Splitter("Well 4 Splitter", pipe4.getOutletStream(), 2);
    splitter4.setSplitFactors(well4SplitFactors);

    System.out.println("Routing configuration:");
    System.out.println("  Well 1: HP=" + well1SplitFactors[0] + ", LP=" + well1SplitFactors[1]);
    System.out.println("  Well 2: HP=" + well2SplitFactors[0] + ", LP=" + well2SplitFactors[1]);
    System.out.println("  Well 3: HP=" + well3SplitFactors[0] + ", LP=" + well3SplitFactors[1]);
    System.out.println("  Well 4: HP=" + well4SplitFactors[0] + ", LP=" + well4SplitFactors[1]);

    // ========================================
    // CREATE HP AND LP MANIFOLDS (MIXERS)
    // ========================================
    System.out.println("\n=== Creating HP and LP Manifolds ===");

    // HP Manifold - collects HP split streams
    Mixer hpManifold = new Mixer("HP Manifold");
    hpManifold.addStream(splitter1.getSplitStream(0)); // HP from Well 1
    hpManifold.addStream(splitter2.getSplitStream(0)); // HP from Well 2
    hpManifold.addStream(splitter3.getSplitStream(0)); // HP from Well 3
    hpManifold.addStream(splitter4.getSplitStream(0)); // HP from Well 4

    // LP Manifold - collects LP split streams
    Mixer lpManifold = new Mixer("LP Manifold");
    lpManifold.addStream(splitter1.getSplitStream(1)); // LP from Well 1
    lpManifold.addStream(splitter2.getSplitStream(1)); // LP from Well 2
    lpManifold.addStream(splitter3.getSplitStream(1)); // LP from Well 3
    lpManifold.addStream(splitter4.getSplitStream(1)); // LP from Well 4

    System.out.println("Created HP Manifold (Wells 1 & 2 routed here)");
    System.out.println("Created LP Manifold (Wells 3 & 4 routed here)");

    // ========================================
    // CREATE ADJUSTERS FOR HEAT TRANSFER COEFFICIENT
    // ========================================
    System.out.println("\n=== Creating Adjusters for Heat Transfer Coefficient ===");

    // Adjuster for HP manifold outlet temperature
    // Adjusts the heat transfer coefficient of pipes feeding HP manifold (pipe1 and pipe2)
    // to match the measured HP outlet temperature
    Adjuster hpAdjuster = new Adjuster("HP Heat Transfer Adjuster");
    hpAdjuster.setAdjustedVariable(pipe1);
    hpAdjuster.setTargetVariable(hpManifold.getOutletStream());
    hpAdjuster.setTargetValue(measuredHPTemp);
    // Use lambda to calculate temperature in Celsius from target stream
    hpAdjuster.setTargetValueCalculator(eq -> ((Stream) eq).getThermoSystem().getTemperature("C"));
    hpAdjuster.setAdjustedValueGetter(() -> pipe1.getHeatTransferCoefficient());
    hpAdjuster.setAdjustedValueSetter((eq, val) -> {
      // Set the same heat transfer coefficient for both HP pipes
      pipe1.setHeatTransferCoefficient(val);
      pipe2.setHeatTransferCoefficient(val);
    });
    hpAdjuster.setMaxAdjustedValue(1000.0); // Max heat transfer coefficient
    hpAdjuster.setMinAdjustedValue(0.01); // Min heat transfer coefficient
    hpAdjuster.setTolerance(0.5); // Temperature tolerance in °C

    // Adjuster for LP manifold outlet temperature
    // Adjusts the heat transfer coefficient of pipes feeding LP manifold (pipe3 and pipe4)
    Adjuster lpAdjuster = new Adjuster("LP Heat Transfer Adjuster");
    lpAdjuster.setAdjustedVariable(pipe3);
    lpAdjuster.setTargetVariable(lpManifold.getOutletStream());
    lpAdjuster.setTargetValue(measuredLPTemp);
    // Use lambda to calculate temperature in Celsius from target stream
    lpAdjuster.setTargetValueCalculator(eq -> ((Stream) eq).getThermoSystem().getTemperature("C"));
    lpAdjuster.setAdjustedValueGetter(() -> pipe3.getHeatTransferCoefficient());
    lpAdjuster.setAdjustedValueSetter((eq, val) -> {
      // Set the same heat transfer coefficient for both LP pipes
      pipe3.setHeatTransferCoefficient(val);
      pipe4.setHeatTransferCoefficient(val);
    });
    lpAdjuster.setMaxAdjustedValue(1000.0); // Max heat transfer coefficient
    lpAdjuster.setMinAdjustedValue(0.01); // Min heat transfer coefficient
    lpAdjuster.setTolerance(0.5); // Temperature tolerance in °C

    System.out.printf("HP Adjuster: Target temperature = %.1f °C%n", measuredHPTemp);
    System.out.printf("LP Adjuster: Target temperature = %.1f °C%n", measuredLPTemp);

    // ========================================
    // BUILD AND RUN THE PROCESS SYSTEM
    // ========================================
    System.out.println("\n=== Building Process System ===");

    ProcessSystem process = new ProcessSystem();

    // Add well streams
    process.add(well1Stream);
    process.add(well2Stream);
    process.add(well3Stream);
    process.add(well4Stream);

    // Add pipes
    process.add(pipe1);
    process.add(pipe2);
    process.add(pipe3);
    process.add(pipe4);

    // Add splitters
    process.add(splitter1);
    process.add(splitter2);
    process.add(splitter3);
    process.add(splitter4);

    // Add manifolds
    process.add(hpManifold);
    process.add(lpManifold);

    // Add adjusters
    process.add(hpAdjuster);
    process.add(lpAdjuster);

    System.out.println("Running process simulation with adjusters...");
    process.run();
    process.run();
    process.run();
    // ========================================
    // DISPLAY RESULTS
    // ========================================
    System.out
        .println("\n╔══════════════════════════════════════════════════════════════════════╗");
    System.out.println("║                           SIMULATION RESULTS                          ║");
    System.out.println("╚══════════════════════════════════════════════════════════════════════╝");

    System.out.println("\n=== Well Inlet Conditions ===");
    System.out.printf("%-15s %15s %15s %15s%n", "Well", "Temperature(°C)", "Pressure(bara)",
        "FlowRate(kg/hr)");
    System.out.println("─────────────────────────────────────────────────────────────────────────");
    System.out.printf("%-15s %15.2f %15.2f %15.0f%n", "Well 1", well1Stream.getTemperature("C"),
        well1Stream.getPressure("bara"), well1Stream.getFlowRate("kg/hr"));
    System.out.printf("%-15s %15.2f %15.2f %15.0f%n", "Well 2", well2Stream.getTemperature("C"),
        well2Stream.getPressure("bara"), well2Stream.getFlowRate("kg/hr"));
    System.out.printf("%-15s %15.2f %15.2f %15.0f%n", "Well 3", well3Stream.getTemperature("C"),
        well3Stream.getPressure("bara"), well3Stream.getFlowRate("kg/hr"));
    System.out.printf("%-15s %15.2f %15.2f %15.0f%n", "Well 4", well4Stream.getTemperature("C"),
        well4Stream.getPressure("bara"), well4Stream.getFlowRate("kg/hr"));

    System.out.println("\n=== Pipe Outlet Conditions ===");
    System.out.printf("%-15s %15s %15s %20s%n", "Pipe", "Outlet Temp(°C)", "Outlet P(bara)",
        "Heat Transfer(W/m²K)");
    System.out.println("─────────────────────────────────────────────────────────────────────────");
    System.out.printf("%-15s %15.2f %15.2f %20.2f%n", "Pipe 1",
        pipe1.getOutletTemperature() - 273.15, pipe1.getOutletPressure(),
        pipe1.getHeatTransferCoefficient());
    System.out.printf("%-15s %15.2f %15.2f %20.2f%n", "Pipe 2",
        pipe2.getOutletTemperature() - 273.15, pipe2.getOutletPressure(),
        pipe2.getHeatTransferCoefficient());
    System.out.printf("%-15s %15.2f %15.2f %20.2f%n", "Pipe 3",
        pipe3.getOutletTemperature() - 273.15, pipe3.getOutletPressure(),
        pipe3.getHeatTransferCoefficient());
    System.out.printf("%-15s %15.2f %15.2f %20.2f%n", "Pipe 4",
        pipe4.getOutletTemperature() - 273.15, pipe4.getOutletPressure(),
        pipe4.getHeatTransferCoefficient());

    System.out.println("\n=== Pipe Hydraulics (Pressure Drop) ===");
    System.out.printf("%-15s %18s %20s%n", "Pipe", "Pressure Drop (bar)", "dP/Length (bar/km)");
    System.out.println("─────────────────────────────────────────────────────────────────────────");
    double dp1 = well1Stream.getPressure("bara") - pipe1.getOutletPressure();
    System.out.printf("%-15s %18.2f %20.2f%n", "Pipe 1", dp1, dp1 / (pipeLength / 1000.0));
    double dp2 = well2Stream.getPressure("bara") - pipe2.getOutletPressure();
    System.out.printf("%-15s %18.2f %20.2f%n", "Pipe 2", dp2, dp2 / (pipeLength / 1000.0));
    double dp3 = well3Stream.getPressure("bara") - pipe3.getOutletPressure();
    System.out.printf("%-15s %18.2f %20.2f%n", "Pipe 3", dp3, dp3 / (pipeLength / 1000.0));
    double dp4 = well4Stream.getPressure("bara") - pipe4.getOutletPressure();
    System.out.printf("%-15s %18.2f %20.2f%n", "Pipe 4", dp4, dp4 / (pipeLength / 1000.0));
    System.out.println("\nTypical acceptable ranges:");
    System.out.println("  Pressure drop: <1-2 bar/km for gas pipelines");

    System.out.println("\n=== Manifold Outlet Conditions ===");
    System.out.printf("%-15s %15s %15s %15s%n", "Manifold", "Temperature(°C)", "Pressure(bara)",
        "FlowRate(kg/hr)");
    System.out.println("─────────────────────────────────────────────────────────────────────────");
    System.out.printf("%-15s %15.2f %15.2f %15.0f%n", "HP Manifold",
        hpManifold.getOutletStream().getTemperature("C"),
        hpManifold.getOutletStream().getPressure("bara"),
        hpManifold.getOutletStream().getFlowRate("kg/hr"));
    System.out.printf("%-15s %15.2f %15.2f %15.0f%n", "LP Manifold",
        lpManifold.getOutletStream().getTemperature("C"),
        lpManifold.getOutletStream().getPressure("bara"),
        lpManifold.getOutletStream().getFlowRate("kg/hr"));

    System.out.println("\n=== Adjuster Status ===");
    System.out.printf("HP Adjuster: Target=%.1f°C, Achieved=%.2f°C, Error=%.4f, Solved=%s%n",
        measuredHPTemp, hpManifold.getOutletStream().getTemperature("C"), hpAdjuster.getError(),
        hpAdjuster.solved());
    System.out.printf("LP Adjuster: Target=%.1f°C, Achieved=%.2f°C, Error=%.4f, Solved=%s%n",
        measuredLPTemp, lpManifold.getOutletStream().getTemperature("C"), lpAdjuster.getError(),
        lpAdjuster.solved());

    System.out.println("\n=== Adjusted Heat Transfer Coefficients ===");
    System.out.printf("HP Pipes (1 & 2): %.2f W/(m²·K)%n", pipe1.getHeatTransferCoefficient());
    System.out.printf("LP Pipes (3 & 4): %.2f W/(m²·K)%n", pipe3.getHeatTransferCoefficient());

    // ========================================
    // DEMONSTRATE ROUTING CHANGE
    // ========================================
    System.out
        .println("\n╔══════════════════════════════════════════════════════════════════════╗");
    System.out.println("║           DEMONSTRATING ROUTING CHANGE (SWITCH WELL 3 TO HP)          ║");
    System.out.println("╚══════════════════════════════════════════════════════════════════════╝");

    // Change routing: move Well 3 from LP to HP
    double[] newWell3SplitFactors = {1.0, 0.0}; // Now 100% HP, 0% LP
    splitter3.setSplitFactors(newWell3SplitFactors);

    System.out.println("New routing configuration:");
    System.out.println("  Well 1: HP=1.0, LP=0.0");
    System.out.println("  Well 2: HP=1.0, LP=0.0");
    System.out.println("  Well 3: HP=1.0, LP=0.0 (CHANGED)");
    System.out.println("  Well 4: HP=0.0, LP=1.0");

    System.out.println("\nRunning process with new routing...");
    process.run();

    System.out.println("\n=== Updated Manifold Outlet Conditions ===");
    System.out.printf("%-15s %15s %15s %15s%n", "Manifold", "Temperature(°C)", "Pressure(bara)",
        "FlowRate(kg/hr)");
    System.out.println("─────────────────────────────────────────────────────────────────────────");
    System.out.printf("%-15s %15.2f %15.2f %15.0f%n", "HP Manifold",
        hpManifold.getOutletStream().getTemperature("C"),
        hpManifold.getOutletStream().getPressure("bara"),
        hpManifold.getOutletStream().getFlowRate("kg/hr"));
    System.out.printf("%-15s %15.2f %15.2f %15.0f%n", "LP Manifold",
        lpManifold.getOutletStream().getTemperature("C"),
        lpManifold.getOutletStream().getPressure("bara"),
        lpManifold.getOutletStream().getFlowRate("kg/hr"));

    System.out.println("\n=== Updated Adjusted Heat Transfer Coefficients ===");
    System.out.printf("HP Pipes (1 & 2): %.2f W/(m²·K)%n", pipe1.getHeatTransferCoefficient());
    System.out.printf("LP Pipes (3 & 4): %.2f W/(m²·K)%n", pipe3.getHeatTransferCoefficient());

    System.out
        .println("\n════════════════════════════════════════════════════════════════════════");
    System.out.println("Example completed successfully!");
  }
}
