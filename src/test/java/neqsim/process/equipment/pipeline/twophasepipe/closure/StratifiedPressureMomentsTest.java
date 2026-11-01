package neqsim.process.equipment.pipeline.twophasepipe.closure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator.StratifiedPressureMoments;

/**
 * Independent integration, phase-limit and hydrostatic traction identities for pressure moments.
 *
 * @author NeqSim
 * @version 1.0
 */
class StratifiedPressureMomentsTest {
  private static final GeometryCalculator GEOMETRY = new GeometryCalculator();
  private static final double[] DIAMETERS = {0.01, 0.2, 1.0148, 3.0};
  private static final double[] FRACTIONS = {0.0, 1e-14, 1e-10, 1e-6, 0.01, 0.1, 0.25, 0.5, 0.75, 0.99, 1.0 - 1e-10,
      1.0 - 1e-14, 1.0};

  /** Independent angular integration includes both exact phase endpoints. */
  @Test
  void independentIntegralAndPhaseLimits() {
    assertEquals(52, qualify(null));
  }

  /** The derivative of each signed moment is its phase area. */
  @Test
  void derivativesArePhaseAreas() {
    for (double diameter : DIAMETERS) {
      double epsilon = 1e-5 * diameter;
      for (double fraction : new double[] {0.01, 0.1, 0.25, 0.5, 0.75, 0.99}) {
        double depth = fraction * diameter;
        StratifiedPressureMoments value = GEOMETRY.calculateStratifiedPressureMoments(depth, diameter);
        StratifiedPressureMoments left = GEOMETRY.calculateStratifiedPressureMoments(depth - epsilon, diameter);
        StratifiedPressureMoments right = GEOMETRY.calculateStratifiedPressureMoments(depth + epsilon, diameter);
        assertEquals(value.getLiquidArea(), (right.getLiquidMoment() - left.getLiquidMoment()) / (2 * epsilon),
            1e-7 * diameter * diameter);
        assertEquals(value.getGasArea(), (right.getGasMoment() - left.getGasMoment()) / (2 * epsilon),
            1e-7 * diameter * diameter);
      }
    }
  }

  /** Hydrostatic traction differences balance integrated axial gravity for either phase. */
  @Test
  void inclinedFiniteVolumeIdentity() {
    assertEquals(48, qualifyForces(null, 4096));
  }

  /** Closed-form half/full sections and complementary phases fix signs and normalizations. */
  @Test
  void analyticalSymmetry() {
    for (double diameter : DIAMETERS) {
      double cube = diameter * diameter * diameter;
      StratifiedPressureMoments half = GEOMETRY.calculateStratifiedPressureMoments(0.5 * diameter, diameter);
      assertEquals(cube / 12.0, half.getLiquidMoment(), 1e-15 * cube);
      assertEquals(-cube / 12.0, half.getGasMoment(), 1e-15 * cube);
      StratifiedPressureMoments full = GEOMETRY.calculateStratifiedPressureMoments(diameter, diameter);
      assertEquals(Math.PI * cube / 8.0, full.getLiquidMoment(), 1e-15 * cube);
      for (double fraction : new double[] {0.01, 0.1, 0.25, 0.5}) {
        StratifiedPressureMoments value = GEOMETRY.calculateStratifiedPressureMoments(fraction * diameter, diameter);
        StratifiedPressureMoments complement = GEOMETRY.calculateStratifiedPressureMoments((1 - fraction) * diameter,
            diameter);
        assertEquals(value.getLiquidMoment(), -complement.getGasMoment(), 1e-15 * cube);
        assertEquals(value.getGasMoment(), -complement.getLiquidMoment(), 1e-15 * cube);
      }
    }
  }

  /**
   * The immutable geometry result survives serialization without shared mutable state.
   *
   * @throws Exception if serialization fails
   */
  @Test
  void serialization() throws Exception {
    StratifiedPressureMoments value = GEOMETRY.calculateStratifiedPressureMoments(0.31, 1.0148);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(value);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      StratifiedPressureMoments restored = (StratifiedPressureMoments) input.readObject();
      assertEquals(value.getLiquidMoment(), restored.getLiquidMoment(), 0.0);
      assertEquals(value.getGasMoment(), restored.getGasMoment(), 0.0);
      assertEquals(value.getLiquidArea(), restored.getLiquidArea(), 0.0);
      assertEquals(value.getGasArea(), restored.getGasArea(), 0.0);
    }
  }

  /** Positive trace moments retain their analytical small-depth limit without a seed or subtraction. */
  @Test
  void traceAndInvalidGeometry() {
    for (double diameter : DIAMETERS) {
      double h = 1e-14 * diameter;
      StratifiedPressureMoments value = GEOMETRY.calculateStratifiedPressureMoments(h, diameter);
      assertTrue(value.getLiquidMoment() > 0.0);
      assertEquals(8.0 / 15.0, value.getLiquidMoment() / (h * h * Math.sqrt(h * diameter)), 1e-10);
      assertEquals(4.0 / 3.0, value.getLiquidArea() / (h * Math.sqrt(h * diameter)), 1e-10);
    }
    for (double h : new double[] {-1.0, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> GEOMETRY.calculateStratifiedPressureMoments(h, 1.0));
    }
    for (double diameter : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 1e-200, 1e-110, 1e200}) {
      assertThrows(IllegalArgumentException.class, () -> GEOMETRY.calculateStratifiedPressureMoments(0.0, diameter));
    }
    assertThrows(IllegalArgumentException.class, () -> GEOMETRY.calculateStratifiedPressureMoments(1e-200, 1.0));
  }

  /**
   * Retain every independently integrated moment control in CSV.
   *
   * @param args output directory for moment, force refinement and timing CSVs
   * @throws IOException if output cannot be written
   */
  public static void main(String[] args) throws IOException {
    Files.createDirectories(Paths.get(args[0]));
    try (PrintWriter output = writer(args[0], "moments.csv")) {
      output.println(
          "diameter_m,depth_fraction,liquid_moment_m3,reference_liquid_m3,gas_moment_m3,reference_gas_m3,error_m3");
      assertEquals(52, qualify(output));
    }
    try (PrintWriter output = writer(args[0], "forces.csv")) {
      output.println("intervals,diameter_m,angle_rad,density_kg_m3,phase,traction_N,gravity_N,residual_N");
      for (int intervals : new int[] {64, 256, 1024, 4096}) {
        assertEquals(48, qualifyForces(output, intervals));
      }
    }
    try (PrintWriter output = writer(args[0], "timing.csv")) {
      output.println("batch,calls,elapsed_ns,checksum");
      for (int batch = -3; batch < 7; batch++) {
        double checksum = 0.0;
        long start = System.nanoTime();
        for (int index = 0; index < 100000; index++) {
          double depth = (0.01 + 0.98 * (index % 997) / 996.0) * 1.0148;
          StratifiedPressureMoments value = GEOMETRY.calculateStratifiedPressureMoments(depth, 1.0148);
          checksum += value.getLiquidMoment() + value.getGasMoment();
        }
        long elapsed = System.nanoTime() - start;
        if (batch >= 0) {
          output.printf(Locale.ROOT, "%d,%d,%d,%.16g%n", batch, 100000, elapsed, checksum);
        }
      }
    }
  }

  /**
   * Open a deterministic UTF-8 audit output.
   *
   * @param directory output directory
   * @param name CSV basename
   * @return owned output writer
   * @throws IOException if creation fails
   */
  private static PrintWriter writer(String directory, String name) throws IOException {
    return new PrintWriter(Files.newBufferedWriter(Paths.get(directory, name), StandardCharsets.UTF_8));
  }

  /**
   * Independently integrate phase gravity on a straight inclined cell with asymmetric wet depths.
   *
   * @param output optional output writer
   * @param intervals even Simpson subinterval count
   * @return phase comparison count
   */
  private static int qualifyForces(PrintWriter output, int intervals) {
    int count = 0;
    for (double diameter : DIAMETERS) {
      for (double angle : new double[] {-0.03, 0.03}) {
        double length = 0.7 * diameter / Math.abs(Math.tan(angle));
        double leftDepth = (angle > 0 ? 0.87 : 0.17) * diameter;
        double rightDepth = (angle > 0 ? 0.17 : 0.87) * diameter;
        StratifiedPressureMoments left = GEOMETRY.calculateStratifiedPressureMoments(leftDepth, diameter);
        StratifiedPressureMoments right = GEOMETRY.calculateStratifiedPressureMoments(rightDepth, diameter);
        double liquidIntegral = 0.0;
        for (int index = 0; index <= intervals; index++) {
          double depth = leftDepth + (rightDepth - leftDepth) * index / intervals;
          double weight = index == 0 || index == intervals ? 1.0 : index % 2 == 0 ? 2.0 : 4.0;
          liquidIntegral += weight * referenceArea(depth, diameter);
        }
        liquidIntegral *= length / (3.0 * intervals);
        for (double density : new double[] {10.0, 800.0, 1000.0}) {
          for (int phase = 0; phase < 2; phase++) {
            double momentDifference = phase == 0 ? right.getLiquidMoment() - left.getLiquidMoment()
                : right.getGasMoment() - left.getGasMoment();
            double volume = phase == 0 ? liquidIntegral : Math.PI * diameter * diameter / 4.0 * length - liquidIntegral;
            double traction = density * 9.81 * Math.cos(angle) * momentDifference;
            double gravity = -density * 9.81 * Math.sin(angle) * volume;
            if (intervals == 4096) {
              assertEquals(gravity, traction, 1e-12 + 1e-10 * Math.abs(gravity));
            }
            if (output != null) {
              output.printf(Locale.ROOT, "%d,%.16g,%.16g,%.16g,%s,%.16g,%.16g,%.16g%n", intervals, diameter, angle,
                  density, phase == 0 ? "liquid" : "gas", traction, gravity, traction - gravity);
            }
            count++;
          }
        }
      }
    }
    return count;
  }

  /**
   * Execute the independently integrated moment matrix.
   *
   * @param output optional retained output writer
   * @return evaluated case count
   */
  private static int qualify(PrintWriter output) {
    int count = 0;
    for (double diameter : DIAMETERS) {
      for (double fraction : FRACTIONS) {
        double depth = fraction * diameter;
        StratifiedPressureMoments value = GEOMETRY.calculateStratifiedPressureMoments(depth, diameter);
        double liquid = referenceMoment(depth, diameter);
        double gas = -referenceMoment(diameter - depth, diameter);
        double error = Math.max(Math.abs(value.getLiquidMoment() - liquid), Math.abs(value.getGasMoment() - gas));
        assertEquals(liquid, value.getLiquidMoment(), 1e-14 * Math.pow(diameter, 3) + 1e-10 * Math.abs(liquid));
        assertEquals(gas, value.getGasMoment(), 1e-14 * Math.pow(diameter, 3) + 1e-10 * Math.abs(gas));
        assertTrue(value.getLiquidMoment() >= 0.0);
        assertTrue(value.getGasMoment() <= 0.0);
        assertEquals(Math.PI * diameter * diameter / 4.0, value.getLiquidArea() + value.getGasArea(),
            1e-15 * diameter * diameter);
        if (fraction == 0.0) {
          assertEquals(0.0, value.getLiquidMoment(), 0.0);
        }
        if (fraction == 1.0) {
          assertEquals(0.0, value.getGasMoment(), 0.0);
        }
        if (output != null) {
          output.printf(Locale.ROOT, "%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g%n", diameter, fraction,
              value.getLiquidMoment(), liquid, value.getGasMoment(), gas, error);
        }
        count++;
      }
    }
    return count;
  }

  /**
   * Integrate pressure times chord area independently in angular coordinates using composite Simpson quadrature.
   *
   * @param h interface depth in m
   * @param diameter diameter in m
   * @return reference first moment in m3
   */
  private static double referenceMoment(double h, double diameter) {
    int intervals = 4096;
    double angle = 2.0 * Math.asin(Math.sqrt(h / diameter));
    double integral = 0.0;
    for (int index = 0; index <= intervals; index++) {
      double theta = angle * index / intervals;
      double sine = Math.sin(theta);
      double halfSine = Math.sin(0.5 * theta);
      double integrand = (h - diameter * halfSine * halfSine) * sine * sine;
      double weight = index == 0 || index == intervals ? 1.0 : index % 2 == 0 ? 2.0 : 4.0;
      integral += weight * integrand;
    }
    return 0.5 * diameter * diameter * angle * integral / (3.0 * intervals);
  }

  /**
   * Evaluate the circular segment area independently for ordinary, partially wet sections.
   *
   * @param h interface depth in m
   * @param diameter diameter in m
   * @return occupied area in m2
   */
  private static double referenceArea(double h, double diameter) {
    double radius = 0.5 * diameter;
    double angle = Math.acos(1.0 - h / radius);
    return radius * radius * (angle - Math.sin(angle) * Math.cos(angle));
  }
}
