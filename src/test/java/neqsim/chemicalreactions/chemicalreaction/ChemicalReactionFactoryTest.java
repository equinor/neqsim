package neqsim.chemicalreactions.chemicalreaction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ChemicalReactionFactoryTest {
  @Test
  void testGetChemicalReaction() {
    String[] reactionNames = ChemicalReactionFactory.getChemicalReactionNames();
    Assertions.assertNotEquals(0, reactionNames.length);

    ChemicalReactionFactory.getChemicalReaction("test");
    ChemicalReactionFactory.getChemicalReaction("CO2water");
    ChemicalReactionFactory.getChemicalReaction(reactionNames[0]);
  }
}
