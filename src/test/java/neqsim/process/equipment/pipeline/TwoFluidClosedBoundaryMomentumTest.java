package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** A closed external face cannot instantaneously remove an adjacent finite-volume cell's momentum. */
class TwoFluidClosedBoundaryMomentumTest {
  @ParameterizedTest
  @EnumSource(value = TimeIntegrator.Method.class, names = { "EULER", "RK4" })
  void coupledClosedFacesRetainCellInertiaAndBlockBoundaryMass(TimeIntegrator.Method method) {
    SystemInterface fluid = new SystemSrkEos(288.15, 70.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("nitrogen", 0.05);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(5.0, "kg/sec");
    inlet.run();
    TwoFluidPipe pipe = new TwoFluidPipe("closed pipe", inlet);
    pipe.setLength(20.0);
    pipe.setDiameter(0.20);
    pipe.setNumberOfSections(4);
    pipe.setTimeIntegrationMethod(method);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    double[] initialVelocity = pipe.getGasVelocityProfile();
    pipe.closeInlet();
    pipe.closeOutlet();

    pipe.runTransient(1.0e-8, UUID.randomUUID());

    double[] finalVelocity = pipe.getGasVelocityProfile();
    for (int cell : new int[] { 0, finalVelocity.length - 1 }) {
      assertTrue(initialVelocity[cell] > 0.0);
      assertEquals(initialVelocity[cell], finalVelocity[cell], initialVelocity[cell] * 1.0e-4,
          "A finite pressure impulse over 1e-8 s must not erase a whole boundary cell's momentum");
    }
    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    assertEquals(0.0, report.getInletMassKg(Phase.TOTAL), 0.0);
    assertEquals(0.0, report.getOutletMassKg(Phase.TOTAL), 0.0);
    assertTrue(report.isWithinTolerance(Phase.TOTAL, 1.0e-10, 1.0e-12));
  }
}
