package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GravityDumpFloodInjectionAnalyzer}.
 *
 * <p>
 * The reference case is a depleted reservoir at 3000 m TVD MSL under 370 m of water, injected with a
 * pump-less gravity seawater column. With the depleted reservoir at 180–200 bara, the sub-seabed
 * column head (~261 bar) exceeds the reservoir pressure, so the well free-falls and forms a vapour
 * cavity — it cannot be throttled at the wellhead.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class GravityDumpFloodInjectionAnalyzerTest {

  /**
   * Builds the reference Verdande-style analyzer with explicitly supplied seawater properties so the
   * test is deterministic and independent of electrolyte flash convergence.
   *
   * @param reservoirPressureBara depleted reservoir pressure [bara]
   * @return configured analyzer (not yet analyzed)
   */
  private GravityDumpFloodInjectionAnalyzer buildReferenceCase(double reservoirPressureBara) {
    GravityDumpFloodInjectionAnalyzer a =
        new GravityDumpFloodInjectionAnalyzer("Verdande WI screen");
    a.setWaterDepth(370.0);
    a.setReservoirDepthTvd(3000.0);
    a.setReservoirPressure(reservoirPressureBara);
    a.setTubingId(0.1524);
    a.setRoughness(4.5e-5);
    a.setSeabedTemperature(2.5);
    a.setInjectionRate(1500.0);
    a.setSeawaterProperties(1012.9, 1.6, 0.00751);
    return a;
  }

  /**
   * Verifies the sub-seabed column head, the free-fall flag, the negative required wellhead
   * pressure, and the vapour-cavity onset depth for the depleted (200 bara) reference case.
   */
  @Test
  void testFreeFallingDepletedReservoir() {
    GravityDumpFloodInjectionAnalyzer a = buildReferenceCase(200.0);
    a.analyze();

    // Sub-seabed column head: rho*g*L = 1012.9 * 9.80665 * 2630 / 1e5 ≈ 261.2 bar.
    assertEquals(261.2, a.getSubSeabedColumnHeadBar(), 1.5, "sub-seabed column head");

    // Column over-pressures the depleted reservoir -> free-falling, vapour-capped.
    assertTrue(a.isFreeFalling(), "should free-fall when head exceeds reservoir pressure");

    // Required wellhead pressure is negative (physically impossible).
    assertTrue(a.getWellheadPressureRequiredBara() < 0.0,
        "required wellhead pressure should be negative");

    // Vapour cavity onset: L - (p_res - Pvap)/(rho*g) ≈ 2630 - 2013 ≈ 617 m below seabed.
    assertEquals(617.0, a.getVapourCavityDepthBelowSeabedM(), 15.0, "vapour cavity depth");

    // Down-hole back-pressure to dissipate ≈ head - reservoir ≈ 61 bar.
    assertEquals(61.0, a.getDownholeBackPressureRequiredBar(), 5.0,
        "down-hole back-pressure to dissipate");

    // A 6-inch wellhead choke taking the full drop cavitates hard (sigma -> ~0).
    assertTrue(a.getCavitationIndex() < 1.5, "wellhead choke should cavitate");

    Map<String, Object> r = a.getResults();
    assertEquals(Boolean.TRUE, r.get("free_falling"));
    assertTrue(((Number) r.get("static_sandface_pressure_bara")).doubleValue() > 290.0,
        "static sandface pressure should exceed 290 bara");
  }

  /**
   * Verifies that the friction tail-pipe sizing returns a small (sub-inch) diameter, demonstrating
   * that a 6-inch tubing cannot dissipate the excess head by friction alone.
   */
  @Test
  void testFrictionTailpipeSizing() {
    GravityDumpFloodInjectionAnalyzer a = buildReferenceCase(180.0);
    a.analyze();
    double idMm = a.getFrictionTailpipeIdMm();
    // The friction-dissipating ID (~69 mm, a ~2.7" tail-pipe) is much smaller than the 152 mm
    // (6") tubing, proving a full-bore string cannot burn the excess head by friction alone.
    assertTrue(idMm > 0.0 && idMm < 100.0,
        "friction-only dissipation requires a sub-tubing ID (<100 mm), got " + idMm + " mm");
    assertTrue(idMm < 152.0, "friction-dissipating ID must be smaller than the 6-inch tubing");
    // Larger depletion (180 bara) -> larger excess head than the 200 bara case.
    assertTrue(a.getDownholeBackPressureRequiredBar() > 70.0,
        "180 bara case should require >70 bar dissipation");
  }

  /**
   * Verifies that an over-pressured (non-depleted) reservoir is not flagged as free-falling.
   */
  @Test
  void testNonDepletedReservoirNotFreeFalling() {
    GravityDumpFloodInjectionAnalyzer a = buildReferenceCase(310.0);
    a.analyze();
    assertFalse(a.isFreeFalling(),
        "reservoir above column head should not free-fall");
    assertTrue(a.getWellheadPressureRequiredBara() > 0.0,
        "required wellhead pressure should be positive when reservoir is strong");
  }
}
