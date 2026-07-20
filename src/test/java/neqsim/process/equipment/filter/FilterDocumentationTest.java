package neqsim.process.equipment.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.filter.FilterMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Executable regression coverage for {@code docs/process/equipment/filters.md}. */
public class FilterDocumentationTest extends neqsim.NeqSimTest {

  /** Verifies every API call in the filter-guide quick start. */
  @Test
  public void testFilterGuideQuickStart() {
    SystemInterface fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("filter feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    FilterPerformanceCurve curve = new FilterPerformanceCurve(
        new double[] {5.0, 10.0, 20.0}, new double[] {2.0, 100.0, 1000.0});
    curve.setTestStandard("ISO 16889:2022");

    Filter filter = new Filter("inlet cartridge filter", feed);
    filter.setFilterServiceType(FilterType.CARTRIDGE);
    filter.setDeltaP(0.10);
    filter.setPerformanceCurve(curve);
    filter.setParticleSize(10.0);
    filter.setInletParticleConcentration(100.0);
    filter.setTerminalDeltaP(1.0);
    filter.setElementCollapsePressure(5.0);
    filter.setElementIntegrityVerified(true);
    filter.run();

    StreamInterface cleanGas = filter.getOutletStream();
    double efficiency = filter.getCurrentRemovalEfficiency();
    double outletConcentration = filter.getOutletParticleConcentration();
    double capturedRate = filter.getCalculatedCapturedRate();

    assertNotNull(cleanGas);
    assertEquals(19.9, cleanGas.getPressure("bara"), 1.0e-8);
    assertEquals(0.99, efficiency, 1.0e-12);
    assertEquals(1.0, outletConcentration, 1.0e-12);
    assertEquals(0.099, capturedRate, 1.0e-12);

    FilterMechanicalDesign design =
        (FilterMechanicalDesign) filter.getMechanicalDesign();
    design.setMaxOperationPressure(70.0);
    design.setMaxOperationTemperature(333.15);
    design.calcDesign();

    int elements = design.getRequiredElements();
    double vesselId = design.getInnerDiameter();
    List<String> warnings = design.getDesignWarnings();
    int warningCount = warnings.size();

    assertTrue(elements > 0);
    assertTrue(vesselId > 0.0);
    assertNotNull(warnings);
    assertTrue(warningCount >= 0);
    assertTrue(design.getShellThickness() > 0.0);
    assertTrue(design.getSelectedNozzleDiameterMm() > 0.0);
    assertTrue(design.isDifferentialPressureDesignAcceptable());

    filter.setLoadingCapacity(2.0);
    filter.setPressureDropIncreaseAtCapacity(0.5);
    filter.setCalculateSteadyState(false);
    filter.runTransient(3600.0, UUID.randomUUID());

    assertEquals(capturedRate, filter.getSolidsLoading(), 1.0e-12);
    assertTrue(filter.getDeltaP() > filter.getCleanDeltaP());
    assertFalse(filter.isReplacementRequired());
  }
}
