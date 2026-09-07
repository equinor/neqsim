package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression for neqsim-python issue 357: a recuperator loop without a Recycle unit. */
class CoolerMassBalanceRegressionTest extends neqsim.NeqSimTest {
  @ParameterizedTest
  @ValueSource(strings = { "optimized", "sequential", "parallel", "dataflow", "hybrid" })
  void ltsCoolerConservesFlow(String mode) throws InterruptedException {
    SystemInterface fluid = new SystemPrEos(273.15 + 15.55, 41.37);
    String[] names = { "nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane" };
    double[] fractions = { 0.0132, 0.006, 0.6098, 0.1866, 0.1054, 0.0412, 0.0378 };
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], fractions[i]);
    }
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    Stream feed = new Stream("Feed gas", fluid);
    feed.setFlowRate(498.1, "kmol/hr");
    Separator separator = new Separator("Inlet Separator", feed);
    HeatExchanger exchanger = new HeatExchanger("Gas-Gas Exchanger", separator.getGasOutStream());
    exchanger.setFlowArrangement("counterflow");
    exchanger.setGuessOutTemperature(273.15 + 7.0);
    exchanger.setUAvalue(10875);
    exchanger.setOutPressure(40.68, "bara");
    Cooler cooler = new Cooler("Chiller", exchanger.getOutStream(0));
    cooler.setOutTemperature(-15, "C");
    cooler.setOutPressure(39.99, "bara");
    Separator coldSeparator = new Separator("Cold Separator", cooler.getOutStream());
    exchanger.setFeedStream(1, coldSeparator.getGasOutStream());
    exchanger.setOutStreamSpecificationNumber(1);
    exchanger.setOutPressure(39.3, "bara");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(exchanger);
    process.add(cooler);
    process.add(coldSeparator);
    process.add(exchanger.getOutStream(1));
    assertTrue(process.hasRecycleLoops());
    assertTrue(process.getExecutionStrategyExplanation().contains("implicit recycle"));
    for (double flow : new double[] { 498.1, 550.0 }) {
      feed.setFlowRate(flow, "kmol/hr");
      for (int repeat = 0; repeat < 2; repeat++) {
        runProcess(process, mode);
        assertTrue(separator.getGasOutStream().getFlowRate("kmol/hr") < flow);
        assertBalance(feed, separator.getGasOutStream(), separator.getLiquidOutStream());
        assertBalance(exchanger.getInStream(0), exchanger.getOutStream(0));
        assertBalance(exchanger.getInStream(1), exchanger.getOutStream(1));
        assertBalance(cooler.getInStream(), cooler.getOutStream());
        assertBalance(cooler.getOutStream(), coldSeparator.getGasOutStream(), coldSeparator.getLiquidOutStream());
        assertBalance(feed, separator.getLiquidOutStream(), coldSeparator.getLiquidOutStream(),
            exchanger.getOutStream(1));
        assertEquals(-15.0, cooler.getOutStream().getTemperature("C"), 1e-8);
        assertEquals(39.99, cooler.getOutStream().getPressure("bara"), 1e-8);
        double hotDuty = exchanger.getOutStream(0).getFluid().getEnthalpy()
            - exchanger.getInStream(0).getFluid().getEnthalpy();
        double coldDuty = exchanger.getOutStream(1).getFluid().getEnthalpy()
            - exchanger.getInStream(1).getFluid().getEnthalpy();
        assertEquals(0.0, hotDuty + coldDuty, Math.abs(hotDuty) * 1e-6);
        assertTrue(hotDuty < 0.0);
        exchanger.getEntropyProduction("J/K");
        assertBalance(cooler.getInStream(), cooler.getOutStream());
      }
    }
  }

  private static void runProcess(ProcessSystem process, String mode) throws InterruptedException {
    UUID id = UUID.randomUUID();
    if ("parallel".equals(mode)) {
      process.runParallel(id);
    } else if ("dataflow".equals(mode)) {
      process.runDataflow(id);
    } else if ("hybrid".equals(mode)) {
      process.runHybrid(id);
    } else if ("sequential".equals(mode)) {
      process.setUseOptimizedExecution(false);
      process.run(id);
    } else {
      process.run(id);
    }
  }

  private static void assertBalance(StreamInterface inlet, StreamInterface... outlets) {
    double molarFlow = 0.0;
    double massFlow = 0.0;
    for (StreamInterface outlet : outlets) {
      molarFlow += outlet.getFlowRate("kmol/hr");
      massFlow += outlet.getFlowRate("kg/hr");
    }
    assertEquals(inlet.getFlowRate("kmol/hr"), molarFlow, 1e-5);
    assertEquals(inlet.getFlowRate("kg/hr"), massFlow, 1e-4);
    for (int i = 0; i < inlet.getFluid().getNumberOfComponents(); i++) {
      double componentMoles = 0.0;
      for (StreamInterface outlet : outlets) {
        componentMoles += outlet.getFluid().getComponent(i).getNumberOfmoles();
      }
      assertEquals(inlet.getFluid().getComponent(i).getNumberOfmoles(), componentMoles, 1e-6);
    }
  }
}
