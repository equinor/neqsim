package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class AdjusterRunStepTest {

  @Test
  void testRunStep() {
    // Create a simple system
    SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 100.0); // 100 moles

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr"); // Total flow 1000 kg/hr

    Splitter splitter = new Splitter("splitter", feed);
    splitter.setSplitNumber(2);
    splitter.setSplitFactors(new double[] {0.5, 0.5});

    // We want to adjust stream 2 flow rate (via splitter)
    // so that stream 1 flow rate becomes 800 kg/hr.
    // Since total is 1000, stream 2 should become 200.

    Adjuster adjuster = new Adjuster("adjuster");

    // Set the equipment references
    adjuster.setAdjustedEquipment(splitter);
    adjuster.setTargetEquipment(splitter.getSplitStream(0));

    // Set the target value (Setpoint)
    adjuster.setTargetValue(800.0);
    adjuster.setMinAdjustedValue(0.0); // Flow rate cannot be negative
    adjuster.setMaxAdjustedValue(1000.0); // Flow rate cannot exceed feed

    // Set the logic using functional interfaces

    // Setter: Adjusts the flow rate of the second outlet (index 1) of the splitter
    // Note: Splitter.setFlowRates takes an array of flow rates. -1 means calculated.
    adjuster.setAdjustedValueSetter((eq, val) -> {
      ((Splitter) eq).setFlowRates(new double[] {-1, val}, "kg/hr");
    });

    // Getter: Gets the current flow rate of the second outlet (index 1)
    adjuster.setAdjustedValueGetter((eq) -> {
      return ((Splitter) eq).getSplitStream(1).getFlowRate("kg/hr");
    });

    // Target Calculator: Gets the flow rate of stream 1 (the measured variable)
    adjuster.setTargetValueCalculator((eq) -> {
      return ((Stream) eq).getFlowRate("kg/hr");
    });

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(splitter);
    // Add the splitter's internal streams to the process
    process.add(splitter.getSplitStream(0));
    process.add(splitter.getSplitStream(1));
    process.add(adjuster);

    // Run step by step
    for (int i = 0; i < 50; i++) {
      process.run_step();
    }

    // Verify results
    assertEquals(800.0, splitter.getSplitStream(0).getFlowRate("kg/hr"), 1.0);
    assertEquals(200.0, splitter.getSplitStream(1).getFlowRate("kg/hr"), 1.0);
  }
}
