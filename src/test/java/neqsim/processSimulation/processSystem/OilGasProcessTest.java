package neqsim.processSimulation.processSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.processsimulation.processequipment.compressor.Compressor;
import neqsim.processsimulation.processequipment.heatExchanger.Cooler;
import neqsim.processsimulation.processequipment.heatExchanger.Heater;
import neqsim.processsimulation.processequipment.mixer.Mixer;
import neqsim.processsimulation.processequipment.pump.Pump;
import neqsim.processsimulation.processequipment.separator.Separator;
import neqsim.processsimulation.processequipment.splitter.Splitter;
import neqsim.processsimulation.processequipment.stream.Stream;
import neqsim.processsimulation.processequipment.stream.StreamInterface;
import neqsim.processsimulation.processequipment.util.Calculator;
import neqsim.processsimulation.processequipment.util.Recycle;
import neqsim.processsimulation.processequipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class OilGasProcessTest extends neqsim.NeqSimTest {
  @Test
  public void runProcess() throws InterruptedException {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.addComponent("water", 51.0);
    thermoSystem.addComponent("nitrogen", 51.0);
    thermoSystem.addComponent("CO2", 51.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 51.0);
    thermoSystem.addComponent("propane", 51.0);
    thermoSystem.addComponent("i-butane", 51.0);
    thermoSystem.addComponent("n-butane", 51.0);
    thermoSystem.addComponent("iC5", 51.0);
    thermoSystem.addComponent("nC5", 1.0);

    thermoSystem.addTBPfraction("C6", 1.0, 86.0 / 1000.0, 0.66);
    thermoSystem.addTBPfraction("C7", 1.0, 91.0 / 1000.0, 0.74);
    thermoSystem.addTBPfraction("C8", 1.0, 103.0 / 1000.0, 0.77);
    thermoSystem.addTBPfraction("C9", 1.0, 117.0 / 1000.0, 0.79);
    thermoSystem.addPlusFraction("C10_C12", 1.0, 145.0 / 1000.0, 0.80);
    thermoSystem.addPlusFraction("C13_C14", 1.0, 181.0 / 1000.0, 0.8279);
    thermoSystem.addPlusFraction("C15_C16", 1.0, 212.0 / 1000.0, 0.837);
    thermoSystem.addPlusFraction("C17_C19", 1.0, 248.0 / 1000.0, 0.849);
    thermoSystem.addPlusFraction("C20_C22", 1.0, 289.0 / 1000.0, 0.863);
    thermoSystem.addPlusFraction("C23_C25", 1.0, 330.0 / 1000.0, 0.875);
    thermoSystem.addPlusFraction("C26_C30", 1.0, 387.0 / 1000.0, 0.88);
    thermoSystem.addPlusFraction("C31_C38", 1.0, 471.0 / 1000.0, 0.90);
    thermoSystem.addPlusFraction("C38_C80", 1.0, 662.0 / 1000.0, 0.92);
    thermoSystem.setMixingRule("classic");
    thermoSystem.setMultiPhaseCheck(true);
    thermoSystem.setMolarComposition(new double[] {0.034266, 0.005269, 0.039189, 0.700553, 0.091154,
        0.050908, 0.007751, 0.014665, 0.004249, 0.004878, 0.004541, 0.007189, 0.006904, 0.004355,
        0.007658, 0.003861, 0.003301, 0.002624, 0.001857, 0.001320, 0.001426, 0.001164, 0.000916});
    // thermoSystem.prettyPrint();

    Stream feedStream = new Stream("feed stream", thermoSystem);
    feedStream.setFlowRate(604094, "kg/hr");
    feedStream.setTemperature(25.5, "C");
    feedStream.setPressure(26.0, "bara");

    neqsim.processsimulation.processequipment.separator.ThreePhaseSeparator seprator1stStage =
        new neqsim.processsimulation.processequipment.separator.ThreePhaseSeparator(
            "1st stage separator", feedStream);

    ThrottlingValve valve1 = new ThrottlingValve("valve1", seprator1stStage.getLiquidOutStream());
    valve1.setOutletPressure(19.0);

    Heater oilHeater = new Heater("oil heater", valve1.getOutletStream());
    oilHeater.setOutTemperature(359.0);

    neqsim.processsimulation.processequipment.separator.ThreePhaseSeparator seprator2ndStage =
        new neqsim.processsimulation.processequipment.separator.ThreePhaseSeparator(
            "2nd stage separator", oilHeater.getOutletStream());

    ThrottlingValve valve2 = new ThrottlingValve("valve2", seprator2ndStage.getLiquidOutStream());
    valve2.setOutletPressure(2.7);

    StreamInterface recircstream1 = valve2.getOutletStream().clone("oilRecirc1");
    recircstream1.setFlowRate(1e-6, "kg/hr");

    neqsim.processsimulation.processequipment.separator.ThreePhaseSeparator seprator3rdStage =
        new neqsim.processsimulation.processequipment.separator.ThreePhaseSeparator(
            "3rd stage separator");
    seprator3rdStage.addStream(valve2.getOutletStream());
    seprator3rdStage.addStream(recircstream1);

    ThrottlingValve pipeloss1st =
        new ThrottlingValve("pipeloss1st", seprator3rdStage.getGasOutStream());
    pipeloss1st.setOutletPressure(2.7 - 0.03);

    Heater coolerLP = new Heater("cooler LP", pipeloss1st.getOutletStream());
    coolerLP.setOutTemperature(273.15 + 25.0);

    Separator sepregenGas = new Separator("sepregenGas", coolerLP.getOutletStream());

    Pump oil1pump = new Pump("oil1pump", sepregenGas.getLiquidOutStream());
    oil1pump.setOutletPressure(19.);

    ThrottlingValve valveLP1 = new ThrottlingValve("valvseLP1", oil1pump.getOutletStream());
    valveLP1.setOutletPressure(2.7);

    Recycle recycle1 = new Recycle("oil recirc 1");
    recycle1.addStream(valveLP1.getOutletStream());
    recycle1.setOutletStream(recircstream1);

    neqsim.processsimulation.processsystem.ProcessSystem operations =
        new neqsim.processsimulation.processsystem.ProcessSystem();
    operations.add(feedStream);
    operations.add(seprator1stStage);
    operations.add(valve1);
    operations.add(oilHeater);
    operations.add(seprator2ndStage);
    operations.add(valve2);
    operations.add(recircstream1);
    operations.add(seprator3rdStage);
    operations.add(pipeloss1st);
    operations.add(coolerLP);
    operations.add(sepregenGas);
    operations.add(oil1pump);
    operations.add(valveLP1);
    operations.add(recycle1);

    operations.run();

    assertEquals(17105.52983567356, seprator3rdStage.getGasOutStream().getFlowRate("kg/hr"), 10.1);

    assertEquals(seprator3rdStage.getGasOutStream().getFlowRate("kg/hr"),
        coolerLP.getOutletStream().getFlowRate("kg/hr"), 1e-4);

    // System.out.println("recycle flow " +
    // recycle1.getOutletStream().getFlowRate("kg/hr"));
    // valveLP1.getOutletStream().getFluid().prettyPrint();
  }

  @Test
  public void runAntiSurgeProcess() throws InterruptedException {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.addComponent("nitrogen", 1.0);
    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 1.0);
    thermoSystem.setMixingRule("classic");

    Stream gas_from_separator = new Stream("gas from separator", thermoSystem);
    gas_from_separator.setPressure(55.0, "bara");
    gas_from_separator.setTemperature(30.0, "C");
    gas_from_separator.setFlowRate(7.0, "MSm3/day");
    gas_from_separator.run();

    Stream recyclegasstream = gas_from_separator.clone("recycle gas stream");
    recyclegasstream.setFlowRate(1e-10, "MSm3/day");
    recyclegasstream.run();

    Mixer gasmixer = new Mixer("gas mixer");
    gasmixer.addStream(gas_from_separator);
    gasmixer.addStream(recyclegasstream);
    gasmixer.run();

    Compressor gascompressor = new Compressor("gas compressor");
    gascompressor.setInletStream(gasmixer.getOutletStream());
    gascompressor.setOutletPressure(90.0, "bara");
    gascompressor.run();

    double fluidh = gascompressor.getPolytropicFluidHead();
    neqsim.processsimulation.processequipment.compressor.CompressorChartGenerator compchartgenerator =
        new neqsim.processsimulation.processequipment.compressor.CompressorChartGenerator(
            gascompressor);
    gascompressor.setCompressorChart(compchartgenerator.generateCompressorChart("mid range"));

    Cooler gascooler = new Cooler("gas cooler");
    gascooler.setInletStream(gascompressor.getOutletStream());
    gascooler.setOutTemperature(30.0, "C");
    gascooler.run();

    Separator gassep = new Separator("gas separator");
    gassep.setInletStream(gascooler.getOutletStream());
    gassep.run();

    Splitter gassplitter = new Splitter("gas splitter");
    gassplitter.setInletStream(gassep.getGasOutStream());
    gassplitter.setFlowRates(new double[] {7.0, 1.2}, "MSm3/day");
    gassplitter.run();

    ThrottlingValve antisurgevalve = new ThrottlingValve("gas valve");
    antisurgevalve.setInletStream(gassplitter.getSplitStream(1));
    antisurgevalve.setOutletPressure(55.0, "bara");
    antisurgevalve.run();

    Recycle recycl = new Recycle("rec");
    recycl.addStream(antisurgevalve.getOutletStream());
    recycl.setOutletStream(recyclegasstream);
    recycl.run();

    Calculator antisurgeCalculator = new Calculator("anti surge calculator");
    antisurgeCalculator.addInputVariable(gascompressor);
    antisurgeCalculator.setOutputVariable(gassplitter);

    neqsim.processsimulation.processsystem.ProcessSystem operations =
        new neqsim.processsimulation.processsystem.ProcessSystem();
    operations.add(gas_from_separator);
    operations.add(recyclegasstream);
    operations.add(gasmixer);
    operations.add(gascompressor);
    operations.add(gascooler);
    operations.add(gassep);
    operations.add(gassplitter);
    operations.add(antisurgevalve);
    operations.add(recycl);
    operations.add(antisurgeCalculator);
    operations.run();

    // gascompressor.setOutletPressure(90.0);
    // gascompressor.getCompressorChart().setUseCompressorChart(false);

    operations.run();
    assertEquals(6.9999999, gassplitter.getSplitStream(0).getFlowRate("MSm3/day"), 1e-2);
    assertEquals(0.0, gassplitter.getSplitStream(1).getFlowRate("MSm3/day"), 1e-2);
    assertEquals(4009.59769517, gascompressor.getCompressorChart().getSurgeCurve()
        .getSurgeFlow(gascompressor.getPolytropicFluidHead()), 1);
    assertEquals(90.91657683, gascompressor.getOutletPressure(), 1e-1);

    gas_from_separator.setFlowRate(2.0, "MSm3/day");
    operations.run();
    // assertEquals(4.00961638,
    // gassplitter.getSplitStream(1).getFlowRate("MSm3/day"), 1e-4);
    assertEquals(2.005737385700, gassplitter.getSplitStream(0).getFlowRate("MSm3/day"), 1e-2);
    assertEquals(4157.46978573787, gascompressor.getCompressorChart().getSurgeCurve()
        .getSurgeFlow(gascompressor.getPolytropicFluidHead()), 1);
    assertEquals(4157.470818, gascompressor.getInletStream().getFlowRate("m3/hr"), 10);
    assertEquals(109.8098956, gascompressor.getOutletPressure(), 1e-1);

    gas_from_separator.setFlowRate(8.0, "MSm3/day");
    operations.run();
    assertEquals(1.0000000000014376E-6, gassplitter.getSplitStream(1).getFlowRate("MSm3/day"),
        1e-1);
    assertEquals(8.000000000000004, gassplitter.getSplitStream(0).getFlowRate("MSm3/day"), 1e-2);
    assertEquals(3914.332053456, gascompressor.getCompressorChart().getSurgeCurve()
        .getSurgeFlow(gascompressor.getPolytropicFluidHead()), 10);
    assertEquals(82.7494476493, gascompressor.getOutletPressure(), 1e-1);

    gas_from_separator.setFlowRate(0.5, "MSm3/day");

    operations.run();
    assertEquals(5.70145187, gassplitter.getSplitStream(1).getFlowRate("MSm3/day"), 1e-2);
    assertEquals(0.50010559709, gassplitter.getSplitStream(0).getFlowRate("MSm3/day"), 5e-2);
    assertEquals(4159.042541, gascompressor.getCompressorChart().getSurgeCurve()
        .getSurgeFlow(gascompressor.getPolytropicFluidHead()), 10);
    assertEquals(112.0488937, gascompressor.getOutletPressure(), 1);
  }

  @Test
  public void runAntiSurgeProcess2() throws InterruptedException {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.addComponent("nitrogen", 1.0);
    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 1.0);
    thermoSystem.setMixingRule("classic");

    Stream gas_from_separator = new Stream("feed stream", thermoSystem);
    gas_from_separator.setPressure(55.0, "bara");
    gas_from_separator.setTemperature(30.0, "C");
    gas_from_separator.setFlowRate(7.0, "MSm3/day");

    Stream recyclegasstream = gas_from_separator.clone("Recycle gas");
    recyclegasstream.setFlowRate(1e-10, "MSm3/day");

    Mixer gasmixer = new Mixer("gas mixer");
    gasmixer.addStream(gas_from_separator);
    gasmixer.addStream(recyclegasstream);

    Cooler gascooler2 = new Cooler("gas cooler2 ");
    gascooler2.setInletStream(gasmixer.getOutletStream());
    gascooler2.setOutTemperature(30.0, "C");

    Compressor gascompressor = new Compressor("gas compressor");
    gascompressor.setInletStream(gascooler2.getOutletStream());
    gascompressor.setPolytropicEfficiency(0.85);
    gascompressor.setOutletPressure(90.0, "bara");

    Cooler gascooler = new Cooler("gas cooler");
    gascooler.setInletStream(gascompressor.getOutletStream());
    gascooler.setOutTemperature(30.0, "C");

    Separator gassep = new Separator("gas separator");
    gassep.setInletStream(gascooler.getOutletStream());

    Splitter gassplitter = new Splitter("gas splitter");
    gassplitter.setInletStream(gassep.getGasOutStream());
    gassplitter.setFlowRates(new double[] {7.0, 1.2}, "MSm3/day");

    ThrottlingValve antisurgevalve = new ThrottlingValve("gas valve");
    antisurgevalve.setInletStream(gassplitter.getSplitStream(1));
    antisurgevalve.setOutletPressure(55.0, "bara");

    Recycle recycl = new Recycle("rec");
    recycl.addStream(antisurgevalve.getOutletStream());
    recycl.setOutletStream(recyclegasstream);
    recycl.setFlowAccuracy(1e-2);

    neqsim.processsimulation.processsystem.ProcessSystem operations =
        new neqsim.processsimulation.processsystem.ProcessSystem();
    operations.add(gas_from_separator);
    operations.add(recyclegasstream);
    operations.add(gasmixer);
    operations.add(gascooler2);
    operations.add(gascompressor);
    operations.add(gascooler);
    operations.add(gassep);
    operations.add(gassplitter);
    operations.add(antisurgevalve);
    operations.add(recycl);
    operations.run();

    double fluidh = gascompressor.getPolytropicFluidHead();
    double flowratetocompressor = gascompressor.getInletStream().getFlowRate("MSm3/day");

    neqsim.processsimulation.processequipment.compressor.CompressorChartGenerator compchartgenerator =
        new neqsim.processsimulation.processequipment.compressor.CompressorChartGenerator(
            gascompressor);
    gascompressor.setCompressorChart(compchartgenerator.generateCompressorChart("mid range"));
    gascompressor.setOutletPressure(90.0);
    gascompressor.getCompressorChart().setUseCompressorChart(false);

    Calculator antisurgeCalculator = new Calculator("anti surge calculator");
    antisurgeCalculator.addInputVariable(gascompressor);
    antisurgeCalculator.setOutputVariable(gassplitter);

    operations.add(antisurgeCalculator);

    operations.run();
    assertEquals(7.00019789, gassplitter.getSplitStream(0).getFlowRate("MSm3/day"), 1e-2);
    assertEquals(0.0, gassplitter.getSplitStream(1).getFlowRate("MSm3/day"), 1e-1);
    assertEquals(4685.038961, gascompressor.getCompressorChart().getSurgeCurve()
        .getSurgeFlow(gascompressor.getPolytropicFluidHead()), 10);
    assertEquals(5176.97051519596, gascompressor.getInletStream().getFlowRate("m3/hr"), 50);
    assertEquals(90.0, gascompressor.getOutletPressure(), 1e-1);

    gas_from_separator.setFlowRate(1.0, "MSm3/day");
    operations.run();
    double flow = gasmixer.getOutletStream().getFlowRate("MSm3/day");
    assertEquals(5.33483946, gassplitter.getSplitStream(1).getFlowRate("MSm3/day"), 1e-2);
    assertEquals(1.0, gassplitter.getSplitStream(0).getFlowRate("MSm3/day"), 1e-2);
    assertEquals(4685.0389615, gascompressor.getCompressorChart().getSurgeCurve()
        .getSurgeFlow(gascompressor.getPolytropicFluidHead()), 10);
    assertEquals(4684.996901, gascompressor.getInletStream().getFlowRate("m3/hr"), 10);
    assertEquals(90.0, gascompressor.getOutletPressure(), 1e-1);

    gas_from_separator.setFlowRate(9.0, "MSm3/day");
    operations.run();
    assertEquals(1.0224661428980936E-8, gassplitter.getSplitStream(1).getFlowRate("MSm3/day"),
        1e-2);
    assertEquals(9.0, gassplitter.getSplitStream(0).getFlowRate("MSm3/day"), 1e-2);
    assertEquals(4685.0389615, gascompressor.getCompressorChart().getSurgeCurve()
        .getSurgeFlow(gascompressor.getPolytropicFluidHead()), 10);
    assertEquals(6656.10407208, gascompressor.getInletStream().getFlowRate("m3/hr"), 10);
    assertEquals(90.0, gascompressor.getOutletPressure(), 1e-2);

    gas_from_separator.setFlowRate(5.5, "MSm3/day");
    operations.run();
    assertEquals(0.834839487572494, gassplitter.getSplitStream(1).getFlowRate("MSm3/day"), 1e-2);
    assertEquals(5.5, gassplitter.getSplitStream(0).getFlowRate("MSm3/day"), 1e-2);
    assertEquals(4685.0389615, gascompressor.getCompressorChart().getSurgeCurve()
        .getSurgeFlow(gascompressor.getPolytropicFluidHead()), 10);
    assertEquals(4685.038986, gascompressor.getInletStream().getFlowRate("m3/hr"), 10);
    assertEquals(90.0, gascompressor.getOutletPressure(), 1e-2);
  }
}
