package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.expander.ExpanderChartKhader;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link TurboMachineryChartLibrary}, the shipped reference-map library.
 *
 * @author NeqSim
 * @version 1.0
 */
public class TurboMachineryChartLibraryTest {

  /**
   * Builds a representative working fluid.
   *
   * @return a methane/ethane SRK fluid at 25 C, 50 bara
   */
  private SystemInterface fluid() {
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule(2);
    return gas;
  }

  /**
   * The library should advertise at least one compressor and one expander reference map.
   */
  @Test
  void testListsAdvertiseReferenceMaps() {
    TurboMachineryChartLibrary library = new TurboMachineryChartLibrary();
    List<String> compressorCharts = library.listCompressorCharts();
    List<String> expanderCharts = library.listExpanderCharts();
    assertFalse(compressorCharts.isEmpty(), "expected at least one compressor reference map");
    assertFalse(expanderCharts.isEmpty(), "expected at least one expander reference map");
    assertTrue(compressorCharts.contains(TurboMachineryChartLibrary.GENERIC_CENTRIFUGAL_3SPEED));
    assertTrue(expanderCharts.contains(TurboMachineryChartLibrary.GENERIC_CRYO_EXPANDER));
    assertTrue(expanderCharts.contains(TurboMachineryChartLibrary.GEOMETRY_RADIAL_IFR));
  }

  /**
   * The generic centrifugal compressor map should produce a sane surge flow and head.
   */
  @Test
  void testCompressorReferenceMapIsUsable() {
    TurboMachineryChartLibrary library = new TurboMachineryChartLibrary();
    SystemInterface gas = fluid();
    new neqsim.thermodynamicoperations.ThermodynamicOperations(gas).TPflash();
    gas.initThermoProperties();
    CompressorChartKhader2015 chart =
        library.getCompressorChart(TurboMachineryChartLibrary.GENERIC_CENTRIFUGAL_3SPEED, gas, 0.3);
    double head = chart.getPolytropicHead(2600.0, 10500.0);
    double eff = chart.getPolytropicEfficiency(2600.0, 10500.0);
    assertTrue(head > 0.0, "head must be positive: " + head);
    assertTrue(eff > 0.5 && eff <= 1.0, "efficiency out of range: " + eff);
    double surgeFlow = chart.getSurgeFlowAtSpeed(10500.0);
    assertTrue(surgeFlow > 0.0, "surge flow must be positive: " + surgeFlow);
  }

  /**
   * The expander reference maps should produce a peaked efficiency characteristic.
   */
  @Test
  void testExpanderReferenceMapsAreUsable() {
    TurboMachineryChartLibrary library = new TurboMachineryChartLibrary();
    SystemInterface ref = fluid();

    ExpanderChartKhader cryo =
        library.getExpanderChart(TurboMachineryChartLibrary.GENERIC_CRYO_EXPANDER, ref);
    double ucPeak = cryo.getOptimumVelocityRatio(1.0);
    double etaPeak = cryo.getEfficiency(ucPeak, 1.0);
    assertTrue(etaPeak > 0.80 && etaPeak < 0.95, "cryo peak efficiency out of range: " + etaPeak);
    assertTrue(cryo.getStageHeadDrop(ucPeak, 1.0, ref) > 0.0, "cryo head must be positive");

    ExpanderChartKhader geom =
        library.getExpanderChart(TurboMachineryChartLibrary.GEOMETRY_RADIAL_IFR, ref);
    double geomUcPeak = geom.getOptimumVelocityRatio(1.0);
    double geomEtaPeak = geom.getEfficiency(geomUcPeak, 1.0);
    assertTrue(geomEtaPeak > 0.70 && geomEtaPeak < 0.95,
        "geometry peak efficiency out of range: " + geomEtaPeak);
  }

  /**
   * Unknown map names should be rejected.
   */
  @Test
  void testUnknownNamesRejected() {
    final TurboMachineryChartLibrary library = new TurboMachineryChartLibrary();
    final SystemInterface gas = fluid();
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        library.getCompressorChart("DOES_NOT_EXIST", gas, 0.3);
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        library.getExpanderChart("DOES_NOT_EXIST", gas);
      }
    });
  }
}
