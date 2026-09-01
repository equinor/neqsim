package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;

/** Tests exact piecewise-isothermal propagation of the published neutral CO2 hydration pair. */
public class AqueousCO2HydrationTrajectoryTest extends NeqSimTest {

  @Test
  void testSameTemperatureSegmentsMatchOneAnalyticalStep() {
    double initialCO2 = 1000.0;
    double initialCarbonicAcid = 20.0;
    double temperatureK = 298.15;
    AqueousCO2HydrationTrajectory.TrajectoryResult trajectory = AqueousCO2HydrationTrajectory.advance(initialCO2,
        initialCarbonicAcid, new double[] { 0.01, 0.02 }, new double[] { temperatureK, temperatureK });
    AqueousCO2HydrationKinetics.Result direct = AqueousCO2HydrationKinetics.advance(initialCO2, initialCarbonicAcid,
        0.03, temperatureK);

    assertEquals(direct.getCO2Concentration(), trajectory.getCO2Concentration(), 1.0e-12);
    assertEquals(direct.getCarbonicAcidConcentration(), trajectory.getCarbonicAcidConcentration(), 1.0e-12);
    assertEquals(1020.0, trajectory.getTotalMolecularCO2Concentration(), 1.0e-12);
    assertEquals(0.0, trajectory.getCarbonBalanceResidual(), 1.0e-12);
    assertEquals(0.03, trajectory.getElapsedTimeSeconds(), 0.0);
    assertEquals(2, trajectory.getSegmentCount());
    assertEquals(temperatureK, trajectory.getMinimumTemperatureK(), 0.0);
    assertEquals(temperatureK, trajectory.getMaximumTemperatureK(), 0.0);
    assertEquals(
        (AqueousCO2HydrationKinetics.hydrationRateConstant(temperatureK)
            + AqueousCO2HydrationKinetics.dehydrationRateConstant(temperatureK)) * 0.03,
        trajectory.getCumulativeRelaxationExposure(), 1.0e-15);
  }

  @Test
  void testChangingTemperatureMatchesOrderedExactUpdates() {
    double initialCO2 = 1000.0;
    double initialCarbonicAcid = 0.0;
    double[] durations = { 0.04, 0.02 };
    double[] temperatures = { 288.15, 305.65 };

    AqueousCO2HydrationKinetics.Result first = AqueousCO2HydrationKinetics.advance(initialCO2, initialCarbonicAcid,
        durations[0], temperatures[0]);
    AqueousCO2HydrationKinetics.Result second = AqueousCO2HydrationKinetics.advance(first.getCO2Concentration(),
        first.getCarbonicAcidConcentration(), durations[1], temperatures[1]);
    AqueousCO2HydrationTrajectory.TrajectoryResult trajectory = AqueousCO2HydrationTrajectory.advance(initialCO2,
        initialCarbonicAcid, durations, temperatures);

    assertEquals(second.getCO2Concentration(), trajectory.getCO2Concentration(), 0.0);
    assertEquals(second.getCarbonicAcidConcentration(), trajectory.getCarbonicAcidConcentration(), 0.0);
    assertEquals(288.15, trajectory.getMinimumTemperatureK(), 0.0);
    assertEquals(305.65, trajectory.getMaximumTemperatureK(), 0.0);
    assertTrue(trajectory.getCO2Concentration() >= 0.0);
    assertTrue(trajectory.getCarbonicAcidConcentration() >= 0.0);

    AqueousCO2HydrationTrajectory.TrajectoryResult reversed = AqueousCO2HydrationTrajectory.advance(initialCO2,
        initialCarbonicAcid, new double[] { 0.02, 0.04 }, new double[] { 305.65, 288.15 });
    assertNotEquals(trajectory.getCarbonicAcidConcentration(), reversed.getCarbonicAcidConcentration(), 1.0e-9,
        "Changing equilibrium targets make the ordered trajectory physically significant");

    AqueousCO2HydrationTrajectory.TrajectoryResult repeated = AqueousCO2HydrationTrajectory.advance(initialCO2,
        initialCarbonicAcid, durations, temperatures);
    assertEquals(trajectory.getCO2Concentration(), repeated.getCO2Concentration(), 0.0);
    assertEquals(trajectory.getCarbonicAcidConcentration(), repeated.getCarbonicAcidConcentration(), 0.0);
  }

  @Test
  void testZeroDurationSegmentIsAQualifiedNoOp() {
    AqueousCO2HydrationTrajectory.TrajectoryResult result = AqueousCO2HydrationTrajectory.advance(3.0, 2.0,
        new double[] { 0.0 }, new double[] { 298.15 });

    assertEquals(3.0, result.getCO2Concentration(), 0.0);
    assertEquals(2.0, result.getCarbonicAcidConcentration(), 0.0);
    assertEquals(0.0, result.getElapsedTimeSeconds(), 0.0);
    assertEquals(0.0, result.getCumulativeRelaxationExposure(), 0.0);
    assertEquals(0.0, result.getCarbonBalanceResidual(), 0.0);
  }

  @Test
  void testInvalidTrajectoryFailsClosed() {
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationTrajectory.advance(-1.0, 0.0, new double[] { 1.0 }, new double[] { 298.15 }));
    assertThrows(IllegalArgumentException.class, () -> AqueousCO2HydrationTrajectory.advance(Double.MAX_VALUE,
        Double.MAX_VALUE, new double[] { 1.0 }, new double[] { 298.15 }));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationTrajectory.advance(1.0, 0.0, null, new double[] { 298.15 }));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationTrajectory.advance(1.0, 0.0, new double[] { 1.0 }, null));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationTrajectory.advance(1.0, 0.0, new double[0], new double[0]));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationTrajectory.advance(1.0, 0.0, new double[] { 1.0 }, new double[] { 298.15, 299.15 }));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationTrajectory.advance(1.0, 0.0, new double[] { -1.0 }, new double[] { 298.15 }));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationTrajectory.advance(1.0, 0.0, new double[] { Double.NaN }, new double[] { 298.15 }));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousCO2HydrationTrajectory.advance(1.0, 0.0, new double[] { 1.0 }, new double[] { 305.66 }));
    assertThrows(IllegalArgumentException.class, () -> AqueousCO2HydrationTrajectory.advance(1.0, 0.0,
        new double[] { Double.MAX_VALUE, Double.MAX_VALUE }, new double[] { 298.15, 298.15 }));
  }
}
