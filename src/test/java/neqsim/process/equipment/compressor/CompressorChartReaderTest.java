package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.File;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

public class CompressorChartReaderTest {

  @Test
  void testSetCurvesToCompressor() throws Exception {
    // Create a fluid using SRK-EoS
    SystemSrkEos fluid = new SystemSrkEos(298.15, 25.0);
    fluid.addComponent("nitrogen", 0.016858);
    fluid.addComponent("CO2", 0.002592);
    fluid.addComponent("methane", 0.925258819674922);
    fluid.addComponent("ethane", 0.0358982969282556);
    fluid.addComponent("propane", 0.00597611555841413);
    fluid.addComponent("i-butane", 0.00349245503479756);
    fluid.addComponent("n-butane", 0.000994270714944656);
    fluid.addComponent("i-pentane", 0.000733327502445109);
    fluid.addComponent("n-pentane", 0.000296887095820468);
    fluid.addComponent("n-hexane", 0.00496887095820468);
    fluid.addComponent("water", 0.00361);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);

    // Create a stream
    Stream stream = new Stream("stream1", fluid);
    stream.setFlowRate(10.1, "MSm3/day");
    stream.setTemperature(25.0, "C");
    stream.setPressure(48.8, "bara");
    stream.run();

    // Create a compressor
    Compressor compressor = new Compressor("compressor1", stream);
    compressor.setCompressorChartType("interpolate and extrapolate");
    compressor.setUsePolytropicCalc(true);
    // compressor.setMaximumSpeed(7383);
    // compressor.setMinimumSpeed(4922);
    compressor.setSpeed(3000);
    compressor.setSolveSpeed(true);
    compressor.setOutletPressure(120.0, "bara");

    // Create a CompressorChartReader and set curves to the compressor
    File file = new File("src/test/java/neqsim/process/equipment/compressor");
    String fileFluid1 = file.getAbsolutePath() + "/curve.csv";
    CompressorChartReader chartReader = new CompressorChartReader(fileFluid1);
    chartReader.setCurvesToCompressor(compressor);

    // Assertions to verify the compressor chart was set correctly
    // assertNotNull(compressor.getCompressorChart().getCurves());
    assertNotNull(compressor.getCompressorChart().getStoneWallCurve());
    assertNotNull(compressor.getCompressorChart().getSurgeCurve());
    assertEquals("kJ/kg", compressor.getCompressorChart().getHeadUnit());

    compressor.run();

    Assertions.assertEquals(5303.277373, compressor.getSpeed(), 0.1);
  }
}
