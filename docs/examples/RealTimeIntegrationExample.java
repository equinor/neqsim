package neqsim.process.util.example;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ControlValve;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.measurementdevice.FlowTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.scenario.ProcessScenarioRunner;
import neqsim.process.util.monitor.KPIDashboard;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Example showing integration patterns for real-time digitalization systems.
 * 
 * Integration Points:
 * - OPC UA/DA servers for real-time data exchange
 * - PI/Seeq historian connections for time-series data
 * - SCADA/DCS integration for control loops
 * - REST APIs for cloud/IoT platforms
 * - MQTT for industrial IoT messaging
 */
public class RealTimeIntegrationExample {

  /**
   * Digital Twin Pattern: Live data feedback integration
   */
  public static class DigitalTwinIntegration {
    
    private ProcessSystem processSystem;
    private ProcessScenarioRunner runner;
    private KPIDashboard kpiDashboard;
    
    // Integration interfaces
    private OPCClientInterface opcClient;
    private PIHistorianInterface piHistorian;
    private SCADAInterface scadaInterface;
    
    public DigitalTwinIntegration() {
      setupProcessSystem();
      setupIntegrationInterfaces();
    }
    
    private void setupProcessSystem() {
      processSystem = new ProcessSystem();
      
      // Create thermodynamic system
      SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
      fluid.addComponent("methane", 85.0);
      fluid.addComponent("ethane", 10.0);
      fluid.addComponent("propane", 5.0);
      fluid.setMixingRule(2);
      
      // Main process stream
      Stream feed = new Stream("Process Feed", fluid);
      feed.setFlowRate(15000.0, "kg/hr");
      feed.setPressure(50.0, "bara");
      feed.setTemperature(25.0, "C");
      
      // Control valve with live setpoint
      ControlValve controlValve = new ControlValve("PV-101", feed);
      controlValve.setPercentValveOpening(75.0);
      controlValve.setCv(400.0);
      
      // Separator with instrumentation
      Separator separator = new Separator("V-101", controlValve.getOutletStream());
      separator.setInternalDiameter(2.5);
      
      // Live measurement devices (connected to real plant)
      PressureTransmitter pressurePI = new PressureTransmitter("PI-101", separator.getGasOutStream());
      TemperatureTransmitter temperaturePI = new TemperatureTransmitter("TI-101", separator.getGasOutStream());
      FlowTransmitter flowPI = new FlowTransmitter("FI-101", separator.getGasOutStream());
      
      // Add to system
      processSystem.add(feed);
      processSystem.add(controlValve);
      processSystem.add(separator);
      
      // Setup scenario runner for real-time updates
      runner = new ProcessScenarioRunner(processSystem);
      kpiDashboard = new KPIDashboard();
    }
    
    private void setupIntegrationInterfaces() {
      // OPC UA Client for real-time data exchange
      opcClient = new OPCClientInterface("opc.tcp://localhost:4840");
      opcClient.subscribe("PLC.PI_101.Value", this::updatePressureReading);
      opcClient.subscribe("PLC.TI_101.Value", this::updateTemperatureReading);
      opcClient.subscribe("PLC.FI_101.Value", this::updateFlowReading);
      opcClient.subscribe("PLC.PV_101.Setpoint", this::updateValveSetpoint);
      
      // PI Historian for time-series data storage
      piHistorian = new PIHistorianInterface("PI_SERVER");
      piHistorian.configureTag("NEQSIM.V101.Pressure.Simulated", "PI-101");
      piHistorian.configureTag("NEQSIM.V101.Temperature.Simulated", "TI-101");
      piHistorian.configureTag("NEQSIM.V101.Flow.Simulated", "FI-101");
      
      // SCADA Interface for control integration
      scadaInterface = new SCADAInterface();
      scadaInterface.registerAlarm("HIGH_PRESSURE", 55.0, this::triggerHighPressureAlarm);
      scadaInterface.registerTrend("Process_Performance", kpiDashboard);
    }
    
    /**
     * Real-time data update methods (called by OPC subscriptions)
     */
    public void updatePressureReading(double value) {
      // Update simulation with live plant data
      PressureTransmitter pi = (PressureTransmitter) processSystem.getMeasurementDevice("PI-101");
      pi.setMeasuredValue(value);
      
      // Trigger model reconciliation if deviation is significant
      double simulatedValue = pi.getSimulatedValue();
      if (Math.abs(value - simulatedValue) > 2.0) { // 2 bar deviation
        reconcileModel("pressure", value, simulatedValue);
      }
      
      // Log to historian
      piHistorian.writeValue("NEQSIM.V101.Pressure.Actual", value);
      piHistorian.writeValue("NEQSIM.V101.Pressure.Simulated", simulatedValue);
    }
    
    public void updateTemperatureReading(double value) {
      TemperatureTransmitter ti = (TemperatureTransmitter) processSystem.getMeasurementDevice("TI-101");
      ti.setMeasuredValue(value);
      
      double simulatedValue = ti.getSimulatedValue();
      piHistorian.writeValue("NEQSIM.V101.Temperature.Actual", value);
      piHistorian.writeValue("NEQSIM.V101.Temperature.Simulated", simulatedValue);
    }
    
    public void updateFlowReading(double value) {
      FlowTransmitter fi = (FlowTransmitter) processSystem.getMeasurementDevice("FI-101");
      fi.setMeasuredValue(value);
      
      double simulatedValue = fi.getSimulatedValue();
      piHistorian.writeValue("NEQSIM.V101.Flow.Actual", value);
      piHistorian.writeValue("NEQSIM.V101.Flow.Simulated", simulatedValue);
    }
    
    public void updateValveSetpoint(double setpoint) {
      ControlValve valve = (ControlValve) processSystem.getUnit("PV-101");
      valve.setPercentValveOpening(setpoint);
      
      // Run simulation with new setpoint
      processSystem.run();
      
      // Send predicted values back to control system
      PressureTransmitter pi = (PressureTransmitter) processSystem.getMeasurementDevice("PI-101");
      opcClient.writeValue("PLC.PI_101.Predicted", pi.getSimulatedValue());
    }
    
    /**
     * Model reconciliation when plant data deviates from simulation
     */
    private void reconcileModel(String parameter, double actualValue, double simulatedValue) {
      System.out.println("Model reconciliation triggered for " + parameter);
      System.out.println("Actual: " + actualValue + ", Simulated: " + simulatedValue);
      
      // Adjust model parameters based on plant data
      // This could involve:
      // - Equipment fouling factors
      // - Heat transfer coefficients
      // - Pressure drop correlations
      // - Composition analysis updates
      
      if ("pressure".equals(parameter)) {
        // Adjust pressure drop correlations or valve Cv
        ControlValve valve = (ControlValve) processSystem.getUnit("PV-101");
        double currentCv = valve.getCv();
        double adjustmentFactor = actualValue / simulatedValue;
        valve.setCv(currentCv * adjustmentFactor);
        
        System.out.println("Adjusted valve Cv from " + currentCv + " to " + valve.getCv());
      }
      
      // Re-run simulation with adjusted parameters
      processSystem.run();
      
      // Log reconciliation event
      piHistorian.writeEvent("NEQSIM.ModelReconciliation", 
          "Parameter: " + parameter + ", Adjustment: " + (actualValue/simulatedValue));
    }
    
    private void triggerHighPressureAlarm(double value) {
      System.out.println("HIGH PRESSURE ALARM: " + value + " bara");
      // Could trigger safety logic in ProcessScenarioRunner
      // runner.activateLogic("ESD Level 1");
    }
    
    /**
     * Continuous operation loop - called periodically (e.g., every 5 seconds)
     */
    public void runContinuousSimulation() {
      while (true) {
        try {
          // Run process simulation
          processSystem.run();
          
          // Update KPI dashboard
          kpiDashboard.updateFromSystem(processSystem);
          
          // Send simulation results to SCADA/historians
          publishSimulationResults();
          
          // Check for alarms/warnings
          checkProcessAlarms();
          
          Thread.sleep(5000); // 5-second cycle
          
        } catch (Exception e) {
          System.err.println("Error in continuous simulation: " + e.getMessage());
          piHistorian.writeEvent("NEQSIM.Error", e.getMessage());
        }
      }
    }
    
    private void publishSimulationResults() {
      // Publish all simulated values to OPC server
      processSystem.getMeasurementDevices().forEach(device -> {
        String tagName = "PLC." + device.getName() + ".Predicted";
        opcClient.writeValue(tagName, device.getSimulatedValue());
      });
      
      // Update trends in SCADA
      scadaInterface.updateTrends(processSystem);
    }
    
    private void checkProcessAlarms() {
      processSystem.getMeasurementDevices().forEach(device -> {
        if (device instanceof PressureTransmitter) {
          PressureTransmitter pi = (PressureTransmitter) device;
          if (pi.getSimulatedValue() > 55.0) {
            scadaInterface.raiseAlarm("HIGH_PRESSURE", pi.getSimulatedValue());
          }
        }
      });
    }
  }
  
  /**
   * Interface classes for different integration systems
   * (These would be implemented with actual client libraries)
   */
  
  // OPC UA/DA Integration
  interface OPCClientInterface {
    void subscribe(String nodeId, java.util.function.Consumer<Double> callback);
    void writeValue(String nodeId, double value);
  }
  
  // PI/OSIsoft Historian Integration  
  interface PIHistorianInterface {
    void configureTag(String piTag, String deviceName);
    void writeValue(String piTag, double value);
    void writeEvent(String piTag, String message);
  }
  
  // SCADA/DCS Integration
  interface SCADAInterface {
    void registerAlarm(String alarmName, double threshold, java.util.function.Consumer<Double> handler);
    void registerTrend(String trendName, KPIDashboard dashboard);
    void updateTrends(ProcessSystem system);
    void raiseAlarm(String alarmName, double value);
  }
  
  // Mock implementations (replace with real client libraries)
  static class OPCClientInterface implements OPCClientInterface {
    private String endpoint;
    
    public OPCClientInterface(String endpoint) {
      this.endpoint = endpoint;
      System.out.println("Connected to OPC server: " + endpoint);
    }
    
    public void subscribe(String nodeId, java.util.function.Consumer<Double> callback) {
      System.out.println("Subscribed to OPC node: " + nodeId);
      // In real implementation, this would use Eclipse Milo or similar OPC client
    }
    
    public void writeValue(String nodeId, double value) {
      System.out.println("Writing to OPC node " + nodeId + ": " + value);
    }
  }
  
  static class PIHistorianInterface implements PIHistorianInterface {
    private String server;
    
    public PIHistorianInterface(String server) {
      this.server = server;
      System.out.println("Connected to PI server: " + server);
    }
    
    public void configureTag(String piTag, String deviceName) {
      System.out.println("Configured PI tag " + piTag + " for device " + deviceName);
    }
    
    public void writeValue(String piTag, double value) {
      System.out.println("Writing to PI tag " + piTag + ": " + value);
      // In real implementation, this would use PI SDK or PI Web API
    }
    
    public void writeEvent(String piTag, String message) {
      System.out.println("Writing PI event to " + piTag + ": " + message);
    }
  }
  
  static class SCADAInterface implements SCADAInterface {
    public void registerAlarm(String alarmName, double threshold, java.util.function.Consumer<Double> handler) {
      System.out.println("Registered alarm: " + alarmName + " (threshold: " + threshold + ")");
    }
    
    public void registerTrend(String trendName, KPIDashboard dashboard) {
      System.out.println("Registered trend: " + trendName);
    }
    
    public void updateTrends(ProcessSystem system) {
      // Update SCADA trend displays with current simulation data
    }
    
    public void raiseAlarm(String alarmName, double value) {
      System.out.println("SCADA ALARM: " + alarmName + " = " + value);
    }
  }
  
  /**
   * Main method demonstrating real-time integration
   */
  public static void main(String[] args) {
    System.out.println("=== NeqSim Real-Time Integration Example ===");
    
    DigitalTwinIntegration digitalTwin = new DigitalTwinIntegration();
    
    // Start continuous simulation in background
    new Thread(digitalTwin::runContinuousSimulation).start();
    
    // Simulate some live data updates
    simulateLiveDataUpdates(digitalTwin);
  }
  
  private static void simulateLiveDataUpdates(DigitalTwinIntegration digitalTwin) {
    try {
      Thread.sleep(2000);
      
      // Simulate pressure increase from plant
      digitalTwin.updatePressureReading(52.5);
      
      Thread.sleep(3000);
      
      // Simulate temperature change
      digitalTwin.updateTemperatureReading(28.5);
      
      Thread.sleep(2000);
      
      // Simulate valve setpoint change from operator
      digitalTwin.updateValveSetpoint(65.0);
      
      Thread.sleep(5000);
      
      // Simulate high pressure condition
      digitalTwin.updatePressureReading(56.2); // Above alarm threshold
      
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}