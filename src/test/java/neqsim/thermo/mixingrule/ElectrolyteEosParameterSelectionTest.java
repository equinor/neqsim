package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEosMod2004;
import neqsim.thermo.system.SystemElectrolyteCPA;
import neqsim.thermo.system.SystemElectrolyteCPAAdvanced;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemFurstElectrolyteEosMod2004;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.constants.FurstElectrolyteConstants;

/** Independent coefficient regressions for EOS-family selection of calculated ionic pairs (#3850). */
class ElectrolyteEosParameterSelectionTest extends neqsim.NeqSimTest {
  @ParameterizedTest
  @CsvSource({"0, false", "0, true", "1, false", "1, true", "2, false", "2, true", "3, false", "3, true", "4, false",
      "4, true"})
  void usesModelCoefficientsAcrossValenceOrderAndRepeatedInitialization(int model, boolean ionsFirst) {
    SystemInterface system = brine(model, ionsFirst);
    for (int repeat = 0; repeat < 3; repeat++) {
      // An unrelated CPA construction and initialization must not change an existing ScRK model.
      brine(2 + repeat, !ionsFirst);
      system.init(0);
      for (int p = 0; p < system.getNumberOfPhases(); p++) {
        PhaseInterface phase = system.getPhase(p);
        ElectrolyteMixingRulesInterface rule = electrolyteRule(phase);
        rule.calcWij(phase);
        for (String cation : new String[] {"Na+", "Ca++"}) {
          int ion = phase.getComponent(cation).getComponentNumber();
          int water = phase.getComponent("water").getComponentNumber();
          int chloride = phase.getComponent("Cl-").getComponentNumber();
          double diameter = phase.getComponent(ion).getStokesCationicDiameter();
          double diameterSum4 = Math.pow(diameter + phase.getComponent(chloride).getPaulingAnionicDiameter(), 4.0);
          // Independent copies of the documented coefficient sets, not the production dispatch.
          double waterExpected;
          double chlorideExpected;
          if (model < 2) {
            waterExpected = 6.99219e-5 * diameter + 4.3984e-6;
            chlorideExpected = -6.06e-8 * diameterSum4 - 2.1795e-5;
          } else if (cation.equals("Ca++")) {
            waterExpected = 5.3997086496838356e-5 * diameter - 1.6449162934393646e-4;
            chlorideExpected = -5.296869462418364e-8 * diameterSum4 - 6.011187378145861e-17;
          } else {
            waterExpected = 4.992083609956488e-5 * diameter - 1.2057478550968262e-4;
            chlorideExpected = -2.063814025090759e-8 * diameterSum4 - 9.499001472075834e-5;
          }
          assertPair(rule, ion, water, waterExpected);
          assertPair(rule, ion, chloride, chlorideExpected);
          // Preserve the existing shared temperature correlation; it is a separate calibration.
          boolean divalent = cation.equals("Ca++");
          double waterT1 = divalent ? 7.5e-3 * diameter - 1.8e-2 : 5.0e-3 * diameter - 1.2e-2;
          double waterT2 = divalent ? 1.5e-5 * diameter - 3.8e-5 : 1.0e-5 * diameter - 2.5e-5;
          double chlorideT1 = divalent ? -3.0e-6 * diameterSum4 + 1.2e-2 : -2.0e-6 * diameterSum4 + 8.0e-3;
          double chlorideT2 = divalent ? -6.0e-9 * diameterSum4 + 2.3e-5 : -4.0e-9 * diameterSum4 + 1.5e-5;
          assertTemperatureTerms(rule, ion, water, waterExpected, waterT1, waterT2);
          assertTemperatureTerms(rule, ion, chloride, chlorideExpected, chlorideT1, chlorideT2);
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4})
  void preservesFittedWaterAndAnionPairsIncludingTemperatureCoefficients(int model) {
    SystemInterface system = brine(model, true);
    PhaseInterface phase = system.getPhase(0);
    EosMixingRuleHandler handler = new EosMixingRuleHandler();
    handler.getMixingRule(model < 2 ? 4 : 10, phase);
    int sodium = phase.getComponent("Na+").getComponentNumber();
    int calcium = phase.getComponent("Ca++").getComponentNumber();
    int water = phase.getComponent("water").getComponentNumber();
    int chloride = phase.getComponent("Cl-").getComponentNumber();
    for (int[] pair : new int[][] {{sodium, water}, {calcium, chloride}}) {
      handler.wijCalcOrFitted[pair[0]][pair[1]] = 1;
      handler.wijCalcOrFitted[pair[1]][pair[0]] = 1;
    }
    ElectrolyteMixingRulesInterface rule = handler.getElectrolyteMixingRule(phase);
    for (int[] pair : new int[][] {{sodium, water}, {calcium, chloride}}) {
      rule.setWijParameter(pair[0], pair[1], 1.23e-4);
      rule.setWijT1Parameter(pair[0], pair[1], 2.34e-3);
      rule.setWijT2Parameter(pair[0], pair[1], 3.45e-5);
    }
    for (int repeat = 0; repeat < 2; repeat++) {
      rule.calcWij(phase);
      for (int[] pair : new int[][] {{sodium, water}, {calcium, chloride}}) {
        assertPair(rule, pair[0], pair[1], 1.23e-4);
        for (int[] direction : new int[][] {pair, {pair[1], pair[0]}}) {
          assertEquals(2.34e-3, rule.gettWijT1Parameter(direction[0], direction[1]), 1e-14);
          assertEquals(3.45e-5, rule.gettWijT2Parameter(direction[0], direction[1]), 1e-14);
        }
      }
    }
  }

  @Test
  void deliberateCustomizationUsesTheSelectedModelTable() {
    double[] originalScrk = FurstElectrolyteConstants.furstParams;
    double[] originalCpa = FurstElectrolyteConstants.furstParamsCPA;
    try {
      FurstElectrolyteConstants.furstParams = originalScrk.clone();
      FurstElectrolyteConstants.furstParamsCPA = originalCpa.clone();
      SystemInterface scrk = brine(0, false);
      SystemInterface cpa = brine(2, false);
      FurstElectrolyteConstants.setFurstParam(2, 1.1e-5);
      FurstElectrolyteConstants.setFurstParam(3, 2.2e-5);
      FurstElectrolyteConstants.setFurstParamCPA(2, 3.3e-5);
      FurstElectrolyteConstants.setFurstParamCPA(3, 4.4e-5);
      for (SystemInterface system : new SystemInterface[] {scrk, cpa}) {
        system.init(0);
        PhaseInterface phase = system.getPhase(0);
        ElectrolyteMixingRulesInterface rule = electrolyteRule(phase);
        rule.calcWij(phase);
        int sodium = phase.getComponent("Na+").getComponentNumber();
        int water = phase.getComponent("water").getComponentNumber();
        double diameter = phase.getComponent(sodium).getStokesCationicDiameter();
        double expected = system == scrk ? 1.1e-5 * diameter + 2.2e-5 : 3.3e-5 * diameter + 4.4e-5;
        assertPair(rule, sodium, water, expected);
      }
    } finally {
      FurstElectrolyteConstants.furstParams = originalScrk;
      FurstElectrolyteConstants.furstParamsCPA = originalCpa;
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4})
  void preservesTheExplicitMdeaCationAnionOverride(int model) {
    SystemInterface system = brine(model, false);
    system.addComponent("MDEA+", 0.1);
    system.addComponent("Cl-", 0.1);
    PhaseInterface phase = system.getPhase(0);
    int cation = phase.getComponent("MDEA+").getComponentNumber();
    int anion = phase.getComponent("Cl-").getComponentNumber();
    EosMixingRuleHandler handler = new EosMixingRuleHandler();
    handler.getMixingRule(model < 2 ? 4 : 10, phase);
    // Exercise calculated-pair dispatch independently of any fitted database override.
    handler.wijCalcOrFitted[cation][anion] = 0;
    handler.wijCalcOrFitted[anion][cation] = 0;
    ElectrolyteMixingRulesInterface rule = handler.getElectrolyteMixingRule(phase);
    double diameterSum = phase.getComponent(cation).getStokesCationicDiameter()
        + phase.getComponent(anion).getPaulingAnionicDiameter();
    assertPair(rule, cation, anion, 1.0e-7 * Math.pow(diameterSum, 4.0) - 9.5e-5);
  }

  private static void assertPair(ElectrolyteMixingRulesInterface rule, int i, int j, double expected) {
    assertEquals(expected, rule.getWijParameter(i, j), 1e-14);
    assertEquals(expected, rule.getWijParameter(j, i), 1e-14);
  }

  private static void assertTemperatureTerms(ElectrolyteMixingRulesInterface rule, int i, int j, double reference,
      double first, double second) {
    for (int[] direction : new int[][] {{i, j}, {j, i}}) {
      assertEquals(first, rule.gettWijT1Parameter(direction[0], direction[1]), 1e-14);
      assertEquals(second, rule.gettWijT2Parameter(direction[0], direction[1]), 1e-14);
      for (double temperature : new double[] {298.15, 323.15}) {
        double expected = reference + first * (1.0 / temperature - 1.0 / 298.15)
            + second * ((298.15 - temperature) / temperature + Math.log(temperature / 298.15));
        assertEquals(expected, rule.getWij(direction[0], direction[1], temperature), 1e-14);
      }
    }
  }

  private static SystemInterface brine(int model, boolean ionsFirst) {
    SystemInterface system;
    if (model == 0) {
      system = new SystemFurstElectrolyteEos(298.15, 1.0);
    } else if (model == 1) {
      system = new SystemFurstElectrolyteEosMod2004(298.15, 1.0);
    } else if (model == 2) {
      system = new SystemElectrolyteCPA(298.15, 1.0);
    } else if (model == 3) {
      system = new SystemElectrolyteCPAstatoil(298.15, 1.0);
    } else {
      system = new SystemElectrolyteCPAAdvanced(298.15, 1.0);
    }
    if (!ionsFirst) {
      system.addComponent("water", 55.508);
    }
    system.addComponent("Na+", 0.5);
    system.addComponent("Ca++", 0.5);
    system.addComponent("Cl-", 1.5);
    if (ionsFirst) {
      system.addComponent("water", 55.508);
    }
    system.setMixingRule(model < 2 ? 4 : 10);
    system.init(0);
    return system;
  }

  private static ElectrolyteMixingRulesInterface electrolyteRule(PhaseInterface phase) {
    if (phase instanceof PhaseModifiedFurstElectrolyteEosMod2004) {
      return ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getElectrolyteMixingRule();
    }
    return ((PhaseModifiedFurstElectrolyteEos) phase).getElectrolyteMixingRule();
  }
}
