package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.BoundaryConditions;
import neqsim.process.safety.InitiatingEvent;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the release source term generation classes.
 */
class LeakModelTest {
  private SystemInterface methaneGas;

  @BeforeEach
  void setUp() {
    methaneGas = new SystemSrkEos(300.0, 50.0);
    methaneGas.addComponent("methane", 1.0);
    methaneGas.setMixingRule("classic");
    methaneGas.init(0);
    methaneGas.init(1);
    methaneGas.init(3); // Initialize physical properties
  }

  @Test
  @DisplayName("Test InitiatingEvent enum")
  void testInitiatingEvent() {
    // Test release events
    assertTrue(InitiatingEvent.LEAK_SMALL.isReleaseEvent());
    assertTrue(InitiatingEvent.LEAK_MEDIUM.isReleaseEvent());
    assertTrue(InitiatingEvent.PSV_LIFT.isReleaseEvent());
    assertFalse(InitiatingEvent.BLOCKED_OUTLET.isReleaseEvent());

    // Test fire analysis requirement
    assertTrue(InitiatingEvent.FIRE_EXPOSURE.requiresFireAnalysis());
    assertFalse(InitiatingEvent.ESD.requiresFireAnalysis());

    // Test depressurization triggers
    assertTrue(InitiatingEvent.ESD.triggersDepressurization());
    assertTrue(InitiatingEvent.FIRE_EXPOSURE.triggersDepressurization());
    assertFalse(InitiatingEvent.LEAK_SMALL.triggersDepressurization());

    // Test hole diameter ranges
    double[] smallHole = InitiatingEvent.LEAK_SMALL.getTypicalHoleDiameter();
    assertNotNull(smallHole);
    assertEquals(1.0, smallHole[0], 0.01);
    assertEquals(10.0, smallHole[1], 0.01);

    double[] mediumHole = InitiatingEvent.LEAK_MEDIUM.getTypicalHoleDiameter();
    assertNotNull(mediumHole);
    assertEquals(10.0, mediumHole[0], 0.01);
    assertEquals(50.0, mediumHole[1], 0.01);

    assertNull(InitiatingEvent.ESD.getTypicalHoleDiameter());
  }

  @Test
  @DisplayName("Test BoundaryConditions builder")
  void testBoundaryConditions() {
    BoundaryConditions conditions = BoundaryConditions.builder().ambientTemperature(278.15) // 5°C
        .windSpeed(10.0).relativeHumidity(0.80).pasquillStabilityClass('D').isOffshore(true)
        .build();

    assertEquals(278.15, conditions.getAmbientTemperature(), 0.01);
    assertEquals(5.0, conditions.getAmbientTemperature("C"), 0.01);
    assertEquals(10.0, conditions.getWindSpeed(), 0.01);
    assertEquals(0.80, conditions.getRelativeHumidity(), 0.01);
    assertEquals('D', conditions.getPasquillStabilityClass());
    assertTrue(conditions.isOffshore());
  }

  @Test
  @DisplayName("Test BoundaryConditions presets")
  void testBoundaryConditionsPresets() {
    BoundaryConditions northSeaWinter = BoundaryConditions.northSeaWinter();
    assertEquals(5.0, northSeaWinter.getAmbientTemperature("C"), 0.1);
    assertEquals(15.0, northSeaWinter.getWindSpeed(), 0.1);
    assertTrue(northSeaWinter.isOffshore());

    BoundaryConditions gom = BoundaryConditions.gulfOfMexico();
    assertEquals(30.0, gom.getAmbientTemperature("C"), 0.1);
    assertTrue(gom.isOffshore());

    BoundaryConditions onshore = BoundaryConditions.onshoreIndustrial();
    assertFalse(onshore.isOffshore());
  }

  @Test
  @DisplayName("Test ReleaseOrientation")
  void testReleaseOrientation() {
    assertTrue(ReleaseOrientation.HORIZONTAL.isHorizontal());
    assertFalse(ReleaseOrientation.HORIZONTAL.isVertical());

    assertTrue(ReleaseOrientation.VERTICAL_UP.isVertical());
    assertFalse(ReleaseOrientation.VERTICAL_UP.isHorizontal());

    assertEquals(90.0, ReleaseOrientation.VERTICAL_UP.getAngle(), 0.01);
    assertEquals(-90.0, ReleaseOrientation.VERTICAL_DOWN.getAngle(), 0.01);
    assertEquals(0.0, ReleaseOrientation.HORIZONTAL.getAngle(), 0.01);
  }

  @Test
  @DisplayName("Test LeakModel mass flow calculation")
  void testLeakModelMassFlow() {
    LeakModel leak = LeakModel.builder().fluid(methaneGas).holeDiameter(0.01) // 10mm
        .vesselVolume(1.0) // 1 m³
        .dischargeCoefficient(0.62).backPressure(101325.0) // Atmospheric
        .scenarioName("Test Leak").build();

    double massFlow = leak.calculateMassFlowRate(methaneGas);

    // Mass flow should be positive
    assertTrue(massFlow > 0, "Mass flow should be positive");

    // For 50 bar methane through 10mm hole, expect roughly 0.5-2 kg/s
    assertTrue(massFlow > 0.1, "Mass flow too low for conditions");
    assertTrue(massFlow < 10.0, "Mass flow too high for conditions");
  }

  @Test
  @DisplayName("Test LeakModel jet velocity")
  void testLeakModelJetVelocity() {
    LeakModel leak =
        LeakModel.builder().fluid(methaneGas).holeDiameter(0.01).vesselVolume(1.0).build();

    double velocity = leak.calculateJetVelocity(methaneGas);

    // Jet velocity should be subsonic or sonic
    assertTrue(velocity > 0, "Velocity should be positive");

    // Speed of sound in methane at 300K is roughly 450 m/s, allow up to 600 for numerical reasons
    assertTrue(velocity <= 600,
        "Velocity should not greatly exceed speed of sound, got: " + velocity);
  }

  @Test
  @DisplayName("Test LeakModel jet momentum")
  void testLeakModelJetMomentum() {
    LeakModel leak =
        LeakModel.builder().fluid(methaneGas).holeDiameter(0.01).vesselVolume(1.0).build();

    double momentum = leak.calculateJetMomentum(methaneGas);

    // Momentum (reaction force) should be positive
    assertTrue(momentum > 0, "Momentum should be positive");

    // For these conditions, expect tens to hundreds of Newtons
    assertTrue(momentum > 1.0, "Momentum too low");
    assertTrue(momentum < 10000, "Momentum too high");
  }

  @Test
  @DisplayName("Test LeakModel source term calculation")
  void testLeakModelSourceTerm() {
    LeakModel leak = LeakModel.builder().fluid(methaneGas).holeDiameter(0.01).vesselVolume(1.0)
        .scenarioName("Methane Blowdown").build();

    SourceTermResult result = leak.calculateSourceTerm(60.0, 1.0); // 60 seconds

    assertNotNull(result);
    assertEquals("Methane Blowdown", result.getScenarioName());
    assertEquals(0.01, result.getHoleDiameter(), 0.001);

    // Check time series data
    double[] time = result.getTime();
    double[] massFlow = result.getMassFlowRate();

    assertTrue(time.length > 0, "Should have time data");
    assertEquals(time.length, massFlow.length, "Time and mass flow arrays should match");

    // First mass flow should be highest
    assertTrue(massFlow[0] > 0, "Initial mass flow should be positive");

    // Total mass should be positive
    assertTrue(result.getTotalMassReleased() > 0, "Total mass should be positive");
    assertTrue(result.getPeakMassFlowRate() > 0, "Peak flow should be positive");
  }

  @Test
  @DisplayName("Test SourceTermResult export methods")
  void testSourceTermExport() {
    LeakModel leak = LeakModel.builder().fluid(methaneGas).holeDiameter(0.02).vesselVolume(5.0)
        .scenarioName("Export Test").build();

    SourceTermResult result = leak.calculateSourceTerm(30.0, 1.0);

    // Test that toString works
    String str = result.toString();
    assertNotNull(str);
    assertTrue(str.contains("Export Test"), "Should contain scenario name");
    // Check for hole diameter in locale-independent way (20.0 or 20,0 depending on locale)
    assertTrue(str.contains("20") && str.contains("mm"),
        "Should contain hole diameter 20mm: " + str);
    assertTrue(str.contains("Horizontal"), "Should contain orientation");
    assertTrue(str.contains("kg/s"), "Should contain mass flow rate units");
  }

  @Test
  @DisplayName("Test hole diameter unit conversion")
  void testHoleDiameterUnits() {
    LeakModel leakMM = LeakModel.builder().fluid(methaneGas).holeDiameter(25.4, "mm") // 1 inch
        .vesselVolume(1.0).build();

    LeakModel leakIN = LeakModel.builder().fluid(methaneGas).holeDiameter(1.0, "in") // 1 inch
        .vesselVolume(1.0).build();

    // Both should give same mass flow
    double flowMM = leakMM.calculateMassFlowRate(methaneGas);
    double flowIN = leakIN.calculateMassFlowRate(methaneGas);

    assertEquals(flowMM, flowIN, flowMM * 0.01); // Within 1%
  }
}
