package neqsim.process.costestimation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.filter.Filter;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.reactor.GibbsReactor;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests guarding the reservoir-to-market weight and CAPEX coverage of the built-in NeqSim cost estimators.
 *
 * <p>
 * These tests verify that (1) process vessels report a finite, non-zero weight and a sane installed cost per kilogram
 * (guarding against the volume-vs-weight magnitude bug), (2) flowlines/pipelines report a non-zero steel weight, and
 * (3) wells contribute a rough all-in drilling-and-completion CAPEX that is not inflated by module factors.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class CostWeightCoverageTest {

  /**
   * Build a small gas fluid for the test streams.
   *
   * @return a simple three-component SRK gas system
   */
  private SystemInterface makeGas() {
    SystemInterface gas = new SystemSrkEos(288.15, 60.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");
    return gas;
  }

  @Test
  void testSeparatorWeightAndSaneCostPerKg() {
    Stream feed = new Stream("Feed", makeGas());
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(60.0, "bara");
    feed.run();

    Separator sep = new Separator("HP Separator", feed);
    sep.run();

    sep.initMechanicalDesign();
    MechanicalDesign md = sep.getMechanicalDesign();
    md.calcDesign();
    md.calculateCostEstimate();

    double weight = md.getWeightTotal();
    double pec = md.getCostEstimate().getPurchasedEquipmentCost();

    assertTrue(weight > 0.0, "Separator shell weight should be positive");
    assertTrue(pec > 0.0, "Separator purchased equipment cost should be positive");

    CostEstimateResult result = md.getCostEstimate().getDetailedEstimateResult();
    assertTrue(result.getBasis().getEstimateClass() == EstimateClass.CLASS_4,
        "Default unit estimate should state Class 4 basis");
    assertTrue(result.getCapitalCosts().get("purchasedEquipmentCost") > 0.0,
        "Detailed result should carry purchased equipment cost");
    assertTrue(result.getMaterialTakeOff().size() > 0, "Detailed result should include material take-off lines");
    assertTrue(result.getMaterialTakeOff().get(0).getWeightKg() > 0.0, "First MTO item should carry positive weight");

    double costPerKg = pec / weight;
    // Guard against the volume-vs-weight magnitude bug (~$5000-6200/kg). A realistic
    // carbon-steel pressure vessel PEC is roughly $10-1500 per kg of shell weight.
    assertTrue(costPerKg < 2000.0,
        "Separator PEC per kg unrealistically high (" + costPerKg + " $/kg) - magnitude bug?");
    assertTrue(costPerKg > 1.0, "Separator PEC per kg unrealistically low (" + costPerKg + " $/kg)");
  }

  @Test
  void testCompressorReportsWeightAndCost() {
    Stream feed = new Stream("Feed", makeGas());
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(60.0, "bara");
    feed.run();

    Compressor comp = new Compressor("Export Compressor", feed);
    comp.setOutletPressure(120.0);
    comp.run();

    comp.initMechanicalDesign();
    MechanicalDesign md = comp.getMechanicalDesign();
    md.calcDesign();
    md.calculateCostEstimate();

    assertTrue(md.getWeightTotal() > 0.0, "Compressor weight should be positive");
    assertTrue(md.getCostEstimate().getTotalCost() > 0.0, "Compressor cost should be positive");
  }

  @Test
  void testPipelineReportsSteelWeight() {
    Stream feed = new Stream("Feed", makeGas());
    feed.setFlowRate(80000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(80.0, "bara");
    feed.run();

    PipeBeggsAndBrills flowline = new PipeBeggsAndBrills("Flowline", feed);
    flowline.setLength(5000.0);
    flowline.setElevation(0.0);
    flowline.setDiameter(0.3);
    flowline.run();

    flowline.initMechanicalDesign();
    MechanicalDesign md = flowline.getMechanicalDesign();
    md.calcDesign();

    assertTrue(md.getWeightTotal() > 0.0,
        "Pipeline steel weight should be positive after wiring calculateWeightsAndAreas");
  }

  @Test
  void testMixerHeaderReportsWeightAndCost() {
    Stream feedA = new Stream("Feed A", makeGas());
    feedA.setFlowRate(30000.0, "kg/hr");
    feedA.setTemperature(25.0, "C");
    feedA.setPressure(60.0, "bara");
    feedA.run();

    Stream feedB = new Stream("Feed B", makeGas());
    feedB.setFlowRate(25000.0, "kg/hr");
    feedB.setTemperature(25.0, "C");
    feedB.setPressure(60.0, "bara");
    feedB.run();

    Mixer mixer = new Mixer("Topside Production Header");
    mixer.addStream(feedA);
    mixer.addStream(feedB);
    mixer.run();

    mixer.initMechanicalDesign();
    MechanicalDesign md = mixer.getMechanicalDesign();
    md.calcDesign();
    md.calculateCostEstimate();

    assertTrue(md.getWeightTotal() > 0.0, "Mixer/header steel weight should be positive");
    assertTrue(md.getCostEstimate().getPurchasedEquipmentCost() > 0.0,
        "Mixer/header purchased cost should be positive");
  }

  @Test
  void testSplitterHeaderReportsWeightAndCost() {
    Stream feed = new Stream("Feed", makeGas());
    feed.setFlowRate(55000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(60.0, "bara");
    feed.run();

    Splitter splitter = new Splitter("Topside Distribution Header", feed, 3);
    splitter.run();

    splitter.initMechanicalDesign();
    MechanicalDesign md = splitter.getMechanicalDesign();
    md.calcDesign();
    md.calculateCostEstimate();

    assertTrue(md.getWeightTotal() > 0.0, "Splitter/header steel weight should be positive");
    assertTrue(md.getCostEstimate().getPurchasedEquipmentCost() > 0.0,
        "Splitter/header purchased cost should be positive");
  }

  @Test
  void testFilterVesselReportsWeightAndCost() {
    Stream feed = new Stream("Feed", makeGas());
    feed.setFlowRate(30000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(60.0, "bara");
    feed.run();

    Filter filter = new Filter("Topside Coalescing Filter", feed);
    filter.run();

    filter.initMechanicalDesign();
    MechanicalDesign md = filter.getMechanicalDesign();
    md.calcDesign();
    md.calculateCostEstimate();

    assertTrue(md.getWeightTotal() > 0.0, "Filter vessel equipped weight should be positive");
    assertTrue(md.getCostEstimate().getPurchasedEquipmentCost() > 0.0,
        "Filter purchased equipment cost should be positive");
  }

  @Test
  void testReactorVesselReportsWeightAndCost() {
    Stream feed = new Stream("Feed", makeGas());
    feed.setFlowRate(20000.0, "kg/hr");
    feed.setTemperature(280.0, "C");
    feed.setPressure(50.0, "bara");
    feed.run();

    GibbsReactor reactor = new GibbsReactor("Topside Reactor", feed);

    reactor.initMechanicalDesign();
    MechanicalDesign md = reactor.getMechanicalDesign();
    md.calcDesign();
    md.calculateCostEstimate();

    assertTrue(md.getWeightTotal() > 0.0, "Reactor vessel equipped weight should be positive");
    assertTrue(md.getCostEstimate().getPurchasedEquipmentCost() > 0.0,
        "Reactor purchased equipment cost should be positive");
  }

  @Test
  void testManifoldReportsWeightAndCost() {
    Stream feedA = new Stream("Feed A", makeGas());
    feedA.setFlowRate(30000.0, "kg/hr");
    feedA.setTemperature(25.0, "C");
    feedA.setPressure(60.0, "bara");
    feedA.run();

    Stream feedB = new Stream("Feed B", makeGas());
    feedB.setFlowRate(20000.0, "kg/hr");
    feedB.setTemperature(25.0, "C");
    feedB.setPressure(60.0, "bara");
    feedB.run();

    Manifold manifold = new Manifold("Topside Production Manifold");
    manifold.addStream(feedA);
    manifold.addStream(feedB);
    manifold.setSplitFactors(new double[] { 0.4, 0.4, 0.2 });
    manifold.run();

    manifold.initMechanicalDesign();
    ManifoldMechanicalDesign md = manifold.getMechanicalDesign();
    md.setNumberOfInlets(2);
    md.setNumberOfOutlets(3);
    md.calcDesign();
    md.calculateCostEstimate();

    assertTrue(md.getWeightTotal() > 0.0, "Manifold dry weight should be positive");
    assertTrue(md.getCostEstimate().getPurchasedEquipmentCost() > 0.0,
        "Manifold purchased equipment cost should be positive");
  }

  @Test
  void testWellRoughCapexIsAllInWithoutModuleInflation() {
    WellFlow well = new WellFlow("Producer-1");
    well.setWellCapex(80.0e6);

    MechanicalDesign md = well.getMechanicalDesign();
    md.calcDesign();
    md.calculateCostEstimate();

    double total = md.getCostEstimate().getTotalCost();
    double tmc = md.getCostEstimate().getTotalModuleCost();

    assertEquals(80.0e6, total, 1.0, "Well total cost should equal the all-in CAPEX");
    assertEquals(80.0e6, tmc, 1.0, "Well CAPEX should not be inflated by module factors");
    assertEquals(0.0, md.getWeightTotal(), 1.0e-9, "Well carries no shell weight");
  }
}
