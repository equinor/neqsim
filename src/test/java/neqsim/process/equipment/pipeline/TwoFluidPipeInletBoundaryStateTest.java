package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe.BoundaryCondition;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Prescribed inlet fluxes must not overwrite an evolving finite-volume cell's thermodynamic or conservative state. */
class TwoFluidPipeInletBoundaryStateTest {
  @Test
  void coupledFlowBoundaryPreservesCellStateAndPrescribesThreePhaseMassFlux() throws Exception {
    for (BoundaryCondition boundary : new BoundaryCondition[] {BoundaryCondition.STREAM_CONNECTED,
        BoundaryCondition.CONSTANT_FLOW}) {
      TwoFluidPipe pipe = createPipe(boundary, -1);
      TwoFluidSection[] sections = (TwoFluidSection[]) read(pipe, "sections");
      TwoFluidSection inletCell = sections[0];
      double[] original = inletCell.getStateVector();
      double[] originalProperties = cellProperties(inletCell);
      double[] expectedFlux = prescribedPhaseMassFlows(pipe, boundary);

      for (int repeat = 0; repeat < 3; repeat++) {
        applyBoundaryConditions(pipe);
        assertArrayEquals(original, inletCell.getStateVector(), 0.0,
            "A coupled flow boundary must preserve finite-volume phase mass and momentum: " + boundary);
        assertArrayEquals(originalProperties, cellProperties(inletCell), 0.0,
            "Feed pressure/densities must not overwrite the evolving control volume: " + boundary);
        TwoFluidConservationEquations equations = (TwoFluidConservationEquations) read(pipe, "equations");
        double[] actualFlux = equations.calcPhaseMassFaceFluxes(sections, sections[0].getLength())[0];
        assertArrayEquals(expectedFlux, actualFlux, 1e-12);
        for (double phaseFlux : actualFlux) {
          assertTrue(phaseFlux > 0.0);
        }
      }
    }
  }

  @Test
  void prescribedFeedCanIntroduceEachAbsentPhaseWithoutChangingCellInventoryAtBoundaryApplication() throws Exception {
    for (BoundaryCondition boundary : new BoundaryCondition[] {BoundaryCondition.STREAM_CONNECTED,
        BoundaryCondition.CONSTANT_FLOW}) {
      for (int absentPhase = 0; absentPhase < 3; absentPhase++) {
        TwoFluidPipe pipe = createPipe(boundary, absentPhase);
        TwoFluidSection[] sections = (TwoFluidSection[]) read(pipe, "sections");
        double[] original = sections[0].getStateVector();
        assertEquals(0.0, original[absentPhase], 0.0);
        double[] expectedFlux = prescribedPhaseMassFlows(pipe, boundary);

        applyBoundaryConditions(pipe);
        assertArrayEquals(original, sections[0].getStateVector(), 0.0);
        TwoFluidConservationEquations equations = (TwoFluidConservationEquations) read(pipe, "equations");
        equations.setIncludeMassTransfer(false);
        equations.setIncludeEnergyEquation(false);
        equations.setEnableInterfacialPressure(false);
        double[][] rates = equations.calcRHS(sections, sections[0].getLength());

        // Both cells lack this phase, so no internal phase transport or transfer source exists.
        // The new phase enters only through the prescribed boundary face mass flux.
        assertTrue(expectedFlux[absentPhase] > 0.0);
        assertEquals(expectedFlux[absentPhase] / sections[0].getLength(), rates[0][absentPhase], 1e-12,
            "The prescribed feed must introduce phase " + absentPhase + " for " + boundary);
        assertEquals(0.0, sections[0].getStateVector()[absentPhase], 0.0,
            "Evaluating boundary flux/RHS must not preempt the conservative update");
      }
    }
  }

  @Test
  void prescribedNegativeInletFlowUsesCellPhasesAndRetainsExactTotalRate() throws Exception {
    TwoFluidPipe pipe = createPipe(BoundaryCondition.CONSTANT_FLOW, -1);
    pipe.setInletMassFlow(-0.17);
    TwoFluidSection[] sections = (TwoFluidSection[]) read(pipe, "sections");
    TwoFluidSection first = sections[0];
    double[] original = first.getStateVector();

    applyBoundaryConditions(pipe);

    assertArrayEquals(original, first.getStateVector(), 0.0,
        "A prescribed outflow must not replace the first physical cell");
    TwoFluidConservationEquations equations = (TwoFluidConservationEquations) read(pipe, "equations");
    double[] actual = equations.calcPhaseMassFaceFluxes(sections, first.getLength())[0];
    double[] phaseInertia = {first.getGasHoldup() * first.getGasDensity(), first.getOilHoldup() * first.getOilDensity(),
        first.getWaterHoldup() * first.getWaterDensity()};
    double totalInertia = phaseInertia[0] + phaseInertia[1] + phaseInertia[2];
    for (int phase = 0; phase < 3; phase++) {
      assertEquals(-0.17 * phaseInertia[phase] / totalInertia, actual[phase], 1.0e-12);
      assertTrue(actual[phase] < 0.0);
    }
    assertEquals(-0.17, actual[0] + actual[1] + actual[2], 1.0e-12);
  }

  /** A pressure reservoir must admit and release mass according to the signed acoustic face solution. */
  @Test
  void pressureDrivenInletMatchesAnalyticalAcousticCompatibilityInBothDirections() {
    TwoFluidSection cell = analyticalBoundarySection(1.0e5, 8.0, 800.0, 1000.0, 300.0, 1200.0);
    TwoFluidSection reservoir = analyticalBoundarySection(1.2e5, 10.0, 750.0, 980.0, 320.0, 1250.0);
    cell.setGasVelocity(2.0);
    cell.setOilVelocity(-0.2);
    cell.setWaterVelocity(0.1);
    cell.updateConservativeVariables();

    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setInletPressureBoundaryState(reservoir);
    double[] inflow = equations.calcPhaseMassFaceFluxes(new TwoFluidSection[] {cell}, cell.getLength())[0];
    double expectedGasVelocity = (cell.getGasDensity() * cell.getGasSoundSpeed() * cell.getGasVelocity()
        + reservoir.getPressure() - cell.getPressure())
        / (reservoir.getGasDensity() * reservoir.getGasSoundSpeed() + cell.getGasDensity() * cell.getGasSoundSpeed());
    assertEquals(reservoir.getGasHoldup() * reservoir.getGasDensity() * expectedGasVelocity * reservoir.getArea(),
        inflow[0], 1.0e-12);
    assertTrue(inflow[0] > 0.0);

    reservoir.setPressure(0.8e5);
    reservoir.setGasVelocity(99.0);
    equations.setInletPressureBoundaryState(reservoir);
    double[] outflow = equations.calcPhaseMassFaceFluxes(new TwoFluidSection[] {cell}, cell.getLength())[0];
    double expectedOutflowVelocity = (cell.getGasDensity() * cell.getGasSoundSpeed() * cell.getGasVelocity()
        + reservoir.getPressure() - cell.getPressure())
        / (reservoir.getGasDensity() * reservoir.getGasSoundSpeed() + cell.getGasDensity() * cell.getGasSoundSpeed());
    assertEquals(cell.getGasHoldup() * cell.getGasDensity() * expectedOutflowVelocity * cell.getArea(), outflow[0],
        1.0e-12);
    assertTrue(outflow[0] < 0.0, "Reversed pressure gradient must use the pipe cell as signed donor");
  }

  /** The pipe-level pressure boundary must preserve the cell and flash a defensive reservoir at ramp pressure. */
  @Test
  void constantPressureBoundaryCreatesSignedFluxWithoutReplacingCellInventory() throws Exception {
    TwoFluidPipe pipe = createPipe(BoundaryCondition.CONSTANT_PRESSURE, -1);
    write(pipe, "useConservativeEnergyRecovery", true);
    TwoFluidSection[] sections = (TwoFluidSection[]) read(pipe, "sections");
    for (TwoFluidSection section : sections) {
      section.setGasVelocity(0.0);
      section.setOilVelocity(0.0);
      section.setWaterVelocity(0.0);
      section.setLiquidVelocity(0.0);
      section.updateConservativeVariables();
    }
    double[] original = sections[0].getStateVector();
    pipe.setInletBoundaryFluid(pipe.getInletStream().getFluid());
    pipe.setInletPressure(1.2e5);
    applyBoundaryConditions(pipe);
    TwoFluidConservationEquations equations = (TwoFluidConservationEquations) read(pipe, "equations");
    double[] inflow = equations.calcPhaseMassFaceFluxes(sections, sections[0].getLength())[0];
    assertArrayEquals(original, sections[0].getStateVector(), 0.0);
    assertTrue(inflow[0] + inflow[1] + inflow[2] > 0.0);

    applyBoundaryConditions(pipe);
    assertEquals(1L, pipe.getPressureInletFlashCount(),
        "Repeated stages at one pressure must reuse an explicitly isolated reservoir flash");

    pipe.setInletPressure(0.5e5);
    applyBoundaryConditions(pipe);
    double[] outflow = equations.calcPhaseMassFaceFluxes(sections, sections[0].getLength())[0];
    assertArrayEquals(original, sections[0].getStateVector(), 0.0);
    assertTrue(outflow[0] + outflow[1] + outflow[2] < 0.0);
    assertEquals(2L, pipe.getPressureInletFlashCount(), "A new prescribed pressure requires one new reservoir flash");
  }

  /** A boundary event can query the accepted mechanical face pressure without a half-cell traction jump. */
  @Test
  void inletFacePressureReportsClosedWallHydrostaticCompatibility() throws Exception {
    TwoFluidPipe pipe = createPipe(BoundaryCondition.CONSTANT_FLOW, -1);
    TwoFluidSection[] sections = (TwoFluidSection[]) read(pipe, "sections");
    TwoFluidSection inlet = sections[0];
    inlet.setInclination(0.1);
    inlet.updateConservativeVariables();
    pipe.closeInlet();
    applyBoundaryConditions(pipe);

    double massPerLength = inlet.getGasMassPerLength() + inlet.getOilMassPerLength() + inlet.getWaterMassPerLength();
    double expectedPa = inlet.getPressure()
        + 0.5 * inlet.getLength() * 9.81 * Math.sin(inlet.getInclination()) * massPerLength / inlet.getArea();
    assertEquals(expectedPa, pipe.getInletFacePressure("Pa"), 1.0e-10);
    assertEquals(expectedPa / 1.0e5, pipe.getInletFacePressure("bara"), 1.0e-12);
  }

  /** Build a fully specified three-phase section for the pressure-boundary analytical check. */
  private static TwoFluidSection analyticalBoundarySection(double pressure, double gasDensity, double oilDensity,
      double waterDensity, double gasSoundSpeed, double liquidSoundSpeed) {
    TwoFluidSection section = new TwoFluidSection(0.0, 10.0, 0.2, 0.0);
    section.setPressure(pressure);
    section.setGasDensity(gasDensity);
    section.setOilDensity(oilDensity);
    section.setWaterDensity(waterDensity);
    section.setLiquidDensity(850.0);
    section.setGasSoundSpeed(gasSoundSpeed);
    section.setLiquidSoundSpeed(liquidSoundSpeed);
    section.setGasHoldup(0.5);
    section.setOilHoldup(0.3);
    section.setWaterHoldup(0.2);
    section.setLiquidHoldup(0.5);
    section.setGasVelocity(0.0);
    section.setOilVelocity(0.0);
    section.setWaterVelocity(0.0);
    section.setLiquidVelocity(0.0);
    section.updateConservativeVariables();
    return section;
  }

  private static TwoFluidPipe createPipe(BoundaryCondition boundary, int absentPhase) throws Exception {
    SystemInterface fluid = new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("n-decane", 1.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("Prescribed three-phase feed", fluid);
    feed.setFlowRate(0.3, "kg/sec");
    feed.run();
    assertTrue(feed.getFluid().hasPhaseType("gas"));
    assertTrue(feed.getFluid().hasPhaseType("oil"));
    assertTrue(feed.getFluid().hasPhaseType("aqueous"));

    TwoFluidPipe pipe = new TwoFluidPipe("Coupled inlet-state isolation", feed);
    pipe.setLength(20.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfSections(2);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setInletBoundaryCondition(boundary);
    pipe.setOutletBoundaryCondition(BoundaryCondition.CLOSED);
    if (boundary == BoundaryCondition.CONSTANT_FLOW) {
      pipe.setInletMassFlow(0.17);
    }

    TwoFluidSection first = new TwoFluidSection(0.0, 10.0, 0.2, 0.0);
    double[] holdup = {0.5, 0.2, 0.3};
    if (absentPhase >= 0) {
      double remaining = 1.0 - holdup[absentPhase];
      holdup[absentPhase] = 0.0;
      for (int phase = 0; phase < 3; phase++) {
        holdup[phase] /= remaining;
      }
    }
    first.setGasHoldup(holdup[0]);
    first.setOilHoldup(holdup[1]);
    first.setWaterHoldup(holdup[2]);
    first.setLiquidHoldup(holdup[1] + holdup[2]);
    first.setWaterCut(holdup[2] / (holdup[1] + holdup[2]));
    first.setPressure(80000.0);
    first.setTemperature(310.0);
    first.setGasDensity(0.6);
    first.setOilDensity(790.0);
    first.setWaterDensity(980.0);
    first.setLiquidDensity((790.0 * holdup[1] + 980.0 * holdup[2]) / (holdup[1] + holdup[2]));
    first.setGasSoundSpeed(300.0);
    first.setLiquidSoundSpeed(1200.0);
    first.setGasViscosity(1.5e-5);
    first.setOilViscosity(0.002);
    first.setWaterViscosity(0.001);
    first.setLiquidViscosity(0.0015);
    first.setSurfaceTension(0.03);
    first.setGasVelocity(-2.0);
    first.setLiquidVelocity(0.3);
    first.setOilVelocity(0.7);
    first.setWaterVelocity(-0.1);
    first.updateConservativeVariables();
    write(pipe, "sections", new TwoFluidSection[] {first, first.clone()});
    write(pipe, "isTransientMode", true);
    return pipe;
  }

  private static double[] prescribedPhaseMassFlows(TwoFluidPipe pipe, BoundaryCondition boundary) {
    SystemInterface fluid = pipe.getInletStream().getFluid();
    double[] masses = {fluid.getPhase("gas").getMass(), fluid.getPhase("oil").getMass(),
        fluid.getPhase("aqueous").getMass()};
    double total = masses[0] + masses[1] + masses[2];
    double prescribed = boundary == BoundaryCondition.CONSTANT_FLOW ? 0.17
        : pipe.getInletStream().getFlowRate("kg/sec");
    for (int phase = 0; phase < 3; phase++) {
      masses[phase] *= prescribed / total;
    }
    return masses;
  }

  private static double[] cellProperties(TwoFluidSection section) {
    return new double[] {section.getPressure(), section.getTemperature(), section.getGasDensity(),
        section.getOilDensity(), section.getWaterDensity(), section.getLiquidDensity(), section.getGasHoldup(),
        section.getOilHoldup(), section.getWaterHoldup(), section.getGasVelocity(), section.getOilVelocity(),
        section.getWaterVelocity()};
  }

  private static void applyBoundaryConditions(TwoFluidPipe pipe) throws Exception {
    Method apply = TwoFluidPipe.class.getDeclaredMethod("applyBoundaryConditions");
    apply.setAccessible(true);
    apply.invoke(pipe);
  }

  private static Object read(Object owner, String name) throws Exception {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(owner);
  }

  private static void write(Object owner, String name, Object value) throws Exception {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(owner, value);
  }
}
