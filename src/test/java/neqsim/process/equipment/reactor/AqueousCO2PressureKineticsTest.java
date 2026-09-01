package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;

/** Tests the published pressure response of aqueous CO2 hydration and dehydration. */
public class AqueousCO2PressureKineticsTest extends NeqSimTest {

  @Test
  void testEqualPressureHasUnitMultiplier() {
    assertEquals(1.0, AqueousCO2PressureKinetics.hydrationMultiplier(100.0, 100.0, 298.15), 0.0);
    assertEquals(1.0, AqueousCO2PressureKinetics.dehydrationMultiplier(100.0, 100.0, 298.15), 0.0);
  }

  @Test
  void testPublishedActivationVolumesGiveExpected100BaraResponse() {
    double hydration = AqueousCO2PressureKinetics.hydrationMultiplier(100.0, 1.0, 298.15);
    double dehydration = AqueousCO2PressureKinetics.dehydrationMultiplier(100.0, 1.0, 298.15);

    assertEquals(1.0403287704082815, hydration, 1.0e-15);
    assertEquals(0.9747647335180847, dehydration, 1.0e-15);
    assertTrue(hydration > 1.0);
    assertTrue(dehydration < 1.0);
    assertEquals(hydration, AqueousCO2PressureKinetics.hydrationMultiplier(100.0, 1.0, 298.15), 0.0);
  }

  @Test
  void testReportedActivationVolumeUncertaintyIsPropagated() {
    AqueousCO2PressureKinetics.MultiplierRange hydration = AqueousCO2PressureKinetics.hydrationMultiplierRange(100.0,
        1.0, 298.15);
    assertEquals(1.0403287704082815, hydration.getNominal(), 1.0e-15);
    assertEquals(1.0324647657319799, hydration.getMinimum(), 1.0e-15);
    assertEquals(1.048252673079751, hydration.getMaximum(), 1.0e-15);

    AqueousCO2PressureKinetics.MultiplierRange dehydration = AqueousCO2PressureKinetics
        .dehydrationMultiplierRange(100.0, 1.0, 298.15);
    assertEquals(0.9747647335180847, dehydration.getNominal(), 1.0e-15);
    assertEquals(0.9732088425468611, dehydration.getMinimum(), 1.0e-15);
    assertEquals(0.9763231119273673, dehydration.getMaximum(), 1.0e-15);
  }

  @Test
  void testPublishedDomainAndInvalidStatesFailClosed() {
    assertTrue(AqueousCO2PressureKinetics.hydrationMultiplier(1000.0, 1.0, 298.15) > 1.0);
    assertTrue(AqueousCO2PressureKinetics.dehydrationMultiplier(1000.0, 1.0, 298.15) < 1.0);

    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2PressureKinetics.hydrationMultiplier(0.99, 1.0, 298.15));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2PressureKinetics.hydrationMultiplier(1000.01, 1.0, 298.15));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2PressureKinetics.hydrationMultiplier(100.0, 1.0, 298.16));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2PressureKinetics.hydrationMultiplier(100.0, Double.NaN, 298.15));
  }
}
