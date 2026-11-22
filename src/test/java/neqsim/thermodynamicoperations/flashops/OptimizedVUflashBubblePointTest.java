package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * VU flash of an oil just below its bubble point, the state of a nearly liquid-full separator (GitHub discussion
 * equinor/neqsim#1574). The Q-function Newton step uses frozen-composition derivatives that understate dV/dP
 * several-fold here, so the flash relies on the equilibrium polish stage.
 */
public class OptimizedVUflashBubblePointTest extends neqsim.NeqSimTest {
  private static SystemInterface oil() {
    SystemInterface fluid = new SystemPrEos(297.7, 5.0);
    fluid.addComponent("CO2", 0.05);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("ethane", 0.6);
    fluid.addComponent("propane", 2.0);
    fluid.addComponent("n-butane", 2.0);
    fluid.addComponent("n-pentane", 1.5);
    fluid.addComponent("n-hexane", 2.3);
    fluid.addTBPfraction("C7+_cut1", 2.1, 108.47 / 1000.0, 0.7411);
    fluid.addTBPfraction("C7+_cut4", 6.5, 164.70 / 1000.0, 0.799);
    fluid.addTBPfraction("C7+_cut6", 5.0, 273.34 / 1000.0, 0.8754);
    fluid.addTBPfraction("C7+_cut8", 3.0, 412.79 / 1000.0, 0.9575);
    fluid.setMixingRule("classic");
    return fluid;
  }

  @ParameterizedTest
  @ValueSource(doubles = {5.6, 4.5, 8.0})
  public void convergesNearBubblePointFromOffsetStart(double startPressureBara) {
    SystemInterface target = oil();
    new ThermodynamicOperations(target).TPflash();
    target.init(2);
    assertEquals(2, target.getNumberOfPhases(), "target state must be two-phase");
    double vapourFraction = target.getBeta();
    assertTrue(vapourFraction > 0.0 && vapourFraction < 0.02, "target vapour fraction " + vapourFraction);
    double volume = target.getVolume("m3");
    double energy = target.getInternalEnergy();

    SystemInterface system = target.clone();
    system.setPressure(startPressureBara);
    system.setTemperature(target.getTemperature() + 1.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.VUflash(volume, energy, "m3", "J");

    OptimizedVUflash flash = (OptimizedVUflash) ops.getOperation();
    assertTrue(flash.isLastRunConverged(), "polish used: " + flash.wasEquilibriumPolishUsed());
    system.init(2);
    assertEquals(volume, system.getVolume("m3"), 1.0e-3 * volume);
    assertEquals(energy, system.getInternalEnergy(), 1.0e-3 * Math.abs(energy));
    assertEquals(5.0, system.getPressure("bara"), 0.05);
    assertEquals(297.7, system.getTemperature(), 0.1);
  }
}
