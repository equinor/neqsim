package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.util.LubeOilSystem.ConsumerType;
import neqsim.process.equipment.util.LubeOilSystem.OilGrade;
import neqsim.process.equipment.util.LubeOilSystem.ServiceCategory;

/**
 * Unit tests for the API 614 / ISO 10438 lube oil console.
 */
public class LubeOilSystemTest {

  private LubeOilSystem console;

  @BeforeEach
  public void setUp() {
    console = new LubeOilSystem("20-LO-001", ServiceCategory.SPECIAL_PURPOSE);
    console.setOilGrade(OilGrade.ISO_VG_46);
    console.setSupplyTemperature(45.0);
    console.addConsumerFromShaftPower("Compressor bearings", ConsumerType.JOURNAL_BEARING, 6000.0);
    console.addConsumerFromShaftPower("Gearbox", ConsumerType.GEARBOX, 6000.0);
    console.addControlOilConsumer("Governor", 60.0, 10.0);
  }

  @Test
  public void testConstruction() {
    assertEquals("20-LO-001", console.getName());
    assertEquals(ServiceCategory.SPECIAL_PURPOSE, console.getServiceCategory());
    assertEquals(3, console.getConsumers().size());
  }

  @Test
  public void testOilPropertyCorrelations() {
    assertEquals(46.0, console.getOilKinematicViscosity(40.0), 0.2);
    assertEquals(6.8, console.getOilKinematicViscosity(100.0), 0.05);
    assertTrue(console.getOilKinematicViscosity(45.0) < console.getOilKinematicViscosity(40.0));
    assertTrue(console.getOilDensity(45.0) < OilGrade.ISO_VG_46.getDensity15());
    assertEquals(2.0, console.getOilHeatCapacity(45.0), 0.15);
  }

  @Test
  public void testFlowFromHeatBalance() {
    console.run();

    double heatLoad = 6000.0 * ConsumerType.JOURNAL_BEARING.getLossFraction();
    double flowLpm = console.getConsumers().get(0).getCalculatedFlow();
    double flowM3PerSec = flowLpm / 60000.0;
    double recoveredDuty = flowM3PerSec * console.getOilDensityAtSupply() * console.getOilHeatCapacityAtSupply()
        * ConsumerType.JOURNAL_BEARING.getTemperatureRise();

    assertEquals(heatLoad, recoveredDuty, 1e-6);
    assertEquals(60.0, console.getControlOilFlow(), 1e-9);
    assertEquals(console.getLubeOilFlow() + console.getControlOilFlow(), console.getNormalOilFlow() / 0.06, 1e-6);
  }

  @Test
  public void testPumpSizing() {
    console.run();

    assertEquals(console.getNormalOilFlow() * 1.10, console.getRatedPumpFlow(), 1e-9);
    // Control oil header pressure governs the pump discharge pressure
    assertTrue(console.getPumpDischargePressure() > console.getControlOilHeaderPressure());
    assertTrue(console.getPumpShaftPower() > console.getPumpHydraulicPower());
    assertTrue(console.getPumpMotorRating() >= console.getPumpShaftPower() * 1.10);
  }

  @Test
  public void testReservoirSizing() {
    console.run();

    assertTrue(console.getReservoirRetentionTime() >= 5.0);
    assertTrue(console.getReservoirTurnoverRate() <= LubeOilSystem.MAX_RESERVOIR_TURNOVERS_PER_HOUR + 1e-9);
    assertTrue(console.getReservoirTotalVolume() > console.getReservoirWorkingVolume());
  }

  @Test
  public void testGeneralPurposeReservoirIsSmaller() {
    console.run();
    double specialPurposeVolume = console.getReservoirWorkingVolume();

    console.setServiceCategory(ServiceCategory.GENERAL_PURPOSE);
    console.run();

    assertTrue(console.getReservoirWorkingVolume() <= specialPurposeVolume);
    assertEquals(0.0, console.getRundownTankVolume(), 1e-12);
  }

  @Test
  public void testCoolerAndCoolingWaterDuty() {
    console.run();

    assertEquals(console.getTotalHeatLoad() + console.getPumpShaftPower(), console.getCoolerDuty(), 1e-9);
    double waterRise = console.getCoolingWaterOutletTemperature() - console.getCoolingWaterInletTemperature();
    assertEquals(console.getCoolerDuty() / (4.18 * waterRise) * 3.6, console.getCoolingWaterFlow(), 1e-6);
  }

  @Test
  public void testRundownTankAndAccumulator() {
    console.setRundownTime(3.0);
    console.run();

    assertEquals(console.getLubeOilFlow() / 1000.0 * 3.0, console.getRundownTankVolume(), 1e-9);
    assertTrue(console.getAccumulatorVolume() > 0.0);
    assertTrue(console.getAccumulatorPrechargePressure() < console.getLubeHeaderPressure() + 1.01325);
  }

  @Test
  public void testHeaterWattDensityLimit() {
    console.run();

    assertTrue(console.getHeaterPower() > 0.0);
    double wattDensity = console.getHeaterPower() * 1000.0 / (console.getHeaterMinimumArea() * 10000.0);
    assertEquals(LubeOilSystem.MAX_HEATER_WATT_DENSITY, wattDensity, 1e-6);
  }

  @Test
  public void testPipingSizing() {
    console.run();

    double area = Math.PI * Math.pow(console.getSupplyPipeDiameter(), 2) / 4.0;
    double velocity = console.getRatedPumpFlow() / 3600.0 / area;
    assertEquals(console.getMaxSupplyVelocity(), velocity, 1e-9);

    // Gravity drains run half full and are therefore much larger than the supply line
    assertTrue(console.getDrainPipeDiameter() > console.getSupplyPipeDiameter());
  }

  @Test
  public void testApiCompliance() {
    console.run();

    assertTrue(console.isApiCompliant());
    assertTrue(console.getDeviations().isEmpty());
    assertFalse(console.getComplianceChecks().isEmpty());
  }

  @Test
  public void testHighSupplyTemperatureIsFlagged() {
    console.setSupplyTemperature(55.0);
    console.run();

    assertFalse(console.isApiCompliant());
    assertEquals(1, console.getDeviations().size());
    assertEquals("Bearing oil supply temperature", console.getDeviations().get(0).getItem());
  }

  @Test
  public void testSingleFilterIsFlagged() {
    console.setNumberOfFilters(1);
    console.setFilterRatingMicron(25.0);
    console.run();

    assertFalse(console.isApiCompliant());
    assertEquals(2, console.getDeviations().size());
  }

  @Test
  public void testValidateSetup() {
    LubeOilSystem empty = new LubeOilSystem("empty console");
    assertFalse(empty.validateSetup().isValid());

    assertTrue(console.validateSetup().isValid());
  }

  @Test
  public void testToJson() {
    console.run();
    String json = console.toJson();

    assertNotNull(json);
    assertTrue(json.contains("API 614"));
    assertTrue(json.contains("reservoir"));
    assertTrue(json.contains("complianceChecks"));
  }
}
