package neqsim.process.lng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for {@link LNGProcessBuilder} and {@link LNGProcessModel}.
 *
 * @author NeqSim contributors
 */
class LNGProcessBuilderTest {
  @ParameterizedTest
  @EnumSource(LNGProcessCycle.class)
  void testBuildsEverySupportedCycle(LNGProcessCycle cycle) {
    LNGProcessModel model = new LNGProcessBuilder().setName("test " + cycle).setCycle(cycle).setFeedFlowRate(20000.0)
        .setNumberOfZones(4).setAdaptiveRefinement(false).build();

    assertEquals(cycle, model.getCycle());
    assertNotNull(model.getProcessSystem());
    assertNotNull(model.getFeedStream());
    assertNotNull(model.getProductStream());
    assertTrue(model.getCompressors().size() >= 2);
    assertTrue(model.getCryogenicHeatExchangers().size() >= 1);

    if (cycle == LNGProcessCycle.NITROGEN_EXPANDER) {
      assertEquals(1, model.getExpanders().size());
    } else {
      assertTrue(model.getExpanders().isEmpty());
    }
  }

  @Test
  @Tag("slow")
  void testSmrRouteRunsAndReportsPerformance() {
    LNGProcessModel model = new LNGProcessBuilder().setName("SMR smoke test").setCycle(LNGProcessCycle.SMR)
        .setFeedFlowRate(20000.0).setNumberOfZones(4).setAdaptiveRefinement(false).build();

    LNGProcessModel.Result result = model.run();

    assertTrue(Double.isFinite(result.getLNGMassFlowKgPerHour()));
    assertTrue(result.getLNGMassFlowKgPerHour() > 0.0);
    assertTrue(Double.isFinite(result.getLNGYield()));
    assertTrue(result.getLNGYield() > 0.0 && result.getLNGYield() <= 1.000001);
    assertTrue(Double.isFinite(result.getSpecificEnergyKWhPerKgLNG()));
    assertTrue(result.getSpecificEnergyKWhPerKgLNG() > 0.0);
    assertTrue(Double.isFinite(result.getProductTemperatureC()));
    assertTrue(result.getRunTimeMilliseconds() >= 0.0);
    assertNotNull(model.toJson());
  }

  @Test
  void testRejectsInvalidConfiguration() {
    LNGProcessBuilder builder = new LNGProcessBuilder();

    assertThrows(IllegalArgumentException.class, () -> builder.setCycle(null));
    assertThrows(IllegalArgumentException.class, () -> builder.setFeedFlowRate(0.0));
    assertThrows(IllegalArgumentException.class, () -> builder.setFeedPressure(-1.0));
    assertThrows(IllegalArgumentException.class, () -> builder.setNumberOfZones(1));
    assertThrows(IllegalArgumentException.class, () -> builder.setCompressorEfficiency(1.1));
  }
}
