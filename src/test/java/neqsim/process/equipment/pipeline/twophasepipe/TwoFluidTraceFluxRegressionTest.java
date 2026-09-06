package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseFlux;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseState;

class TwoFluidTraceFluxRegressionTest {
  @Test
  void positiveTraceHoldupRetainsItsAdvectiveMassAndEnergy() {
    AUSMPlusFluxCalculator calculator = new AUSMPlusFluxCalculator();
    PhaseState state = new PhaseState(1000.0, 2.0, 2.0e5, 1200.0, 1.0e5, 5.0e-11);

    PhaseFlux flux = calculator.calcPhaseFlux(state, state, 1.0);

    assertTrue(flux.massFlux > 0.0, "A positive transported phase cannot be treated as exactly absent");
    assertEquals(1.0e-7, flux.massFlux, 1.0e-17);
    assertEquals(0.01, flux.energyFlux, 1.0e-12);
  }

  @Test
  void uniformTraceWaterHasTheSameMassFluxAtInternalAndExternalFaces() {
    TwoFluidSection cell = new TwoFluidSection(0.5, 1.0, 0.1, 0.0);
    cell.setGasDensity(10.0);
    cell.setOilDensity(800.0);
    cell.setWaterDensity(1000.0);
    cell.setLiquidDensity(1000.0);
    cell.setGasHoldup(1.0 - 5.0e-11);
    cell.setLiquidHoldup(5.0e-11);
    cell.setWaterCut(1.0);
    cell.setGasVelocity(2.0);
    cell.setLiquidVelocity(2.0);
    cell.setGasSoundSpeed(300.0);
    cell.setLiquidSoundSpeed(1200.0);
    cell.setPressure(2.0e5);
    cell.updateConservativeVariables();
    TwoFluidSection next = cell.clone();
    next.setPosition(1.5);
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();

    double[][] flux = equations.calcPhaseMassFaceFluxes(new TwoFluidSection[] { cell, next }, 1.0);

    double expected = cell.getWaterMassPerLength() * 2.0;
    assertTrue(expected > 0.0);
    for (int face = 0; face < flux.length; face++) {
      assertEquals(expected, flux[face][2], 1.0e-18, "Uniform trace transport at face " + face);
    }
  }

  @Test
  void outletMustTransportBothTraceLiquidsWhenGasHoldupRoundsToOne() {
    TwoFluidSection cell = new TwoFluidSection(0.5, 1.0, 0.1, 0.0);
    cell.setGasDensity(10.0);
    cell.setOilDensity(800.0);
    cell.setWaterDensity(1000.0);
    cell.setLiquidDensity(900.0);
    cell.setGasHoldup(1.0);
    cell.setLiquidHoldup(1.0e-17);
    cell.setWaterCut(0.5);
    cell.setGasVelocity(2.0);
    cell.setLiquidVelocity(2.0);
    cell.setGasSoundSpeed(300.0);
    cell.setLiquidSoundSpeed(1200.0);
    cell.setPressure(2.0e5);
    cell.updateConservativeVariables();
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();

    double[][] flux = equations.calcPhaseMassFaceFluxes(new TwoFluidSection[] { cell }, 1.0);

    for (int phase = 1; phase < 3; phase++) {
      double expected = cell.getStateVector()[phase] * 2.0;
      assertTrue(expected > 0.0, "A positive liquid inventory remains below gas-holdup precision");
      assertEquals(expected, flux[0][phase], expected * 1.0e-12);
      assertEquals(expected, flux[1][phase], expected * 1.0e-12);
    }
  }
}
