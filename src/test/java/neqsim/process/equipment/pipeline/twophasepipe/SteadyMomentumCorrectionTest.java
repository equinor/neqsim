package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.Test;

/** The steady-consistency momentum correction must hold the steady state and only while a cell keeps its regime. */
class SteadyMomentumCorrectionTest {
  private static final int[] MOMENTUM = {3, 4, 5};

  private static TwoFluidSection[] line(double gasVelocity, double liquidHoldup, double liquidVelocity) {
    TwoFluidSection[] sections = new TwoFluidSection[4];
    for (int i = 0; i < sections.length; i++) {
      TwoFluidSection s = new TwoFluidSection(10.0 * i, 10.0, 0.2, 0.0);
      s.setGasDensity(60.0);
      s.setOilDensity(700.0);
      s.setWaterDensity(1000.0);
      s.setLiquidDensity(700.0);
      s.setGasViscosity(1.3e-5);
      s.setLiquidViscosity(5.0e-4);
      s.setOilViscosity(5.0e-4);
      s.setSurfaceTension(0.015);
      s.setGasHoldup(1.0 - liquidHoldup);
      s.setLiquidHoldup(liquidHoldup);
      s.setOilHoldup(liquidHoldup);
      s.setWaterHoldup(0.0);
      s.setWaterCut(0.0);
      s.setGasVelocity(gasVelocity);
      s.setOilVelocity(liquidVelocity);
      s.setWaterVelocity(liquidVelocity);
      s.setLiquidVelocity(liquidVelocity);
      s.setPressure(50.0e5 - 100.0 * i);
      s.updateDerivedQuantities();
      s.updateConservativeVariables();
      sections[i] = s;
    }
    return sections;
  }

  private static TwoFluidConservationEquations equations() {
    TwoFluidConservationEquations eq = new TwoFluidConservationEquations();
    eq.setIncludeMassTransfer(false);
    return eq;
  }

  @Test
  void correctionHoldsCalibratedStateAndDropsWhenRegimeChanges() {
    TwoFluidConservationEquations corrected = equations();
    TwoFluidSection[] calibration = line(3.0, 0.2, 0.5);
    corrected.calibrateSteadyMomentumCorrection(line(3.0, 0.2, 0.5), 10.0);
    double[][] held = corrected.calcRHS(calibration, 10.0);
    for (int cell = 0; cell < held.length; cell++) {
      assertEquals(0.0, held[cell][MOMENTUM[0]], 1.0e-6, "gas momentum rate at the calibrated state, cell " + cell);
      assertEquals(0.0, held[cell][MOMENTUM[1]], 1.0e-6, "oil momentum rate at the calibrated state, cell " + cell);
    }
    PipeSection.FlowRegime calibratedRegime = calibration[1].getFlowRegime();

    TwoFluidSection[] disturbed = line(8.0, 0.6, 3.0);
    double[][] withCorrection = corrected.calcRHS(disturbed, 10.0);
    double[][] without = equations().calcRHS(line(8.0, 0.6, 3.0), 10.0);
    assertNotEquals(calibratedRegime, disturbed[1].getFlowRegime(),
        "fixture must change the regime for the gate to be exercised");
    for (int cell = 0; cell < disturbed.length; cell++) {
      if (disturbed[cell].getFlowRegime() == calibration[cell].getFlowRegime()) {
        continue;
      }
      for (int index : MOMENTUM) {
        assertEquals(without[cell][index], withCorrection[cell][index], 1.0e-9 * (1.0 + Math.abs(without[cell][index])),
            "a cell that left its calibration regime must not carry the calibrated force, cell " + cell);
      }
    }
  }
}
