package neqsim.process.fielddevelopment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests the collapsed-profile handling of {@link ForwardVlpProvider}.
 *
 * @author ESOL
 * @version 1.0
 */
class ForwardVlpProviderCollapseTest {

  /**
   * Pipe that cannot deliver the rate below 16.5 bara inlet: the model fails (NaN) instead of returning a small value.
   */
  private static final ForwardVlpProvider.ForwardPressureModel FAILS_BELOW_THRESHOLD = new ForwardVlpProvider.ForwardPressureModel() {
    @Override
    public double outletPressure(double rate, double inlet, double wr, double gr, double alq) {
      if (inlet < 16.5) {
        return Double.NaN;
      }
      return inlet - 12.0;
    }
  };

  @Test
  void defaultKeepsTheFirstFailureAsNaN() {
    ForwardVlpProvider provider = new ForwardVlpProvider(FAILS_BELOW_THRESHOLD).setInletBounds(2.0, 450.0);
    assertTrue(Double.isNaN(provider.bottomHolePressure(1000.0, 5.0, 0.0, 0.0, 0.0)));
    assertEquals(0, provider.getCollapsedEvaluations());
  }

  @Test
  void collapseDropMakesTheSolveReachTheRealRoot() {
    ForwardVlpProvider provider = new ForwardVlpProvider(FAILS_BELOW_THRESHOLD).setInletBounds(2.0, 450.0)
        .setCollapsedProfileDrop(30.0);
    double inlet = provider.bottomHolePressure(1000.0, 5.0, 0.0, 0.0, 0.0);
    assertEquals(17.0, inlet, 0.1,
        "inlet = outlet 5 + 12 bar drop; the first guess (16 bara) is below the failing threshold and collapsed");
    assertTrue(provider.getCollapsedEvaluations() >= 1);
  }

  /** A collapse-to-flow transition cannot locate an outlet pressure below the first feasible value. */
  @Test
  void collapseBoundaryDoesNotSupplyADiscontinuousRoot() {
    ForwardVlpProvider provider = new ForwardVlpProvider(FAILS_BELOW_THRESHOLD).setCollapsedProfileDrop(30.0);
    double inlet = provider.bottomHolePressure(1000.0, 4.0, 0.0, 0.0, 0.0);
    assertTrue(Double.isNaN(inlet), "The lowest feasible outlet is 4.5 bara; returned inlet " + inlet);
    assertTrue(provider.getCollapsedEvaluations() > 0);
    assertEquals(0, provider.getDiscontinuousRoots());
  }

  /** A wide bracket tolerance must allow the search to continue past a synthetic lower endpoint to a real root. */
  @Test
  void wideBracketWithACollapsedEndpointStillFindsAReachableTarget() {
    ForwardVlpProvider.ForwardPressureModel model = (rate, inlet, wr, gr, alq) -> {
      if (inlet < 17.0) {
        return Double.NaN;
      }
      return inlet < 18.0 ? 5.0 : inlet - 12.0;
    };
    ForwardVlpProvider provider = new ForwardVlpProvider(model).setCollapsedProfileDrop(30.0).setBracketTolerance(25.0);
    double inlet = provider.bottomHolePressure(1000.0, 5.0, 0.0, 0.0, 0.0);
    assertEquals(5.0, model.outletPressure(1000.0, inlet, 0.0, 0.0, 0.0), 0.02);
    assertEquals(0, provider.getDiscontinuousRoots());
  }

  /** Even a large pressure tolerance cannot accept a failed model evaluation as a normal root. */
  @Test
  void syntheticResidualInsideToleranceIsNeverAccepted() {
    ForwardVlpProvider provider = new ForwardVlpProvider(FAILS_BELOW_THRESHOLD).setCollapsedProfileDrop(1.0)
        .setTolerance(2.0);
    double inlet = provider.bottomHolePressure(1000.0, 4.0, 0.0, 0.0, 0.0);
    assertTrue(inlet >= 16.5 && Math.abs((inlet - 12.0) - 4.0) < 2.0, "Returned inlet " + inlet);
    assertEquals(0, provider.getDiscontinuousRoots());
  }

  /** Numerical overflow in the forward model cannot supply the upper endpoint of a discontinuity. */
  @Test
  void infiniteOutletCannotEstablishAJump() {
    ForwardVlpProvider provider = new ForwardVlpProvider(
        (rate, inlet, wr, gr, alq) -> inlet < 30.0 ? inlet - 5.0 : Double.POSITIVE_INFINITY);
    assertTrue(Double.isNaN(provider.bottomHolePressure(1000.0, 26.0, 0.0, 0.0, 0.0)));
    assertEquals(0, provider.getDiscontinuousRoots());
  }

  @Test
  void aRealNoFlowRegionStillEndsAsNaN() {
    ForwardVlpProvider provider = new ForwardVlpProvider(new ForwardVlpProvider.ForwardPressureModel() {
      @Override
      public double outletPressure(double rate, double inlet, double wr, double gr, double alq) {
        return Double.NaN;
      }
    }).setInletBounds(2.0, 450.0).setCollapsedProfileDrop(30.0);
    assertTrue(Double.isNaN(provider.bottomHolePressure(1000.0, 5.0, 0.0, 0.0, 0.0)));
    assertTrue(provider.getCollapsedEvaluations() > 1);
  }

  /** A forward model that jumps across the requested outlet pressure returns the jump location, not NaN. */
  @Test
  void aJumpAcrossTheTargetReturnsTheJumpLocation() {
    ForwardVlpProvider provider = new ForwardVlpProvider(new ForwardVlpProvider.ForwardPressureModel() {
      @Override
      public double outletPressure(double rate, double inlet, double wr, double gr, double alq) {
        return inlet < 30.0 ? inlet - 5.0 : inlet - 3.0;
      }
    }).setInletBounds(2.0, 450.0);
    assertEquals(30.0, provider.bottomHolePressure(1000.0, 26.0, 0.0, 0.0, 0.0), 1.0e-3);
    assertEquals(1, provider.getDiscontinuousRoots());
    assertEquals(25.0, provider.bottomHolePressure(1000.0, 20.0, 0.0, 0.0, 0.0), 0.05);
    assertEquals(1, provider.getDiscontinuousRoots());
    provider.setBracketTolerance(0.0);
    assertTrue(Double.isNaN(provider.bottomHolePressure(1000.0, 26.0, 0.0, 0.0, 0.0)));
  }
}
