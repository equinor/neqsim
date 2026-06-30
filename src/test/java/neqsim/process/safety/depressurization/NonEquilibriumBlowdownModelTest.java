package neqsim.process.safety.depressurization;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link NonEquilibriumBlowdownModel}.
 *
 * <p>
 * Validates the two-zone (vapour/liquid) fire-exposed blowdown model: a fire-heated two-phase vessel develops a
 * vapour-minus-liquid temperature bifurcation while depressurizing through the orifice, and the model rejects a
 * single-phase feed that cannot split into the required zones.
 *
 * @author ESOL
 * @version 1.0
 */
public class NonEquilibriumBlowdownModelTest {

  /** Builds a two-phase hydrocarbon fluid (vapour + liquid) at moderate conditions. */
  private static SystemInterface twoPhaseFluid() {
    SystemInterface fluid = new SystemSrkEos(280.0, 50.0);
    fluid.addComponent("methane", 0.6);
    fluid.addComponent("propane", 0.2);
    fluid.addComponent("n-butane", 0.2);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /** A fire-heated two-phase blowdown must bifurcate the zone temperatures and depressurize. */
  @Test
  public void developsBifurcationAndDepressurizes() {
    NonEquilibriumBlowdownModel model = new NonEquilibriumBlowdownModel(twoPhaseFluid(), 10.0, 0.012, 0.85, 1.0e5);
    model.setFireExposure(0.9, 1200.0, 30.0, 20.0).setWall(4000.0, 470.0).setInsideGasCoefficient(50.0)
        .setInterfacial(80.0, 4.0).setLatentHeat(350000.0).setTimeStep(1.0).setMaxTime(150.0).setStopPressure(3.0e5);

    NonEquilibriumBlowdownModel.NemResult result = model.run();

    assertTrue(result.timeS.size() > 1, "Transient should advance more than one step");
    assertTrue(result.maxTemperatureBifurcationK > 0.0, "Vapour and liquid zones should diverge in temperature");
    double lastPressure = result.pressureBara.get(result.pressureBara.size() - 1);
    assertTrue(lastPressure < result.initialPressureBara, "Vessel should depressurize");
  }

  /** A single-phase gas feed must be rejected because it cannot form two zones. */
  @Test
  public void rejectsSinglePhaseFeed() {
    SystemInterface gas = new SystemSrkEos(300.0, 50.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    NonEquilibriumBlowdownModel model = new NonEquilibriumBlowdownModel(gas, 10.0, 0.012, 0.85, 1.0e5);
    model.setFireExposure(0.9, 1200.0, 30.0, 20.0).setWall(4000.0, 470.0).setLatentHeat(350000.0).setMaxTime(50.0)
        .setStopPressure(3.0e5);
    assertThrows(IllegalStateException.class, () -> model.run());
  }

  /** A null fluid must be rejected at construction. */
  @Test
  public void rejectsNullFluid() {
    assertThrows(IllegalArgumentException.class, () -> new NonEquilibriumBlowdownModel(null, 10.0, 0.012, 0.85, 1.0e5));
  }
}
