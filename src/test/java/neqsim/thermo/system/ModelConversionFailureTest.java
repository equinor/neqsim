package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseGEUnifacUMRPRU;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** A model conversion must preserve the fluid or fail explicitly. */
class ModelConversionFailureTest extends neqsim.NeqSimTest {
  @Test
  void unsupportedDuanSunConversionCannotReturnAnUnrelatedCo2Fluid() {
    SystemInterface source = brine();
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> source.setModel("Duan-Sun"));
    assertTrue(error.getMessage().contains("Duan-Sun"));
    assertNotNull(error.getCause());
    assertTrue(error.getCause().getMessage().contains("not supported"));
    assertEquals(3, source.getNumberOfComponents());
    assertEquals(55.508, source.getComponent("water").getNumberOfmoles(), 0.0);
  }

  @Test
  void invalidModelNamesFailExplicitly() {
    SystemInterface source = brine();
    assertThrows(IllegalArgumentException.class, () -> source.setModel("no-such-model"));
    assertThrows(IllegalArgumentException.class, () -> source.setModel(null));
  }

  @Test
  void componentCopyFailureDoesNotReturnAPartialFluid() {
    SystemInterface source = brine();
    source.renameComponent("Cl-", "unregistered-conversion-test-component");
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> source.setModel("PR-EOS"));
    assertNotNull(error.getCause());
    assertTrue(error.getMessage().contains("unregistered-conversion-test-component"));
    assertEquals(3, source.getNumberOfComponents());
  }

  @Test
  void supportedConversionPreservesComponentsAmountsAndState() {
    SystemInterface source = brine();
    SystemInterface converted = source.setModel("PR-EOS");
    assertTrue(converted instanceof SystemPrEos);
    assertEquals(source.getNumberOfComponents(), converted.getNumberOfComponents());
    assertEquals(source.getTemperature(), converted.getTemperature(), 0.0);
    assertEquals(source.getPressure(), converted.getPressure(), 0.0);
    for (int i = 0; i < source.getNumberOfComponents(); i++) {
      String name = source.getComponent(i).getComponentName();
      assertEquals(source.getComponent(name).getNumberOfmoles(), converted.getComponent(name).getNumberOfmoles(), 0.0);
    }
  }

  /** Verify the canonical name and aliases on a gas containing characterized and plus cuts. */
  @Test
  void umrCpaPreservesCharacterizedFractionsAndPhaseOptions() {
    SystemInterface source = new SystemSrkEos(298.15, 70.0);
    source.addComponent("methane", 0.98);
    source.addTBPfraction_Mw_Tb("C10", 0.015, 0.136, 447.15);
    source.addPlusFraction("C20", 0.005, 0.282, 0.84);
    source.renameComponent("C10_PC", "AssayCut");
    for (int phase = 0; phase < source.getMaxNumberOfPhases(); phase++) {
      source.getPhase(phase).getComponent("AssayCut").setCpE(1.0e-12);
    }
    source.setMixingRule(2);
    for (String model : new String[] {"UMR-CPA", "UMR-CPA-EoS", "umr-cpa-eos"}) {
      boolean enhanced = "UMR-CPA-EoS".equals(model);
      source.setEnhancedMultiPhaseCheck(enhanced);
      source.setMultiPhaseCheck(enhanced);
      SystemInterface target = source.setModel(model);
      assertTrue(target instanceof SystemUMRCPAEoS);
      assertEquals("UMR-CPA", target.getModelName());
      assertEquals(source.getTemperature(), target.getTemperature(), 0.0);
      assertEquals(source.getPressure(), target.getPressure(), 0.0);
      assertEquals(source.getTotalNumberOfMoles(), target.getTotalNumberOfMoles(), 1e-14);
      assertEquals(source.doMultiPhaseCheck(), target.doMultiPhaseCheck());
      assertEquals(source.doEnhancedMultiPhaseCheck(), target.doEnhancedMultiPhaseCheck());
      for (int phase = 0; phase < target.getMaxNumberOfPhases(); phase++) {
        EosMixingRulesInterface rule = (EosMixingRulesInterface) target.getPhase(phase).getMixingRule();
        assertTrue(rule.getGEPhase() instanceof PhaseGEUnifacUMRPRU);
        for (String name : new String[] {"methane", "AssayCut", "C20_PC"}) {
          ComponentInterface original = source.getComponent(name);
          ComponentInterface converted = target.getPhase(phase).getComponent(name);
          assertEquals(original.getNumberOfmoles(), converted.getNumberOfmoles(), 1e-14);
          assertEquals(original.isIsPlusFraction(), converted.isIsPlusFraction());
          assertEquals(original.isIsTBPfraction(), converted.isIsTBPfraction());
          if (!"methane".equals(name)) {
            assertEquals(original.getNormalLiquidDensity(), converted.getNormalLiquidDensity(), 0.0);
            assertEquals(original.getMolarMass(), converted.getMolarMass(), 1e-14);
            assertEquals(original.getTC(), converted.getTC(), 1e-9);
            assertEquals(original.getPC(), converted.getPC(), 1e-9);
            assertEquals(original.getAcentricFactor(), converted.getAcentricFactor(), 1e-9);
            assertEquals(original.getNormalBoilingPoint(), converted.getNormalBoilingPoint(), 1e-9);
            assertEquals(original.getCpA(), converted.getCpA(), 1e-9);
            assertEquals(original.getCpE(), converted.getCpE(), 0.0);
          }
        }
      }
      new ThermodynamicOperations(target).TPflash();
      assertTrue(target.getNumberOfPhases() >= 1);
      assertTrue(Double.isFinite(target.getPhase(0).getZ()) && target.getPhase(0).getZ() > 0.0);
      assertEquals("SRK-EOS", source.getModelName());
    }
  }

  private static SystemInterface brine() {
    SystemInterface source = new SystemSrkEos(313.15, 5.0);
    source.addComponent("water", 55.508);
    source.addComponent("Na+", 1.0);
    source.addComponent("Cl-", 1.0);
    source.init(0);
    return source;
  }
}
