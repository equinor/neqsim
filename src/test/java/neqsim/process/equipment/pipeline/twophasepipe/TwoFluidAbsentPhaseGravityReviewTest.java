package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Gravity must depend only on the independently present phase inventories.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidAbsentPhaseGravityReviewTest {
  /** An absent gas's unused density cannot create a non-finite gravity force. */
  @Test
  void absentGasDensityDoesNotContaminateLiquidOnlyGravity() {
    TwoFluidSection section = liquidSection(false);
    section.setGasDensity(Double.NaN);
    double[][] sources = equations().calcSourceTerms(new TwoFluidSection[] {section});
    assertEquals(0.0, sources[0][3], 0.0);
    assertEquals(expectedGravity(section, 800.0), sources[0][4], 1.0e-12);
  }

  /** An absent aqueous phase cannot suppress gravity on the present oil phase. */
  @Test
  void absentWaterDensityDoesNotSuppressOilGravity() {
    for (double absentDensity : new double[] {0.0, Double.NaN}) {
      TwoFluidSection section = liquidSection(false);
      section.setWaterDensity(absentDensity);
      double[][] sources = equations().calcSourceTerms(new TwoFluidSection[] {section});
      assertEquals(expectedGravity(section, 800.0), sources[0][4], 1.0e-12);
      assertEquals(0.0, sources[0][5], 0.0);
    }
  }

  /** An absent oil phase cannot suppress gravity on the present aqueous phase. */
  @Test
  void absentOilDensityDoesNotSuppressWaterGravity() {
    for (double absentDensity : new double[] {0.0, Double.NaN}) {
      TwoFluidSection section = liquidSection(true);
      section.setOilDensity(absentDensity);
      double[][] sources = equations().calcSourceTerms(new TwoFluidSection[] {section});
      assertEquals(0.0, sources[0][4], 0.0);
      assertEquals(expectedGravity(section, 1000.0), sources[0][5], 1.0e-12);
    }
  }

  /** Body force must retain acceleration g while a trial's recovered holdups are normalized. */
  @Test
  void gravityUsesConservativeMassWhenTrialVolumeIsNotClosed() {
    TwoFluidSection section = liquidSection(false);
    double[] state = section.getStateVector();
    state[1] *= 1.01;
    section.setStateVector(state);
    section.extractPrimitiveVariables();
    double[][] sources = equations().calcSourceTerms(new TwoFluidSection[] {section});
    assertEquals(-state[1] * 9.81 * Math.sin(section.getInclination()), sources[0][4], 1.0e-12);
  }

  /**
   * Make a stationary pure liquid with all conservative fields initialized.
   *
   * @param water true for pure water, false for pure oil
   * @return initialized inclined section
   */
  private static TwoFluidSection liquidSection(boolean water) {
    TwoFluidSection section = new TwoFluidSection(0.5, 1.0, 0.2, Math.toRadians(10.0));
    section.setPressure(2.0e5);
    section.setGasHoldup(0.0);
    section.setLiquidHoldup(1.0);
    section.setWaterCut(water ? 1.0 : 0.0);
    section.setGasDensity(10.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(water ? 1000.0 : 800.0);
    section.setGasVelocity(0.0);
    section.setLiquidVelocity(0.0);
    section.setOilVelocity(0.0);
    section.setWaterVelocity(0.0);
    section.updateConservativeVariables();
    return section;
  }

  /**
   * Calculate the independent hydrostatic force per unit length.
   *
   * @param section inclined pure-liquid section
   * @param density density of its present phase in kg/m3
   * @return signed gravity force in N/m
   */
  private static double expectedGravity(TwoFluidSection section, double density) {
    return -density * 9.81 * section.getArea() * Math.sin(section.getInclination());
  }

  /**
   * Configure an isolated momentum-source evaluation.
   *
   * @return equations with energy and phase transfer disabled
   */
  private static TwoFluidConservationEquations equations() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(false);
    equations.setIncludeMassTransfer(false);
    return equations;
  }
}
