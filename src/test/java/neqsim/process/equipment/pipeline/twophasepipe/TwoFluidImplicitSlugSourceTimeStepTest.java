package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker.SlugBubbleUnit;

/**
 * Explicit source timestep limits apply only to forces retained in the explicit momentum operator.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidImplicitSlugSourceTimeStepTest {

  /** Implicit tracked-slug wall and gas-liquid drag cannot impose an explicit relaxation restriction. */
  @Test
  void implicitSlugForcesDoNotRestrictAnOilOnlyExplicitSourceStep() {
    TwoFluidSection section = section(0.0);
    TwoFluidConservationEquations equations = equations(marker());
    equations.setEnableWaterOilSlip(false);
    double explicitBound = equations.calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] {section});
    assertTrue(Double.isFinite(explicitBound) && explicitBound > 0.0);
    equations.setConservativeSlugForceIntegrationEnabled(true);
    assertNotNull(equations.conservativeSlugForces(section));
    double[] before = section.getStateVector();
    assertEquals(Double.POSITIVE_INFINITY,
        equations.calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] {section}),
        "wall and gas-liquid friction are advanced by the implicit subcell split");
    assertArrayEquals(before, section.getStateVector(), 0.0);
  }

  /** Oil-water slip remains explicit even where tracked-slug wall and gas-liquid forces are implicit. */
  @Test
  void implicitSlugForcesRetainTheExplicitOilWaterRelaxationBound() {
    TwoFluidSection section = section(0.4);
    TwoFluidConservationEquations equations = equations(marker());
    equations.setEnableWaterOilSlip(true);
    equations.setConservativeSlugForceIntegrationEnabled(true);
    assertNotNull(equations.conservativeSlugForces(section));
    equations.calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] {section});

    // The oil-water force is quadratic in slip for the frozen regime and thermophysical state.
    double slip = section.getOilVelocity() - section.getWaterVelocity();
    assertTrue(Math.abs(slip) > 0.0);
    double shearDerivative = 2.0 * Math.abs(section.calcOilWaterInterfacialShear() / slip);
    double interfacialLength = 0.5 * section.getDiameter() * section.getLiquidHoldup();
    double rate = shearDerivative * interfacialLength
        * (1.0 / section.getOilMassPerLength() + 1.0 / section.getWaterMassPerLength());
    assertTrue(Double.isFinite(rate) && rate > 0.0);
    assertEquals(1.0 / rate, equations.calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] {section}), 1e-7 / rate,
        "only the explicit oil-water shear determines the source bound");
  }

  /** An enabled option without an active subcell reconstruction must retain mean-force source limits. */
  @Test
  void markersOutsideTheCellDoNotDisableExplicitMeanForceLimits() {
    TwoFluidSection section = section(0.0);
    SlugBubbleUnit marker = marker();
    marker.tailPosition = 20.0;
    marker.frontPosition = 21.0;
    TwoFluidConservationEquations equations = equations(marker);
    double explicitBound = equations.calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] {section});
    assertTrue(Double.isFinite(explicitBound) && explicitBound > 0.0);
    equations.setConservativeSlugForceIntegrationEnabled(true);
    assertNull(equations.conservativeSlugForces(section));
    assertEquals(explicitBound, equations.calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] {section}),
        1e-9 * explicitBound);
  }

  /**
   * Construct a source evaluator with one conservative tracked marker.
   *
   * @param marker tracked slug interval
   * @return equation evaluator with source transfer and thermal terms disabled
   */
  private static TwoFluidConservationEquations equations(SlugBubbleUnit marker) {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeMassTransfer(false);
    equations.setIncludeEnergyEquation(false);
    equations.setConservativeSlugs(Collections.singletonList(marker));
    return equations;
  }

  /**
   * Construct an interval that admits a liquid body and falling surrounding film.
   *
   * @return conservative slug marker
   */
  private static SlugBubbleUnit marker() {
    SlugBubbleUnit marker = new SlugBubbleUnit();
    marker.tailPosition = 0.0;
    marker.frontPosition = 1.0;
    marker.slugHoldup = 0.9;
    marker.usesConservativeFilmCoupling = true;
    marker.hasConservativeVelocity = true;
    marker.slugLiquidVelocity = 3.0;
    return marker;
  }

  /**
   * Build a conservative gas/liquid cell with optional resolved oil-water slip.
   *
   * @param waterCut water fraction of total liquid volume
   * @return section with admissible body/film reconstruction
   */
  private static TwoFluidSection section(double waterCut) {
    TwoFluidSection section = new TwoFluidSection(5.0, 10.0, 0.1, 0.5);
    section.setGasDensity(50.0);
    section.setLiquidDensity((1.0 - waterCut) * 800.0 + waterCut * 1000.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setGasHoldup(0.8);
    section.setLiquidHoldup(0.2);
    section.setWaterCut(waterCut);
    section.setOilHoldup(0.2 * (1.0 - waterCut));
    section.setWaterHoldup(0.2 * waterCut);
    section.setGasVelocity(3.0);
    section.setLiquidVelocity(1.0);
    section.setOilVelocity(1.0);
    section.setWaterVelocity(0.6);
    section.setGasViscosity(1e-5);
    section.setLiquidViscosity(1e-3);
    section.setOilViscosity(1e-3);
    section.setWaterViscosity(1e-3);
    section.setSurfaceTension(0.02);
    section.setPressure(1e5);
    section.setTemperature(300.0);
    section.setMixtureHeatCapacity(2000.0);
    section.setGasEnthalpy(5e5);
    section.setLiquidEnthalpy(2e5);
    section.updateDerivedQuantities();
    section.updateConservativeVariables();
    section.extractPrimitiveVariables();
    return section;
  }
}
