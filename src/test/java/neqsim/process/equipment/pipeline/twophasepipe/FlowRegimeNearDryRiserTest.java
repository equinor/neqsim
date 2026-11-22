package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import org.junit.jupiter.api.Test;

/**
 * A rising cell that holds a trace of liquid in nearly stagnant gas must not take a liquid-continuous regime.
 *
 * <p>
 * The states are from the top cells of a 44 degree riser whose outlet is closed while the line packs: 0.3 to 3 % liquid
 * draining as a film, gas velocity passing through zero. The bubble criterion inferred a void fraction below 0.25 from
 * the superficial velocities, so a cell that was 99.7 % gas took the bubble closure and its film velocity jumped from
 * -0.7 to +14 m/s.
 * </p>
 */
class FlowRegimeNearDryRiserTest {
  private static final double RISER_INCLINATION = Math.atan2(430.0, Math.sqrt(620.0 * 620.0 - 430.0 * 430.0));

  @Test
  void stagnantGasOverTraceLiquidIsNotBubbleFlow() {
    FlowRegimeDetector detector = new FlowRegimeDetector();
    for (double gasVelocity : new double[] {0.0, 0.001, 0.02}) {
      for (double liquidVelocity : new double[] {0.0, 0.1, 0.5}) {
        TwoFluidSection section = section(0.003, gasVelocity, liquidVelocity);
        assertNotEquals(FlowRegime.BUBBLE, detector.detectFlowRegime(section),
            "gas=" + gasVelocity + " m/s, liquid=" + liquidVelocity + " m/s");
      }
    }
  }

  @Test
  void bubbleFlowStillNeedsOnlyAContinuousLiquid() {
    FlowRegimeDetector detector = new FlowRegimeDetector();
    assertEquals(FlowRegime.BUBBLE, detector.detectFlowRegime(section(0.80, 0.05, 0.2)));
    assertEquals(FlowRegime.BUBBLE, detector.detectFlowRegime(section(0.49, 0.02, 0.2)));
    assertNotEquals(FlowRegime.BUBBLE, detector.detectFlowRegime(section(0.47, 0.02, 0.2)));
  }

  @Test
  void countercurrentAndCocurrentStatesKeepTheSlugClassification() {
    FlowRegimeDetector detector = new FlowRegimeDetector();
    assertEquals(FlowRegime.SLUG, detector.detectFlowRegime(section(0.12, 0.4, -1.5)));
    assertEquals(FlowRegime.SLUG, detector.detectFlowRegime(section(0.60, 0.4, -1.5)));
    assertEquals(FlowRegime.SLUG, detector.detectFlowRegime(section(0.12, 0.8, 0.3)));
  }

  private static TwoFluidSection section(double liquidHoldup, double gasVelocity, double liquidVelocity) {
    TwoFluidSection section = new TwoFluidSection(1300.0, 62.3, 0.3048, RISER_INCLINATION);
    section.setPressure(110.0e5);
    section.setTemperature(350.0);
    section.setGasDensity(90.4);
    section.setLiquidDensity(748.6);
    section.setOilDensity(748.6);
    section.setWaterDensity(960.0);
    section.setGasHoldup(1.0 - liquidHoldup);
    section.setLiquidHoldup(liquidHoldup);
    section.setOilHoldup(liquidHoldup);
    section.setWaterHoldup(0.0);
    section.setWaterCut(0.0);
    section.setOilFractionInLiquid(1.0);
    section.setGasVelocity(gasVelocity);
    section.setLiquidVelocity(liquidVelocity);
    section.setOilVelocity(liquidVelocity);
    section.setWaterVelocity(0.0);
    section.setGasViscosity(1.6e-5);
    section.setLiquidViscosity(4.0e-4);
    section.setOilViscosity(4.0e-4);
    section.setWaterViscosity(3.5e-4);
    section.setSurfaceTension(0.008);
    section.setRoughness(4.5e-5);
    section.updateConservativeVariables();
    section.updateDerivedQuantities();
    return section;
  }
}
