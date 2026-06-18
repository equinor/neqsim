package neqsim.chemicalreactions.chemicalreaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
// import org.apache.logging.log4j.LogManager;
// import org.apache.logging.log4j.Logger;

public class ChemicalReactionListTest {
  // private static final Logger logger = LogManager.getLogger(ChemicalReactionListTest.class);

  ChemicalReactionList test;

  @BeforeEach
  public void setUp() {
    test = new ChemicalReactionList();
  }

  @Disabled
  @Test
  public void TestremoveJunkReactions() {
    // String[] test2 = {"water", "MDEA"};
    // test.removeJunkReactions(test2);
    // String[] comp = test.getAllComponents();
    // logger.info("components: " + comp.length);
  }
}
