package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;
import neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction;

/**
 * Physical disappearance and power limits of unresolved gas/bulk-liquid drag with independent liquid velocities.
 */
class TwoFluidMixtureDragPartitionTest {
  @Test
  void bothLiquidDisappearanceLimitsRetainFiniteCommonAccelerationAndTraceForce() {
    for (boolean traceWater : new boolean[] { false, true }) {
      for (double fraction : new double[] { 0.0, 1.0e-24, 1.0e-16, 1.0e-10, 0.01, 0.5 }) {
        double oil = 0.6 * (traceWater ? 1.0 - fraction : fraction);
        double water = 0.6 * (traceWater ? fraction : 1.0 - fraction);
        TwoFluidSection section = section(oil, water, 2.0, 1.0, 1.0);
        double[] state = section.getStateVector();
        double[] force = dragIncrement(equations(), section, 8.0);
        double totalLiquidMass = state[1] + state[2];
        // A specified 8 Pa shear on 0.125 m interface gives exactly 1 N/m reaction.
        assertEquals(-1.0, force[3], 0.0);
        assertEquals(1.0, force[4] + force[5], 0.0);
        for (int phase = 1; phase < 3; phase++) {
          if (state[phase] == 0.0) {
            assertEquals(0.0, force[phase + 3], 0.0);
          } else {
            assertTrue(force[phase + 3] > 0.0, "Every positive liquid inventory receives positive drag");
            assertEquals(1.0 / totalLiquidMass, force[phase + 3] / state[phase], 1.0e-14 / totalLiquidMass);
          }
        }
        assertArrayEquals(state, section.getStateVector(), 0.0, "A source evaluation must not advance inventory");
      }
    }
  }

  @Test
  void independentCountercurrentLiquidVelocitiesCannotMakeBulkGasDragCreateMechanicalPower() {
    TwoFluidSection section = section(0.3, 0.3, 0.4, 2.0, -1.0);
    assertEquals(1.0 / 3.0, section.getLiquidVelocity(), 1.0e-15);
    assertTrue(section.getGasVelocity() > section.getLiquidVelocity());
    double[] state = section.getStateVector();
    double[] force = dragIncrement(equations(), section, 8.0);
    double power = force[3] * section.getGasVelocity() + force[4] * section.getOilVelocity()
        + force[5] * section.getWaterVelocity();
    double receivingForce = force[4] + force[5];
    assertEquals(receivingForce * (section.getLiquidVelocity() - section.getGasVelocity()), power, 1.0e-13);
    assertTrue(power < 0.0, "Gas drag must dissipate kinetic energy at this bulk-slip state");
    double areaWeightedReceivingVelocity = 0.5 * (section.getOilVelocity() + section.getWaterVelocity());
    assertTrue(receivingForce * (areaWeightedReceivingVelocity - section.getGasVelocity()) > 0.0,
        "This state independently distinguishes mass weighting from holdup weighting");
    assertArrayEquals(state, section.getStateVector(), 0.0);
  }

  @Test
  void reversingVelocitiesAndShearReversesEachReactionAndPreservesOtherSources() {
    TwoFluidSection forward = section(0.3, 0.3, 0.4, 2.0, -1.0);
    TwoFluidSection reverse = section(0.3, 0.3, -0.4, -2.0, 1.0);
    double[] forwardForce = dragIncrement(equations(), forward, 8.0);
    double[] reverseForce = dragIncrement(equations(), reverse, -8.0);
    for (int phase = 0; phase < 3; phase++) {
      assertEquals(-forwardForce[phase + 3], reverseForce[phase + 3], 1.0e-13);
    }
    assertEquals(-reverseForce[3], reverseForce[4] + reverseForce[5], 1.0e-13);
  }

  @Test
  void linearDragRelaxationUsesTotalLiquidMassAcrossBothTraceLimits() throws Exception {
    double coefficient = 7.0;
    TwoFluidConservationEquations equations = linearDragEquations(coefficient);
    for (boolean traceWater : new boolean[] { false, true }) {
      for (double fraction : new double[] { 0.0, 1.0e-24, 1.0e-16, 1.0e-10, 0.01, 0.5 }) {
        double oil = 0.6 * (traceWater ? 1.0 - fraction : fraction);
        double water = 0.6 * (traceWater ? fraction : 1.0 - fraction);
        TwoFluidSection section = section(oil, water, 2.0, 1.0, 1.0);
        double[] state = section.getStateVector();
        // The independent two-body linear-drag eigenvalue uses gas and total liquid inertia.
        double expectedRate = coefficient * (1.0 / state[0] + 1.0 / (state[1] + state[2]));
        double timeStep = equations.calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] { section });
        assertTrue(Double.isFinite(timeStep) && timeStep > 0.0);
        // Centered probes of the zero-slip quadratic oil-water law leave an O(1e-6) derivative residue.
        assertEquals(1.0 / expectedRate, timeStep, 1.0e-7 / expectedRate);
        assertArrayEquals(state, section.getStateVector(), 0.0);
        assertBulkVelocity(section);
      }
    }
  }

  @Test
  void primitiveHoldupCannotReplaceMissingConservativeLiquidInertia() {
    TwoFluidSection section = section(0.3, 0.3, 2.0, 1.0, 1.0);
    double[] invalid = section.getStateVector();
    invalid[1] = 0.0;
    invalid[2] = 0.0;
    invalid[4] = 0.0;
    invalid[5] = 0.0;
    section.setStateVector(invalid);
    section.setInterfacialShear(8.0);
    assertThrows(IllegalStateException.class, () -> equations().calcSourceTerms(new TwoFluidSection[] { section }));
    assertArrayEquals(invalid, section.getStateVector(), 0.0);
  }

  /** Compare the same physical state with only gas-liquid shear changed; oil-water forces cancel in the difference. */
  private static double[] dragIncrement(TwoFluidConservationEquations equations, TwoFluidSection section,
      double shear) {
    assertBulkVelocity(section);
    section.setInterfacialShear(0.0);
    double[] without = equations.calcSourceTerms(new TwoFluidSection[] { section })[0];
    section.setInterfacialShear(shear);
    double[] with = equations.calcSourceTerms(new TwoFluidSection[] { section })[0];
    double[] change = new double[7];
    for (int variable = 0; variable < change.length; variable++) {
      change[variable] = with[variable] - without[variable];
    }
    for (int phase = 0; phase < 3; phase++) {
      assertEquals(without[phase], with[phase], 0.0, "Drag changes no phase mass source");
    }
    assertEquals(without[6], with[6], 0.0, "Interphase drag changes no total energy source");
    return change;
  }

  private static TwoFluidConservationEquations equations() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setEnableWaterOilSlip(true);
    equations.setIncludeMassTransfer(false);
    equations.setIncludeEnergyEquation(true);
    equations.setHeatTransferCoefficient(0.0);
    return equations;
  }

  /** Build a strict conservative state directly so a trace oil phase is not lost through 1-waterCut cancellation. */
  private static TwoFluidSection section(double oil, double water, double gasVelocity, double oilVelocity,
      double waterVelocity) {
    TwoFluidSection section = new TwoFluidSection(0.5, 1.0, 0.2, 0.0);
    section.setPressure(1.0e5);
    section.setGasDensity(30.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setGasViscosity(1.0e-5);
    section.setLiquidViscosity(1.0e-3);
    section.setOilViscosity(1.0e-3);
    section.setWaterViscosity(1.0e-3);
    section.setSurfaceTension(0.02);
    double area = section.getArea();
    double gasMass = 0.4 * 30.0 * area;
    double oilMass = oil * 800.0 * area;
    double waterMass = water * 1000.0 * area;
    double energy = gasMass * (2.0e5 + 0.5 * gasVelocity * gasVelocity)
        + oilMass * (1.0e5 + 0.5 * oilVelocity * oilVelocity)
        + waterMass * (1.0e5 + 0.5 * waterVelocity * waterVelocity) - 1.0e5 * area;
    section.setConservativeEndpoint(new double[] { gasMass, oilMass, waterMass, gasMass * gasVelocity,
        oilMass * oilVelocity, waterMass * waterVelocity, energy }, 1.0e-12);
    section.setFlowRegime(FlowRegime.STRATIFIED_SMOOTH);
    section.setRegimeWeights(null);
    section.setGasWallShear(0.0);
    section.setLiquidWallShear(0.0);
    section.setInterfacialWidth(0.125);
    assertBulkVelocity(section);
    return section;
  }

  private static void assertBulkVelocity(TwoFluidSection section) {
    double[] state = section.getStateVector();
    assertEquals((state[4] + state[5]) / (state[1] + state[2]), section.getLiquidVelocity(), 0.0,
        "The bulk drag velocity must use the same conservative liquid inertia as its reaction");
  }

  /** Replace empirical laws with specified linear drag so the relaxation reference is independent of their regimes. */
  private static TwoFluidConservationEquations linearDragEquations(final double coefficient) throws Exception {
    TwoFluidConservationEquations equations = equations();
    Field wall = TwoFluidConservationEquations.class.getDeclaredField("wallFriction");
    wall.setAccessible(true);
    wall.set(equations, new WallFriction() {
      private static final long serialVersionUID = 1L;

      @Override
      public WallFrictionResult calculate(FlowRegime regime, double gasVelocity, double liquidVelocity,
          double gasDensity, double liquidDensity, double gasViscosity, double liquidViscosity, double liquidHoldup,
          double diameter, double roughness) {
        return new WallFrictionResult();
      }
    });
    Field friction = TwoFluidConservationEquations.class.getDeclaredField("interfacialFriction");
    friction.setAccessible(true);
    friction.set(equations, new InterfacialFriction() {
      private static final long serialVersionUID = 1L;

      @Override
      public InterfacialFrictionResult calculate(FlowRegime regime, double gasVelocity, double liquidVelocity,
          double gasDensity, double liquidDensity, double gasViscosity, double liquidViscosity, double liquidHoldup,
          double diameter, double surfaceTension) {
        InterfacialFrictionResult result = new InterfacialFrictionResult();
        result.interfacialShear = coefficient * (gasVelocity - liquidVelocity);
        result.interfacialAreaPerLength = 1.0;
        return result;
      }
    });
    return equations;
  }
}
