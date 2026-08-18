package neqsim.process.equipment.absorber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemSrkCPA;
import neqsim.thermo.system.SystemSrkEos;

@Tag("slow")
class StrippingColumnTest extends NeqSimTest {
  private static final class StripperCase {
    private final StreamInterface strippingGas;
    private final StreamInterface richLiquid;
    private final StrippingColumn stripper;
    private final ProcessSystem process;

    private StripperCase(StreamInterface strippingGas, StreamInterface richLiquid, StrippingColumn stripper,
        ProcessSystem process) {
      this.strippingGas = strippingGas;
      this.richLiquid = richLiquid;
      this.stripper = stripper;
      this.process = process;
    }
  }

  @Test
  void stripsMethanolFromWaterAtLowPressureWithEfficiencySensitivity() {
    StripperCase idealCase = createMethanolStripperCase(1.0);
    StripperCase reducedEfficiencyCase = createMethanolStripperCase(0.40);

    idealCase.process.run();
    reducedEfficiencyCase.process.run();

    assertAcceptedAndConservative(idealCase);
    assertAcceptedAndConservative(reducedEfficiencyCase);

    double idealStrippedFraction = strippedFraction(idealCase, "methanol");
    double reducedEfficiencyStrippedFraction = strippedFraction(reducedEfficiencyCase, "methanol");
    assertTrue(idealStrippedFraction > 0.0, "The ideal column must strip methanol");
    assertTrue(reducedEfficiencyStrippedFraction > 0.0, "The reduced-efficiency column must still strip methanol");
    assertTrue(idealStrippedFraction > reducedEfficiencyStrippedFraction,
        "Ideal stages must strip more methanol than 40% efficient stages");
    assertEquals(0.40, reducedEfficiencyCase.stripper.getComponentMurphreeEfficiency(2, "methanol"), 1.0e-12);
  }

  @Test
  void stripsLightHydrocarbonsAtModeratePressureWithWarmReuseAndNearbyFeed() {
    StripperCase stripperCase = createHydrocarbonStripperCase();
    StrippingColumn stripper = stripperCase.stripper;

    stripperCase.process.run();

    assertAcceptedAndConservative(stripperCase);
    double initialOverheadPropane = componentFlow(stripper.getOverheadGasStream(), "propane");
    double initialLeanPropane = componentFlow(stripper.getLeanLiquidStream(), "propane");
    double initialPropaneFraction = strippedFraction(stripperCase, "propane");
    double initialPentaneFraction = strippedFraction(stripperCase, "n-pentane");
    assertTrue(initialPropaneFraction > 0.0, "The column must strip propane from the rich oil");
    assertTrue(initialPentaneFraction > 0.0, "The column must strip n-pentane from the rich oil");
    assertTrue(initialPropaneFraction > initialPentaneFraction,
        "The lighter hydrocarbon must be stripped more strongly");

    stripperCase.process.run();

    assertAcceptedAndConservative(stripperCase);
    assertEquals(0, stripper.getLastIterationCount(), stripper.getConvergenceDiagnostics());
    assertEquals("Reused unchanged sequential solution", stripper.getLastSolveStatusReason(),
        stripper.getConvergenceDiagnostics());
    assertEquals(initialOverheadPropane, componentFlow(stripper.getOverheadGasStream(), "propane"),
        Math.max(1.0e-9, 1.0e-9 * initialOverheadPropane));
    assertEquals(initialLeanPropane, componentFlow(stripper.getLeanLiquidStream(), "propane"),
        Math.max(1.0e-9, 1.0e-9 * initialLeanPropane));

    stripperCase.richLiquid.setFlowRate(945.0, "kg/hr");
    stripperCase.richLiquid.run();
    stripperCase.process.run();

    assertAcceptedAndConservative(stripperCase);
    assertNotEquals("Reused unchanged sequential solution", stripper.getLastSolveStatusReason(),
        stripper.getConvergenceDiagnostics());
    assertNotEquals(initialOverheadPropane, componentFlow(stripper.getOverheadGasStream(), "propane"), 1.0e-8);
    assertNotEquals(initialLeanPropane, componentFlow(stripper.getLeanLiquidStream(), "propane"), 1.0e-8);
  }

  private static StripperCase createMethanolStripperCase(double methanolEfficiency) {
    SystemSrkCPA gasFluid = new SystemSrkCPA(333.15, 2.0);
    gasFluid.addComponent("nitrogen", 0.999);
    gasFluid.addComponent("methanol", 0.001);
    gasFluid.addComponent("water", 0.0);
    gasFluid.setMixingRule(10);
    gasFluid.setMultiPhaseCheck(false);

    Stream strippingGas = new Stream("methanol stripping gas", gasFluid);
    strippingGas.setFlowRate(100.0, "kg/hr");
    strippingGas.setTemperature(60.0, "C");
    strippingGas.setPressure(2.0, "bara");

    SystemSrkCPA liquidFluid = new SystemSrkCPA(333.15, 2.0);
    liquidFluid.addComponent("nitrogen", 0.0);
    liquidFluid.addComponent("methanol", 0.04);
    liquidFluid.addComponent("water", 0.96);
    liquidFluid.setMixingRule(10);
    liquidFluid.setMultiPhaseCheck(false);

    Stream richLiquid = new Stream("methanol rich water", liquidFluid);
    richLiquid.setFlowRate(1000.0, "kg/hr");
    richLiquid.setTemperature(60.0, "C");
    richLiquid.setPressure(2.0, "bara");

    StripperCase stripperCase = configureIsothermalStripper("rigorous methanol stripper", strippingGas, richLiquid, 4,
        2.0, 333.15);
    for (int trayNumber = 0; trayNumber < stripperCase.stripper.getNumberOfTrays(); trayNumber++) {
      stripperCase.stripper.setComponentMurphreeEfficiency(trayNumber, "methanol", methanolEfficiency);
    }
    return stripperCase;
  }

  private static StripperCase createHydrocarbonStripperCase() {
    SystemSrkEos gasFluid = new SystemSrkEos(343.15, 12.0);
    gasFluid.addComponent("methane", 0.9900);
    gasFluid.addComponent("propane", 0.0080);
    gasFluid.addComponent("n-butane", 0.0015);
    gasFluid.addComponent("n-pentane", 0.0005);
    gasFluid.addComponent("n-heptane", 0.0);
    gasFluid.setMixingRule("classic");
    gasFluid.setMultiPhaseCheck(false);

    Stream strippingGas = new Stream("hydrocarbon stripping gas", gasFluid);
    strippingGas.setFlowRate(150.0, "kg/hr");
    strippingGas.setTemperature(70.0, "C");
    strippingGas.setPressure(12.0, "bara");

    SystemSrkEos liquidFluid = new SystemSrkEos(343.15, 12.0);
    liquidFluid.addComponent("methane", 0.02);
    liquidFluid.addComponent("propane", 0.08);
    liquidFluid.addComponent("n-butane", 0.12);
    liquidFluid.addComponent("n-pentane", 0.15);
    liquidFluid.addComponent("n-heptane", 0.63);
    liquidFluid.setMixingRule("classic");
    liquidFluid.setMultiPhaseCheck(false);

    Stream richLiquid = new Stream("rich hydrocarbon liquid", liquidFluid);
    richLiquid.setFlowRate(900.0, "kg/hr");
    richLiquid.setTemperature(70.0, "C");
    richLiquid.setPressure(12.0, "bara");

    return configureIsothermalStripper("rigorous hydrocarbon stripper", strippingGas, richLiquid, 5, 12.0, 343.15);
  }

  private static StripperCase configureIsothermalStripper(String name, StreamInterface strippingGas,
      StreamInterface richLiquid, int numberOfTrays, double pressure, double stageTemperature) {
    StrippingColumn stripper = new StrippingColumn(name, numberOfTrays);
    stripper.addStrippingGasStream(strippingGas);
    stripper.addRichLiquidStream(richLiquid);
    stripper.setTopPressure(pressure);
    stripper.setBottomPressure(pressure);
    stripper.setSolverType(DistillationColumn.SolverType.MESH_RESIDUAL);
    stripper.setTemperatureTolerance(1.0e-2);
    stripper.setMassBalanceTolerance(5.0e-2);
    stripper.setEnthalpyBalanceTolerance(5.0e-2);
    stripper.setMaxNumberOfIterations(80);
    for (int trayNumber = 0; trayNumber < stripper.getNumberOfTrays(); trayNumber++) {
      stripper.getTray(trayNumber).setOutTemperature(stageTemperature);
    }

    assertSame(strippingGas, stripper.getStrippingGasStream());
    assertSame(richLiquid, stripper.getRichLiquidStream());

    ProcessSystem process = new ProcessSystem();
    process.add(strippingGas);
    process.add(richLiquid);
    process.add(stripper);
    return new StripperCase(strippingGas, richLiquid, stripper, process);
  }

  private static void assertAcceptedAndConservative(StripperCase stripperCase) {
    StrippingColumn stripper = stripperCase.stripper;
    String diagnostics = stripper.getConvergenceDiagnostics();
    assertTrue(stripper.solved(), diagnostics);
    assertNotEquals(DistillationColumn.SolveStatus.FALLBACK_PRODUCTS, stripper.getLastSolveStatus(), diagnostics);
    assertEquals(DistillationColumn.SolverType.MESH_RESIDUAL, stripper.getLastSolverTypeUsed(), diagnostics);
    assertTrue(stripper.isEnforceMeshResidualTolerance(), diagnostics);
    assertTrue(Double.isFinite(stripper.getLastMeshResidualNorm()), diagnostics);
    assertTrue(stripper.getLastMeshResidualNorm() <= stripper.getMeshResidualTolerance(), diagnostics);
    assertTrue(Double.isFinite(stripper.getEnergyBalanceError()), diagnostics);

    StreamInterface overheadGas = stripper.getOverheadGasStream();
    StreamInterface leanLiquid = stripper.getLeanLiquidStream();
    assertSame(stripper.getGasOutStream(), overheadGas);
    assertSame(stripper.getLiquidOutStream(), leanLiquid);
    assertTrue(overheadGas.getFlowRate("kg/hr") > 0.0, diagnostics);
    assertTrue(leanLiquid.getFlowRate("kg/hr") > 0.0, diagnostics);
    assertTrue(overheadGas.getTemperature("K") > 0.0, diagnostics);
    assertTrue(leanLiquid.getTemperature("K") > 0.0, diagnostics);

    double inletMass = stripperCase.strippingGas.getFlowRate("kg/hr") + stripperCase.richLiquid.getFlowRate("kg/hr");
    double outletMass = overheadGas.getFlowRate("kg/hr") + leanLiquid.getFlowRate("kg/hr");
    assertEquals(inletMass, outletMass, Math.max(1.0e-6, 5.0e-3 * inletMass), diagnostics);

    for (String componentName : componentNames(stripperCase.strippingGas, stripperCase.richLiquid)) {
      double inletComponentFlow = componentFlow(stripperCase.strippingGas, componentName)
          + componentFlow(stripperCase.richLiquid, componentName);
      double outletComponentFlow = componentFlow(overheadGas, componentName) + componentFlow(leanLiquid, componentName);
      assertEquals(inletComponentFlow, outletComponentFlow, Math.max(1.0e-6, 5.0e-3 * Math.abs(inletComponentFlow)),
          componentName + " balance. " + diagnostics);
    }
  }

  private static double strippedFraction(StripperCase stripperCase, String componentName) {
    double richFeedFlow = componentFlow(stripperCase.richLiquid, componentName);
    double overheadGain = componentFlow(stripperCase.stripper.getOverheadGasStream(), componentName)
        - componentFlow(stripperCase.strippingGas, componentName);
    return richFeedFlow > 0.0 ? overheadGain / richFeedFlow : 0.0;
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
    Set<String> names = new LinkedHashSet<String>();
    for (StreamInterface stream : streams) {
      for (int componentNumber = 0; componentNumber < stream.getFluid().getPhase(0)
          .getNumberOfComponents(); componentNumber++) {
        names.add(stream.getFluid().getPhase(0).getComponent(componentNumber).getName());
      }
    }
    return names;
  }
}
