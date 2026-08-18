package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TwoFluidOutletBoundaryTest {
  @Test
  void signedOutletModeCarriesReversedPhaseMassInsteadOfClampingIt() {
    TwoFluidSection outlet = new TwoFluidSection(0.5, 1.0, 1.0, 0.0);
    double area = outlet.getArea();
    outlet.setPressure(5.0e6);
    outlet.setGasDensity(10.0);
    outlet.setOilDensity(800.0);
    outlet.setWaterDensity(1000.0);
    outlet.setGasHoldup(0.5);
    outlet.setOilHoldup(0.5);
    outlet.setWaterHoldup(0.0);
    outlet.setLiquidHoldup(0.5);
    outlet.setGasVelocity(1.0);
    outlet.setOilVelocity(-0.25);
    outlet.setWaterVelocity(0.0);
    outlet.setGasMassPerLength(0.5 * 10.0 * area);
    outlet.setOilMassPerLength(0.5 * 800.0 * area);
    outlet.setWaterMassPerLength(0.0);

    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    double[][] clamped = equations.calcPhaseMassFaceFluxes(new TwoFluidSection[] { outlet }, 1.0);
    assertEquals(0.0, clamped[1][1], 0.0);
    assertTrue(equations.isOutletBackflowClamped());

    equations.clearOutletBackflowClamped();
    equations.setAllowOutletPhaseBackflow(true);
    double[][] signed = equations.calcPhaseMassFaceFluxes(new TwoFluidSection[] { outlet }, 1.0);

    assertEquals(-0.25 * 0.5 * 800.0 * area, signed[1][1], 1.0e-12);
    assertTrue(signed[1][0] > 0.0);
    assertFalse(equations.isOutletBackflowClamped());
  }
}
