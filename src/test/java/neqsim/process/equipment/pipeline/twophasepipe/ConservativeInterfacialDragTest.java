package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;

/**
 * Conservative source contracts for frozen-resistance interfacial drag through phase appearance.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ConservativeInterfacialDragTest {
  /** A newly condensed annular trace must not acquire an explicit finite-inertia impulse. */
  @Test
  void appearingTraceContractsSlipWithoutIncreasingKineticEnergyOrCreatingMass() {
    TwoFluidSection section = section(119.0, 1.8347723551540115e-10, 0.0, 20.0, 0.0, 0.0);
    double[] before = section.getStateVector();
    TwoFluidConservationEquations explicit = equations(false);
    double[] source = explicit.calcSourceTerms(new TwoFluidSection[] {section})[0];
    double[] unstable = before.clone();
    unstable[3] += source[3] / 24.0;
    unstable[4] += source[4] / 24.0;
    assertTrue(kinetic(unstable) > kinetic(before) * 1e4, "The regression must expose the explicit drag instability");
    double[] after = equations(true).applyConservativeFriction(new double[][] {before}, new TwoFluidSection[] {section},
        1.0 / 24.0)[0];
    audit(before, after);
    assertTrue(after[4] / after[1] > 0.0 && after[4] / after[1] <= before[3] / before[0]);
    assertTrue(after[3] / after[0] >= after[4] / after[1]);
    assertArrayEquals(before, section.getStateVector(), 0.0, "The closure probe must not mutate the supplied cell");
  }

  /** Both signs of phase slip must relax without overshoot, even below legacy primitive mass thresholds. */
  @Test
  void reversedSlipAndArbitrarilySmallPositiveInertiaRemainPassive() {
    for (double sign : new double[] {-1.0, 1.0}) {
      for (double mass : new double[] {1e-8, 1e-16, 1e-30, 1e-200}) {
        TwoFluidSection section = section(12.0, mass, 0.0, sign * 12.0, -sign * 2.0, 0.0);
        double[] before = section.getStateVector();
        double[] after = equations(true).applyConservativeFriction(new double[][] {before},
            new TwoFluidSection[] {section}, 1.0)[0];
        audit(before, after);
        assertTrue(Double.isFinite(after[4] / mass));
        assertTrue(Math.abs(after[3] / after[0] - after[4] / mass) <= 14.0);
        assertEquals(mass, after[1], 0.0);
      }
    }
  }

  /** Gas-liquid exchange is rank-one in bulk liquid inertia; a separate oil-water source owns relative slip. */
  @Test
  void bulkLiquidDragPreservesIndependentLiquidSlipUntilOilWaterDragIsSelected() {
    TwoFluidSection section = section(30.0, 3.0, 5.0, 15.0, -3.0, 2.0);
    double[] before = section.getStateVector();
    TwoFluidConservationEquations equations = equations(true);
    equations.setEnableWaterOilSlip(false);
    double[] bulk = equations.applyConservativeFriction(new double[][] {before}, new TwoFluidSection[] {section},
        0.2)[0];
    audit(before, bulk);
    assertEquals(-5.0, bulk[4] / bulk[1] - bulk[5] / bulk[2], 2e-14);
    double oilAcceleration = (bulk[4] - before[4]) / before[1];
    double waterAcceleration = (bulk[5] - before[5]) / before[2];
    assertEquals(oilAcceleration, waterAcceleration, 2e-14);
    equations.setEnableWaterOilSlip(true);
    double[] separated = equations.applyConservativeFriction(new double[][] {before}, new TwoFluidSection[] {section},
        0.2)[0];
    audit(before, separated);
    assertEquals(bulk[4] + bulk[5], separated[4] + separated[5], 2e-14);
    assertTrue(Math.abs(separated[4] / separated[1] - separated[5] / separated[2]) < 5.0);
  }

  /** Exactly absent phases can receive only a conservative rounding correction, never an invented mass. */
  @Test
  void zeroDurationTransfersOnlyAbsentPhaseRoundoffAndRejectsMaterialMomentum() {
    TwoFluidSection section = section(119.0, 0.0, 0.0, 20.0, 0.0, 0.0);
    double[] before = section.getStateVector();
    before[4] = 3.365806529e-28;
    double[] after = equations(true).applyConservativeFriction(new double[][] {before}, new TwoFluidSection[] {section},
        0.0)[0];
    assertEquals(before[3] + before[4], after[3], 0.0);
    assertEquals(0.0, after[4], 0.0);
    assertEquals(0.0, after[1], 0.0);
    assertEquals(before[6], after[6], 0.0);
    before[4] = 1.0;
    assertThrows(IllegalArgumentException.class,
        () -> equations(true).applyConservativeFriction(new double[][] {before}, new TwoFluidSection[] {section}, 0.0));
  }

  /** The linearly implicit source retains the configured blended instantaneous force as its small-step limit. */
  @Test
  void smallStepUsesTheNativeBlendedForceAndRemovesItFromTheExplicitSourceExactlyOnce() {
    TwoFluidSection section = section(30.0, 3.0, 0.0, 15.0, 0.0, 0.0);
    Map<FlowRegime, Double> weights = new EnumMap<>(FlowRegime.class);
    weights.put(FlowRegime.ANNULAR, 0.4);
    weights.put(FlowRegime.STRATIFIED_SMOOTH, 0.6);
    section.setRegimeWeights(weights);
    double force = 0.0;
    double area = 0.0;
    for (Map.Entry<FlowRegime, Double> entry : weights.entrySet()) {
      InterfacialFriction.InterfacialFrictionResult result = new InterfacialFriction().calculate(entry.getKey(),
          section.getGasVelocity(), section.getLiquidVelocity(), section.getGasDensity(), section.getLiquidDensity(),
          section.getGasViscosity(), section.getLiquidViscosity(), section.getLiquidHoldup(), section.getDiameter(),
          section.getSurfaceTension());
      force += entry.getValue() * result.interfacialShear * result.interfacialAreaPerLength;
      area += entry.getValue() * result.interfacialAreaPerLength;
    }
    section.setInterfacialWidth(area);
    section.setInterfacialShear(force / area);
    double[] before = section.getStateVector();
    double[] after = equations(true).applyConservativeFriction(new double[][] {before}, new TwoFluidSection[] {section},
        1e-7)[0];
    assertEquals(-force, (after[3] - before[3]) / 1e-7, Math.abs(force) * 2e-5);
    TwoFluidConservationEquations enabled = equations(true);
    TwoFluidConservationEquations disabled = equations(false);
    double[] explicit = disabled.calcSourceTerms(new TwoFluidSection[] {section})[0];
    double[] split = enabled.calcSourceTerms(new TwoFluidSection[] {section})[0];
    assertEquals(-force, explicit[3] - split[3], Math.abs(force) * 1e-12);
    assertEquals(force, explicit[4] - split[4], Math.abs(force) * 1e-12);
  }

  /** A disabled source preserves identity, and serialization retains the enabled source owner. */
  @Test
  void disabledIdentityAndSerializedOwnershipArePreserved() throws Exception {
    TwoFluidSection section = section(30.0, 3.0, 0.0, 15.0, 0.0, 0.0);
    double[][] before = {section.getStateVector()};
    assertSame(before, equations(false).applyConservativeFriction(before, new TwoFluidSection[] {section}, 0.1));
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(equations(true));
    }
    TwoFluidConservationEquations restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (TwoFluidConservationEquations) input.readObject();
    }
    assertTrue(restored.isConservativeFrictionEnabled());
    double[] after = restored.applyConservativeFriction(before, new TwoFluidSection[] {section}, 0.1)[0];
    audit(before[0], after);
  }

  /**
   * Create the source owner without equilibrium or thermal sources.
   *
   * @param enabled whether the source split owns interfacial forces
   * @return configured equations
   */
  private static TwoFluidConservationEquations equations(boolean enabled) {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeMassTransfer(false);
    equations.setIncludeEnergyEquation(true);
    equations.setConservativeFrictionEnabled(enabled);
    try {
      java.lang.reflect.Field field = TwoFluidConservationEquations.class.getDeclaredField("wallFriction");
      field.setAccessible(true);
      field.set(equations, new ZeroWallFriction());
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError(failure);
    }
    return equations;
  }

  /**
   * Force-free stationary wall fixture isolates internal phase exchange from external wall impulse.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static class ZeroWallFriction extends neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction {
    private static final long serialVersionUID = 1L;

    /** {@inheritDoc} */
    @Override
    public WallFrictionResult calculate(FlowRegime regime, double gasVelocity, double liquidVelocity, double gasDensity,
        double liquidDensity, double gasViscosity, double liquidViscosity, double liquidHoldup, double diameter,
        double roughness) {
      return new WallFrictionResult();
    }
  }

  /**
   * Construct a frozen annular closure state with exact prescribed conservative phase inertias.
   *
   * @param gasMass gas mass per length
   * @param oilMass oil mass per length
   * @param waterMass water mass per length
   * @param gasVelocity gas speed
   * @param oilVelocity oil speed
   * @param waterVelocity water speed
   * @return private finite-volume state
   */
  private static TwoFluidSection section(double gasMass, double oilMass, double waterMass, double gasVelocity,
      double oilVelocity, double waterVelocity) {
    TwoFluidSection section = new TwoFluidSection(5.0, 10.0, 1.0148, 0.0);
    section.setGasDensity(150.0);
    section.setOilDensity(500.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(500.0);
    section.setGasViscosity(1e-5);
    section.setLiquidViscosity(5e-4);
    section.setOilViscosity(5e-4);
    section.setWaterViscosity(1e-3);
    section.setGasVelocity(gasVelocity);
    section.setLiquidVelocity(
        oilMass + waterMass > 0.0 ? (oilMass * oilVelocity + waterMass * waterVelocity) / (oilMass + waterMass) : 0.0);
    section.setOilVelocity(oilVelocity);
    section.setWaterVelocity(waterVelocity);
    section.setSurfaceTension(0.02);
    section.setPressure(1e7);
    section.setTemperature(277.15);
    section.setFlowRegime(FlowRegime.ANNULAR);
    section.setRegimeWeights(null);
    section.setStateVector(new double[] {gasMass, oilMass, waterMass, gasMass * gasVelocity, oilMass * oilVelocity,
        waterMass * waterVelocity, 1e6});
    section.extractPrimitiveVariables();
    section.setGasVelocity(gasVelocity);
    section.setOilVelocity(oilVelocity);
    section.setWaterVelocity(waterVelocity);
    section.setLiquidVelocity(
        oilMass + waterMass > 0.0 ? (oilMass * oilVelocity + waterMass * waterVelocity) / (oilMass + waterMass) : 0.0);
    section.updateStratifiedGeometry();
    InterfacialFriction.InterfacialFrictionResult force = new InterfacialFriction().calculate(FlowRegime.ANNULAR,
        gasVelocity, section.getLiquidVelocity(), section.getGasDensity(), section.getLiquidDensity(),
        section.getGasViscosity(), section.getLiquidViscosity(), section.getLiquidHoldup(), section.getDiameter(),
        section.getSurfaceTension());
    section.setInterfacialShear(force.interfacialShear);
    section.setInterfacialWidth(force.interfacialAreaPerLength);
    return section;
  }

  /**
   * Verify the invariants independently from the source implementation.
   *
   * @param before original conservative state
   * @param after source-advanced state
   */
  private static void audit(double[] before, double[] after) {
    for (int phase = 0; phase < 3; phase++) {
      assertEquals(before[phase], after[phase], 0.0);
      assertTrue(Double.isFinite(after[phase + 3]));
    }
    assertEquals(before[6], after[6], 0.0);
    assertEquals(before[3] + before[4] + before[5], after[3] + after[4] + after[5],
        64.0 * Math.ulp(Math.abs(before[3]) + Math.abs(before[4]) + Math.abs(before[5])));
    assertTrue(kinetic(after) <= kinetic(before) + 64.0 * Math.ulp(kinetic(before)));
  }

  /**
   * Calculate kinetic energy from positive conservative phase masses.
   *
   * @param state conservative variables
   * @return kinetic energy per length
   */
  private static double kinetic(double[] state) {
    double result = 0.0;
    for (int phase = 0; phase < 3; phase++) {
      if (state[phase] > 0.0) {
        result += 0.5 * state[phase + 3] * (state[phase + 3] / state[phase]);
      }
    }
    return result;
  }
}
