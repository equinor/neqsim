package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemSrkEos;

/** Tests standard outlet resolution used by DEXPI topology and layout projection. */
class DexpiStreamUtilsTest extends NeqSimTest {
  @Test
  void resolvesConfiguredRecycleOutlet() {
    StreamInterface outlet = new Stream("recycle outlet", new SystemSrkEos(298.15, 50.0));
    Recycle recycle = new Recycle("recycle");
    recycle.setOutletStream(outlet);

    assertSame(outlet, DexpiStreamUtils.getGasOutletStream(recycle));
  }

  @Test
  void keepsUnconfiguredRecycleOutletAbsent() {
    Recycle recycle = new Recycle("unconfigured recycle");

    assertNull(DexpiStreamUtils.getGasOutletStream(recycle));
  }
}
