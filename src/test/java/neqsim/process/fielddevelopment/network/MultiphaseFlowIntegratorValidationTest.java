package neqsim.process.fielddevelopment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.fielddevelopment.network.MultiphaseFlowIntegrator.PipelineResult;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for failed and non-finite hydraulic screening results. */
class MultiphaseFlowIntegratorValidationTest {
  @Test
  void documentedScreeningWorkflowRemainsExecutableForBothModels() {
    for (MultiphaseFlowIntegrator.HydraulicModel model : MultiphaseFlowIntegrator.HydraulicModel.values()) {
      Stream stream = feed();
      MultiphaseFlowIntegrator integrator = new MultiphaseFlowIntegrator();
      integrator.setHydraulicModel(model);
      integrator.setPipelineLength(1.0);
      integrator.setPipelineDiameter(0.3);
      integrator.setOverallHeatTransferCoeff(0.0);
      integrator.setMinArrivalPressure(1.0);
      PipelineResult result = integrator.calculateHydraulics(stream, 1.0);
      assertTrue(result.isFeasible(), result.getInfeasibilityReason());
      for (PipelineResult point : integrator.calculateHydraulicsCurve(stream.getFluid(), 60.0,
          new double[] { 5000.0, 10000.0 })) {
        assertTrue(point.isFeasible(), point.getInfeasibilityReason());
      }
      double diameter = integrator.sizePipeline(stream, 1.0, 0.8);
      integrator.setPipelineDiameter(diameter);
      PipelineResult sized = integrator.calculateHydraulics(stream, 1.0);
      assertTrue(sized.isFeasible(), sized.getInfeasibilityReason());
      assertTrue(sized.getErosionalVelocityRatio() <= 0.8);
    }
  }

  @Test
  void rejectsUnconvergedTwoFluidProfile() {
    final TwoFluidPipe[] solver = new TwoFluidPipe[1];
    MultiphaseFlowIntegrator integrator = new MultiphaseFlowIntegrator() {
      @Override
      TwoFluidPipe createTwoFluidPipe(Stream inlet) {
        TwoFluidPipe pipe = super.createTwoFluidPipe(inlet);
        pipe.setSteadyStateMaxIterations(1);
        solver[0] = pipe;
        return pipe;
      }
    };
    integrator.setPipelineLength(10.0);
    integrator.setPipelineDiameter(0.1);
    integrator.setNumberOfSegments(5);
    PipelineResult result = integrator.calculateHydraulics(feed(), 1.0);
    assertFalse(solver[0].isSteadyStateConverged(), "Fixture must exhaust the refinement budget");
    assertFalse(result.isFeasible());
    assertTrue(result.getInfeasibilityReason().contains("converg"), result.getInfeasibilityReason());
  }

  @Test
  void rejectsNonFiniteFeasibilityEvidence() throws Exception {
    MultiphaseFlowIntegrator integrator = new MultiphaseFlowIntegrator();
    Method check = MultiphaseFlowIntegrator.class.getDeclaredMethod("checkFeasibility", PipelineResult.class,
        double.class);
    check.setAccessible(true);
    for (int field = 0; field < 4; field++) {
      PipelineResult result = validResult();
      double minimumPressure = 10.0;
      if (field == 0) {
        result.setArrivalPressureBar(Double.NaN);
      } else if (field == 1) {
        result.setArrivalTemperatureC(Double.POSITIVE_INFINITY);
      } else if (field == 2) {
        result.setErosionalVelocityRatio(Double.NaN);
      } else {
        minimumPressure = Double.NaN;
      }
      check.invoke(integrator, result, minimumPressure);
      assertFalse(result.isFeasible(), "Non-finite evidence field " + field);
      assertTrue(result.getInfeasibilityReason().contains("finite"));
    }
    PipelineResult valid = validResult();
    check.invoke(integrator, valid, 10.0);
    assertTrue(valid.isFeasible());
  }

  @Test
  void sizingRejectsAllFailedCandidatesAndRestoresDiameter() {
    MultiphaseFlowIntegrator integrator = sizingStub(false, false);
    integrator.setPipelineDiameter(0.3);
    assertThrows(IllegalStateException.class, () -> integrator.sizePipeline(null, 10.0, 0.8));
    assertEquals(0.3, integrator.getPipelineDiameterM());
  }

  @Test
  void sizingRestoresDiameterAfterCalculationThrows() {
    MultiphaseFlowIntegrator integrator = sizingStub(false, true);
    integrator.setPipelineDiameter(0.3);
    assertThrows(IllegalStateException.class, () -> integrator.sizePipeline(null, 10.0, 0.8));
    assertEquals(0.3, integrator.getPipelineDiameterM());
  }

  @Test
  void sizingSelectsFirstPassingCandidateAndAcceptsLimitEquality() {
    MultiphaseFlowIntegrator integrator = sizingStub(true, false);
    integrator.setPipelineDiameter(0.3);
    assertEquals(0.254, integrator.sizePipeline(null, 10.0, 0.8));
    assertEquals(0.3, integrator.getPipelineDiameterM());
  }

  @Test
  void sizingRejectsInvalidVelocityLimit() {
    MultiphaseFlowIntegrator integrator = sizingStub(true, false);
    for (double limit : new double[] { 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY }) {
      assertThrows(IllegalArgumentException.class, () -> integrator.sizePipeline(null, 10.0, limit));
    }
  }

  private static MultiphaseFlowIntegrator sizingStub(final boolean succeeds, final boolean throwsException) {
    return new MultiphaseFlowIntegrator() {
      @Override
      public PipelineResult calculateHydraulics(StreamInterface inlet, double arrivalPressureBar) {
        if (throwsException) {
          throw new IllegalStateException("Synthetic solver failure");
        }
        PipelineResult result = validResult();
        result.setFeasible(succeeds && getPipelineDiameterM() >= 0.254);
        result.setErosionalVelocityRatio(0.8);
        return result;
      }
    };
  }

  private static PipelineResult validResult() {
    PipelineResult result = new PipelineResult();
    result.setArrivalPressureBar(40.0);
    result.setArrivalTemperatureC(50.0);
    result.setErosionalVelocityRatio(0.5);
    return result;
  }

  private static Stream feed() {
    SystemSrkEos fluid = new SystemSrkEos(323.15, 60.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream stream = new Stream("feed", fluid);
    stream.setFlowRate(10000.0, "kg/hr");
    stream.run();
    return stream;
  }
}
