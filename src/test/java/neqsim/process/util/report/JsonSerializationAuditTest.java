package neqsim.process.util.report;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.GasScrubber;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.SetPoint;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Audit test to identify equipment classes that return null from toJson().
 *
 * <p>
 * This test documents which equipment types need toJson() implementations for full JSON
 * serialization support.
 * </p>
 *
 * @author esol
 */
public class JsonSerializationAuditTest {
  /**
   * Audit which equipment types return null from toJson().
   */
  @Test
  void auditEquipmentJsonSupport() {
    SystemSrkEos testFluid = new SystemSrkEos(298.0, 10.0);
    testFluid.addComponent("methane", 80.0);
    testFluid.addComponent("ethane", 10.0);
    testFluid.addComponent("propane", 5.0);
    testFluid.addComponent("n-heptane", 5.0);
    testFluid.setMixingRule("classic");
    testFluid.setMultiPhaseCheck(true);

    List<String> missingToJson = new ArrayList<String>();
    List<String> hasToJson = new ArrayList<String>();

    // Create a comprehensive process to test various equipment
    ProcessSystem process = new ProcessSystem();

    Stream inlet = new Stream("feed", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");

    Separator separator = new Separator("separator", inlet);
    GasScrubber scrubber = new GasScrubber("scrubber", separator.getGasOutStream());

    Compressor compressor = new Compressor("compressor", scrubber.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");

    ThrottlingValve valve = new ThrottlingValve("valve", separator.getLiquidOutStream());
    valve.setOutletPressure(10.0, "bara");

    Heater heater = new Heater("heater", compressor.getOutStream());
    heater.setOutTemperature(50.0, "C");

    Mixer mixer = new Mixer("mixer");
    mixer.addStream(heater.getOutletStream());

    Splitter splitter = new Splitter("splitter", valve.getOutletStream(), 2);
    splitter.setSplitFactors(new double[] {0.5, 0.5});

    Tank tank = new Tank("tank", splitter.getSplitStream(0));

    Pump pump = new Pump("pump", tank.getLiquidOutStream());
    pump.setOutletPressure(20.0, "bara");

    // Utility equipment
    Adjuster adjuster = new Adjuster("adjuster");
    adjuster.setAdjustedVariable(valve, "pressure");
    adjuster.setTargetVariable(separator, "pressure", 49.0, "bara");

    SetPoint setPoint = new SetPoint("setpoint", heater, "outTemperature", inlet);

    process.add(inlet);
    process.add(separator);
    process.add(scrubber);
    process.add(compressor);
    process.add(valve);
    process.add(heater);
    process.add(mixer);
    process.add(splitter);
    process.add(tank);
    process.add(pump);
    process.add(adjuster);
    process.add(setPoint);

    process.run();

    // Audit each equipment
    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      String className = equipment.getClass().getSimpleName();
      String json = equipment.toJson();
      if (json == null) {
        missingToJson.add(className + " (" + equipment.getName() + ")");
      } else {
        hasToJson.add(className + " (" + equipment.getName() + ")");
      }
    }

    // Print audit results
    System.out.println("\n========================================");
    System.out.println("JSON SERIALIZATION AUDIT RESULTS");
    System.out.println("========================================\n");

    System.out.println("Equipment with toJson() implementation (" + hasToJson.size() + "):");
    for (String name : hasToJson) {
      System.out.println("  ✓ " + name);
    }

    System.out.println("\nEquipment returning null from toJson() (" + missingToJson.size() + "):");
    for (String name : missingToJson) {
      System.out.println("  ✗ " + name);
    }

    // List all known equipment classes that need toJson()
    System.out.println("\n========================================");
    System.out.println("EQUIPMENT CLASSES NEEDING toJson() IMPLEMENTATION:");
    System.out.println("========================================");
    String[] missingClasses = {
        // Adsorber
        "SimpleAdsorber",
        // Absorber
        "SimpleAbsorber",
        // Battery
        "BatteryStorage",
        // Diff pressure
        "Orifice",
        // Ejector
        "Ejector",
        // Electrolyzer
        "CO2Electrolyzer", "Electrolyzer",
        // Expander
        "ExpanderOld",
        // Filter
        "Filter",
        // Flare
        "Flare", "FlareStack",
        // Heat exchanger
        "ReBoiler",
        // Membrane
        "MembraneSeparator",
        // Pipeline
        "Pipeline", "TransientPipe",
        // Power generation
        "FuelCell", "GasTurbine", "SolarPanel", "WindTurbine",
        // Reactor
        "GibbsReactor", "GibbsReactorCO2",
        // Reservoir
        "ReservoirCVDsim", "ReservoirDiffLibsim", "ReservoirTPsim", "SimpleReservoir",
        "TubingPerformance", "WellFlow", "WellSystem",
        // Separator subclasses
        "GasScrubber", "GasScrubberSimple", "Hydrocyclone", "NeqGasScrubber", "TwoPhaseSeparator",
        // Stream
        "VirtualStream",
        // Subsea
        "SimpleFlowLine", "SubseaWell",
        // Tank
        "VesselDepressurization",
        // Util
        "Adjuster", "Calculator", "FlowRateAdjuster", "FlowSetter", "GORfitter",
        "MoleFractionControllerUtil", "MPFMfitter", "NeqSimUnit", "SetPoint", "Setter",
        "StreamSaturatorUtil", "StreamTransition"};

    for (String cls : missingClasses) {
      System.out.println("  - " + cls);
    }

    System.out
        .println("\nTotal: " + missingClasses.length + " classes need toJson() implementations");
    System.out.println("\n========================================");
  }
}
