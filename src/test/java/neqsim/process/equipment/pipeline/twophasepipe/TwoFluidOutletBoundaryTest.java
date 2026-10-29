package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Signed outlet transport and phase-consistent pressure traction.
 *
 * @author NeqSim
 * @version 1.0
 */
class TwoFluidOutletBoundaryTest {
  /** Reversed advection must retain its signed mass flux. */
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
    double[][] clamped = equations.calcPhaseMassFaceFluxes(new TwoFluidSection[] {outlet}, 1.0);
    assertEquals(0.0, clamped[1][1], 0.0);
    assertTrue(equations.isOutletBackflowClamped());

    equations.clearOutletBackflowClamped();
    equations.setAllowOutletPhaseBackflow(true);
    double[][] signed = equations.calcPhaseMassFaceFluxes(new TwoFluidSection[] {outlet}, 1.0);

    assertEquals(-0.25 * 0.5 * 800.0 * area, signed[1][1], 1.0e-12);
    assertTrue(signed[1][0] > 0.0);
    assertFalse(equations.isOutletBackflowClamped());
  }

  /**
   * A different reversed-flow donor supplies advection, while pressure accelerates the phases in the pipe.
   */
  @Test
  void reverseDonorCannotChangePhasePressureTraction() {
    for (double liquidHoldup : new double[] {0.0, 1.0e-16, 0.4}) {
      TwoFluidSection cell = boundaryCell(1.0 - liquidHoldup, 0.75 * liquidHoldup, 0.25 * liquidHoldup);
      TwoFluidSection donor = boundaryCell(0.2, 0.3, 0.5);
      TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
      equations.setIncludeMassTransfer(false);
      equations.setConsistentPhasePressureEnabled(true);
      equations.setAllowOutletPhaseBackflow(true);
      equations.setOutletBoundaryState(donor);
      equations.setOutletBoundaryPressure(cell.getPressure());
      TwoFluidSection[] cells = {cell};
      double[][] reference = equations.calcRHS(cells, cell.getLength());
      double pressureChange = 1.0e4;
      equations.setOutletBoundaryPressure(cell.getPressure() + pressureChange);
      double[][] disturbed = equations.calcRHS(cells, cell.getLength());
      double[] holdups = {cell.getGasHoldup(), cell.getOilHoldup(), cell.getWaterHoldup()};
      for (int phase = 0; phase < 3; phase++) {
        assertEquals(-holdups[phase] * pressureChange * cell.getArea() / cell.getLength(),
            disturbed[0][phase + 3] - reference[0][phase + 3], 1.0e-9,
            "Pressure force must scale with pipe phase availability: phase " + phase + ", liquid " + liquidHoldup);
      }
      double[][] fluxes = equations.calcPhaseMassFaceFluxes(cells, cell.getLength());
      assertEquals(donor.getOilHoldup() * donor.getOilDensity() * cell.getOilVelocity() * cell.getArea(), fluxes[1][1],
          1.0e-14, "Reversed mass must still use the external donor");
    }
  }

  /**
   * A trace phase beside a macroscopic phase retains its finite pressure acceleration without subtractive cancellation.
   */
  @Test
  void tracePhasePressureAccelerationSurvivesAJumpInHoldup() {
    TwoFluidSection[] cells = {boundaryCell(1.0, 1.0e-38, 0.0), boundaryCell(1.0, 1.0e-38, 0.0),
        boundaryCell(0.6, 0.4, 0.0)};
    for (int cell = 0; cell < cells.length; cell++) {
      cells[cell].setPosition(cell + 0.5);
      cells[cell].setPressure(2.0e5 + cell * 1.0e4);
      cells[cell].setGasVelocity(1.0);
      cells[cell].setLiquidVelocity(1.0);
      cells[cell].setOilVelocity(1.0);
      cells[cell].updateConservativeVariables();
    }
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeMassTransfer(false);
    equations.setConservativeFrictionEnabled(true);
    equations.setConsistentPhasePressureEnabled(true);
    equations.getFluxCalculator().setCenteredPressureFluxEnabled(true);
    double[][] rates = equations.calcRHS(cells, 1.0);
    double acceleration = (rates[1][4] - cells[1].getOilVelocity() * rates[1][1]) / cells[1].getOilMassPerLength();
    assertEquals(-1.0e4 / cells[1].getOilDensity(), acceleration, 1.0e-10,
        "A pressure gradient must accelerate trace oil in proportion to its actual inertia");
  }

  /**
   * Build a uniform pressure boundary with a small negative phase velocity.
   *
   * @param gas gas volume fraction
   * @param oil oil volume fraction
   * @param water aqueous volume fraction
   * @return initialized boundary section
   */
  private static TwoFluidSection boundaryCell(double gas, double oil, double water) {
    TwoFluidSection cell = new TwoFluidSection(0.5, 1.0, 0.2, 0.0);
    cell.setPressure(2.0e5);
    cell.setGasDensity(5.0);
    cell.setOilDensity(800.0);
    cell.setWaterDensity(1000.0);
    cell.setLiquidDensity(850.0);
    cell.setGasHoldup(gas);
    cell.setOilHoldup(oil);
    cell.setWaterHoldup(water);
    cell.setLiquidHoldup(oil + water);
    cell.setGasVelocity(-1.0e-4);
    cell.setLiquidVelocity(-1.0e-4);
    cell.setOilVelocity(-1.0e-4);
    cell.setWaterVelocity(-1.0e-4);
    cell.updateConservativeVariables();
    return cell;
  }
}
