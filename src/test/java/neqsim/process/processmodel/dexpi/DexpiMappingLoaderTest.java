package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.EquipmentEnum;

/**
 * Tests for {@link DexpiMappingLoader}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class DexpiMappingLoaderTest extends NeqSimTest {

  /**
   * Clears the mapping cache after each test to avoid state leaking between tests.
   */
  @AfterEach
  public void tearDown() {
    DexpiMappingLoader.clearCache();
  }

  /**
   * Tests that equipment mapping loads successfully from properties file.
   */
  @Test
  public void testLoadEquipmentMappingNotEmpty() {
    Map<String, EquipmentEnum> mapping = DexpiMappingLoader.loadEquipmentMapping();

    assertNotNull(mapping);
    assertFalse(mapping.isEmpty(), "Equipment mapping should not be empty");
  }

  /**
   * Tests that common equipment classes are present in the mapping.
   */
  @Test
  public void testEquipmentMappingContainsCommonClasses() {
    Map<String, EquipmentEnum> mapping = DexpiMappingLoader.loadEquipmentMapping();

    assertEquals(EquipmentEnum.Separator, mapping.get("Separator"));
    assertEquals(EquipmentEnum.Compressor, mapping.get("CentrifugalCompressor"));
    assertEquals(EquipmentEnum.Pump, mapping.get("CentrifugalPump"));
    assertEquals(EquipmentEnum.Heater, mapping.get("FiredHeater"));
    assertEquals(EquipmentEnum.HeatExchanger, mapping.get("ShellAndTubeHeatExchanger"));
  }

  /**
   * Tests that piping component mapping loads successfully from properties file.
   */
  @Test
  public void testLoadPipingMappingNotEmpty() {
    Map<String, EquipmentEnum> mapping = DexpiMappingLoader.loadPipingComponentMapping();

    assertNotNull(mapping);
    assertFalse(mapping.isEmpty(), "Piping component mapping should not be empty");
  }

  /**
   * Tests that piping component mapping contains valve types.
   */
  @Test
  public void testPipingMappingContainsValves() {
    Map<String, EquipmentEnum> mapping = DexpiMappingLoader.loadPipingComponentMapping();

    assertEquals(EquipmentEnum.ThrottlingValve, mapping.get("GateValve"));
    assertEquals(EquipmentEnum.ThrottlingValve, mapping.get("BallValve"));
    assertEquals(EquipmentEnum.ThrottlingValve, mapping.get("ButterflyValve"));
  }

  /**
   * Tests that mappings are unmodifiable.
   */
  @Test
  public void testMappingsAreUnmodifiable() {
    Map<String, EquipmentEnum> mapping = DexpiMappingLoader.loadEquipmentMapping();

    try {
      mapping.put("TestClass", EquipmentEnum.Stream);
      // If put succeeds, the map is not truly unmodifiable - test fails
      assertTrue(false, "Map should be unmodifiable");
    } catch (UnsupportedOperationException e) {
      // Expected behavior
      assertTrue(true);
    }
  }

  /**
   * Tests that the cache returns the same object on subsequent calls.
   */
  @Test
  public void testCachingReturnsSameInstance() {
    Map<String, EquipmentEnum> first = DexpiMappingLoader.loadEquipmentMapping();
    Map<String, EquipmentEnum> second = DexpiMappingLoader.loadEquipmentMapping();

    assertTrue(first == second, "Cached mapping should return same instance");
  }

  /**
   * Tests that clearCache forces a reload.
   */
  @Test
  public void testClearCacheAndReload() {
    Map<String, EquipmentEnum> first = DexpiMappingLoader.loadEquipmentMapping();
    DexpiMappingLoader.clearCache();
    Map<String, EquipmentEnum> reloaded = DexpiMappingLoader.loadEquipmentMapping();

    // Should be equal in content but may be a different object
    assertEquals(first.size(), reloaded.size());
  }
}
