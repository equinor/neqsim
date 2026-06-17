package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for the structured segment API returned by
 * {@link PTPhaseEnvelopeMichelsen#getSegments()} and
 * {@link ThermodynamicOperations#getEnvelopeSegments()}. The segment API is the preferred way to
 * consume envelope output; the flat get("dewT") arrays remain for backward compatibility.
 */
class PTPhaseEnvelopeSegmentsTest {

  /**
   * A typical gas condensate envelope is traced in two passes that meet at the critical point. The
   * segment API should expose this as at least one dew segment and one bubble segment, each
   * internally contiguous (no NaN, monotonically traced).
   */
  @Test
  void testSegmentsBasicStructure() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    List<EnvelopeSegment> segments = ops.getEnvelopeSegments();
    assertNotNull(segments, "Segments list must not be null");
    assertFalse(segments.isEmpty(), "At least one segment must be produced");

    boolean hasDew = false;
    boolean hasBub = false;
    for (EnvelopeSegment s : segments) {
      assertTrue(s.size() >= 1, "Segment must contain at least one point");
      double[] T = s.getTemperatures();
      double[] P = s.getPressures();
      assertEquals(T.length, P.length, "T and P arrays must have equal length");
      for (int i = 0; i < T.length; i++) {
        assertFalse(Double.isNaN(T[i]), "Segment temperatures must not contain NaN");
        assertFalse(Double.isNaN(P[i]), "Segment pressures must not contain NaN");
        assertTrue(T[i] > 50.0 && T[i] < 700.0, "Segment T out of range: " + T[i]);
        assertTrue(P[i] > 0.0 && P[i] < 2000.0, "Segment P out of range: " + P[i]);
      }
      if (s.getPhaseType() == EnvelopeSegment.PhaseType.DEW) {
        hasDew = true;
      }
      if (s.getPhaseType() == EnvelopeSegment.PhaseType.BUBBLE) {
        hasBub = true;
      }
    }
    assertTrue(hasDew, "Envelope should have at least one DEW segment");
    assertTrue(hasBub, "Envelope should have at least one BUBBLE segment");
  }

  /**
   * Concatenating all segment points per phase type must reproduce the non-NaN entries of the
   * flat arrays returned by {@code get("dewT")} / {@code get("bubT")}. This proves the segment
   * view and the flat view are consistent.
   */
  @Test
  void testSegmentsMatchFlatArrays() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.06);
    fluid.addComponent("n-butane", 0.04);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    int dewPointsFromSegments = 0;
    int bubPointsFromSegments = 0;
    for (EnvelopeSegment s : ops.getEnvelopeSegments()) {
      if (s.getPhaseType() == EnvelopeSegment.PhaseType.DEW) {
        dewPointsFromSegments += s.size();
      } else {
        bubPointsFromSegments += s.size();
      }
    }

    int dewPointsFromFlat = countNonNaN(ops.get("dewT"));
    int bubPointsFromFlat = countNonNaN(ops.get("bubT"));

    assertEquals(dewPointsFromFlat, dewPointsFromSegments,
        "Sum of DEW segment sizes must equal non-NaN count in flat dewT array");
    assertEquals(bubPointsFromFlat, bubPointsFromSegments,
        "Sum of BUBBLE segment sizes must equal non-NaN count in flat bubT array");
  }

  /**
   * Count non-NaN entries in an array.
   *
   * @param arr the array to scan
   * @return number of entries that are not NaN
   */
  private static int countNonNaN(double[] arr) {
    int n = 0;
    for (double v : arr) {
      if (!Double.isNaN(v)) {
        n++;
      }
    }
    return n;
  }
}
