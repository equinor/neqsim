package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseFlux;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseState;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

class AUSMImplicitPressureSplitTest {
  @ParameterizedTest
  @EnumSource(TimeIntegrator.Method.class)
  void coupledPipePressureTractionDoesNotDependOnCountercurrentPhaseVelocities(TimeIntegrator.Method method)
      throws Exception {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("pressure predictor inlet", fluid);
    inlet.setFlowRate(0.1, "kg/sec");
    inlet.run();
    TwoFluidPipe pipe = new TwoFluidPipe("pressure predictor", inlet);
    pipe.setLength(10.0);
    pipe.setDiameter(0.1);
    pipe.setNumberOfSections(3);
    pipe.setTimeIntegrationMethod(method);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.run();
    pipe.runTransient(1.0e-4, UUID.randomUUID());
    assertTrue(pipe.isCoupledPressureMomentumConverged());
    assertEquals(1.0e-4, pipe.getLastMassBalanceReport().getElapsedTimeSeconds(), 1.0e-14);

    Field field = TwoFluidPipe.class.getDeclaredField("equations");
    field.setAccessible(true);
    AUSMPlusFluxCalculator calculator = ((TwoFluidConservationEquations) field.get(pipe)).getFluxCalculator();
    double pressure = 2.0e5;
    PhaseState positive = new PhaseState(2.0, 5.0, pressure, 300.0, 1.0e5, 0.05);
    PhaseState negative = new PhaseState(2.0, -5.0, pressure, 300.0, 1.0e5, 0.05);
    PhaseFlux right = calculator.calcPhaseFlux(positive, negative, 0.01);
    PhaseFlux left = calculator.calcPhaseFlux(negative, positive, 0.01);
    assertEquals(0.0, right.massFlux, 0.0);
    assertEquals(0.0, left.massFlux, 0.0);
    assertEquals(pressure, right.interfacePressure, 0.0);
    assertEquals(pressure, left.interfacePressure, 0.0);
    assertEquals(left.momentumFlux, right.momentumFlux, 0.0,
        "A uniform pressure must not acquire gas-velocity-dependent traction in the coupled predictor");

    pipe.setEnableCoupledPressureMomentum(false);
    pipe.runTransient(1.0e-4, UUID.randomUUID());
    PhaseFlux restored = calculator.calcPhaseFlux(positive, negative, 0.01);
    PhaseFlux original = new AUSMPlusFluxCalculator().calcPhaseFlux(positive, negative, 0.01);
    assertEquals(original.momentumFlux, restored.momentumFlux, 0.0);
  }

  /**
   * A periodic alternating velocity has zero averaged face mass flow. The AUSM pressure polynomial nevertheless
   * advances it with low-Mach amplification 1-4*(3/4+alpha)*p*dt/(rho*c*dx). Centering the pressure removes this
   * explicit acoustic diffusion rather than relying on an implicit volume correction that cannot see this mode.
   */
  @Test
  void centeredPressureRemovesExplicitAcousticVelocityModeGrowth() {
    double density = 10.0;
    double soundSpeed = 300.0;
    double pressure = density * soundSpeed * soundSpeed / 1.4;
    double velocity = 1.0e-3;
    double dt = 0.1;
    double dx = 1.0;
    PhaseState positive = new PhaseState(density, velocity, pressure, soundSpeed, 1.0e5, 1.0);
    PhaseState negative = new PhaseState(density, -velocity, pressure, soundSpeed, 1.0e5, 1.0);
    AUSMPlusFluxCalculator fluxCalculator = new AUSMPlusFluxCalculator();
    assertFalse(fluxCalculator.isCenteredPressureFluxEnabled());

    PhaseFlux oldRight = fluxCalculator.calcPhaseFlux(positive, negative, 1.0);
    PhaseFlux oldLeft = fluxCalculator.calcPhaseFlux(negative, positive, 1.0);
    double oldVelocity = velocity - dt / (density * dx) * (oldRight.momentumFlux - oldLeft.momentumFlux);
    double expectedAmplification = 1.0
        - 4.0 * (0.75 + fluxCalculator.getAlpha()) * pressure * dt / (density * soundSpeed * dx);

    assertEquals(0.0, oldRight.massFlux, 0.0);
    assertEquals(0.0, oldLeft.massFlux, 0.0);
    assertEquals(expectedAmplification, oldVelocity / velocity, 1.0e-5);
    assertTrue(Math.abs(oldVelocity) > 60.0 * velocity, "The legacy predictor exposes the acoustic restriction");
    assertTrue(soundSpeed * dt / dx > 1.0);
    assertTrue(velocity * dt / dx < 0.5, "The material Courant condition is satisfied");

    fluxCalculator.setCenteredPressureFluxEnabled(true);
    PhaseFlux right = fluxCalculator.calcPhaseFlux(positive, negative, 1.0);
    PhaseFlux left = fluxCalculator.calcPhaseFlux(negative, positive, 1.0);
    double correctedVelocity = velocity - dt / (density * dx) * (right.momentumFlux - left.momentumFlux);

    assertEquals(pressure, right.interfacePressure, 0.0);
    assertEquals(pressure, left.interfacePressure, 0.0);
    assertEquals(velocity, correctedVelocity, 0.0, "The grid-scale velocity mode is neutral, not amplified");
    assertEquals(0.0, right.massFlux, 0.0);
    assertEquals(0.0, right.energyFlux, 0.0);
  }

  @Test
  void changingPressureSplitPreservesTheAUSMAdvectiveFluxes() {
    AUSMPlusFluxCalculator fluxCalculator = new AUSMPlusFluxCalculator();
    PhaseState left = new PhaseState(10.0, 20.0, 2.0e6, 300.0, 1.2e5, 0.4);
    PhaseState right = new PhaseState(20.0, 35.0, 1.8e6, 320.0, 1.1e5, 0.7);
    double area = 0.2;
    PhaseFlux original = fluxCalculator.calcPhaseFlux(left, right, area);

    fluxCalculator.setCenteredPressureFluxEnabled(true);
    PhaseFlux centered = fluxCalculator.calcPhaseFlux(left, right, area);

    assertEquals(original.massFlux, centered.massFlux, 0.0);
    assertEquals(original.energyFlux, centered.energyFlux, 0.0);
    assertEquals(original.holdupFlux, centered.holdupFlux, 0.0);
    assertEquals(original.interfaceHoldup, centered.interfaceHoldup, 0.0);
    assertEquals(1.9e6, centered.interfacePressure, 0.0);
    assertEquals(
        original.momentumFlux
            + original.interfaceHoldup * area * (centered.interfacePressure - original.interfacePressure),
        centered.momentumFlux, 1.0e-9);

    fluxCalculator.setCenteredPressureFluxEnabled(false);
    PhaseFlux restored = fluxCalculator.calcPhaseFlux(left, right, area);
    assertEquals(original.momentumFlux, restored.momentumFlux, 0.0);
    assertEquals(original.interfacePressure, restored.interfacePressure, 0.0);
  }

  @Test
  void absentPhaseStillCarriesNoFluxWithCenteredPressure() {
    AUSMPlusFluxCalculator fluxCalculator = new AUSMPlusFluxCalculator();
    fluxCalculator.setCenteredPressureFluxEnabled(true);
    PhaseFlux flux = fluxCalculator.calcPhaseFlux(new PhaseState(10.0, 5.0, 2.0e6, 300.0, 1.0e5, 0.0),
        new PhaseState(20.0, -4.0, 1.8e6, 320.0, 1.2e5, 0.0), 0.2);

    assertEquals(0.0, flux.massFlux, 0.0);
    assertEquals(0.0, flux.momentumFlux, 0.0);
    assertEquals(0.0, flux.energyFlux, 0.0);
  }
}
