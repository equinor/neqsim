package neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.methods.methodinterface.ViscosityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Numerical, unit and phase-package integration checks for Letsou-Stiel viscosity.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class LetsouStielViscosityTest {
  /** Checks the independently published Chemicals/DIPPR ethanol example with explicit NeqSim units. */
  @Test
  void ethanolReferenceExample() {
    assertEquals(0.0002036150875308, LetsouStielViscosity.calculateViscosity(400.0, 0.04607, 516.25, 63.83, 0.6371),
        1e-16);
  }

  /** Verifies both validity boundaries are accepted and nearby extrapolations are refused. */
  @Test
  void reducedTemperatureRange() {
    for (double reducedTemperature : new double[] {0.76, 0.85, 0.98}) {
      assertTrue(LetsouStielViscosity.calculateViscosity(500.0 * reducedTemperature, 0.078, 500.0, 45.0, 0.2) > 0);
    }
    for (double reducedTemperature : new double[] {0.75999, 0.98001, 1.0}) {
      assertThrows(IllegalArgumentException.class,
          () -> LetsouStielViscosity.calculateViscosity(500.0 * reducedTemperature, 0.078, 500.0, 45.0, 0.2));
    }
  }

  /** Rejects missing, nonfinite and nonphysical critical-property inputs. */
  @Test
  void invalidInputs() {
    for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class,
          () -> LetsouStielViscosity.calculateViscosity(400, invalid, 500, 45, 0.2));
      assertThrows(IllegalArgumentException.class,
          () -> LetsouStielViscosity.calculateViscosity(400, 0.078, invalid, 45, 0.2));
      assertThrows(IllegalArgumentException.class,
          () -> LetsouStielViscosity.calculateViscosity(400, 0.078, 500, invalid, 0.2));
    }
  }

  /**
   * Prepares the executable guide example with benzene in a compressed liquid state.
   *
   * @return initialized pure-liquid system
   */
  private SystemInterface benzene() {
    SystemInterface fluid = new SystemSrkEos(500.0, 40.0);
    fluid.addComponent("benzene", 1.0);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }

  /** Verifies the documented model-selection path, returned SI value and legacy pure-component units. */
  @Test
  void documentedLiquidExample() {
    SystemInterface fluid = benzene();
    PhysicalProperties properties = fluid.getPhase("oil").getPhysicalProperties();
    properties.setViscosityModel("Letsou-Stiel");
    fluid.getPhase("oil").initPhysicalProperties();
    assertTrue(properties.getViscosityModel() instanceof LetsouStielViscosity);
    double viscosity = properties.getViscosity();
    assertTrue(viscosity > 8e-5 && viscosity < 1.2e-4, "Benzene estimate: " + viscosity);
    assertEquals(viscosity * 1000.0, properties.getViscosityModel().getPureComponentViscosity(0), 1e-14);
    assertEquals(LetsouStielViscosity.calculateViscosity(500, fluid.getPhase("oil").getComponent(0).getMolarMass(),
        fluid.getPhase("oil").getComponent(0).getTC(), fluid.getPhase("oil").getComponent(0).getPC(),
        fluid.getPhase("oil").getComponent(0).getAcentricFactor()), viscosity, 1e-14);
  }

  /** Verifies repeated evaluation, state updates and clone ownership. */
  @Test
  void cloneAndStateUpdate() {
    SystemInterface fluid = benzene();
    fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("Letsou-Stiel");
    double before = fluid.getPhase("oil").getPhysicalProperties().getViscosityModel().calcViscosity();
    SystemInterface copy = fluid.clone();
    copy.setTemperature(480.0);
    new ThermodynamicOperations(copy).TPflash();
    copy.initProperties();
    double after = copy.getPhase("oil").getPhysicalProperties().getViscosityModel().calcViscosity();
    assertTrue(after > before);
    assertEquals(before, fluid.getPhase("oil").getPhysicalProperties().getViscosityModel().calcViscosity(), 1e-14);
  }

  /** Refuses a gas phase or an unimplemented mixture rule instead of returning a placeholder. */
  @Test
  void rejectsUnsupportedPhasesAndMixtures() {
    SystemInterface gas = new SystemSrkEos(500.0, 1.0);
    gas.addComponent("benzene", 1.0);
    gas.setMixingRule("classic");
    new ThermodynamicOperations(gas).TPflash();
    gas.initProperties();
    LetsouStielViscosity gasModel = new LetsouStielViscosity(gas.getPhase(0).getPhysicalProperties());
    assertThrows(IllegalArgumentException.class, gasModel::calcViscosity);
    ViscosityInterface previousGasModel = gas.getPhase(0).getPhysicalProperties().getViscosityModel();
    assertThrows(IllegalArgumentException.class,
        () -> gas.getPhase(0).getPhysicalProperties().setViscosityModel("Letsou-Stiel"));
    assertSame(previousGasModel, gas.getPhase(0).getPhysicalProperties().getViscosityModel());
    SystemInterface mixture = new SystemSrkEos(500.0, 40.0);
    mixture.addComponent("benzene", 1.0);
    mixture.addComponent("toluene", 1.0);
    mixture.setMixingRule("classic");
    new ThermodynamicOperations(mixture).TPflash();
    mixture.initProperties();
    LetsouStielViscosity mixtureModel = new LetsouStielViscosity(mixture.getPhase(0).getPhysicalProperties());
    assertThrows(IllegalArgumentException.class, mixtureModel::calcViscosity);
    ViscosityInterface previousMixtureModel = mixture.getPhase(0).getPhysicalProperties().getViscosityModel();
    assertThrows(IllegalArgumentException.class,
        () -> mixture.getPhase(0).getPhysicalProperties().setViscosityModel("Letsou-Stiel"));
    assertSame(previousMixtureModel, mixture.getPhase(0).getPhysicalProperties().getViscosityModel());
  }

  /** Verifies an out-of-range selection leaves the existing liquid viscosity model installed. */
  @Test
  void rejectedTemperaturePreservesPreviousModel() {
    SystemInterface fluid = benzene();
    PhysicalProperties properties = fluid.getPhase("oil").getPhysicalProperties();
    ViscosityInterface previousModel = properties.getViscosityModel();
    fluid.setTemperature(350.0);
    assertThrows(IllegalArgumentException.class, () -> properties.setViscosityModel("Letsou-Stiel"));
    assertSame(previousModel, properties.getViscosityModel());
  }
}
