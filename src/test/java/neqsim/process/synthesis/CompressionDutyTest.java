package neqsim.process.synthesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link CompressionDuty} and {@link FlowsheetSynthesisEngine#proposeAndBuildCompression(CompressionDuty)}.
 */
class CompressionDutyTest {

  private Stream gasFeed(double pBara, double tC, double flowKgHr) {
    SystemInterface fluid = new SystemSrkEos(273.15 + tC, pBara);
    fluid.addComponent("methane", 0.88);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");
    Stream s = new Stream("feed", fluid);
    s.setFlowRate(flowKgHr, "kg/hr");
    s.setTemperature(tC, "C");
    s.setPressure(pBara, "bara");
    s.run();
    return s;
  }

  @Test
  void dutyValidatesConstructorArguments() {
    final Stream feed = gasFeed(10.0, 25.0, 5000.0);
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        new CompressionDuty(null, feed, 100.0);
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        new CompressionDuty("X", null, 100.0);
      }
    });
    // Discharge below feed pressure rejected.
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        new CompressionDuty("X", feed, 5.0);
      }
    });
  }

  @Test
  void singleStageChosenWhenRatioBelowLimit() {
    Stream feed = gasFeed(40.0, 25.0, 8000.0);
    CompressionDuty duty = new CompressionDuty("Export", feed, 100.0); // ratio 2.5
    CompressionProposal p = new FlowsheetSynthesisEngine().proposeAndBuildCompression(duty);
    assertEquals(1, p.getStages());
    assertEquals(1, p.getStageNames().size());
    assertTrue(p.getPerStagePressureRatio() < duty.getMaxStageRatio() + 1.0e-9);
    assertNotNull(p.getProcessSystem().getUnit("Export-K1"));
  }

  @Test
  void multiStageWithIntercoolersChosenForHighOverallRatio() {
    Stream feed = gasFeed(10.0, 25.0, 5000.0);
    CompressionDuty duty = new CompressionDuty("Inj", feed, 200.0); // ratio 20
    CompressionProposal p = new FlowsheetSynthesisEngine().proposeAndBuildCompression(duty);
    // ceil(ln(20)/ln(3.5)) = ceil(2.39) = 3
    assertEquals(3, p.getStages());
    assertTrue(p.getPerStagePressureRatio() <= duty.getMaxStageRatio() + 1.0e-9);
    assertNotNull(p.getProcessSystem().getUnit("Inj-K1"));
    assertNotNull(p.getProcessSystem().getUnit("Inj-K2"));
    assertNotNull(p.getProcessSystem().getUnit("Inj-K3"));
    // Inter-coolers between every stage pair (2 of them).
    assertNotNull(p.getProcessSystem().getUnit("Inj-IC1"));
    assertNotNull(p.getProcessSystem().getUnit("Inj-IC2"));
    // After-cooler enabled by default.
    assertNotNull(p.getProcessSystem().getUnit("Inj-AC"));
  }

  @Test
  void proposedFlowsheetRunsAndReachesTargetPressure() {
    Stream feed = gasFeed(10.0, 25.0, 5000.0);
    CompressionDuty duty = new CompressionDuty("Inj", feed, 200.0);
    CompressionProposal p = new FlowsheetSynthesisEngine().proposeAndBuildCompression(duty);
    p.getProcessSystem().run();
    Compressor lastStage = (Compressor) p.getProcessSystem().getUnit("Inj-K" + p.getStages());
    double pout = lastStage.getOutletStream().getPressure("bara");
    assertEquals(200.0, pout, 1.0e-3, "last stage discharge should equal duty target");
    // After-cooler should bring temperature near 35 C.
    Cooler ac = (Cooler) p.getProcessSystem().getUnit("Inj-AC");
    double tOut = ac.getOutletStream().getTemperature("C");
    assertEquals(duty.getFinalCoolerTemperatureC(), tOut, 0.5, "after-cooler outlet T should equal duty spec");
  }

  @Test
  void rationaleStringMentionsStagesAndRatio() {
    Stream feed = gasFeed(10.0, 25.0, 5000.0);
    CompressionDuty duty = new CompressionDuty("Inj", feed, 200.0);
    CompressionProposal p = new FlowsheetSynthesisEngine().proposeAndBuildCompression(duty);
    String r = p.getRationale();
    assertNotNull(r);
    assertTrue(r.toLowerCase(java.util.Locale.ROOT).contains("stage"));
    assertTrue(r.contains("ratio") || r.contains("pressure"));
  }

  @Test
  void afterCoolerCanBeDisabled() {
    Stream feed = gasFeed(10.0, 25.0, 5000.0);
    CompressionDuty duty = new CompressionDuty("Inj", feed, 50.0).setAfterCooler(false, 40.0);
    CompressionProposal p = new FlowsheetSynthesisEngine().proposeAndBuildCompression(duty);
    org.junit.jupiter.api.Assertions.assertNull(p.getProcessSystem().getUnit("Inj-AC"),
        "after-cooler must not be built when disabled");
    for (String n : p.getStageNames()) {
      org.junit.jupiter.api.Assertions.assertFalse(n.endsWith("-AC"),
          "stage name list must not include after-cooler when disabled");
    }
  }
}
