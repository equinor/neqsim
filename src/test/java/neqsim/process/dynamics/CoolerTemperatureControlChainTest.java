package neqsim.process.dynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.ControllerPerformanceMetrics;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.SensorFaultType;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** End-to-end qualification of sampled temperature measurement, PID and cooler thermal/actuator lags. */
class CoolerTemperatureControlChainTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(CoolerTemperatureControlChainTest.class);

  /** A feed-temperature disturbance produces bounded control action with one shared start-of-step sample. */
  @Test
  void feedTemperatureDisturbanceProducesBoundedSampledControlResponse() {
    TemperatureControlChain chain = createChain("disturbance", 5.0);
    chain.feed.setTemperature(100.0, "C");
    chain.feed.run();

    Response response = run(chain, 1.0, 60, "disturbance", true);
    ControllerPerformanceMetrics metrics = chain.controller.getPerformanceMetrics();

    logger.info(
        "temperature-chain metrics final={} C min={} C max={} C valve={} percent IAE={} C-s peakError={} C travel={} pp settling={} s",
        response.finalTemperature, response.minimumTemperature, response.maximumTemperature,
        chain.cooler.getCoolingValveOpening(), metrics.getIntegralAbsoluteError(), metrics.getPeakAbsoluteError(),
        metrics.getControllerOutputTravel(), metrics.getSettlingTime());
    assertEquals(60, metrics.getSampleCount());
    assertTrue(response.maximumTemperature <= 100.0, "cooler outlet must not exceed the disturbed inlet");
    assertTrue(response.minimumTemperature >= 20.0, "cooler outlet must not cross the cooling-medium temperature");
    assertTrue(response.finalTemperature < 50.0, "sampled PID must reject most of the 20 C feed disturbance in 60 s");
    assertTrue(chain.cooler.getCoolingValveOpening() > 50.0,
        "direct-acting temperature control must increase cooling-utility opening");
    assertTrue(metrics.getIntegralAbsoluteError() > 0.0);
    assertTrue(metrics.getPeakAbsoluteError() > 0.0);
    assertTrue(metrics.getControllerOutputTravel() > 0.0);
    assertTrue(response.maximumTemperature <= 38.3, "60 s peak outlet temperature must remain below 38.3 C");
    assertTrue(response.finalTemperature <= 37.6, "60 s final outlet temperature must remain below 37.6 C");
    assertTrue(chain.cooler.getCoolingValveOpening() <= 55.4,
        "60 s utility-valve opening must remain below 55.4 percent");
    assertTrue(metrics.getIntegralAbsoluteError() <= 161.0, "60 s temperature-control IAE must remain below 161 C-s");
    assertTrue(metrics.getPeakAbsoluteError() <= 3.3, "held-sample peak error must remain below 3.3 C");
    assertTrue(metrics.getControllerOutputTravel() <= 5.6,
        "controller-output travel must remain below 5.6 percentage points");
    assertTrue(metrics.getSettlingTime() <= 59.0,
        "temperature must enter and remain in the default two-percent band by 59 s");
  }

  /** Halving the physical scan interval keeps the ten-second temperature and actuator response close. */
  @Test
  void timestepRefinementKeepsResponseWithinDeclaredSensitivity() {
    TemperatureControlChain coarse = createChain("coarse", 5.0);
    TemperatureControlChain refined = createChain("refined", 5.0);
    coarse.feed.setTemperature(100.0, "C");
    refined.feed.setTemperature(100.0, "C");
    coarse.feed.run();
    refined.feed.run();

    Response coarseResponse = run(coarse, 1.0, 10, "coarse", true);
    Response refinedResponse = run(refined, 0.5, 20, "refined", true);

    logger.info("temperature-chain refinement temperature coarse={} refined={} C opening coarse={} refined={} percent",
        coarseResponse.finalTemperature, refinedResponse.finalTemperature, coarse.cooler.getCoolingValveOpening(),
        refined.cooler.getCoolingValveOpening());
    assertEquals(coarseResponse.finalTemperature, refinedResponse.finalTemperature, 0.75,
        "halving dt must change the ten-second outlet temperature by no more than 0.75 C");
    assertEquals(coarse.cooler.getCoolingValveOpening(), refined.cooler.getCoolingValveOpening(), 1.0,
        "halving dt must change the ten-second utility opening by no more than one percentage point");
  }

  /** A stuck transmitter can hide a rising process temperature and is therefore not a qualified protection layer. */
  @Test
  void stuckTemperatureSignalIsVisibleAsAnUnresolvedSafetyLimitation() {
    TemperatureControlChain chain = createChain("fault", 5.0);
    chain.transmitter.setFault(SensorFaultType.STUCK_AT_VALUE, 35.0);
    chain.feed.setTemperature(120.0, "C");
    chain.feed.run();

    Response response = run(chain, 1.0, 20, "fault", false);

    assertEquals(35.0, chain.transmitter.getTransientSample("C"), 0.0);
    assertTrue(response.finalTemperature - chain.transmitter.getTransientSample("C") > 5.0,
        "a stuck-at-setpoint TT must mask more than 5 C of the real thermal excursion; actual outlet="
            + response.finalTemperature + " C");
    assertTrue(response.maximumTemperature <= 120.0);
    assertTrue(response.minimumTemperature >= 20.0);
  }

  private static Response run(TemperatureControlChain chain, double dt, int count, String scope,
      boolean verifyRawSample) {
    double minimumTemperature = Double.POSITIVE_INFINITY;
    double maximumTemperature = Double.NEGATIVE_INFINITY;
    for (int index = 0; index < count; index++) {
      double temperatureAtScan = chain.cooler.getOutletStream().getTemperature("C");
      UUID id = TransientStepIdentifier.deterministicPhysicalStep("cooler-temperature-chain-" + scope, index);
      chain.process.runTransient(dt, id);
      double sample = chain.transmitter.getTransientSample("C");
      if (verifyRawSample) {
        assertEquals(temperatureAtScan, sample, 1.0e-9,
            "the physical TT scan must hold the start-of-step cooler outlet temperature");
      }
      assertEquals(sample,
          chain.controller.getEventLog().get(chain.controller.getEventLog().size() - 1).getMeasuredValue(), 0.0,
          "the controller must consume the held temperature sample");
      String[][] history = chain.process.getHistorySnapshot();
      assertEquals(sample, Double.parseDouble(history[history.length - 1][2]), 0.0,
          "measurement history must reuse the held temperature sample");
      double outletTemperature = chain.cooler.getOutletStream().getTemperature("C");
      minimumTemperature = Math.min(minimumTemperature, outletTemperature);
      maximumTemperature = Math.max(maximumTemperature, outletTemperature);
      assertTrue(chain.cooler.getCoolingValveOpening() >= 0.0 && chain.cooler.getCoolingValveOpening() <= 100.0);
    }
    return new Response(chain.cooler.getOutletStream().getTemperature("C"), minimumTemperature, maximumTemperature);
  }

  private static TemperatureControlChain createChain(String prefix, double actuatorTimeConstant) {
    SystemSrkEos fluid = new SystemSrkEos(353.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream(prefix + " feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(80.0, "C");
    feed.setPressure(50.0, "bara");

    Cooler cooler = new Cooler(prefix + " cooler", feed);
    cooler.setOutTemperature(35.0, "C");
    ProcessSystem process = new ProcessSystem(prefix + " temperature-control process");
    process.add(feed);
    process.add(cooler);
    process.run();
    cooler.configureDynamicTemperatureControl(10000.0, 80.0, 35.0, 20.0, "C");
    cooler.setDynamicTimeConstants(actuatorTimeConstant, 10.0);
    cooler.setCalculateSteadyState(false);

    TemperatureTransmitter transmitter = new TemperatureTransmitter(prefix + " TT", cooler.getOutletStream());
    transmitter.setUnit("C");
    transmitter.setMinimumValue(20.0);
    transmitter.setMaximumValue(120.0);
    transmitter.setTransientSamplingEnabled(true);
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass(prefix + " TIC");
    controller.setTransmitter(transmitter);
    controller.setControllerSetPoint(35.0, "C");
    controller.setControllerParameters(4.0, 30.0, 0.0);
    controller.setOutputLimits(0.0, 100.0);
    cooler.setController(controller);
    process.add(transmitter);
    return new TemperatureControlChain(process, feed, cooler, transmitter, controller);
  }

  private static final class Response {
    private final double finalTemperature;
    private final double minimumTemperature;
    private final double maximumTemperature;

    private Response(double finalTemperature, double minimumTemperature, double maximumTemperature) {
      this.finalTemperature = finalTemperature;
      this.minimumTemperature = minimumTemperature;
      this.maximumTemperature = maximumTemperature;
    }
  }

  private static final class TemperatureControlChain {
    private final ProcessSystem process;
    private final Stream feed;
    private final Cooler cooler;
    private final TemperatureTransmitter transmitter;
    private final ControllerDeviceBaseClass controller;

    private TemperatureControlChain(ProcessSystem process, Stream feed, Cooler cooler,
        TemperatureTransmitter transmitter, ControllerDeviceBaseClass controller) {
      this.process = process;
      this.feed = feed;
      this.cooler = cooler;
      this.transmitter = transmitter;
      this.controller = controller;
    }
  }
}
