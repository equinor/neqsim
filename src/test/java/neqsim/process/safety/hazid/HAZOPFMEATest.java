package neqsim.process.safety.hazid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class HAZOPFMEATest {

  @Test
  void hazopGridGeneratesAllCombinations() {
    HAZOPTemplate hz = new HAZOPTemplate("Node-1", "Pump P-101 inlet")
        .generateGrid(HAZOPTemplate.Parameter.FLOW, HAZOPTemplate.Parameter.PRESSURE);
    // 7 guide-words × 2 parameters = 14
    assertEquals(14, hz.getDeviations().size());
  }

  @Test
  void hazopDocumentationPatternAddsDeviationAndReports() {
    HAZOPTemplate hz = new HAZOPTemplate("Node 3 - HP separator inlet",
        "Route HP separator inlet flow within design pressure and liquid handling limits");
    hz.addDeviation(HAZOPTemplate.GuideWord.MORE, HAZOPTemplate.Parameter.FLOW,
        "Upstream pump runaway", "Liquid carry-over to flare KO drum",
        "FT-101 high-flow alarm; BDV on overpressure", "Add HIPPS interlock");

    assertEquals(1, hz.getDeviations().size());
    assertTrue(hz.report().contains("MORE + FLOW"));
    assertTrue(hz.report().contains("Add HIPPS interlock"));
  }

  @Test
  void fmeaRPNCalculatedAndSorted() {
    FMEAWorksheet f = new FMEAWorksheet("Compressor K-100");
    f.addEntry("Bearing", "Wear", "Vibration", "Lub failure", 5, 4, 6, "VIB monitor");
    f.addEntry("Seal", "Leak", "HC release", "Aging", 9, 3, 4, "Online seal monitor");
    assertEquals(120, f.sortedByRPN().get(0).rpn());
    assertEquals(108, f.sortedByRPN().get(1).rpn());
    assertTrue(f.entriesAboveRPN(110).size() == 1);
  }
}
