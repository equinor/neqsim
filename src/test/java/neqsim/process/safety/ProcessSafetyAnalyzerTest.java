package neqsim.process.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.ProcessSafetyAnalysisSummary.UnitKpiSnapshot;
import neqsim.thermo.system.SystemInterface;

/** Tests for {@link ProcessSafetyAnalyzer}. */
public class ProcessSafetyAnalyzerTest extends NeqSimTest {
  @Test
  public void analyzeScenarioPersistsSummaryAndCapturesKpis() {
    ProcessSystem base = new ProcessSystem("base");
    DummyUnit pump = new DummyUnit("pump1");
    pump.setMassBalance(42.0);
    pump.setPressure(12.5);
    pump.setTemperature(320.0);
    pump.setController(new ControllerDeviceBaseClass("controller"));
    base.add(pump);

    InMemoryResultRepository repository = new InMemoryResultRepository();
    ProcessSafetyAnalyzer analyzer = new ProcessSafetyAnalyzer(base, repository);

    ProcessSafetyScenario scenario = ProcessSafetyScenario.builder("Blocked pump")
        .blockOutlet("pump1").controllerSetPoint("pump1", 5.0).build();

    ProcessSafetyAnalysisSummary summary = analyzer.analyze(scenario);

    assertEquals("Blocked pump", summary.getScenarioName());
    assertEquals(1, repository.findAll().size());
    assertEquals(summary, repository.findAll().get(0));

    assertTrue(summary.getConditionMessages().containsKey("pump1"));
    assertFalse(summary.getConditionMessages().get("pump1").isEmpty());

    UnitKpiSnapshot kpi = summary.getUnitKpis().get("pump1");
    assertNotNull(kpi);
    assertEquals(42.0, kpi.getMassBalance(), 1e-6);
    assertEquals(12.5, kpi.getPressure(), 1e-6);
    assertEquals(320.0, kpi.getTemperature(), 1e-6);
  }

  @Test
  public void analyzeMultipleScenariosReturnsSummaries() {
    ProcessSystem base = new ProcessSystem("base");
    DummyUnit cooler = new DummyUnit("cooler1");
    cooler.setMassBalance(10.0);
    cooler.setPressure(5.0);
    cooler.setTemperature(280.0);
    cooler.setController(new ControllerDeviceBaseClass("controller"));
    base.add(cooler);

    ProcessSafetyAnalyzer analyzer = new ProcessSafetyAnalyzer(base);

    List<ProcessSafetyScenario> scenarios = new ArrayList<>();
    scenarios.add(ProcessSafetyScenario.builder("Utility loss").utilityLoss("cooler1").build());
    scenarios.add(ProcessSafetyScenario.builder("Controller change")
        .controllerSetPoint("cooler1", 15.0).build());

    List<ProcessSafetyAnalysisSummary> summaries = analyzer.analyze(scenarios);

    assertEquals(2, summaries.size());
    assertEquals("Utility loss", summaries.get(0).getScenarioName());
    assertEquals("Controller change", summaries.get(1).getScenarioName());
  }

  private static final class InMemoryResultRepository implements ProcessSafetyResultRepository {
    private final List<ProcessSafetyAnalysisSummary> summaries = new ArrayList<>();

    @Override
    public void save(ProcessSafetyAnalysisSummary summary) {
      summaries.add(summary);
    }

    @Override
    public List<ProcessSafetyAnalysisSummary> findAll() {
      return new ArrayList<>(summaries);
    }
  }

  private static final class DummyUnit extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1L;

    private double massBalance;
    private double pressure;
    private double temperature;
    private double regulatorSignal;
    private ControllerDeviceInterface controller;

    private DummyUnit(String name) {
      super(name);
    }

    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    @Override
    public double getMassBalance(String unit) {
      return massBalance;
    }

    @Override
    public double getPressure() {
      return pressure;
    }

    @Override
    public double getPressure(String unit) {
      return pressure;
    }

    @Override
    public void setPressure(double pressure) {
      this.pressure = pressure;
    }

    @Override
    public double getTemperature() {
      return temperature;
    }

    @Override
    public double getTemperature(String unit) {
      return temperature;
    }

    @Override
    public void setTemperature(double temperature) {
      this.temperature = temperature;
    }

    @Override
    public void setRegulatorOutSignal(double signal) {
      this.regulatorSignal = signal;
    }

    @Override
    public void runConditionAnalysis(ProcessEquipmentInterface refExchanger) {
      conditionAnalysisMessage = "Condition analysis for " + getName();
    }

    @Override
    public String getConditionAnalysisMessage() {
      return conditionAnalysisMessage;
    }

    @Override
    public SystemInterface getThermoSystem() {
      return null;
    }

    @Override
    public ControllerDeviceInterface getController() {
      return controller;
    }

    @Override
    public void setController(ControllerDeviceInterface controller) {
      this.controller = controller;
    }

    void setMassBalance(double massBalance) {
      this.massBalance = massBalance;
    }

    double getRegulatorSignal() {
      return regulatorSignal;
    }
  }
}
