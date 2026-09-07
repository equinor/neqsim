package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/** Mechanical diagnostics must reproduce the source budget without modifying phase evolution. */
class MomentumForceDiagnosticsTest {
  @Test
  void forceSnapshotClosesTheSourceBudgetAndDoesNotChangeIt() {
    for (double waterCut : new double[] { 0.0, 0.4, 1.0 }) {
      TwoFluidSection section = new TwoFluidSection(0.5, 1.0, 0.1, 0.5);
      section.setGasDensity(10.0);
      section.setOilDensity(800.0);
      section.setWaterDensity(1000.0);
      section.setGasHoldup(0.4);
      section.setLiquidHoldup(0.6);
      section.setWaterCut(waterCut);
      section.setOilHoldup(0.6 * (1.0 - waterCut));
      section.setWaterHoldup(0.6 * waterCut);
      section.setLiquidDensity(800.0 * (1.0 - waterCut) + 1000.0 * waterCut);
      section.setGasVelocity(2.0);
      section.setOilVelocity(-0.2);
      section.setWaterVelocity(-0.2);
      section.setGasWallShear(3.0);
      section.setLiquidWallShear(-2.0);
      section.setInterfacialShear(4.0);
      section.setPressure(2e5);
      section.updateConservativeVariables();
      double[] original = section.getStateVector();
      TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
      equations.setIncludeMassTransfer(false);
      equations.setEnableWaterOilSlip(false);
      TwoFluidSection[] sections = { section };
      double[] before = equations.calcSourceTerms(sections)[0];
      assertEquals(0, equations.getLastMomentumSourceForcesPerLength().length);
      equations.setMomentumForceDiagnosticsEnabled(true);
      double[] after = equations.calcSourceTerms(sections)[0];
      assertArrayEquals(before, after, 0.0);
      assertArrayEquals(original, section.getStateVector(), 0.0);
      double[] forces = equations.getLastMomentumSourceForcesPerLength()[0];
      assertEquals(after[3], forces[0] + forces[2] + forces[4], 1e-12);
      assertEquals(after[4] + after[5], forces[1] + forces[3] + forces[5], 1e-12);
      assertEquals(0.0, forces[2] + forces[3], 0.0);
      double expectedGravity = -(section.getGasHoldup() * section.getGasDensity()
          + section.getLiquidHoldup() * section.getLiquidDensity()) * 9.81 * section.getArea() * Math.sin(0.5);
      assertEquals(expectedGravity, forces[4] + forces[5], 1e-12);
      forces[0] = Double.NaN;
      assertEquals(after[3],
          equations.getLastMomentumSourceForcesPerLength()[0][0]
              + equations.getLastMomentumSourceForcesPerLength()[0][2]
              + equations.getLastMomentumSourceForcesPerLength()[0][4],
          1e-12);
      equations.setMomentumForceDiagnosticsEnabled(false);
      assertEquals(0, equations.getLastMomentumSourceForcesPerLength().length);
    }
  }
}
