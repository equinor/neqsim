package neqsim.process.fielddevelopment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Calendar;
import java.util.GregorianCalendar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reservoir.WellSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.concept.InfrastructureInput;
import neqsim.process.fielddevelopment.concept.ReservoirInput;
import neqsim.process.fielddevelopment.concept.WellsInput;
import neqsim.process.fielddevelopment.facility.ConceptToProcessLinker;
import neqsim.process.fielddevelopment.network.NetworkResult;
import neqsim.process.fielddevelopment.network.NetworkSolver;
import neqsim.process.fielddevelopment.reservoir.ReservoirCouplingExporter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for field-development integration utilities.
 *
 * @author ESOL
 * @version 1.0
 */
class FieldDevelopmentIntegrationUtilitiesTest extends neqsim.NeqSimTest {

  @Test
  @DisplayName("ConceptToProcessLinker creates process equipment from a field concept")
  void testConceptToProcessLinkerCreatesProcessSystem() {
    FieldConcept concept = FieldConcept.builder("Linker Gas Concept")
        .reservoir(ReservoirInput.leanGas().gor(12000.0).resourceEstimate(8.0, "GSm3").build())
        .wells(WellsInput.builder().producerCount(2).ratePerWell(0.8e6, "Sm3/d")
            .tubeheadPressure(90.0).build())
        .infrastructure(InfrastructureInput.subseaTieback().tiebackLength(30.0).waterDepth(250.0)
            .exportPressure(150.0).build())
        .build();

    ConceptToProcessLinker linker = new ConceptToProcessLinker();
    neqsim.process.processmodel.ProcessSystem process =
        linker.generateProcessSystem(concept, ConceptToProcessLinker.FidelityLevel.CONCEPT);

    assertNotNull(process);
    assertTrue(process.size() >= 5, "Gas concept should generate inlet handling and export units");
    process.run();
    assertTrue(linker.getUtilitySummary(process).contains("UTILITY SUMMARY"));
  }

  @Test
  @DisplayName("ReservoirCouplingExporter writes VFP and schedule keywords")
  void testReservoirCouplingExporterGeneratesEclipseKeywords() {
    ReservoirCouplingExporter exporter = new ReservoirCouplingExporter();
    exporter.setPressureRange(20.0, 40.0, 2);
    exporter.setRateRange(500.0, 1000.0, 2);
    exporter.setWctRange(0.0, 0.5, 2);
    exporter.setGorRange(100.0, 300.0, 2);
    exporter.setDatumDepth(2000.0);

    SystemInterface fluid = createGasFluid();
    ReservoirCouplingExporter.VfpTable prodTable = exporter.generateVfpProd("PROD-1", fluid, 7);
    ReservoirCouplingExporter.VfpTable injTable = exporter.generateVfpInj("INJ-1", fluid, 8);

    assertEquals(7, prodTable.getTableNumber());
    assertEquals(8, injTable.getTableNumber());
    assertEquals(2, exporter.getVfpTables().size());

    Calendar calendar = new GregorianCalendar(2028, Calendar.JANUARY, 1);
    exporter.addGroupConstraint(calendar.getTime(), "HOST", 5000.0, 2.0e6, 2000.0);
    exporter.addWellControl(calendar.getTime(), "PROD-1", "GRAT", 1.5e6);
    exporter.addVfpReference(calendar.getTime(), "PROD-1", 7);

    String keywords = exporter.getEclipseKeywords();
    assertTrue(keywords.contains("VFPPROD"));
    assertTrue(keywords.contains("VFPINJ"));
    assertTrue(keywords.contains("SCHEDULE"));
    assertTrue(keywords.contains("GCONPROD"));
    assertTrue(keywords.contains("WCONPROD"));
    assertTrue(keywords.contains("WVFPPROD"));

    String forecastCsv = exporter.exportProductionForecastCsv(new int[] {2028, 2029},
        new double[] {1000.0, 900.0}, new double[] {2.0e6, 1.8e6}, new double[] {100.0, 150.0});
    assertTrue(forecastCsv.contains("Year,Oil_Sm3d,Gas_Sm3d,Water_Sm3d"));
    assertTrue(forecastCsv.contains("2029"));
  }

  @Test
  @DisplayName("NetworkSolver allocates multi-well rates under a facility capacity limit")
  void testNetworkSolverAppliesFacilityCapacityLimit() {
    WellSystem wellA = createGasWell("Well-A", 3.0e-6);
    WellSystem wellB = createGasWell("Well-B", 2.5e-6);

    NetworkSolver network = new NetworkSolver("Subsea Gathering");
    network.addWell(wellA, 5.0, 0.16).addWell(wellB, 8.0, 0.18).setManifoldPressure(55.0, "bara")
        .setMaxTotalRate(1.5, "MSm3/day").setReferenceFluid(createGasFluid());

    NetworkResult result = network.solve();

    assertNotNull(result);
    assertTrue(result.getTotalRate("MSm3/day") <= 1.5 + 1.0e-6);
    assertEquals(2, result.getProducingWellCount());
    assertTrue(result.getWellRate("Well-A", "Sm3/day") >= 0.0);
    assertTrue(result.getFlowlinePressureDrop("Well-B") >= 0.0);
    assertTrue(result.getSummaryTable().contains("Subsea Gathering"));

    StreamInterface combined = network.getCombinedStream();
    assertEquals(55.0, combined.getPressure("bara"), 1.0e-6);
  }

  /**
   * Creates a representative gas fluid for integration tests.
   *
   * @return configured thermodynamic system
   */
  private SystemInterface createGasFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 85.0, 250.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("CO2", 0.01);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return fluid;
  }

  /**
   * Creates a configured gas well for network solver tests.
   *
   * @param name well name
   * @param productivityIndex productivity index in Sm3/day/bar2
   * @return configured well system
   */
  private WellSystem createGasWell(String name, double productivityIndex) {
    Stream reservoirStream = new Stream(name + " reservoir", createGasFluid());
    reservoirStream.setFlowRate(3.0, "MSm3/day");
    reservoirStream.setTemperature(85.0, "C");
    reservoirStream.setPressure(250.0, "bara");
    reservoirStream.run();

    WellSystem well = new WellSystem(name);
    well.setReservoirStream(reservoirStream);
    well.setProductionIndex(productivityIndex, "Sm3/day/bar2");
    well.setIPRModel(WellSystem.IPRModel.PRODUCTION_INDEX);
    well.setWellheadPressure(50.0, "bara");
    well.setTubingDiameter(0.12, "m");
    well.setTubingLength(2500.0, "m");
    return well;
  }
}
