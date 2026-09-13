package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitIntegrator.PreparedInterval;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PreparedStep;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.phase.Phase;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Exact accepted-interval publication with independent phase/component accounting. */
class TwoFluidUnsplitPublicationTest {
  private static final double START = 4.5;

  @Test
  void publishesAcceptedFaceTransfersAcrossNearbyTimeStepsAndSurvivesSerialization() {
    for (double duration : new double[] { 1.0 / 512.0, 1.0 / 256.0 }) {
      TwoFluidSection[] accepted = sections(false, 1.0);
      TwoFluidSection[] original = copy(accepted);
      PreparedInterval interval = interval(accepted, duration, false);
      TwoFluidUnsplitPublication publication = TwoFluidUnsplitPublication.prepare(interval, accepted, START, duration,
          1.0e-8);
      assertEquals(START, publication.getStartTimeSeconds(), 0.0);
      assertEquals(START + duration, publication.getEndTimeSeconds(), 0.0);
      assertEquals(duration, publication.getElapsedTimeSeconds(), 0.0);
      assertEquals(2, publication.getAcceptedSubsteps());
      double[] inlet = new double[3];
      double[] outlet = new double[3];
      double[] source = new double[3];
      for (PreparedStep step : interval.getSubsteps()) {
        double[][] faces = step.getEvaluation().getPhaseMassFaceFluxes();
        double[][] sources = step.getEvaluation().getPhaseMassSourcesPerLength();
        for (int phase = 0; phase < 3; phase++) {
          inlet[phase] += step.getTimeStepSeconds() * faces[0][phase];
          outlet[phase] += step.getTimeStepSeconds() * faces[accepted.length][phase];
          for (int cell = 0; cell < accepted.length; cell++) {
            source[phase] += step.getTimeStepSeconds() * sources[cell][phase] * accepted[cell].getLength();
          }
        }
      }
      assertArrayEquals(inlet, publication.getInletMassKg(), 0.0);
      assertArrayEquals(outlet, publication.getOutletMassKg(), 0.0);
      assertArrayEquals(source, publication.getSourceMassKg(), 0.0);
      assertArrayEquals(inventories(accepted), publication.getInitialMassKg(), 0.0);
      assertArrayEquals(inventories(interval.getEndpointSections()), publication.getFinalMassKg(), 0.0);
      for (int phase = 0; phase < 3; phase++) {
        assertEquals(outlet[phase] / duration, publication.getOutletMassFlowKgPerSecond()[phase], 0.0);
        double residual = publication.getFinalMassKg()[phase] - publication.getInitialMassKg()[phase] - inlet[phase]
            + outlet[phase] - source[phase];
        assertEquals(0.0, residual / Math.max(1.0, publication.getInitialMassKg()[phase]), 1.0e-8);
      }
      TwoFluidSection last = publication.getEndpointSections()[accepted.length - 1];
      assertNotEquals(last.getGasMassPerLength() * last.getGasVelocity(), publication.getOutletMassFlowKgPerSecond()[0],
          1.0e-8, "Interval-average accepted transport is not the final section velocity flux");
      publication.getEndpointSections()[0].setPressure(1.0);
      publication.getInitialMassKg()[0] = -1.0;
      publication.getFinalMassKg()[0] = -1.0;
      publication.getInletMassKg()[0] = -1.0;
      publication.getOutletMassKg()[0] = -1.0;
      publication.getSourceMassKg()[0] = -1.0;
      publication.getOutletMassFlowKgPerSecond()[0] = -1.0;
      assertArrayEquals(outlet, publication.getOutletMassKg(), 0.0);
      assertSectionsEqual(interval.getEndpointSections(), publication.getEndpointSections());
      TwoFluidUnsplitPublication restored = SerializationUtils.clone(publication);
      assertArrayEquals(outlet, restored.getOutletMassKg(), 0.0);
      assertArrayEquals(inlet, restored.getInletMassKg(), 0.0);
      assertArrayEquals(source, restored.getSourceMassKg(), 0.0);
      assertEquals(publication.getElapsedTimeSeconds(), restored.getElapsedTimeSeconds(), 0.0);
      assertSectionsEqual(publication.getEndpointSections(), restored.getEndpointSections());
      assertSectionsEqual(original, accepted);
    }
  }

  @Test
  void mismatchedClockGeometryAndConservativeStateRejectWithoutChangingInputs() {
    TwoFluidSection[] accepted = sections(false, 1.0);
    TwoFluidSection[] original = copy(accepted);
    double duration = 1.0 / 512.0;
    PreparedInterval interval = interval(accepted, duration, false);
    assertThrows(IllegalArgumentException.class,
        () -> TwoFluidUnsplitPublication.prepare(interval, accepted, START + duration, duration, 1.0e-8));
    assertThrows(IllegalArgumentException.class,
        () -> TwoFluidUnsplitPublication.prepare(interval, accepted, START, duration / 2.0, 1.0e-8));
    for (double invalid : new double[] { 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY }) {
      assertThrows(IllegalArgumentException.class,
          () -> TwoFluidUnsplitPublication.prepare(interval, accepted, START, duration, invalid));
    }
    TwoFluidSection[] moved = copy(accepted);
    moved[0].setPosition(10.0);
    assertThrows(IllegalStateException.class,
        () -> TwoFluidUnsplitPublication.prepare(interval, moved, START, duration, 1.0e-8));
    TwoFluidSection[] changedMomentum = copy(accepted);
    changedMomentum[0].setGasMomentumPerLength(accepted[0].getGasMomentumPerLength() + 1.0);
    assertThrows(IllegalStateException.class,
        () -> TwoFluidUnsplitPublication.prepare(interval, changedMomentum, START, duration, 1.0e-8));
    TwoFluidSection[] invalidState = copy(accepted);
    invalidState[0].setGasMassPerLength(-1.0);
    assertThrows(IllegalArgumentException.class,
        () -> TwoFluidUnsplitPublication.prepare(interval, invalidState, START, duration, 1.0e-8));
    assertEquals(-1.0, invalidState[0].getGasMassPerLength(), 0.0);
    assertSectionsEqual(original, accepted);
  }

  @Test
  void closedPureGasPreservesExactlyAbsentLiquidsAndPublishesAnEmptyCarrier() {
    TwoFluidSection[] accepted = sections(true, 0.0);
    PreparedInterval interval = interval(accepted, 0.2, true);
    for (TwoFluidSection section : accepted) {
      section.setOilDensity(0.0);
      section.setWaterDensity(0.0);
    }
    TwoFluidUnsplitPublication publication = TwoFluidUnsplitPublication.prepare(interval, accepted, START, 0.2, 1.0e-8);
    assertEquals(0.0, accepted[0].getOilDensity(), 0.0);
    assertEquals(0.0, accepted[0].getWaterDensity(), 0.0);
    assertArrayEquals(new double[3], publication.getOutletMassKg(), 0.0);
    assertArrayEquals(new double[3], publication.getInletMassKg(), 0.0);
    for (TwoFluidSection section : publication.getEndpointSections()) {
      assertEquals(0.0, section.getOilMassPerLength(), 0.0);
      assertEquals(0.0, section.getWaterMassPerLength(), 0.0);
      assertEquals(0.0, section.getOilMomentumPerLength(), 0.0);
      assertEquals(0.0, section.getWaterMomentumPerLength(), 0.0);
    }
    SystemInterface template = new SystemSrkEos(300.0, 50.0);
    template.addComponent("methane", 0.8);
    template.addComponent("nitrogen", 0.2);
    template.setMixingRule("classic");
    new ThermodynamicOperations(template).TPflash();
    template.setTotalFlowRate(0.0, "kg/sec");
    double[] before = template.getMolarComposition().clone();
    SystemInterface outlet = publication.createOutletFluid(template, null, 45.0e5, 295.0);
    assertEquals(0.0, outlet.getFlowRate("kg/sec"), 0.0);
    assertArrayEquals(before, outlet.getMolarComposition(), 1.0e-12);
    assertArrayEquals(before, template.getMolarComposition(), 0.0);
    assertEquals(0.0, template.getFlowRate("kg/sec"), 0.0);
    assertEquals(50.0e5, template.getPressure("Pa"), 0.0);
  }

  @Test
  void unequalUniformThreePhaseTransfersPublishEveryNamedComponentThroughDownstreamFlash() {
    SystemInterface template = fluid(false);
    SystemInterface inlet = fluid(true);
    assertEquals(3, template.getNumberOfPhases());
    TwoFluidSection[] accepted = sections(false, 1.0);
    double duration = 1.0 / 512.0;
    TwoFluidUnsplitPublication publication = TwoFluidUnsplitPublication.prepare(interval(accepted, duration, false),
        accepted, START, duration, 1.0e-8);
    double[] before = template.getMolarComposition().clone();
    SystemInterface outlet = publication.createOutletFluid(template, inlet, 45.0e5, 295.0);
    assertNotSame(template, outlet);
    assertArrayEquals(before, template.getMolarComposition(), 0.0);
    assertEquals(50.0e5, template.getPressure("Pa"), 0.0);
    assertEquals(45.0e5, outlet.getPressure("Pa"), 0.0);
    assertEquals(295.0, outlet.getTemperature("K"), 0.0);
    assertComponentFlows(template, publication, outlet);
    assertNotEquals(before[0], outlet.getMolarComposition()[0], 0.001);
    Stream downstream = new Stream("unsplit-published-outlet", outlet);
    downstream.run();
    assertComponentFlows(template, publication, downstream.getFluid());
    SystemInterface empty = template.clone();
    empty.setTotalFlowRate(0.0, "kg/sec");
    assertComponentFlows(template, publication, publication.createOutletFluid(empty, inlet, 45.0e5, 295.0));
    assertEquals(0.0, empty.getFlowRate("kg/sec"), 0.0);
    TwoFluidUnsplitPublication restored = SerializationUtils.clone(publication);
    assertComponentFlows(template, restored, restored.createOutletFluid(template, inlet, 45.0e5, 295.0));
  }

  @Test
  void nonuniformCellAndInletCompositionsFailBeforeAnyPublication() {
    TwoFluidSection[] accepted = sections(false, 0.0);
    accepted[1].setTemperature(305.0);
    PreparedInterval interval = interval(accepted, 1.0 / 512.0, true);
    TwoFluidUnsplitPublication publication = TwoFluidUnsplitPublication.prepare(interval, accepted, START, 1.0 / 512.0,
        1.0e-8);
    SystemInterface template = fluid(false);
    double[] before = template.getMolarComposition().clone();
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> publication.createOutletFluid(template, null, 45.0e5, 295.0));
    assertTrue(failure.getMessage().contains("Nonuniform frozen phase composition"));
    assertArrayEquals(before, template.getMolarComposition(), 0.0);
    accepted = sections(false, 0.0);
    TwoFluidUnsplitPublication uniform = TwoFluidUnsplitPublication.prepare(interval(accepted, 0.1, true), accepted,
        START, 0.1, 1.0e-8);
    SystemInterface changedInlet = template.clone();
    changedInlet.setTemperature(310.0);
    new ThermodynamicOperations(changedInlet).TPflash();
    assertThrows(IllegalStateException.class, () -> uniform.createOutletFluid(template, changedInlet, 45.0e5, 295.0));
    SystemInterface changedMolarMass = fluid(true);
    changedMolarMass.getPhase(0).getComponent("methane").setMolarMass(0.020);
    assertThrows(IllegalArgumentException.class,
        () -> uniform.createOutletFluid(template, changedMolarMass, 45.0e5, 295.0));
    SystemInterface invalidPhase = fluid(true);
    ((Phase) invalidPhase.getPhase(0)).numberOfMolesInPhase = Double.NaN;
    assertThrows(IllegalArgumentException.class,
        () -> uniform.createOutletFluid(template, invalidPhase, 45.0e5, 295.0));
    assertArrayEquals(new double[3], uniform.getOutletMassKg(), 0.0);
  }

  @Test
  void persistentReferenceConditionsPreservePhaseCompositionWhenCurrentPressureChanges() {
    TwoFluidSection[] frozen = sections(false, 1.0);
    TwoFluidSection[] current = copy(frozen);
    for (TwoFluidSection cell : current) {
      cell.setPressure(49.0e5);
      cell.setGasDensity(39.9);
      cell.updateConservativeVariables();
    }
    double duration = 1.0 / 4096.0;
    TwoFluidUnsplitPublication publication = TwoFluidUnsplitPublication.prepare(interval(current, duration, false),
        current, START, duration, 1.0e-8);
    SystemInterface template = fluid(false);
    assertThrows(IllegalStateException.class, () -> publication.createOutletFluid(template, template, 48.0e5, 295.0));
    SystemInterface outlet = publication.createOutletFluid(template, template, frozen, 48.0e5, 295.0);
    assertComponentFlows(template, publication, outlet);
    assertEquals(50.0e5, frozen[0].getPressure(), 0.0);
    assertEquals(49.0e5, current[0].getPressure(), 0.0);
    TwoFluidSection[] changedGeometry = copy(frozen);
    changedGeometry[0].setPosition(20.0);
    assertThrows(IllegalArgumentException.class,
        () -> publication.createOutletFluid(template, template, changedGeometry, 48.0e5, 295.0));
    TwoFluidSection[] changedTemperature = copy(frozen);
    changedTemperature[0].setTemperature(310.0);
    assertThrows(IllegalArgumentException.class,
        () -> publication.createOutletFluid(template, template, changedTemperature, 48.0e5, 295.0));
  }

  @Test
  void signedBackflowStaysInTheLedgerButCannotBeClampedIntoAForwardStream() {
    TwoFluidSection[] accepted = sections(false, -1.0);
    double duration = 1.0 / 4096.0;
    TwoFluidUnsplitPublication publication = TwoFluidUnsplitPublication.prepare(interval(accepted, duration, false),
        accepted, START, duration, 1.0e-8);
    double[] signedTransfer = publication.getOutletMassKg();
    assertTrue(signedTransfer[0] < 0.0);
    assertTrue(signedTransfer[1] < 0.0);
    assertTrue(signedTransfer[2] < 0.0);
    assertThrows(IllegalStateException.class, () -> publication.createOutletFluid(fluid(false), null, 45.0e5, 295.0));
    assertArrayEquals(signedTransfer, publication.getOutletMassKg(), 0.0);
  }

  private static void assertComponentFlows(SystemInterface template, TwoFluidUnsplitPublication publication,
      SystemInterface outlet) {
    String[] phases = { "gas", "oil", "aqueous" };
    double totalMassFlow = 0.0;
    for (double phaseFlow : publication.getOutletMassFlowKgPerSecond()) {
      totalMassFlow += phaseFlow;
    }
    assertEquals(totalMassFlow, outlet.getFlowRate("kg/sec"), 1.0e-10);
    for (int component = 0; component < template.getNumberOfComponents(); component++) {
      String name = template.getPhase(0).getComponent(component).getComponentName();
      double molarMass = template.getPhase(0).getComponent(component).getMolarMass();
      double expectedFlow = 0.0;
      for (int phase = 0; phase < phases.length; phase++) {
        PhaseInterface source = template.getPhase(phases[phase]);
        expectedFlow += publication.getOutletMassFlowKgPerSecond()[phase] * source.getComponent(name).getx() * molarMass
            / source.getMolarMass();
      }
      double actualFlow = outlet.getTotalNumberOfMoles() * outlet.getPhase(0).getComponent(name).getz() * molarMass;
      assertEquals(expectedFlow, actualFlow, 1.0e-10, name);
    }
  }

  private static PreparedInterval interval(TwoFluidSection[] accepted, double duration, boolean closed) {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(false);
    equations.setIncludeMassTransfer(false);
    equations.setClosedBoundaries(closed, closed);
    equations.setAllowOutletPhaseBackflow(true);
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setRelativeTolerance(1.0e-10);
    TwoFluidUnsplitIntegrator integrator = new TwoFluidUnsplitIntegrator(equations,
        (cell, state, pressure, time) -> new double[] { 40.0 + 1.0e-6 * (pressure - 5.0e6), 700.0, 1000.0 }, solver);
    return integrator.prepareInterval(accepted, 10.0, duration, START, 5.0e6, !closed, 1.0e-8, duration / 2.0);
  }

  private static TwoFluidSection[] sections(boolean gasOnly, double velocityScale) {
    TwoFluidSection[] cells = { new TwoFluidSection(2.5, 5.0, 0.1, 0.0), new TwoFluidSection(10.0, 10.0, 0.1, 0.0),
        new TwoFluidSection(22.5, 15.0, 0.1, 0.0) };
    for (TwoFluidSection cell : cells) {
      cell.setPressure(5.0e6);
      cell.setTemperature(300.0);
      cell.setGasDensity(40.0);
      cell.setOilDensity(700.0);
      cell.setWaterDensity(1000.0);
      cell.setLiquidDensity(850.0);
      cell.setGasViscosity(1.2e-5);
      cell.setOilViscosity(1.0e-3);
      cell.setWaterViscosity(1.0e-3);
      cell.setLiquidViscosity(1.0e-3);
      cell.setGasSoundSpeed(300.0);
      cell.setLiquidSoundSpeed(1200.0);
      cell.setSurfaceTension(0.02);
      cell.setGasHoldup(gasOnly ? 1.0 : 0.6);
      cell.setLiquidHoldup(gasOnly ? 0.0 : 0.4);
      cell.setOilHoldup(gasOnly ? 0.0 : 0.2);
      cell.setWaterHoldup(gasOnly ? 0.0 : 0.2);
      cell.setWaterCut(gasOnly ? 0.0 : 0.5);
      cell.setOilFractionInLiquid(gasOnly ? 1.0 : 0.5);
      cell.setGasVelocity(3.0 * velocityScale);
      cell.setLiquidVelocity(0.5 * velocityScale);
      cell.setOilVelocity(0.55 * velocityScale);
      cell.setWaterVelocity(0.45 * velocityScale);
      cell.updateConservativeVariables();
      cell.updateDerivedQuantities();
    }
    return cells;
  }

  private static SystemInterface fluid(boolean reversed) {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    if (reversed) {
      fluid.addComponent("water", 0.1);
      fluid.addComponent("n-heptane", 0.2);
      fluid.addComponent("methane", 0.7);
    } else {
      fluid.addComponent("methane", 0.7);
      fluid.addComponent("n-heptane", 0.2);
      fluid.addComponent("water", 0.1);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.setTotalFlowRate(1.0, "kg/sec");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    return fluid;
  }

  private static double[] inventories(TwoFluidSection[] sections) {
    double[] result = new double[3];
    for (TwoFluidSection cell : sections) {
      double[] state = cell.getStateVector();
      for (int phase = 0; phase < 3; phase++) {
        result[phase] += state[phase] * cell.getLength();
      }
    }
    return result;
  }

  private static TwoFluidSection[] copy(TwoFluidSection[] sections) {
    TwoFluidSection[] result = new TwoFluidSection[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
      result[cell] = sections[cell].clone();
    }
    return result;
  }

  private static void assertSectionsEqual(TwoFluidSection[] expected, TwoFluidSection[] actual) {
    assertEquals(expected.length, actual.length);
    for (int cell = 0; cell < expected.length; cell++) {
      assertArrayEquals(expected[cell].getStateVector(), actual[cell].getStateVector(), 0.0);
      assertEquals(expected[cell].getPressure(), actual[cell].getPressure(), 0.0);
      assertEquals(expected[cell].getTemperature(), actual[cell].getTemperature(), 0.0);
    }
  }
}
