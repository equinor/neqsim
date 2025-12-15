package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.graph.ProcessGraph;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration tests for simultaneous modular solving and sensitivity-based tear stream selection
 * using large, realistic process simulations.
 */
class SimultaneousSolvingIntegrationTest {

  private SystemInterface richGasFluid;
  private SystemInterface leanGasFluid;

  @BeforeEach
  void setUp() {
    // Create a rich natural gas fluid for testing
    richGasFluid = new SystemSrkEos(280.0, 65.0);
    richGasFluid.addComponent("nitrogen", 0.02);
    richGasFluid.addComponent("CO2", 0.03);
    richGasFluid.addComponent("methane", 0.75);
    richGasFluid.addComponent("ethane", 0.08);
    richGasFluid.addComponent("propane", 0.05);
    richGasFluid.addComponent("i-butane", 0.02);
    richGasFluid.addComponent("n-butane", 0.02);
    richGasFluid.addComponent("i-pentane", 0.015);
    richGasFluid.addComponent("n-pentane", 0.015);
    richGasFluid.setMixingRule("classic");

    // Lean gas for recycle streams
    leanGasFluid = new SystemSrkEos(280.0, 65.0);
    leanGasFluid.addComponent("nitrogen", 0.03);
    leanGasFluid.addComponent("CO2", 0.02);
    leanGasFluid.addComponent("methane", 0.90);
    leanGasFluid.addComponent("ethane", 0.04);
    leanGasFluid.addComponent("propane", 0.01);
    leanGasFluid.setMixingRule("classic");
  }

  @Test
  @DisplayName("Gas processing plant with single recycle loop")
  void testGasProcessingWithSingleRecycle() {
    ProcessSystem process = new ProcessSystem("Gas Processing Plant");

    // Feed stream
    Stream feedGas = new Stream("Feed Gas", richGasFluid.clone());
    feedGas.setFlowRate(50000.0, "kg/hr");
    feedGas.setTemperature(25.0, "C");
    feedGas.setPressure(65.0, "bara");
    process.add(feedGas);

    // Recycle stream (initialized with lean gas)
    Stream recycleGas = new Stream("Recycle Gas", leanGasFluid.clone());
    recycleGas.setFlowRate(5000.0, "kg/hr");
    recycleGas.setTemperature(40.0, "C");
    recycleGas.setPressure(65.0, "bara");
    process.add(recycleGas);

    // Mix feed with recycle
    Mixer feedMixer = new Mixer("Feed Mixer");
    feedMixer.addStream(feedGas);
    feedMixer.addStream(recycleGas);
    process.add(feedMixer);

    // Inlet cooler
    Cooler inletCooler = new Cooler("Inlet Cooler", feedMixer.getOutletStream());
    inletCooler.setOutTemperature(15.0, "C");
    process.add(inletCooler);

    // Inlet separator
    ThreePhaseSeparator inletSeparator =
        new ThreePhaseSeparator("Inlet Separator", inletCooler.getOutletStream());
    process.add(inletSeparator);

    // Gas heater before compression
    Heater gasHeater = new Heater("Gas Heater", inletSeparator.getGasOutStream());
    gasHeater.setOutTemperature(35.0, "C");
    process.add(gasHeater);

    // Compressor
    Compressor compressor = new Compressor("Main Compressor", gasHeater.getOutletStream());
    compressor.setOutletPressure(90.0, "bara");
    compressor.setIsentropicEfficiency(0.75);
    process.add(compressor);

    // After-cooler
    Cooler afterCooler = new Cooler("After Cooler", compressor.getOutletStream());
    afterCooler.setOutTemperature(40.0, "C");
    process.add(afterCooler);

    // High pressure separator
    Separator hpSeparator = new Separator("HP Separator", afterCooler.getOutletStream());
    process.add(hpSeparator);

    // Splitter - split some gas for recycle
    Splitter gasSplitter = new Splitter("Gas Splitter", hpSeparator.getGasOutStream());
    gasSplitter.setSplitFactors(new double[] {0.9, 0.1});
    process.add(gasSplitter);

    // Product gas - use StreamInterface
    neqsim.process.equipment.stream.StreamInterface productGas = gasSplitter.getSplitStream(0);

    // JT valve for recycle
    ThrottlingValve jtValve = new ThrottlingValve("JT Valve", gasSplitter.getSplitStream(1));
    jtValve.setOutletPressure(65.0, "bara");
    process.add(jtValve);

    // Recycle unit
    Recycle recycle = new Recycle("Main Recycle");
    recycle.addStream(jtValve.getOutletStream());
    recycle.setOutletStream(recycleGas);
    recycle.setTolerance(1e-3);
    recycle.setAccelerationMethod(AccelerationMethod.BROYDEN);
    process.add(recycle);

    // Build and analyze graph
    ProcessGraph graph = process.buildGraph();

    System.out.println("\n===== Gas Processing Plant Graph Analysis =====");
    System.out.println(graph.getSummary());

    // Check for cycles
    assertTrue(graph.hasCycles(), "Process should have cycles (recycle loop)");

    // Get sensitivity analysis report
    String sensitivityReport = graph.getSensitivityAnalysisReport();
    System.out.println(sensitivityReport);

    // Select tear streams with sensitivity analysis
    ProcessGraph.TearStreamResult tearResult = graph.selectTearStreamsWithSensitivity();
    System.out.println("Selected tear streams: " + tearResult.getTearStreamCount());

    // Run the process
    long startTime = System.currentTimeMillis();
    process.run();
    long endTime = System.currentTimeMillis();

    System.out.println("Simulation completed in " + (endTime - startTime) + " ms");
    System.out.println("Recycle iterations: " + recycle.getIterations());
    System.out.println("Recycle converged: " + recycle.solved());

    // Verify convergence
    assertTrue(recycle.solved(), "Recycle should converge");
    assertTrue(recycle.getIterations() < 50, "Should converge in reasonable iterations");

    // Verify mass balance
    double feedFlow = feedGas.getFlowRate("kg/hr");
    double productFlow = productGas.getFlowRate("kg/hr");
    double liquidFlow = inletSeparator.getLiquidOutStream().getFlowRate("kg/hr")
        + hpSeparator.getLiquidOutStream().getFlowRate("kg/hr");

    System.out.println("\nMass Balance:");
    System.out.println("  Feed: " + String.format("%.2f", feedFlow) + " kg/hr");
    System.out.println("  Product Gas: " + String.format("%.2f", productFlow) + " kg/hr");
    System.out.println("  Liquids: " + String.format("%.2f", liquidFlow) + " kg/hr");

    double massBalance = Math.abs(feedFlow - productFlow - liquidFlow) / feedFlow * 100;
    System.out.println("  Mass balance error: " + String.format("%.4f", massBalance) + "%");

    assertTrue(massBalance < 1.0, "Mass balance should be within 1%");

    System.out.println("=================================================\n");
  }

  @Test
  @DisplayName("Two-stage compression with dual recycle loops")
  void testTwoStageCompressionWithDualRecycles() {
    ProcessSystem process = new ProcessSystem("Two-Stage Compression");

    // Feed stream
    Stream feedGas = new Stream("Feed Gas", richGasFluid.clone());
    feedGas.setFlowRate(30000.0, "kg/hr");
    feedGas.setTemperature(30.0, "C");
    feedGas.setPressure(20.0, "bara");
    process.add(feedGas);

    // First recycle stream (anti-surge)
    Stream recycle1Stream = new Stream("Recycle 1 Stream", leanGasFluid.clone());
    recycle1Stream.setFlowRate(1000.0, "kg/hr");
    recycle1Stream.setTemperature(40.0, "C");
    recycle1Stream.setPressure(20.0, "bara");
    process.add(recycle1Stream);

    // First stage mixer
    Mixer mixer1 = new Mixer("Stage 1 Mixer");
    mixer1.addStream(feedGas);
    mixer1.addStream(recycle1Stream);
    process.add(mixer1);

    // First stage compressor
    Compressor compressor1 = new Compressor("Stage 1 Compressor", mixer1.getOutletStream());
    compressor1.setOutletPressure(45.0, "bara");
    compressor1.setIsentropicEfficiency(0.78);
    process.add(compressor1);

    // Intercooler
    Cooler intercooler = new Cooler("Intercooler", compressor1.getOutletStream());
    intercooler.setOutTemperature(35.0, "C");
    process.add(intercooler);

    // Interstage separator
    Separator interstageSepa = new Separator("Interstage Separator", intercooler.getOutletStream());
    process.add(interstageSepa);

    // Second recycle stream
    Stream recycle2Stream = new Stream("Recycle 2 Stream", leanGasFluid.clone());
    recycle2Stream.setFlowRate(500.0, "kg/hr");
    recycle2Stream.setTemperature(45.0, "C");
    recycle2Stream.setPressure(45.0, "bara");
    process.add(recycle2Stream);

    // Second stage mixer
    Mixer mixer2 = new Mixer("Stage 2 Mixer");
    mixer2.addStream(interstageSepa.getGasOutStream());
    mixer2.addStream(recycle2Stream);
    process.add(mixer2);

    // Second stage compressor
    Compressor compressor2 = new Compressor("Stage 2 Compressor", mixer2.getOutletStream());
    compressor2.setOutletPressure(100.0, "bara");
    compressor2.setIsentropicEfficiency(0.76);
    process.add(compressor2);

    // Aftercooler
    Cooler aftercooler = new Cooler("Aftercooler", compressor2.getOutletStream());
    aftercooler.setOutTemperature(40.0, "C");
    process.add(aftercooler);

    // Final separator
    Separator finalSeparator = new Separator("Final Separator", aftercooler.getOutletStream());
    process.add(finalSeparator);

    // Split for recycle 1 (anti-surge first stage)
    Splitter splitter1 = new Splitter("Anti-surge Splitter 1", finalSeparator.getGasOutStream());
    splitter1.setSplitFactors(new double[] {0.95, 0.05});
    process.add(splitter1);

    // JT valve for first recycle
    ThrottlingValve jtValve1 = new ThrottlingValve("JT Valve 1", splitter1.getSplitStream(1));
    jtValve1.setOutletPressure(20.0, "bara");
    process.add(jtValve1);

    // First recycle
    Recycle recycle1 = new Recycle("Recycle 1");
    recycle1.addStream(jtValve1.getOutletStream());
    recycle1.setOutletStream(recycle1Stream);
    recycle1.setTolerance(1e-3);
    recycle1.setPriority(100); // Higher priority (solved first)
    recycle1.setAccelerationMethod(AccelerationMethod.WEGSTEIN);
    process.add(recycle1);

    // Split for recycle 2 (anti-surge second stage)
    Splitter splitter2 = new Splitter("Anti-surge Splitter 2", splitter1.getSplitStream(0));
    splitter2.setSplitFactors(new double[] {0.98, 0.02});
    process.add(splitter2);

    // JT valve for second recycle
    ThrottlingValve jtValve2 = new ThrottlingValve("JT Valve 2", splitter2.getSplitStream(1));
    jtValve2.setOutletPressure(45.0, "bara");
    process.add(jtValve2);

    // Second recycle
    Recycle recycle2 = new Recycle("Recycle 2");
    recycle2.addStream(jtValve2.getOutletStream());
    recycle2.setOutletStream(recycle2Stream);
    recycle2.setTolerance(1e-3);
    recycle2.setPriority(50); // Lower priority (solved second)
    recycle2.setAccelerationMethod(AccelerationMethod.WEGSTEIN);
    process.add(recycle2);

    // Build and analyze graph
    ProcessGraph graph = process.buildGraph();

    System.out.println("\n===== Two-Stage Compression Graph Analysis =====");
    System.out.println(graph.getSummary());

    // Get sensitivity analysis
    String sensitivityReport = graph.getSensitivityAnalysisReport();
    System.out.println(sensitivityReport);

    // Check SCCs
    ProcessGraph.SCCResult sccResult = graph.findStronglyConnectedComponents();
    System.out.println("Recycle loops detected: " + sccResult.getRecycleLoops().size());

    // Set up RecycleController with coordinated acceleration
    RecycleController controller = new RecycleController();
    controller.addRecycle(recycle1);
    controller.addRecycle(recycle2);
    controller.setUseCoordinatedAcceleration(true);
    controller.init();

    System.out.println("\nRecycleController setup:");
    System.out.println("  Recycles: " + controller.getRecycleCount());
    System.out.println("  Coordinated acceleration: " + controller.isUseCoordinatedAcceleration());

    // Run the process
    long startTime = System.currentTimeMillis();
    process.run();
    long endTime = System.currentTimeMillis();

    System.out.println("\nSimulation completed in " + (endTime - startTime) + " ms");
    System.out.println("Recycle 1 iterations: " + recycle1.getIterations());
    System.out.println("Recycle 2 iterations: " + recycle2.getIterations());
    System.out.println("Recycle 1 converged: " + recycle1.solved());
    System.out.println("Recycle 2 converged: " + recycle2.solved());

    // Get diagnostics
    System.out.println("\n" + controller.getConvergenceDiagnostics());

    // Verify convergence
    assertTrue(recycle1.solved(), "Recycle 1 should converge");
    assertTrue(recycle2.solved(), "Recycle 2 should converge");

    // Check compression ratio
    double compressionRatio = splitter2.getSplitStream(0).getPressure() / feedGas.getPressure();
    System.out.println("Overall compression ratio: " + String.format("%.2f", compressionRatio));
    assertTrue(compressionRatio > 4.5, "Compression ratio should be > 4.5");

    System.out.println("==================================================\n");
  }

  @Test
  @DisplayName("Gas recirculation loop simulation")
  void testRefrigerationCycle() {
    // Create a simple methane gas loop (simpler than phase-change refrigeration)
    SystemInterface gasFluid = new SystemSrkEos(298.0, 30.0);
    gasFluid.addComponent("methane", 0.95);
    gasFluid.addComponent("ethane", 0.05);
    gasFluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem("Gas Recirculation Loop");

    // Recycle stream (starting point)
    Stream recycleStream = new Stream("Recirculation Gas", gasFluid.clone());
    recycleStream.setFlowRate(5000.0, "kg/hr");
    recycleStream.setTemperature(25.0, "C");
    recycleStream.setPressure(30.0, "bara");
    process.add(recycleStream);

    // Compressor (boosting gas)
    Compressor compressor = new Compressor("Booster Compressor", recycleStream);
    compressor.setOutletPressure(60.0, "bara");
    compressor.setIsentropicEfficiency(0.75);
    process.add(compressor);

    // Aftercooler
    Cooler aftercooler = new Cooler("Aftercooler", compressor.getOutletStream());
    aftercooler.setOutTemperature(35.0, "C");
    process.add(aftercooler);

    // Separator (scrubber)
    Separator scrubber = new Separator("Scrubber", aftercooler.getOutletStream());
    process.add(scrubber);

    // Expander (turboexpander for pressure letdown)
    Compressor expander = new Compressor("Turboexpander", scrubber.getGasOutStream());
    expander.setOutletPressure(30.0, "bara");
    expander.setIsentropicEfficiency(0.78);
    process.add(expander);

    // Recycle to close the loop
    Recycle recycle = new Recycle("Gas Loop");
    recycle.addStream(expander.getOutletStream());
    recycle.setOutletStream(recycleStream);
    recycle.setTolerance(1e-3);
    recycle.setAccelerationMethod(AccelerationMethod.BROYDEN);
    process.add(recycle);

    // Build and analyze graph
    ProcessGraph graph = process.buildGraph();

    System.out.println("\n===== Gas Recirculation Loop Graph Analysis =====");
    System.out.println(graph.getSummary());
    System.out.println(graph.getSensitivityAnalysisReport());

    // Run simulation
    long startTime = System.currentTimeMillis();
    process.run();
    long endTime = System.currentTimeMillis();

    System.out.println("Simulation completed in " + (endTime - startTime) + " ms");
    System.out.println("Recycle iterations: " + recycle.getIterations());
    System.out.println("Recycle converged: " + recycle.solved());

    // Verify convergence
    assertTrue(recycle.solved(), "Gas recirculation loop should converge");

    // Check compressor power
    double compressorPower = compressor.getPower("kW");
    System.out.println("Compressor power: " + String.format("%.2f", compressorPower) + " kW");

    // Check expander power (negative means producing power)
    double expanderPower = expander.getPower("kW");
    System.out.println("Expander power: " + String.format("%.2f", expanderPower) + " kW");

    // Net power is compressor minus expander recovery
    double netPower = compressorPower + expanderPower; // expander power is negative
    System.out.println("Net power: " + String.format("%.2f", netPower) + " kW");

    assertTrue(compressorPower > 0, "Compressor should consume power");

    System.out.println("================================================\n");
  }

  @Test
  @DisplayName("Performance comparison: Individual vs Coordinated acceleration")
  void testAccelerationPerformanceComparison() {
    System.out.println("\n===== Acceleration Method Performance Comparison =====\n");

    // Test with Direct Substitution
    long dsTime = runCompressionWithMethod(AccelerationMethod.DIRECT_SUBSTITUTION);
    System.out.println("Direct Substitution: " + dsTime + " ms");

    // Test with Wegstein
    long wegsteinTime = runCompressionWithMethod(AccelerationMethod.WEGSTEIN);
    System.out.println("Wegstein: " + wegsteinTime + " ms");

    // Test with Broyden
    long broydenTime = runCompressionWithMethod(AccelerationMethod.BROYDEN);
    System.out.println("Broyden: " + broydenTime + " ms");

    System.out.println(
        "\nSpeedup Wegstein vs DS: " + String.format("%.2fx", (double) dsTime / wegsteinTime));
    System.out
        .println("Speedup Broyden vs DS: " + String.format("%.2fx", (double) dsTime / broydenTime));

    System.out.println("=======================================================\n");
  }

  private long runCompressionWithMethod(AccelerationMethod method) {
    ProcessSystem process = new ProcessSystem("Compression Test");

    Stream feedGas = new Stream("Feed", richGasFluid.clone());
    feedGas.setFlowRate(20000.0, "kg/hr");
    feedGas.setTemperature(25.0, "C");
    feedGas.setPressure(30.0, "bara");
    process.add(feedGas);

    Stream recycleStream = new Stream("Recycle Stream", leanGasFluid.clone());
    recycleStream.setFlowRate(2000.0, "kg/hr");
    recycleStream.setTemperature(35.0, "C");
    recycleStream.setPressure(30.0, "bara");
    process.add(recycleStream);

    Mixer mixer = new Mixer("Mixer");
    mixer.addStream(feedGas);
    mixer.addStream(recycleStream);
    process.add(mixer);

    Compressor compressor = new Compressor("Compressor", mixer.getOutletStream());
    compressor.setOutletPressure(80.0, "bara");
    compressor.setIsentropicEfficiency(0.75);
    process.add(compressor);

    Cooler cooler = new Cooler("Cooler", compressor.getOutletStream());
    cooler.setOutTemperature(35.0, "C");
    process.add(cooler);

    Separator separator = new Separator("Separator", cooler.getOutletStream());
    process.add(separator);

    Splitter splitter = new Splitter("Splitter", separator.getGasOutStream());
    splitter.setSplitFactors(new double[] {0.9, 0.1});
    process.add(splitter);

    ThrottlingValve valve = new ThrottlingValve("Valve", splitter.getSplitStream(1));
    valve.setOutletPressure(30.0, "bara");
    process.add(valve);

    Recycle recycle = new Recycle("Recycle");
    recycle.addStream(valve.getOutletStream());
    recycle.setOutletStream(recycleStream);
    recycle.setTolerance(1e-4);
    recycle.setAccelerationMethod(method);
    process.add(recycle);

    long startTime = System.currentTimeMillis();
    process.run();
    long endTime = System.currentTimeMillis();

    System.out.println("  " + method + ": " + recycle.getIterations() + " iterations, "
        + "converged=" + recycle.solved());

    return endTime - startTime;
  }
}
