package neqsim.process.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RelationshipGraph} unsupervised lead-lag relationship discovery.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class RelationshipGraphTest {

  /**
   * Builds a synthetic historian data set where a driver tag leads a follower by a known lag, a third tag is an inverse
   * follower, and a fourth tag is random noise. Verifies the graph recovers the correct driver, follower, direction,
   * lag magnitude, and sign, and ranks the noise pairing below threshold.
   */
  @Test
  void discoversKnownLeadLagRelationship() {
    int n = 200;
    int knownLag = 5;
    double[] driver = new double[n];
    double[] follower = new double[n];
    double[] inverseFollower = new double[n];
    double[] noise = new double[n];
    double[] timestamps = new double[n];

    java.util.Random rng = new java.util.Random(42L);
    for (int i = 0; i < n; i++) {
      timestamps[i] = i * 60.0; // 60 s sampling
      driver[i] = Math.sin(i * 0.1);
      noise[i] = rng.nextGaussian();
    }
    for (int i = 0; i < n; i++) {
      int src = i - knownLag;
      follower[i] = src >= 0 ? driver[src] : 0.0;
      inverseFollower[i] = src >= 0 ? -driver[src] : 0.0;
    }

    Map<String, double[]> data = new HashMap<String, double[]>();
    data.put("DRIVER", driver);
    data.put("FOLLOWER", follower);
    data.put("INV_FOLLOWER", inverseFollower);
    data.put("NOISE", noise);

    RelationshipGraph graph = new RelationshipGraph();
    graph.setTimestamps(timestamps);
    graph.setMaxLagSamples(10);
    graph.setMinAbsCorrelation(0.5);
    List<RelationshipGraph.Relationship> edges = graph.analyze(data);

    assertNotNull(edges);
    assertFalse(edges.isEmpty(), "expected at least the driver->follower relationships");

    // Top relationship should be one of the driver-follower pairs (|r| near 1).
    RelationshipGraph.Relationship top = edges.get(0);
    assertTrue(Math.abs(top.getCorrelation()) > 0.9, "top relationship should be strongly correlated");

    RelationshipGraph.Relationship driverFollower = findEdge(edges, "DRIVER", "FOLLOWER");
    assertNotNull(driverFollower, "DRIVER->FOLLOWER relationship should be discovered");
    assertEquals("DRIVER", driverFollower.getSource(), "driver should be identified as the leader");
    assertEquals("FOLLOWER", driverFollower.getTarget(), "follower should be identified as the effect");
    assertEquals(RelationshipGraph.Direction.LEADS, driverFollower.getDirection());
    assertEquals(knownLag, driverFollower.getLagSamples(), "lag magnitude should match the injected lag");
    assertEquals(knownLag * 60.0, driverFollower.getLagSeconds(), 1e-9, "lag in seconds should use median timestep");
    assertTrue(driverFollower.getCorrelation() > 0.9, "positive correlation expected");

    RelationshipGraph.Relationship inverse = findEdge(edges, "DRIVER", "INV_FOLLOWER");
    assertNotNull(inverse, "DRIVER->INV_FOLLOWER relationship should be discovered");
    assertTrue(inverse.getCorrelation() < -0.9, "inverse follower should have strong negative correlation");

    // Noise should not correlate strongly with the driver.
    RelationshipGraph.Relationship driverNoise = findEdge(edges, "DRIVER", "NOISE");
    if (driverNoise != null) {
      assertTrue(Math.abs(driverNoise.getCorrelation()) < 0.5, "noise should not correlate strongly with the driver");
    }
  }

  /**
   * Verifies that analysis on fewer than two tags returns an empty list without error.
   */
  @Test
  void returnsEmptyForInsufficientTags() {
    RelationshipGraph graph = new RelationshipGraph();
    Map<String, double[]> single = new HashMap<String, double[]>();
    single.put("ONLY", new double[] { 1.0, 2.0, 3.0, 4.0, 5.0, 6.0 });
    assertTrue(graph.analyze(single).isEmpty());
    assertTrue(graph.analyze(null).isEmpty());
    assertTrue(graph.analyze(new HashMap<String, double[]>()).isEmpty());
  }

  /**
   * Verifies that NaN gaps are tolerated and a synchronous relationship is reported with zero lag.
   */
  @Test
  void handlesNaNAndSynchronousRelationship() {
    int n = 60;
    double[] a = new double[n];
    double[] b = new double[n];
    for (int i = 0; i < n; i++) {
      a[i] = Math.sin(i * 0.2);
      b[i] = 2.0 * a[i] + 1.0; // perfectly synchronous, scaled
    }
    a[10] = Double.NaN;
    b[25] = Double.NaN;

    Map<String, double[]> data = new HashMap<String, double[]>();
    data.put("A", a);
    data.put("B", b);

    RelationshipGraph graph = new RelationshipGraph();
    graph.setMaxLagSamples(5);
    graph.setMinAbsCorrelation(0.5);
    List<RelationshipGraph.Relationship> edges = graph.analyze(data);

    assertEquals(1, edges.size());
    RelationshipGraph.Relationship edge = edges.get(0);
    assertEquals(RelationshipGraph.Direction.SYNCHRONOUS, edge.getDirection());
    assertEquals(0, edge.getLagSamples());
    assertTrue(edge.getCorrelation() > 0.99, "scaled copy should be near-perfectly correlated");
    assertNotNull(graph.toTextReport(edges));
  }

  /**
   * Verifies that rank (Spearman) correlation captures a strong monotonic non-linear coupling that linear Pearson
   * under-reports.
   */
  @Test
  void rankCorrelationCapturesMonotonicNonlinearity() {
    int n = 60;
    double[] x = new double[n];
    double[] y = new double[n];
    for (int i = 0; i < n; i++) {
      x[i] = i / 10.0;
      y[i] = Math.exp(3.0 * x[i]); // strictly increasing, strongly convex
    }
    Map<String, double[]> data = new HashMap<String, double[]>();
    data.put("X", x);
    data.put("Y", y);

    RelationshipGraph linear = new RelationshipGraph();
    linear.setMaxLagSamples(0);
    linear.setMinAbsCorrelation(0.0);
    double pearson = Math.abs(linear.analyze(data).get(0).getCorrelation());

    RelationshipGraph rank = new RelationshipGraph();
    rank.setUseRankCorrelation(true);
    rank.setMaxLagSamples(0);
    rank.setMinAbsCorrelation(0.0);
    double spearman = Math.abs(rank.analyze(data).get(0).getCorrelation());

    assertTrue(spearman > pearson, "rank correlation should exceed linear for a convex monotonic mapping");
    assertTrue(spearman > 0.999, "a perfectly monotonic mapping should give rank |r| ~ 1");
  }

  /**
   * Finds the relationship connecting two named tags regardless of discovered direction.
   *
   * @param edges discovered relationships
   * @param tag1 one tag name
   * @param tag2 the other tag name
   * @return the matching relationship, or null when none connects the two tags
   */
  private RelationshipGraph.Relationship findEdge(List<RelationshipGraph.Relationship> edges, String tag1,
      String tag2) {
    for (RelationshipGraph.Relationship r : edges) {
      boolean match = (r.getSource().equals(tag1) && r.getTarget().equals(tag2))
          || (r.getSource().equals(tag2) && r.getTarget().equals(tag1));
      if (match) {
        return r;
      }
    }
    return null;
  }
}
