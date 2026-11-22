package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.closure.OilWaterFlowRegimeDetector.OilWaterFlowRegime;

/**
 * Gas-agitated regimes mix oil and water at the total volumetric flux, not at the bulk liquid velocity.
 *
 * <p>
 * Regression for equinor/neqsim#5046: a slower liquid in an annular riser was reclassified as stratified oil-water,
 * which collapsed the oil-water interfacial area and let water fall back against rising oil.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class OilWaterGasAgitatedMixingTest {
  private static final double GAS_HOLDUP = 0.6;
  private static final double LIQUID_HOLDUP = 0.4;
  private static final double GAS_VELOCITY = 5.0;
  private static final double LIQUID_VELOCITY = 0.4;

  /**
   * Builds a 50 degree riser cell with a slow liquid and a fast gas.
   *
   * @param regime gas-liquid flow regime to impose
   * @return initialized section
   */
  private static TwoFluidSection riserCell(PipeSection.FlowRegime regime) {
    TwoFluidSection section = new TwoFluidSection(0.0, 10.0, 0.2032, Math.toRadians(50.0));
    section.setGasDensity(45.0);
    section.setOilDensity(680.0);
    section.setWaterDensity(1000.0);
    section.setOilViscosity(1.0e-3);
    section.setWaterViscosity(6.0e-4);
    section.setGasHoldup(GAS_HOLDUP);
    section.setLiquidHoldup(LIQUID_HOLDUP);
    section.setWaterCut(0.3);
    section.setOilFractionInLiquid(0.7);
    section.setWaterHoldup(0.3 * LIQUID_HOLDUP);
    section.setOilHoldup(0.7 * LIQUID_HOLDUP);
    section.setGasVelocity(GAS_VELOCITY);
    section.setLiquidVelocity(LIQUID_VELOCITY);
    section.setOilVelocity(LIQUID_VELOCITY);
    section.setWaterVelocity(LIQUID_VELOCITY);
    section.setFlowRegime(regime);
    section.updateThreePhaseProperties();
    return section;
  }

  /** Annular, churn and bubbly cells disperse the liquids; stratified and slug flow keep the liquid criterion. */
  @Test
  void gasAgitationDispersesOilAndWater() {
    double flux = GAS_HOLDUP * GAS_VELOCITY + LIQUID_HOLDUP * LIQUID_VELOCITY;
    for (PipeSection.FlowRegime regime : new PipeSection.FlowRegime[] {PipeSection.FlowRegime.ANNULAR,
        PipeSection.FlowRegime.CHURN, PipeSection.FlowRegime.DISPERSED_BUBBLE}) {
      TwoFluidSection agitated = riserCell(regime);
      assertEquals(flux, agitated.calcOilWaterMixingVelocity(), 1.0e-12, regime.name());
      OilWaterFlowRegime oilWater = agitated.getOilWaterFlowRegime();
      assertTrue(oilWater == OilWaterFlowRegime.DISPERSED_WATER_IN_OIL
          || oilWater == OilWaterFlowRegime.DISPERSED_OIL_IN_WATER || oilWater == OilWaterFlowRegime.DUAL_DISPERSION,
          regime + " gave " + oilWater);
    }

    // The film between slugs can stay segregated, so intermittent flow keeps the liquid-velocity criterion.
    assertEquals(LIQUID_VELOCITY, riserCell(PipeSection.FlowRegime.SLUG).calcOilWaterMixingVelocity(), 1.0e-12);
    TwoFluidSection layered = riserCell(PipeSection.FlowRegime.STRATIFIED_WAVY);
    assertEquals(LIQUID_VELOCITY, layered.calcOilWaterMixingVelocity(), 1.0e-12);
    assertNotEquals(riserCell(PipeSection.FlowRegime.ANNULAR).getOilWaterFlowRegime(), layered.getOilWaterFlowRegime());
    assertTrue(riserCell(PipeSection.FlowRegime.ANNULAR).calcOilWaterInterfacialAreaPerLength() > 10.0
        * layered.calcOilWaterInterfacialAreaPerLength());
  }

  /** Dropping the relative part keeps gas and the combined liquid force and gives both liquids one acceleration. */
  @Test
  void combinedLiquidCorrectionRemovesOnlyRelativeOilWaterForce() {
    double[] state = new double[7];
    state[0] = 1.0;
    state[1] = 7.9;
    state[2] = 1.9;
    double[] calibrated = {-36.0, 25.6, 9.1};
    double[] retained = TwoFluidConservationEquations.retainCombinedLiquidCorrection(calibrated, state);
    assertEquals(calibrated[0], retained[0], 0.0);
    assertEquals(calibrated[1] + calibrated[2], retained[1] + retained[2], 1.0e-12);
    assertEquals(retained[1] / state[1], retained[2] / state[2], 1.0e-12);

    double[] waterFree = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
    waterFree[0] = 1.0;
    waterFree[1] = 7.9;
    double[] unchanged = TwoFluidConservationEquations.retainCombinedLiquidCorrection(calibrated, waterFree);
    assertEquals(calibrated[1], unchanged[1], 0.0);
    assertEquals(calibrated[2], unchanged[2], 0.0);
  }
}
