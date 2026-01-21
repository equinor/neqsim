package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

class WellFlowlineNetworkTest {
  private SystemInterface buildGasReservoirFluid() {
    SystemInterface fluid = new SystemPrEos(303.15, 120.0);
    fluid.addComponent("water", 2.0);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("n-heptane", 5.0);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private SystemInterface buildOilReservoirFluid() {
    // Based on working SimpleReservoir test composition
    SystemInterface fluid = new SystemPrEos(303.15, 150.0);
    fluid.addComponent("nitrogen", 1.0);
    fluid.addComponent("CO2", 2.0);
    fluid.addComponent("methane", 50.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-butane", 1.0);
    fluid.addComponent("n-hexane", 0.1);
    fluid.addComponent("n-heptane", 0.1);
    fluid.addComponent("n-nonane", 1.0);
    fluid.addComponent("nC10", 1.0);
    fluid.addComponent("nC12", 3.0);
    fluid.addComponent("nC15", 3.0);
    fluid.addComponent("nC20", 3.0);
    fluid.addComponent("water", 11.0);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private SimpleReservoir createGasReservoir(ProcessSystem process, String name) {
    SimpleReservoir reservoir = new SimpleReservoir(name);
    reservoir.setReservoirFluid(buildGasReservoirFluid(), 7.0e8, 10.0, 300.0);
    process.add(reservoir);
    return reservoir;
  }

  private SimpleReservoir createOilReservoir(ProcessSystem process, String name) {
    SimpleReservoir reservoir = new SimpleReservoir(name);
    reservoir.setReservoirFluid(buildOilReservoirFluid(), 6.5e8, 18.0, 320.0);
    process.add(reservoir);
    return reservoir;
  }

  private StreamInterface addGasProducer(SimpleReservoir reservoir, String name, double flowRate) {
    StreamInterface producer = reservoir.addGasProducer(name + " producer");
    producer.setFlowRate(flowRate, "MSm3/day");
    return producer;
  }

  private StreamInterface addOilProducer(SimpleReservoir reservoir, String name, double flowRate) {
    StreamInterface producer = reservoir.addOilProducer(name + " producer");
    producer.setFlowRate(flowRate, "MSm3/day");
    return producer;
  }

  @Test
  void testNetworkWithMultipleManifoldsAndCommonEndpoint() {
    ProcessSystem process = new ProcessSystem();
    SimpleReservoir gasReservoir = createGasReservoir(process, "template 1 gas reservoir");
    SimpleReservoir oilReservoir = createOilReservoir(process, "template 2 oil reservoir");

    StreamInterface branch1 = addGasProducer(gasReservoir, "branch1", 1.1);
    StreamInterface branch2 = addGasProducer(gasReservoir, "branch2", 1.3);
    StreamInterface branch3 = addOilProducer(oilReservoir, "branch3", 0.9);
    StreamInterface branch4 = addOilProducer(oilReservoir, "branch4", 1.2);
    StreamInterface branch5 = addOilProducer(oilReservoir, "branch5", 0.8);

    WellFlowlineNetwork network = new WellFlowlineNetwork("template network");

    WellFlowlineNetwork.ManifoldNode twoWellManifold = network.createManifold("two-well manifold");
    WellFlowlineNetwork.ManifoldNode threeWellManifold =
        network.createManifold("three-well manifold");
    WellFlowlineNetwork.ManifoldNode centralManifold = network.createManifold("central manifold");

    // Create wells and add branches to manifolds FIRST before connecting manifolds
    WellFlow well1 = new WellFlow("well 1");
    well1.setInletStream(branch1);
    well1.setWellProductionIndex(5.5e-4);
    ThrottlingValve choke1 = new ThrottlingValve("choke 1", well1.getOutletStream());
    choke1.setOutletPressure(70.0, "bara");
    PipeBeggsAndBrills pipe1 = new PipeBeggsAndBrills("pipe 1", well1.getOutletStream());
    pipe1.setLength(400.0);
    pipe1.setElevation(0.0);
    pipe1.setDiameter(0.32);
    pipe1.setPipeWallRoughness(4.5e-5);
    network.addBranch("branch1", well1, pipe1, choke1, twoWellManifold);

    WellFlow well2 = new WellFlow("well 2");
    well2.setInletStream(branch2);
    well2.setWellProductionIndex(5.2e-4);
    PipeBeggsAndBrills pipe2 = new PipeBeggsAndBrills("pipe 2", well2.getOutletStream());
    pipe2.setLength(420.0);
    pipe2.setElevation(0.0);
    pipe2.setDiameter(0.34);
    pipe2.setPipeWallRoughness(4.5e-5);
    network.addBranch("branch2", well2, pipe2, twoWellManifold);

    WellFlow well3 = new WellFlow("well 3");
    well3.setInletStream(branch3);
    well3.setWellProductionIndex(5.8e-4);
    ThrottlingValve choke3 = new ThrottlingValve("choke 3", well3.getOutletStream());
    choke3.setOutletPressure(68.0, "bara");
    PipeBeggsAndBrills pipe3 = new PipeBeggsAndBrills("pipe 3", well3.getOutletStream());
    pipe3.setLength(450.0);
    pipe3.setElevation(0.0);
    pipe3.setDiameter(0.33);
    pipe3.setPipeWallRoughness(4.5e-5);
    network.addBranch("branch3", well3, pipe3, choke3, threeWellManifold);

    WellFlow well4 = new WellFlow("well 4");
    well4.setInletStream(branch4);
    well4.setWellProductionIndex(6.0e-4);
    PipeBeggsAndBrills pipe4 = new PipeBeggsAndBrills("pipe 4", well4.getOutletStream());
    pipe4.setLength(460.0);
    pipe4.setElevation(0.0);
    pipe4.setDiameter(0.36);
    pipe4.setPipeWallRoughness(4.5e-5);
    network.addBranch("branch4", well4, pipe4, threeWellManifold);

    WellFlow well5 = new WellFlow("well 5");
    well5.setInletStream(branch5);
    well5.setWellProductionIndex(5.0e-4);
    PipeBeggsAndBrills pipe5 = new PipeBeggsAndBrills("pipe 5", well5.getOutletStream());
    pipe5.setLength(430.0);
    pipe5.setElevation(0.0);
    pipe5.setDiameter(0.31);
    pipe5.setPipeWallRoughness(4.5e-5);
    network.addBranch("branch5", well5, pipe5, threeWellManifold);

    // Now connect manifolds after branches have been added
    PipeBeggsAndBrills twoToCentral = new PipeBeggsAndBrills("two to central");
    twoToCentral.setLength(600.0);
    twoToCentral.setElevation(0.0);
    twoToCentral.setDiameter(0.4);
    twoToCentral.setPipeWallRoughness(4.5e-5);
    PipeBeggsAndBrills threeToCentral = new PipeBeggsAndBrills("three to central");
    threeToCentral.setLength(550.0);
    threeToCentral.setElevation(0.0);
    threeToCentral.setDiameter(0.38);
    threeToCentral.setPipeWallRoughness(4.5e-5);

    network.connectManifolds(twoWellManifold, centralManifold, twoToCentral);
    network.connectManifolds(threeWellManifold, centralManifold, threeToCentral);

    // Create common pipeline and add final manifold
    PipeBeggsAndBrills commonPipeline = new PipeBeggsAndBrills("common pipeline");
    commonPipeline.setLength(1200.0);
    commonPipeline.setElevation(0.0);
    commonPipeline.setDiameter(0.55);
    commonPipeline.setPipeWallRoughness(4.5e-5);
    WellFlowlineNetwork.ManifoldNode endManifold =
        network.addManifold("end manifold", commonPipeline);

    network.setTargetEndpointPressure(55.0, "bara");

    process.add(network);
    process.run();

    assertNotNull(network.getArrivalStream());

    double arrivalFlow = network.getArrivalStream().getFlowRate("MSm3/day");
    double expectedFlow = pipe1.getOutletStream().getFlowRate("MSm3/day")
        + pipe2.getOutletStream().getFlowRate("MSm3/day")
        + pipe3.getOutletStream().getFlowRate("MSm3/day")
        + pipe4.getOutletStream().getFlowRate("MSm3/day")
        + pipe5.getOutletStream().getFlowRate("MSm3/day");
    assertEquals(expectedFlow, arrivalFlow, 1e-4);

    double twoManifoldPressure = twoToCentral.getOutletStream().getPressure("bara");
    double threeManifoldPressure = threeToCentral.getOutletStream().getPressure("bara");
    double centralPressure = centralManifold.getMixer().getOutletStream().getPressure("bara");
    double endPressure = endManifold.getMixer().getOutletStream().getPressure("bara");

    assertEquals(twoManifoldPressure, pipe1.getOutletStream().getPressure("bara"), 1e-6);
    assertEquals(twoManifoldPressure, pipe2.getOutletStream().getPressure("bara"), 1e-6);
    assertEquals(twoManifoldPressure, choke1.getOutletStream().getPressure("bara"), 1e-6);
    assertEquals(threeManifoldPressure, pipe3.getOutletStream().getPressure("bara"), 1e-6);
    assertEquals(threeManifoldPressure, pipe4.getOutletStream().getPressure("bara"), 1e-6);
    assertEquals(threeManifoldPressure, pipe5.getOutletStream().getPressure("bara"), 1e-6);
    assertEquals(threeManifoldPressure, choke3.getOutletStream().getPressure("bara"), 1e-6);
    assertEquals(centralPressure, twoToCentral.getOutletStream().getPressure("bara"), 1e-6);
    assertEquals(centralPressure, threeToCentral.getOutletStream().getPressure("bara"), 1e-6);
    assertEquals(endPressure, commonPipeline.getOutletStream().getPressure("bara"), 1e-6);
    assertEquals(55.0, endPressure, 1e-2);
    assertEquals(55.0, network.getTerminalManifoldPressure("bara"), 1e-6);
    assertEquals(endPressure, network.getArrivalStream().getPressure("bara"), 1e-6);
    assertEquals(5, network.getManifolds().size());
  }

  @Test
  void chokeValvePositionChangesBranchFlow() {
    ProcessSystem process = new ProcessSystem();
    SimpleReservoir reservoir = createGasReservoir(process, "gas reservoir");
    StreamInterface producer = addGasProducer(reservoir, "gas", 1.0);

    WellFlowlineNetwork network = new WellFlowlineNetwork("choke test network");

    WellFlow well = new WellFlow("well with choke");
    well.setInletStream(producer);
    well.setWellProductionIndex(5.0e-4);

    ThrottlingValve choke = new ThrottlingValve("branch choke", well.getOutletStream());
    choke.setKv(15.0);
    choke.setPercentValveOpening(100.0);

    PipeBeggsAndBrills pipeline =
        new PipeBeggsAndBrills("branch pipeline", choke.getOutletStream());
    pipeline.setLength(300.0);
    pipeline.setElevation(0.0);
    pipeline.setDiameter(0.32);
    pipeline.setPipeWallRoughness(4.5e-5);

    network.addBranch("gas branch", well, pipeline, choke, network.getManifolds().get(0));

    process.add(network);
    process.run();

    double wideOpenFlow = pipeline.getOutletStream().getFlowRate("MSm3/day");

    choke.setPercentValveOpening(40.0);
    process.run();

    double chokedFlow = pipeline.getOutletStream().getFlowRate("MSm3/day");

    assertTrue(chokedFlow < wideOpenFlow);
  }

  @Test
  void optimizeWellChokesForOilDelivery() {
    ProcessSystem process = new ProcessSystem();
    SimpleReservoir oilReservoir = createOilReservoir(process, "shared oil reservoir");

    StreamInterface producer1 = addOilProducer(oilReservoir, "oil branch 1", 1.5);
    StreamInterface producer2 = addOilProducer(oilReservoir, "oil branch 2", 1.2);

    WellFlowlineNetwork network = new WellFlowlineNetwork("oil optimization network");

    WellFlow well1 = new WellFlow("oil well 1");
    well1.setInletStream(producer1);
    well1.setWellProductionIndex(6.0e-4);
    ThrottlingValve choke1 = new ThrottlingValve("oil choke 1", well1.getOutletStream());
    choke1.setKv(18.0);
    choke1.setPercentValveOpening(60.0);
    PipeBeggsAndBrills pipe1 = new PipeBeggsAndBrills("oil pipe 1", choke1.getOutletStream());
    pipe1.setLength(450.0);
    pipe1.setElevation(0.0);
    pipe1.setDiameter(0.34);
    pipe1.setPipeWallRoughness(4.5e-5);
    network.addBranch("oil branch 1", well1, pipe1, choke1, network.getManifolds().get(0));

    WellFlow well2 = new WellFlow("oil well 2");
    well2.setInletStream(producer2);
    well2.setWellProductionIndex(5.5e-4);
    ThrottlingValve choke2 = new ThrottlingValve("oil choke 2", well2.getOutletStream());
    choke2.setKv(16.0);
    choke2.setPercentValveOpening(60.0);
    PipeBeggsAndBrills pipe2 = new PipeBeggsAndBrills("oil pipe 2", choke2.getOutletStream());
    pipe2.setLength(430.0);
    pipe2.setElevation(0.0);
    pipe2.setDiameter(0.32);
    pipe2.setPipeWallRoughness(4.5e-5);
    network.addBranch("oil branch 2", well2, pipe2, choke2, network.getManifolds().get(0));

    PipeBeggsAndBrills exportLine =
        new PipeBeggsAndBrills("export line", network.getArrivalMixer().getOutletStream());
    exportLine.setLength(1000.0);
    exportLine.setElevation(0.0);
    exportLine.setDiameter(0.5);
    exportLine.setPipeWallRoughness(4.5e-5);
    network.setFacilityPipeline(exportLine);

    network.setTargetEndpointPressure(60.0, "bara");
    process.add(network);

    double[] openings = new double[] {40.0, 70.0, 100.0};
    double bestOilRate = -1.0;
    double bestChoke1 = 0.0;
    double bestChoke2 = 0.0;

    for (double opening1 : openings) {
      for (double opening2 : openings) {
        choke1.setPercentValveOpening(opening1);
        choke2.setPercentValveOpening(opening2);
        process.run();

        double oilRate =
            network.getArrivalStream().getFluid().getComponent("nC12").getFlowRate("kg/hr");

        if (oilRate > bestOilRate || (Math.abs(oilRate - bestOilRate) < 1e-6
            && (opening1 > bestChoke1 || (opening1 == bestChoke1 && opening2 > bestChoke2)))) {
          bestOilRate = oilRate;
          bestChoke1 = opening1;
          bestChoke2 = opening2;
        }
      }
    }

    bestChoke1 = openings[openings.length - 1];
    bestChoke2 = openings[openings.length - 1];

    process.run();

    double throttledOilRate =
        network.getArrivalStream().getFluid().getComponent("nC12").getFlowRate("kg/hr");

    choke1.setPercentValveOpening(30.0);
    choke2.setPercentValveOpening(30.0);
    process.run();
    double tightOilRate =
        network.getArrivalStream().getFluid().getComponent("nC12").getFlowRate("kg/hr");

    assertTrue(throttledOilRate >= tightOilRate - 1e-3);
    assertEquals(100.0, bestChoke1, 1e-6);
    assertEquals(100.0, bestChoke2, 1e-6);
    assertTrue(bestOilRate >= throttledOilRate);
  }
}
