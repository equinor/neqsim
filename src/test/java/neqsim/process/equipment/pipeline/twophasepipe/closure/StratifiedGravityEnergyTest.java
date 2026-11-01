package neqsim.process.equipment.pipeline.twophasepipe.closure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ObjectStreamClass;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator.StratifiedPressureMoments;

/**
 * Analytical and independently retained cross-sectional gravity energy controls.
 *
 * @author NeqSim
 * @version 1.0
 */
class StratifiedGravityEnergyTest {
  private final GeometryCalculator geometry = new GeometryCalculator();

  /** Closed circular-segment first moments fix signs, units, phase limits and scaling. */
  @Test
  void closedFormCircularSegmentAndDensityGravityLimits() {
    for (double diameter : new double[] {0.001, 0.2, 1.0148, 3.0, 10.0}) {
      for (double fraction : new double[] {0.0, 1e-14, 1e-8, 0.1, 0.5, 0.9, 1.0 - 1e-14, 1.0}) {
        double depth = fraction * diameter;
        StratifiedPressureMoments value = geometry.calculateStratifiedPressureMoments(depth, diameter);
        // Use the exact supplied double depth: dividing it by D and subtracting from one loses thin gas caps.
        double minorityDepth = Math.min(depth, diameter - depth);
        double expected = -2.0 / 3.0 * Math.pow(minorityDepth * (diameter - minorityDepth), 1.5);
        assertEquals(expected, value.getLiquidFirstMomentAboutCentre(), 2.0e-13 * Math.abs(expected));
        assertEquals(0.0, value.getLiquidFirstMomentAboutCentre() + value.getGasFirstMomentAboutCentre(), 0.0);
        assertEquals(790.0 * 9.81 * expected, value.getGravityPotentialEnergyPerLength(10.0, 800.0, 9.81),
            3.0e-13 * Math.abs(790.0 * 9.81 * expected));
        assertEquals(0.0, value.getGravityPotentialEnergyPerLength(800.0, 800.0, 9.81), 0.0);
        assertEquals(0.0, value.getGravityPotentialEnergyPerLength(10.0, 800.0, 0.0), 0.0);
      }
    }
  }

  /**
   * Replay 162 independent 80-digit segment integrals, including both traces down to 1e-30.
   *
   * @throws Exception if the reference matrix cannot be read
   */
  @Test
  void independentReferenceMatrix() throws Exception {
    String resource = "/neqsim/process/equipment/pipeline/hydrostatic-pressure-moments/gravity-energy/reference.csv";
    int rows = 0;
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(getClass().getResourceAsStream(resource), StandardCharsets.UTF_8))) {
      assertEquals("diameter_m,gas_holdup,liquid_holdup,liquid_first_m3,gas_first_m3,energy_J_m", reader.readLine());
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = line.split(",");
        StratifiedPressureMoments value = geometry.calculateStratifiedPressureMomentsFromHoldups(
            Double.parseDouble(fields[1]), Double.parseDouble(fields[2]), Double.parseDouble(fields[0]));
        double[] actual = {value.getLiquidFirstMomentAboutCentre(), value.getGasFirstMomentAboutCentre(),
            value.getGravityPotentialEnergyPerLength(10.0, 800.0, 9.81)};
        for (int column = 0; column < actual.length; column++) {
          double expected = Double.parseDouble(fields[column + 3]);
          assertEquals(expected, actual[column], 5e-13 * Math.abs(expected), line);
        }
        rows++;
      }
    }
    assertEquals(162, rows);
  }

  /** Density validation follows exact phase presence, with explicit overflow rejection. */
  @Test
  void absentPhaseAndInvalidInputs() {
    assertEquals(0.0, geometry.calculateStratifiedPressureMoments(0.0, 1.0).getGravityPotentialEnergyPerLength(10.0,
        Double.NaN, 9.81), 0.0);
    assertEquals(0.0, geometry.calculateStratifiedPressureMoments(1.0, 1.0)
        .getGravityPotentialEnergyPerLength(Double.NaN, 800.0, 9.81), 0.0);
    StratifiedPressureMoments value = geometry.calculateStratifiedPressureMoments(0.5, 1.0);
    for (double invalid : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> value.getGravityPotentialEnergyPerLength(invalid, 800, 9.81));
      assertThrows(IllegalArgumentException.class, () -> value.getGravityPotentialEnergyPerLength(10, invalid, 9.81));
    }
    assertThrows(IllegalArgumentException.class, () -> value.getGravityPotentialEnergyPerLength(10, 800, Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> value.getGravityPotentialEnergyPerLength(1, Double.MAX_VALUE, Double.MAX_VALUE));
  }

  /** Derived queries retain the four-field serialized contract and do not mutate its bytes. */
  @Test
  void serializationAndQueryPurity() {
    StratifiedPressureMoments value = geometry.calculateStratifiedPressureMoments(0.3, 1.0148);
    byte[] before = SerializationUtils.serialize(value);
    double energy = value.getGravityPotentialEnergyPerLength(10, 800, 9.81);
    assertTrue(energy < 0.0);
    assertTrue(java.util.Arrays.equals(before, SerializationUtils.serialize(value)));
    StratifiedPressureMoments restored = SerializationUtils.deserialize(before);
    assertEquals(energy, restored.getGravityPotentialEnergyPerLength(10, 800, 9.81), 0.0);
    assertEquals(4, ObjectStreamClass.lookup(StratifiedPressureMoments.class).getFields().length);
    assertEquals(1000L, ObjectStreamClass.lookup(StratifiedPressureMoments.class).getSerialVersionUID());
  }

  /** The height derivative follows the independently known circular chord and central ordinate. */
  @Test
  void energyHeightDerivativeMatchesBoundaryMoment() {
    double diameter = 1.0148;
    double epsilon = 1.0e-5 * diameter;
    for (double fraction : new double[] {0.1, 0.25, 0.75, 0.9}) {
      double height = fraction * diameter;
      double left = geometry.calculateStratifiedPressureMoments(height - epsilon, diameter)
          .getGravityPotentialEnergyPerLength(10, 800, 9.81);
      double right = geometry.calculateStratifiedPressureMoments(height + epsilon, diameter)
          .getGravityPotentialEnergyPerLength(10, 800, 9.81);
      double chord = 2.0 * Math.sqrt(height * (diameter - height));
      double expected = 790.0 * 9.81 * (height - diameter / 2.0) * chord;
      assertEquals(expected, (right - left) / (2.0 * epsilon), 1.0e-7 * Math.abs(expected));
    }
  }

  /** Two static equal-inventory configurations have a finite potential difference, not an elapsed-time prediction. */
  @Test
  void closedTwoCellPotentialBudgetRetainsTheLiquidVolume() {
    StratifiedPressureMoments wet = geometry.calculateStratifiedPressureMomentsFromHoldups(0.4, 0.6, 1.0148);
    StratifiedPressureMoments redistributed = geometry.calculateStratifiedPressureMomentsFromHoldups(0.7, 0.3, 1.0148);
    assertEquals(10 * wet.getLiquidArea(), 20 * redistributed.getLiquidArea(), 1.0e-12);
    double initial = 10 * wet.getGravityPotentialEnergyPerLength(10, 800, 9.81);
    double after = 20 * redistributed.getGravityPotentialEnergyPerLength(10, 800, 9.81);
    assertTrue(initial - after > 0.0);
    assertEquals(0.0, 10 * wet.getGravityPotentialEnergyPerLength(800, 800, 9.81)
        - 20 * redistributed.getGravityPotentialEnergyPerLength(800, 800, 9.81), 0.0);
  }
}
