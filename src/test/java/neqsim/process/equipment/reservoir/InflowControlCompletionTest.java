package neqsim.process.equipment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reservoir.InflowControlCompletion.Comparison;
import neqsim.process.equipment.reservoir.InflowControlCompletion.Result;

/**
 * Tests for {@link InflowControlCompletion}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class InflowControlCompletionTest {
  /**
   * Three-zone completion: an oil heel, a middle zone with some gas and a water-producing toe.
   *
   * @return completion without device
   */
  private static InflowControlCompletion completion() {
    InflowControlCompletion c = new InflowControlCompletion("A-1");
    c.setFluidProperties(800.0, 1030.0, 150.0, 0.002, 0.0005, 2.0e-5);
    c.setFormationVolumeFactors(1.2, 1.0, 0.005);
    c.addZone("heel", 60.0, 0.0, 0.0);
    c.addZone("middle", 40.0, 0.1, 0.3);
    c.addZone("toe", 80.0, 0.7, 0.0);
    return c;
  }

  /** Without a device each zone gives PI times drawdown. */
  @Test
  void bareCompletionIsLinear() {
    InflowControlCompletion c = completion();
    Result r = c.solve(10.0, false);
    assertEquals(1800.0, r.getZoneFlow(0) + r.getZoneFlow(1) + r.getZoneFlow(2), 1e-6);
    assertEquals((600.0 + 400.0 * 0.6 + 800.0 * 0.3) / 1.2, r.getOilRate(), 1e-6);
    assertEquals(0.0, r.getZoneDeviceDp(0), 0.0);
    assertEquals(0.0, c.solve(0.0, false).getOilRate(), 0.0);
  }

  /** With a device the zone flow satisfies the series pressure balance. */
  @Test
  void deviceZoneSatisfiesPressureBalance() {
    InflowControlCompletion c = completion();
    c.setDevice(InflowControlDevice.aicd("AICD", 20, 1.0, 2.0, 2.0, 0.5, 800.0, 0.002));
    Result r = c.solve(10.0, true);
    for (int i = 0; i < 3; i++) {
      double pi = i == 0 ? 60.0 : i == 1 ? 40.0 : 80.0;
      assertEquals(10.0, r.getZoneFlow(i) / pi + r.getZoneDeviceDp(i), 1e-6);
    }
    assertTrue(r.getZoneFlow(2) < 800.0);
  }

  /** An AICD cuts water and gas at the same oil rate and needs extra drawdown. */
  @Test
  void aicdReducesWaterAndGasAtSameOil() {
    InflowControlCompletion c = completion();
    c.setDevice(InflowControlDevice.aicd("AICD", 20, 1.0, 2.0, 2.0, 2.0, 800.0, 0.002));
    Comparison cmp = c.compareAtSameOil(10.0, 100.0);
    assertTrue(cmp.isOilRateReached());
    assertEquals(cmp.getBare().getOilRate(), cmp.getWithDevice().getOilRate(), 1e-6 * cmp.getBare().getOilRate());
    assertTrue(cmp.getExtraDrawdownBar() > 0.0);
    assertTrue(cmp.getWaterReduction() > 0.05, "water " + cmp.getWaterReduction());
    assertTrue(cmp.getGasReduction() > 0.0, "gas " + cmp.getGasReduction() + " dd " + cmp.getExtraDrawdownBar());
    assertTrue(cmp.getWithDevice().getWaterCut() < cmp.getBare().getWaterCut());
  }

  /** A passive ICD with the same pressure drop on oil discriminates less than an AICD. */
  @Test
  void aicdOutperformsPassiveIcd() {
    InflowControlDevice icd = InflowControlDevice.icd("ICD", 20, 4.0, 1, 0.8);
    double dpRef = icd.pressureDropBar(20.0 * 24.0,
        new InflowControlDevice.Mixture(800.0, 1030.0, 150.0, 0.002, 0.0005, 2.0e-5, 0.0, 0.0));
    InflowControlCompletion a = completion();
    a.setDevice(InflowControlDevice.aicd("AICD", 20, 1.0, dpRef, 2.0, 2.0, 800.0, 0.002));
    InflowControlCompletion p = completion();
    p.setDevice(icd);
    double waterAicd = a.compareAtSameOil(10.0, 200.0).getWaterReduction();
    double waterIcd = p.compareAtSameOil(10.0, 200.0).getWaterReduction();
    assertTrue(waterAicd > waterIcd);
  }

  /** An unreachable oil rate is reported, not hidden. */
  @Test
  void unreachableOilRateIsFlagged() {
    InflowControlCompletion c = completion();
    c.setDevice(InflowControlDevice.aicd("AICD", 20, 1.0, 2.0, 2.0, 0.5, 800.0, 0.002));
    Comparison cmp = c.compareAtSameOil(10.0, 10.5);
    assertFalse(cmp.isOilRateReached());
    assertTrue(cmp.getWithDevice().getOilRate() < cmp.getBare().getOilRate());
  }

  /** Missing setup is rejected. */
  @Test
  void rejectsInvalidSetup() {
    assertThrows(IllegalStateException.class, () -> new InflowControlCompletion("x").solve(1.0, false));
    assertThrows(IllegalStateException.class, () -> completion().solve(1.0, true));
    assertThrows(IllegalArgumentException.class, () -> new InflowControlCompletion("x").addZone("z", 0.0, 0.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> new InflowControlCompletion("x").addZone("z", 1.0, 0.8, 0.5));
  }
}
