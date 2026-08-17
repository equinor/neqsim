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
import neqsim.thermo.system.SystemSrkCPAstatoil;

@Tag("slow")
class AbsorptionColumnTest extends NeqSimTest {
  private static final class AbsorberCase {
    private final StreamInterface wetGas;
    private final StreamInterface leanTeg;
    private final AbsorptionColumn absorber;

    private AbsorberCase(StreamInterface wetGas, StreamInterface leanTeg, AbsorptionColumn absorber) {
      this.wetGas = wetGas;
      this.leanTeg = leanTeg;
      this.absorber = absorber;
    }
  }

  @Test
  void rigorouslyDehydratesGasWithComponentMurphreeEfficiency() {
    AbsorberCase idealCase = runTegAbsorberCase(1.0);
    AbsorberCase reducedEfficiencyCase = runTegAbsorberCase(0.35);

    assertConvergedAndConservative(idealCase);
    assertConvergedAndConservative(reducedEfficiencyCase);

    double wetGasWater = componentFlow(idealCase.wetGas, "water");
    double idealDryGasWater = componentFlow(idealCase.absorber.getGasOutStream(), "water");
    double reducedEfficiencyDryGasWater = componentFlow(reducedEfficiencyCase.absorber.getGasOutStream(), "water");

    assertTrue(idealDryGasWater < wetGasWater, "The rigorous TEG column must remove water");
    assertTrue(idealDryGasWater < reducedEfficiencyDryGasWater,
        "Reducing the water Murphree efficiency must reduce dehydration");
    assertEquals(4, idealCase.absorber.getNumberOfTrays());
    assertEquals(0.35, reducedEfficiencyCase.absorber.getComponentMurphreeEfficiency(2, "water"), 1.0e-12);
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

    AbsorptionColumn absorber = new AbsorptionColumn("rigorous TEG absorber", 4);
    absorber.addGasInStream(wetGas);
    absorber.addSolventInStream(leanTeg);
    absorber.setTopPressure(70.0);
    absorber.setBottomPressure(70.0);
    absorber.setTemperatureTolerance(1.0e-2);
    absorber.setMassBalanceTolerance(5.0e-2);
    absorber.setEnthalpyBalanceTolerance(5.0e-2);
    absorber.setMaxNumberOfIterations(80);
    for (int trayNumber = 0; trayNumber < absorber.getNumberOfTrays(); trayNumber++) {
      absorber.setComponentMurphreeEfficiency(trayNumber, "water", waterEfficiency);
    }

    ProcessSystem process = new ProcessSystem();
    process.add(wetGas);
    process.add(leanTeg);
    process.add(absorber);
    process.run();
    return new AbsorberCase(wetGas, leanTeg, absorber);
  }

  private static void assertConvergedAndConservative(AbsorberCase absorberCase) {
    AbsorptionColumn absorber = absorberCase.absorber;
    assertTrue(absorber.solved(), absorber.getConvergenceDiagnostics());

    StreamInterface dryGas = absorber.getGasOutStream();
    StreamInterface richTeg = absorber.getLiquidOutStream();
    double inletMass = absorberCase.wetGas.getFlowRate("kg/hr") + absorberCase.leanTeg.getFlowRate("kg/hr");
    double outletMass = dryGas.getFlowRate("kg/hr") + richTeg.getFlowRate("kg/hr");
    assertEquals(inletMass, outletMass, inletMass * 5.0e-3, absorber.getConvergenceDiagnostics());

    for (String componentName : componentNames(absorberCase.wetGas, absorberCase.leanTeg)) {
      double inletComponentFlow = componentFlow(absorberCase.wetGas, componentName)
          + componentFlow(absorberCase.leanTeg, componentName);
      double outletComponentFlow = componentFlow(dryGas, componentName) + componentFlow(richTeg, componentName);
      double tolerance = Math.max(1.0e-6, inletComponentFlow * 5.0e-3);
      assertEquals(inletComponentFlow, outletComponentFlow, tolerance,
          "Component balance must close for " + componentName + ". " + absorber.getConvergenceDiagnostics());
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
