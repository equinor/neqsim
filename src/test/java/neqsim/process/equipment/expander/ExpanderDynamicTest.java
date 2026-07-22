package neqsim.process.equipment.expander;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Dynamic simulation tests for {@link Expander}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class ExpanderDynamicTest {

  /**
   * Builds a high-pressure gas feed for expander dynamic tests.
   *
   * @return initialized feed stream
   */
  private Stream buildFeedStream() {
    SystemInterface gas = new SystemSrkEos(273.15 + 35.0, 60.0);
    gas.addComponent("nitrogen", 0.01);
    gas.addComponent("CO2", 0.02);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.05);
    gas.addComponent("propane", 0.02);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed gas", gas);
    feed.setFlowRate(150000.0, "kg/hr");
    feed.setTemperature(35.0, "C");
    feed.setPressure(60.0, "bara");
    feed.run();
    return feed;
  }

  /**
   * Builds a configured expander for dynamic tests.
   *
   * @return configured expander
   */
  private Expander buildDynamicExpander() {
    Expander expander = new Expander("dynamic expander", buildFeedStream());
    expander.setOutletPressure(20.0);
    expander.setIsentropicEfficiency(0.80);
    expander.setCalculateSteadyState(false);
    expander.setNozzleOpening(0.20);
    expander.setTargetNozzleOpening(1.0);
    expander.setNozzleOpeningRate(0.10);
    expander.setRecoveredPowerRampRate(250.0);
    expander.setRotationalInertia(25.0);
    expander.setSpeed(3000.0);
    expander.setMaxAccelerationRate(100.0);
    expander.setMaxDecelerationRate(100.0);
    return expander;
  }

  /**
   * Verifies that transient execution ramps nozzle opening, recovered power and shaft speed.
   */
  @Test
  void testDynamicNozzlePowerAndSpeedRamp() {
    Expander expander = buildDynamicExpander();

    expander.runTransient(1.0, UUID.randomUUID());

    Assertions.assertEquals(0.30, expander.getNozzleOpening(), 1.0e-10);
    Assertions.assertEquals(250.0, expander.getDynamicRecoveredPower("kW"), 1.0e-8);
    Assertions.assertTrue(expander.getSpeed() > 3000.0, "Recovered power should accelerate the shaft");
    Assertions.assertTrue(expander.getPower("kW") < 0.0, "Expander should still report recovered steady power");
  }

  /**
   * Verifies that an external shaft load decelerates the expander shaft when it exceeds recovered power.
   */
  @Test
  void testExternalLoadDeceleratesShaft() {
    Expander expander = buildDynamicExpander();
    expander.setExternalShaftLoad(5000.0);

    expander.runTransient(1.0, UUID.randomUUID());

    Assertions.assertTrue(expander.getSpeed() < 3000.0, "External load should decelerate the shaft");
  }

  /**
   * Verifies that the expander propagates shaft speed to a coupled compressor load.
   */
  @Test
  void testCoupledCompressorReceivesShaftSpeed() {
    Expander expander = buildDynamicExpander();
    Compressor load = new Compressor("booster load", buildFeedStream());
    load.setOutletPressure(75.0);
    expander.setCoupledCompressorLoad(load);
    expander.setCoupledCompressorSpeedRatio(1.5);

    expander.runTransient(1.0, UUID.randomUUID());

    Assertions.assertEquals(expander.getSpeed() * 1.5, load.getSpeed(), 1.0e-8);
    Assertions.assertTrue(load.getPower("kW") > 0.0, "Coupled compressor should run as a positive shaft load");
  }

  /**
   * Verifies process-level transient execution reaches the expander dynamic branch.
   */
  @Test
  void testProcessSystemRunTransientExecutesDynamicExpander() {
    Stream feed = buildFeedStream();
    Expander expander = new Expander("process expander", feed);
    expander.setOutletPressure(20.0);
    expander.setCalculateSteadyState(false);
    expander.setNozzleOpening(0.20);
    expander.setTargetNozzleOpening(0.80);
    expander.setNozzleOpeningRate(0.10);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(expander);

    process.runTransient(1.0, UUID.randomUUID());

    Assertions.assertEquals(0.30, expander.getNozzleOpening(), 1.0e-10);
    Assertions.assertTrue(expander.getDynamicRecoveredPower("kW") > 0.0);
  }
}
