package neqsim.process.processmodel;

import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Dynamic separator flowsheet with inlet level control, gas-outlet pressure control and liquid-outlet flow control,
 * taken from GitHub discussion equinor/neqsim#1574 (oil with TBP fractions, 1 m x 5 m horizontal vessel, Cv 120 inlet
 * valve). With a 100 s step the inlet valve passes about 32 times the gas-cap inventory per step, which is the stiff
 * case the automatic transient sub-stepping exists for.
 */
final class SeparatorControlFlowsheet {
  final ProcessSystem process;
  final Separator separator;
  final ThrottlingValve inletValve;
  final ThrottlingValve liquidValve;
  final ThrottlingValve gasValve;
  final ControllerDeviceBaseClass levelController;
  final ControllerDeviceBaseClass pressureController;
  final ControllerDeviceBaseClass flowController;

  SeparatorControlFlowsheet() {
    SystemInterface fluid = new SystemPrEos(298.15, 1.01325);
    fluid.addComponent("CO2", 1.5870);
    fluid.addComponent("methane", 52.51);
    fluid.addComponent("ethane", 6.24);
    fluid.addComponent("propane", 4.23);
    fluid.addComponent("i-butane", 0.855);
    fluid.addComponent("n-butane", 2.213);
    fluid.addComponent("i-pentane", 1.124);
    fluid.addComponent("n-pentane", 1.271);
    fluid.addComponent("n-hexane", 2.289);
    fluid.addTBPfraction("C7+_cut1", 0.8501, 108.47 / 1000.0, 0.7411);
    fluid.addTBPfraction("C7+_cut2", 1.2802, 120.4 / 1000.0, 0.755);
    fluid.addTBPfraction("C7+_cut3", 1.6603, 133.64 / 1000.0, 0.7695);
    fluid.addTBPfraction("C7+_cut4", 6.5311, 164.70 / 1000.0, 0.799);
    fluid.addTBPfraction("C7+_cut5", 6.3311, 215.94 / 1000.0, 0.8387);
    fluid.addTBPfraction("C7+_cut6", 4.9618, 273.34 / 1000.0, 0.8754);
    fluid.addTBPfraction("C7+_cut7", 2.9105, 334.92 / 1000.0, 0.90731);
    fluid.addTBPfraction("C7+_cut8", 3.0505, 412.79 / 1000.0, 0.9575);
    fluid.setPressure(9.0);
    fluid.setTemperature(300.0);
    fluid.setMixingRule("classic");

    Stream well = new Stream("well stream", fluid);
    well.setFlowRate(150.0, "kg/hr");

    inletValve = new ThrottlingValve("LCV-00", well);
    inletValve.setPercentValveOpening(80.0);
    inletValve.setOutletPressure(8.0);
    inletValve.setCalculateSteadyState(false);
    inletValve.setMinimumValveOpening(1.0);
    inletValve.setMaximumValveOpening(85.0);
    inletValve.setCv(120.0);

    separator = new Separator("V-001", inletValve.getOutletStream());
    separator.setCalculateSteadyState(false);
    separator.setOrientation("horizontal");
    separator.setSeparatorLength(5.0);
    separator.setInternalDiameter(1.0);
    separator.setLiquidLevel(0.4);

    liquidValve = new ThrottlingValve("LCV-001", separator.getLiquidOutStream());
    liquidValve.setPercentValveOpening(25.0);
    liquidValve.setOutletPressure(2.0);
    liquidValve.setCalculateSteadyState(false);
    liquidValve.setMinimumValveOpening(1.0);
    liquidValve.setMaximumValveOpening(85.0);
    liquidValve.setCv(1.0);

    gasValve = new ThrottlingValve("PCV-001", separator.getGasOutStream());
    gasValve.setPercentValveOpening(5.0);
    gasValve.setOutletPressure(2.0);
    gasValve.setCalculateSteadyState(false);
    gasValve.setMinimumValveOpening(0.01);
    gasValve.setCv(80.0);

    LevelTransmitter levelTransmitter = new LevelTransmitter(separator);
    levelTransmitter.setMaximumValue(0.99);
    levelTransmitter.setMinimumValue(0.01);
    PressureTransmitter pressureTransmitter = new PressureTransmitter(separator.getGasOutStream());
    pressureTransmitter.setUnit("bar");
    pressureTransmitter.setMaximumValue(50.0);
    pressureTransmitter.setMinimumValue(1.0);
    VolumeFlowTransmitter flowTransmitter = new VolumeFlowTransmitter(liquidValve.getOutletStream());
    flowTransmitter.setUnit("kg/hr");
    flowTransmitter.setMinimumValue(1.0);
    flowTransmitter.setMaximumValue(500.0);

    levelController = new ControllerDeviceBaseClass();
    levelController.setTransmitter(levelTransmitter);
    levelController.setReverseActing(true);
    levelController.setControllerSetPoint(0.5);
    levelController.setControllerParameters(20.0, 600.0, 0.0);
    levelController.setOutputLimits(0.0, 100.0);

    pressureController = new ControllerDeviceBaseClass();
    pressureController.setTransmitter(pressureTransmitter);
    pressureController.setReverseActing(false);
    pressureController.setControllerSetPoint(5.0);
    pressureController.setControllerParameters(15.0, 350.0, 0.0);
    pressureController.setOutputLimits(0.0, 100.0);

    flowController = new ControllerDeviceBaseClass();
    flowController.setTransmitter(flowTransmitter);
    flowController.setReverseActing(true);
    flowController.setControllerSetPoint(100.0);
    flowController.setControllerParameters(0.17, 220.0, 0.0);

    process = new ProcessSystem("oil process");
    process.add(well);
    process.add(inletValve);
    process.add(separator);
    process.add(liquidValve);
    process.add(gasValve);
    process.add(levelTransmitter);
    inletValve.setController(levelController);
    gasValve.setController(pressureController);
    liquidValve.setController(flowController);
    process.run();
  }

  double pressureBara() {
    return separator.getThermoSystem().getPressure("bara");
  }
}
