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
    for (BoundaryCondition boundary : new BoundaryCondition[] { BoundaryCondition.STREAM_CONNECTED,
        BoundaryCondition.CONSTANT_FLOW }) {
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
    for (BoundaryCondition boundary : new BoundaryCondition[] { BoundaryCondition.STREAM_CONNECTED,
        BoundaryCondition.CONSTANT_FLOW }) {
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
    double[] holdup = { 0.5, 0.2, 0.3 };
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
    write(pipe, "sections", new TwoFluidSection[] { first, first.clone() });
    write(pipe, "isTransientMode", true);
    return pipe;
  }

  private static double[] prescribedPhaseMassFlows(TwoFluidPipe pipe, BoundaryCondition boundary) {
    SystemInterface fluid = pipe.getInletStream().getFluid();
    double[] masses = { fluid.getPhase("gas").getMass(), fluid.getPhase("oil").getMass(),
        fluid.getPhase("aqueous").getMass() };
    double total = masses[0] + masses[1] + masses[2];
    double prescribed = boundary == BoundaryCondition.CONSTANT_FLOW ? 0.17
        : pipe.getInletStream().getFlowRate("kg/sec");
    for (int phase = 0; phase < 3; phase++) {
      masses[phase] *= prescribed / total;
    }
    return masses;
  }

  private static double[] cellProperties(TwoFluidSection section) {
    return new double[] { section.getPressure(), section.getTemperature(), section.getGasDensity(),
        section.getOilDensity(), section.getWaterDensity(), section.getLiquidDensity(), section.getGasHoldup(),
        section.getOilHoldup(), section.getWaterHoldup(), section.getGasVelocity(), section.getOilVelocity(),
        section.getWaterVelocity() };
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
