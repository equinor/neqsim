package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;

/**
 * Regression test for phase-consistent separator outlet handling without XML fixtures.
 *
 * <p>
 * This test reproduces the risky path seen after restore: a liquid outlet stream object exists before run(), and the
 * separator must not trigger an unnecessary outlet TP reflash when no gas-in-liquid entrainment is requested. An
 * unnecessary reflash can reclassify heavy reflux as gas in edge cases.
 * </p>
 */
class SeparatorPhaseConsistencyTest {
  /**
   * Stream test double tracking whether {@link #run(UUID)} is invoked by the separator.
   */
  private static class TrackingStream extends Stream {
    private static final long serialVersionUID = 1L;
    private boolean runCalled = false;

    TrackingStream(String name, SystemInterface thermoSystem) {
      super(name, thermoSystem);
    }

    @Override
    public void run(UUID id) {
      runCalled = true;
      super.run(id);
    }
  }

  /**
   * Ensures heavy liquid outlet remains liquid-like and is not unnecessarily re-run.
   */
  @Tag("slow")
  @Test
  void heavyLiquidOutletStaysLiquidWithoutXmlDeserialize() {
    SystemInterface feedFluid = new neqsim.thermo.system.SystemSrkEos(273.15 + 34.247289, 3.842415);
    feedFluid.addComponent("methane", 0.088);
    feedFluid.addComponent("propane", 13.48);
    feedFluid.addComponent("i-butane", 15.00);
    feedFluid.addComponent("n-butane", 25.28);
    feedFluid.addComponent("i-pentane", 10.00);
    feedFluid.addComponent("n-pentane", 15.79);
    feedFluid.addComponent("n-hexane", 20.362);
    feedFluid.setMixingRule("classic");

    Stream feed = new Stream("feed", feedFluid);
    feed.setFlowRate(67954.7, "kg/hr");
    feed.setTemperature(34.247289, "C");
    feed.setPressure(3.842415, "bara");
    feed.run();

    Separator separator = new Separator("Separator after degasser gas", feed);
    separator.run();

    TrackingStream trackingLiquid = new TrackingStream("liquidOutStream",
        separator.getLiquidOutStream().getFluid().clone());
    separator.liquidOutStream = trackingLiquid;

    separator.run();

    SystemInterface liquidOut = separator.getLiquidOutStream().getFluid();
    String dominantPhase = liquidOut.getPhase(0).getPhaseTypeName();
    assertFalse(trackingLiquid.runCalled,
        "Liquid outlet stream should not be TP-reflashed when gasInLiquid entrainment is zero");

    assertNotEquals("gas", dominantPhase, "Liquid outlet dominant phase must stay liquid-like after separator run");
    assertTrue(liquidOut.hasPhaseType("oil") || liquidOut.hasPhaseType("aqueous"),
        "Liquid outlet must contain an oil or aqueous phase");

    double methane = liquidOut.getPhase(0).getComponent("methane").getx();
    double nButane = liquidOut.getPhase(0).getComponent("n-butane").getx();
    assertTrue(methane < nButane,
        "Recovered liquid outlet should remain heavy (n-butane fraction above methane fraction)");
  }
}
