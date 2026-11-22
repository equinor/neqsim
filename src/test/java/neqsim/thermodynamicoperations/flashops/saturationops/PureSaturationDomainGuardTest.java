package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.exception.IsNaNException;

/**
 * Verifies pure-fluid domain rejection independently of iterative convergence flags.
 *
 * @author Even Solbraa
 * @version 1.0
 */
@Timeout(15)
class PureSaturationDomainGuardTest {
  /**
   * Checks derivative bubble and hydrocarbon dew pressure operations at and above Tc.
   *
   * @param derivative whether to run the derivative bubble-pressure method
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void purePressureOperationsRejectBeforeMutatingFluid(boolean derivative) {
    for (double factor : new double[] {1.0, 1.2}) {
      SystemInterface fluid = methane();
      fluid.setTemperature(factor * fluid.getComponent(0).getTC());
      double temperature = fluid.getTemperature();
      double pressure = fluid.getPressure();
      int phases = fluid.getNumberOfPhases();
      PhaseInterface firstPhase = fluid.getPhase(0);
      double beta = fluid.getBeta(0);
      ConstantDutyFlashInterface operation = derivative ? new BubblePointPressureFlashDer(fluid)
          : new HCdewPointPressureFlash(fluid);
      assertThrows(IllegalStateException.class, operation::run);
      assertEquals(temperature, fluid.getTemperature(), 0.0);
      assertEquals(pressure, fluid.getPressure(), 0.0);
      assertEquals(phases, fluid.getNumberOfPhases());
      assertSame(firstPhase, fluid.getPhase(0));
      assertEquals(beta, fluid.getBeta(0), 0.0);
      assertEquals(1.0, fluid.getTotalNumberOfMoles(), 0.0);
    }
  }

  /** Checks the non-derivative bubble-temperature domain at and above Pc. */
  @Test
  void pureBubbleTemperatureRejectsBeforeMutatingFluid() {
    for (double factor : new double[] {1.0, 1.2}) {
      SystemInterface fluid = methane();
      fluid.setPressure(factor * fluid.getComponent(0).getPC());
      double temperature = fluid.getTemperature();
      double pressure = fluid.getPressure();
      int phases = fluid.getNumberOfPhases();
      double beta = fluid.getBeta(0);
      assertThrows(IllegalStateException.class, () -> new BubblePointTemperatureNoDer(fluid).run());
      assertEquals(temperature, fluid.getTemperature(), 0.0);
      assertEquals(pressure, fluid.getPressure(), 0.0);
      assertEquals(phases, fluid.getNumberOfPhases());
      assertEquals(beta, fluid.getBeta(0), 0.0);
    }
  }

  /** Checks facade failure propagation, including the existing wrapped cause contract. */
  @Test
  void publicFacadePropagatesDomainFailures() {
    SystemInterface bubble = methane();
    bubble.setTemperature(bubble.getComponent(0).getTC());
    IsNaNException failure = assertThrows(IsNaNException.class,
        () -> new ThermodynamicOperations(bubble).bubblePointPressureFlash(true));
    assertTrue(failure.getCause() instanceof IllegalStateException);
    SystemInterface dew = methane();
    dew.setTemperature(dew.getComponent(0).getTC());
    assertThrows(IllegalStateException.class, () -> new ThermodynamicOperations(dew).dewPointPressureFlashHC());
    SystemInterface temperature = methane();
    temperature.setPressure(temperature.getComponent(0).getPC());
    assertThrows(IllegalStateException.class,
        () -> new ThermodynamicOperations(temperature).bubblePointTemperatureFlash());
  }

  /**
   * Checks both pressure-operation variants on an ordinary subcritical methane point.
   *
   * @param derivative whether to run derivative bubble pressure rather than hydrocarbon dew pressure
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void subcriticalPressureOperationsStillFindSaturation(boolean derivative) {
    SystemInterface fluid = methane();
    ConstantDutyFlashInterface operation = derivative ? new BubblePointPressureFlashDer(fluid)
        : new HCdewPointPressureFlash(fluid);
    operation.run();
    fluid.init(1);
    assertEquals(10.47, fluid.getPressure(), 0.2);
    assertTrue(fluid.getPhase(0).getMolarVolume() > fluid.getPhase(1).getMolarVolume());
    assertEquals(fluid.getPhase(0).getComponent(0).getFugacityCoefficient(),
        fluid.getPhase(1).getComponent(0).getFugacityCoefficient(), 1.0e-6);
    for (int phase = 0; phase < 2; phase++) {
      double pressure = ((neqsim.thermo.phase.PhaseEosInterface) fluid.getPhase(phase)).calcPressure();
      assertEquals(fluid.getPressure(), pressure, 1.0e-6 * fluid.getPressure());
    }
  }

  /**
   * Checks ordinary and near-critical subcritical temperature solutions against phase fugacities.
   *
   * @param pressureFraction fraction of methane's critical pressure
   * @throws IsNaNException if a subcritical saturation point unexpectedly fails
   */
  @ParameterizedTest
  @ValueSource(doubles = {0.2, 0.9})
  void subcriticalBubbleTemperatureStillSatisfiesEquilibrium(double pressureFraction) throws IsNaNException {
    SystemInterface fluid = methane();
    fluid.setPressure(pressureFraction * fluid.getComponent(0).getPC());
    new ThermodynamicOperations(fluid).bubblePointTemperatureFlash();
    fluid.init(1);
    assertTrue(fluid.getTemperature() < fluid.getComponent(0).getTC());
    assertTrue(fluid.getTemperature() > fluid.getComponent(0).getTriplePointTemperature());
    assertTrue(fluid.getPhase(0).getMolarVolume() > fluid.getPhase(1).getMolarVolume() * 1.0001);
    for (int phase = 0; phase < 2; phase++) {
      double pressure = ((neqsim.thermo.phase.PhaseEosInterface) fluid.getPhase(phase)).calcPressure();
      assertEquals(fluid.getPressure(), pressure, 1.0e-6 * fluid.getPressure());
    }
    assertEquals(fluid.getPhase(0).getComponent(0).getFugacityCoefficient(),
        fluid.getPhase(1).getComponent(0).getFugacityCoefficient(), 1.0e-6);
  }

  /**
   * Creates a pure methane system in the ordinary saturation domain.
   *
   * @return one mole of methane at 150 K and 10 bara
   */
  private static SystemInterface methane() {
    SystemInterface fluid = new SystemSrkEos(150.0, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return fluid;
  }
}
