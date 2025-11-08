package neqsim.process.logic.startup;package neqsim.process.logic.startup;



import static org.junit.jupiter.api.Assertions.assertFalse;import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertTrue;import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeEach;import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;import org.junit.jupiter.api.BeforeEach;

import neqsim.process.equipment.stream.Stream;import org.junit.jupiter.api.Test;

import neqsim.process.logic.LogicAction;import neqsim.process.equipment.separator.Separator;

import neqsim.process.logic.condition.PressureCondition;import neqsim.process.equipment.stream.Stream;

import neqsim.process.logic.condition.TemperatureCondition;import neqsim.process.logic.LogicAction;

import neqsim.process.logic.condition.TimerCondition;import neqsim.process.logic.LogicState;

import neqsim.thermo.system.SystemInterface;import neqsim.process.logic.condition.PressureCondition;

import neqsim.thermo.system.SystemSrkEos;import neqsim.process.logic.condition.TemperatureCondition;

import neqsim.process.logic.condition.TimerCondition;

/**import neqsim.thermo.system.SystemInterface;

 * Test class for StartupLogic functionality.import neqsim.thermo.system.SystemSrkEos;

 * 

 * <p>/**

 * Tests cover: * Test class for StartupLogic functionality.

 * <ul> * 

 * <li>Permissive checking and waiting</li> * <p>

 * <li>Sequential action execution after permissives met</li> * Tests cover:

 * <li>Timeout handling when permissives not met</li> * <ul>

 * <li>Automatic abort if permissives lost during startup</li> * <li>Permissive checking and waiting</li>

 * </ul> * <li>Sequential action execution after permissives met</li>

 * * <li>Timeout handling when permissives not met</li>

 * @author ESOL * <li>Automatic abort if permissives lost during startup</li>

 */ * <li>Status reporting and progress tracking</li>

class StartupLogicTest { * </ul>

 *

  private SystemInterface testSystem; * @author ESOL

  private Stream testStream; */

  private StartupLogic startupLogic;class StartupLogicTest {



  @BeforeEach  private SystemInterface testSystem;

  void setUp() {  private Stream testStream;

    testSystem = new SystemSrkEos(298.15, 10.0);  private Separator testSeparator;

    testSystem.addComponent("methane", 1.0);  private StartupLogic startupLogic;

    testSystem.setMixingRule("classic");  private TimerCondition timerCondition;

    testSystem.createDatabase(true);

    testSystem.init(0);  @BeforeEach

  void setUp() {

    testStream = new Stream("Test Stream", testSystem);    // Create test system

    testStream.setFlowRate(100.0, "kg/hr");    testSystem = new SystemSrkEos(298.15, 10.0);

    testStream.setTemperature(25.0, "C");    testSystem.addComponent("methane", 1.0);

    testStream.setPressure(10.0, "bara");    testSystem.setMixingRule("classic");

    testStream.run();    testSystem.createDatabase(true);

    testSystem.init(0);

    startupLogic = new StartupLogic("Test Startup");

  }    // Create test stream

    testStream = new Stream("Test Stream", testSystem);

  @Test    testStream.setFlowRate(100.0, "kg/hr");

  void testInitialState() {    testStream.setTemperature(25.0, "C");

    assertFalse(startupLogic.isActive());    testStream.setPressure(10.0, "bara");

    assertFalse(startupLogic.isComplete());    testStream.run();

  }

    // Create test separator

  @Test    testSeparator = new Separator("Test Separator", testStream);

  void testActivation() {    testSeparator.run();

    startupLogic.activate();

    assertTrue(startupLogic.isActive());    // Create startup logic

  }    startupLogic = new StartupLogic("Test Startup");

  }

  @Test

  void testPermissivesAlreadyMet() {  @Test

    PressureCondition pressureOk = new PressureCondition(testStream, 5.0, ">");  void testInitialState() {

    TemperatureCondition tempOk = new TemperatureCondition(testStream, 50.0, "<");    assertEquals(LogicState.IDLE, startupLogic.getState());

    assertFalse(startupLogic.isActive());

    startupLogic.addPermissive(pressureOk);    assertFalse(startupLogic.isComplete());

    startupLogic.addPermissive(tempOk);  }



    TestAction action1 = new TestAction("Action 1");  @Test

    startupLogic.addAction(action1, 0.0);  void testActivation() {

    startupLogic.activate();

    startupLogic.activate();    assertTrue(startupLogic.isActive());

    startupLogic.execute(0.1);    assertTrue(startupLogic.getState() == LogicState.WAITING_PERMISSIVES 

        || startupLogic.getState() == LogicState.RUNNING);

    assertTrue(action1.isExecuted());  }

  }

  @Test

  @Test  void testPermissivesAlreadyMet() {

  void testWaitingForPermissives() {    // Add permissives that are already satisfied

    TimerCondition timerCondition = new TimerCondition(2.0);    PressureCondition pressureOk = new PressureCondition(testStream, 5.0, ">");

    startupLogic.addPermissive(timerCondition);    TemperatureCondition tempOk = new TemperatureCondition(testStream, 50.0, "<");



    TestAction action1 = new TestAction("Action 1");    startupLogic.addPermissive(pressureOk);

    startupLogic.addAction(action1, 0.0);    startupLogic.addPermissive(tempOk);



    startupLogic.activate();    // Add a simple action

    timerCondition.start();    TestAction action1 = new TestAction("Action 1");

    startupLogic.addAction(action1, 0.0);

    // Execute for 1 second

    for (int i = 0; i < 10; i++) {    // Activate

      timerCondition.update(0.1);    startupLogic.activate();

      startupLogic.execute(0.1);

    }    // Execute one step

    startupLogic.execute(0.1);

    assertFalse(action1.isExecuted());

    // Should move to executing actions immediately

    // Execute for another 2 seconds    assertTrue(startupLogic.getState() == LogicState.RUNNING 

    for (int i = 0; i < 20; i++) {        || startupLogic.getState() == LogicState.COMPLETED);

      timerCondition.update(0.1);    assertTrue(action1.isExecuted());

      startupLogic.execute(0.1);  }

    }

  @Test

    assertTrue(action1.isExecuted());  void testWaitingForPermissives() {

  }    // Add timer permissive that requires waiting

    timerCondition = new TimerCondition(5.0); // 5 seconds

  @Test    startupLogic.addPermissive(timerCondition);

  void testSequentialActionExecution() {

    TestAction action1 = new TestAction("Action 1");    // Add action

    TestAction action2 = new TestAction("Action 2");    TestAction action1 = new TestAction("Action 1");

    TestAction action3 = new TestAction("Action 3");    startupLogic.addAction(action1, 0.0);



    startupLogic.addAction(action1, 0.0);    // Activate and start timer

    startupLogic.addAction(action2, 1.0);    startupLogic.activate();

    startupLogic.addAction(action3, 2.0);    timerCondition.start();



    startupLogic.activate();    // Execute for 2 seconds

    startupLogic.execute(0.0);    for (int i = 0; i < 20; i++) {

      timerCondition.update(0.1);

    assertTrue(action1.isExecuted());      startupLogic.execute(0.1);

    assertFalse(action2.isExecuted());    }

    assertFalse(action3.isExecuted());

    // Should still be waiting

    for (int i = 0; i < 15; i++) {    assertEquals(LogicState.WAITING_PERMISSIVES, startupLogic.getState());

      startupLogic.execute(0.1);    assertFalse(action1.isExecuted());

    }

    // Execute for another 4 seconds (total 6 seconds)

    assertTrue(action2.isExecuted());    for (int i = 0; i < 40; i++) {

      timerCondition.update(0.1);

    for (int i = 0; i < 25; i++) {      startupLogic.execute(0.1);

      startupLogic.execute(0.1);    }

    }

    // Now should be executing actions

    assertTrue(action3.isExecuted());    assertTrue(startupLogic.getState() == LogicState.RUNNING 

    assertTrue(startupLogic.isComplete());        || startupLogic.getState() == LogicState.COMPLETED);

  }    assertTrue(action1.isExecuted());

  }

  @Test

  void testReset() {  @Test

    TestAction action1 = new TestAction("Action 1");  void testPermissiveTimeout() {

    startupLogic.addAction(action1, 0.0);    // Add pressure permissive that won't be met

    PressureCondition highPressure = new PressureCondition(testStream, 100.0, ">");

    startupLogic.activate();    startupLogic.addPermissive(highPressure);

    startupLogic.execute(0.1);    startupLogic.setPermissiveTimeout(10.0); // 10 seconds timeout



    assertTrue(startupLogic.isComplete());    // Activate

    startupLogic.activate();

    startupLogic.reset();

    // Execute for 12 seconds

    assertFalse(startupLogic.isActive());    for (int i = 0; i < 120; i++) {

    assertFalse(startupLogic.isComplete());      startupLogic.execute(0.1);

  }    }



  /**    // Should be failed due to timeout

   * Simple test action for testing startup logic.    assertEquals(LogicState.FAILED, startupLogic.getState());

   */  }

  private static class TestAction implements LogicAction {

    private final String name;  @Test

    private boolean executed = false;  void testSequentialActionExecution() {

    // No permissives needed for this test

    public TestAction(String name) {    TestAction action1 = new TestAction("Action 1");

      this.name = name;    TestAction action2 = new TestAction("Action 2");

    }    TestAction action3 = new TestAction("Action 3");



    @Override    startupLogic.addAction(action1, 0.0);

    public void execute() {    startupLogic.addAction(action2, 2.0); // 2 seconds after previous

      executed = true;    startupLogic.addAction(action3, 3.0); // 3 seconds after previous

    }

    // Activate

    @Override    startupLogic.activate();

    public boolean isComplete() {

      return executed;    // Execute immediately - action 1 should execute

    }    startupLogic.execute(0.0);

    assertTrue(action1.isExecuted());

    @Override    assertFalse(action2.isExecuted());

    public String getDescription() {    assertFalse(action3.isExecuted());

      return name;

    }    // Execute for 2.5 seconds - action 2 should execute

    for (int i = 0; i < 25; i++) {

    @Override      startupLogic.execute(0.1);

    public String getTargetName() {    }

      return "TestTarget";    assertTrue(action2.isExecuted());

    }    assertFalse(action3.isExecuted());



    public boolean isExecuted() {    // Execute for another 3 seconds - action 3 should execute

      return executed;    for (int i = 0; i < 30; i++) {

    }      startupLogic.execute(0.1);

  }    }

}    assertTrue(action3.isExecuted());


    // Should be complete
    assertEquals(LogicState.COMPLETED, startupLogic.getState());
    assertTrue(startupLogic.isComplete());
  }

  @Test
  void testPermissiveLostDuringStartup() {
    // Add pressure permissive
    PressureCondition pressureOk = new PressureCondition(testStream, 5.0, ">");
    startupLogic.addPermissive(pressureOk);

    // Add actions
    TestAction action1 = new TestAction("Action 1");
    TestAction action2 = new TestAction("Action 2");
    startupLogic.addAction(action1, 0.0);
    startupLogic.addAction(action2, 2.0);

    // Activate
    startupLogic.activate();
    startupLogic.execute(0.1);

    // Action 1 should execute
    assertTrue(action1.isExecuted());

    // Now lower pressure below threshold
    testStream.setPressure(3.0, "bara");
    testStream.run();

    // Continue execution - should abort
    for (int i = 0; i < 25; i++) {
      startupLogic.execute(0.1);
    }

    // Should be failed
    assertEquals(LogicState.FAILED, startupLogic.getState());
    assertFalse(action2.isExecuted()); // Action 2 should not have executed
  }

  @Test
  void testReset() {
    // Add simple action
    TestAction action1 = new TestAction("Action 1");
    startupLogic.addAction(action1, 0.0);

    // Activate and execute
    startupLogic.activate();
    startupLogic.execute(0.1);

    assertTrue(startupLogic.isComplete());

    // Reset
    startupLogic.reset();

    // Should be back to idle
    assertEquals(LogicState.IDLE, startupLogic.getState());
    assertFalse(startupLogic.isActive());
    assertFalse(startupLogic.isComplete());
  }

  @Test
  void testGetStatus() {
    // Add permissive
    PressureCondition pressureOk = new PressureCondition(testStream, 5.0, ">");
    startupLogic.addPermissive(pressureOk);

    // Add action
    TestAction action1 = new TestAction("Action 1");
    startupLogic.addAction(action1, 0.0);

    // Activate
    startupLogic.activate();

    // Get status
    String status = startupLogic.getStatusDescription();

    // Should contain key information
    assertTrue(status.contains("Test Startup"));
    assertTrue(status.contains("WAITING") || status.contains("RUNNING"));
  }

  @Test
  void testMultiplePermissives() {
    // Add multiple permissives
    PressureCondition pressureOk = new PressureCondition(testStream, 5.0, ">");
    TemperatureCondition tempOk = new TemperatureCondition(testStream, 50.0, "<");
    timerCondition = new TimerCondition(2.0);

    startupLogic.addPermissive(pressureOk);
    startupLogic.addPermissive(tempOk);
    startupLogic.addPermissive(timerCondition);

    // Add action
    TestAction action1 = new TestAction("Action 1");
    startupLogic.addAction(action1, 0.0);

    // Activate
    startupLogic.activate();
    timerCondition.start();

    // Execute for 1 second (timer not met yet)
    for (int i = 0; i < 10; i++) {
      timerCondition.update(0.1);
      startupLogic.execute(0.1);
    }

    // Should still be waiting
    assertEquals(LogicState.WAITING_PERMISSIVES, startupLogic.getState());

    // Execute for another 2 seconds (timer met)
    for (int i = 0; i < 20; i++) {
      timerCondition.update(0.1);
      startupLogic.execute(0.1);
    }

    // All permissives met, should execute action
    assertTrue(action1.isExecuted());
  }

  /**
   * Simple test action for testing startup logic.
   */
  private static class TestAction implements LogicAction {
    private final String name;
    private boolean executed = false;

    public TestAction(String name) {
      this.name = name;
    }

    @Override
    public void execute() {
      executed = true;
    }

    @Override
    public boolean isComplete() {
      return executed;
    }

    @Override
    public String getDescription() {
      return name;
    }

    @Override
    public String getTargetName() {
      return "TestTarget";
    }

    public boolean isExecuted() {
      return executed;
    }
  }
}
