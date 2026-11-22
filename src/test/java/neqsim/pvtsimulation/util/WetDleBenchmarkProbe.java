package neqsim.pvtsimulation.util;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import neqsim.pvtsimulation.simulation.DepletionSnapshot;
import neqsim.pvtsimulation.util.MaterialBalancePvtHistory.Path;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Exports the complete declared synthetic native wet-DLE experiment and separator outputs.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class WetDleBenchmarkProbe {
  /**
   * Exports a state in native component order.
   *
   * @param fluid immutable snapshot copy
   * @return volume, inventory and every present phase
   */
  private static Map<String, Object> state(SystemInterface fluid) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("volumeM3", fluid.getCorrectedVolume());
    double[] inventory = new double[fluid.getNumberOfComponents()];
    for (int component = 0; component < inventory.length; component++) {
      inventory[component] = fluid.getComponent(component).getNumberOfmoles();
    }
    data.put("componentMoles", inventory);
    List<Object> phases = new ArrayList<>();
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("type", fluid.getPhase(phase).getType().getDesc());
      entry.put("moles", fluid.getPhase(phase).getNumberOfMolesInPhase());
      double[] composition = new double[inventory.length];
      for (int component = 0; component < inventory.length; component++) {
        composition[component] = fluid.getPhase(phase).getComponent(component).getx();
      }
      entry.put("x", composition);
      phases.add(entry);
    }
    data.put("phases", phases);
    return data;
  }

  /**
   * Exports an unfitted one-mole SRK/classic path with explicit surface basis.
   *
   * @param args output JSON path
   * @throws Exception if calculation or file writing fails
   */
  public static void main(String[] args) throws Exception {
    SystemInterface fluid = new SystemSrkEos(350, 200);
    fluid.addComponent("methane", .6);
    fluid.addComponent("n-decane", .2);
    fluid.addComponent("water", .2);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.useVolumeCorrection(false);
    fluid.init(0);
    CalibratedPvtModel model = new CalibratedPvtModel(fluid, fluid, "synthetic wet DLE", "SRK", "SRK",
        "independent numerical reference; no measured fit", new String[] {"other-experiment"});
    PvtSeparatorPath separator = new PvtSeparatorPath(new double[] {10, 1.01325}, new double[] {303.15, 298.15},
        "synthetic 25 C, 101.325 kPa absolute final basis");
    DepletionPvtHistoryGenerator.NativeResult result = DepletionPvtHistoryGenerator.replayNative(model, false, Path.DLE,
        "whole synthetic wet DLE holdout", 350, new double[] {200, 100, 50}, separator);
    List<Object> rows = new ArrayList<>();
    for (int row = 0; row < result.getSnapshots().length; row++) {
      DepletionSnapshot snapshot = result.getSnapshots()[row];
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("pressureBara", snapshot.getRetainedFluid().getPressure());
      data.put("equilibrium", state(snapshot.getEquilibriumFluid()));
      data.put("retained", state(snapshot.getRetainedFluid()));
      data.put("withdrawnMoles", snapshot.getProducedComponentMoles());
      data.put("retainedProductMoles", result.getRetainedProducts(row).getComponentMoles());
      data.put("withdrawnProductMoles", result.getWithdrawalProducts(row).getComponentMoles());
      data.put("retainedProductVolumesM3", result.getRetainedProducts(row).getVolumesM3());
      data.put("withdrawnProductVolumesM3", result.getWithdrawalProducts(row).getVolumesM3());
      rows.add(data);
    }
    try (FileWriter writer = new FileWriter(args[0])) {
      new Gson().toJson(rows, writer);
    }
  }
}
