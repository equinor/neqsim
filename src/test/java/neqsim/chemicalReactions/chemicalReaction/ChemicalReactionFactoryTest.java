package neqsim.chemicalReactions.chemicalReaction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ChemicalReactionFactoryTest {
  @Test
  void testGetChemicalReaction() {
    ChemicalReactionFactory crf = new ChemicalReactionFactory();

    String[] reactionNames = crf.getChemicalReactionNames();
    Assertions.assertNotEquals(0, reactionNames.length);
    crf.getChemicalReaction("test");

    crf.getChemicalReaction(reactionNames[0]);
  }
}
