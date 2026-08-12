package neqsim.process.equipment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InflowPerformance}, the liquid inflow performance relationships.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class InflowPerformanceTest {
  /** Reservoir pressure used throughout, bara. */
  private static final double RESERVOIR_PRESSURE = 70.68;
  /** Bubble point pressure used throughout, bara. */
  private static final double BUBBLE_POINT = 66.94;
  /** Productivity index used throughout, Sm3/(day.bar). */
  private static final double PRODUCTIVITY_INDEX = 200.0;

  /** A linear inflow relationship must reproduce the straight line it is defined by. */
  @Test
  void testLinearRateIsProportionalToDrawdown() {
    InflowPerformance ipr = InflowPerformance.linear(PRODUCTIVITY_INDEX, RESERVOIR_PRESSURE);
    assertEquals(PRODUCTIVITY_INDEX * 10.0, ipr.rate(RESERVOIR_PRESSURE - 10.0), 1.0e-9);
    assertEquals(0.0, ipr.rate(RESERVOIR_PRESSURE), 1.0e-9);
    assertEquals(PRODUCTIVITY_INDEX * RESERVOIR_PRESSURE, ipr.absoluteOpenFlow(), 1.0e-9);
  }

  /** Rate and pressure must be exact inverses of each other for every model. */
  @Test
  void testRateAndPressureAreInverses() {
    InflowPerformance[] models = new InflowPerformance[] {
        InflowPerformance.linear(PRODUCTIVITY_INDEX, RESERVOIR_PRESSURE),
        InflowPerformance.vogel(PRODUCTIVITY_INDEX, RESERVOIR_PRESSURE),
        InflowPerformance.composite(PRODUCTIVITY_INDEX, RESERVOIR_PRESSURE, BUBBLE_POINT) };
    for (InflowPerformance ipr : models) {
      for (double fraction = 0.05; fraction < 1.0; fraction += 0.05) {
        double rate = ipr.absoluteOpenFlow() * fraction;
        double pwf = ipr.bottomHolePressure(rate);
        assertEquals(rate, ipr.rate(pwf), 1.0e-6,
            "model " + ipr.getModel() + " is not self-consistent at " + rate + " Sm3/day");
      }
    }
  }

  /** Vogel's curve must give its textbook maximum rate and be monotonic. */
  @Test
  void testVogelMaximumRateAndMonotonicity() {
    InflowPerformance ipr = InflowPerformance.vogel(PRODUCTIVITY_INDEX, RESERVOIR_PRESSURE);
    assertEquals(PRODUCTIVITY_INDEX * RESERVOIR_PRESSURE / 1.8, ipr.absoluteOpenFlow(), 1.0e-9);
    double previous = -1.0;
    for (double pwf = RESERVOIR_PRESSURE; pwf >= 0.0; pwf -= 5.0) {
      double rate = ipr.rate(pwf);
      assertTrue(rate > previous, "Vogel rate must increase as the drawdown increases");
      previous = rate;
    }
  }

  /**
   * The composite model must follow the straight line above the bubble point and fall below it afterwards. A linear
   * model used below the bubble point is optimistic, and that is the whole reason this class exists.
   */
  @Test
  void testCompositeMatchesLinearAboveBubblePointAndIsLowerBelow() {
    InflowPerformance linear = InflowPerformance.linear(PRODUCTIVITY_INDEX, RESERVOIR_PRESSURE);
    InflowPerformance composite = InflowPerformance.composite(PRODUCTIVITY_INDEX, RESERVOIR_PRESSURE, BUBBLE_POINT);

    double aboveBubblePoint = BUBBLE_POINT + 2.0;
    assertEquals(linear.rate(aboveBubblePoint), composite.rate(aboveBubblePoint), 1.0e-9);

    double belowBubblePoint = BUBBLE_POINT - 20.0;
    assertTrue(composite.rate(belowBubblePoint) < linear.rate(belowBubblePoint),
        "the composite curve must bend below the straight line once free gas appears");
    assertTrue(composite.absoluteOpenFlow() < linear.absoluteOpenFlow(),
        "the composite absolute open flow must be lower than the linear one");
  }

  /** A composite model on an already saturated reservoir must reduce to Vogel. */
  @Test
  void testCompositeReducesToVogelWhenSaturated() {
    InflowPerformance vogel = InflowPerformance.vogel(PRODUCTIVITY_INDEX, RESERVOIR_PRESSURE);
    InflowPerformance composite = InflowPerformance.composite(PRODUCTIVITY_INDEX, RESERVOIR_PRESSURE,
        RESERVOIR_PRESSURE);
    for (double pwf = 0.0; pwf <= RESERVOIR_PRESSURE; pwf += 7.0) {
      assertEquals(vogel.rate(pwf), composite.rate(pwf), 1.0e-9);
    }
  }

  /** Free gas at the sandface must be flagged exactly at the bubble point. */
  @Test
  void testFreeGasFlag() {
    InflowPerformance ipr = InflowPerformance.composite(PRODUCTIVITY_INDEX, RESERVOIR_PRESSURE, BUBBLE_POINT);
    assertFalse(ipr.hasFreeGasAtSandface(BUBBLE_POINT + 0.1));
    assertTrue(ipr.hasFreeGasAtSandface(BUBBLE_POINT - 0.1));
  }

  /**
   * The radial productivity index must agree with the familiar field-unit Darcy expression 0.00708 k h / (mu B
   * ln(re/rw) - 0.75) after unit conversion.
   */
  @Test
  void testRadialProductivityIndexAgainstFieldUnitDarcy() {
    double permeability = 100.0;
    double netPayMetre = 30.0;
    double drainageRadius = 500.0;
    double wellboreRadius = 0.1;
    double viscosity = 1.5;
    double formationVolumeFactor = 1.2;

    double si = InflowPerformance.radialProductivityIndex(permeability, netPayMetre, drainageRadius, wellboreRadius,
        viscosity, formationVolumeFactor, 0.0);

    double netPayFeet = netPayMetre / 0.3048;
    double fieldUnits = 0.00708 * permeability * netPayFeet
        / (viscosity * formationVolumeFactor * (Math.log(drainageRadius / wellboreRadius) - 0.75));
    // STB/(day.psi) converted to Sm3/(day.bar)
    double converted = fieldUnits * 14.5038 / 6.2898;

    assertEquals(converted, si, converted * 0.005,
        "the SI Darcy constant must match the field-unit expression to better than 0.5 %");
  }

  /**
   * The Joshi productivity index must exceed the radial one for the same rock, and must fall as the vertical
   * permeability is reduced.
   */
  @Test
  void testJoshiProductivityIndexBehaviour() {
    double permeability = 2000.0;
    double netPay = 45.0;
    double drainLength = 1500.0;
    double drainageRadius = 800.0;
    double wellboreRadius = 0.108;
    double viscosity = 3.0;
    double formationVolumeFactor = 1.1224;

    double horizontal = InflowPerformance.joshiProductivityIndex(permeability, netPay, drainLength, drainageRadius,
        wellboreRadius, viscosity, formationVolumeFactor, 0.3, 0.0);
    double vertical = InflowPerformance.radialProductivityIndex(permeability, netPay, drainageRadius, wellboreRadius,
        viscosity, formationVolumeFactor, 0.0);
    assertTrue(horizontal > vertical, "a 1500 m horizontal drain must out-produce a vertical well in the same rock");

    double poorVertical = InflowPerformance.joshiProductivityIndex(permeability, netPay, drainLength, drainageRadius,
        wellboreRadius, viscosity, formationVolumeFactor, 0.05, 0.0);
    assertTrue(poorVertical < horizontal, "reducing kv/kh must reduce the horizontal productivity index");

    double damaged = InflowPerformance.joshiProductivityIndex(permeability, netPay, drainLength, drainageRadius,
        wellboreRadius, viscosity, formationVolumeFactor, 0.3, 5.0);
    assertTrue(damaged < horizontal, "a positive skin must reduce the productivity index");
  }

  /** A Joshi-derived inflow object must keep its inputs and behave as a composite curve. */
  @Test
  void testJoshiInflowObject() {
    InflowPerformance ipr = InflowPerformance.joshiHorizontal(2000.0, 45.0, 1500.0, 800.0, 0.108, 3.0, 1.1224, 0.3, 2.0,
        RESERVOIR_PRESSURE, BUBBLE_POINT);
    assertEquals(InflowPerformance.Model.JOSHI_HORIZONTAL, ipr.getModel());
    assertNotNull(ipr.getJoshiInputs());
    assertEquals(9, ipr.getJoshiInputs().length);
    assertTrue(ipr.getProductivityIndex() > 0.0);
    double rate = 3000.0;
    assertEquals(rate, ipr.rate(ipr.bottomHolePressure(rate)), 1.0e-6);
  }

  /** The reservoir pressure must be settable so one object can be re-used as the field depletes. */
  @Test
  void testDepletionReducesDeliverability() {
    InflowPerformance ipr = InflowPerformance.composite(PRODUCTIVITY_INDEX, RESERVOIR_PRESSURE, BUBBLE_POINT);
    double initial = ipr.absoluteOpenFlow();
    ipr.setReservoirPressure(RESERVOIR_PRESSURE - 20.0);
    assertTrue(ipr.absoluteOpenFlow() < initial,
        "a depleted reservoir must deliver less at the same productivity index");
  }

  /** The inflow curve must run from the reservoir pressure down to zero. */
  @Test
  void testCurve() {
    InflowPerformance ipr = InflowPerformance.composite(PRODUCTIVITY_INDEX, RESERVOIR_PRESSURE, BUBBLE_POINT);
    List<double[]> curve = ipr.curve(11);
    assertEquals(11, curve.size());
    assertEquals(RESERVOIR_PRESSURE, curve.get(0)[0], 1.0e-9);
    assertEquals(0.0, curve.get(0)[1], 1.0e-9);
    assertEquals(0.0, curve.get(curve.size() - 1)[0], 1.0e-9);
    assertEquals(ipr.absoluteOpenFlow(), curve.get(curve.size() - 1)[1], 1.0e-9);
  }

  /** Invalid inputs must be rejected rather than producing a silently wrong productivity index. */
  @Test
  void testInvalidInputsAreRejected() {
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        InflowPerformance.linear(-1.0, RESERVOIR_PRESSURE);
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        InflowPerformance.linear(PRODUCTIVITY_INDEX, 0.0);
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        InflowPerformance.radialProductivityIndex(100.0, 30.0, 0.05, 0.1, 1.0, 1.0, 0.0);
      }
    });
  }
}
