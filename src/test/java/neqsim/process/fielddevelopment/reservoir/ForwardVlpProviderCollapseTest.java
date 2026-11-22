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
}
