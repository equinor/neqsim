package neqsim.process.mechanicaldesign.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.GasScrubber;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the internals elevation API on {@link SeparatorMechanicalDesign} and the derived
 * cyclone-top elevation and drainage-head check on {@link GasScrubberMechanicalDesign}.
 *
 * <p>
 * All elevations are metres from the vessel Bottom Tangent Line (BTL), positive upward.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class SeparatorInternalsElevationTest {

  /** Absolute tolerance for elevation comparisons [m]. */
  private static final double TOL = 1.0e-9;

  /**
   * Builds a wet gas stream for scrubber tests.
   *
   * @return a stream carrying a run two-phase gas/water fluid
   */
  private Stream createWetGasStream() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 60.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.04);
    fluid.addComponent("water", 0.01);
    fluid.setMixingRule("classic");
    Stream stream = new Stream("feed", fluid);
    stream.setFlowRate(50000.0, "kg/hr");
    stream.setTemperature(30.0, "C");
    stream.setPressure(60.0, "bara");
    stream.run();
    return stream;
  }

  /**
   * Builds a separator mechanical design bound to a running separator.
   *
   * @return an initialised mechanical design
   */
  private SeparatorMechanicalDesign createDesign() {
    Separator sep = new Separator("sep", createWetGasStream());
    sep.run();
    sep.initMechanicalDesign();
    return (SeparatorMechanicalDesign) sep.getMechanicalDesign();
  }

  /** Elevations round-trip through their accessors and default to zero when unset. */
  @Test
  public void testElevationAccessorsRoundTrip() {
    SeparatorMechanicalDesign design = createDesign();

    assertEquals(0.0, design.getInletNozzleElevation(), TOL);
    assertEquals(0.0, design.getInletDeviceTopElevation(), TOL);
    assertEquals(0.0, design.getMeshPadBottomElevation(), TOL);

    design.setInletNozzleElevation(1.20);
    design.setInletDeviceTopElevation(1.35);
    design.setMeshPadBottomElevation(2.25);

    assertEquals(1.20, design.getInletNozzleElevation(), TOL);
    assertEquals(1.35, design.getInletDeviceTopElevation(), TOL);
    assertEquals(2.25, design.getMeshPadBottomElevation(), TOL);
  }

  /**
   * With no explicit inlet device top, the default places it half a nozzle bore above the nozzle
   * centreline.
   */
  @Test
  public void testInletDeviceTopElevationFallsBackToHalfNozzleBore() {
    SeparatorMechanicalDesign design = createDesign();
    design.setInletNozzleElevation(1.20);
    design.setInletNozzleID(0.30);

    assertEquals(1.35, design.getInletDeviceTopElevationOrDefault(), TOL);

    design.setInletDeviceTopElevation(1.50);
    assertEquals(1.50, design.getInletDeviceTopElevationOrDefault(), TOL);
  }

  /** The default is only usable when both nozzle elevation and bore are set. */
  @Test
  public void testInletDeviceTopElevationDefaultRequiresBothInputs() {
    SeparatorMechanicalDesign design = createDesign();

    design.setInletNozzleID(0.30);
    assertEquals(0.0, design.getInletDeviceTopElevationOrDefault(), TOL);

    design.setInletNozzleElevation(1.20);
    design.setInletNozzleID(0.0);
    assertEquals(0.0, design.getInletDeviceTopElevationOrDefault(), TOL);
  }

  /** Mesh pad top is the bottom face plus the pad thickness, which is stored in mm. */
  @Test
  public void testMeshPadTopElevationAddsThicknessConvertedFromMillimetres() {
    SeparatorMechanicalDesign design = createDesign();

    assertEquals(0.0, design.getMeshPadTopElevation(), TOL);

    design.setMeshPadBottomElevation(2.25);
    design.setDemisterThickness(150.0);

    assertEquals(2.40, design.getMeshPadTopElevation(), 1.0e-9);
  }

  /** Free path is mesh pad bottom minus inlet device top. */
  @Test
  public void testFreePathHeightAboveInletDevice() {
    SeparatorMechanicalDesign design = createDesign();
    design.setInletDeviceTopElevation(1.35);
    design.setMeshPadBottomElevation(2.25);

    assertEquals(0.90, design.getFreePathHeightAboveInletDevice(), 1.0e-9);
  }

  /** An unset or inverted geometry yields zero free path rather than a negative distance. */
  @Test
  public void testFreePathHeightGuardsUnsetAndInvertedGeometry() {
    SeparatorMechanicalDesign design = createDesign();
    assertEquals(0.0, design.getFreePathHeightAboveInletDevice(), TOL);

    design.setInletDeviceTopElevation(1.35);
    assertEquals(0.0, design.getFreePathHeightAboveInletDevice(), TOL);

    // Mesh pad below the inlet device is not a negative free path.
    design.setMeshPadBottomElevation(1.00);
    assertEquals(0.0, design.getFreePathHeightAboveInletDevice(), TOL);
  }

  /**
   * Builds a gas scrubber mechanical design bound to a running scrubber.
   *
   * @return an initialised scrubber mechanical design
   */
  private GasScrubberMechanicalDesign createScrubberDesign() {
    GasScrubber scrubber = new GasScrubber("scrubber", createWetGasStream());
    scrubber.run();
    scrubber.initMechanicalDesign();
    return scrubber.getMechanicalDesign();
  }

  /** Cyclone top is the deck elevation plus the tube length. */
  @Test
  public void testCycloneTopElevation() {
    GasScrubberMechanicalDesign design = createScrubberDesign();

    assertEquals(0.0, design.getCycloneTopElevation(), TOL);

    design.setDemistingCyclones(12, 0.06, 2.50, 0.80);

    assertEquals(3.30, design.getCycloneTopElevation(), 1.0e-9);
  }

  /** Cyclone top needs both deck elevation and tube length. */
  @Test
  public void testCycloneTopElevationRequiresDeckAndLength() {
    GasScrubberMechanicalDesign design = createScrubberDesign();

    // Three-arg overload sets the deck but leaves the tube length unset.
    design.setDemistingCyclones(12, 0.06, 2.50);
    assertEquals(0.0, design.getCycloneTopElevation(), TOL);

    design.setCycloneDeckElevationM(0.0);
    design.setCycloneLengthM(0.80);
    assertEquals(0.0, design.getCycloneTopElevation(), TOL);
  }

  /**
   * The drainage head result is self-consistent: total dP is the sum of its parts, the available
   * head follows the deck-to-LA(HH) elevation, and the percentage is required over available.
   */
  @Test
  public void testDrainageHeadIsSelfConsistent() {
    GasScrubberMechanicalDesign design = createScrubberDesign();
    design.setMeshPad(1.20, 150.0);
    design.setDemistingCyclones(12, 0.06, 2.50, 0.80);
    design.setLaHHElevationM(1.30);

    DrainageHeadResult result = design.computeDrainageHead();

    assertEquals(result.getMeshDpPa() + result.getCycloneDpToDrainPa(), result.getTotalDpPa(),
        1.0e-9);
    assertEquals(1200.0, result.getAvailableHeadMm(), 1.0e-6);
    assertTrue(result.getRequiredHeadMm() >= 0.0, "required head must not be negative");
    assertEquals(result.getRequiredHeadMm() / result.getAvailableHeadMm() * 100.0,
        result.getPercentOfAvailable(), 1.0e-9);
    assertTrue(result.getGasDensityKgM3() > 0.0, "gas density must be positive");
    assertTrue(result.getLiquidDensityKgM3() > result.getGasDensityKgM3(),
        "liquid must be denser than gas");
  }
}
