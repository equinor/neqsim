package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentHydrateGF;
import neqsim.thermo.system.SystemElectrolyteCPA;
import neqsim.thermo.system.SystemElectrolyteCPAAdvanced;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermo.system.SystemPrCPA;
import neqsim.thermo.system.SystemSrkCPAs;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkCPAstatoilAndersonMixing;
import neqsim.thermo.system.SystemSrkCPAstatoilBroydenImplicit;
import neqsim.thermo.system.SystemSrkCPAstatoilFullyImplicit;
import neqsim.thermo.system.SystemSrkCPAstatoilReduced;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verifies that the hydrate cavity model is chosen by fluid model family, so every SRK-CPA solver variant gives the
 * same hydrate equilibrium.
 *
 * @author NeqSim
 * @version 1.0
 */
class PhaseHydrateModelSelectionTest {
  /**
   * Adds methane and water and prepares the hydrate phase.
   *
   * @param fluid empty fluid system
   * @return the configured fluid
   */
  private static SystemInterface methaneWater(SystemInterface fluid) {
    fluid.addComponent("methane", 90.0);
    fluid.addComponent("water", 10.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.setHydrateCheck(true);
    return fluid;
  }

  /**
   * Returns the hydrate cavity model of a fluid.
   *
   * @param fluid fluid with a hydrate phase
   * @return hydrate model name
   */
  private static String model(SystemInterface fluid) {
    return ((PhaseHydrate) fluid.getPhases()[4]).getHydrateModel();
  }

  /** Checks that all SRK-CPA and electrolyte-CPA systems select the CPA hydrate model. */
  @Test
  void everySrkCpaVariantUsesTheCpaHydrateModel() {
    SystemInterface[] variants = {new SystemSrkCPAs(280.0, 100.0), new SystemSrkCPAstatoil(280.0, 100.0),
        new SystemSrkCPAstatoilFullyImplicit(280.0, 100.0), new SystemSrkCPAstatoilReduced(280.0, 100.0),
        new SystemSrkCPAstatoilBroydenImplicit(280.0, 100.0), new SystemSrkCPAstatoilAndersonMixing(280.0, 100.0),
        new SystemElectrolyteCPAstatoil(280.0, 100.0), new SystemElectrolyteCPA(280.0, 100.0),
        new SystemElectrolyteCPAAdvanced(280.0, 100.0)};
    for (SystemInterface variant : variants) {
      SystemInterface fluid = methaneWater(variant);
      assertEquals("CPAHydrateModel", model(fluid), variant.getClass().getSimpleName());
      assertTrue(fluid.getPhases()[4].getComponent("water") instanceof ComponentHydrateGF,
          variant.getClass().getSimpleName());
    }
  }

  /** Checks that SRK, PR-CPA and Pitzer keep their hydrate models. */
  @Test
  void nonSrkCpaFamiliesKeepTheirModels() {
    SystemInterface srk = new SystemSrkEos(280.0, 100.0);
    srk.addComponent("methane", 90.0);
    srk.addComponent("water", 10.0);
    srk.setMixingRule("classic");
    srk.setHydrateCheck(true);
    assertEquals("PVTsimHydrateModel", model(srk));

    SystemInterface prCpa = new SystemPrCPA(280.0, 100.0);
    prCpa.addComponent("methane", 90.0);
    prCpa.addComponent("water", 10.0);
    prCpa.setMixingRule(10);
    prCpa.setHydrateCheck(true);
    assertEquals("PVTsimHydrateModel", model(prCpa));

    SystemPitzer pitzer = new SystemPitzer(280.0, 100.0);
    pitzer.addComponent("methane", 90.0);
    pitzer.addComponent("water", 10.0);
    pitzer.setMixingRule("classic");
    pitzer.setHydrateCheck(true);
    assertEquals("PitzerHydrateModel", model(pitzer));
  }

  /** Checks the model-name fallback selection. */
  @Test
  void fluidModelNameSelectionMatchesSolverSuffixes() {
    assertEquals("CPAHydrateModel",
        PhaseHydrate.hydrateModelForFluidModelName("CPAs-SRK-EOS-statoil-FullyImplicit-Reduced"));
    assertEquals("CPAHydrateModel", PhaseHydrate.hydrateModelForFluidModelName("Electrolyte-CPA-Advanced"));
    assertEquals("PVTsimHydrateModel", PhaseHydrate.hydrateModelForFluidModelName("CPA-PR-EOS"));
    assertEquals("PVTsimHydrateModel", PhaseHydrate.hydrateModelForFluidModelName(""));
    assertEquals("PitzerHydrateModel", PhaseHydrate.hydrateModelForFluidModelName("Pitzer-GE-model"));
  }

  /**
   * Checks that CPA solver variants give the same hydrate temperature.
   *
   * @throws Exception if a hydrate calculation fails
   */
  @Test
  void solverVariantsGiveTheSameHydrateTemperature() throws Exception {
    SystemInterface reference = methaneWater(new SystemSrkCPAstatoil(280.0, 100.0));
    new ThermodynamicOperations(reference).hydrateFormationTemperature();
    SystemInterface implicit = methaneWater(new SystemSrkCPAstatoilFullyImplicit(280.0, 100.0));
    new ThermodynamicOperations(implicit).hydrateFormationTemperature();
    // Before family-based selection the FullyImplicit variant used the PVTsim parameters: 12.99 C versus 12.76 C.
    assertEquals(reference.getTemperature(), implicit.getTemperature(), 0.02);
    assertEquals(273.15 + 12.76, reference.getTemperature(), 0.1);
  }

  /**
   * Checks that the hydrate operations add the hydrate phase themselves.
   *
   * @throws Exception if a hydrate calculation fails
   */
  @Test
  void hydrateTemperatureWorksWithoutExplicitHydrateCheck() throws Exception {
    SystemInterface fluid = new SystemSrkCPAstatoil(280.0, 100.0);
    fluid.addComponent("methane", 90.0);
    fluid.addComponent("water", 10.0);
    fluid.setMixingRule(10);
    new ThermodynamicOperations(fluid).hydrateFormationTemperature();
    assertEquals(273.15 + 12.76, fluid.getTemperature(), 0.1);

    SystemInterface pressureCase = new SystemSrkCPAstatoil(273.15 + 12.76, 50.0);
    pressureCase.addComponent("methane", 90.0);
    pressureCase.addComponent("water", 10.0);
    pressureCase.setMixingRule(10);
    new ThermodynamicOperations(pressureCase).hydrateFormationPressure();
    assertEquals(100.0, pressureCase.getPressure(), 1.0);
  }
}
