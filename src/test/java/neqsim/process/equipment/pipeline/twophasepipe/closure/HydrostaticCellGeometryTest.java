package neqsim.process.equipment.pipeline.twophasepipe.closure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator.HydrostaticCellGeometry;

/**
 * Independent static geometry limits, not transient pool qualification.
 *
 * @author NeqSim
 * @version 1.0
 */
class HydrostaticCellGeometryTest {
  /** Calculator under test. */
  private final GeometryCalculator calculator = new GeometryCalculator();

  /** Exact absence and fullness do not create a film or interface. */
  @Test
  void exactEndpoints() {
    for (double angle : new double[] {-0.2, 0.0, 0.2}) {
      HydrostaticCellGeometry empty = calculator.calculateHydrostaticCell(0.0, 1.2, 30.0, angle);
      HydrostaticCellGeometry full = calculator.calculateHydrostaticCell(1.0, 1.2, 30.0, angle);
      assertEquals(0.0, empty.getMaximumDepth());
      assertEquals(0.0, empty.getLiquidVolume());
      assertEquals(0.0, empty.getInterfaceArea());
      assertEquals(0.0, empty.getLiquidWallArea());
      assertEquals(Math.PI * 1.2 * 1.2 / 4.0, empty.getMinimumGasArea());
      assertEquals(1.2, full.getMinimumDepth());
      assertEquals(0.0, full.getMinimumGasArea());
      assertEquals(0.0, full.getInterfaceArea());
      assertEquals(Math.PI * 1.2 * 30.0, full.getLiquidWallArea());
    }
  }

  /**
   * Replay all public independent terrain references using the frozen geometry tolerances.
   *
   * @throws Exception if the qualification resource cannot be read
   */
  @Test
  void publicTerrainReferences() throws Exception {
    int rows = 0;
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(getClass().getResourceAsStream("/neqsim/process/pipeline/hydrostatic-cell/reference.csv"),
            StandardCharsets.UTF_8))) {
      reader.readLine();
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = line.split(",");
        double holdup = Double.parseDouble(fields[2]);
        double diameter = Double.parseDouble(fields[3]);
        double length = Double.parseDouble(fields[4]);
        HydrostaticCellGeometry geometry = calculator.calculateHydrostaticCell(holdup, diameter, length,
            Double.parseDouble(fields[5]));
        assertEquals(Double.parseDouble(fields[6]), geometry.getStartDepth(), 1e-8, line);
        assertEquals(Double.parseDouble(fields[7]), geometry.getEndDepth(), 1e-8, line);
        double[] actual = {geometry.getInterfaceArea(), geometry.getLiquidWallArea(), geometry.getMinimumGasArea()};
        for (int index = 0; index < actual.length; index++) {
          double expected = Double.parseDouble(fields[index + 8]);
          assertEquals(expected, actual[index], Math.max(1e-9, 1e-7 * Math.abs(expected)), line);
        }
        double volume = holdup * Math.PI * diameter * diameter * length / 4.0;
        assertEquals(volume, geometry.getLiquidVolume(), Math.max(1e-9, 1e-9 * volume), line);
        rows++;
      }
    }
    assertEquals(3060, rows);
  }

  /** Horizontal analytical half-circle and positive trace volumes across dimensions. */
  @Test
  void horizontalAndTrace() {
    for (double diameter : new double[] {0.01, 1.0148, 10.0}) {
      HydrostaticCellGeometry half = calculator.calculateHydrostaticCell(0.5, diameter, 20.0, 0.0);
      assertEquals(diameter / 2.0, half.getMaximumDepth(), 1e-13 * diameter);
      assertEquals(diameter * 20.0, half.getInterfaceArea(), 1e-12 * diameter * 20.0);
      assertEquals(Math.PI * diameter * 10.0, half.getLiquidWallArea(), 1e-12 * diameter * 20.0);
      for (double fraction : new double[] {1e-20, 1e-12, 0.01, 0.9, 1.0 - 1e-12}) {
        HydrostaticCellGeometry g = calculator.calculateHydrostaticCell(fraction, diameter, 20.0, 0.0);
        assertTrue(g.getMinimumDepth() > 0.0);
        assertTrue(g.getMaximumDepth() < diameter);
        assertEquals(fraction * Math.PI * diameter * diameter * 5.0, g.getLiquidVolume(),
            fraction * diameter * diameter * 1e-8);
        assertTrue(Math.abs(g.getRelativeVolumeResidual()) < 1e-9);
      }
    }
  }

  /** Separate adaptive axial quadrature determines input volume and reference areas. */
  @Test
  void independentInclinedPlanes() {
    double diameter = 1.0148;
    for (double length : new double[] {2.0, 25.0, 400.0}) {
      for (double angle : new double[] {-0.15, -0.005, 0.005, 0.15}) {
        for (double centre : new double[] {0.07, 0.5, 0.93}) {
          double range = Math.abs(Math.tan(angle)) * length / (2.0 * diameter);
          double fraction = average(centre, range, 0) / (Math.PI / 4.0);
          HydrostaticCellGeometry g = calculator.calculateHydrostaticCell(fraction, diameter, length, angle);
          assertEquals(Math.min(1.0, centre + range) * diameter, g.getMaximumDepth(), 1e-8);
          assertEquals(Math.max(0.0, centre - range) * diameter, g.getMinimumDepth(), 1e-8);
          double area = average(centre, range, 1) * diameter * length / Math.cos(angle);
          assertEquals(area, g.getInterfaceArea(), Math.max(1e-9, area * 1e-7));
          double wall = average(centre, range, 2) * diameter * length;
          assertEquals(wall, g.getLiquidWallArea(), Math.max(1e-9, wall * 1e-7));
          assertEquals(fraction * Math.PI * diameter * diameter * length / 4.0, g.getLiquidVolume(), 1e-9);
        }
      }
    }
  }

  /** Near-horizontal cells converge smoothly without cancellation of angular endpoints. */
  @Test
  void nearHorizontalLimit() {
    for (double angle : new double[] {-1e-16, 1e-16, 1e-12, 1e-9}) {
      HydrostaticCellGeometry flat = calculator.calculateHydrostaticCell(0.2, 1.0, 10.0, 0.0);
      HydrostaticCellGeometry tilted = calculator.calculateHydrostaticCell(0.2, 1.0, 10.0, angle);
      assertEquals(flat.getLiquidVolume(), tilted.getLiquidVolume(), 1e-12);
      assertEquals(flat.getInterfaceArea(), tilted.getInterfaceArea(), 1e-10);
      assertEquals(flat.getLiquidWallArea(), tilted.getLiquidWallArea(), 1e-10);
      assertEquals(10.0 * Math.tan(angle), tilted.getStartDepth() - tilted.getEndDepth(), 1e-15);
    }
  }

  /** Orientation changes endpoints but preserves extensive metrics. */
  @Test
  void reversedSlope() {
    HydrostaticCellGeometry up = calculator.calculateHydrostaticCell(0.003, 1.0, 100.0, 0.03);
    HydrostaticCellGeometry down = calculator.calculateHydrostaticCell(0.003, 1.0, 100.0, -0.03);
    assertEquals(up.getStartDepth(), down.getEndDepth());
    assertEquals(up.getEndDepth(), down.getStartDepth());
    assertEquals(up.getInterfaceArea(), down.getInterfaceArea());
    assertEquals(up.getLiquidWallArea(), down.getLiquidWallArea());
    assertEquals(up.getMinimumGasArea(), down.getMinimumGasArea());
    assertEquals(0.0, up.getEndDepth());
  }

  /** Splitting the same plane preserves volume and physical areas. */
  @Test
  void partition() {
    double range = Math.tan(0.04) * 10.0;
    HydrostaticCellGeometry all = calculator.calculateHydrostaticCell(average(0.6, range, 0) * 4 / Math.PI, 1, 20,
        0.04);
    HydrostaticCellGeometry left = calculator
        .calculateHydrostaticCell(average(0.6 + range / 2, range / 2, 0) * 4 / Math.PI, 1, 10, 0.04);
    HydrostaticCellGeometry right = calculator
        .calculateHydrostaticCell(average(0.6 - range / 2, range / 2, 0) * 4 / Math.PI, 1, 10, 0.04);
    assertEquals(all.getLiquidVolume(), left.getLiquidVolume() + right.getLiquidVolume(), 1e-9);
    assertEquals(all.getInterfaceArea(), left.getInterfaceArea() + right.getInterfaceArea(), 1e-8);
    assertEquals(all.getLiquidWallArea(), left.getLiquidWallArea() + right.getLiquidWallArea(), 1e-8);
    assertEquals(left.getEndDepth(), right.getStartDepth(), 1e-8);
  }

  /** Query leaves conservative and ordinary closure geometry untouched. */
  @Test
  void readOnlySection() {
    TwoFluidSection section = new TwoFluidSection(0, 100, 1, 0.03);
    section.setGasHoldup(0.99);
    section.setLiquidHoldup(0.01);
    section.updateConservativeVariables();
    section.updateStratifiedGeometry();
    double[] before = section.getStateVector();
    double level = section.getStratifiedLiquidLevel();
    double width = section.getInterfacialWidth();
    HydrostaticCellGeometry g = section.getHydrostaticCellGeometry();
    assertEquals(g.getMaximumDepth(), section.getHydrostaticCellGeometry().getMaximumDepth());
    assertArrayEquals(before, section.getStateVector(), 0.0);
    assertEquals(level, section.getStratifiedLiquidLevel());
    assertEquals(width, section.getInterfacialWidth());
    assertEquals(0.01, section.getLiquidHoldup());
  }

  /**
   * Immutable results survive serialization.
   *
   * @throws Exception if serialization fails
   */
  @Test
  void serialization() throws Exception {
    HydrostaticCellGeometry g = calculator.calculateHydrostaticCell(0.7, 1, 5, 0.1);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(g);
    }
    HydrostaticCellGeometry copy;
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      copy = (HydrostaticCellGeometry) in.readObject();
    }
    assertEquals(g.getStartDepth(), copy.getStartDepth());
    assertEquals(g.getEndDepth(), copy.getEndDepth());
    assertEquals(g.getLiquidVolume(), copy.getLiquidVolume());
    assertEquals(g.getInterfaceArea(), copy.getInterfaceArea());
    assertEquals(g.getLiquidWallArea(), copy.getLiquidWallArea());
    assertEquals(g.getMinimumGasArea(), copy.getMinimumGasArea());
    assertEquals(g.getRelativeVolumeResidual(), copy.getRelativeVolumeResidual());
  }

  /** Invalid and unresolvable inputs fail explicitly. */
  @Test
  void invalid() {
    for (double a : new double[] {-1e-10, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> calculator.calculateHydrostaticCell(a, 1, 10, 0));
    }
    for (double angle : new double[] {Math.PI / 2, -Math.PI / 2, Math.PI, Double.NaN}) {
      assertThrows(IllegalArgumentException.class, () -> calculator.calculateHydrostaticCell(0.1, 1, 10, angle));
    }
    assertThrows(IllegalArgumentException.class, () -> calculator.calculateHydrostaticCell(0.1, 0, 10, 0));
    assertThrows(IllegalArgumentException.class, () -> calculator.calculateHydrostaticCell(0.1, 1, -1, 0));
    assertThrows(IllegalArgumentException.class, () -> calculator.calculateHydrostaticCell(1e-100, 1, 100, 0.1));
  }

  /**
   * Independent integration over normal depth, with dry/full breakpoints.
   *
   * @param centre normalized centre depth
   * @param range normalized axial half range
   * @param metric area=0, width=1, perimeter=2
   * @return independent cell-average metric
   */
  private static double average(double centre, double range, int metric) {
    double low = centre - range, high = centre + range;
    double full = metric == 0 ? Math.PI / 4 : metric == 2 ? Math.PI : 0;
    double sum = Math.max(0, high - Math.max(1, low)) * full;
    double a = Math.max(0, Math.min(1, low)), b = Math.max(0, Math.min(1, high));
    if (b > a) {
      sum += integrate(a, b, metric, 1e-13, 24);
    }
    return sum / (2 * range);
  }

  /**
   * Independent adaptive Simpson quadrature.
   *
   * @param a lower depth
   * @param b upper depth
   * @param metric requested metric
   * @param tolerance absolute error control
   * @param depth recursion budget
   * @return integral
   */
  private static double integrate(double a, double b, int metric, double tolerance, int depth) {
    double m = (a + b) / 2;
    double fa = value(a, metric), fm = value(m, metric), fb = value(b, metric);
    double coarse = (b - a) * (fa + 4 * fm + fb) / 6;
    double fine = (b - a) * (fa + 4 * value((a + m) / 2, metric) + 2 * fm + 4 * value((m + b) / 2, metric) + fb) / 12;
    if (depth == 0 || Math.abs(fine - coarse) < 15 * tolerance) {
      return fine + (fine - coarse) / 15;
    }
    return integrate(a, m, metric, tolerance / 2, depth - 1) + integrate(m, b, metric, tolerance / 2, depth - 1);
  }

  /**
   * Independent circular-strip expression.
   *
   * @param h normalized depth in [0,1]
   * @param metric requested metric
   * @return metric
   */
  private static double value(double h, int metric) {
    double root = Math.sqrt(h * (1 - h));
    if (metric == 1) {
      return 2 * root;
    }
    double angle = Math.acos(1 - 2 * h);
    return metric == 2 ? angle : angle / 4 - (0.5 - h) * root;
  }
}
