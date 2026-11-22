package neqsim.process.fielddevelopment.tieback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HostSynergyScreening}.
 *
 * @author ESOL
 * @version 1.0
 */
class HostSynergyScreeningTest {

  @Test
  void demandThatFitsHasNoBindingYear() {
    HostSynergyScreening.UllageCover c = HostSynergyScreening.ullageCover(new double[] {10.0, 20.0, 15.0},
        new double[] {12.0, 25.0, 15.0});
    assertTrue(c.fits());
    assertEquals(-1, c.getFirstBindingYearIndex());
    assertEquals(1.0, c.getMinCoverRatio(), 1e-12);
    assertEquals(0.0, c.getUnmetDemandTotal(), 1e-12);
    assertEquals(1.0, c.getFractionServed(), 1e-12);
  }

  @Test
  void bindingYearAndUnmetDemandAreReported() {
    HostSynergyScreening.UllageCover c = HostSynergyScreening.ullageCover(new double[] {10.0, 30.0, 15.0},
        new double[] {12.0, 25.0, 20.0});
    assertFalse(c.fits());
    assertEquals(1, c.getFirstBindingYearIndex());
    assertEquals(5.0, c.getUnmetDemandTotal(), 1e-12);
    assertEquals(25.0 / 30.0, c.getMinCoverRatio(), 1e-12);
    assertEquals(1.0 - 5.0 / 55.0, c.getFractionServed(), 1e-12);
  }

  @Test
  void synergyIsOnlyCreditedWhenTheDemandFits() {
    HostSynergyScreening.UllageCover fit = HostSynergyScreening.ullageCover(new double[] {5.0, 5.0},
        new double[] {6.0, 6.0});
    HostSynergyScreening.UllageCover tight = HostSynergyScreening.ullageCover(new double[] {5.0, 9.0},
        new double[] {6.0, 6.0});
    assertEquals(80.0, HostSynergyScreening.synergyValue(200.0, 120.0, fit), 1e-12);
    assertEquals(0.0, HostSynergyScreening.synergyValue(200.0, 120.0, tight), 1e-12);
  }

  @Test
  void invalidInputIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> HostSynergyScreening.ullageCover(new double[] {1.0}, new double[] {1.0, 2.0}));
    assertThrows(IllegalArgumentException.class, () -> HostSynergyScreening.ullageCover(new double[0], new double[0]));
  }
}
