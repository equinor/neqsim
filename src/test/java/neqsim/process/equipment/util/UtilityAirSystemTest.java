package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.util.UtilityAirSystem.AirQualityClass;
import neqsim.process.equipment.util.UtilityAirSystem.CompressorType;
import neqsim.process.equipment.util.UtilityAirSystem.DryerType;

/**
 * Unit tests for UtilityAirSystem class.
 */
public class UtilityAirSystemTest {

  private UtilityAirSystem airSystem;

  @BeforeEach
  public void setUp() {
    airSystem = new UtilityAirSystem("Instrument Air System", 500.0);
  }

  @Test
  public void testConstruction() {
    assertEquals("Instrument Air System", airSystem.getName());
    assertEquals(500.0, airSystem.getTotalAirDemand(), 1e-6);
  }

  @Test
  public void testQualityClassConstruction() {
    UtilityAirSystem system = new UtilityAirSystem("Breathing Air", 100.0, AirQualityClass.CLASS_1);

    assertEquals(AirQualityClass.CLASS_1, system.getTargetQuality());
  }

  @Test
  public void testRunCalculation() {
    airSystem.setDischargePressure(8.0);
    airSystem.setInletTemperature(25.0);
    airSystem.setInletRelativeHumidity(70.0);
    airSystem.run();

    assertTrue(airSystem.getCompressorPowerKW() > 0);
    assertTrue(airSystem.getSpecificEnergy() > 0);
    assertTrue(airSystem.getDryerPurgeLoss() >= 0);
    assertTrue(airSystem.getCondensateVolume() >= 0);
  }

  @Test
  public void testDryerPurgeLoss() {
    airSystem.setDryerType(DryerType.DESICCANT_HEATED);
    airSystem.run();

    double purgeLoss = airSystem.getDryerPurgeLoss();
    // Desiccant dryer has ~8% purge loss
    assertTrue(purgeLoss > 0);
    assertTrue(purgeLoss < airSystem.getTotalAirDemand() * 0.20);
  }

  @Test
  public void testQualityTargetMet() {
    airSystem.setTargetQuality(AirQualityClass.CLASS_2);
    airSystem.setDryerType(DryerType.DESICCANT_HEATED);
    airSystem.run();

    assertTrue(airSystem.isQualityTargetMet());
  }

  @Test
  public void testReceiverHoldup() {
    airSystem.setReceiverVolume(10.0);
    airSystem.setDischargePressure(8.0);
    airSystem.setTotalAirDemand(500.0);
    airSystem.run();

    double holdupMinutes = airSystem.getReceiverHoldupMinutes();
    assertTrue(holdupMinutes > 0);
  }

  @Test
  public void testConsumerManagement() {
    UtilityAirSystem system = new UtilityAirSystem("Test System");

    system.addConsumer("Control Valves", 200.0, AirQualityClass.CLASS_2);
    system.addConsumer("Pneumatic Tools", 100.0, AirQualityClass.CLASS_4);

    assertEquals(300.0, system.getTotalAirDemand(), 1e-6);
  }

  @Test
  public void testAirQualityClasses() {
    // Class 1 should have lowest dew point
    assertTrue(
        AirQualityClass.CLASS_1.getMaxDewPointC() < AirQualityClass.CLASS_2.getMaxDewPointC());

    // Class 1 should have lowest oil content
    assertTrue(AirQualityClass.CLASS_1.getMaxOilMgM3() < AirQualityClass.CLASS_2.getMaxOilMgM3());

    // Class 1 should have smallest particle size
    assertTrue(AirQualityClass.CLASS_1.getMaxParticleSizeMicron() < AirQualityClass.CLASS_5
        .getMaxParticleSizeMicron());
  }

  @Test
  public void testDryerTypes() {
    // Desiccant should achieve lower dew point than refrigerated
    assertTrue(DryerType.DESICCANT_HEATED.getAchievableDewPointC() < DryerType.REFRIGERATED
        .getAchievableDewPointC());

    // Refrigerated should have higher yield (less purge)
    assertTrue(DryerType.REFRIGERATED.getAirYieldFraction() > DryerType.DESICCANT_HEATLESS
        .getAirYieldFraction());
  }

  @Test
  public void testAutoSize() {
    airSystem.setTotalAirDemand(1000.0);
    airSystem.autoSize();

    assertTrue(airSystem.getReceiverVolume() > 0);
    assertEquals(3, airSystem.getNumberOfCompressors());
  }

  @Test
  public void testAnnualOperatingCost() {
    airSystem.run();

    double cost = airSystem.calculateAnnualOperatingCost(0.10, 8760.0);
    assertTrue(cost > 0);
  }

  @Test
  public void testJsonOutput() {
    airSystem.run();

    String json = airSystem.toJson();

    assertNotNull(json);
    assertTrue(json.contains("systemName"));
    assertTrue(json.contains("totalAirDemandNm3h"));
    assertTrue(json.contains("operatingResults"));
  }

  @Test
  public void testDifferentCompressorTypes() {
    airSystem.setCompressorType(CompressorType.ROTARY_SCREW);
    assertEquals(CompressorType.ROTARY_SCREW, airSystem.getCompressorType());

    airSystem.setCompressorType(CompressorType.CENTRIFUGAL);
    assertEquals(CompressorType.CENTRIFUGAL, airSystem.getCompressorType());
  }

  @Test
  public void testHighHumidityConditions() {
    airSystem.setInletRelativeHumidity(95.0);
    airSystem.setInletTemperature(35.0);
    airSystem.run();

    // High humidity should produce more condensate
    assertTrue(airSystem.getCondensateVolume() > 0);
  }

  @Test
  public void testLowHumidityConditions() {
    airSystem.setInletRelativeHumidity(20.0);
    airSystem.setInletTemperature(10.0);
    airSystem.run();

    // Low humidity should produce less condensate
    double lowHumidityCondensate = airSystem.getCondensateVolume();

    airSystem.setInletRelativeHumidity(90.0);
    airSystem.run();
    double highHumidityCondensate = airSystem.getCondensateVolume();

    assertTrue(lowHumidityCondensate < highHumidityCondensate);
  }

  @Test
  public void testPressureEffect() {
    airSystem.setDischargePressure(4.0);
    airSystem.run();
    double lowPressurePower = airSystem.getCompressorPowerKW();

    airSystem.setDischargePressure(10.0);
    airSystem.run();
    double highPressurePower = airSystem.getCompressorPowerKW();

    assertTrue(highPressurePower > lowPressurePower);
  }

  @Test
  public void testAirConsumerClass() {
    UtilityAirSystem.AirConsumer consumer =
        new UtilityAirSystem.AirConsumer("Test Consumer", 100.0, AirQualityClass.CLASS_3);

    assertEquals("Test Consumer", consumer.getName());
    assertEquals(100.0, consumer.getDemandNm3h(), 1e-6);
    assertEquals(AirQualityClass.CLASS_3, consumer.getRequiredQuality());
    assertFalse(consumer.isCritical());

    consumer.setCritical(true);
    assertTrue(consumer.isCritical());
  }
}
