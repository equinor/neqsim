package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for the Michelsen PT phase envelope on a realistic multicomponent gas.
 *
 * <p>
 * Two acceptance criteria are checked for every case: the envelope calculation must succeed and return both branches,
 * and each traced segment must be a continuous curve rather than a zigzag.
 * </p>
 *
 * <p>
 * Continuity is measured with a shape ratio rather than a step-size limit. The tracer already inserts a NaN break
 * whenever two consecutive points are further apart than {@code JUMP_BREAK_FACTOR * dTmax}, so asserting that steps are
 * below that limit would be tautological. Instead each segment is scored with
 * {@code wiggle = sum|dX| / (max X - min X)}, which is 1 for a monotone segment, near 2 for a segment containing one
 * turning point such as the cricondentherm, and grows without bound as a curve oscillates. Measured values are 1.3 to
 * 2.0 for correctly traced envelopes and 10.6 for a known zigzag, so the limit of 3.0 below separates the two cases
 * with a wide margin.
 * </p>
 *
 * <p>
 * The test fluid is an anonymised laboratory gas chromatograph analysis; see the header of
 * {@code rich_gas_anonymised.csv} for how the reported fractions were perturbed.
 * </p>
 *
 * @author Pablo Dupuy
 */
class PTPhaseEnvelopeContinuityTest extends neqsim.NeqSimTest {
  /** Anonymised rich gas composition, ordered by decreasing mole percent. */
  private static final String COMPOSITION_RESOURCE = "/neqsim/thermodynamicoperations/phaseenvelopeops/multicomponentenvelopeops/rich_gas_anonymised.csv";

  /** Largest acceptable ratio of total to net variation within one traced segment. */
  private static final double MAX_WIGGLE = 3.0;

  /** Largest acceptable number of direction reversals within one traced segment. */
  private static final int MAX_REVERSALS = 3;

  /** Segments shorter than this carry no shape information and are not scored. */
  private static final int MIN_SCORED_SEGMENT = 3;

  /** Fluid temperature used for every case, in Kelvin. */
  private static final double T_K = 298.0;

  /** Fluid pressure used for every case, in bara. */
  private static final double P_BARA = 50.0;

  /** Total mole percent assigned to the pseudo-components used to pad the component count. */
  private static final double PSEUDO_TOTAL_PCT = 0.02;

  /** Pseudo-component molar masses in kg/mol; addTBPfraction multiplies these by 1000. */
  private static final double[] PSEUDO_MOLAR_MASS = {0.1002, 0.1142, 0.1283, 0.1423, 0.1563, 0.1703};

  /** Pseudo-component liquid densities in g/cm3, paired with {@link #PSEUDO_MOLAR_MASS}. */
  private static final double[] PSEUDO_DENSITY = {0.684, 0.703, 0.718, 0.730, 0.740, 0.749};

  /** One component of the test composition. */
  private static final class Component {
    /** NeqSim component name. */
    private final String name;

    /** Mole percent. */
    private final double molePercent;

    /**
     * Constructor for Component.
     *
     * @param name NeqSim component name, must exist in the component database
     * @param molePercent mole percent, strictly positive
     */
    Component(String name, double molePercent) {
      this.name = name;
      this.molePercent = molePercent;
    }
  }

  /**
   * Read the anonymised composition from the test resources.
   *
   * @return components ordered by decreasing mole percent, never empty
   * @throws IOException if the resource cannot be read
   */
  private static List<Component> readComposition() throws IOException {
    List<Component> out = new ArrayList<Component>();
    InputStream in = PTPhaseEnvelopeContinuityTest.class.getResourceAsStream(COMPOSITION_RESOURCE);
    assertNotNull(in, "missing test resource " + COMPOSITION_RESOURCE);
    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    try {
      String line = reader.readLine();
      while (line != null) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("component,")) {
          int comma = trimmed.lastIndexOf(',');
          out.add(new Component(trimmed.substring(0, comma), Double.parseDouble(trimmed.substring(comma + 1))));
        }
        line = reader.readLine();
      }
    } finally {
      reader.close();
    }
    assertFalse(out.isEmpty(), "composition resource contained no components");
    return out;
  }

  /**
   * Build a fluid from the first {@code numberOfNamed} components plus optional pseudo-components.
   *
   * @param fluid empty system to populate; its mixing rule is set by the caller afterwards
   * @param numberOfNamed how many named components to take from the composition resource
   * @param numberOfPseudo how many TBP pseudo-components to append, may be zero
   * @return the populated fluid, for chaining
   * @throws IOException if the composition resource cannot be read
   */
  private static SystemInterface fill(SystemInterface fluid, int numberOfNamed, int numberOfPseudo) throws IOException {
    List<Component> all = readComposition();
    int named = Math.min(numberOfNamed, all.size());
    double total = numberOfPseudo > 0 ? PSEUDO_TOTAL_PCT : 0.0;
    for (int i = 0; i < named; i++) {
      total += all.get(i).molePercent;
    }
    for (int i = 0; i < named; i++) {
      fluid.addComponent(all.get(i).name, 100.0 * all.get(i).molePercent / total);
    }
    for (int i = 0; i < numberOfPseudo; i++) {
      int k = i % PSEUDO_MOLAR_MASS.length;
      fluid.addTBPfraction("PC" + i, 100.0 * (PSEUDO_TOTAL_PCT / numberOfPseudo) / total, PSEUDO_MOLAR_MASS[k],
          PSEUDO_DENSITY[k]);
    }
    return fluid;
  }

  /**
   * Ratio of total variation to net range; 1 for a monotone series, larger as it oscillates.
   *
   * @param values series to score, at least two entries
   * @return the ratio, or NaN when the series is flat and the ratio is undefined
   */
  private static double wiggle(double[] values) {
    double min = values[0];
    double max = values[0];
    double totalVariation = 0.0;
    for (int i = 1; i < values.length; i++) {
      totalVariation += Math.abs(values[i] - values[i - 1]);
      min = Math.min(min, values[i]);
      max = Math.max(max, values[i]);
    }
    double span = max - min;
    return span < 1e-9 ? Double.NaN : totalVariation / span;
  }

  /**
   * Count how many times the series changes direction.
   *
   * @param values series to score, at least two entries
   * @return number of sign changes in the first difference, ignoring flat steps
   */
  private static int directionReversals(double[] values) {
    int reversals = 0;
    int previousSign = 0;
    for (int i = 1; i < values.length; i++) {
      double delta = values[i] - values[i - 1];
      if (Math.abs(delta) < 1e-9) {
        continue;
      }
      int sign = delta > 0 ? 1 : -1;
      if (previousSign != 0 && sign != previousSign) {
        reversals++;
      }
      previousSign = sign;
    }
    return reversals;
  }

  /**
   * Run the envelope and assert it succeeded and that every segment is a continuous curve.
   *
   * @param fluid a fluid whose components and mixing rule are already set
   * @param label description used in assertion messages
   * @return the operation, so callers can assert on specific envelope properties
   */
  private static PTPhaseEnvelopeMichelsen assertContinuousEnvelope(SystemInterface fluid, String label) {
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();
    PTPhaseEnvelopeMichelsen operation = (PTPhaseEnvelopeMichelsen) ops.getOperation();

    List<EnvelopeSegment> segments = operation.getSegments();
    assertFalse(segments.isEmpty(), label + ": envelope produced no segments");

    // A cricondentherm still sitting at the inlet state means nothing was ever traced.
    double[] cricondenTherm = operation.getCricondenTherm();
    assertFalse(Math.abs(cricondenTherm[0] - T_K) < 1e-6 && Math.abs(cricondenTherm[1] - P_BARA) < 1e-6,
        label + ": cricondentherm equals the inlet state, envelope was not traced");

    boolean sawDew = false;
    boolean sawBubble = false;
    int scored = 0;
    for (EnvelopeSegment segment : segments) {
      if (segment.getPhaseType() == EnvelopeSegment.PhaseType.DEW) {
        sawDew = true;
      } else {
        sawBubble = true;
      }
      if (segment.size() < MIN_SCORED_SEGMENT) {
        continue;
      }
      scored++;
      double[] temperatures = segment.getTemperatures();
      double[] pressures = segment.getPressures();
      String where = label + ": " + segment.getPhaseType() + " segment of " + segment.size() + " points";

      double wiggleT = wiggle(temperatures);
      if (!Double.isNaN(wiggleT)) {
        assertTrue(wiggleT <= MAX_WIGGLE,
            where + " zigzags in temperature, wiggle=" + wiggleT + " limit=" + MAX_WIGGLE);
      }
      double wiggleP = wiggle(pressures);
      if (!Double.isNaN(wiggleP)) {
        assertTrue(wiggleP <= MAX_WIGGLE, where + " zigzags in pressure, wiggle=" + wiggleP + " limit=" + MAX_WIGGLE);
      }
      assertTrue(directionReversals(temperatures) <= MAX_REVERSALS, where + " reverses direction in temperature "
          + directionReversals(temperatures) + " times, limit=" + MAX_REVERSALS);
      assertTrue(directionReversals(pressures) <= MAX_REVERSALS, where + " reverses direction in pressure "
          + directionReversals(pressures) + " times, limit=" + MAX_REVERSALS);
    }
    assertTrue(scored > 0, label + ": no segment was long enough to score");
    assertTrue(sawDew, label + ": no dew segment was traced");
    assertTrue(sawBubble, label + ": no bubble segment was traced");
    return operation;
  }

  /**
   * The continuity score must accept a smooth curve and reject a zigzag.
   *
   * <p>
   * Without this the continuity limits could silently become vacuous. The zigzag below mimics the failure mode being
   * guarded against: a flat list that alternates between two branches instead of following one. Measured values on real
   * envelopes are 1.3 to 2.0, and a real zigzag from the grid-based envelope method scores 10.6 with 24 reversals.
   * </p>
   */
  @Test
  void testContinuityScoreRejectsZigzag() {
    double[] smooth = new double[40];
    for (int i = 0; i < smooth.length; i++) {
      // half a sine, so one turning point, like a branch containing the cricondentherm
      smooth[i] = 50.0 * Math.sin(Math.PI * i / (smooth.length - 1.0));
    }
    assertTrue(wiggle(smooth) <= MAX_WIGGLE, "a single-turning-point curve must pass");
    assertTrue(directionReversals(smooth) <= MAX_REVERSALS, "a single-turning-point curve must pass");

    double[] zigzag = new double[40];
    for (int i = 0; i < zigzag.length; i++) {
      zigzag[i] = i % 2 == 0 ? -60.0 + i : 20.0 + i;
    }
    assertTrue(wiggle(zigzag) > MAX_WIGGLE, "a zigzag must be rejected, wiggle=" + wiggle(zigzag));
    assertTrue(directionReversals(zigzag) > MAX_REVERSALS,
        "a zigzag must be rejected, reversals=" + directionReversals(zigzag));
  }

  /**
   * A three-component subset of the gas traces a continuous envelope on UMR-PRU.
   *
   * @throws IOException if the composition resource cannot be read
   */
  @Test
  void testUmrPruThreeComponentEnvelopeIsContinuous() throws IOException {
    SystemInterface fluid = new SystemUMRPRUMCEos(T_K, P_BARA);
    fill(fluid, 3, 0);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    assertEquals(3, fluid.getPhase(0).getNumberOfComponents());

    PTPhaseEnvelopeMichelsen operation = assertContinuousEnvelope(fluid, "UMR-PRU 3 components");
    assertEquals(-32.23, operation.getCricondenTherm()[0] - 273.15, 1.0, "cricondentherm temperature drifted");
    assertEquals(52.14, operation.getCricondenTherm()[1], 2.0, "cricondentherm pressure drifted");
  }

  /**
   * A 25-component subset, still carrying the heavy tail, traces a continuous envelope on UMR-PRU.
   *
   * @throws IOException if the composition resource cannot be read
   */
  @Test
  void testUmrPruMulticomponentEnvelopeIsContinuous() throws IOException {
    SystemInterface fluid = new SystemUMRPRUMCEos(T_K, P_BARA);
    fill(fluid, 25, 0);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    assertEquals(25, fluid.getPhase(0).getNumberOfComponents());

    PTPhaseEnvelopeMichelsen operation = assertContinuousEnvelope(fluid, "UMR-PRU 25 components");
    assertEquals(27.44, operation.getCricondenTherm()[0] - 273.15, 2.0, "cricondentherm temperature drifted");
    assertEquals(48.58, operation.getCricondenTherm()[1], 3.0, "cricondentherm pressure drifted");
  }

  /**
   * More than 100 components trace a continuous envelope.
   *
   * <p>
   * The cricondenden composition arrays were previously allocated with a fixed length of 100, so this fluid threw
   * {@code ArrayIndexOutOfBoundsException}. SRK is used because the defect was an array bound rather than anything
   * thermodynamic, and SRK keeps the test affordable; the UMR-PRU equivalent is
   * {@link #testUmrPruAboveOneHundredComponents()}.
   * </p>
   *
   * @throws IOException if the composition resource cannot be read
   */
  @Test
  void testEnvelopeAboveOneHundredComponents() throws IOException {
    SystemInterface fluid = new SystemSrkEos(T_K, P_BARA);
    fill(fluid, 84, 30);
    fluid.setMixingRule("classic");
    int components = fluid.getPhase(0).getNumberOfComponents();
    assertTrue(components > 100, "expected more than 100 components, got " + components);

    PTPhaseEnvelopeMichelsen operation = assertContinuousEnvelope(fluid, components + " components, SRK");
    assertEquals(components, operation.get("cricondenthermX").length,
        "cricondentherm composition array was not sized to the component count");
    assertEquals(components, operation.get("cricondenbarY").length,
        "cricondenbar composition array was not sized to the component count");
  }

  /**
   * The same above-100-component fluid on UMR-PRU.
   *
   * <p>
   * Tagged slow because a UMR-PRU envelope of this size takes minutes; the default build excludes it. Run with
   * {@code -DexcludedTestGroups=} to include it.
   * </p>
   *
   * @throws IOException if the composition resource cannot be read
   */
  @Test
  @Tag("slow")
  void testUmrPruAboveOneHundredComponents() throws IOException {
    SystemInterface fluid = new SystemUMRPRUMCEos(T_K, P_BARA);
    fill(fluid, 84, 30);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    int components = fluid.getPhase(0).getNumberOfComponents();
    assertTrue(components > 100, "expected more than 100 components, got " + components);

    PTPhaseEnvelopeMichelsen operation = assertContinuousEnvelope(fluid, components + " components, UMR-PRU");
    assertEquals(components, operation.get("cricondenthermX").length,
        "cricondentherm composition array was not sized to the component count");
  }
}
