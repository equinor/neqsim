package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.NeqSimTest;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemSoreideWhitson;
import neqsim.util.database.NeqSimDataBase;

/** Prevent malformed Soreide-Whitson data from silently selecting another EOS column. */
class SoreideWhitsonInteractionDataTest extends NeqSimTest {
  @ParameterizedTest
  @CsvSource({"propane,0.1241", "n-butane,0.133", "n-pentane,0.14", "n-hexane,0.145", "n-heptane,0.145",
      "mercury,0.0145", "methane,0.107"})
  void loadsDedicatedInteractionInBothComponentOrders(String component, double expected) {
    for (boolean reverse : new boolean[] {false, true}) {
      SystemSoreideWhitson system = new SystemSoreideWhitson(298.0, 20.0);
      system.addComponent(reverse ? component : "CO2", 0.3);
      system.addComponent(reverse ? "CO2" : component, 0.3);
      system.addComponent("water", 0.4);
      system.addSalinity(0.0, "mole/sec");
      system.init(0);
      system.setMixingRule(11);
      system.init(1);
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        EosMixingRulesInterface rule = ((PhaseEosInterface) system.getPhase(phase)).getEosMixingRule();
        assertEquals(expected, rule.getBinaryInteractionParameter(0, 1), 1e-12, component);
        assertEquals(expected, rule.getBinaryInteractionParameter(1, 0), 1e-12, "symmetric non-water pair");
      }
    }
  }

  @Test
  void everyPresentWhitsonInteractionIsFiniteAndParseable() throws Exception {
    int values = 0;
    try (NeqSimDataBase database = new NeqSimDataBase();
        ResultSet rows = database.getResultSet("SELECT COMP1, COMP2, KIJWhitsonSoriede FROM INTER")) {
      while (rows.next()) {
        String value = rows.getString("KIJWhitsonSoriede");
        if (value != null && !value.trim().isEmpty()) {
          assertTrue(Double.isFinite(Double.parseDouble(value)),
              rows.getString("COMP1") + "/" + rows.getString("COMP2"));
          values++;
        }
      }
    }
    assertTrue(values > 300, "audit the complete populated column");
  }
}
