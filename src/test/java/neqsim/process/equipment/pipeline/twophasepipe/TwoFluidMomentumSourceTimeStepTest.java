package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;
import neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction;

/** Explicit source stability is independent of the implicit acoustic timestep. */
class TwoFluidMomentumSourceTimeStepTest {
  @Test
  void quadraticDragResolvesThePairRelativeVelocityEigenvalue() throws Exception {
    TwoFluidSection section = section(0.4, 0.0, 4.0, 1.0, 1.0);
    double coefficient = 3.0;
    TwoFluidConservationEquations equations = equations(0.0, 0.0, coefficient, true);
    double expectedRate = 2.0 * coefficient * 3.0
        * (1.0 / section.getGasMassPerLength() + 1.0 / section.getOilMassPerLength());
    double[] initial = section.getStateVector();
    double timeStep = equations.calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] { section });
    assertEquals(1.0 / expectedRate, timeStep, 1.0e-9 / expectedRate);
    assertArrayEquals(initial, section.getStateVector(), 0.0, "A source estimate must not advance the state");
    TwoFluidConservationEquations strongerDrag = equations(0.0, 0.0, 2.0 * coefficient, true);
    assertEquals(timeStep / 2.0, strongerDrag.calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] { section }),
        timeStep * 1.0e-9);
  }

  @Test
  void linearDragRetainsItsFiniteRelaxationTimeAtZeroSlip() throws Exception {
    TwoFluidSection section = section(0.4, 0.0, 2.0, 2.0, 2.0);
    double coefficient = 7.0;
    double expectedRate = coefficient * (1.0 / section.getGasMassPerLength() + 1.0 / section.getOilMassPerLength());
    assertEquals(1.0 / expectedRate,
        equations(0.0, 0.0, coefficient, false).calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] { section }),
        1.0e-9 / expectedRate);
  }

  @Test
  void dragDependingOnAbsoluteGasSpeedRetainsItsLiquidVelocityDerivative() throws Exception {
    TwoFluidSection section = section(0.4, 0.0, 1.0, -1.0, -1.0);
    TwoFluidConservationEquations equations = equations(0.0, 0.0, 0.0, false);
    Field interfaceField = TwoFluidConservationEquations.class.getDeclaredField("interfacialFriction");
    interfaceField.setAccessible(true);
    interfaceField.set(equations, new InterfacialFriction() {
      private static final long serialVersionUID = 1L;

      @Override
      public InterfacialFrictionResult calculate(FlowRegime regime, double gasVelocity, double liquidVelocity,
          double gasDensity, double liquidDensity, double gasViscosity, double liquidViscosity, double liquidHoldup,
          double diameter, double surfaceTension) {
        InterfacialFrictionResult result = new InterfacialFrictionResult();
        double slip = gasVelocity - liquidVelocity;
        // Laminar annular drag has this gas-Reynolds-number dependence.
        result.interfacialShear = slip * Math.abs(slip) / Math.abs(gasVelocity);
        result.interfacialAreaPerLength = 1.0;
        return result;
      }
    });
    // At this state dF/duG=0 while |dF/duL|=4; probing only gas misses the source.
    assertEquals(section.getOilMassPerLength() / 4.0,
        equations.calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] { section }),
        section.getOilMassPerLength() * 1.0e-8);
  }

  @Test
  void stationarySinglePhaseRetainsLaminarWallRelaxation() throws Exception {
    TwoFluidSection section = section(1.0, 0.0, 0.0, 0.0, 0.0);
    double coefficient = 11.0;
    assertEquals(section.getOilMassPerLength() / coefficient,
        equations(0.0, coefficient, 0.0, false).calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] { section }),
        1.0e-10);
  }

  @Test
  void exactlyAbsentPhasesDoNotIntroduceAnInverseMassConstraint() throws Exception {
    for (double liquidHoldup : new double[] { 0.0, 1.0 }) {
      TwoFluidSection section = section(liquidHoldup, 0.0, 3.0, -4.0, 9.0);
      double[] initial = section.getStateVector();
      assertEquals(Double.POSITIVE_INFINITY,
          equations(0.0, 0.0, 1.0e9, true).calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] { section }));
      assertArrayEquals(initial, section.getStateVector(), 0.0);
    }
  }

  @Test
  void threePhaseGasDragRetainsTheCommonLiquidAccelerationTimeScale() throws Exception {
    TwoFluidSection section = section(0.8, 0.01, 3.0, 1.0, 1.0);
    TwoFluidConservationEquations equations = equations(0.0, 0.0, 4.0, false);
    equations.setEnableWaterOilSlip(true);
    // Gas drag uses the bulk liquid velocity, so each liquid receives the same acceleration even with slip enabled.
    double liquidInverseMass = 1.0 / (section.getOilMassPerLength() + section.getWaterMassPerLength());
    double expectedRate = 4.0 * (1.0 / section.getGasMassPerLength() + liquidInverseMass);
    double dt = equations.calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] { section });
    assertEquals(1.0 / expectedRate, dt, 1.0e-7 / expectedRate);
    equations.setEnableWaterOilSlip(false);
    assertEquals(dt, equations.calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] { section }), dt * 1.0e-7,
        "Both paths use aggregate liquid inertia for the same bulk gas drag");
  }

  @Test
  void oilWaterSlipUsesBothConservativeMassesAndOriginalVelocities() throws Exception {
    TwoFluidSection section = section(1.0, 0.4, 0.0, 3.0, 1.0);
    TwoFluidConservationEquations equations = equations(0.0, 0.0, 0.0, false);
    equations.setEnableWaterOilSlip(true);
    double phaseAvailability = 4.0 * 0.6 * 0.4;
    double derivative = phaseAvailability * 0.02 * 900.0 * 2.0;
    double expectedRate = derivative * section.getDiameter() * 0.5
        * (1.0 / section.getOilMassPerLength() + 1.0 / section.getWaterMassPerLength());
    assertEquals(1.0 / expectedRate, equations.calcExplicitMomentumSourceTimeStep(new TwoFluidSection[] { section }),
        1.0e-9 / expectedRate);
  }

  private static TwoFluidSection section(double liquidHoldup, double waterCut, double gasVelocity, double oilVelocity,
      double waterVelocity) {
    TwoFluidSection section = new TwoFluidSection(5.0, 10.0, 0.3, 0.0);
    section.setLiquidHoldup(liquidHoldup);
    section.setGasHoldup(1.0 - liquidHoldup);
    section.setWaterCut(waterCut);
    section.setOilHoldup(liquidHoldup * (1.0 - waterCut));
    section.setWaterHoldup(liquidHoldup * waterCut);
    section.setGasDensity(40.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(800.0 * (1.0 - waterCut) + 1000.0 * waterCut);
    section.setGasViscosity(1.0e-5);
    section.setLiquidViscosity(0.001);
    section.setGasVelocity(gasVelocity);
    section.setLiquidVelocity(oilVelocity);
    section.setOilVelocity(oilVelocity);
    section.setWaterVelocity(waterVelocity);
    section.updateConservativeVariables();
    return section;
  }

  private static TwoFluidConservationEquations equations(final double gasWallCoefficient,
      final double liquidWallCoefficient, final double dragCoefficient, final boolean quadratic) throws Exception {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeMassTransfer(false);
    equations.setEnableWaterOilSlip(false);
    Field wall = TwoFluidConservationEquations.class.getDeclaredField("wallFriction");
    wall.setAccessible(true);
    wall.set(equations, new WallFriction() {
      private static final long serialVersionUID = 1L;

      @Override
      public WallFrictionResult calculate(FlowRegime regime, double gasVelocity, double liquidVelocity,
          double gasDensity, double liquidDensity, double gasViscosity, double liquidViscosity, double liquidHoldup,
          double diameter, double roughness) {
        WallFrictionResult result = new WallFrictionResult();
        result.gasWallForcePerLength = gasWallCoefficient * gasVelocity;
        result.liquidWallForcePerLength = liquidWallCoefficient * liquidVelocity;
        return result;
      }
    });
    Field interfaceField = TwoFluidConservationEquations.class.getDeclaredField("interfacialFriction");
    interfaceField.setAccessible(true);
    interfaceField.set(equations, new InterfacialFriction() {
      private static final long serialVersionUID = 1L;

      @Override
      public InterfacialFrictionResult calculate(FlowRegime regime, double gasVelocity, double liquidVelocity,
          double gasDensity, double liquidDensity, double gasViscosity, double liquidViscosity, double liquidHoldup,
          double diameter, double surfaceTension) {
        InterfacialFrictionResult result = new InterfacialFrictionResult();
        double slip = gasVelocity - liquidVelocity;
        result.interfacialShear = dragCoefficient * slip * (quadratic ? Math.abs(slip) : 1.0);
        result.interfacialAreaPerLength = 1.0;
        return result;
      }
    });
    return equations;
  }
}
