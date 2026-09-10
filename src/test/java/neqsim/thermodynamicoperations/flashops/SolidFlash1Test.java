package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SolidFlash1Test extends neqsim.NeqSimTest {
  @ParameterizedTest
  @ValueSource(doubles = { 308.15, 318.15 })
  void selectedSulfurConservesTraceTbpInventories(double temperature) {
    SystemInterface fluid = sulfurWithTraceTbp();
    fluid.setTemperature(temperature);
    new ThermodynamicOperations(fluid).TPSolidflash();

    assertTrue(fluid.hasPhaseType(PhaseType.SOLID));
    assertSulfurSelection(fluid);
    assertConserved(fluid);
    assertEquals(0.002, fluid.getComponent("S8").getNumberOfmoles(), 1e-14);
    assertEquals(1e-7, fluid.getComponent("heavy1_PC").getNumberOfmoles(), 1e-16);
    assertEquals(1e-9, fluid.getComponent("heavy2_PC").getNumberOfmoles(), 1e-18);
    PhaseInterface solid = fluid.getPhase(PhaseType.SOLID);
    assertEquals(1.0, solid.getComponent("S8").getx(), 1e-12);
    assertEquals(0.0, solid.getComponent("heavy1_PC").getx(), 1e-12);
    assertEquals(0.0, solid.getComponent("heavy2_PC").getx(), 1e-12);

    PhaseInterface gas = fluid.getPhase(PhaseType.GAS);
    SystemInterface extracted = fluid.phaseToSystem("gas");
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      assertEquals(gas.getComponent(i).getNumberOfMolesInPhase(), extracted.getComponent(i).getNumberOfmoles(), 1e-12);
    }
    assertConserved(extracted);
  }

  @Test
  void repeatedSelectedSulfurFlashPreservesSelectionAndInventories() {
    SystemInterface fluid = sulfurWithTraceTbp();
    SolidFlash1 flash = new SolidFlash1(fluid);
    for (int run = 0; run < 3; run++) {
      flash.run();
      assertSulfurSelection(fluid);
      assertConserved(fluid);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  void unsupportedCompetingSolidsDoNotCreateAliasedPhases(boolean checkAllSolids) {
    SystemInterface fluid = sulfurWithTraceTbp();
    fluid.setTemperature(250.0);
    if (checkAllSolids) {
      fluid.setSolidPhaseCheck(true);
    } else {
      fluid.setSolidPhaseCheck("water");
    }
    UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
        () -> new ThermodynamicOperations(fluid).TPSolidflash());
    assertTrue(failure.getMessage().contains("multiple solid"));
    assertFalse(fluid.hasPhaseType(PhaseType.SOLID));
    assertConserved(fluid);
  }

  @ParameterizedTest
  @ValueSource(strings = { "water", "heavy1_PC" })
  void multipleSelectedComponentsWithOneStableSolidAreSupported(String otherSolid) {
    SystemInterface fluid = sulfurWithTraceTbp();
    fluid.setSolidPhaseCheck(otherSolid);
    new ThermodynamicOperations(fluid).TPSolidflash();
    assertTrue(fluid.getComponent(otherSolid).doSolidCheck());
    assertTrue(fluid.getComponent("S8").doSolidCheck());
    assertEquals(1.0, fluid.getPhase(PhaseType.SOLID).getComponent("S8").getx(), 1e-12);
    assertConserved(fluid);
  }

  @Test
  void sulfurPrecipitationDocumentationExample() {
    SystemInterface gas = new SystemSrkEos(273.15 + 50.0, 150.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("H2S", 0.05);
    gas.addComponent("S8", 1e-8);
    gas.setMixingRule(2);
    gas.setMultiPhaseCheck(true);
    gas.setSolidPhaseCheck("S8");
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPSolidflash();
    assertEquals(gas.hasPhaseType(PhaseType.SOLID), gas.hasPhaseType("solid"));
    assertSulfurSelection(gas);
    assertConserved(gas);
  }

  @Test
  void unselectedSulfurDoesNotPrecipitate() {
    SystemInterface fluid = sulfurWithTraceTbp();
    fluid.setSolidPhaseCheck(false);
    fluid.setSolidPhaseCheck("methane");
    new ThermodynamicOperations(fluid).TPSolidflash();
    assertFalse(fluid.hasPhaseType(PhaseType.SOLID));
    assertFalse(fluid.getComponent("S8").doSolidCheck());
    assertConserved(fluid);
  }

  @Test
  void explicitSolidFlashWithoutSelectionStillChecksSolids() {
    SystemInterface fluid = new SystemSrkEos(190.0, 1.0);
    fluid.addComponent("CO2", 1.0);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPSolidflash();
    assertTrue(fluid.hasPhaseType(PhaseType.SOLID));
    assertConserved(fluid);
  }

  private SystemInterface sulfurWithTraceTbp() {
    SystemInterface fluid = new SystemSrkCPAstatoil(308.15, 1.7);
    fluid.addComponent("methane", 10.0);
    fluid.addComponent("H2S", 0.2);
    fluid.addComponent("water", 0.2);
    fluid.addComponent("S8", 0.002);
    fluid.getCharacterization().setTBPModel("PedersenSRK");
    fluid.addTBPfraction("heavy1", 1e-7, 0.33492, 0.90731);
    fluid.addTBPfraction("heavy2", 1e-9, 0.41279, 0.94575);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.setSolidPhaseCheck("S8");
    return fluid;
  }

  private void assertSulfurSelection(SystemInterface fluid) {
    assertTrue(fluid.doSolidPhaseCheck());
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      PhaseInterface phase = fluid.getPhase(p);
      if (phase.getType() == PhaseType.SOLID) {
        continue;
      }
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        assertEquals("S8".equals(phase.getComponent(i).getName()), phase.getComponent(i).doSolidCheck(),
            phase.getComponent(i).getName());
      }
    }
  }

  private void assertConserved(SystemInterface fluid) {
    double totalBeta = 0.0;
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      PhaseInterface phase = fluid.getPhase(p);
      double sumX = 0.0;
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        double x = phase.getComponent(i).getx();
        assertTrue(Double.isFinite(x) && x >= 0.0 && x <= 1.0, "physical mole fraction");
        sumX += x;
      }
      assertEquals(1.0, sumX, 1e-8, "normalized " + phase.getType());
      assertTrue(Double.isFinite(fluid.getBeta(p)) && fluid.getBeta(p) >= 0.0);
      totalBeta += fluid.getBeta(p);
      for (int q = 0; q < p; q++) {
        assertTrue(phase != fluid.getPhase(q), "independent phase storage");
      }
    }
    assertEquals(1.0, totalBeta, 1e-8);
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      double actual = 0.0;
      for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
        actual += fluid.getPhase(p).getComponent(i).getNumberOfMolesInPhase();
      }
      double expected = fluid.getComponent(i).getNumberOfmoles();
      assertEquals(expected, actual, Math.max(1e-14, expected * 1e-8), fluid.getComponent(i).getName());
    }
  }

  @Test
  void testPhaseCheck() {
    SystemInterface fluid1 = new SystemSrkEos();
    fluid1.addComponent("CO2", 1.0);
    fluid1.setPressure(15.448979591836736);
    fluid1.setTemperature(273.15 + 46.734693877551024);
    fluid1.setSolidPhaseCheck("CO2");
    fluid1.setMultiPhaseCheck(true);
    ThermodynamicOperations flashOps = new ThermodynamicOperations(fluid1);
    flashOps.TPSolidflash();
    assertEquals("gas", fluid1.getPhase(0).getType().getDesc());
  }
}
