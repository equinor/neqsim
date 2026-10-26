package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Synthetic rich-gas depressurization regression through the first appearance of oil.
 *
 * <p>
 * This checks conservative transport only. The synthetic geometry and equilibrium-relaxation source do not qualify
 * blowdown cooling, subsequent outlet backflow, liquid pooling or a full pipeline restart.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
@Tag("slow")
class TwoFluidPhaseAppearancePipelineTest {
  /**
   * Condensation and same-step pressure-correction advection must pass the formerly rejected interval without a seed.
   */
  @Test
  void richGasDepressurizationAdvancesBeyondFirstOilAppearanceWithoutManufacturedInventory() {
    TwoFluidPipe pipe = createRichGasPipe();
    for (TwoFluidSection section : pipe.getSectionSnapshots()) {
      assertEquals(0.0, section.getOilMassPerLength(), 0.0, "Initial oil inventory must be exactly zero");
      assertEquals(0.0, section.getWaterMassPerLength(), 0.0, "The synthetic gas contains no water");
    }

    double sourceOilKg = 0.0;
    double inletOilKg = 0.0;
    double outletOilKg = 0.0;
    double finalOilKg = 0.0;
    int acceptedIntervalsWithOil = 0;
    double stepSeconds = 0.25;
    // The original missing-composition failure occurred during 17.5-17.75 s. At 18 s this fixture
    // encounters the separately unsupported outlet backflow, outside this transport regression.
    for (int step = 1; step <= 71; step++) {
      double elapsedSeconds = step * stepSeconds;
      pipe.setOutletPressure(150.0 - 140.0 * elapsedSeconds / 120.0, "bara");
      try {
        pipe.runTransient(stepSeconds, null);
      } catch (RuntimeException failure) {
        throw new AssertionError("Rich-gas candidate failed at t=" + elapsedSeconds + " s; last accepted t="
            + (elapsedSeconds - stepSeconds) + " s, oil=" + finalOilKg + " kg, integrated oil source=" + sourceOilKg
            + " kg, accepted intervals with oil=" + acceptedIntervalsWithOil, failure);
      }
      assertEquals(elapsedSeconds, pipe.getSimulationTime(), 1e-10);
      assertTrue(pipe.isCoupledPressureMomentumConverged(), "Pressure correction at t=" + elapsedSeconds);

      TwoFluidMassBalanceReport mass = pipe.getLastMassBalanceReport();
      for (Phase phase : Phase.values()) {
        assertTrue(mass.isWithinTolerance(phase, 1e-6, 1e-8),
            phase + " mass balance at t=" + elapsedSeconds + ": residual=" + mass.getResidualKg(phase));
      }
      TwoFluidComponentConservationReport components = pipe.getLastComponentConservationReport();
      assertTrue(components.isConverged(), "Component balance at t=" + elapsedSeconds + ": " + components.getMessage());
      sourceOilKg += mass.getSourceMassKg(Phase.OIL);
      inletOilKg += mass.getInletMassKg(Phase.OIL);
      outletOilKg += mass.getOutletMassKg(Phase.OIL);
      finalOilKg = mass.getFinalMassKg(Phase.OIL);
      assertEquals(sourceOilKg + inletOilKg - outletOilKg, finalOilKg, 1e-6,
          "All oil must be accounted for by accepted physical source and boundary transfers");
      assertEquals(0.0, inletOilKg, 0.0, "The closed inlet cannot supply liquid");
      if (finalOilKg > 1e-8) {
        acceptedIntervalsWithOil++;
      }
      for (TwoFluidSection section : pipe.getSectionSnapshots()) {
        assertTrue(Double.isFinite(section.getOilMassPerLength()) && section.getOilMassPerLength() >= 0.0);
        assertEquals(0.0, section.getWaterMassPerLength(), 0.0);
      }
    }
    assertTrue(finalOilKg > 1e-8, "The rich gas must actually condense in the transient, rather than avoid the defect");
    assertTrue(sourceOilKg > 0.0, "The newly appearing oil must originate from the accepted condensation source");
    assertTrue(acceptedIntervalsWithOil >= 1, "The formerly rejected interval must accept newly appearing oil");
  }

  /**
   * Construct the synthetic 5 km, 12-cell terrain fixture with no initial hydrocarbon liquid.
   *
   * @return initialized pipe with a closed inlet and 150 bara outlet boundary
   */
  private static TwoFluidPipe createRichGasPipe() {
    SystemSrkEos fluid = new SystemSrkEos(277.15, 150.0);
    fluid.addComponent("methane", 0.916);
    fluid.addComponent("ethane", 0.050);
    fluid.addComponent("propane", 0.020);
    fluid.addComponent("n-butane", 0.007);
    fluid.addComponent("n-pentane", 0.003);
    fluid.addComponent("n-heptane", 0.004);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("synthetic rich-gas appearance feed", fluid);
    inlet.setFlowRate(0.001, "kg/sec");
    inlet.run();
    assertEquals(1, inlet.getFluid().getNumberOfPhases());

    TwoFluidPipe pipe = new TwoFluidPipe("synthetic source-funded oil appearance", inlet);
    pipe.setLength(5000.0);
    pipe.setDiameter(42.0 * 0.0254 - 2.0 * 0.026);
    pipe.setRoughness(1e-5);
    pipe.setNumberOfSections(12);
    double[] faceElevationsMetres = new double[13];
    for (int face = 0; face < faceElevationsMetres.length; face++) {
      double firstWave = Math.sin(Math.PI * face / 12.0);
      double thirdWave = Math.sin(3.0 * Math.PI * face / 12.0);
      faceElevationsMetres[face] = -300.0 - 55.0 * firstWave * firstWave - 12.0 * thirdWave * thirdWave;
    }
    pipe.setCellFaceElevationProfile(faceElevationsMetres);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION);
    pipe.setEnableSlugTracking(false);
    pipe.setEnableAdaptiveTimestepping(true);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setEnableInterfacialPressure(true);
    pipe.setImplicitInterfacialPressureCoupling(true);
    pipe.setCflNumber(0.3);
    pipe.setMaximumTransientSubsteps(200);
    pipe.setComponentTransportEnabled(true);
    pipe.setStoreComponentConservationHistory(false);
    pipe.setIncludeMassTransfer(true);
    pipe.setMassTransferRelaxationTime(30.0);
    pipe.setThermodynamicUpdateInterval(1);
    pipe.setIncludeEnergyEquation(false);
    pipe.setEnableJouleThomson(false);
    pipe.setHeatTransferCoefficient(0.0);
    pipe.setSurfaceTemperature(277.15, "K");
    pipe.setOutletPressure(150.0, "bara");
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    pipe.closeInlet();
    pipe.setMassTransferRelaxationTime(30.0);
    return pipe;
  }
}
