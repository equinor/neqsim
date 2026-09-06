package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Mechanical momentum-source qualification through gas, oil and water disappearance.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidMomentumSourceConservationTest {

  /** Mechanical sources cannot inject momentum into an absent phase, including trace liquid flow. */
  @Test
  void phasePresenceRoutesSourcesAndPreservesTotalForce() {
    double[][] holdups = { { 0.6, 0.4, 0.0 }, { 0.6, 0.0, 0.4 }, { 1.0 - 1e-10, 1e-10, 0.0 },
        { 1.0 - 1e-10, 0.0, 1e-10 }, { 0.6, 0.15, 0.25 }, { 0.0, 1.0, 0.0 }, { 0.0, 0.0, 1.0 }, { 1.0, 0.0, 0.0 } };
    for (boolean slip : new boolean[] { false, true }) {
      TwoFluidConservationEquations equations = createEquations(slip);
      for (double[] fractions : holdups) {
        TwoFluidSection section = createSection(fractions[0], fractions[1], fractions[2]);
        double[] source = equations.calcSourceTerms(new TwoFluidSection[] { section })[0];
        for (int phase = 0; phase < 3; phase++) {
          assertEquals(0.0, source[phase], 0.0);
          if (fractions[phase] == 0.0) {
            assertEquals(0.0, source[phase + 3], 0.0, "Absent phase " + phase + " with slip=" + slip);
          }
        }
        double wallForce = (fractions[0] > 0.0 ? -5.0 * 0.25 : 0.0)
            + (fractions[1] + fractions[2] > 0.0 ? -11.0 * 0.35 : 0.0);
        double gravityForce = -9.81 * Math.sin(section.getInclination()) * section.getArea()
            * (fractions[0] * 30.0 + fractions[1] * 800.0 + fractions[2] * 1000.0);
        assertEquals(wallForce + gravityForce, source[3] + source[4] + source[5], 1e-12);
      }
    }
  }

  /** Vanishing total liquid holdup does not replace the actual oil/water wall-force shares with 50/50. */
  @Test
  void traceLiquidWallForceUsesActualLiquidShares() {
    TwoFluidConservationEquations equations = createEquations(true);
    TwoFluidSection section = createSection(1.0 - 1e-10, 2e-11, 8e-11);
    section.setInclination(0.0);
    section.setGasWallShear(0.0);
    section.setInterfacialShear(0.0);
    double[] source = equations.calcSourceTerms(new TwoFluidSection[] { section })[0];
    assertEquals(-11.0 * 0.35 * 0.2, source[4], 1e-12);
    assertEquals(-11.0 * 0.35 * 0.8, source[5], 1e-12);
  }

  /** With slip disabled, both liquid phases accelerate equally despite unequal densities. */
  @Test
  void noSlipMechanicalForceGivesCommonLiquidAcceleration() {
    TwoFluidConservationEquations equations = createEquations(false);
    TwoFluidSection section = createSection(0.6, 0.2, 0.2);
    double[] before = section.getStateVector();
    double[] source = equations.calcSourceTerms(new TwoFluidSection[] { section })[0];
    double oilAcceleration = source[4] / before[1];
    double waterAcceleration = source[5] / before[2];
    assertEquals(oilAcceleration, waterAcceleration, 1e-12);
    assertEquals((source[4] + source[5]) / (before[1] + before[2]), oilAcceleration, 1e-12);
    double dt = 1e-4;
    double oilVelocity = (before[4] + dt * source[4]) / before[1];
    double waterVelocity = (before[5] + dt * source[5]) / before[2];
    assertEquals(oilVelocity, waterVelocity, 1e-12);
  }

  /** Oil-water shear exchanges equal and opposite forces without changing mixture momentum. */
  @Test
  void oilWaterSlipDragRemainsPairwiseConservative() {
    TwoFluidConservationEquations equations = createEquations(true);
    TwoFluidSection section = createSection(0.6, 0.15, 0.25);
    double[] baseline = equations.calcSourceTerms(new TwoFluidSection[] { section })[0];
    section.setOilVelocity(1.4);
    section.setWaterVelocity(0.7);
    double[] withSlip = equations.calcSourceTerms(new TwoFluidSection[] { section })[0];
    double oilForceChange = withSlip[4] - baseline[4];
    double waterForceChange = withSlip[5] - baseline[5];
    assertTrue(oilForceChange < 0.0);
    assertEquals(-oilForceChange, waterForceChange, 1e-12);
    assertEquals(baseline[3], withSlip[3], 0.0);
  }

  /** Disabling slip must not redirect water condensation momentum into the oil equation. */
  @Test
  void noSlipPhaseChangeKeepsDonorMomentumInItsReceivingPhase() {
    TwoFluidConservationEquations equations = createEquations(false);
    TwoFluidSection section = createSection(0.6, 0.2, 0.2);
    double[] baseline = equations.calcSourceTerms(new TwoFluidSection[] { section })[0];
    equations.setIncludeMassTransfer(true);
    section.setMassTransferRate(-0.2);
    double[] condensation = equations.calcSourceTerms(new TwoFluidSection[] { section })[0];
    double gasMassSource = -0.2 / section.getLength();
    assertEquals(gasMassSource, condensation[0], 1e-12);
    assertEquals(-0.5 * gasMassSource, condensation[1], 1e-12);
    assertEquals(-0.5 * gasMassSource, condensation[2], 1e-12);
    assertEquals(gasMassSource * 2.0, condensation[3] - baseline[3], 1e-12);
    assertEquals(-0.5 * gasMassSource * 2.0, condensation[4] - baseline[4], 1e-12);
    assertEquals(-0.5 * gasMassSource * 2.0, condensation[5] - baseline[5], 1e-12);
    assertEquals(baseline[3] + baseline[4] + baseline[5], condensation[3] + condensation[4] + condensation[5], 1e-12);
  }

  /** Uniform gas/oil and gas/water states keep the absent liquid's complete momentum RHS exactly zero. */
  @Test
  void fullRhsDoesNotAccelerateAnAbsentLiquidPhase() {
    for (boolean slip : new boolean[] { false, true }) {
      for (boolean waterOnly : new boolean[] { false, true }) {
        TwoFluidConservationEquations equations = createEquations(slip);
        TwoFluidSection section = createSection(0.6, waterOnly ? 0.0 : 0.4, waterOnly ? 0.4 : 0.0);
        section.setInclination(0.0);
        section.setGasViscosity(1.5e-5);
        section.setLiquidViscosity(1e-3);
        section.setOilViscosity(1e-3);
        section.setWaterViscosity(1e-3);
        section.setSurfaceTension(0.03);
        section.setPressure(1e5);
        TwoFluidSection[] cells = { section.clone(), section.clone(), section.clone() };
        double[][] rhs = equations.calcRHS(cells, section.getLength());
        int absentMomentum = waterOnly ? 4 : 5;
        assertEquals(0.0, rhs[1][absentMomentum], 0.0);
        for (double value : rhs[1]) {
          assertTrue(Double.isFinite(value));
        }
      }
    }
  }

  /** No-slip virtual mass uses both liquid momenta and routes reaction only to present liquid phases. */
  @Test
  void noSlipVirtualMassUsesAllLiquidRatesAndConservesPhaseReaction() {
    for (double waterCut : new double[] { 1.0, 0.0, 0.35 }) {
      TwoFluidConservationEquations equations = createEquations(false);
      equations.setEnableVirtualMassForce(true);
      equations.setVirtualMassCoefficient(0.5);
      TwoFluidSection section = createSection(0.6, 0.4 * (1.0 - waterCut), 0.4 * waterCut);
      double gasMass = section.getGasMassPerLength();
      double oilMass = section.getOilMassPerLength();
      double waterMass = section.getWaterMassPerLength();
      double liquidMass = oilMass + waterMass;
      double gasAcceleration = 7.0;
      double liquidAcceleration = -3.0;
      double[][] rates = new double[1][TwoFluidConservationEquations.NUM_EQUATIONS];
      rates[0][0] = 0.2;
      rates[0][1] = 0.1 * oilMass / liquidMass;
      rates[0][2] = 0.1 * waterMass / liquidMass;
      rates[0][3] = gasMass * gasAcceleration + section.getGasVelocity() * rates[0][0];
      rates[0][4] = oilMass * liquidAcceleration + section.getLiquidVelocity() * rates[0][1];
      rates[0][5] = waterMass * liquidAcceleration + section.getLiquidVelocity() * rates[0][2];
      double[] original = rates[0].clone();

      // Independent two-body added-inertia solution, including the u * dm/dt conversion.
      double addedMass = 0.5 * section.getGasHoldup() * section.getLiquidDensity() * section.getArea();
      double gasForce = -addedMass * (gasAcceleration - liquidAcceleration)
          / (1.0 + addedMass * (1.0 / gasMass + 1.0 / liquidMass));
      equations.applyVirtualMassCoupling(new TwoFluidSection[] { section }, rates);

      assertEquals(original[3] + gasForce, rates[0][3], 1e-12, "Water cut=" + waterCut);
      assertEquals(original[4] - gasForce * oilMass / liquidMass, rates[0][4], 1e-12);
      assertEquals(original[5] - gasForce * waterMass / liquidMass, rates[0][5], 1e-12);
      assertEquals(original[3] + original[4] + original[5], rates[0][3] + rates[0][4] + rates[0][5], 1e-12);
      for (int phase = 0; phase < 3; phase++) {
        assertEquals(original[phase], rates[0][phase], 0.0);
      }
      if (oilMass == 0.0) {
        assertEquals(0.0, rates[0][4], 0.0, "Absent oil must not receive virtual-mass force");
      }
      if (waterMass == 0.0) {
        assertEquals(0.0, rates[0][5], 0.0, "Absent water must not receive virtual-mass force");
      }
      if (oilMass > 0.0 && waterMass > 0.0) {
        assertEquals((rates[0][4] - original[4]) / oilMass, (rates[0][5] - original[5]) / waterMass, 1e-12,
            "The added-mass reaction must preserve common liquid acceleration");
      }
    }
  }

  /** No-slip pressure-flux cancellation follows liquid holdup shares, including the pure-water endpoint. */
  @Test
  void noSlipInterfacialPressureCancelsEachPresentLiquidPressureFlux() {
    for (double waterCut : new double[] { 1.0, 0.0, 0.35 }) {
      TwoFluidConservationEquations equations = createEquations(false);
      equations.setEnableInterfacialPressure(true);
      double[] gasHoldups = { 0.2, 0.4, 0.6 };
      TwoFluidSection[] sections = new TwoFluidSection[gasHoldups.length];
      for (int cell = 0; cell < sections.length; cell++) {
        double liquidHoldup = 1.0 - gasHoldups[cell];
        sections[cell] = createSection(gasHoldups[cell], liquidHoldup * (1.0 - waterCut), liquidHoldup * waterCut);
        sections[cell].setPressure(1e5);
        sections[cell].setGasSoundSpeed(300.0);
        sections[cell].setLiquidSoundSpeed(1200.0);
        sections[cell].setGasVelocity(0.0);
        sections[cell].setLiquidVelocity(0.0);
        sections[cell].setOilVelocity(0.0);
        sections[cell].setWaterVelocity(0.0);
        sections[cell].updateConservativeVariables();
      }
      // This public flux calculation caches the interface holdups/pressure used by the correction.
      equations.calcPhaseMassFaceFluxes(sections, sections[0].getLength());
      double[][] rates = new double[sections.length][TwoFluidConservationEquations.NUM_EQUATIONS];
      equations.applyInterfacialPressure(sections, rates);

      for (int cell = 0; cell < sections.length; cell++) {
        double alphaRight = cell < sections.length - 1 ? 0.5 * (gasHoldups[cell] + gasHoldups[cell + 1])
            : gasHoldups[cell];
        double alphaLeft = cell > 0 ? 0.5 * (gasHoldups[cell - 1] + gasHoldups[cell]) : gasHoldups[cell];
        // Equal pressure and zero velocities leave exactly p * A * d(alpha)/dx to cancel.
        double gasForce = 1e5 * sections[cell].getArea() * (alphaRight - alphaLeft) / sections[cell].getLength();
        double liquidForce = -gasForce;
        assertTrue(gasForce > 0.0);
        assertEquals(gasForce, rates[cell][3], 1e-10);
        assertEquals(liquidForce * (1.0 - waterCut), rates[cell][4], 1e-10, "Water cut=" + waterCut);
        assertEquals(liquidForce * waterCut, rates[cell][5], 1e-10);
        assertEquals(0.0, rates[cell][3] + rates[cell][4] + rates[cell][5], 1e-10);
        if (waterCut == 1.0) {
          assertEquals(0.0, rates[cell][4], 0.0, "Absent oil must not receive pressure correction");
        }
        if (waterCut == 0.0) {
          assertEquals(0.0, rates[cell][5], 0.0, "Absent water must not receive pressure correction");
        }
      }
    }
  }

  /**
   * Construct source equations without phase change or thermal mechanisms.
   *
   * @param slip whether separate mechanical oil/water acceleration is enabled
   * @return configured source evaluator
   */
  private static TwoFluidConservationEquations createEquations(boolean slip) {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setEnableWaterOilSlip(slip);
    equations.setIncludeMassTransfer(false);
    equations.setIncludeEnergyEquation(false);
    return equations;
  }

  /**
   * Set an independently specified phase partition and known shear/perimeter values.
   *
   * @param gas gas holdup
   * @param oil oil holdup
   * @param water water holdup
   * @return section with initialized conservative inventory
   */
  private static TwoFluidSection createSection(double gas, double oil, double water) {
    TwoFluidSection section = new TwoFluidSection(0.0, 10.0, 0.2, 0.1);
    section.setGasHoldup(gas);
    section.setLiquidHoldup(oil + water);
    section.setOilHoldup(oil);
    section.setWaterHoldup(water);
    section.setWaterCut(oil + water > 0.0 ? water / (oil + water) : 0.0);
    section.setGasDensity(30.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(oil + water > 0.0 ? (oil * 800.0 + water * 1000.0) / (oil + water) : 800.0);
    section.setGasVelocity(2.0);
    section.setLiquidVelocity(1.0);
    section.setOilVelocity(1.0);
    section.setWaterVelocity(1.0);
    section.updateConservativeVariables();
    section.setGasWallShear(5.0);
    section.setLiquidWallShear(11.0);
    section.setInterfacialShear(3.0);
    section.setGasWettedPerimeter(0.25);
    section.setLiquidWettedPerimeter(0.35);
    section.setInterfacialWidth(0.1);
    return section;
  }
}
