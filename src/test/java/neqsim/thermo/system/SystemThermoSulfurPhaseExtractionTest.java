package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Conservation regressions for phase extraction after a component-selected S8 flash (issue #3628). Synthetic wet
 * rich-gas inventories come from the stabilization/recompression reproducer.
 */
class SystemThermoSulfurPhaseExtractionTest extends neqsim.NeqSimTest {
  private static final String[] COMPONENTS = { "CO2", "methane", "ethane", "propane", "i-butane", "n-butane",
      "i-pentane", "n-pentane", "n-hexane", "H2S", "water", "C7+_cut1_PC", "C7+_cut2_PC", "C7+_cut3_PC", "C7+_cut4_PC",
      "C7+_cut5_PC", "C7+_cut6_PC", "C7+_cut7_PC", "C7+_cut8_PC", "nitrogen", "oxygen", "S8" };
  // Molar rates [mol/s] before the 33 bara scrubber and the final 180 bara export contact.
  private static final double[] SCRUBBER_RATES = { 5.426175570833792, 178.6334978118069, 20.187382861471725,
      11.528526408861342, 1.803693632013223, 3.9870963545629756, 1.315713211244905, 1.2042618525798088,
      0.9669327412405113, 1.4961268348889367, 2.5160614583232253, 0.060541220465648235, 0.044453125080732986,
      0.02731484770052652, 0.02179656510319209, 0.0016441754883491889, 6.883946664754348e-05, 1.712724761684427e-06,
      3.630373386406932e-08, 2.3259326839179675, 0.0032807414050853006, 0.004244665873915542 };
  private static final double[] EXPORT_RATES = { 5.419922550112308, 178.5622516507791, 20.151883209057278,
      11.466094330797235, 1.7829961362073965, 3.924793989319542, 1.2728872028782405, 1.1533426110138327,
      0.8638106470108451, 1.4848063962066116, 0.1554436772399516, 0.03203990816062581, 0.015543686242774073,
      0.005318656800335305, 0.000808925102338541, 3.0678468821357016e-06, 4.154473891219739e-09, 2.651976774409522e-12,
      6.476974682454262e-16, 2.3256092373875337, 7.904738644944889e-06, 0.0006211014396727213 };
  // Pedersen SRK TBP cuts: molar mass [kg/mol], normal liquid density [g/cm3].
  private static final double[][] CUT_PROPERTIES = { { 0.10847, 0.7411 }, { 0.12040000000000001, 0.755 },
      { 0.13363999999999998, 0.7695 }, { 0.16469999999999999, 0.799 }, { 0.21594, 0.8387 },
      { 0.27333999999999997, 0.8754 }, { 0.33492, 0.90731 }, { 0.41279000000000005, 0.94575 } };

  /** Named extraction must not reintroduce the parent's solid sulfur through an inactive gas slot. */
  @Test
  void namedGasExtractionConservesInventoryWhenStorageZeroIsInactive() {
    SystemInterface source = solidFluid(EXPORT_RATES, 303.15, 180.0);
    assertTrue(source.hasPhaseType("gas"));
    assertTrue(source.hasPhaseType("solid"));
    boolean usesStorageZero = false;
    for (int phase = 0; phase < source.getMaxNumberOfPhases(); phase++) {
      usesStorageZero |= source.getPhaseIndex(phase) == 0;
    }
    assertFalse(usesStorageZero, "Regression requires the solid-flash map to skip storage zero");
    PhaseInterface sourceGas = source.getPhase("gas");
    double[] expected = phaseInventory(sourceGas);
    SystemInterface parentSnapshot = source.clone();

    SystemInterface extracted = source.phaseToSystem("gas");

    assertInventory(expected, extracted, "named gas extraction");
    assertStoredInventories(expected, extracted);
    assertParentUnchanged(parentSnapshot, source);
    assertTrue(extracted.doSolidPhaseCheck());
    assertTrue(extracted.doMultiPhaseCheck());
    assertTrue(extracted.getComponent("S8").doSolidCheck());
    assertFalse(extracted.getComponent("methane").doSolidCheck());
    assertEquals(sourceGas.getType(), extracted.getPhase(0).getType());
  }

  /**
   * Both phase-check modes must conserve wet gas across connected equipment at nearby discharge pressures.
   *
   * @param solidCheck whether to retain the extracted system's solid check
   * @param dischargePressure compressor and interstage-gas pressure in bara
   */
  @ParameterizedTest
  @CsvSource({ "true, 90.0", "false, 90.0", "true, 95.0", "false, 95.0" })
  void extractedWetGasConservesComponentsThroughRecompression(boolean solidCheck, double dischargePressure) {
    SystemInterface source = solidFluid(SCRUBBER_RATES, 305.15, 33.0);
    assertTrue(source.hasPhaseType("gas"));
    assertTrue(source.hasPhaseType("solid"));
    double[] expected = phaseInventory(source.getPhase("gas"));
    SystemInterface parentSnapshot = source.clone();
    SystemInterface gas = source.phaseToSystem(source.getPhaseNumberOfPhase("gas"));
    assertInventory(expected, gas, "indexed gas extraction");
    assertStoredInventories(expected, gas);
    if (!solidCheck) {
      gas.setSolidPhaseCheck(false);
    }
    assertEquals(solidCheck, gas.doSolidPhaseCheck());
    Stream inlet = new Stream("extracted wet gas", gas);
    Compressor compressor = new Compressor("recompressor", inlet);
    compressor.setOutletPressure(dischargePressure);
    compressor.setIsentropicEfficiency(0.75);
    compressor.run();
    assertInventory(expected, compressor.getOutStream().getThermoSystem(), "compressor");

    SystemInterface sideGas = new SystemSrkCPAstatoil(313.15, dischargePressure);
    for (String component : COMPONENTS) {
      if (!component.endsWith("_PC")) {
        sideGas.addComponent(component, 0.0);
      }
    }
    sideGas.addComponent("methane", 1.0);
    sideGas.addComponent("H2S", 0.01);
    sideGas.addComponent("water", 0.02);
    sideGas.setMixingRule(10);
    sideGas.setMultiPhaseCheck(true);
    Stream sideStream = new Stream("wet interstage gas", sideGas);
    sideStream.run();
    for (int component = 0; component < COMPONENTS.length; component++) {
      if (sideGas.hasComponent(COMPONENTS[component])) {
        expected[component] += sideGas.getComponent(COMPONENTS[component]).getNumberOfmoles();
      }
    }
    Mixer mixer = new Mixer("interstage mixer");
    mixer.addStream(compressor.getOutStream());
    mixer.addStream(sideStream);
    mixer.run();
    assertInventory(expected, mixer.getOutStream().getThermoSystem(), "mixer");
    Cooler cooler = new Cooler("aftercooler", mixer.getOutStream());
    cooler.setOutTemperature(303.15);
    cooler.run();
    SystemInterface cooled = cooler.getOutStream().getThermoSystem();
    assertInventory(expected, cooled, "cooler");
    assertTrue(cooled.hasPhaseType("gas"));
    assertTrue(cooled.getNumberOfPhases() > 1, "Wet recompression must exercise liquid dropout");
    assertEquals(solidCheck, cooled.doSolidPhaseCheck());
    assertFalse(cooled.hasPhaseType("solid"), "S8 is undersaturated at this aftercooler condition");
    double[] cooledGas = phaseInventory(cooled.getPhase("gas"));
    SystemInterface extractedAgain = cooled.phaseToSystem("gas");
    assertInventory(cooledGas, extractedAgain, "repeated gas extraction");
    assertStoredInventories(cooledGas, extractedAgain);
    // Reusing and reflashing the extracted system must not change the parent inventory.
    assertParentUnchanged(parentSnapshot, source);
  }

  /**
   * Creates the rich-gas contact feed using the notebook's ordinary TPflash sequence.
   *
   * @param rates component molar rates in mol/s
   * @param temperature temperature in K
   * @param pressure absolute pressure in bar
   * @return equilibrated fluid with component-selected solid sulfur
   */
  private SystemInterface solidFluid(double[] rates, double temperature, double pressure) {
    SystemInterface fluid = new SystemSrkCPAstatoil(temperature, pressure);
    fluid.getCharacterization().setTBPModel("PedersenSRK");
    int cut = 0;
    for (int component = 0; component < COMPONENTS.length; component++) {
      String name = COMPONENTS[component];
      if (name.endsWith("_PC")) {
        fluid.addTBPfraction(name.substring(0, name.length() - 3), rates[component], CUT_PROPERTIES[cut][0],
            CUT_PROPERTIES[cut][1]);
        cut++;
      } else {
        fluid.addComponent(name, rates[component]);
      }
    }
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();
    fluid.setSolidPhaseCheck("S8");
    operations.TPflash();
    fluid.initProperties();
    return fluid;
  }

  /**
   * Returns overall component inventories in the fixed fixture order.
   *
   * @param fluid system to inspect
   * @return component molar rates in mol/s
   */
  private double[] inventory(SystemInterface fluid) {
    double[] result = new double[COMPONENTS.length];
    for (int component = 0; component < result.length; component++) {
      result[component] = fluid.getComponent(COMPONENTS[component]).getNumberOfmoles();
    }
    return result;
  }

  /**
   * Returns component inventories in one physical phase.
   *
   * @param phase physical phase to inspect
   * @return component molar rates in mol/s
   */
  private double[] phaseInventory(PhaseInterface phase) {
    double[] result = new double[COMPONENTS.length];
    for (int component = 0; component < result.length; component++) {
      result[component] = phase.getComponent(COMPONENTS[component]).getNumberOfMolesInPhase();
    }
    return result;
  }

  /**
   * Checks bulk inventory and the independent sum across physical phases.
   *
   * @param expected expected component molar rates in mol/s
   * @param fluid resulting system
   * @param operation operation name for assertion messages
   */
  private void assertInventory(double[] expected, SystemInterface fluid, String operation) {
    for (int component = 0; component < expected.length; component++) {
      String name = COMPONENTS[component];
      double tolerance = Math.max(1.0e-12, Math.abs(expected[component]) * 1.0e-7);
      assertEquals(expected[component], fluid.getComponent(name).getNumberOfmoles(), tolerance,
          operation + " bulk " + name);
      double phaseSum = 0.0;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        phaseSum += fluid.getPhase(phase).getComponent(name).getNumberOfMolesInPhase();
      }
      assertEquals(expected[component], phaseSum, tolerance, operation + " phase sum " + name);
    }
  }

  /**
   * Inactive storage must hold the extracted overall inventory before subsequent flashes.
   *
   * @param expected extracted component molar rates in mol/s
   * @param extracted system to inspect
   */
  private void assertStoredInventories(double[] expected, SystemInterface extracted) {
    for (PhaseInterface phase : extracted.getPhases()) {
      if (phase == null) {
        continue;
      }
      for (int component = 0; component < expected.length; component++) {
        assertEquals(expected[component], phase.getComponent(COMPONENTS[component]).getNumberOfmoles(),
            Math.max(1.0e-12, expected[component] * 1.0e-10), "stored " + COMPONENTS[component]);
      }
    }
  }

  /**
   * Checks that extraction and downstream reuse leave every parent phase inventory unchanged.
   *
   * @param snapshot parent state before extraction
   * @param parent parent after extraction or reuse
   */
  private void assertParentUnchanged(SystemInterface snapshot, SystemInterface parent) {
    assertArrayEquals(inventory(snapshot), inventory(parent), 0.0);
    assertEquals(snapshot.getNumberOfPhases(), parent.getNumberOfPhases());
    for (int phase = 0; phase < parent.getNumberOfPhases(); phase++) {
      assertArrayEquals(phaseInventory(snapshot.getPhase(phase)), phaseInventory(parent.getPhase(phase)), 0.0);
    }
  }
}
