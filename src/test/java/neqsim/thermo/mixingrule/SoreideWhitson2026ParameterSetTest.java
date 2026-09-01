package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.system.SystemSoreideWhitson;
import org.junit.jupiter.api.Test;

/** Tests for the Burgoyne-Nielsen (2026) Soreide-Whitson parameter set. */
class SoreideWhitson2026ParameterSetTest {
  private static final String[] GASES = { "CO2", "H2S", "methane", "nitrogen", "hydrogen", "ethane", "propane",
      "n-butane" };

  /** Reference values generated with the authors' drop-in implementation. */
  @Test
  void reproducesAuthorsDropInReferenceValues() {
    double[][] conditions = { { 280.0, 0.0 }, { 320.0, 2.0 }, { 400.0, 4.0 } };
    double[][] expected = {
        { -0.15430515199999989, -0.040636141140641707, -0.35607665803427202, -0.70525508000000003, -1.1653958207557671,
            -0.23864970725296367, -0.24791483046230281, -0.26490810114410868 },
        { -0.066977075883884005, 0.0047695414544906556, -0.18628176263909707, -0.48908940185930494,
            -0.79098165279971333, -0.09766949672946576, -0.12892229757704116, -0.16434934114272814 },
        { 0.049975792300732402, 0.084943486799094026, 0.096410431980090763, -0.070208107931660546, -0.21768059796366851,
            0.11532368474101742, 0.067371881402650893, 0.012057774238313251 } };

    for (int conditionIndex = 0; conditionIndex < conditions.length; conditionIndex++) {
      for (int gasIndex = 0; gasIndex < GASES.length; gasIndex++) {
        double temperature = conditions[conditionIndex][0];
        double salinity = conditions[conditionIndex][1];
        assertEquals(expected[conditionIndex][gasIndex],
            SoreideWhitson2026ParameterSet.aqueousKij(GASES[gasIndex], "water", temperature, salinity), 2.0e-15,
            GASES[gasIndex]);
        assertEquals(expected[conditionIndex][gasIndex],
            SoreideWhitson2026ParameterSet.aqueousKij("H2O", GASES[gasIndex], temperature, salinity), 2.0e-15,
            GASES[gasIndex] + " reverse pair");
      }
    }
  }

  /** Verify analytical first and second temperature derivatives against centered finite differences. */
  @Test
  void analyticalTemperatureDerivativesMatchFiniteDifferences() {
    double temperature = 335.0;
    double salinity = 1.75;
    double firstStep = 1.0e-3;
    double secondStep = 2.0e-2;

    for (String gas : GASES) {
      double forward = SoreideWhitson2026ParameterSet.aqueousKij(gas, "water", temperature + firstStep, salinity);
      double backward = SoreideWhitson2026ParameterSet.aqueousKij(gas, "water", temperature - firstStep, salinity);
      double finiteFirst = (forward - backward) / (2.0 * firstStep);
      assertEquals(finiteFirst, SoreideWhitson2026ParameterSet.aqueousKijdT(gas, "water", temperature, salinity),
          2.0e-10, gas);

      double center = SoreideWhitson2026ParameterSet.aqueousKij(gas, "water", temperature, salinity);
      forward = SoreideWhitson2026ParameterSet.aqueousKij(gas, "water", temperature + secondStep, salinity);
      backward = SoreideWhitson2026ParameterSet.aqueousKij(gas, "water", temperature - secondStep, salinity);
      double finiteSecond = (forward - 2.0 * center + backward) / (secondStep * secondStep);
      assertEquals(finiteSecond, SoreideWhitson2026ParameterSet.aqueousKijdTdT(gas, "water", temperature, salinity),
          2.0e-8, gas);
    }
  }

  /** Verify published non-aqueous constants and bounded pair routing. */
  @Test
  void appliesNonAqueousConstantsOnlyToSupportedWaterGasPairs() {
    double[] expected = { 0.1896, 0.1610, 0.4850, 0.4778, 0.4680, 0.4920, 0.5525, 0.5091 };
    for (int gasIndex = 0; gasIndex < GASES.length; gasIndex++) {
      assertTrue(SoreideWhitson2026ParameterSet.supportsWaterGasPair(GASES[gasIndex], "water"));
      assertEquals(expected[gasIndex], SoreideWhitson2026ParameterSet.nonAqueousKij(GASES[gasIndex], "water"), 0.0,
          GASES[gasIndex]);
    }
    assertFalse(SoreideWhitson2026ParameterSet.supportsWaterGasPair("n-pentane", "water"));
    assertFalse(SoreideWhitson2026ParameterSet.supportsWaterGasPair("methane", "ethane"));
  }

  /** Verify that the mixing rule routes both phase-specific BIPs to the new parameter set. */
  @Test
  void mixingRuleRoutesAqueousAndNonAqueousPairs() {
    SystemSoreideWhitson system = new SystemSoreideWhitson(320.0, 100.0);
    system.addComponent("H2S", 1.0);
    system.addComponent("water", 10.0);
    ComponentEosInterface[] components = (ComponentEosInterface[]) system.getPhase(0).getcomponentArray();
    int h2sIndex = findComponent(components, "H2S");
    int waterIndex = findComponent(components, "water");

    EosMixingRuleHandler handler = new EosMixingRuleHandler();
    EosMixingRuleHandler.WhitsonSoreideMixingRule mixingRule = handler.new WhitsonSoreideMixingRule();
    assertEquals(0.0047695414544906556, mixingRule.getkijWhitsonSoreideAqueous(components, 2.0, 320.0, h2sIndex,
        waterIndex, SoreideWhitsonParameterization.BURGOYNE_NIELSEN_2026), 2.0e-15);
    assertEquals(0.1610, mixingRule.getkijWhitsonSoreideNonAqueous(components, 320.0, h2sIndex, waterIndex,
        SoreideWhitsonParameterization.BURGOYNE_NIELSEN_2026), 0.0);
  }

  /** Verify the stable enum name and concise Python-friendly alias. */
  @Test
  void resolvesParameterizationNames() {
    assertEquals(SoreideWhitsonParameterization.BURGOYNE_NIELSEN_2026,
        SoreideWhitsonParameterization.byName("BURGOYNE_NIELSEN_2026"));
    assertEquals(SoreideWhitsonParameterization.BURGOYNE_NIELSEN_2026,
        SoreideWhitsonParameterization.byName("bn-2026"));
  }

  private int findComponent(ComponentEosInterface[] components, String componentName) {
    for (int componentIndex = 0; componentIndex < components.length; componentIndex++) {
      if (components[componentIndex].getComponentName().equalsIgnoreCase(componentName)) {
        return componentIndex;
      }
    }
    throw new IllegalArgumentException("Component not found: " + componentName);
  }
}
