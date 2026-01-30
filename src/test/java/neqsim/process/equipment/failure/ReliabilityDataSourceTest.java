package neqsim.process.equipment.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for ReliabilityDataSource.
 */
class ReliabilityDataSourceTest {

  @Test
  void testGetInstance() {
    ReliabilityDataSource dataSource = ReliabilityDataSource.getInstance();
    assertNotNull(dataSource);

    // Same instance should be returned
    ReliabilityDataSource dataSource2 = ReliabilityDataSource.getInstance();
    assertEquals(dataSource, dataSource2);
  }

  @Test
  void testGetCompressorReliability() {
    ReliabilityDataSource dataSource = ReliabilityDataSource.getInstance();

    ReliabilityDataSource.ReliabilityData data =
        dataSource.getReliabilityData("Compressor", "Centrifugal");

    assertNotNull(data);
    assertEquals("Compressor", data.getEquipmentType());
    assertEquals("Centrifugal", data.getSubType());
    assertTrue(data.getMtbf() > 0);
    assertTrue(data.getMttr() > 0);
    assertTrue(data.getAvailability() > 0 && data.getAvailability() <= 1);
  }

  @Test
  void testGetPumpReliability() {
    ReliabilityDataSource dataSource = ReliabilityDataSource.getInstance();

    ReliabilityDataSource.ReliabilityData data =
        dataSource.getReliabilityData("Pump", "Centrifugal");

    assertNotNull(data);
    assertTrue(data.getMtbf() > 0);
    assertTrue(data.getFailuresPerYear() > 0);
  }

  @Test
  void testGetCompressorFailureModes() {
    ReliabilityDataSource dataSource = ReliabilityDataSource.getInstance();

    List<ReliabilityDataSource.FailureModeData> modes =
        dataSource.getFailureModes("Compressor", "Centrifugal");

    assertNotNull(modes);
    assertTrue(modes.size() > 0);

    // Check probabilities sum to approximately 100%
    double totalProb = 0;
    for (ReliabilityDataSource.FailureModeData mode : modes) {
      totalProb += mode.getProbability();
      assertNotNull(mode.getFailureMode());
    }
    assertTrue(totalProb >= 90 && totalProb <= 110, "Failure mode probabilities should sum ~100%");
  }

  @Test
  void testGetEquipmentTypes() {
    ReliabilityDataSource dataSource = ReliabilityDataSource.getInstance();

    List<String> types = dataSource.getEquipmentTypes();

    assertNotNull(types);
    assertTrue(types.size() > 0);
    assertTrue(types.contains("Compressor"));
    assertTrue(types.contains("Pump"));
    assertTrue(types.contains("HeatExchanger"));
  }

  @Test
  void testGetSubTypes() {
    ReliabilityDataSource dataSource = ReliabilityDataSource.getInstance();

    List<String> subTypes = dataSource.getSubTypes("Compressor");

    assertNotNull(subTypes);
    assertTrue(subTypes.size() > 0);
    assertTrue(subTypes.contains("Centrifugal"));
  }

  @Test
  void testCreateFailureMode() {
    ReliabilityDataSource dataSource = ReliabilityDataSource.getInstance();

    EquipmentFailureMode mode =
        dataSource.createFailureMode("My Compressor", "Compressor", "Centrifugal");

    assertNotNull(mode);
    assertTrue(mode.getName().contains("My Compressor"));
    assertTrue(mode.getMttr() > 0);
  }

  @Test
  void testFailureModeDataConversion() {
    ReliabilityDataSource.FailureModeData data =
        new ReliabilityDataSource.FailureModeData("Compressor", "Seal Failure", 20.0);
    data.setTypicalMttr(72);
    data.setSeverity("High");

    EquipmentFailureMode mode = data.toEquipmentFailureMode("Test Compressor");

    assertNotNull(mode);
    assertEquals(72, mode.getMttr(), 0.01);
    // Check that the name contains the equipment name
    assertTrue(mode.getName().contains("Test Compressor"));
  }

  @Test
  void testReliabilityDataCalculations() {
    ReliabilityDataSource.ReliabilityData data =
        new ReliabilityDataSource.ReliabilityData("Test", "Type", 8760, 24);

    // 1 failure per year
    assertEquals(1.0, data.getFailuresPerYear(), 0.01);

    // Availability = 8760 / (8760 + 24)
    double expectedAvail = 8760.0 / (8760.0 + 24.0);
    assertEquals(expectedAvail, data.getAvailability(), 0.001);

    // Failure rate per million hours
    double expectedRate = 1e6 / 8760;
    assertEquals(expectedRate, data.getFailureRate(), 0.1);
  }

  @Test
  void testUnknownEquipment() {
    ReliabilityDataSource dataSource = ReliabilityDataSource.getInstance();

    ReliabilityDataSource.ReliabilityData data =
        dataSource.getReliabilityData("Unknown Equipment", "Unknown Type");

    // Should return null for unknown equipment
    assertTrue(data == null);
  }
}
