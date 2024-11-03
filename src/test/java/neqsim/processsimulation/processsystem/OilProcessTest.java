package neqsim.processsimulation.processsystem;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Test class for GlycolRig.
 */
public class OilProcessTest extends neqsim.NeqSimTest {
  static Logger logger = LogManager.getLogger(OilProcessTest.class);

  ProcessSystem p;
  String _name = "TestProcess";

  @BeforeEach
  public void setUp() {
    p = new ProcessSystem();
    p.setName(_name);
  }
}
