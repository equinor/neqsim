package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Analytical invariants and EOS-based screening for off-design map coordinates.
 *
 * @author NeqSim
 * @version 1.0
 */
class CompressorMapSimilarityTest {
  /** Checks identity and matched-Mach/head/mass transformations analytically. */
  @Test
  void analyticalSimilarityAndRoundTrips() {
    CompressorMapSimilarity identity = new CompressorMapSimilarity(400, 400, 10, 10);
    assertEquals(1000, identity.toReferenceVolumeFlow(1000), 0);
    assertEquals(9000, identity.toReferenceSpeed(9000), 0);
    assertEquals(50, identity.toReferenceHead(50), 0);
    CompressorMapSimilarity change = new CompressorMapSimilarity(400, 500, 10, 20);
    assertEquals(800, change.toReferenceVolumeFlow(1000), 1e-10);
    assertEquals(7200, change.toReferenceSpeed(9000), 1e-10);
    assertEquals(32, change.toReferenceHead(50), 1e-10);
    assertEquals(4, change.toReferenceMassFlow(10), 1e-10);
    assertEquals(1000, change.toActualVolumeFlow(change.toReferenceVolumeFlow(1000)), 1e-10);
    assertEquals(10, change.toActualMassFlow(change.toReferenceMassFlow(10)), 1e-10);
    // Ideal same-gas pressure doubling: same actual-volume coordinates, twice actual mass flow.
    CompressorMapSimilarity pressure = new CompressorMapSimilarity(400, 400, 10, 20);
    assertEquals(1000, pressure.toReferenceVolumeFlow(1000), 0);
    assertEquals(5, pressure.toReferenceMassFlow(10), 0);
    // Ideal same-gas temperature x4 at equal pressure: acoustic x2, density /4.
    CompressorMapSimilarity temperature = new CompressorMapSimilarity(400, 800, 10, 2.5);
    assertEquals(500, temperature.toReferenceVolumeFlow(1000), 0);
    assertEquals(20, temperature.toReferenceMassFlow(10), 0);
  }

  /**
   * Creates a gas state for the documented EOS API.
   *
   * @param component component name
   * @param temperature kelvin
   * @param pressure bara
   * @return configured gas
   */
  private SystemInterface gas(String component, double temperature, double pressure) {
    SystemInterface fluid = new SystemSrkEos(temperature, pressure);
    fluid.addComponent(component, 1);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /** Checks composition/T/P effects and that source fluids are untouched. */
  @Test
  void eosCoordinatesRespondToGasStateWithoutMutation() {
    SystemInterface reference = gas("methane", 300, 5);
    CompressorMapSimilarity hotter = CompressorMapSimilarity.fromFluids(reference, gas("methane", 400, 5));
    assertTrue(hotter.toReferenceSpeed(9000) < 9000);
    CompressorMapSimilarity heavier = CompressorMapSimilarity.fromFluids(reference, gas("propane", 300, 5));
    assertTrue(heavier.toActualVolumeFlow(1000) < 1000);
    CompressorMapSimilarity denser = CompressorMapSimilarity.fromFluids(reference, gas("methane", 300, 10));
    assertTrue(denser.toReferenceMassFlow(10) < 10);
    assertEquals(300, reference.getTemperature(), 0);
    assertEquals(5, reference.getPressure(), 0);
    assertEquals(1, reference.getTotalNumberOfMoles(), 1e-10);
  }

  /** Checks missing, wet and invalid states never produce fabricated correction values. */
  @Test
  void invalidAndWetInputsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new CompressorMapSimilarity(Double.NaN, 400, 10, 10));
    assertThrows(IllegalArgumentException.class, () -> new CompressorMapSimilarity(400, 0, 10, 10));
    CompressorMapSimilarity identity = new CompressorMapSimilarity(400, 400, 10, 10);
    assertThrows(IllegalArgumentException.class, () -> identity.toReferenceMassFlow(-1));
    assertThrows(IllegalArgumentException.class, () -> identity.toReferenceSpeed(Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> CompressorMapSimilarity.fromFluids(gas("methane", 300, 5), gas("water", 300, 5)));
  }
}
