package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression coverage for solvent-specific electrolyte pair parameters (issue #3846). */
class ElectrolyteSolventParameterTest extends neqsim.NeqSimTest {
  @ParameterizedTest
  @CsvSource({"TEG, TEG, Na+, 4.98e-5, -1.22e-4", "TEG, triethylene glycol, Na+, 4.98e-5, -1.22e-4",
      "TEG, TEG, Ca++, 5.4e-5, -1.72e-4", "TEG, triethylene glycol, Ca++, 5.4e-5, -1.72e-4",
      "MEG, MEG, Na+, 8.0e-5, -1.15e-4", "MEG, ethylene glycol, Na+, 8.0e-5, -1.15e-4",
      "MEG, MEG, Ca++, 9.6e-5, -1.38e-4", "MEG, ethylene glycol, Ca++, 9.6e-5, -1.38e-4"})
  void selectsGlycolParametersAndPreservesTemperatureTerms(String solvent, String alias, String cation, double slope,
      double intercept) {
    SystemInterface system = new SystemFurstElectrolyteEos(298.15, 10.01325);
    addComponents(system, solvent, false);
    system.setMixingRule(4);
    system.init(0);
    PhaseInterface phase = system.getPhase(1);
    // Keep database properties while explicitly exercising the mixing rule's long-name alias.
    phase.getComponent(solvent).setComponentName(alias);
    ElectrolyteMixingRulesInterface rule = electrolyteRule(phase);
    rule.calcWij(phase);
    int i = phase.getComponent(cation).getComponentNumber();
    int j = phase.getComponent(alias).getComponentNumber();
    double diameter = phase.getComponent(i).getStokesCationicDiameter();
    double expected = slope * diameter + intercept;
    assertEquals(expected, rule.getWijParameter(i, j), 1e-14);
    assertEquals(expected, rule.getWijParameter(j, i), 1e-14);
    int offset = cation.equals("Ca++") ? 8 : 0;
    double w1 = FurstElectrolyteConstants.getFurstParamTDep(offset) * diameter
        + FurstElectrolyteConstants.getFurstParamTDep(offset + 1);
    double w2 = FurstElectrolyteConstants.getFurstParamTDep(offset + 2) * diameter
        + FurstElectrolyteConstants.getFurstParamTDep(offset + 3);
    assertEquals(w1, rule.gettWijT1Parameter(i, j), 1e-14);
    assertEquals(w1, rule.gettWijT1Parameter(j, i), 1e-14);
    assertEquals(w2, rule.gettWijT2Parameter(i, j), 1e-14);
    assertEquals(w2, rule.gettWijT2Parameter(j, i), 1e-14);
    for (double temperature : new double[] {298.15, 323.15}) {
      double expectedAtTemperature = expected + w1 * (1.0 / temperature - 1.0 / 298.15)
          + w2 * ((298.15 - temperature) / temperature + Math.log(temperature / 298.15));
      assertEquals(expectedAtTemperature, rule.getWij(i, j, temperature), 1e-14);
      assertEquals(expectedAtTemperature, rule.getWij(j, i, temperature), 1e-14);
    }
  }

  static Stream<Arguments> electrolyteSystems() {
    return Stream.of(false, true)
        .flatMap(ionsFirst -> Stream.of(Arguments.of(new SystemFurstElectrolyteEos(298.15, 10.01325), 4, ionsFirst),
            Arguments.of(new SystemFurstElectrolyteEosMod2004(298.15, 10.01325), 4, ionsFirst),
            Arguments.of(new SystemElectrolyteCPA(298.15, 10.01325), 10, ionsFirst),
            Arguments.of(new SystemElectrolyteCPAstatoil(298.15, 10.01325), 10, ionsFirst),
            Arguments.of(new SystemElectrolyteCPAAdvanced(298.15, 10.01325), 10, ionsFirst)));
  }

  @ParameterizedTest
  @MethodSource("electrolyteSystems")
  void initializesTegParametersAcrossSystemsAndComponentOrders(SystemInterface system, int mixingRule,
      boolean ionsFirst) {
    addComponents(system, "TEG", ionsFirst);
    system.setMixingRule(mixingRule);
    for (int repeat = 0; repeat < 2; repeat++) {
      system.init(0);
      for (int p = 0; p < system.getNumberOfPhases(); p++) {
        PhaseInterface phase = system.getPhase(p);
        ElectrolyteMixingRulesInterface rule = electrolyteRule(phase);
        int teg = phase.getComponent("TEG").getComponentNumber();
        for (String cation : new String[] {"Na+", "Ca++"}) {
          int ion = phase.getComponent(cation).getComponentNumber();
          double diameter = phase.getComponent(ion).getStokesCationicDiameter();
          double expected = cation.equals("Na+") ? 4.98e-5 * diameter - 1.22e-4 : 5.4e-5 * diameter - 1.72e-4;
          assertEquals(expected, rule.getWijParameter(ion, teg), 1e-14);
          assertEquals(expected, rule.getWijParameter(teg, ion), 1e-14);
        }
      }
    }
  }

  @ParameterizedTest
  @CsvSource({"TEG, 0.000160864", "MEG, 0.0003394"})
  void reproducesIssueThroughTpFlash(String solvent, double expected) {
    SystemInterface system = new SystemFurstElectrolyteEos(298.15, 10.01325);
    system.addComponent("methane", 0.1);
    system.addComponent("water", 1.0);
    system.addComponent(solvent, 0.5);
    system.addComponent("Na+", 0.001);
    system.addComponent("Cl-", 0.001);
    system.setMixingRule(4);
    new ThermodynamicOperations(system).TPflash();
    system.initProperties();
    PhaseInterface phase = system.getPhase(1);
    int sodium = phase.getComponent("Na+").getComponentNumber();
    int glycol = phase.getComponent(solvent).getComponentNumber();
    assertEquals(expected, electrolyteRule(phase).getWijParameter(sodium, glycol), 1e-14);
  }

  @Test
  void preservesFittedTegPairAndTemperatureCoefficients() {
    SystemInterface system = new SystemFurstElectrolyteEos(298.15, 10.01325);
    addComponents(system, "TEG", false);
    PhaseInterface phase = system.getPhase(0);
    EosMixingRuleHandler handler = new EosMixingRuleHandler();
    handler.getMixingRule(4, phase);
    int ion = phase.getComponent("Na+").getComponentNumber();
    int teg = phase.getComponent("TEG").getComponentNumber();
    handler.wijCalcOrFitted[ion][teg] = 1;
    handler.wijCalcOrFitted[teg][ion] = 1;
    ElectrolyteMixingRulesInterface rule = handler.getElectrolyteMixingRule(phase);
    rule.setWijParameter(ion, teg, 1.23e-4);
    rule.setWijT1Parameter(ion, teg, 2.34e-3);
    rule.setWijT2Parameter(ion, teg, 3.45e-5);
    rule.calcWij(phase);
    assertEquals(1.23e-4, rule.getWijParameter(ion, teg), 1e-14);
    assertEquals(1.23e-4, rule.getWijParameter(teg, ion), 1e-14);
    assertEquals(2.34e-3, rule.gettWijT1Parameter(ion, teg), 1e-14);
    assertEquals(3.45e-5, rule.gettWijT2Parameter(ion, teg), 1e-14);
  }

  private static void addComponents(SystemInterface system, String solvent, boolean ionsFirst) {
    if (!ionsFirst) {
      system.addComponent(solvent, 0.5);
    }
    system.addComponent("Na+", 0.001);
    system.addComponent("Ca++", 0.001);
    system.addComponent("Cl-", 0.003);
    system.addComponent("water", 1.0);
    system.addComponent("methane", 0.1);
    if (ionsFirst) {
      system.addComponent(solvent, 0.5);
    }
  }

  private static ElectrolyteMixingRulesInterface electrolyteRule(PhaseInterface phase) {
    if (phase instanceof PhaseModifiedFurstElectrolyteEosMod2004) {
      return ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getElectrolyteMixingRule();
    }
    return ((PhaseModifiedFurstElectrolyteEos) phase).getElectrolyteMixingRule();
  }
}
