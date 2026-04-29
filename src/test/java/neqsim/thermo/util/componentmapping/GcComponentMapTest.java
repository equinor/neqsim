package neqsim.thermo.util.componentmapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GcComponentMap}.
 *
 * @author NeqSim Agent
 * @version 1.0
 */
class GcComponentMapTest {

  /**
   * Tests that the singleton loads and has entries.
   */
  @Test
  void testSingletonLoads() {
    GcComponentMap map = GcComponentMap.getInstance();
    assertNotNull(map);
    assertTrue(map.size() > 50, "Dictionary should have at least 50 entries");
  }

  /**
   * Tests exact match resolution for common components.
   */
  @Test
  void testResolveExactMatch() {
    GcComponentMap map = GcComponentMap.getInstance();
    assertEquals("methane", map.resolve("Methane"));
    assertEquals("ethane", map.resolve("Ethane"));
    assertEquals("i-butane", map.resolve("i-Butane"));
    assertEquals("CO2", map.resolve("CO2"));
    assertEquals("nitrogen", map.resolve("N2"));
    assertEquals("water", map.resolve("H2O"));
  }

  /**
   * Tests case-insensitive matching.
   */
  @Test
  void testCaseInsensitive() {
    GcComponentMap map = GcComponentMap.getInstance();
    assertEquals("methane", map.resolve("methane"));
    assertEquals("methane", map.resolve("METHANE"));
    assertEquals("methane", map.resolve("Methane"));
  }

  /**
   * Tests whitespace tolerance.
   */
  @Test
  void testWhitespaceTolerance() {
    GcComponentMap map = GcComponentMap.getInstance();
    assertEquals("methane", map.resolve("  Methane  "));
    assertEquals("CO2", map.resolve(" CO2 "));
  }

  /**
   * Tests that unknown labels throw in strict mode.
   */
  @Test
  void testResolveThrowsOnUnknown() {
    GcComponentMap map = GcComponentMap.getInstance();
    assertThrows(IllegalArgumentException.class, () -> map.resolve("UnknownGasXYZ"));
  }

  /**
   * Tests that null label throws in strict mode.
   */
  @Test
  void testResolveThrowsOnNull() {
    GcComponentMap map = GcComponentMap.getInstance();
    assertThrows(IllegalArgumentException.class, () -> map.resolve(null));
  }

  /**
   * Tests lenient resolution for unknown labels.
   */
  @Test
  void testResolveLenient() {
    GcComponentMap map = GcComponentMap.getInstance();
    assertEquals("methane", map.resolveLenient("Methane"));
    assertEquals("UnknownGasXYZ", map.resolveLenient("UnknownGasXYZ"));
  }

  /**
   * Tests PNA class retrieval.
   */
  @Test
  void testGetPnaClass() {
    GcComponentMap map = GcComponentMap.getInstance();
    assertEquals("P", map.getPnaClass("Methane"));
    assertEquals("N", map.getPnaClass("cyclohexane"));
    assertEquals("A", map.getPnaClass("Benzene"));
    assertEquals("O", map.getPnaClass("CO2"));
    assertEquals("", map.getPnaClass("UnknownXYZ"));
  }

  /**
   * Tests the contains check.
   */
  @Test
  void testContains() {
    GcComponentMap map = GcComponentMap.getInstance();
    assertTrue(map.contains("Methane"));
    assertTrue(map.contains("methane"));
    assertFalse(map.contains("UnknownXYZ"));
    assertFalse(map.contains(null));
  }

  /**
   * Tests co-elution group detection — multiple labels for the same NeqSim name.
   */
  @Test
  void testCoElutionGroup() {
    GcComponentMap map = GcComponentMap.getInstance();
    List<String> group = map.findCoElutionGroup("i-pentane");
    assertTrue(group.size() >= 3,
        "i-pentane should have at least 3 aliases: i-Pentane, i-pentane, isopentane, 2-M-C4");
  }

  /**
   * Tests Norwegian GC label spelling variant.
   */
  @Test
  void testNorwegianSpellingVariant() {
    GcComponentMap map = GcComponentMap.getInstance();
    assertEquals("ethylbenzene", map.resolve("Etylbenzene"));
  }
}
