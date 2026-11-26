package neqsim.process.safety.depressurization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PsvValveModel}.
 *
 * <p>
 * Validates the pop-action pressure-safety-valve model: the open/reseat hysteresis loop latches and unlatches at the
 * correct pressures, cycle counting increments once per pop, closed valves pass no flow, and the API 520 choked
 * mass-flow magnitude is physically reasonable.
 *
 * @author ESOL
 * @version 1.0
 */
public class PsvValveModelTest {

  /** The valve must open at the set pressure and reseat at set*(1-blowdown). */
  @Test
  public void hysteresisLatchesAndReseats() {
    PsvValveModel psv = new PsvValveModel(50.0e5, 0.1, 0.975, 1.0e-3, 1.0e5);
    assertEquals(50.0e5, psv.getSetPressurePa(), 1.0);
    assertEquals(45.0e5, psv.getReseatPressurePa(), 1.0);

    assertFalse(psv.update(48.0e5), "Below set pressure the valve stays closed");
    assertTrue(psv.update(51.0e5), "At or above set pressure the valve pops open");
    assertTrue(psv.update(46.0e5), "Above reseat the valve stays open (latched)");
    assertFalse(psv.update(44.0e5), "Below reseat the valve closes again");
  }

  /** Each full open/close excursion must count exactly one cycle. */
  @Test
  public void cycleCountIncrementsPerPop() {
    PsvValveModel psv = new PsvValveModel(50.0e5, 0.1, 0.975, 1.0e-3, 1.0e5);
    assertEquals(0, psv.getCycleCount());
    psv.update(51.0e5);
    psv.update(44.0e5);
    psv.update(51.0e5);
    psv.update(44.0e5);
    assertEquals(2, psv.getCycleCount());
  }

  /** A closed valve must pass zero mass flow. */
  @Test
  public void closedValvePassesNoFlow() {
    PsvValveModel psv = new PsvValveModel(50.0e5, 0.1, 0.975, 1.0e-3, 1.0e5);
    psv.update(40.0e5);
    double flow = psv.massFlowKgPerS(40.0e5, 350.0, 0.018, 1.3, 0.95);
    assertEquals(0.0, flow, 0.0);
  }

  /** An open valve must pass a positive, physically reasonable choked flow. */
  @Test
  public void openValvePassesPositiveFlow() {
    PsvValveModel psv = new PsvValveModel(50.0e5, 0.1, 0.975, 1.0e-3, 1.0e5);
    psv.update(55.0e5);
    assertTrue(psv.isOpen());
    double flow = psv.massFlowKgPerS(55.0e5, 350.0, 0.018, 1.3, 0.95);
    assertTrue(flow > 0.0, "Open valve must pass flow");
    assertTrue(flow < 100.0, "Flow magnitude should be physically reasonable");
  }

  /** The mass-flow correlation must reject a heat-capacity ratio at or below unity. */
  @Test
  public void rejectsInvalidGamma() {
    PsvValveModel psv = new PsvValveModel(50.0e5, 0.1, 0.975, 1.0e-3, 1.0e5);
    psv.update(55.0e5);
    assertThrows(IllegalArgumentException.class, () -> psv.massFlowKgPerS(55.0e5, 350.0, 0.018, 1.0, 0.95));
  }

  /** Non-finite gas properties cannot represent a relieving capacity, including when commanded closed. */
  @Test
  public void rejectsNonfiniteFlowProperties() {
    PsvValveModel psv = new PsvValveModel(50.0e5, 0.1, 0.975, 1.0e-3, 1.0e5);
    double[] valid = {55.0e5, 350.0, 0.018, 1.3, 0.95};
    for (int index = 0; index < valid.length; index++) {
      for (double invalid : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
        double[] gas = valid.clone();
        gas[index] = invalid;
        String where = "gas property " + index + " = " + invalid;
        assertThrows(IllegalArgumentException.class,
            () -> psv.fullOpenMassFlowKgPerS(gas[0], gas[1], gas[2], gas[3], gas[4]), where);
        assertThrows(IllegalArgumentException.class, () -> psv.massFlowKgPerS(gas[0], gas[1], gas[2], gas[3], gas[4]),
            where);
        for (double lift : new double[] {0.0, 0.4}) {
          assertThrows(IllegalArgumentException.class,
              () -> psv.massFlowKgPerS(gas[0], gas[1], gas[2], gas[3], gas[4], lift), where);
        }
      }
    }
  }

  /** Non-finite lift commands must be rejected before they can produce a flow. */
  @Test
  public void rejectsNonfiniteLift() {
    PsvValveModel psv = new PsvValveModel(50.0e5, 0.1, 0.975, 1.0e-3, 1.0e5);
    for (double lift : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> psv.massFlowKgPerS(55.0e5, 350.0, 0.018, 1.3, 0.95, lift));
    }
  }

  /** Reset must close the valve and clear the cycle counter. */
  @Test
  public void resetClearsState() {
    PsvValveModel psv = new PsvValveModel(50.0e5, 0.1, 0.975, 1.0e-3, 1.0e5);
    psv.update(55.0e5);
    psv.update(40.0e5);
    psv.reset();
    assertFalse(psv.isOpen());
    assertEquals(0, psv.getCycleCount());
  }

  /** The stateless full-open flow must equal the latched flow once the valve is open, and work while closed. */
  @Test
  public void fullOpenFlowIsStatelessAndMatchesLatchedFlow() {
    PsvValveModel psv = new PsvValveModel(50.0e5, 0.1, 0.975, 1.0e-3, 1.0e5);
    double stateless = psv.fullOpenMassFlowKgPerS(55.0e5, 350.0, 0.018, 1.3, 0.95);
    assertFalse(psv.isOpen(), "The stateless call must not latch the valve");
    psv.update(55.0e5);
    double latched = psv.massFlowKgPerS(55.0e5, 350.0, 0.018, 1.3, 0.95);
    assertTrue(stateless > 0.0);
    assertEquals(latched, stateless, 1.0e-12);
  }

  /** A partially lifted valve passes the lift fraction of the full-open flow; lift is clamped to 0..1. */
  @Test
  public void liftFractionScalesFlow() {
    PsvValveModel psv = new PsvValveModel(50.0e5, 0.1, 0.975, 1.0e-3, 1.0e5);
    double full = psv.fullOpenMassFlowKgPerS(55.0e5, 350.0, 0.018, 1.3, 0.95);
    assertEquals(0.4 * full, psv.massFlowKgPerS(55.0e5, 350.0, 0.018, 1.3, 0.95, 0.4), 1.0e-12 * full);
    assertEquals(0.0, psv.massFlowKgPerS(55.0e5, 350.0, 0.018, 1.3, 0.95, -0.2), 0.0);
    assertEquals(full, psv.massFlowKgPerS(55.0e5, 350.0, 0.018, 1.3, 0.95, 1.7), 1.0e-12 * full);
  }
}
