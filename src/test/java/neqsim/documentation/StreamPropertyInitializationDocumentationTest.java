package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies the stream initialization boundary described in the fluid-properties guide.
 *
 * @author ESOL
 * @version 1.0
 */
class StreamPropertyInitializationDocumentationTest extends neqsim.NeqSimTest {
  /**
   * Creates an unrun SRK gas stream with explicit absolute pressure and mass-flow units.
   *
   * @param temperatureK temperature in kelvin
   * @return unrun methane/ethane stream
   */
  private Stream gasStream(double temperatureK) {
    SystemInterface fluid = new SystemSrkEos(temperatureK, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream stream = new Stream("gas", fluid);
    stream.setFlowRate(100.0, "kg/hr");
    return stream;
  }

  /**
   * Checks finite positive transport properties on the current gas phase.
   *
   * @param fluid initialized fluid
   */
  private void assertTransportAvailable(SystemInterface fluid) {
    assertTrue(fluid.hasPhaseType("gas"));
    double viscosity = fluid.getPhase("gas").getViscosity("kg/msec");
    double conductivity = fluid.getPhase("gas").getThermalConductivity("W/mK");
    assertTrue(Double.isFinite(viscosity) && viscosity > 0.0);
    assertTrue(Double.isFinite(conductivity) && conductivity > 0.0);
  }

  /** Verifies default full initialization at two nearby gas temperatures. */
  @Test
  void defaultFullInitializesTransport() {
    for (double temperatureK : new double[] {298.15, 308.15}) {
      Stream stream = gasStream(temperatureK);
      assertEquals(Stream.PropertyInitLevel.FULL, stream.getPropertyInitLevel());
      stream.run();
      assertTransportAvailable(stream.getFluid());
    }
  }

  /** Verifies explicit initialization recovers transport on the current, cloned stream fluid. */
  @Test
  void densityOnlyRequiresExplicitTransportRecovery() {
    for (double temperatureK : new double[] {298.15, 308.15}) {
      Stream stream = gasStream(temperatureK);
      SystemInterface original = stream.getFluid();
      stream.setPropertyInitLevel(Stream.PropertyInitLevel.DENSITY_ONLY);
      stream.run();
      SystemInterface current = stream.getFluid();
      assertNotSame(original, current);
      assertTrue(current.getPhase("gas").getDensity("kg/m3") > 0.0);
      assertEquals(0.0, current.getPhase("gas").getViscosity("kg/msec"), 0.0);
      assertEquals(0.0, current.getPhase("gas").getThermalConductivity("W/mK"), 0.0);
      stream.getFluid().initProperties();
      assertTransportAvailable(current);
      assertEquals(temperatureK, current.getTemperature(), 1e-9);
      assertEquals(50.0, current.getPressure(), 1e-9);
      assertEquals(100.0, current.getFlowRate("kg/hr"), 1e-8);
      assertEquals(Stream.PropertyInitLevel.DENSITY_ONLY, stream.getPropertyInitLevel());
    }
  }

  /** Verifies global override propagation, null semantics, and explicit full-mode restoration. */
  @Test
  void processOverrideDoesNotResetLevelsWhenCleared() {
    ProcessSystem process = new ProcessSystem();
    Stream stream = gasStream(298.15);
    process.add(stream);
    assertNull(process.getPropertyInitLevel());
    assertTrue(process.setPropertyInitLevel(Stream.PropertyInitLevel.DENSITY_ONLY) > 0);
    stream.setPropertyInitLevel(Stream.PropertyInitLevel.FULL);
    process.run();
    assertEquals(Stream.PropertyInitLevel.DENSITY_ONLY, stream.getPropertyInitLevel());
    process.setPropertyInitLevel(null);
    assertNull(process.getPropertyInitLevel());
    assertEquals(Stream.PropertyInitLevel.DENSITY_ONLY, stream.getPropertyInitLevel());
    process.setPropertyInitLevel(Stream.PropertyInitLevel.FULL);
    assertEquals(Stream.PropertyInitLevel.FULL, stream.getPropertyInitLevel());
    stream.run();
    assertTransportAvailable(stream.getFluid());
  }
}
