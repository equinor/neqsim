package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Reference-water regressions for cooling utility integration.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ReferenceCoolingWaterTest {
  /** Checks mass/volume closure and initialized transport properties at ambient conditions. */
  @Test
  void waterVolumeAndPhysicalDensityAgree() {
    SystemWaterIF97 water = new SystemWaterIF97(298.15, 3.0);
    water.setTotalFlowRate(10.0, "kg/sec");
    new ThermodynamicOperations(water).TPflash();
    water.initProperties();
    assertEquals(997.14, water.getDensity("kg/m3"), 0.1);
    assertEquals(10.0 / water.getDensity("kg/m3"), water.getFlowRate("m3/sec"), 1e-10);
    assertEquals(water.getDensity("kg/m3"), water.getPhase(0).getPhysicalProperties().getDensity(), 0.01);
    assertEquals(0.000890, water.getViscosity("kg/msec"), 0.000003);
    assertEquals(0.607, water.getThermalConductivity("W/mK"), 0.003);
  }

  /** Checks zero-salinity equivalence, repeated PH solves, and mass-flow scaling. */
  @Test
  void seawaterPhFlashAndZeroSalinityLimit() {
    SystemSeawater seawater = new SystemSeawater(288.15, 5.0, 35.0);
    seawater.setTotalFlowRate(2.0, "kg/sec");
    ThermodynamicOperations operations = new ThermodynamicOperations(seawater);
    seawater.setTemperature(308.15);
    operations.TPflash();
    double enthalpy = seawater.getEnthalpy();
    seawater.setTemperature(278.15);
    operations.PHflash(enthalpy);
    assertEquals(308.15, seawater.getTemperature(), 1e-7);
    operations.PHflash(enthalpy);
    assertEquals(308.15, seawater.getTemperature(), 1e-7);
    assertEquals(enthalpy, seawater.getEnthalpy(), Math.abs(enthalpy) * 1e-10);
    assertEquals(2.0, seawater.getFlowRate("kg/sec"), 1e-10);
    assertThrows(IllegalArgumentException.class, () -> operations.PHflash(1e10));
    assertEquals(308.15, seawater.getTemperature(), 1e-7);

    seawater.setSalinity(0.0);
    operations.TPflash();
    seawater.initProperties();
    SystemWaterIF97 water = new SystemWaterIF97(308.15, 5.0);
    water.setTotalFlowRate(2.0, "kg/sec");
    new ThermodynamicOperations(water).TPflash();
    water.initProperties();
    assertEquals(water.getEnthalpy(), seawater.getEnthalpy(), 1e-6);
    assertEquals(water.getCp(), seawater.getCp(), 1e-6);
    assertEquals(water.getDensity("kg/m3"), seawater.getDensity("kg/m3"), 1e-8);
    assertEquals(water.getViscosity("kg/msec"), seawater.getViscosity("kg/msec"), 1e-12);
    assertEquals(water.getThermalConductivity("W/mK"), seawater.getThermalConductivity("W/mK"), 1e-12);
  }

  /**
   * Checks clone ownership, serialization, and changed-salinity property initialization.
   *
   * @throws Exception if the round-trip serialization fails
   */
  @Test
  void seawaterCopiesPreserveBulkComposition() throws Exception {
    SystemSeawater original = new SystemSeawater(293.15, 3.0, 35.0);
    original.setTotalFlowRate(4.0, "kg/sec");
    new ThermodynamicOperations(original).TPflash();
    original.initProperties();
    SystemSeawater copy = original.clone();
    copy.setSalinity(0.0);
    new ThermodynamicOperations(copy).TPflash();
    copy.initProperties();
    assertEquals(35.0, original.getSalinity(), 0.0);
    assertTrue(original.getDensity("kg/m3") > copy.getDensity("kg/m3"));
    assertTrue(original.getCp("J/kgK") < copy.getCp("J/kgK"));
    assertTrue(original.getViscosity("kg/msec") > copy.getViscosity("kg/msec"));
    assertTrue(original.getThermalConductivity("W/mK") < copy.getThermalConductivity("W/mK"));

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }
    SystemSeawater restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (SystemSeawater) input.readObject();
    }
    new ThermodynamicOperations(restored).TPflash();
    restored.initProperties();
    assertEquals(35.0, restored.getSalinity(), 0.0);
    assertEquals(original.getEnthalpy(), restored.getEnthalpy(), 1e-8);
    assertEquals(original.getViscosity("kg/msec"), restored.getViscosity("kg/msec"), 1e-12);
    assertEquals(original.getFlowRate("m3/sec"), restored.getFlowRate("m3/sec"), 1e-12);
  }
}
