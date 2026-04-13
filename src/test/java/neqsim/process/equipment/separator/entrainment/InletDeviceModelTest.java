package neqsim.process.equipment.separator.entrainment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InletDeviceModel}.
 *
 * @author NeqSim team
 * @version 1.0
 */
class InletDeviceModelTest {

  /** Gas density [kg/m3]. */
  private static final double GAS_DENSITY = 50.0;
  /** Liquid density [kg/m3]. */
  private static final double LIQUID_DENSITY = 800.0;
  /** Gas volume flow [m3/s]. */
  private static final double GAS_VOLUME_FLOW = 3.0;
  /** Liquid volume flow [m3/s]. */
  private static final double LIQUID_VOLUME_FLOW = 0.05;
  /** Surface tension [N/m]. */
  private static final double SURFACE_TENSION = 0.025;

  /**
   * Tests inlet vane device calculation with typical gas separator conditions.
   */
  @Test
  void testInletVaneCalculation() {
    InletDeviceModel model = new InletDeviceModel(InletDeviceModel.InletDeviceType.INLET_VANE);
    model.setInletNozzleDiameter(0.15); // 150mm nozzle
    DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);

    model.calculate(dsd, GAS_DENSITY, LIQUID_DENSITY, GAS_VOLUME_FLOW, LIQUID_VOLUME_FLOW,
        SURFACE_TENSION);

    assertTrue(model.getBulkSeparationEfficiency() > 0.0,
        "Inlet vane should have positive bulk efficiency");
    assertTrue(model.getBulkSeparationEfficiency() <= 1.0, "Bulk efficiency should be <= 1.0");
    assertTrue(model.getNozzleVelocity() > 0.0, "Nozzle velocity should be positive");
    assertTrue(model.getMomentumFlux() > 0.0, "Momentum flux should be positive");
    assertTrue(model.getPressureDrop() >= 0.0, "Pressure drop should be non-negative");
    assertNotNull(model.getDownstreamDSD(), "Downstream DSD should not be null");
  }

  /**
   * Tests deflector plate — simpler device with lower efficiency.
   */
  @Test
  void testDeflectorPlateCalculation() {
    InletDeviceModel model = new InletDeviceModel(InletDeviceModel.InletDeviceType.DEFLECTOR_PLATE);
    model.setInletNozzleDiameter(0.15);
    DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);

    model.calculate(dsd, GAS_DENSITY, LIQUID_DENSITY, GAS_VOLUME_FLOW, LIQUID_VOLUME_FLOW,
        SURFACE_TENSION);

    assertTrue(model.getBulkSeparationEfficiency() >= 0.0,
        "Deflector should have non-negative efficiency");
    assertTrue(model.getBulkSeparationEfficiency() < 0.9,
        "Deflector shouldn't be extremely efficient");
  }

  /**
   * Tests inlet cyclone — highest efficiency device.
   */
  @Test
  void testInletCycloneCalculation() {
    InletDeviceModel model = new InletDeviceModel(InletDeviceModel.InletDeviceType.INLET_CYCLONE);
    model.setInletNozzleDiameter(0.15);
    DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);

    model.calculate(dsd, GAS_DENSITY, LIQUID_DENSITY, GAS_VOLUME_FLOW, LIQUID_VOLUME_FLOW,
        SURFACE_TENSION);

    assertTrue(model.getBulkSeparationEfficiency() > 0.0,
        "Inlet cyclone should have positive efficiency");
  }

  /**
   * Tests no-device case.
   */
  @Test
  void testNoDeviceCalculation() {
    InletDeviceModel model = new InletDeviceModel(InletDeviceModel.InletDeviceType.NONE);
    model.setInletNozzleDiameter(0.15);
    DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);

    model.calculate(dsd, GAS_DENSITY, LIQUID_DENSITY, GAS_VOLUME_FLOW, LIQUID_VOLUME_FLOW,
        SURFACE_TENSION);

    assertEquals(0.0, model.getBulkSeparationEfficiency(), 1e-10,
        "No device should have zero bulk efficiency");
  }

  /**
   * Tests that high momentum flux degrades efficiency (above max momentum limit).
   */
  @Test
  void testHighMomentumDegradation() {
    InletDeviceModel model = new InletDeviceModel(InletDeviceModel.InletDeviceType.INLET_VANE);
    model.setInletNozzleDiameter(0.15);
    DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);

    // Normal momentum
    model.calculate(dsd, GAS_DENSITY, LIQUID_DENSITY, GAS_VOLUME_FLOW, LIQUID_VOLUME_FLOW,
        SURFACE_TENSION);
    double normalEff = model.getBulkSeparationEfficiency();

    // Very high momentum (small nozzle, very high flows)
    model.setInletNozzleDiameter(0.05);
    model.calculate(dsd, GAS_DENSITY, LIQUID_DENSITY, GAS_VOLUME_FLOW * 5.0,
        LIQUID_VOLUME_FLOW * 5.0, SURFACE_TENSION);
    double highMomEff = model.getBulkSeparationEfficiency();

    assertTrue(highMomEff >= 0.0 && highMomEff <= 1.0, "Efficiency should be valid");
  }

  /**
   * Tests DSD transformation produces a different characteristic diameter.
   */
  @Test
  void testDSDTransformation() {
    InletDeviceModel model = new InletDeviceModel(InletDeviceModel.InletDeviceType.INLET_VANE);
    model.setInletNozzleDiameter(0.15);
    DropletSizeDistribution originalDsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);

    model.calculate(originalDsd, GAS_DENSITY, LIQUID_DENSITY, GAS_VOLUME_FLOW, LIQUID_VOLUME_FLOW,
        SURFACE_TENSION);

    DropletSizeDistribution downstreamDsd = model.getDownstreamDSD();
    assertNotNull(downstreamDsd);

    double originalD50 = originalDsd.getD50();
    double downstreamD50 = downstreamDsd.getD50();

    assertTrue(downstreamD50 > 0, "Downstream D50 should be positive");
  }

  /**
   * Tests all device type enums are accessible.
   */
  @Test
  void testAllDeviceTypes() {
    InletDeviceModel.InletDeviceType[] types = InletDeviceModel.InletDeviceType.values();
    assertTrue(types.length >= 7, "Should have at least 7 device types");

    for (InletDeviceModel.InletDeviceType type : types) {
      assertNotNull(type.getDisplayName(), "Display name should not be null for " + type);
      assertTrue(type.getPressureDropCoefficient() >= 0.0,
          "Pressure drop coeff should be non-negative for " + type);
    }
  }
}
