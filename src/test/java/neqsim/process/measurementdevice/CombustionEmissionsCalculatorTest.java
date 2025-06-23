package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class CombustionEmissionsCalculatorTest {
  @Test
  void testGetMeasuredValue() {
    SystemInterface fluid = new SystemSrkEos(190, 10);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("CO2", 0.01);
    fluid.addComponent("methane", 1);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.01);
    fluid.addComponent("n-butane", 0.001);
    fluid.addComponent("i-butane", 0.001);

    Stream stream1 = new Stream("stream1", fluid);
    stream1.setFlowRate(1.0, "kg/hr");
    stream1.run();
    CombustionEmissionsCalculator comp = new CombustionEmissionsCalculator("name1", stream1);
    assertEquals(2.77772643250, comp.getMeasuredValue("kg/hr"), 0.0001);
  }

  @Test
  void testAntiSurgeCalc() {
    ProcessSystem process = new ProcessSystem();
    SystemInterface fluid = new SystemSrkEos(290, 50);
    fluid.addComponent("methane", 0.01);

    Stream stream1 = new Stream("stream1", fluid);
    stream1.setFlowRate(2.0, "MSm3/day");
    stream1.run();
    process.add(stream1);

    Compressor compressor1 = new Compressor("compressor", stream1);
    compressor1.setOutletPressure(100, "bara");
    compressor1.run();
    process.add(compressor1);

    double[] chartConditions = new double[] {};
    double[] surgeflow = new double[] {2789.0, 2550.0, 2500.0, 2200.0};
    double[] surgehead = new double[] {80.0, 72.0, 70.0, 65.0};
    compressor1.getCompressorChart().getSurgeCurve().setCurve(chartConditions, surgeflow,
        surgehead);

    Splitter splitter1 = new Splitter("splitter 1", stream1);
    splitter1.setSplitFactors(new double[] {0.5, 0.5});
    splitter1.run();
    process.add(splitter1);

    Calculator calc1 = new Calculator("anti surge calculator 1");
    calc1.addInputVariable(compressor1);
    calc1.setOutputVariable(splitter1);
    calc1.run();
    process.add(calc1);

    Calculator calc2 = new Calculator("anti surge calculator 2");
    calc2.addInputVariable(compressor1);
    calc2.setOutputVariable(splitter1);
    // calc2.run();
    process.add(calc2);

    assertEquals(1547.476990846, stream1.getFlowRate("m3/hr"), 0.1);
    assertEquals(3535.055413, compressor1.getSurgeFlowRate(), 0.1);
    assertEquals(104.9725, compressor1.getPolytropicFluidHead(), 0.1);
    assertEquals(-0.56224816592, compressor1.getDistanceToSurge(), 0.1);
  }
}
