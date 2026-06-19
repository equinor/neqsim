package neqsim.process.equipment.mixer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests verifying that {@link Mixer} conserves mass even when two inlet streams carry a component with the
 * same name but a different molar mass (e.g. independently characterized pseudo/plus-fractions).
 *
 * @author ESOL
 */
class MixerMassConservationTest {

  /**
   * Two inlets carry a component with the SAME name and the SAME molar mass. Total outlet mass must equal the sum of
   * the inlet masses.
   */
  @Test
  void testMassConservedSameMolarMass() {
    SystemInterface sysA = new SystemSrkEos(298.15, 10.0);
    sysA.addComponent("methane", 1.0);
    sysA.addTBPfraction("C7", 1.0, 0.100, 0.70);
    sysA.setMixingRule(2);

    SystemInterface sysB = new SystemSrkEos(298.15, 10.0);
    sysB.addComponent("methane", 1.0);
    sysB.addTBPfraction("C7", 1.0, 0.100, 0.70);
    sysB.setMixingRule(2);

    Stream streamA = new Stream("stream A", sysA);
    streamA.setFlowRate(1000.0, "kg/hr");
    streamA.setTemperature(25.0, "C");
    streamA.setPressure(10.0, "bara");
    streamA.run();

    Stream streamB = new Stream("stream B", sysB);
    streamB.setFlowRate(1000.0, "kg/hr");
    streamB.setTemperature(25.0, "C");
    streamB.setPressure(10.0, "bara");
    streamB.run();

    double inletMass = streamA.getFlowRate("kg/hr") + streamB.getFlowRate("kg/hr");

    Mixer mixer = new Mixer("test mixer");
    mixer.addStream(streamA);
    mixer.addStream(streamB);
    mixer.run();

    double outletMass = mixer.getOutletStream().getFlowRate("kg/hr");
    assertEquals(inletMass, outletMass, 1.0e-3);
  }

  /**
   * Base stream (stream 0) is MISSING a pseudo-component that a later inlet carries. The component gets added to the
   * mixed stream via the new-component path. Total outlet mass must still equal the sum of the inlet masses.
   */
  @Test
  void testMassConservedBaseMissingComponent() {
    SystemInterface sysA = new SystemSrkEos(298.15, 10.0);
    sysA.addComponent("methane", 1.0);
    sysA.setMixingRule(2);

    SystemInterface sysB = new SystemSrkEos(298.15, 10.0);
    sysB.addComponent("methane", 1.0);
    sysB.addTBPfraction("C7", 1.0, 0.110, 0.74);
    sysB.addTBPfraction("C10", 0.5, 0.140, 0.78);
    sysB.setMixingRule(2);

    Stream streamA = new Stream("stream A", sysA);
    streamA.setFlowRate(1000.0, "kg/hr");
    streamA.setTemperature(25.0, "C");
    streamA.setPressure(10.0, "bara");
    streamA.run();

    Stream streamB = new Stream("stream B", sysB);
    streamB.setFlowRate(1500.0, "kg/hr");
    streamB.setTemperature(25.0, "C");
    streamB.setPressure(10.0, "bara");
    streamB.run();

    double inletMass = streamA.getFlowRate("kg/hr") + streamB.getFlowRate("kg/hr");

    Mixer mixer = new Mixer("test mixer");
    mixer.addStream(streamA);
    mixer.addStream(streamB);
    mixer.run();

    double outletMass = mixer.getOutletStream().getFlowRate("kg/hr");
    assertEquals(inletMass, outletMass, inletMass * 1.0e-3);
  }

  /**
   * Two inlets carry a component with the SAME name ("C7") but a DIFFERENT molar mass (two independent
   * characterizations). Without molar-mass scaling, moles are conserved but mass is not. The mixer must scale moles so
   * total outlet mass equals the sum of the inlet masses.
   */
  @Test
  void testMassConservedDifferentMolarMass() {
    SystemInterface sysA = new SystemSrkEos(298.15, 10.0);
    sysA.addComponent("methane", 1.0);
    sysA.addTBPfraction("C7", 1.0, 0.095, 0.69);
    sysA.setMixingRule(2);

    SystemInterface sysB = new SystemSrkEos(298.15, 10.0);
    sysB.addComponent("methane", 1.0);
    sysB.addTBPfraction("C7", 1.0, 0.110, 0.74);
    sysB.setMixingRule(2);

    Stream streamA = new Stream("stream A", sysA);
    streamA.setFlowRate(1000.0, "kg/hr");
    streamA.setTemperature(25.0, "C");
    streamA.setPressure(10.0, "bara");
    streamA.run();

    Stream streamB = new Stream("stream B", sysB);
    streamB.setFlowRate(1500.0, "kg/hr");
    streamB.setTemperature(25.0, "C");
    streamB.setPressure(10.0, "bara");
    streamB.run();

    double inletMass = streamA.getFlowRate("kg/hr") + streamB.getFlowRate("kg/hr");

    Mixer mixer = new Mixer("test mixer");
    mixer.addStream(streamA);
    mixer.addStream(streamB);
    mixer.run();

    double outletMass = mixer.getOutletStream().getFlowRate("kg/hr");
    // Allow 0.1 % tolerance for flash round-off; the unscaled bug gave several percent error.
    assertEquals(inletMass, outletMass, inletMass * 1.0e-3);
  }
}
