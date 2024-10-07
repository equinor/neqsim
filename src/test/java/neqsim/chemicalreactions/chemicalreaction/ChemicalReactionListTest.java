package neqsim.chemicalreactions.chemicalreaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class ChemicalReactionListTest {
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
    // System.out.println("components: " + comp.length);
  }
}
