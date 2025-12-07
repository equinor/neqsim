package neqsim.process.controllerdevice;

import java.util.UUID;
import neqsim.process.controllerdevice.structure.CascadeControllerStructure;
import neqsim.process.controllerdevice.structure.FeedForwardControllerStructure;
import neqsim.process.controllerdevice.structure.RatioControllerStructure;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Example mirroring the process-control integration test. It shows how to wire
 * transmitters, PID controllers and controller structures to a simple process.
 */
public class ProcessControlExample {

  /** Transmitter returning the current valve opening. */
  static class ValvePositionTransmitter extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1L;
    private final ThrottlingValve valve;

    ValvePositionTransmitter(String name, ThrottlingValve valve) {
      super(name, "%");
      this.valve = valve;
    }

    @Override
    public double getMeasuredValue() {
      return valve.getPercentValveOpening();
    }

    @Override
    public double getMeasuredValue(String unit) {
      return getMeasuredValue();
    }
  }

  private ProcessControlExample() {}

  public static void main(String[] args) {
    // Create a simple gas process: feed stream -> valve -> separator
    SystemInterface gas = new neqsim.thermo.system.SystemSrkEos(298.15, 10.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.createDatabase(true);
    gas.setMixingRule(2);

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setPressure(10.0, "bara");

    ThrottlingValve valve = new ThrottlingValve("valve", feed);
    valve.setOutletPressure(5.0);
    valve.setCalculateSteadyState(false);

    Separator sep = new Separator("sep");
    sep.addStream(valve.getOutletStream());
    sep.setCalculateSteadyState(false);

    // Measurement device with noise and delay to mimic a realistic transmitter
    VolumeFlowTransmitter flowMeas = new VolumeFlowTransmitter(sep.getGasOutStream());
    flowMeas.setUnit("kg/hr");
    flowMeas.setNoiseStdDev(1.0);
    flowMeas.setDelaySteps(1);
    flowMeas.setRandomSeed(0);
    flowMeas.setMaximumValue(200.0);
    flowMeas.setMinimumValue(0.0);

    // PID controller acting on valve opening
    ControllerDeviceBaseClass flowController = new ControllerDeviceBaseClass("flow");
    flowController.setTransmitter(flowMeas);
    flowController.setControllerSetPoint(120.0, "kg/hr");
    flowController.setOutputLimits(0.0, 100.0);
    flowController.setDerivativeFilterTime(1.0);
    flowController.autoTuneStepResponse(1.0, 10.0, 2.0);
    flowController.addGainSchedulePoint(80.0, flowController.getKp(), flowController.getTi(),
        flowController.getTd());
    flowController.addGainSchedulePoint(120.0, flowController.getKp() * 0.5, flowController.getTi(),
        flowController.getTd());
    flowController.resetEventLog();
    flowController.resetPerformanceMetrics();

    valve.setController(flowController);

    ProcessSystem sys = new ProcessSystem();
    sys.add(feed);
    sys.add(valve);
    sys.add(sep);
    sys.add(flowMeas);
    sys.run();

    // Run controller iterations with noise and delay in measurements
    for (int i = 0; i < 20; i++) {
      flowController.runTransient(flowController.getResponse(), 1.0, UUID.randomUUID());
    }

    System.out.println("Flow controller response after iterations: " + flowController.getResponse());
    System.out.println("Events captured: " + flowController.getEventLog().size());

    // Demonstrate cascade, ratio and feed-forward structures
    ControllerDeviceBaseClass secondary = new ControllerDeviceBaseClass("position");
    MeasurementDeviceInterface posTrans = new ValvePositionTransmitter("pos", valve);
    secondary.setTransmitter(posTrans);
    secondary.setControllerParameters(1.0, 1.0, 0.0);
    CascadeControllerStructure cascade = new CascadeControllerStructure(flowController, secondary);
    cascade.runTransient(1.0);

    VolumeFlowTransmitter feedMeas = new VolumeFlowTransmitter(feed);
    feedMeas.setUnit("kg/hr");
    RatioControllerStructure ratio = new RatioControllerStructure(flowController, feedMeas);
    ratio.setRatio(0.8);
    ratio.runTransient(1.0);

    FeedForwardControllerStructure ff = new FeedForwardControllerStructure(flowController, feedMeas);
    ff.setFeedForwardGain(0.1);
    ff.runTransient(1.0);

    System.out.println("Cascade output: " + cascade.getOutput());
    System.out.println("Ratio output: " + ratio.getOutput());
    System.out.println("Feed-forward output: " + ff.getOutput());
  }
}
