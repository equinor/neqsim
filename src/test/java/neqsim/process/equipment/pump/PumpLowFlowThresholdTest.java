package neqsim.process.equipment.pump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies that {@link Pump} interprets its low-flow bypass threshold in kg/hr, consistent with every other equipment
 * type and with {@code ProcessSystem.setSectionLowFlowThreshold(double)}.
 *
 * <p>
 * The threshold used to be compared against kg/sec inside {@code Pump.run(UUID)}, which meant a plant-wide threshold of
 * e.g. 50 kg/hr silently became 50 kg/sec (180 000 kg/hr) for pumps and bypassed them at any normal flow.
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class PumpLowFlowThresholdTest extends neqsim.NeqSimTest {

  /**
   * Builds a water stream at the requested mass flow.
   *
   * @param flowKgHr total mass flow in kg/hr
   * @return a ready-to-use thermodynamic system
   */
  private static SystemInterface makeLiquid(double flowKgHr) {
    SystemInterface fluid = new SystemSrkEos(298.15, 5.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(flowKgHr, "kg/hr");
    return fluid;
  }

  /**
   * Builds and runs a single-pump process.
   *
   * @param feedKgHr pump feed mass flow in kg/hr
   * @param thresholdKgHr low-flow bypass threshold in kg/hr
   * @return the pump after the process has run
   */
  private static Pump runPump(double feedKgHr, double thresholdKgHr) {
    Stream feed = new Stream("feed", makeLiquid(feedKgHr));
    feed.setPressure(5.0, "bara");
    feed.setTemperature(25.0, "C");

    Pump pump = new Pump("pump", feed);
    pump.setOutletPressure(20.0, "bara");
    pump.setMinimumFlow(thresholdKgHr);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(pump);
    process.run();
    return pump;
  }

  @Test
  public void thresholdIsInterpretedInKgPerHour() {
    // 1000 kg/hr feed with a 50 kg/hr threshold must NOT bypass. Under the old
    // kg/sec comparison this pump was bypassed (1000 kg/hr = 0.278 kg/sec < 50).
    Pump pump = runPump(1000.0, 50.0);

    assertTrue(pump.isActive());
    assertTrue(pump.getPower("kW") > 0.0);
    assertEquals(20.0, pump.getOutletStream().getPressure("bara"), 1e-6);
  }

  @Test
  public void flowBelowThresholdBypassesThePump() {
    Pump pump = runPump(10.0, 50.0);

    assertFalse(pump.isActive());
    assertEquals(0.0, pump.getPower("kW"), 1e-9);
    assertEquals(20.0, pump.getOutletStream().getPressure("bara"), 1e-6);
  }
}
