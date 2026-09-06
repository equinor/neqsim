package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe.OLGAModelType;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;

/** Verifies the gas-to-liquid velocity convention used by the annular holdup closures. */
class TwoFluidPipeAnnularSlipTest {
  @Test
  void annularSlipClosureRecoversGasToLiquidVelocityRatio() throws Exception {
    checkAnnularSlip(false);
  }

  @Test
  void annularFilmClosurePreservesItsMinimumGasToLiquidVelocityRatio() throws Exception {
    checkAnnularSlip(true);
  }

  private void checkAnnularSlip(boolean filmModel) throws Exception {
    TwoFluidPipe pipe = new TwoFluidPipe("annular-slip");
    pipe.setDiameter(0.3);
    pipe.setOLGAModelType(OLGAModelType.FULL);
    pipe.setEnableAnnularFilmModel(filmModel);
    pipe.setEnableTerrainTracking(false);
    pipe.setEnforceMinimumSlip(false);

    TwoFluidSection section = new TwoFluidSection(0.0, 1.0, 0.3, 0.0);
    section.setGasDensity(40.0);
    section.setLiquidDensity(700.0);
    section.setGasViscosity(1.2e-5);
    section.setLiquidViscosity(1.0e-3);
    section.setSurfaceTension(0.025);
    section.setFlowRegime(FlowRegime.ANNULAR);

    Method closure = TwoFluidPipe.class.getDeclaredMethod("calculateLocalHoldup", TwoFluidSection.class,
        TwoFluidSection.class, double.class, double.class, double.class);
    closure.setAccessible(true);

    double[] superficialGasVelocities = { 4.0, 8.0, 16.0, 32.0 };
    double[] expectedSlipRatios = { 1.65625, 2.125, 4.0, 4.0 };
    for (int gasCase = 0; gasCase < superficialGasVelocities.length; gasCase++) {
      double gasSuperficialVelocity = superficialGasVelocities[gasCase];
      for (double noSlipLiquidFraction : new double[] { 0.01, 0.1, 0.3 }) {
        double liquidSuperficialVelocity = gasSuperficialVelocity * noSlipLiquidFraction / (1.0 - noSlipLiquidFraction);
        double[] holdup = (double[]) closure.invoke(pipe, section, null,
            gasSuperficialVelocity * section.getGasDensity() * section.getArea(),
            liquidSuperficialVelocity * section.getLiquidDensity() * section.getArea(), section.getArea());

        double gasVelocity = gasSuperficialVelocity / holdup[1];
        double liquidVelocity = liquidSuperficialVelocity / holdup[0];
        double recoveredSlip = gasVelocity / liquidVelocity;
        assertTrue(Double.isFinite(recoveredSlip));
        assertTrue(gasVelocity > liquidVelocity, "The gas core must outrun the annular liquid film");
        if (filmModel) {
          assertTrue(recoveredSlip + 1.0e-12 >= expectedSlipRatios[gasCase],
              "The annular film closure must respect its stated gas-to-liquid slip minimum");
        } else {
          assertEquals(expectedSlipRatios[gasCase], recoveredSlip, 1.0e-12,
              "Phase velocities reconstructed from holdup must recover the specified slip ratio");
        }
      }
    }
  }
}
