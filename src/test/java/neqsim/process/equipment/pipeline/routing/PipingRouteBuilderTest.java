package neqsim.process.equipment.pipeline.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessConnection;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link PipingRouteBuilder}.
 */
class PipingRouteBuilderTest {

  /**
   * Verifies that a route table builds serial Beggs-and-Brill pipe units with topology metadata.
   */
  @Test
  void testBuildCreatesSerialPipeModel() {
    Stream feed = createFeedStream();
    PipingRouteBuilder builder =
        new PipingRouteBuilder().addSegment("S1", "Manifold", "Valve Station", 100.0, "m", 0.2, "m")
            .setSegmentElevationChange("S1", 10.0, "m").setSegmentWallThickness("S1", 8.0, "mm")
            .setDefaultPipeWallRoughness(45.0, "micrometer").addMinorLoss("S1", "manual valve", 1.0)
            .addSegment("S2", "Valve Station", "Compressor Scrubber", 25.0, "m", 8.0, "inch")
            .addMinorLoss("Valve Station->Compressor Scrubber", "long-radius bend", 0.3);

    ProcessSystem process = builder.build(feed);

    assertEquals(3, process.getUnitOperations().size());
    assertEquals(2, process.getConnections().size());
    ProcessConnection firstConnection = process.getConnections().get(0);
    assertEquals("feed", firstConnection.getSourceEquipment());
    assertEquals("Pipe S1 Manifold to Valve Station", firstConnection.getTargetEquipment());
    assertEquals(ProcessConnection.ConnectionType.MATERIAL, firstConnection.getType());

    PipeBeggsAndBrills firstPipe =
        (PipeBeggsAndBrills) process.getUnit("Pipe S1 Manifold to Valve Station");
    PipeBeggsAndBrills secondPipe =
        (PipeBeggsAndBrills) process.getUnit("Pipe S2 Valve Station to Compressor Scrubber");
    assertNotNull(firstPipe);
    assertNotNull(secondPipe);
    assertEquals(0.2, firstPipe.getDiameter(), 1.0e-12);
    assertEquals(0.2032, secondPipe.getDiameter(), 1.0e-12);
    assertEquals(50.0, firstPipe.getTotalFittingsLdRatio(), 1.0e-12);
    assertEquals(10.0, firstPipe.getEquivalentLength(), 1.0e-12);
  }

  /**
   * Verifies that the generated route can run as a process simulation.
   */
  @Test
  void testGeneratedRouteRunsAndDropsPressure() {
    Stream feed = createFeedStream();
    PipingRouteBuilder builder = new PipingRouteBuilder()
        .addSegment("S1", "A", "B", 75.0, "m", 0.20, "m").addMinorLoss("S1", "gate valve", 0.2)
        .addSegment("S2", "B", "C", 75.0, "m", 0.20, "m").setDefaultNumberOfIncrements(3);

    ProcessSystem process = builder.build(feed);
    process.run();

    PipeBeggsAndBrills outletPipe = (PipeBeggsAndBrills) process.getUnit("Pipe S2 B to C");
    assertTrue(outletPipe.getOutletStream().getPressure("bara") < feed.getPressure("bara"));
    assertTrue(outletPipe.getOutletStream().getPressure("bara") > 0.0);
  }

  /**
   * Verifies that a route can be inserted into a larger process and feed downstream equipment.
   */
  @Test
  void testAddToProcessSystemReturnsStreamForDownstreamEquipment() {
    Stream feed = createFeedStream();
    ProcessSystem process = new ProcessSystem("Full plant process");
    process.add(feed);
    PipingRouteBuilder route =
        new PipingRouteBuilder().addSegment("S1", "Feed", "Route outlet", 50.0, "m", 0.20, "m")
            .addMinorLoss("S1", "check valve", 0.5);

    StreamInterface routeOutlet = route.addToProcessSystem(process, feed);
    Cooler downstreamCooler = new Cooler("Downstream cooler", routeOutlet);
    downstreamCooler.setOutletTemperature(25.0, "C");
    process.add(downstreamCooler);
    process.connect(route.getSegment("S1").getPipeName(), "outlet", downstreamCooler.getName(),
        "inlet", ProcessConnection.ConnectionType.MATERIAL);

    process.run();

    assertEquals(3, process.getUnitOperations().size());
    assertEquals(2, process.getConnections().size());
    assertTrue(downstreamCooler.getOutletStream().getPressure("bara") < feed.getPressure("bara"));
    assertTrue(downstreamCooler.getOutletStream().getTemperature("C") < feed.getTemperature("C"));
  }

  /**
   * Verifies JSON export of geometry, assumptions, and minor-loss data.
   */
  @Test
  void testToJsonIncludesRouteAssumptions() {
    PipingRouteBuilder builder =
        new PipingRouteBuilder().addSegment("S1", "A", "B", 0.1, "km", 200.0, "mm")
            .setMinorLossFrictionFactor(0.025).addMinorLoss("A to B", "strainer", 2.5);

    String json = builder.toJson();

    assertTrue(json.contains("\"minorLossFrictionFactor\": 0.025"));
    assertTrue(json.contains("\"length_m\": 100.0"));
    assertTrue(json.contains("\"nominalDiameter_m\": 0.2"));
    assertTrue(json.contains("\"fittingType\": \"strainer\""));
    assertTrue(json.contains("\"equivalentLengthRatio\": 100.0"));
  }

  /**
   * Verifies the documented STID line-list route example.
   */
  @Test
  void testDocumentationLineListExample() {
    Stream feed = createFeedStream();
    PipingRouteBuilder route = new PipingRouteBuilder()
        .setDefaultPipeWallRoughness(45.0, "micrometer").setMinorLossFrictionFactor(0.02)
        .addSegment("ROUTE-001-S1", "Upstream manifold", "separator outlet", 120.0, "m", 0.508, "m")
        .setSegmentWallThickness("ROUTE-001-S1", 12.7, "mm")
        .addMinorLoss("ROUTE-001-S1", "gate valve", 0.15)
        .addSegment("ROUTE-001-S2", "separator outlet", "compressor scrubber", 65.0, "m", 0.508,
            "m")
        .setSegmentElevationChange("ROUTE-001-S2", 4.0, "m")
        .addMinorLoss("separator outlet->compressor scrubber", "long-radius bend", 0.20);

    ProcessSystem process = route.build(feed);
    process.run();

    PipeBeggsAndBrills lastPipe = (PipeBeggsAndBrills) process
        .getUnit("Pipe ROUTE-001-S2 separator outlet to compressor scrubber");
    assertTrue(lastPipe.getOutletStream().getPressure("bara") < feed.getPressure("bara"));
    assertTrue(route.toJson().contains("ROUTE-001-S1"));
  }

  /**
   * Verifies validation of unknown segment references.
   */
  @Test
  void testRejectsUnknownMinorLossSegment() {
    PipingRouteBuilder builder =
        new PipingRouteBuilder().addSegment("S1", "A", "B", 10.0, "m", 0.1, "m");

    assertThrows(IllegalArgumentException.class,
        () -> builder.addMinorLoss("unknown", "manual valve", 1.0));
  }

  /**
   * Creates a gas feed stream for route tests.
   *
   * @return initialized feed stream
   */
  private Stream createFeedStream() {
    SystemInterface gas = new SystemSrkEos(303.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(50.0, "bara");
    feed.run();
    return feed;
  }
}
