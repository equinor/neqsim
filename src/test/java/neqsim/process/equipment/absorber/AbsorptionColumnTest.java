package neqsim.process.equipment.absorber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemSrkCPA;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

@Tag("slow")
class AbsorptionColumnTest extends NeqSimTest {
  private static final class AbsorberCase {
    private final StreamInterface gasFeed;
    private final StreamInterface solventFeed;
    private final AbsorptionColumn absorber;

    private AbsorberCase(StreamInterface gasFeed, StreamInterface solventFeed, AbsorptionColumn absorber) {
      this.gasFeed = gasFeed;
      this.solventFeed = solventFeed;
      this.absorber = absorber;
    }
  }

  @Test
  void rigorouslyDehydratesGasWithComponentMurphreeEfficiency() {
    AbsorberCase idealCase = runTegAbsorberCase(1.0);
    AbsorberCase reducedEfficiencyCase = runTegAbsorberCase(0.35);

    assertConvergedAndConservative(idealCase);
    assertConvergedAndConservative(reducedEfficiencyCase);

    double wetGasWater = componentFlow(idealCase.gasFeed, "water");
    double idealDryGasWater = componentFlow(idealCase.absorber.getGasOutStream(), "water");
    double reducedEfficiencyDryGasWater = componentFlow(reducedEfficiencyCase.absorber.getGasOutStream(), "water");
    double idealDryGasWaterFraction = componentMoleFraction(idealCase.absorber.getGasOutStream(), "water");
    double reducedEfficiencyDryGasWaterFraction = componentMoleFraction(
        reducedEfficiencyCase.absorber.getGasOutStream(), "water");

    assertTrue(idealDryGasWater < wetGasWater, "The rigorous TEG column must remove water");
    assertTrue(reducedEfficiencyDryGasWater < wetGasWater,
        "The reduced-efficiency rigorous TEG column must still remove water");
    assertTrue(idealDryGasWaterFraction < reducedEfficiencyDryGasWaterFraction,
        "Reducing water Murphree efficiency must increase treated-gas water mole fraction");
    assertEquals(4, idealCase.absorber.getNumberOfTrays());
    assertEquals(0.35, reducedEfficiencyCase.absorber.getComponentMurphreeEfficiency(2, "water"), 1.0e-12);
  }

  @Test
  void recoversHeavyHydrocarbonsWithLeanOilAtModeratePressure() {
    SystemSrkEos gasFluid = new SystemSrkEos(303.15, 15.0);
    gasFluid.addComponent("methane", 0.920);
    gasFluid.addComponent("ethane", 0.040);
    gasFluid.addComponent("propane", 0.025);
    gasFluid.addComponent("n-butane", 0.010);
    gasFluid.addComponent("n-pentane", 0.005);
    gasFluid.addComponent("n-heptane", 0.0);
    gasFluid.setMixingRule("classic");
    gasFluid.setMultiPhaseCheck(false);

    Stream gasFeed = new Stream("lean oil absorber gas", gasFluid);
    gasFeed.setFlowRate(2000.0, "kg/hr");
    gasFeed.setTemperature(30.0, "C");
    gasFeed.setPressure(15.0, "bara");

    SystemSrkEos oilFluid = new SystemSrkEos(293.15, 15.0);
    oilFluid.addComponent("methane", 0.0);
    oilFluid.addComponent("ethane", 0.0);
    oilFluid.addComponent("propane", 0.0);
    oilFluid.addComponent("n-butane", 0.0);
    oilFluid.addComponent("n-pentane", 0.0);
    oilFluid.addComponent("n-heptane", 1.0);
    oilFluid.setMixingRule("classic");
    oilFluid.setMultiPhaseCheck(false);

    Stream leanOil = new Stream("lean oil", oilFluid);
    leanOil.setFlowRate(600.0, "kg/hr");
    leanOil.setTemperature(20.0, "C");
    leanOil.setPressure(15.0, "bara");

    AbsorberCase absorberCase = runIsothermalAbsorberCase("lean oil absorber", gasFeed, leanOil, 5, 15.0, 298.15);
    assertConvergedAndConservative(absorberCase);

    double propaneRecovery = componentRecovery(gasFeed, absorberCase.absorber.getGasOutStream(), "propane");
    double pentaneRecovery = componentRecovery(gasFeed, absorberCase.absorber.getGasOutStream(), "n-pentane");
    assertTrue(componentFlow(absorberCase.absorber.getGasOutStream(), "n-butane") < componentFlow(gasFeed, "n-butane"),
        "Lean oil must absorb n-butane from the gas");
    assertTrue(
        componentFlow(absorberCase.absorber.getLiquidOutStream(), "n-butane") > componentFlow(leanOil, "n-butane"),
        "The rich oil must gain n-butane");
    assertTrue(pentaneRecovery > propaneRecovery,
        "A lean-oil absorber should recover the heavier hydrocarbon more strongly");
  }

  @Test
  void scrubsMethanolFromLowPressureGasWithWater() {
    SystemSrkCPA gasFluid = new SystemSrkCPA(308.15, 5.0);
    gasFluid.addComponent("methane", 0.985);
    gasFluid.addComponent("methanol", 0.015);
    gasFluid.addComponent("water", 0.0);
    gasFluid.setMixingRule(10);
    gasFluid.setMultiPhaseCheck(false);

    Stream gasFeed = new Stream("methanol contaminated gas", gasFluid);
    gasFeed.setFlowRate(800.0, "kg/hr");
    gasFeed.setTemperature(35.0, "C");
    gasFeed.setPressure(5.0, "bara");

    SystemSrkCPA waterFluid = new SystemSrkCPA(293.15, 5.0);
    waterFluid.addComponent("methane", 0.0);
    waterFluid.addComponent("methanol", 0.001);
    waterFluid.addComponent("water", 0.999);
    waterFluid.setMixingRule(10);
    waterFluid.setMultiPhaseCheck(false);

    Stream washWater = new Stream("wash water", waterFluid);
    washWater.setFlowRate(1000.0, "kg/hr");
    washWater.setTemperature(20.0, "C");
    washWater.setPressure(5.0, "bara");

    AbsorberCase absorberCase = runIsothermalAbsorberCase("methanol water wash", gasFeed, washWater, 4, 5.0, 298.15);
    assertConvergedAndConservative(absorberCase);

    assertTrue(componentFlow(absorberCase.absorber.getGasOutStream(), "methanol") < componentFlow(gasFeed, "methanol"),
        "Water wash must reduce methanol in the gas");
    assertTrue(
        componentFlow(absorberCase.absorber.getLiquidOutStream(), "methanol") > componentFlow(washWater, "methanol"),
        "The rich wash water must gain methanol");
  }

  private static AbsorberCase runTegAbsorberCase(double waterEfficiency) {
    SystemSrkCPAstatoil gasFluid = new SystemSrkCPAstatoil(303.15, 70.0);
    gasFluid.addComponent("nitrogen", 0.01);
    gasFluid.addComponent("CO2", 0.02);
    gasFluid.addComponent("methane", 0.90);
    gasFluid.addComponent("ethane", 0.05);
    gasFluid.addComponent("propane", 0.0195);
    gasFluid.addComponent("water", 0.0005);
    gasFluid.addComponent("TEG", 0.0);
    gasFluid.setMixingRule(10);
    gasFluid.setMultiPhaseCheck(false);

    Stream wetGas = new Stream("wet feed gas", gasFluid);
    wetGas.setFlowRate(5000.0, "kg/hr");
    wetGas.setTemperature(30.0, "C");
    wetGas.setPressure(70.0, "bara");

    SystemSrkCPAstatoil tegFluid = new SystemSrkCPAstatoil(308.15, 70.0);
    tegFluid.addComponent("nitrogen", 0.0);
    tegFluid.addComponent("CO2", 0.0);
    tegFluid.addComponent("methane", 0.0);
    tegFluid.addComponent("ethane", 0.0);
    tegFluid.addComponent("propane", 0.0);
    tegFluid.addComponent("water", 0.005);
    tegFluid.addComponent("TEG", 0.995);
    tegFluid.setMixingRule(10);
    tegFluid.setMultiPhaseCheck(false);

    Stream leanTeg = new Stream("lean TEG", tegFluid);
    leanTeg.setFlowRate(500.0, "kg/hr");
    leanTeg.setTemperature(35.0, "C");
    leanTeg.setPressure(70.0, "bara");

    AbsorberCase absorberCase = configureIsothermalAbsorberCase("rigorous TEG absorber", wetGas, leanTeg, 4, 70.0,
        303.15);
    AbsorptionColumn absorber = absorberCase.absorber;
    for (int trayNumber = 0; trayNumber < absorber.getNumberOfTrays(); trayNumber++) {
      absorber.setComponentMurphreeEfficiency(trayNumber, "water", waterEfficiency);
    }

    run(absorberCase);
    return absorberCase;
  }

  private static AbsorberCase runIsothermalAbsorberCase(String name, StreamInterface gasFeed,
      StreamInterface solventFeed, int numberOfTrays, double pressure, double stageTemperature) {
    AbsorberCase absorberCase = configureIsothermalAbsorberCase(name, gasFeed, solventFeed, numberOfTrays, pressure,
        stageTemperature);
    run(absorberCase);
    return absorberCase;
  }

  private static AbsorberCase configureIsothermalAbsorberCase(String name, StreamInterface gasFeed,
      StreamInterface solventFeed, int numberOfTrays, double pressure, double stageTemperature) {
    AbsorptionColumn absorber = new AbsorptionColumn(name, numberOfTrays);
    absorber.addGasInStream(gasFeed);
    absorber.addSolventInStream(solventFeed);
    absorber.setTopPressure(pressure);
    absorber.setBottomPressure(pressure);
    for (int trayNumber = 0; trayNumber < absorber.getNumberOfTrays(); trayNumber++) {
      absorber.getTray(trayNumber).setOutTemperature(stageTemperature);
    }
    absorber.setTemperatureTolerance(1.0e-2);
    absorber.setMassBalanceTolerance(5.0e-2);
    absorber.setEnthalpyBalanceTolerance(5.0e-2);
    absorber.setMaxNumberOfIterations(80);
    return new AbsorberCase(gasFeed, solventFeed, absorber);
  }

  private static void run(AbsorberCase absorberCase) {
    ProcessSystem process = new ProcessSystem();
    process.add(absorberCase.gasFeed);
    process.add(absorberCase.solventFeed);
    process.add(absorberCase.absorber);
    process.run();
  }

  private static void assertConvergedAndConservative(AbsorberCase absorberCase) {
    AbsorptionColumn absorber = absorberCase.absorber;
    assertTrue(absorber.solved(), absorber.getConvergenceDiagnostics());

    StreamInterface treatedGas = absorber.getGasOutStream();
    StreamInterface richSolvent = absorber.getLiquidOutStream();
    double inletMass = absorberCase.gasFeed.getFlowRate("kg/hr") + absorberCase.solventFeed.getFlowRate("kg/hr");
    double outletMass = treatedGas.getFlowRate("kg/hr") + richSolvent.getFlowRate("kg/hr");
    assertEquals(inletMass, outletMass, inletMass * 5.0e-3, absorber.getConvergenceDiagnostics());

    for (String componentName : componentNames(absorberCase.gasFeed, absorberCase.solventFeed)) {
      double inletComponentFlow = componentFlow(absorberCase.gasFeed, componentName)
          + componentFlow(absorberCase.solventFeed, componentName);
      double outletComponentFlow = componentFlow(treatedGas, componentName) + componentFlow(richSolvent, componentName);
      double tolerance = Math.max(1.0e-6, inletComponentFlow * 5.0e-3);
      assertEquals(inletComponentFlow, outletComponentFlow, tolerance,
          "Component balance must close for " + componentName + ". " + absorber.getConvergenceDiagnostics());
    }
  }

  private static double componentRecovery(StreamInterface gasFeed, StreamInterface treatedGas, String componentName) {
    double feedFlow = componentFlow(gasFeed, componentName);
    return feedFlow > 0.0 ? (feedFlow - componentFlow(treatedGas, componentName)) / feedFlow : 0.0;
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

  private static double componentMoleFraction(StreamInterface stream, String componentName) {
    ComponentInterface component = stream.getFluid().getPhase(0).getComponent(componentName);
    return component == null ? 0.0 : component.getx();
  }

  private static Set<String> componentNames(StreamInterface... streams) {
    Set<String> names = new LinkedHashSet<>();
    for (StreamInterface stream : streams) {
      for (int componentNumber = 0; componentNumber < stream.getFluid().getPhase(0)
          .getNumberOfComponents(); componentNumber++) {
        names.add(stream.getFluid().getPhase(0).getComponent(componentNumber).getName());
      }
    }
    return names;
  }
}
