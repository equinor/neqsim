package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction;
import neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction.WallFrictionResult;

/** Wall forces retain their closure geometry through regime changes and blending. */
class TwoFluidWallForceConsistencyTest {
  private static final double DIAMETER = 0.3;
  private static final double DENSITY = 800.0;
  private static final double VISCOSITY = 0.002;
  private static final double VELOCITY = 2.0;
  private static final double ROUGHNESS = 4.6e-5;

  @Test
  void identicalPhasesRecoverTheHomogeneousPipeWallForce() {
    WallFriction model = new WallFriction();
    WallFrictionResult single = model.calculate(FlowRegime.SINGLE_PHASE_LIQUID, VELOCITY, VELOCITY, DENSITY, DENSITY,
        VISCOSITY, VISCOSITY, 1.0, DIAMETER, ROUGHNESS);
    double expectedForce = -single.liquidWallShear * Math.PI * DIAMETER;
    for (FlowRegime regime : new FlowRegime[] { FlowRegime.SLUG, FlowRegime.CHURN }) {
      for (double liquidHoldup : new double[] { 0.1, 0.35, 0.7, 0.9 }) {
        TwoFluidSection section = section(regime, liquidHoldup);
        double[][] source = equations().calcSourceTerms(new TwoFluidSection[] { section });
        assertEquals(expectedForce, source[0][3] + source[0][4] + source[0][5], Math.abs(expectedForce) * 1.0e-12,
            regime + " must not partition the mixture wall force twice");
      }
    }
  }

  @Test
  void liquidFilmWetsTheFullWallAfterAStratifiedState() {
    for (FlowRegime regime : new FlowRegime[] { FlowRegime.ANNULAR, FlowRegime.MIST, FlowRegime.BUBBLE,
        FlowRegime.DISPERSED_BUBBLE }) {
      TwoFluidSection section = section(regime, 0.35);
      WallFrictionResult friction = friction(regime, section);
      double[][] source = equations().calcSourceTerms(new TwoFluidSection[] { section });
      assertEquals(-friction.liquidWallShear * Math.PI * DIAMETER, source[0][4],
          Math.abs(friction.liquidWallShear) * 1.0e-12, regime + " must not reuse the old liquid segment perimeter");
    }
  }

  @Test
  void transitionBlendsIntegratedForcesWithoutCrossTerms() {
    TwoFluidSection section = section(FlowRegime.SLUG, 0.35);
    Map<FlowRegime, Double> weights = new EnumMap<>(FlowRegime.class);
    weights.put(FlowRegime.STRATIFIED_SMOOTH, 0.4);
    weights.put(FlowRegime.SLUG, 0.6);
    section.setRegimeWeights(weights);
    WallFrictionResult stratified = friction(FlowRegime.STRATIFIED_SMOOTH, section);
    WallFrictionResult slug = friction(FlowRegime.SLUG, section);
    double expectedGasForce = -(0.4 * stratified.gasWallShear * section.getGasWettedPerimeter()
        + 0.6 * slug.gasWallShear * Math.PI * DIAMETER);
    double expectedOilForce = -(0.4 * stratified.liquidWallShear * section.getLiquidWettedPerimeter()
        + 0.6 * slug.liquidWallShear * Math.PI * DIAMETER);
    double[][] source = equations().calcSourceTerms(new TwoFluidSection[] { section });
    assertEquals(expectedGasForce, source[0][3], Math.abs(expectedGasForce) * 1.0e-12);
    assertEquals(expectedOilForce, source[0][4], Math.abs(expectedOilForce) * 1.0e-12);
  }

  private static TwoFluidConservationEquations equations() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeMassTransfer(false);
    equations.setIncludeEnergyEquation(false);
    return equations;
  }

  private static WallFrictionResult friction(FlowRegime regime, TwoFluidSection section) {
    return new WallFriction().calculate(regime, VELOCITY, VELOCITY, DENSITY, DENSITY, VISCOSITY, VISCOSITY,
        section.getLiquidHoldup(), DIAMETER, ROUGHNESS);
  }

  private static TwoFluidSection section(FlowRegime regime, double liquidHoldup) {
    TwoFluidSection section = new TwoFluidSection(5.0, 10.0, DIAMETER, 0.0);
    section.setRoughness(ROUGHNESS);
    section.setGasHoldup(1.0 - liquidHoldup);
    section.setLiquidHoldup(liquidHoldup);
    section.setWaterCut(0.0);
    section.setOilHoldup(liquidHoldup);
    section.setGasDensity(DENSITY);
    section.setLiquidDensity(DENSITY);
    section.setOilDensity(DENSITY);
    section.setWaterDensity(DENSITY);
    section.setGasViscosity(VISCOSITY);
    section.setLiquidViscosity(VISCOSITY);
    section.setGasVelocity(VELOCITY);
    section.setLiquidVelocity(VELOCITY);
    section.updateConservativeVariables();
    section.updateStratifiedGeometry();
    section.setFlowRegime(regime);
    WallFrictionResult friction = friction(regime, section);
    section.setGasWallShear(friction.gasWallShear);
    section.setLiquidWallShear(friction.liquidWallShear);
    return section;
  }
}
