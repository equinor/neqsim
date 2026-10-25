package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies that accelerated sweeps connect liquid and vapor traffic on both sides of a middle feed.
 *
 * @author NeqSim / AI-assisted
 * @version 1.0
 */
class AcceleratedTraySweepConnectivityTest extends neqsim.NeqSimTest {
  /** Tray double retaining distinct outlet states while recording installed inlet snapshots. */
  private static final class RecordingTray extends SimpleTray {
    private static final long serialVersionUID = 1000L;
    private final StreamInterface gas;
    private final StreamInterface liquid;

    /**
     * Create a tray with distinct gas and liquid outlet snapshots.
     *
     * @param stage stage index used to distinguish compositions and flow rates
     */
    private RecordingTray(int stage) {
      super("recording tray " + stage);
      gas = createStream("gas " + stage, 0.1 + 0.1 * stage, 10.0 + stage);
      liquid = createStream("liquid " + stage, 0.8 - 0.1 * stage, 20.0 + stage);
      addStream(createStream("initial gas " + stage, 0.05, 1.0));
      addStream(createStream("initial liquid " + stage, 0.95, 2.0));
    }

    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getGasOutStream() {
      return gas;
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getLiquidOutStream() {
      return liquid;
    }
  }

  /**
   * Create a material stream with a distinct methane fraction and molar flow.
   *
   * @param name stream name
   * @param methaneFraction methane mole fraction
   * @param flow molar flow in mol/hr
   * @return an equilibrated source stream
   */
  private static Stream createStream(String name, double methaneFraction, double flow) {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 5.0);
    fluid.addComponent("methane", methaneFraction);
    fluid.addComponent("n-butane", 1.0 - methaneFraction);
    fluid.setMixingRule("classic");
    Stream stream = new Stream(name, fluid);
    stream.setFlowRate(flow, "mol/hr");
    stream.run();
    return stream;
  }

  /**
   * A middle feed must not prevent current condenser liquid from reaching the rectifying trays and feed tray.
   *
   * @throws Exception if the private sweep cannot be invoked for this connectivity regression
   */
  @Test
  void middleFeedSweepRefreshesBothLiquidLegsAndVaporLeg() throws Exception {
    DistillationColumn column = new DistillationColumn("recorded sweep", 5, false, false);
    List<SimpleTray> trays = new ArrayList<SimpleTray>();
    for (int stage = 0; stage < 5; stage++) {
      trays.add(new RecordingTray(stage));
    }
    Field trayField = DistillationColumn.class.getDeclaredField("trays");
    trayField.setAccessible(true);
    trayField.set(column, trays);
    Method sweep = DistillationColumn.class.getDeclaredMethod("performFullTraySweep", UUID.class, int.class);
    sweep.setAccessible(true);
    sweep.invoke(column, UUID.randomUUID(), 2);

    for (int stage = 0; stage < 4; stage++) {
      StreamInterface expected = trays.get(stage + 1).getLiquidOutStream();
      StreamInterface installed = trays.get(stage).getStream(1);
      assertEquals(expected.getFlowRate("mol/hr"), installed.getFlowRate("mol/hr"), 1.0e-10,
          "Every downward liquid interface must carry the current upstream inventory");
      assertEquals(expected.getFluid().getMolarComposition()[0], installed.getFluid().getMolarComposition()[0], 1.0e-12,
          "Every downward liquid interface must carry the current upstream composition");
      assertNotSame(expected, installed, "The receiving tray owns an independent inlet snapshot");
    }
    for (int stage = 1; stage < 5; stage++) {
      int inlet = stage == 4 ? 1 : 0;
      StreamInterface expected = trays.get(stage - 1).getGasOutStream();
      StreamInterface installed = trays.get(stage).getStream(inlet);
      assertEquals(expected.getFlowRate("mol/hr"), installed.getFlowRate("mol/hr"), 1.0e-10);
      assertEquals(expected.getFluid().getMolarComposition()[0], installed.getFluid().getMolarComposition()[0],
          1.0e-12);
      assertNotSame(expected, installed);
    }
    assertEquals(1, column.getLastAcceleratedFullTraySweepCount());
    assertEquals(8, column.getLastAcceleratedInternalStreamTransferCount());
  }
}
