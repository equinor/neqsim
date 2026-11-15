package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.system.SystemSoreideWhitson;
import org.junit.jupiter.api.Test;

/** Tests for the Burgoyne-Nielsen (2026) Soreide-Whitson parameter set. */
class SoreideWhitson2026ParameterSetTest {
  private static final String[] GASES = {"CO2", "H2S", "methane", "nitrogen", "hydrogen", "ethane", "propane",
      "n-butane"};

  /** Reference values evaluated from the authors' tagged procedure-1 refit. */
  @Test
  void reproducesAuthorsCoupledRefitReferenceValues() {
    double[][] conditions = {{353.15, 0.0}, {423.15, 0.0}, {353.15, 2.0}, {473.15, 4.0}};
    double[][] expected = {
        {-0.05152159630824915, 0.017471369354653155, -0.14535827383779654, -0.457370912038235, -0.6714009498253152,
            -0.061696555426724864, -0.1041245441287401, -0.14255971679421342},
        {0.011030118305995984, 0.05888719607456824, 0.008299541224991313, -0.22670183676372635, -0.34498592439591447,
            0.051006997387999915, -0.00548515798352306, -0.045964774759741645},
        {-0.024795025825220092, 0.028191730413884494, -0.09366725165127932, -0.37046550334660233, -0.5809215266095247,
            -0.02407789664986079, -0.0738538587129132, -0.11692322685069857},
        {0.13527130028086048, 0.14529522951918458, 0.30818110294873335, 0.3099013676579756, 0.2159123889189739,
            0.25835247563869634, 0.1657521650635497, 0.10903487388860506}};

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
    double[] expected = {0.1896, 0.1610, 0.4850, 0.4778, 0.4680, 0.4920, 0.5525, 0.5091};
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
    assertEquals(0.004174350745102501, mixingRule.getkijWhitsonSoreideAqueous(components, 2.0, 320.0, h2sIndex,
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
