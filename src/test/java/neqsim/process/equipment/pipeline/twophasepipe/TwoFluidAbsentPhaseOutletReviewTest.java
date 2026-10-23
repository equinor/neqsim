package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * Outlet backflow diagnostics must describe transported phases rather than unused primitive extensions.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidAbsentPhaseOutletReviewTest {
  /** A stale velocity of an exactly absent phase cannot invalidate a forward-flowing result. */
  @Test
  void absentPhaseReverseVelocitiesDoNotReportClampedBackflow() {
    TwoFluidSection section = liquidSection();
    section.setGasVelocity(-10.0);
    section.setWaterVelocity(-1.0);
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.calcPhaseMassFaceFluxes(new TwoFluidSection[] {section}, 1.0);
    assertFalse(equations.isOutletBackflowClamped(), "No present phase is reversed at the outlet");
  }

  /** Unused non-finite velocities cannot create mass flux for an exactly absent phase. */
  @Test
  void absentPhaseNonfiniteVelocitiesCarryZeroBoundaryMass() {
    TwoFluidSection section = liquidSection();
    section.setGasVelocity(Double.NaN);
    section.setWaterVelocity(Double.NaN);
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    double[][] faces = equations.calcPhaseMassFaceFluxes(new TwoFluidSection[] {section}, 1.0);
    for (double[] face : faces) {
      assertEquals(0.0, face[0], 0.0);
      assertEquals(0.0, face[2], 0.0);
    }
  }

  /** An independently positive trace must retain its backflow diagnostic. */
  @Test
  void positiveTraceWaterReverseVelocityStillReportsClampedBackflow() {
    TwoFluidSection section = liquidSection();
    section.setWaterCut(1.0e-20);
    section.updateConservativeVariables();
    section.setWaterVelocity(-1.0);
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.calcPhaseMassFaceFluxes(new TwoFluidSection[] {section}, 1.0);
    assertTrue(equations.isOutletBackflowClamped(), "Positive trace phases must not be hidden by a holdup cutoff");
  }

  /**
   * Unused gas and aqueous properties cannot poison an oil-only boundary's total-energy flux.
   *
   * @throws Exception if a boundary evaluator cannot be invoked
   */
  @Test
  void absentGasAndWaterPropertiesDoNotContaminateBoundaryEnergy() throws Exception {
    TwoFluidSection section = liquidSection();
    section.setLiquidEnthalpy(1.0e5);
    section.setGasEnthalpy(Double.NaN);
    section.setGasDensity(Double.NaN);
    section.setGasVelocity(Double.NaN);
    section.setWaterDensity(Double.NaN);
    section.setWaterVelocity(Double.NaN);
    double expected = section.getOilMassPerLength() * section.getOilVelocity()
        * (section.getLiquidEnthalpy() + 0.5 * section.getOilVelocity() * section.getOilVelocity());
    assertBoundaryEnergy(section, expected);
  }

  /**
   * Unused liquid enthalpy cannot contaminate a dry-gas boundary's total-energy flux.
   *
   * @throws Exception if a boundary evaluator cannot be invoked
   */
  @Test
  void absentLiquidPropertiesDoNotContaminateDryGasBoundaryEnergy() throws Exception {
    TwoFluidSection section = liquidSection();
    section.setGasHoldup(1.0);
    section.setLiquidHoldup(0.0);
    section.setGasVelocity(2.0);
    section.setGasEnthalpy(2.0e5);
    section.updateConservativeVariables();
    section.setLiquidEnthalpy(Double.NaN);
    section.setOilVelocity(Double.NaN);
    section.setWaterVelocity(Double.NaN);
    double expected = section.getGasMassPerLength() * section.getGasVelocity()
        * (section.getGasEnthalpy() + 0.5 * section.getGasVelocity() * section.getGasVelocity());
    assertBoundaryEnergy(section, expected);
  }

  /**
   * Check both boundary implementations and both outlet backflow policies.
   *
   * @param section boundary state with independently known total-energy flux
   * @param expected total-energy flux in W
   * @throws Exception if reflection fails
   */
  private static void assertBoundaryEnergy(TwoFluidSection section, double expected) throws Exception {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    for (boolean signed : new boolean[] {false, true}) {
      equations.setAllowOutletPhaseBackflow(signed);
      for (String name : new String[] {"calcInletFlux", "calcOutletFlux"}) {
        Method method = TwoFluidConservationEquations.class.getDeclaredMethod(name, TwoFluidSection.class,
            double.class);
        method.setAccessible(true);
        double[] flux = (double[]) method.invoke(equations, section, section.getPressure());
        assertEquals(expected, flux[6], 1.0e-8);
      }
    }
  }

  /**
   * Make a forward oil-only boundary cell with initialized conservative inventories.
   *
   * @return pure-oil section
   */
  private static TwoFluidSection liquidSection() {
    TwoFluidSection section = new TwoFluidSection(0.5, 1.0, 0.2, 0.0);
    section.setPressure(2.0e5);
    section.setGasHoldup(0.0);
    section.setLiquidHoldup(1.0);
    section.setWaterCut(0.0);
    section.setGasDensity(10.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(800.0);
    section.setGasVelocity(0.0);
    section.setLiquidVelocity(1.0);
    section.setOilVelocity(1.0);
    section.setWaterVelocity(0.0);
    section.updateConservativeVariables();
    return section;
  }
}
