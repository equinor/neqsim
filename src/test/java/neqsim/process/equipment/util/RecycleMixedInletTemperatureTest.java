package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests that a multi-inlet recycle returns a physical mixed temperature when it merges three-phase water/hydrocarbon
 * liquids with a near-empty inlet. In a 26-component PR78 platform model the PH flash of such a mix collapsed to 4.9 K;
 * the recycle now detects an implausible result and restarts the flash.
 *
 * @author NeqSim
 * @version 1.0
 */
class RecycleMixedInletTemperatureTest {

  /**
   * Creates a gas-condensate fluid with water and multiphase check.
   *
   * @param temperatureC temperature in Celsius
   * @param pressureBara pressure in bara
   * @return configured fluid
   */
  private static SystemInterface createFluid(double temperatureC, double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(273.15 + temperatureC, pressureBara);
    fluid.addComponent("methane", 0.30);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.12);
    fluid.addComponent("n-butane", 0.10);
    fluid.addComponent("n-heptane", 0.25);
    fluid.addComponent("water", 0.15);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Creates and runs a stream.
   *
   * @param name stream name
   * @param temperatureC temperature in Celsius
   * @param pressureBara pressure in bara
   * @param flowKgHr mass flow in kg/hr
   * @return the solved stream
   */
  private static Stream stream(String name, double temperatureC, double pressureBara, double flowKgHr) {
    Stream s = new Stream(name, createFluid(temperatureC, pressureBara));
    s.setFlowRate(flowKgHr, "kg/hr");
    s.run();
    return s;
  }

  /**
   * The recycle outlet must agree with a Mixer of the same inlets and stay within the inlet temperature range.
   */
  @Test
  void multiInletRecycleTemperatureMatchesMixer() {
    Stream empty = stream("empty pump outlet", 30.0, 16.4, 1.0e-12);
    Stream liquidA = stream("scrubber A liquid", 19.9, 16.4, 46800.0);
    Stream liquidB = stream("scrubber B liquid", 19.9, 16.4, 46800.0);
    Stream fuelKo = stream("fuel KO liquid", 8.9, 16.4, 116.0);

    Stream seed = stream("condensate return", 30.0, 16.4, 1.0);
    Recycle recycle = new Recycle("condensate recycle");
    recycle.addStream(empty);
    recycle.addStream(liquidA);
    recycle.addStream(liquidB);
    recycle.addStream(fuelKo);
    recycle.setOutletStream(seed);
    recycle.run();

    Mixer mixer = new Mixer("reference mixer");
    mixer.addStream(empty.clone());
    mixer.addStream(liquidA.clone());
    mixer.addStream(liquidB.clone());
    mixer.addStream(fuelKo.clone());
    mixer.run();

    double tRecycle = recycle.getOutletStream().getTemperature("C");
    double tMixer = mixer.getOutletStream().getTemperature("C");
    assertTrue(tRecycle > 5.0 && tRecycle < 30.0, "recycle outlet temperature " + tRecycle + " C");
    assertEquals(tMixer, tRecycle, 0.5);
    assertEquals(mixer.getOutletStream().getFlowRate("kg/hr"), recycle.getOutletStream().getFlowRate("kg/hr"),
        1.0e-3 * mixer.getOutletStream().getFlowRate("kg/hr"));
  }

  /**
   * The plausibility guard rejects a collapsed flash and accepts temperatures near the inlet range.
   */
  @Test
  void plausibilityGuardRejectsCollapsedFlash() {
    Recycle recycle = new Recycle("guard");
    recycle.addStream(stream("cold", 9.0, 16.4, 100.0));
    recycle.addStream(stream("warm", 30.0, 16.4, 100.0));
    assertFalse(recycle.isPhysicalMixTemperature(4.9));
    assertFalse(recycle.isPhysicalMixTemperature(Double.NaN));
    assertFalse(recycle.isPhysicalMixTemperature(-1.0));
    assertTrue(recycle.isPhysicalMixTemperature(273.15 + 20.0));
    assertTrue(recycle.isPhysicalMixTemperature(273.15 - 40.0));
  }
}
