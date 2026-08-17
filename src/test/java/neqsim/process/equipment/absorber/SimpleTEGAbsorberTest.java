package neqsim.process.equipment.absorber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class SimpleTEGAbsorberTest extends NeqSimTest {
  private static final double BALANCE_TOLERANCE_KG_PER_HOUR = 1.0e-6;

  private static final class AbsorberCase {
    private final StreamInterface wetGas;
    private final StreamInterface leanTeg;
    private final SimpleTEGAbsorber absorber;

    private AbsorberCase(StreamInterface wetGas, StreamInterface leanTeg, SimpleTEGAbsorber absorber) {
      this.wetGas = wetGas;
      this.leanTeg = leanTeg;
      this.absorber = absorber;
    }
  }

  private static double componentFlow(StreamInterface stream, String componentName) {
    double flow = 0.0;
    for (int phaseNumber = 0; phaseNumber < stream.getFluid().getNumberOfPhases(); phaseNumber++) {
      ComponentInterface component = stream.getFluid().getPhase(phaseNumber).getComponent(componentName);
      if (component != null) {
        flow += component.getFlowRate("kg/hr");
      }
    }
    return flow;
  }

  private static Set<String> componentNames(StreamInterface... streams) {
    Set<String> componentNames = new LinkedHashSet<String>();
    for (StreamInterface stream : streams) {
      for (int phaseNumber = 0; phaseNumber < stream.getFluid().getNumberOfPhases(); phaseNumber++) {
        for (int componentNumber = 0; componentNumber < stream.getFluid().getPhase(phaseNumber)
            .getNumberOfComponents(); componentNumber++) {
          componentNames.add(stream.getFluid().getPhase(phaseNumber).getComponent(componentNumber).getName());
        }
      }
    }
    return componentNames;
  }

  private static Map<String, Double> componentMoles(SystemInterface system) {
    Map<String, Double> componentMoles = new LinkedHashMap<String, Double>();
    for (int componentNumber = 0; componentNumber < system.getPhase(0).getNumberOfComponents(); componentNumber++) {
      ComponentInterface component = system.getPhase(0).getComponent(componentNumber);
      componentMoles.put(component.getName(), component.getNumberOfmoles());
    }
    return componentMoles;
  }

  private static void assertFlashInventory(Map<String, Double> expectedMoles, SystemInterface system, String step) {
    double expectedTotal = 0.0;
    for (Map.Entry<String, Double> entry : expectedMoles.entrySet()) {
      double activePhaseMoles = 0.0;
      for (int phaseNumber = 0; phaseNumber < system.getNumberOfPhases(); phaseNumber++) {
        activePhaseMoles += system.getPhase(phaseNumber).getComponent(entry.getKey()).getNumberOfMolesInPhase();
      }
      double tolerance = 1.0e-9 * Math.max(1.0, Math.abs(entry.getValue()));
      assertEquals(entry.getValue(), system.getPhase(0).getComponent(entry.getKey()).getNumberOfmoles(), tolerance,
          step + " must preserve the reported inventory for " + entry.getKey());
      assertEquals(entry.getValue(), activePhaseMoles, tolerance,
          step + " phases must recombine to the feed inventory for " + entry.getKey());
      expectedTotal += entry.getValue();
    }
    assertEquals(expectedTotal, system.getTotalNumberOfMoles(), 1.0e-9 * Math.max(1.0, expectedTotal),
        step + " must preserve total moles");
  }

  private static double maximumLogFugacityResidual(SystemInterface system) {
    if (system.getNumberOfPhases() != 2) {
      return 0.0;
    }
    double maximumResidual = 0.0;
    for (int componentNumber = 0; componentNumber < system.getPhase(0).getNumberOfComponents(); componentNumber++) {
      if (system.getPhase(0).getComponent(componentNumber).getz() <= 1.0e-50) {
        continue;
      }
      double firstLogFugacity = Math
          .log(Math.max(system.getPhase(0).getComponent(componentNumber).getx(), Double.MIN_NORMAL))
          + Math.log(system.getPhase(0).getComponent(componentNumber).getFugacityCoefficient());
      double secondLogFugacity = Math
          .log(Math.max(system.getPhase(1).getComponent(componentNumber).getx(), Double.MIN_NORMAL))
          + Math.log(system.getPhase(1).getComponent(componentNumber).getFugacityCoefficient());
      maximumResidual = Math.max(maximumResidual, Math.abs(firstLogFugacity - secondLogFugacity));
    }
    return maximumResidual;
  }

  private static AbsorberCase runAbsorberCase(boolean setTargetWater) {
    SystemSrkCPAstatoil gasFluid = new SystemSrkCPAstatoil(303.15, 70.0);
    gasFluid.addComponent("methane", 0.90);
    gasFluid.addComponent("ethane", 0.05);
    gasFluid.addComponent("propane", 0.02);
    gasFluid.addComponent("CO2", 0.02);
    gasFluid.addComponent("nitrogen", 0.01);
    gasFluid.setMixingRule(10);
    gasFluid.setMultiPhaseCheck(false);

    Stream gasFeed = new Stream("gas basis", gasFluid);
    gasFeed.setFlowRate(1.0, "MSm3/day");
    gasFeed.setTemperature(30.0, "C");
    gasFeed.setPressure(70.0, "bara");
    StreamSaturatorUtil saturator = new StreamSaturatorUtil("water saturator", gasFeed);

    SystemSrkCPAstatoil tegFluid = new SystemSrkCPAstatoil(308.15, 70.0);
    tegFluid.addComponent("TEG", 0.995);
    tegFluid.addComponent("water", 0.005);
    tegFluid.setMixingRule(10);
    tegFluid.setMultiPhaseCheck(false);

    Stream leanTeg = new Stream("lean TEG", tegFluid);
    leanTeg.setFlowRate(100.0, "kg/hr");

    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("TEG contactor");
    absorber.addGasInStream(saturator.getOutletStream());
    absorber.addSolventInStream(leanTeg);
    absorber.setNumberOfStages(6);
    absorber.setStageEfficiency(0.25);
    absorber.setInternalDiameter(2.0);
    if (setTargetWater) {
      absorber.setWaterInDryGas(30.0e-6);
    }

    ProcessSystem process = new ProcessSystem();
    process.add(gasFeed);
    process.add(saturator);
    process.add(leanTeg);
    process.add(absorber);
    process.run();

    return new AbsorberCase(saturator.getOutletStream(), leanTeg, absorber);
  }

  private static AbsorberCase runLowPressureHighWaterCase() {
    SystemSrkCPAstatoil gasFluid = new SystemSrkCPAstatoil(301.15, 29.0);
    gasFluid.addComponent("nitrogen", 1.42);
    gasFluid.addComponent("CO2", 0.5339);
    gasFluid.addComponent("methane", 95.2412);
    gasFluid.addComponent("ethane", 2.2029);
    gasFluid.addComponent("propane", 0.3231);
    gasFluid.addComponent("i-butane", 0.1341);
    gasFluid.addComponent("n-butane", 0.0827);
    gasFluid.addComponent("i-pentane", 0.0679);
    gasFluid.addComponent("n-pentane", 0.0350);
    gasFluid.addComponent("n-hexane", 0.0176);
    gasFluid.addComponent("water", 0.1088);
    gasFluid.addComponent("TEG", 0.0);
    gasFluid.setMixingRule(10);

    Stream wetGas = new Stream("low-pressure wet gas", gasFluid);
    wetGas.setFlowRate(215700.0, "kg/hr");
    wetGas.setTemperature(28.0, "C");
    wetGas.setPressure(29.0, "bara");

    SystemSrkCPAstatoil tegFluid = gasFluid.clone();
    tegFluid.setMolarComposition(new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.03, 0.97 });
    Stream leanTeg = new Stream("low-pressure lean TEG", tegFluid);
    leanTeg.setFlowRate(6700.0, "kg/hr");
    leanTeg.setTemperature(36.4, "C");
    leanTeg.setPressure(29.0, "bara");

    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("low-pressure TEG contactor");
    absorber.addGasInStream(wetGas);
    absorber.addSolventInStream(leanTeg);
    absorber.setNumberOfStages(3);
    absorber.setStageEfficiency(0.8);
    absorber.setInternalDiameter(2.0);

    ProcessSystem process = new ProcessSystem();
    process.add(wetGas);
    process.add(leanTeg);
    process.add(absorber);
    process.run();

    return new AbsorberCase(wetGas, leanTeg, absorber);
  }

  private static void assertLowPressureFeedFlashConsistency(AbsorberCase absorberCase) {
    SystemInterface wetGas = absorberCase.wetGas.getFluid();
    SystemInterface leanTeg = absorberCase.leanTeg.getFluid();
    SystemInterface mixedFeed = wetGas.clone();
    mixedFeed.setNumberOfPhases(2);
    mixedFeed.init(0);
    mixedFeed.addFluid(leanTeg);
    mixedFeed.init_x_y();
    mixedFeed.initBeta();
    mixedFeed.init(2);
    Map<String, Double> expectedMoles = componentMoles(mixedFeed);

    wetGas.init(3);
    leanTeg.init(3);
    double targetEnthalpy = wetGas.getEnthalpy() + leanTeg.getEnthalpy();
    double guessTemperature = (wetGas.getTemperature() * wetGas.getTotalNumberOfMoles()
        + leanTeg.getTemperature() * leanTeg.getTotalNumberOfMoles()) / mixedFeed.getTotalNumberOfMoles();
    mixedFeed.setTemperature(guessTemperature);

    ThermodynamicOperations operations = new ThermodynamicOperations(mixedFeed);
    operations.TPflash();
    assertFlashInventory(expectedMoles, mixedFeed, "TP flash");
    operations.PHflash(targetEnthalpy, 0);
    mixedFeed.init(2);
    assertFlashInventory(expectedMoles, mixedFeed, "PH flash");

    double relativeEnthalpyResidual = Math.abs(mixedFeed.getEnthalpy() - targetEnthalpy)
        / Math.max(1.0, Math.abs(targetEnthalpy));
    assertTrue(relativeEnthalpyResidual < 1.0e-8,
        "PH flash relative enthalpy residual was " + relativeEnthalpyResidual);
    double fugacityResidual = maximumLogFugacityResidual(mixedFeed);
    assertTrue(fugacityResidual < 1.0e-8, "Maximum log-fugacity residual was " + fugacityResidual);
  }

  private static void assertPortBalances(AbsorberCase absorberCase, StreamInterface dryGas, StreamInterface richTeg) {
    double wetWaterFlow = componentFlow(absorberCase.wetGas, "water");
    double leanWaterFlow = componentFlow(absorberCase.leanTeg, "water");
    double dryWaterFlow = componentFlow(dryGas, "water");
    double richWaterFlow = componentFlow(richTeg, "water");
    double gasWaterTransfer = wetWaterFlow - dryWaterFlow;
    double solventWaterPickup = richWaterFlow - leanWaterFlow;

    assertTrue(gasWaterTransfer > 0.0, "The absorber must remove water from the gas");
    assertEquals(gasWaterTransfer, solventWaterPickup, BALANCE_TOLERANCE_KG_PER_HOUR,
        "Water removed from the gas must appear in the rich TEG");
    assertEquals(wetWaterFlow + leanWaterFlow, dryWaterFlow + richWaterFlow, BALANCE_TOLERANCE_KG_PER_HOUR,
        "The absorber water-component balance must close");
    assertEquals(absorberCase.wetGas.getFlowRate("kg/hr") + absorberCase.leanTeg.getFlowRate("kg/hr"),
        dryGas.getFlowRate("kg/hr") + richTeg.getFlowRate("kg/hr"), BALANCE_TOLERANCE_KG_PER_HOUR,
        "The absorber total mass balance must close");
    for (String componentName : componentNames(absorberCase.wetGas, absorberCase.leanTeg)) {
      double inletComponentFlow = componentFlow(absorberCase.wetGas, componentName)
          + componentFlow(absorberCase.leanTeg, componentName);
      double outletComponentFlow = componentFlow(dryGas, componentName) + componentFlow(richTeg, componentName);
      assertEquals(inletComponentFlow, outletComponentFlow, BALANCE_TOLERANCE_KG_PER_HOUR,
          "The absorber component balance must close for " + componentName);
    }
  }

  private static void assertConservativeOutlets(AbsorberCase absorberCase) {
    StreamInterface dryGas = absorberCase.absorber.getGasOutStream();
    StreamInterface richTeg = absorberCase.absorber.getLiquidOutStream();

    assertPortBalances(absorberCase, dryGas, richTeg);
    assertEquals(1, dryGas.getFluid().getNumberOfPhases(), "The gas outlet must contain one phase");
    assertEquals(PhaseType.GAS, dryGas.getFluid().getPhase(0).getType());
    assertEquals(1, richTeg.getFluid().getNumberOfPhases(), "The rich-TEG outlet must contain one phase");
    assertEquals(PhaseType.AQUEOUS, richTeg.getFluid().getPhase(0).getType(),
        "The rich-TEG outlet must retain its aqueous identity");

    Stream dryGasPassThrough = new Stream("dry gas identity check", dryGas);
    Stream richTegPassThrough = new Stream("rich TEG identity check", richTeg);
    dryGasPassThrough.run();
    richTegPassThrough.run();
    assertEquals(PhaseType.GAS, dryGasPassThrough.getFluid().getPhase(0).getType(),
        "A downstream TP-flashed stream must retain the dry-gas identity");
    assertEquals(PhaseType.AQUEOUS, richTegPassThrough.getFluid().getPhase(0).getType(),
        "A downstream TP-flashed stream must retain the rich-TEG aqueous identity");
    assertPortBalances(absorberCase, dryGasPassThrough, richTegPassThrough);
  }

  @Test
  void transfersWaterToRichTegWhenFeedsHaveDifferentComponentSlates() {
    AbsorberCase absorberCase = runAbsorberCase(false);

    assertConservativeOutlets(absorberCase);
    assertEquals(1.5, absorberCase.absorber.getNumberOfTheoreticalStages(), 1.0e-12);
    assertTrue(absorberCase.absorber.getFsFactor() > 0.0);
    assertTrue(absorberCase.absorber.getMinimumDiameterForFsLimit() > 0.0);
  }

  @Test
  void targetWaterModePreservesWaterAndTotalMass() {
    AbsorberCase absorberCase = runAbsorberCase(true);

    assertConservativeOutlets(absorberCase);
    double dryGasWater = absorberCase.absorber.getGasOutStream().getFluid().getPhase(0).getComponent("water").getx();
    assertEquals(30.0e-6, dryGasWater, 2.0e-8);
  }

  @Test
  void lowPressureHighWaterCasePreservesEveryComponentAcrossReinitialization() {
    AbsorberCase absorberCase = runLowPressureHighWaterCase();

    assertConservativeOutlets(absorberCase);
    assertLowPressureFeedFlashConsistency(absorberCase);
    double inletWaterMoleFraction = absorberCase.wetGas.getFluid().getComponent("water").getz();
    assertEquals(1088.0e-6, inletWaterMoleFraction, 5.0e-6);
    StreamInterface dryGas = absorberCase.absorber.getGasOutStream();
    StreamInterface richTeg = absorberCase.absorber.getLiquidOutStream();
    double dryGasWaterPpm = dryGas.getFluid().getPhase(0).getComponent("water").getx() * 1.0e6;
    double richTegWaterMassFraction = componentFlow(richTeg, "water") / richTeg.getFlowRate("kg/hr");
    assertEquals(55.1, dryGasWaterPpm, 2.0, "Dry-gas water must remain in the qualified process-output band");
    assertEquals(0.0376, richTegWaterMassFraction, 1.0e-3,
        "Rich-TEG water mass fraction must remain in the qualified process-output band");
    assertEquals(29.2, dryGas.getTemperature("C"), 0.5, "Adiabatic contact temperature must remain stable");
  }

  @Test
  void logicalPhaseExtractionIgnoresBackingArrayOrder() {
    AbsorberCase absorberCase = runLowPressureHighWaterCase();
    SystemInterface remapped = absorberCase.absorber.getOutStream().getFluid().clone();
    int firstBackingPhase = remapped.getPhaseIndex(0);
    int secondBackingPhase = remapped.getPhaseIndex(1);
    remapped.setPhaseIndex(0, secondBackingPhase);
    remapped.setPhaseIndex(1, firstBackingPhase);

    SystemInterface extracted = SimpleTEGAbsorber.extractLogicalPhase(remapped, 0);

    assertEquals(remapped.getPhase(0).getType(), extracted.getPhase(0).getType());
    assertEquals(1, extracted.getNumberOfPhases());
    for (int componentNumber = 0; componentNumber < remapped.getPhase(0).getNumberOfComponents(); componentNumber++) {
      ComponentInterface expectedComponent = remapped.getPhase(0).getComponent(componentNumber);
      ComponentInterface actualComponent = extracted.getPhase(0).getComponent(expectedComponent.getName());
      double tolerance = 1.0e-10 * Math.max(1.0, expectedComponent.getNumberOfMolesInPhase());
      assertEquals(expectedComponent.getNumberOfMolesInPhase(), actualComponent.getNumberOfmoles(), tolerance,
          "Logical phase extraction must retain " + expectedComponent.getName());
    }

    Stream extractedPassThrough = new Stream("remapped rich TEG identity check", extracted);
    extractedPassThrough.run();
    assertEquals(PhaseType.AQUEOUS, extractedPassThrough.getFluid().getPhase(0).getType());
  }

  @Test
  void testLowWaterLiquidPhaseDoesNotCauseNaN() {
    SystemSrkCPAstatoil gasFluid = new SystemSrkCPAstatoil(303.15, 70.0);
    gasFluid.addComponent("methane", 0.999);
    gasFluid.addComponent("water", 1.0e-6);
    gasFluid.setMixingRule(10);

    Stream dryGasFeed = new Stream("dry gas", gasFluid);
    dryGasFeed.setFlowRate(1.0, "MSm3/day");
    dryGasFeed.setTemperature(30.0, "C");
    dryGasFeed.setPressure(70.0, "bara");

    SystemSrkCPAstatoil tegFluid = new SystemSrkCPAstatoil(308.15, 70.0);
    tegFluid.addComponent("TEG", 0.9999);
    tegFluid.addComponent("water", 0.0001);
    tegFluid.setMixingRule(10);

    Stream leanTeg = new Stream("lean TEG", tegFluid);
    leanTeg.setFlowRate(100.0, "kg/hr");

    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("TEG contactor dry");
    absorber.addGasInStream(dryGasFeed);
    absorber.addSolventInStream(leanTeg);

    ProcessSystem process = new ProcessSystem();
    process.add(dryGasFeed);
    process.add(leanTeg);
    process.add(absorber);
    process.run();

    assertTrue(Double.isFinite(absorber.getKwater()), "kwater must be finite");
    assertTrue(Double.isFinite(absorber.getGasOutStream().getFlowRate("kg/hr")));
  }
}
