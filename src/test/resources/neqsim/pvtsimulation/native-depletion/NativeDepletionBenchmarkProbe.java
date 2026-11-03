import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DepletionSnapshot;
import neqsim.pvtsimulation.util.CalibratedPvtModel;
import neqsim.pvtsimulation.util.DepletionPvtHistoryGenerator;
import neqsim.pvtsimulation.util.MaterialBalancePvtHistory;
import neqsim.pvtsimulation.util.PvtSeparatorPath;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Exports the frozen binary native-depletion matrix for an independent numerical audit.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class NativeDepletionBenchmarkProbe {
  /** Prevents construction of the command-line probe. */
  private NativeDepletionBenchmarkProbe() {
  }

  /**
   * Exports two synthetic fixtures without fitting any model parameter.
   *
   * @param args one output JSON path
   * @throws Exception if the protocol, separator, or file write fails
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("One output JSON path required");
    }
    List<Object> cases = new ArrayList<>();
    for (int which = 0; which < 2; which++) {
      double methane = which == 0 ? 0.7 : 0.95;
      double temperature = which == 0 ? 323.15 : 373.15;
      double[] pressures = which == 0 ? new double[] {500, 300, 200, 100, 50}
          : new double[] {500, 300, 200, 100};
      SystemInterface fluid = new SystemSrkEos(temperature, 500);
      fluid.addComponent("methane", methane);
      fluid.addComponent("n-decane", 1 - methane);
      fluid.setMixingRule("classic");
      fluid.useVolumeCorrection(false);
      fluid.init(0);
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("path", which == 0 ? "DLE" : "CVD");
      data.put("temperatureK", temperature);
      data.put("initial", new double[] {methane, 1 - methane});
      double[][] parameters = new double[2][3];
      for (int i = 0; i < 2; i++) {
        parameters[i] = new double[] {fluid.getComponent(i).getTC(), fluid.getComponent(i).getPC(),
            fluid.getComponent(i).getAcentricFactor()};
      }
      data.put("TcK_PcBara_omega", parameters);
      data.put("kij", ((PhaseEosInterface) fluid.getPhase(0)).getEosMixingRule()
          .getBinaryInteractionParameter(0, 1));
      if (which == 1) {
        ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(fluid.clone());
        cvd.setTemperature(temperature, "K");
        cvd.setPressures(pressures);
        cvd.runCalc();
        data.put("scanSaturationPressureBara", cvd.getSaturationPressure());
      }
      CalibratedPvtModel model = new CalibratedPvtModel(fluid, fluid, "synthetic audit", "original", "tuned",
          "no fitting", new String[] {"independent numerical audit"});
      PvtSeparatorPath separator = new PvtSeparatorPath(new double[] {10, 1.0133},
          new double[] {303.15, 288.71}, "declared two-stage final basis");
      DepletionPvtHistoryGenerator.NativeResult result = DepletionPvtHistoryGenerator.replayNative(model, false,
          which == 0 ? MaterialBalancePvtHistory.Path.DLE : MaterialBalancePvtHistory.Path.CVD,
          "complete synthetic path", temperature, pressures, separator);
      List<Object> rows = new ArrayList<>();
      for (DepletionSnapshot snapshot : result.getSnapshots()) {
        Map<String, Object> row = new LinkedHashMap<>();
        SystemInterface cell = snapshot.getRetainedFluid();
        SystemInterface equilibrium = snapshot.getEquilibriumFluid();
        row.put("pressureBara", cell.getPressure());
        row.put("retainedMoles", new double[] {cell.getComponent(0).getNumberOfmoles(),
            cell.getComponent(1).getNumberOfmoles()});
        row.put("withdrawnMoles", snapshot.getProducedComponentMoles());
        row.put("volumeM3", cell.getCorrectedVolume());
        row.put("equilibriumVolumeM3", equilibrium.getCorrectedVolume());
        List<Object> phases = new ArrayList<>();
        for (int phase = 0; phase < equilibrium.getNumberOfPhases(); phase++) {
          Map<String, Object> phaseData = new LinkedHashMap<>();
          phaseData.put("type", equilibrium.getPhase(phase).getType().getDesc());
          phaseData.put("moles", equilibrium.getPhase(phase).getNumberOfMolesInPhase());
          phaseData.put("x", new double[] {equilibrium.getPhase(phase).getComponent(0).getx(),
              equilibrium.getPhase(phase).getComponent(1).getx()});
          phaseData.put("z", equilibrium.getPhase(phase).getZ());
          phases.add(phaseData);
        }
        row.put("phases", phases);
        rows.add(row);
      }
      data.put("rows", rows);
      cases.add(data);
    }
    try (FileWriter writer = new FileWriter(args[0])) {
      new Gson().toJson(cases, writer);
    }
  }
}
