package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction;

/**
 * Passive unresolved liquid-wall resistance under resolved oil/water counterflow.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidCounterflowWallDissipationTest {

  /** A wall law opposing bulk liquid momentum cannot supply oil/water kinetic energy. */
  @Test
  void bulkLiquidWallResistanceIsDissipativeForBothCounterflowDirections() {
    for (double direction : new double[] {1.0, -1.0}) {
      TwoFluidSection section = counterflowSection(direction);
      double liquidVelocity = section.getLiquidVelocity();
      assertEquals(-0.125 * direction, liquidVelocity, 1e-12);
      WallFriction.WallFrictionResult wall = new WallFriction().calculate(FlowRegime.SINGLE_PHASE_LIQUID,
          section.getGasVelocity(), liquidVelocity, section.getGasDensity(), section.getLiquidDensity(),
          section.getGasViscosity(), section.getLiquidViscosity(), section.getLiquidHoldup(), section.getDiameter(),
          section.getRoughness());
      assertTrue(wall.liquidWallForcePerLength * liquidVelocity > 0.0);

      TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
      equations.setEnableWaterOilSlip(true);
      equations.setIncludeMassTransfer(false);
      equations.setIncludeEnergyEquation(false);
      section.setLiquidWallShear(0.0);
      double[] baseline = equations.calcSourceTerms(new TwoFluidSection[] {section})[0];
      section.setLiquidWallShear(wall.liquidWallShear);
      double[] resisted = equations.calcSourceTerms(new TwoFluidSection[] {section})[0];
      double oilWallForce = resisted[4] - baseline[4];
      double waterWallForce = resisted[5] - baseline[5];
      assertEquals(-wall.liquidWallForcePerLength, oilWallForce + waterWallForce, 1e-12,
          "partitioning must retain the bulk liquid wall force");
      double wallPower = section.getOilVelocity() * oilWallForce + section.getWaterVelocity() * waterWallForce;
      assertTrue(wallPower <= 1e-12,
          "passive wall resistance injected " + wallPower + " W/m into oil/water counterflow");
    }
  }

  /** Co-current wall resistance preserves trace force and never accelerates an absent liquid phase. */
  @Test
  void coCurrentWallResistanceUsesConservativeMassSharesThroughPhaseDisappearance() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setEnableWaterOilSlip(true);
    equations.setIncludeMassTransfer(false);
    equations.setIncludeEnergyEquation(false);
    for (double oilHoldup : new double[] {0.0, 1e-12, 0.5, 1.0 - 1e-12, 1.0}) {
      for (double velocity : new double[] {-2.0, 2.0}) {
        TwoFluidSection section = liquidSection(oilHoldup, velocity, velocity);
        WallFriction.WallFrictionResult wall = new WallFriction().calculate(FlowRegime.SINGLE_PHASE_LIQUID,
            section.getGasVelocity(), section.getLiquidVelocity(), section.getGasDensity(), section.getLiquidDensity(),
            section.getGasViscosity(), section.getLiquidViscosity(), section.getLiquidHoldup(), section.getDiameter(),
            section.getRoughness());
        section.setLiquidWallShear(wall.liquidWallShear);
        double[] source = equations.calcSourceTerms(new TwoFluidSection[] {section})[0];
        double liquidMass = section.getOilMassPerLength() + section.getWaterMassPerLength();
        double totalForce = -wall.liquidWallForcePerLength;
        assertEquals(totalForce, source[4] + source[5], 1e-12);
        assertEquals(totalForce * section.getOilMassPerLength() / liquidMass, source[4], 1e-12);
        assertEquals(totalForce * section.getWaterMassPerLength() / liquidMass, source[5], 1e-12);
        assertTrue(section.getOilVelocity() * source[4] + section.getWaterVelocity() * source[5] < 0.0);
        for (int phase = 1; phase <= 2; phase++) {
          if (section.getStateVector()[phase] == 0.0) {
            assertEquals(0.0, source[phase + 3], 0.0, "absent liquid must receive no wall force");
          } else {
            assertTrue(source[phase + 3] != 0.0, "a trace liquid must retain its wall-force contribution");
          }
        }
      }
    }
  }

  /**
   * Construct unequal-density liquid phases with opposing bulk and holdup-weighted velocities.
   *
   * @param direction sign reversal applied to both phase velocities
   * @return initialized conservative section
   */
  private static TwoFluidSection counterflowSection(double direction) {
    return liquidSection(0.5, 3.0 * direction, -2.0 * direction);
  }

  /**
   * Build a gas-free conservative oil/water state with prescribed phase velocities.
   *
   * @param oilHoldup oil volume fraction
   * @param oilVelocity oil velocity in metres per second
   * @param waterVelocity water velocity in metres per second
   * @return initialized liquid section
   */
  private static TwoFluidSection liquidSection(double oilHoldup, double oilVelocity, double waterVelocity) {
    TwoFluidSection section = new TwoFluidSection(0.0, 10.0, 0.2, 0.0);
    section.setFlowRegime(FlowRegime.SINGLE_PHASE_LIQUID);
    section.setGasHoldup(0.0);
    section.setLiquidHoldup(1.0);
    section.setOilHoldup(oilHoldup);
    section.setWaterHoldup(1.0 - oilHoldup);
    section.setWaterCut(1.0 - oilHoldup);
    section.setGasDensity(30.0);
    section.setOilDensity(600.0);
    section.setWaterDensity(1000.0);
    double liquidDensity = oilHoldup * 600.0 + (1.0 - oilHoldup) * 1000.0;
    section.setLiquidDensity(liquidDensity);
    section.setGasViscosity(1e-5);
    section.setOilViscosity(0.002);
    section.setWaterViscosity(0.001);
    section.setLiquidViscosity(0.002);
    section.setGasVelocity(0.0);
    section.setLiquidVelocity(
        (oilHoldup * 600.0 * oilVelocity + (1.0 - oilHoldup) * 1000.0 * waterVelocity) / liquidDensity);
    section.setOilVelocity(oilVelocity);
    section.setWaterVelocity(waterVelocity);
    section.setGasWallShear(0.0);
    section.setInterfacialShear(0.0);
    section.updateConservativeVariables();
    section.extractPrimitiveVariables();
    return section;
  }
}
