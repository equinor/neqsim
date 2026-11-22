package neqsim.thermo.util.mercury;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermo.util.readwrite.EclipseFluidReadWrite;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests the conversion of fluids to UMR-PRU-MC for mercury studies with {@link MercuryFluidConverter}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class MercuryFluidConverterTest {
  /** Characterized oil with lumped pseudo-components and the Peng-Robinson equation of state. */
  private static final String E300_FILE = "src/test/java/neqsim/thermo/util/readwrite/fluid1.e300";

  /** Moles of ideal gas in one standard cubic metre at 15 C and 1.01325 bara. */
  private static final double MOLES_PER_SM3 = 42.29255785;

  /**
   * Flash a copy of a fluid and return the mole fraction of the gas phase.
   *
   * @param fluid fluid to flash
   * @param temperature temperature in kelvin
   * @param pressure pressure in bara
   * @return mole fraction of gas
   */
  private static double gasFraction(SystemInterface fluid, double temperature, double pressure) {
    SystemInterface copy = fluid.clone();
    copy.setTemperature(temperature);
    copy.setPressure(pressure);
    copy.setMultiPhaseCheck(true);
    new ThermodynamicOperations(copy).TPflash();
    assertTrue(copy.hasPhaseType("gas"));
    return copy.getBeta(copy.getPhaseNumberOfPhase("gas"));
  }

  @Test
  void convertsAnE300FluidAndKeepsThePseudoComponents() {
    SystemInterface source = EclipseFluidReadWrite.read(E300_FILE);
    SystemInterface converted = MercuryFluidConverter.toUmrPruMc(source, 100.0, "ug/Sm3");

    assertTrue(converted instanceof SystemUMRPRUMCEos);
    assertEquals(source.getNumberOfComponents() + 1, converted.getNumberOfComponents());
    assertEquals(source.getTemperature(), converted.getTemperature(), 1.0e-12);
    assertEquals(source.getPressure(), converted.getPressure(), 1.0e-12);
    assertTrue(converted.doMultiPhaseCheck());

    int pseudoComponents = 0;
    for (int i = 0; i < source.getNumberOfComponents(); i++) {
      ComponentInterface from = source.getPhase(0).getComponent(i);
      ComponentInterface to = converted.getPhase(0).getComponent(i);
      assertEquals(from.getNumberOfmoles(), to.getNumberOfmoles(), 1.0e-12 * from.getNumberOfmoles());
      if (from.isIsTBPfraction()) {
        pseudoComponents++;
        assertTrue(to.isIsTBPfraction());
        assertTrue(to.getComponentName().endsWith("_PC"));
        assertEquals(from.getTC(), to.getTC(), 1.0e-10);
        assertEquals(from.getPC(), to.getPC(), 1.0e-10);
        assertEquals(from.getAcentricFactor(), to.getAcentricFactor(), 1.0e-12);
        assertEquals(from.getMolarMass(), to.getMolarMass(), 1.0e-12);
        assertEquals(from.getVolumeCorrectionConst(), to.getVolumeCorrectionConst(), 1.0e-12);
      } else {
        assertEquals(from.getComponentName(), to.getComponentName());
      }
    }
    assertEquals(12, pseudoComponents);

    // 100 ug per standard cubic metre of the whole fluid.
    ComponentInterface mercury = converted.getPhase(0).getComponent("mercury");
    assertEquals(100.0, mercury.getz() * mercury.getMolarMass() * 1.0e9 * MOLES_PER_SM3, 1.0e-6);

    // The phase split of the converted fluid is close to that of the Peng-Robinson fluid of the file.
    assertEquals(gasFraction(source, 333.15, 60.0), gasFraction(converted, 333.15, 60.0), 0.02);
    assertEquals(gasFraction(source, 288.15, 1.01325), gasFraction(converted, 288.15, 1.01325), 0.02);
  }

  @Test
  void mercuryContentUnits() {
    SystemInterface gas = new SystemSrkEos(273.15 + 20.0, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");

    // In a single gas phase the content of the fluid is the content of the gas.
    SystemInterface converted = MercuryFluidConverter.toUmrPruMc(gas, 50.0, "ug/Sm3");
    MercuryPhaseEquilibrium equilibrium = new MercuryPhaseEquilibrium(converted);
    equilibrium.run();
    assertEquals(1, equilibrium.getFluid().getNumberOfPhases());
    assertFalse(equilibrium.hasFreeMercury());
    assertEquals(50.0, equilibrium.getMercuryConcentration(0, "ug/Sm3"), 1.0e-6);

    converted = MercuryFluidConverter.toUmrPruMc(gas, 50.0, "ug/Nm3");
    equilibrium = new MercuryPhaseEquilibrium(converted);
    equilibrium.run();
    assertEquals(50.0, equilibrium.getMercuryConcentration(0, "ug/Nm3"), 1.0e-6);

    converted = MercuryFluidConverter.toUmrPruMc(gas, 20.0, "ug/kg");
    equilibrium = new MercuryPhaseEquilibrium(converted);
    equilibrium.run();
    assertEquals(20.0, equilibrium.getMercuryConcentration(0, "ug/kg"), 1.0e-8);

    converted = MercuryFluidConverter.toUmrPruMc(gas, 3.0, "ppb");
    assertEquals(3.0e-9, converted.getPhase(0).getComponent("mercury").getz(), 1.0e-18);

    converted = MercuryFluidConverter.toUmrPruMc(gas, 2.0e-8, "mole fraction");
    assertEquals(2.0e-8, converted.getPhase(0).getComponent("mercury").getz(), 1.0e-18);
    assertEquals(gas.getTotalNumberOfMoles(),
        converted.getTotalNumberOfMoles() - converted.getPhase(0).getComponent("mercury").getNumberOfmoles(), 1.0e-12);
  }

  @Test
  void keepsOrReplacesMercuryOfTheSourceFluid() {
    SystemInterface gas = new SystemSrkEos(273.15 + 20.0, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("water", 0.1);
    gas.addComponent("mercury", 1.0e-7);
    gas.setMixingRule("classic");

    SystemInterface kept = MercuryFluidConverter.toUmrPruMc(gas);
    assertEquals(3, kept.getNumberOfComponents());
    assertEquals(1.0e-7, kept.getPhase(0).getComponent("mercury").getNumberOfmoles(), 1.0e-18);

    SystemInterface replaced = MercuryFluidConverter.toUmrPruMc(gas, 5.0e-9, "mole fraction");
    assertEquals(3, replaced.getNumberOfComponents());
    assertEquals(5.0e-9, replaced.getPhase(0).getComponent("mercury").getz(), 1.0e-18);

    SystemInterface removed = MercuryFluidConverter.toUmrPruMc(gas, 0.0, "ug/Sm3");
    assertEquals(2, removed.getNumberOfComponents());
    assertFalse(removed.getPhase(0).hasComponent("mercury"));

    // The converted fluid uses the UMR-PRU water parameters and forms an aqueous phase.
    new ThermodynamicOperations(kept).TPflash();
    assertTrue(kept.hasPhaseType("aqueous"));
  }

  @Test
  void rejectsInvalidInput() {
    SystemInterface gas = new SystemSrkEos(273.15 + 20.0, 50.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");

    assertThrows(IllegalArgumentException.class, () -> MercuryFluidConverter.toUmrPruMc(null));
    assertThrows(IllegalArgumentException.class, () -> MercuryFluidConverter.toUmrPruMc(null, 1.0, "ppb"));
    assertThrows(IllegalArgumentException.class, () -> MercuryFluidConverter.toUmrPruMc(gas, -1.0, "ppb"));
    assertThrows(IllegalArgumentException.class, () -> MercuryFluidConverter.toUmrPruMc(gas, Double.NaN, "ppb"));
    assertThrows(IllegalArgumentException.class, () -> MercuryFluidConverter.toUmrPruMc(gas, 1.0, "mg/l"));
    assertThrows(IllegalArgumentException.class, () -> MercuryFluidConverter.toUmrPruMc(gas, 1.5, "mole fraction"));
    assertThrows(IllegalArgumentException.class, () -> MercuryFluidConverter.toUmrPruMc(gas, 2.0e9, "ug/kg"));
  }
}
