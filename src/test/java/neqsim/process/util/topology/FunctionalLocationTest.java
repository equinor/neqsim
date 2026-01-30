package neqsim.process.util.topology;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for FunctionalLocation class.
 */
public class FunctionalLocationTest {

  @Test
  void testParseStidTag() {
    FunctionalLocation loc = new FunctionalLocation("1775-KA-23011A");

    assertEquals("1775", loc.getInstallationCode());
    assertEquals("Gullfaks C", loc.getInstallationName());
    assertEquals("KA", loc.getEquipmentTypeCode());
    assertEquals("Compressor", loc.getEquipmentTypeDescription());
    assertEquals("23011", loc.getSequentialNumber());
    assertEquals("A", loc.getTrainSuffix());
    assertEquals("1775-KA-23011A", loc.getFullTag());
    assertTrue(loc.isParallelUnit());
  }

  @Test
  void testBuildTag() {
    FunctionalLocation loc = new FunctionalLocation("1775", "PA", "24001", "B");

    assertEquals("1775-PA-24001B", loc.getFullTag());
    assertEquals("Pump", loc.getEquipmentTypeDescription());
    assertEquals("Gullfaks C", loc.getInstallationName());
  }

  @Test
  void testBuilder() {
    FunctionalLocation loc = FunctionalLocation.builder().installation("2540").type("VG")
        .number("30001").train(null).description("HP Separator").build();

    assertEquals("2540-VG-30001", loc.getFullTag());
    assertEquals("Separator", loc.getEquipmentTypeDescription());
    assertEquals("Åsgard A", loc.getInstallationName());
    assertEquals("HP Separator", loc.getDescription());
    assertFalse(loc.isParallelUnit());
  }

  @Test
  void testIsParallelTo() {
    FunctionalLocation locA = new FunctionalLocation("1775-KA-23011A");
    FunctionalLocation locB = new FunctionalLocation("1775-KA-23011B");
    FunctionalLocation locOther = new FunctionalLocation("1775-KA-23012A");

    assertTrue(locA.isParallelTo(locB));
    assertTrue(locB.isParallelTo(locA));
    assertFalse(locA.isParallelTo(locOther));
  }

  @Test
  void testIsSameInstallation() {
    FunctionalLocation loc1 = new FunctionalLocation("1775-KA-23011A");
    FunctionalLocation loc2 = new FunctionalLocation("1775-PA-24001B");
    FunctionalLocation loc3 = new FunctionalLocation("2540-KA-23011A");

    assertTrue(loc1.isSameInstallation(loc2));
    assertFalse(loc1.isSameInstallation(loc3));
  }

  @Test
  void testIsSameSystem() {
    FunctionalLocation loc1 = new FunctionalLocation("1775-KA-23011A");
    FunctionalLocation loc2 = new FunctionalLocation("1775-PA-23022B");
    FunctionalLocation loc3 = new FunctionalLocation("1775-VA-24001A");

    assertTrue(loc1.isSameSystem(loc2)); // Both in system 23
    assertFalse(loc1.isSameSystem(loc3)); // Different systems (23 vs 24)
  }

  @Test
  void testGetBaseTag() {
    FunctionalLocation loc = new FunctionalLocation("1775-KA-23011A");
    assertEquals("1775-KA-23011", loc.getBaseTag());
  }

  @Test
  void testEquipmentTypeCodes() {
    assertEquals("Compressor",
        new FunctionalLocation("1775-KA-00001").getEquipmentTypeDescription());
    assertEquals("Pump", new FunctionalLocation("1775-PA-00001").getEquipmentTypeDescription());
    assertEquals("Valve", new FunctionalLocation("1775-VA-00001").getEquipmentTypeDescription());
    assertEquals("Heat Exchanger",
        new FunctionalLocation("1775-WA-00001").getEquipmentTypeDescription());
    assertEquals("Separator",
        new FunctionalLocation("1775-VG-00001").getEquipmentTypeDescription());
    assertEquals("Turbine", new FunctionalLocation("1775-GA-00001").getEquipmentTypeDescription());
    assertEquals("Cooler", new FunctionalLocation("1775-WC-00001").getEquipmentTypeDescription());
    assertEquals("Heater", new FunctionalLocation("1775-WH-00001").getEquipmentTypeDescription());
  }

  @Test
  void testInstallationCodes() {
    assertEquals("Gullfaks A", new FunctionalLocation("1770-KA-00001").getInstallationName());
    assertEquals("Gullfaks B", new FunctionalLocation("1773-KA-00001").getInstallationName());
    assertEquals("Gullfaks C", new FunctionalLocation("1775-KA-00001").getInstallationName());
    assertEquals("Åsgard A", new FunctionalLocation("2540-KA-00001").getInstallationName());
    assertEquals("Åsgard B", new FunctionalLocation("2541-KA-00001").getInstallationName());
    assertEquals("Åsgard C", new FunctionalLocation("2542-KA-00001").getInstallationName());
    assertEquals("Troll A", new FunctionalLocation("1910-KA-00001").getInstallationName());
  }

  @Test
  void testEqualsAndHashCode() {
    FunctionalLocation loc1 = new FunctionalLocation("1775-KA-23011A");
    FunctionalLocation loc2 = new FunctionalLocation("1775-KA-23011A");
    FunctionalLocation loc3 = new FunctionalLocation("1775-KA-23011B");

    assertEquals(loc1, loc2);
    assertEquals(loc1.hashCode(), loc2.hashCode());
    assertNotEquals(loc1, loc3);
  }

  @Test
  void testCompareTo() {
    FunctionalLocation loc1 = new FunctionalLocation("1775-KA-23011A");
    FunctionalLocation loc2 = new FunctionalLocation("1775-KA-23011B");

    assertTrue(loc1.compareTo(loc2) < 0);
    assertTrue(loc2.compareTo(loc1) > 0);
  }
}
