package neqsim.process.mechanicaldesign.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.filter.Filter;
import neqsim.process.equipment.filter.FilterPerformanceCurve;
import neqsim.process.equipment.filter.FilterType;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests type-specific filter mechanical design and integrity screening. */
public class FilterMechanicalDesignTest {

  private Stream createGasFeed() {
    SystemInterface fluid = new SystemSrkEos(303.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("filter design feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();
    return feed;
  }

  /** Tests vessel, element, nozzle, weight, and pressure-boundary results. */
  @Test
  public void testCoalescerMechanicalDesignProducesUsableResults() {
    Stream feed = createGasFeed();
    Filter filter = new Filter("inlet coalescer", feed);
    filter.setFilterServiceType(FilterType.COALESCER);
    filter.setDeltaP(0.15);
    filter.setTerminalDeltaP(1.0);
    filter.setElementCollapsePressure(5.0);
    filter.setElementIntegrityVerified(true);
    FilterPerformanceCurve performance = new FilterPerformanceCurve(new double[] { 1.0, 3.0, 10.0 },
        new double[] { 10.0, 1000.0, 10000.0 });
    performance.setTestStandard("ISO 16889:2022 supplier-equivalent data");
    filter.setPerformanceCurve(performance);
    filter.run();

    FilterMechanicalDesign design = (FilterMechanicalDesign) filter.getMechanicalDesign();
    design.calcDesign();

    assertTrue(design.getRequiredElements() > 0);
    assertTrue(design.getShellThickness() >= 0.006);
    assertTrue(design.getVesselLength() > 0.0);
    assertTrue(design.getSelectedNozzleDiameterMm() > 0.0);
    assertTrue(design.getCalculatedFaceVelocity() <= 0.08 + 1.0e-12);
    assertTrue(design.getTotalEquippedWeight() > design.getEmptyVesselWeight());
    assertTrue(design.isDifferentialPressureDesignAcceptable());
    assertTrue(design.getDesignWarnings().isEmpty());
    assertTrue(design.toJson().contains("ASME Section VIII Division 1 screening equations"));
  }

  /** Tests warnings for an unsafe terminal/collapse setting and missing integrity evidence. */
  @Test
  public void testMechanicalDesignReportsElementIntegrityWarnings() {
    Stream feed = createGasFeed();
    Filter filter = new Filter("unqualified strainer", feed);
    filter.setFilterServiceType(FilterType.BASKET_STRAINER);
    filter.setDeltaP(2.0);
    filter.setTerminalDeltaP(1.0);
    filter.setElementCollapsePressure(1.5);
    filter.run();

    FilterMechanicalDesign design = (FilterMechanicalDesign) filter.getMechanicalDesign();
    design.calcDesign();

    assertFalse(design.isDifferentialPressureDesignAcceptable());
    assertTrue(design.getDesignWarnings().size() >= 3);
  }

  /** Tests that granular media uses vessel cross-sectional area rather than cartridge count. */
  @Test
  public void testGranularMediaSizingUpdatesHydraulicArea() {
    Stream feed = createGasFeed();
    Filter filter = new Filter("activated carbon guard", feed);
    filter.setFilterServiceType(FilterType.ACTIVATED_CARBON);
    filter.setElementIntegrityVerified(true);
    filter.setNominalRemovalEfficiency(0.9);

    FilterMechanicalDesign design = (FilterMechanicalDesign) filter.getMechanicalDesign();
    design.calcDesign();

    assertEquals(1, design.getRequiredElements());
    assertEquals(Math.PI * design.getInnerDiameter() * design.getInnerDiameter() / 4.0, filter.getMediaArea(), 1.0e-12);
  }
}
