package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests vapor and liquid side-draw split handling on distillation trays and columns.
 *
 * @author esol
 * @version 1.0
 */
public class SimpleTraySideDrawTest {

  /**
   * Test that vapor side draws remove flow from internal tray traffic while conserving phase flow.
   */
  @Test
  public void gasSideDrawSplitsTrayOutletFlow() {
    Stream feed = createMethaneFeed("side draw feed");

    SimpleTray referenceTray = new SimpleTray("reference tray");
    referenceTray.addStream(feed);
    referenceTray.run(UUID.randomUUID());
    double referenceGasFlow = referenceTray.getGasOutStream().getFlowRate("kg/hr");

    SimpleTray sideDrawTray = new SimpleTray("side draw tray");
    sideDrawTray.addStream(feed);
    sideDrawTray.setGasSideDrawFraction(0.25);
    sideDrawTray.run(UUID.randomUUID());

    double internalGasFlow = sideDrawTray.getGasOutStream().getFlowRate("kg/hr");
    double sideDrawFlow = sideDrawTray.getGasSideDrawStream().getFlowRate("kg/hr");

    assertEquals(referenceGasFlow, internalGasFlow + sideDrawFlow, referenceGasFlow * 1.0e-8);
    assertEquals(0.25 * referenceGasFlow, sideDrawFlow, referenceGasFlow * 1.0e-8);
  }

  /**
   * Test that column side draws are exposed as outlet streams.
   */
  @Test
  public void columnReportsSideDrawAsOutletStream() {
    Stream feed = createMethaneFeed("column side draw feed");
    DistillationColumn column = new DistillationColumn("SideDrawColumn", 1, false, false);
    column.addFeedStream(feed, 0);
    column.setGasSideDrawFraction(0, 0.10);
    column.run(UUID.randomUUID());

    StreamInterface sideDrawStream = column.getSideDrawStream(0, DistillationColumn.SideDrawPhase.GAS);
    List<StreamInterface> outlets = column.getOutletStreams();

    assertTrue(sideDrawStream.getFlowRate("kg/hr") > 0.0);
    assertTrue(outlets.contains(sideDrawStream));
    assertEquals(0.0, column.getMassBalance("kg/hr"), feed.getFlowRate("kg/hr") * 1.0e-6);
  }

  /**
   * Test that a side-draw flow specification adjusts the draw fraction to meet target flow.
   */
  @Test
  public void sideDrawFlowSpecificationClosesProductFlow() {
    Stream feed = createMethaneFeed("specified side draw feed");
    DistillationColumn column = new DistillationColumn("SpecifiedSideDrawColumn", 1, false, false);
    column.addFeedStream(feed, 0);

    DistillationColumn.ColumnSideDrawSpecification specification = column
        .addSideDrawFlowSpecification(0, DistillationColumn.SideDrawPhase.GAS, 25.0, "kg/hr");
    specification.setTolerance(1.0e-5);
    column.run(UUID.randomUUID());

    StreamInterface sideDrawStream = column.getSideDrawStream(0, DistillationColumn.SideDrawPhase.GAS);

    assertEquals(25.0, sideDrawStream.getFlowRate("kg/hr"), 25.0e-4);
    assertTrue(specification.getLastRelativeResidual() < 1.0e-5);
    assertTrue(column.isLastColumnTearConverged());
    assertTrue(column.getLastColumnTearIterationCount() > 0);
    assertEquals(0.0, column.getMassBalance("kg/hr"), feed.getFlowRate("kg/hr") * 1.0e-6);
  }

  /**
   * Test that side-draw stream caches are refreshed when a tray is rerun.
   */
  @Test
  public void sideDrawCacheRefreshesAfterTrayRerun() {
    Stream feed = createMethaneFeed("cache refresh feed");
    SimpleTray tray = new SimpleTray("cache refresh tray");
    tray.addStream(feed);
    tray.setGasSideDrawFraction(0.25);
    tray.run(UUID.randomUUID());
    assertEquals(25.0, tray.getGasSideDrawStream().getFlowRate("kg/hr"), 25.0e-4);

    feed.setFlowRate(200.0, "kg/hr");
    feed.run();
    tray.run(UUID.randomUUID());

    assertEquals(50.0, tray.getGasSideDrawStream().getFlowRate("kg/hr"), 50.0e-4);
  }

  /**
   * Test that impossible side-draw specs are bounded and reported as not converged.
   */
  @Test
  public void impossibleSideDrawFlowSpecIsBoundedAndReportsNonConvergence() {
    Stream feed = createMethaneFeed("impossible side draw feed");
    SimpleTray maxDrawTray = new SimpleTray("maximum gas draw tray");
    maxDrawTray.addStream(feed);
    maxDrawTray.setGasSideDrawFraction(1.0);
    maxDrawTray.run(UUID.randomUUID());
    double maximumGasDrawFlow = maxDrawTray.getGasSideDrawStream().getFlowRate("kg/hr");

    DistillationColumn column = new DistillationColumn("ImpossibleSideDrawColumn", 1, false,
        false);
    column.addFeedStream(feed, 0);
    column.addSideDrawFlowSpecification(0, DistillationColumn.SideDrawPhase.GAS,
      maximumGasDrawFlow * 2.0, "kg/hr");

    column.run(UUID.randomUUID());

    assertFalse(column.isLastColumnTearConverged());
    assertTrue(column.getTray(0).getGasSideDrawFraction() <= 1.0);
    assertTrue(column.getSideDrawStream(0, DistillationColumn.SideDrawPhase.GAS)
      .getFlowRate("kg/hr") <= maximumGasDrawFlow + 1.0e-8);
  }

  /**
   * Test that invalid side-draw fractions are rejected.
   */
  @Test
  public void invalidSideDrawFractionThrows() {
    SimpleTray tray = new SimpleTray("validation tray");
    assertThrows(IllegalArgumentException.class, () -> tray.setGasSideDrawFraction(-0.1));
    assertThrows(IllegalArgumentException.class, () -> tray.setLiquidSideDrawFraction(1.1));
  }

  /**
   * Test that liquid side product and pumparound fractions cannot exceed all liquid traffic.
   */
  @Test
  public void liquidSideDrawAndPumparoundFractionsAreBoundedTogether() {
    SimpleTray tray = new SimpleTray("liquid split validation tray");
    tray.setLiquidSideDrawFraction(0.60);
    assertThrows(IllegalArgumentException.class, () -> tray.setLiquidPumparoundDrawFraction(0.50));
  }

  /**
   * Create a gas feed for side-draw split tests.
   *
   * @param name stream name
   * @return initialized methane stream
   */
  private Stream createMethaneFeed(String name) {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();
    return feed;
  }
}