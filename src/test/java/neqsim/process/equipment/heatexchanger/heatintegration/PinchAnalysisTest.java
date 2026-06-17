package neqsim.process.equipment.heatexchanger.heatintegration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the PinchAnalysis class using a classic four-stream textbook example.
 *
 * <p>
 * Reference: Linnhoff, B. et al., "A User Guide on Process Integration for the Efficient Use of
 * Energy", IChemE, 1982. The standard four-stream problem is widely used in chemical engineering
 * courses for teaching pinch analysis.
 * </p>
 */
class PinchAnalysisTest {

  /**
   * Four-stream problem with heat surplus. Hot streams have more energy than cold streams need, so
   * hot utility should be zero and cold utility equals the net surplus.
   *
   * <p>
   * Hot streams:
   * </p>
   * <ul>
   * <li>H1: 180 C to 80 C, MCp = 30 kW/K, duty = 3000 kW</li>
   * <li>H2: 150 C to 50 C, MCp = 15 kW/K, duty = 1500 kW</li>
   * </ul>
   *
   * <p>
   * Cold streams:
   * </p>
   * <ul>
   * <li>C1: 30 C to 140 C, MCp = 20 kW/K, duty = 2200 kW</li>
   * <li>C2: 60 C to 120 C, MCp = 25 kW/K, duty = 1500 kW</li>
   * </ul>
   *
   * <p>
   * Total hot = 4500, Total cold = 3700, Net surplus = 800 kW. With deltaT_min = 10 C.
   * </p>
   */
  @Test
  void testFourStreamProblemWithSurplus() {
    PinchAnalysis pinch = new PinchAnalysis(10.0);
    pinch.addHotStream("H1", 180, 80, 30);
    pinch.addHotStream("H2", 150, 50, 15);
    pinch.addColdStream("C1", 30, 140, 20);
    pinch.addColdStream("C2", 60, 120, 25);
    pinch.run();

    double qHot = pinch.getMinimumHeatingUtility();
    double qCold = pinch.getMinimumCoolingUtility();

    // Hot utility should be >= 0
    assertTrue(qHot >= 0.0, "Hot utility must be non-negative");

    // Cold utility should be positive (heat surplus needs cooling)
    assertTrue(qCold > 0, "Cold utility should be > 0 for this problem");

    // Net surplus: Qcold - Qhot should equal total hot - total cold = 800
    assertEquals(800.0, qCold - qHot, 1.0, "Net heat surplus should be approximately 800 kW");

    // Maximum heat recovery
    double maxRecovery = pinch.getMaximumHeatRecovery();
    assertTrue(maxRecovery > 0, "Max heat recovery should be positive");
  }

  /**
   * Four-stream problem that requires both hot and cold utility. This uses a problem where cold
   * streams need more energy than hot streams provide.
   */
  @Test
  void testFourStreamProblemNeedingBothUtilities() {
    PinchAnalysis pinch = new PinchAnalysis(10.0);
    // Hot streams: total = 10*(175-45) + 40*(125-65) = 1300 + 2400 = 3700 kW
    pinch.addHotStream("H1", 175, 45, 10);
    pinch.addHotStream("H2", 125, 65, 40);
    // Cold streams: total = 20*(155-20) + 15*(112-40) = 2700 + 1080 = 3780 kW
    pinch.addColdStream("C1", 20, 155, 20);
    pinch.addColdStream("C2", 40, 112, 15);
    pinch.run();

    double qHot = pinch.getMinimumHeatingUtility();
    double qCold = pinch.getMinimumCoolingUtility();

    // Both should be non-negative
    assertTrue(qHot >= 0, "Hot utility must be >= 0");
    assertTrue(qCold >= 0, "Cold utility must be >= 0");

    // Net deficit = 3780 - 3700 = 80 kW, so Qhot - Qcold = 80
    assertEquals(80.0, qHot - qCold, 1.0, "Net heat deficit should be approximately 80 kW");
  }

  /**
   * Test with a simple two-stream problem where hot stream has more energy than cold stream needs.
   */
  @Test
  void testTwoStreamProblem() {
    PinchAnalysis pinch = new PinchAnalysis(10.0);
    pinch.addHotStream("H1", 200, 100, 10); // duty = 10 * 100 = 1000 kW
    pinch.addColdStream("C1", 50, 150, 8); // duty = 8 * 100 = 800 kW
    pinch.run();

    double qHot = pinch.getMinimumHeatingUtility();
    double qCold = pinch.getMinimumCoolingUtility();

    // Net surplus = 1000 - 800 = 200 kW
    assertEquals(200.0, qCold - qHot, 50.0, "Net surplus should be approximately 200 kW");

    // Both utilities should be non-negative
    assertTrue(qHot >= 0.0, "Hot utility must be >= 0");
    assertTrue(qCold >= 0.0, "Cold utility must be >= 0");
  }

  /**
   * Test stream counts.
   */
  @Test
  void testStreamCounts() {
    PinchAnalysis pinch = new PinchAnalysis(10.0);
    assertEquals(0, pinch.getNumberOfHotStreams());
    assertEquals(0, pinch.getNumberOfColdStreams());

    pinch.addHotStream("H1", 200, 100, 10);
    pinch.addColdStream("C1", 50, 150, 8);
    pinch.addColdStream("C2", 60, 120, 5);

    assertEquals(1, pinch.getNumberOfHotStreams());
    assertEquals(2, pinch.getNumberOfColdStreams());
  }

  /**
   * Test adding HeatStream objects directly.
   */
  @Test
  void testAddHeatStreamDirectly() {
    PinchAnalysis pinch = new PinchAnalysis(10.0);
    HeatStream hot = new HeatStream("H1", 200, 100, 10);
    HeatStream cold = new HeatStream("C1", 50, 180, 12);

    pinch.addStream(hot);
    pinch.addStream(cold);

    assertEquals(1, pinch.getNumberOfHotStreams());
    assertEquals(1, pinch.getNumberOfColdStreams());

    pinch.run();
    assertTrue(pinch.getMinimumHeatingUtility() >= 0);
    assertTrue(pinch.getMinimumCoolingUtility() >= 0);
  }

  /**
   * Test JSON output.
   */
  @Test
  void testJsonOutput() {
    PinchAnalysis pinch = new PinchAnalysis(10.0);
    pinch.addHotStream("H1", 180, 80, 30);
    pinch.addColdStream("C1", 30, 140, 20);
    pinch.run();

    String json = pinch.toJson();
    assertNotNull(json);
    assertTrue(json.contains("minimumHeatingUtility_kW"));
    assertTrue(json.contains("minimumCoolingUtility_kW"));
    assertTrue(json.contains("pinchTemperatureHot_C"));
    assertTrue(json.contains("deltaTmin_C"));
  }

  /**
   * Test that calling getters before run() throws exception.
   */
  @Test
  void testGetBeforeRunThrows() {
    PinchAnalysis pinch = new PinchAnalysis(10.0);
    pinch.addHotStream("H1", 180, 80, 30);
    assertThrows(IllegalStateException.class, () -> pinch.getMinimumHeatingUtility());
  }

  /**
   * Test composite curves are produced.
   */
  @Test
  void testCompositeCurves() {
    PinchAnalysis pinch = new PinchAnalysis(10.0);
    pinch.addHotStream("H1", 180, 80, 30);
    pinch.addHotStream("H2", 150, 50, 15);
    pinch.addColdStream("C1", 30, 140, 20);
    pinch.addColdStream("C2", 60, 120, 25);
    pinch.run();

    assertNotNull(pinch.getHotCompositeCurve());
    assertNotNull(pinch.getColdCompositeCurve());
    assertNotNull(pinch.getGrandCompositeCurve());

    double[] hotQ = pinch.getHotCompositeCurve().get("Q_kW");
    double[] hotT = pinch.getHotCompositeCurve().get("T_K");
    assertNotNull(hotQ);
    assertNotNull(hotT);
    assertEquals(hotQ.length, hotT.length);
    assertTrue(hotQ.length >= 2, "Composite curve should have at least 2 points");

    // Enthalpy should be monotonically increasing
    for (int i = 1; i < hotQ.length; i++) {
      assertTrue(hotQ[i] >= hotQ[i - 1], "Cumulative enthalpy should be monotonically increasing");
    }
  }

  /**
   * Test addProcessStream with a NeqSim stream object. Adds a gas stream that needs cooling from
   * 100 C to 30 C and verifies it appears as a hot stream in the analysis.
   */
  @Test
  void testAddProcessStream() {
    SystemInterface gas = new SystemSrkEos(273.15 + 100.0, 50.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    Stream feed = new Stream("Hot Gas", gas);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();

    PinchAnalysis pinch = new PinchAnalysis(10.0);
    pinch.addProcessStream("Hot Gas", feed, 30.0); // cool from 100 to 30 C
    pinch.addColdStream("Cold Water", 20.0, 80.0, 50.0); // manual cold stream

    assertEquals(1, pinch.getNumberOfHotStreams(), "Process stream should be added as hot");
    assertEquals(1, pinch.getNumberOfColdStreams());

    pinch.run();
    assertTrue(pinch.getMinimumHeatingUtility() >= 0.0);
    assertTrue(pinch.getMinimumCoolingUtility() >= 0.0);
  }

  /**
   * Test fromProcessSystem with Heater and Cooler equipment. Creates a simple process with a heater
   * and cooler, runs it, then extracts pinch data.
   */
  @Test
  void testFromProcessSystemWithHeaterAndCooler() {
    // Create two separate fluid systems - one to be cooled, one to be heated
    SystemInterface hotFluid = new SystemSrkEos(273.15 + 150.0, 30.0);
    hotFluid.addComponent("methane", 0.9);
    hotFluid.addComponent("ethane", 0.1);
    hotFluid.setMixingRule("classic");

    SystemInterface coldFluid = new SystemSrkEos(273.15 + 30.0, 30.0);
    coldFluid.addComponent("methane", 0.9);
    coldFluid.addComponent("ethane", 0.1);
    coldFluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();

    Stream hotFeed = new Stream("Hot Feed", hotFluid);
    hotFeed.setFlowRate(5000.0, "kg/hr");
    process.add(hotFeed);

    Cooler cooler = new Cooler("Cooler-1", hotFeed);
    cooler.setOutTemperature(273.15 + 40.0);
    process.add(cooler);

    Stream coldFeed = new Stream("Cold Feed", coldFluid);
    coldFeed.setFlowRate(5000.0, "kg/hr");
    process.add(coldFeed);

    Heater heater = new Heater("Heater-1", coldFeed);
    heater.setOutTemperature(273.15 + 100.0);
    process.add(heater);

    process.run();

    // Extract pinch analysis
    PinchAnalysis pinch = PinchAnalysis.fromProcessSystem(process, 10.0);

    assertTrue(pinch.getNumberOfHotStreams() >= 1, "Should find at least 1 hot stream (cooler)");
    assertTrue(pinch.getNumberOfColdStreams() >= 1, "Should find at least 1 cold stream (heater)");

    pinch.run();
    assertTrue(pinch.getMinimumHeatingUtility() >= 0.0);
    assertTrue(pinch.getMinimumCoolingUtility() >= 0.0);
    assertTrue(pinch.getMaximumHeatRecovery() > 0.0,
        "Heat recovery should be possible when hot and cold duties overlap");

    // Verify JSON contains all streams
    String json = pinch.toJson();
    assertNotNull(json);
    assertTrue(json.contains("Cooler-1") || json.contains("Heater-1"),
        "JSON should reference the equipment names");
  }

  /**
   * Test addStreamsFromHeatExchanger with a two-stream HeatExchanger.
   */
  @Test
  void testAddStreamsFromTwoStreamHeatExchanger() {
    SystemInterface hotFluid = new SystemSrkEos(273.15 + 200.0, 30.0);
    hotFluid.addComponent("methane", 0.9);
    hotFluid.addComponent("ethane", 0.1);
    hotFluid.setMixingRule("classic");

    SystemInterface coldFluid = new SystemSrkEos(273.15 + 30.0, 30.0);
    coldFluid.addComponent("methane", 0.9);
    coldFluid.addComponent("ethane", 0.1);
    coldFluid.setMixingRule("classic");

    Stream hotStream = new Stream("HX Hot In", hotFluid);
    hotStream.setFlowRate(5000.0, "kg/hr");

    Stream coldStream = new Stream("HX Cold In", coldFluid);
    coldStream.setFlowRate(5000.0, "kg/hr");

    HeatExchanger hx = new HeatExchanger("HX-100", hotStream, coldStream);
    hx.setGuessOutTemperature(273.15 + 80.0);

    ProcessSystem process = new ProcessSystem();
    process.add(hotStream);
    process.add(coldStream);
    process.add(hx);
    process.run();

    PinchAnalysis pinch = new PinchAnalysis(10.0);
    pinch.addStreamsFromHeatExchanger(hx);

    // Should have extracted at least one hot and one cold stream
    int totalStreams = pinch.getNumberOfHotStreams() + pinch.getNumberOfColdStreams();
    assertTrue(totalStreams >= 2,
        "Should extract at least 2 streams from a two-stream HX, got " + totalStreams);

    // Add a manual stream and run
    pinch.addColdStream("External cold", 20.0, 60.0, 10.0);
    pinch.run();
    // Allow tiny floating-point rounding error (e.g. -5.7e-14)
    assertTrue(pinch.getMinimumCoolingUtility() >= -1e-6,
        "Cooling utility should be >= 0, got " + pinch.getMinimumCoolingUtility());
  }

  /**
   * Test that fromProcessSystem correctly identifies Cooler as hot stream. A cooler removes heat
   * from the process, so the process side should be a hot stream in pinch terms.
   */
  @Test
  void testCoolerIdentifiedAsHotStream() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 120.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();
    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    process.add(feed);

    Cooler cooler = new Cooler("Aftercooler", feed);
    cooler.setOutTemperature(273.15 + 35.0);
    process.add(cooler);

    process.run();

    PinchAnalysis pinch = PinchAnalysis.fromProcessSystem(process, 10.0);
    assertEquals(1, pinch.getNumberOfHotStreams(),
        "Cooler process-side should be classified as hot stream");
    assertEquals(0, pinch.getNumberOfColdStreams(), "No cold streams expected");
  }
}
