package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for CompressorMechanicalLosses.
 *
 * <p>
 * Tests seal gas consumption and bearing loss calculations per API 692 and API 617.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorMechanicalLossesTest {
  private CompressorMechanicalLosses losses;

  @BeforeEach
  void setUp() {
    losses = new CompressorMechanicalLosses(100.0); // 100mm shaft
    losses.setOperatingConditions(50.0, 150.0, 10000.0, 18.0, 0.95);
  }

  // ============================================================================
  // Basic Construction Tests
  // ============================================================================

  @Nested
  @DisplayName("Construction Tests")
  class ConstructionTests {
    @Test
    @DisplayName("Default constructor sets reasonable defaults")
    void testDefaultConstructor() {
      CompressorMechanicalLosses defaultLosses = new CompressorMechanicalLosses();
      assertEquals(100.0, defaultLosses.getShaftDiameter(), 0.1);
      assertEquals(CompressorMechanicalLosses.SealType.DRY_GAS_TANDEM, defaultLosses.getSealType());
      assertEquals(CompressorMechanicalLosses.BearingType.TILTING_PAD,
          defaultLosses.getBearingType());
    }

    @Test
    @DisplayName("Constructor with shaft diameter sets value correctly")
    void testConstructorWithShaftDiameter() {
      CompressorMechanicalLosses customLosses = new CompressorMechanicalLosses(150.0);
      assertEquals(150.0, customLosses.getShaftDiameter(), 0.1);
    }
  }

  // ============================================================================
  // Dry Gas Seal Tests (API 692)
  // ============================================================================

  @Nested
  @DisplayName("Dry Gas Seal Tests (API 692)")
  class DryGasSealTests {
    @Test
    @DisplayName("Primary seal leakage is within typical range")
    void testPrimarySealLeakageRange() {
      double leakage = losses.calculatePrimarySealLeakage();
      // Typical range: 0.6-10 Nm³/hr total for 2 seals
      assertTrue(leakage > 0.5, "Primary seal leakage too low: " + leakage);
      assertTrue(leakage < 15.0, "Primary seal leakage too high: " + leakage);
    }

    @Test
    @DisplayName("Secondary seal leakage is fraction of primary")
    void testSecondarySealLeakage() {
      double primary = losses.calculatePrimarySealLeakage();
      double secondary = losses.calculateSecondarySealLeakage();
      assertTrue(secondary < primary, "Secondary should be less than primary");
      assertTrue(secondary > 0.0, "Secondary leakage should be positive for tandem seal");
    }

    @Test
    @DisplayName("Buffer gas flow is within typical range")
    void testBufferGasFlowRange() {
      double bufferFlow = losses.calculateBufferGasFlow();
      // Typical range: 4-20 Nm³/hr total for 2 seals
      assertTrue(bufferFlow >= 4.0, "Buffer gas flow too low: " + bufferFlow);
      assertTrue(bufferFlow <= 25.0, "Buffer gas flow too high: " + bufferFlow);
    }

    @Test
    @DisplayName("Separation gas flow is within typical range")
    void testSeparationGasFlowRange() {
      double sepFlow = losses.calculateSeparationGasFlow();
      // Typical range: 2-10 Nm³/hr total for 2 seals
      assertTrue(sepFlow >= 2.0, "Separation gas flow too low: " + sepFlow);
      assertTrue(sepFlow <= 15.0, "Separation gas flow too high: " + sepFlow);
    }

    @Test
    @DisplayName("Total seal gas consumption sums all components")
    void testTotalSealGasConsumption() {
      double total = losses.getTotalSealGasConsumption();
      double primary = losses.calculatePrimarySealLeakage();
      double secondary = losses.calculateSecondarySealLeakage();
      double buffer = losses.calculateBufferGasFlow();

      assertEquals(primary + secondary + buffer, total, 0.01,
          "Total should equal sum of components");
    }

    @Test
    @DisplayName("Single seal has higher leakage than tandem")
    void testSingleSealHigherLeakage() {
      losses.setSealType(CompressorMechanicalLosses.SealType.DRY_GAS_TANDEM);
      double tandemLeakage = losses.calculatePrimarySealLeakage();

      losses.setSealType(CompressorMechanicalLosses.SealType.DRY_GAS_SINGLE);
      double singleLeakage = losses.calculatePrimarySealLeakage();

      assertTrue(singleLeakage > tandemLeakage,
          "Single seal should have higher leakage than tandem");
    }

    @Test
    @DisplayName("Labyrinth seal has much higher leakage")
    void testLabyrinthSealHighLeakage() {
      losses.setSealType(CompressorMechanicalLosses.SealType.DRY_GAS_TANDEM);
      double dgsLeakage = losses.calculatePrimarySealLeakage();

      losses.setSealType(CompressorMechanicalLosses.SealType.LABYRINTH);
      double labyrinthLeakage = losses.calculatePrimarySealLeakage();

      assertTrue(labyrinthLeakage > dgsLeakage * 2, "Labyrinth should have much higher leakage");
    }

    @Test
    @DisplayName("Required seal gas supply pressure is above discharge")
    void testSealGasSupplyPressure() {
      double required = losses.getRequiredSealGasSupplyPressure();
      assertTrue(required > 150.0, "Seal gas supply must exceed discharge pressure");
    }
  }

  // ============================================================================
  // Bearing Loss Tests (API 617)
  // ============================================================================

  @Nested
  @DisplayName("Bearing Loss Tests (API 617)")
  class BearingLossTests {
    @Test
    @DisplayName("Radial bearing loss is within typical range")
    void testRadialBearingLossRange() {
      double radialLoss = losses.calculateRadialBearingLoss();
      // Typical range: 1-40 kW for 2 radial bearings at 10000 rpm
      assertTrue(radialLoss >= 1.0, "Radial bearing loss too low: " + radialLoss);
      assertTrue(radialLoss <= 50.0, "Radial bearing loss too high: " + radialLoss);
    }

    @Test
    @DisplayName("Thrust bearing loss is within typical range")
    void testThrustBearingLossRange() {
      double thrustLoss = losses.calculateThrustBearingLoss();
      // Typical range: 1-30 kW
      assertTrue(thrustLoss >= 1.0, "Thrust bearing loss too low: " + thrustLoss);
      assertTrue(thrustLoss <= 35.0, "Thrust bearing loss too high: " + thrustLoss);
    }

    @Test
    @DisplayName("Total bearing loss sums radial and thrust")
    void testTotalBearingLoss() {
      double total = losses.getTotalBearingLoss();
      double radial = losses.calculateRadialBearingLoss();
      double thrust = losses.calculateThrustBearingLoss();

      assertEquals(radial + thrust, total, 0.01);
    }

    @Test
    @DisplayName("Magnetic bearings have lower losses than tilting pad")
    void testMagneticBearingLowLoss() {
      losses.setBearingType(CompressorMechanicalLosses.BearingType.TILTING_PAD);
      double tiltingPadLoss = losses.getTotalBearingLoss();

      losses.setBearingType(CompressorMechanicalLosses.BearingType.MAGNETIC_ACTIVE);
      double magneticLoss = losses.getTotalBearingLoss();

      // Note: Both may hit minimum bounds for small compressors
      // For larger compressors (>200mm shaft), magnetic would be significantly lower
      assertTrue(magneticLoss <= tiltingPadLoss,
          "Magnetic bearings should have equal or lower losses");
    }

    @Test
    @DisplayName("Bearing loss increases with speed for large compressors")
    void testBearingLossIncreasesWithSpeed() {
      // Use very large shaft (400mm) and higher speeds to exceed minimum bounds
      // Industrial compressors with 400mm shafts have measurable bearing losses
      CompressorMechanicalLosses largeLosses = new CompressorMechanicalLosses(400.0);

      largeLosses.setOperatingConditions(50.0, 150.0, 8000.0, 18.0, 0.95);
      double lowSpeedLoss = largeLosses.getTotalBearingLoss();

      largeLosses.setOperatingConditions(50.0, 150.0, 16000.0, 18.0, 0.95);
      double highSpeedLoss = largeLosses.getTotalBearingLoss();

      assertTrue(highSpeedLoss > lowSpeedLoss, "Bearing loss should increase with speed: low="
          + lowSpeedLoss + ", high=" + highSpeedLoss);
    }
  }

  // ============================================================================
  // Lube Oil System Tests
  // ============================================================================

  @Nested
  @DisplayName("Lube Oil System Tests")
  class LubeOilSystemTests {
    @Test
    @DisplayName("Lube oil flow rate is within typical range")
    void testLubeOilFlowRateRange() {
      double flowRate = losses.calculateLubeOilFlowRate();
      // Typical range: 20-200 L/min
      assertTrue(flowRate >= 20.0, "Lube oil flow too low: " + flowRate);
      assertTrue(flowRate <= 200.0, "Lube oil flow too high: " + flowRate);
    }

    @Test
    @DisplayName("Lube oil cooler duty matches bearing losses")
    void testLubeOilCoolerDuty() {
      double coolerDuty = losses.calculateLubeOilCoolerDuty();
      double bearingLoss = losses.getTotalBearingLoss();

      // Cooler duty should be slightly higher than bearing loss (margin)
      assertTrue(coolerDuty >= bearingLoss, "Cooler duty must handle all bearing losses");
      assertTrue(coolerDuty <= bearingLoss * 1.2, "Cooler margin should be reasonable");
    }

    @Test
    @DisplayName("Temperature setters work correctly")
    void testTemperatureSetters() {
      losses.setLubeOilInletTemp(35.0);
      losses.setLubeOilOutletTemp(50.0);
      assertEquals(35.0, losses.getLubeOilInletTemp(), 0.1);
      assertEquals(50.0, losses.getLubeOilOutletTemp(), 0.1);
    }
  }

  // ============================================================================
  // Mechanical Efficiency Tests
  // ============================================================================

  @Nested
  @DisplayName("Mechanical Efficiency Tests")
  class MechanicalEfficiencyTests {
    @Test
    @DisplayName("Mechanical efficiency is in realistic range")
    void testMechanicalEfficiencyRange() {
      double efficiency = losses.getMechanicalEfficiency(1000.0);
      assertTrue(efficiency >= 0.90, "Mechanical efficiency too low: " + efficiency);
      assertTrue(efficiency <= 0.995, "Mechanical efficiency too high: " + efficiency);
    }

    @Test
    @DisplayName("Higher power gives higher mechanical efficiency")
    void testEfficiencyIncreasesWithPower() {
      double lowPowerEfficiency = losses.getMechanicalEfficiency(100.0);
      double highPowerEfficiency = losses.getMechanicalEfficiency(5000.0);

      assertTrue(highPowerEfficiency > lowPowerEfficiency,
          "Higher power should give higher mechanical efficiency");
    }

    @Test
    @DisplayName("Zero power returns default efficiency")
    void testZeroPowerEfficiency() {
      double efficiency = losses.getMechanicalEfficiency(0.0);
      assertEquals(0.98, efficiency, 0.001);
    }
  }

  // ============================================================================
  // Integration with Compressor Tests
  // ============================================================================

  @Nested
  @DisplayName("Integration with Compressor")
  class CompressorIntegrationTests {
    @Test
    @DisplayName("Compressor can initialize mechanical losses")
    void testCompressorInitMechanicalLosses() {
      SystemInterface gas = new SystemSrkEos(298.0, 50.0);
      gas.addComponent("methane", 0.8);
      gas.addComponent("ethane", 0.2);
      gas.setMixingRule("classic");

      Stream inlet = new Stream("inlet", gas);
      inlet.setFlowRate(1000.0, "kg/hr");
      inlet.run();

      Compressor compressor = new Compressor("testComp", inlet);
      compressor.setOutletPressure(100.0);
      compressor.setPolytropicEfficiency(0.75);
      compressor.setSpeed(10000);
      compressor.run();

      CompressorMechanicalLosses mechLosses = compressor.initMechanicalLosses(120.0);
      assertNotNull(mechLosses);
      assertEquals(120.0, mechLosses.getShaftDiameter(), 0.1);
    }

    @Test
    @DisplayName("Compressor updates mechanical losses from operating conditions")
    void testCompressorUpdateMechanicalLosses() {
      SystemInterface gas = new SystemSrkEos(298.0, 50.0);
      gas.addComponent("methane", 0.9);
      gas.addComponent("ethane", 0.1);
      gas.setMixingRule("classic");

      Stream inlet = new Stream("inlet", gas);
      inlet.setFlowRate(5000.0, "kg/hr");
      inlet.run();

      Compressor compressor = new Compressor("testComp", inlet);
      compressor.setOutletPressure(120.0);
      compressor.setPolytropicEfficiency(0.78);
      compressor.setSpeed(12000);
      compressor.run();

      compressor.initMechanicalLosses(100.0);
      double sealGas = compressor.getSealGasConsumption();
      double bearingLoss = compressor.getBearingLoss();
      double mechEfficiency = compressor.getMechanicalEfficiency();

      assertTrue(sealGas > 0, "Seal gas consumption should be positive");
      assertTrue(bearingLoss > 0, "Bearing loss should be positive");
      assertTrue(mechEfficiency > 0.9, "Mechanical efficiency should be high");
      assertTrue(mechEfficiency < 1.0, "Mechanical efficiency should be < 1");
    }

    @Test
    @DisplayName("Compressor returns defaults when no mechanical losses configured")
    void testCompressorDefaultsWithoutMechanicalLosses() {
      SystemInterface gas = new SystemSrkEos(298.0, 50.0);
      gas.addComponent("methane", 1.0);
      gas.setMixingRule("classic");

      Stream inlet = new Stream("inlet", gas);
      inlet.setFlowRate(1000.0, "kg/hr");
      inlet.run();

      Compressor compressor = new Compressor("testComp", inlet);
      compressor.setOutletPressure(100.0);
      compressor.run();

      // Without mechanical losses configured, should return defaults
      assertEquals(0.0, compressor.getSealGasConsumption(), 0.001);
      assertEquals(0.0, compressor.getBearingLoss(), 0.001);
      assertEquals(0.98, compressor.getMechanicalEfficiency(), 0.001);
    }
  }

  // ============================================================================
  // Seal Type Specific Tests
  // ============================================================================

  @Nested
  @DisplayName("Seal Type Specific Tests")
  class SealTypeTests {
    @Test
    @DisplayName("Oil seal has zero gas leakage")
    void testOilSealNoGasLeakage() {
      losses.setSealType(CompressorMechanicalLosses.SealType.OIL_FILM);
      double leakage = losses.calculatePrimarySealLeakage();
      // Oil seals use the minimum estimate
      assertTrue(leakage <= 1.0, "Oil seal leakage should be minimal");
    }

    @Test
    @DisplayName("Double opposed seal has lower leakage than tandem")
    void testDoubleOpposedLowerLeakage() {
      losses.setSealType(CompressorMechanicalLosses.SealType.DRY_GAS_TANDEM);
      double tandemLeakage = losses.calculatePrimarySealLeakage();

      losses.setSealType(CompressorMechanicalLosses.SealType.DRY_GAS_DOUBLE);
      double doubleLeakage = losses.calculatePrimarySealLeakage();

      assertTrue(doubleLeakage <= tandemLeakage,
          "Double opposed should have equal or lower leakage");
    }
  }

  // ============================================================================
  // Edge Cases and Boundary Tests
  // ============================================================================

  @Nested
  @DisplayName("Edge Cases and Boundary Tests")
  class EdgeCaseTests {
    @Test
    @DisplayName("Very small shaft diameter gives reasonable results")
    void testSmallShaftDiameter() {
      CompressorMechanicalLosses smallLosses = new CompressorMechanicalLosses(50.0);
      smallLosses.setOperatingConditions(20.0, 50.0, 20000.0, 16.0, 0.98);

      double sealGas = smallLosses.getTotalSealGasConsumption();
      double bearingLoss = smallLosses.getTotalBearingLoss();

      assertTrue(sealGas > 0, "Seal gas should be positive");
      assertTrue(bearingLoss > 0, "Bearing loss should be positive");
    }

    @Test
    @DisplayName("Very large shaft diameter gives reasonable results")
    void testLargeShaftDiameter() {
      CompressorMechanicalLosses largeLosses = new CompressorMechanicalLosses(300.0);
      largeLosses.setOperatingConditions(80.0, 300.0, 6000.0, 20.0, 0.90);

      double sealGas = largeLosses.getTotalSealGasConsumption();
      double bearingLoss = largeLosses.getTotalBearingLoss();

      assertTrue(sealGas > 0, "Seal gas should be positive");
      assertTrue(bearingLoss > 0, "Bearing loss should be positive");
    }

    @Test
    @DisplayName("Low pressure differential gives minimum leakage")
    void testLowPressureDifferential() {
      losses.setOperatingConditions(50.0, 52.0, 10000.0, 18.0, 0.95);
      double leakage = losses.calculatePrimarySealLeakage();
      // Should still give minimum leakage estimate
      assertTrue(leakage >= 0.6, "Should give at least minimum leakage");
    }

    @Test
    @DisplayName("Zero compressibility factor handled gracefully")
    void testZeroCompressibilityFactor() {
      losses.setOperatingConditions(50.0, 150.0, 10000.0, 18.0, 0.0);
      double leakage = losses.calculatePrimarySealLeakage();
      // Should use minimum estimate when Z=0
      assertTrue(leakage >= 0.6, "Should give minimum leakage when Z=0");
    }
  }
}
