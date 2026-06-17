package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ScrubColumn}.
 *
 * @author NeqSim
 */
class ScrubColumnTest {

  @Test
  void testBasicScrubColumn() {
    // Feed gas with some heavy components
    SystemInterface feedFluid = new SystemSrkEos(273.15 + 25.0, 55.0);
    feedFluid.addComponent("methane", 0.85);
    feedFluid.addComponent("ethane", 0.06);
    feedFluid.addComponent("propane", 0.04);
    feedFluid.addComponent("n-butane", 0.02);
    feedFluid.addComponent("n-pentane", 0.015);
    feedFluid.addComponent("n-hexane", 0.005);
    feedFluid.addComponent("nitrogen", 0.01);
    feedFluid.setMixingRule("classic");

    Stream feed = new Stream("scrub_feed", feedFluid);
    feed.setFlowRate(500000.0, "kg/hr");
    feed.setTemperature(-30.0, "C");
    feed.setPressure(55.0, "bara");

    ScrubColumn scrubCol = new ScrubColumn("Scrub Column", 5, true, true);
    scrubCol.addFeedStream(feed, 3);
    scrubCol.setHeavyKeyComponent("n-pentane");
    scrubCol.setMaxHeavyKeyInOverhead(0.001);
    scrubCol.setMinimumBottomsTemperature(-50.0, "C");

    assertEquals("n-pentane", scrubCol.getHeavyKeyComponent());
    assertEquals(0.001, scrubCol.getMaxHeavyKeyInOverhead(), 1e-6);
    assertEquals(273.15 - 50.0, scrubCol.getMinimumBottomsTemperature(), 0.01);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(scrubCol);
    process.run();

    // Column should produce gas and liquid outlets
    assertNotNull(scrubCol.getGasOutStream());
    assertNotNull(scrubCol.getLiquidOutStream());
  }

  @Test
  void testFreezeOutRiskAccessors() {
    ScrubColumn col = new ScrubColumn("test_scrub", 3, true, true);
    // Before run, risk should be false
    assertFalse(col.hasFreezeOutRisk());
    assertEquals(0.0, col.getNGLRecovery(), 1e-10);
    assertEquals(0.0, col.getHeavyKeyInOverheadMolFrac(), 1e-10);
  }
}
