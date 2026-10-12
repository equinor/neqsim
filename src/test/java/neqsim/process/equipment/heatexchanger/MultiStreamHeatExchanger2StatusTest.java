package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression coverage for exchanger diagnostics and strict-mode results.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class MultiStreamHeatExchanger2StatusTest {
  /** Verifies that specified outlets do not imply a satisfied energy balance. */
  @Test
  void fixedOutletsMustSatisfyEnergyBalance() {
    MultiStreamHeatExchanger2 exchanger = fixedOutletExchanger();
    exchanger.run();

    assertTrue(Math.abs(exchanger.energyDiff()) > 1.0);
    assertEquals(MultiStreamHeatExchanger2.SolverStatus.FALLBACK, exchanger.getSolverStatus());
    assertFalse(exchanger.isSpecificationMet());
    assertTrue(exchanger.getSolverMessage().contains("energy residual"));
  }

  /** Verifies that strict-mode diagnostics describe the published outlet state. */
  @Test
  void strictModePublishesFixedOutletsBeforeThrowing() {
    MultiStreamHeatExchanger2 exchanger = fixedOutletExchanger();
    exchanger.setThrowOnUnmetSpecification(true);
    UUID calculationId = UUID.randomUUID();

    assertThrows(IllegalStateException.class, () -> exchanger.run(calculationId));
    assertEquals(40.0, exchanger.getOutStream(0).getTemperature("C"), 1e-8);
    assertEquals(50.0, exchanger.getOutStream(1).getTemperature("C"), 1e-8);
    assertEquals(calculationId, exchanger.getCalculationIdentifier());
    assertFalse(exchanger.isSpecificationMet());
  }

  /** Verifies that non-finite approach specifications are rejected at the API boundary. */
  @Test
  void rejectsNonFiniteApproach() {
    MultiStreamHeatExchanger2 exchanger = new MultiStreamHeatExchanger2("invalid approach");
    assertThrows(IllegalArgumentException.class, () -> exchanger.setTemperatureApproach(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> exchanger.setTemperatureApproach(Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> exchanger.setTemperatureApproach(-1.0));
  }

  /** Verifies that a known impossible three-unknown problem never enters Newton iteration. */
  @Test
  void infeasibleThreeUnknownCaseStopsBeforeNewton() {
    MultiStreamHeatExchanger2 exchanger = new MultiStreamHeatExchanger2("three unknowns") {
      private static final long serialVersionUID = 1L;

      /** Fails the test if preflight allows an impossible Newton solve. */
      @Override
      public void threeUnknowns() {
        throw new AssertionError("Infeasible three-unknown solve must not be attempted");
      }
    };
    exchanger.addInStreamMSHE(stream("hot", 100.0), "hot", null);
    exchanger.addInStreamMSHE(stream("cold one", 0.0), "cold", null);
    exchanger.addInStreamMSHE(stream("cold two", 10.0), "cold", null);
    exchanger.addInStreamMSHE(stream("fixed cold", 20.0), "cold", 95.0);
    exchanger.setTemperatureApproach(10.0);
    exchanger.setUAvalue(10000.0);

    assertThrows(IllegalStateException.class, exchanger::run);
    assertEquals(MultiStreamHeatExchanger2.SolverStatus.INFEASIBLE, exchanger.getSolverStatus());
    assertFalse(exchanger.isSpecificationMet());
    assertEquals(5.0, exchanger.getMaximumFeasibleApproach(), 1e-8);
  }

  /** Verifies that the one-unknown mode only enforces its active energy equation. */
  @Test
  void oneUnknownDoesNotClaimToEnforceApproach() {
    MultiStreamHeatExchanger2 exchanger = new MultiStreamHeatExchanger2("energy only");
    exchanger.addInStreamMSHE(stream("hot", 100.0), "hot", null);
    exchanger.addInStreamMSHE(stream("cold", 0.0), "cold", 30.0);
    exchanger.setTemperatureApproach(5.0);
    exchanger.setThrowOnUnmetSpecification(true);
    exchanger.run();

    assertEquals(MultiStreamHeatExchanger2.SolverStatus.CONVERGED, exchanger.getSolverStatus());
    assertTrue(exchanger.isSpecificationMet());
    assertEquals(0.0, exchanger.energyDiff(), 1e-3);
    assertTrue(exchanger.getTemperatureApproach() > 50.0);
    assertTrue(Double.isNaN(exchanger.getMaximumFeasibleApproach()));
  }

  /** Verifies that an ordinary two-stream solve is not labelled underdetermined. */
  @Test
  void twoUnknownsWithoutFixedOutletsConvergeNormally() {
    MultiStreamHeatExchanger2 exchanger = new MultiStreamHeatExchanger2("two streams");
    exchanger.addInStreamMSHE(stream("hot", 100.0), "hot", null);
    exchanger.addInStreamMSHE(stream("cold", 0.0), "cold", null);
    exchanger.setTemperatureApproach(5.0);
    exchanger.run();

    assertEquals(MultiStreamHeatExchanger2.SolverStatus.CONVERGED, exchanger.getSolverStatus());
    assertEquals(0.0, exchanger.energyDiff(), 1e-3);
    assertEquals(5.0, exchanger.getTemperatureApproach(), 1e-3);
  }

  /** Verifies that a non-finite final diagnostic cannot be classified as successful. */
  @Test
  void nonFiniteResidualCannotReportSuccess() {
    MultiStreamHeatExchanger2 exchanger = new MultiStreamHeatExchanger2("non-finite diagnostic") {
      private static final long serialVersionUID = 1L;
      private boolean solved;

      /** Solves the physical state before injecting a failed diagnostic evaluation. */
      @Override
      public void twoUnknowns() {
        super.twoUnknowns();
        solved = true;
      }

      /**
       * Simulates a non-finite final composite-curve evaluation.
       *
       * @return the calculated pinch while solving, then NaN
       */
      @Override
      public double pinch() {
        return solved ? Double.NaN : super.pinch();
      }
    };
    exchanger.addInStreamMSHE(stream("hot", 100.0), "hot", null);
    exchanger.addInStreamMSHE(stream("cold", 0.0), "cold", null);
    exchanger.run();

    assertEquals(MultiStreamHeatExchanger2.SolverStatus.FALLBACK, exchanger.getSolverStatus());
    assertFalse(exchanger.isSpecificationMet());
  }

  /** Verifies that missing three-unknown specifications produce a useful failure state. */
  @Test
  void missingUaReportsFailedAndDoesNotMarkCalculationComplete() {
    MultiStreamHeatExchanger2 exchanger = new MultiStreamHeatExchanger2("missing UA");
    exchanger.addInStreamMSHE(stream("hot", 100.0), "hot", null);
    exchanger.addInStreamMSHE(stream("cold one", 0.0), "cold", null);
    exchanger.addInStreamMSHE(stream("cold two", 10.0), "cold", null);
    UUID calculationId = UUID.randomUUID();

    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> exchanger.run(calculationId));
    assertTrue(failure.getMessage().contains("positive UA"));
    assertEquals(MultiStreamHeatExchanger2.SolverStatus.FAILED, exchanger.getSolverStatus());
    assertFalse(exchanger.isSpecificationMet());
    assertFalse(calculationId.equals(exchanger.getCalculationIdentifier()));
  }

  /** Verifies the cold-end upper bound imposed by a fixed hot outlet. */
  @Test
  void fixedHotOutletLimitsApproach() {
    MultiStreamHeatExchanger2 exchanger = new MultiStreamHeatExchanger2("fixed hot limit");
    exchanger.addInStreamMSHE(stream("hot", 100.0), "hot", null);
    exchanger.addInStreamMSHE(stream("cold", 0.0), "cold", null);
    exchanger.addInStreamMSHE(stream("fixed hot", 80.0), "hot", 30.0);
    exchanger.setTemperatureApproach(40.0);
    exchanger.run();

    assertEquals(30.0, exchanger.getMaximumFeasibleApproach(), 1e-8);
    assertEquals(MultiStreamHeatExchanger2.SolverStatus.INFEASIBLE, exchanger.getSolverStatus());
    assertFalse(exchanger.isSpecificationMet());
    assertEquals(0.0, exchanger.energyDiff(), 1e-3);
    assertTrue(exchanger.getSolverMessage().contains("fixed outlet of hot stream"));
  }

  /**
   * Builds a deliberately unbalanced fixed-outlet exchanger.
   *
   * @return exchanger with finite but inconsistent heat duties
   */
  private MultiStreamHeatExchanger2 fixedOutletExchanger() {
    MultiStreamHeatExchanger2 exchanger = new MultiStreamHeatExchanger2("fixed outlets");
    exchanger.addInStreamMSHE(stream("hot", 100.0), "hot", 40.0);
    exchanger.addInStreamMSHE(stream("cold", 0.0), "cold", 50.0);
    return exchanger;
  }

  /**
   * Creates a reproducible synthetic nitrogen stream.
   *
   * @param name stream name
   * @param temperature inlet temperature in Celsius
   * @return flashed stream at 10 bara and 5000 kg/h
   */
  private Stream stream(String name, double temperature) {
    SystemSrkEos fluid = new SystemSrkEos(temperature + 273.15, 10.0);
    fluid.addComponent("nitrogen", 1.0);
    fluid.setMixingRule(2);
    Stream stream = new Stream(name, fluid);
    stream.setFlowRate(5000.0, "kg/hr");
    stream.run();
    return stream;
  }
}
