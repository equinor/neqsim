package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.ControllerDeviceInterface.ControllerMode;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.thermo.system.SystemSrkEos;

/** Regression coverage for the physical bounds and timestep semantics of the dynamic cooler. */
class CoolerTransientBoundsRegressionTest extends neqsim.NeqSimTest {
  private Cooler createCooler() {
    SystemSrkEos fluid = new SystemSrkEos(353.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();
    Cooler cooler = new Cooler("dynamic cooler", feed);
    cooler.configureDynamicTemperatureControl(10000.0, 80.0, 35.0, 20.0, "C");
    cooler.setDynamicTimeConstants(5.0, 10.0);
    cooler.run();
    cooler.setCalculateSteadyState(false);
    return cooler;
  }

  @Test
  void repeatedEvaluationDoesNotAdvanceThermalStateTwice() {
    Cooler cooler = createCooler();
    cooler.getInletStream().setTemperature(100.0, "C");
    cooler.getInletStream().run();
    UUID step = UUID.randomUUID();
    cooler.runTransient(2.0, step);
    double temperature = cooler.getOutletStream().getTemperature("K");
    double opening = cooler.getCoolingValveOpening();

    cooler.runTransient(2.0, step);

    assertEquals(temperature, cooler.getOutletStream().getTemperature("K"), 1.0e-10,
        "Repeated evaluation of one physical timestep must reuse its initial thermal state");
    assertEquals(opening, cooler.getCoolingValveOpening(), 1.0e-12);
    assertEquals(2.0, cooler.getTime(), 0.0);
    cooler.runTransient(2.0, UUID.randomUUID());
    assertTrue(cooler.getOutletStream().getTemperature("K") > temperature);
    assertEquals(4.0, cooler.getTime(), 0.0);
  }

  @Test
  void repeatedEvaluationReusesActuatorStateAndRefreshesChangedInlet() {
    Cooler cooler = createCooler();
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass("utility controller");
    controller.setTransmitter(new TemperatureTransmitter("outlet temperature", cooler.getOutletStream()));
    controller.setMode(ControllerMode.MANUAL);
    controller.setManualOutput(100.0);
    cooler.setController(controller);
    UUID step = UUID.randomUUID();
    cooler.runTransient(2.0, step);
    double opening = 50.0 + 2.0 / 7.0 * 50.0;
    assertEquals(opening, cooler.getCoolingValveOpening(), 1.0e-12);

    cooler.getInletStream().setTemperature(100.0, "C");
    cooler.getInletStream().run();
    cooler.runTransient(2.0, step);

    double ntu = -Math.log(15.0 / 60.0) * opening / 50.0;
    double equilibriumTemperatureC = 20.0 + 80.0 * Math.exp(-ntu);
    double expectedTemperatureC = 35.0 + 2.0 / 12.0 * (equilibriumTemperatureC - 35.0);
    assertEquals(expectedTemperatureC, cooler.getOutletStream().getTemperature("C"), 1.0e-9,
        "The second pass must refresh inlet conditions using the same initial thermal state");
    assertEquals(opening, cooler.getCoolingValveOpening(), 1.0e-12);
    assertEquals(2.0, cooler.getTime(), 0.0);
  }

  @Test
  void colderThanUtilityFeedDoesNotReceiveArtificialHeating() {
    Cooler cooler = createCooler();
    cooler.setDynamicTimeConstants(0.0, 0.0);
    cooler.getInletStream().setTemperature(10.0, "C");
    cooler.getInletStream().run();

    cooler.runTransient(1.0, UUID.randomUUID());

    assertEquals(10.0, cooler.getOutletStream().getTemperature("C"), 1.0e-9,
        "A cooling-only model must bypass cooling below the utility temperature");
    assertEquals(0.0, cooler.getDuty(), 1.0e-5);
  }
}
