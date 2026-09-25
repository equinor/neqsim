package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/** Missing tear-stream configuration is reported at the recycle call site. */
class RecycleOutletConfigurationTest {
  @Test
  void explainsMissingOutletBeforeWiringOrRunning() {
    Recycle recycle = new Recycle("gas recycle");
    Stream inlet = new Stream("inlet", new SystemSrkEos(298.15, 1.0));
    inlet.getThermoSystem().addComponent("methane", 1.0);
    recycle.addStream(inlet);
    IllegalStateException wiring = assertThrows(IllegalStateException.class, recycle::getOutletStream);
    assertTrue(wiring.getMessage().contains("gas recycle"));
    assertTrue(wiring.getMessage().contains("setOutletStream"));
    IllegalStateException running = assertThrows(IllegalStateException.class, () -> recycle.run(UUID.randomUUID()));
    assertTrue(running.getMessage().contains("setOutletStream"));
    recycle.setOutletStream(inlet);
    assertSame(inlet, recycle.getOutletStream());
  }
}
