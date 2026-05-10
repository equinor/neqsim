package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class PipelineTest {
  /**
   * Creates and initializes a feed stream for the pipeline regression cases.
   *
   * @param name stream name
   * @param flow flow rate in MSm3/day
   * @param temperature temperature in degrees Celsius
   * @param pressure pressure in bara
   * @return initialized feed stream
   */
  private Stream createFeedStream(String name, double flow, double temperature, double pressure) {
    SystemInterface testSystem = new SystemSrkEos(273.15 + temperature, pressure);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("ethane", 0.1);
    testSystem.setMixingRule("classic");

    Stream stream = new Stream(name, testSystem);
    stream.setFlowRate(flow, "MSm3/day");
    stream.setTemperature(temperature, "C");
    stream.setPressure(pressure, "bara");
    stream.run();
    return stream;
  }

  @Test
  public void testMain() {
    double flow = 60.0;
    double temperature = 20.0;
    double pressure = 200.0;

    double diameter = 1.0;
    double length = 700000.0;
    double elevation = 0;
    double wallroughness = 5e-6;

    Stream pipelineFeed = createFeedStream("pipeline feed", flow, temperature, pressure);
    Stream simplePipelineFeed =
        createFeedStream("simple pipeline feed", flow, temperature, pressure);
    Stream beggsBrillsFeed = createFeedStream("beggs brills feed", flow, temperature, pressure);

    OnePhasePipeLine pipeline = new OnePhasePipeLine("pipeline", pipelineFeed);
    pipeline.setNumberOfLegs(1);
    pipeline.setPipeDiameters(new double[] {diameter, diameter});
    pipeline.setLegPositions(new double[] {0, length});
    pipeline.setHeightProfile(new double[] {0, elevation});
    pipeline.setPipeWallRoughness(new double[] {wallroughness, wallroughness});
    pipeline.setOuterTemperatures(new double[] {temperature + 273.15, temperature + 273.15});
    pipeline.setPipeOuterHeatTransferCoefficients(new double[] {15.0, 15.0});
    pipeline.setPipeWallHeatTransferCoefficients(new double[] {15.0, 15.0});

    AdiabaticPipe simplePipeline = new AdiabaticPipe("simplePipeline", simplePipelineFeed);
    simplePipeline.setDiameter(diameter);
    simplePipeline.setLength(length);
    simplePipeline.setPipeWallRoughness(wallroughness);
    simplePipeline.setInletElevation(0);
    simplePipeline.setOutletElevation(elevation);

    PipeBeggsAndBrills beggsBrilsPipe = new PipeBeggsAndBrills("simplePipeline 2", beggsBrillsFeed);
    beggsBrilsPipe.setPipeWallRoughness(wallroughness);
    beggsBrilsPipe.setLength(length);
    beggsBrilsPipe.setElevation(elevation);
    beggsBrilsPipe.setDiameter(diameter);
    beggsBrilsPipe.setRunIsothermal(false);

    ProcessSystem operations = new ProcessSystem();
    operations.add(pipelineFeed);
    operations.add(simplePipelineFeed);
    operations.add(beggsBrillsFeed);
    operations.add(pipeline);
    operations.add(simplePipeline);
    operations.add(beggsBrilsPipe);
    operations.run();

    Assertions.assertEquals(123.876927, pipeline.getOutletPressure("bara"), 0.1);
    Assertions.assertEquals(120.711887695240, simplePipeline.getOutletPressure(), 0.1);
    Assertions.assertEquals(128.376, beggsBrilsPipe.getOutletPressure(), 0.2);
  }
}
