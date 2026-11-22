import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DepletionSnapshot;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
/**
 * Dry methane/n-butane CVD tail qualification without fitted inputs.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TailBenchmarkProbe {
  /**
   * Exports all declared pressure states without partial success artifacts.
   *
   * @param args output JSON path
   * @throws Exception if simulation or output fails
   */
  public static void main(String[] args) throws Exception {
    List<Object> cases = new ArrayList<>();
    for (double temperature : new double[] {275}) {
      for (double methane : new double[] {.95}) {
        SystemInterface fluid = new SystemSrkEos(temperature, 500);
        fluid.addComponent("methane", methane);
        fluid.addComponent("n-butane", 1 - methane);
        fluid.setMixingRule("classic");
        fluid.useVolumeCorrection(false);
        fluid.init(0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", "CVD");
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
        ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(fluid);
        cvd.setTemperature(temperature, "K");
        cvd.setPressures(new double[] {500, 200, 100, 50, 20, 5, 2});
        cvd.setRecordDepletionSnapshots(true);
        cvd.setRefineSaturationConditions(true);
        cvd.runCalc();
        data.put("refinedPressureBara", cvd.getSaturationPressure());
        List<Object> rows = new ArrayList<>();
        for (DepletionSnapshot snapshot : cvd.getDepletionSnapshots()) {
          SystemInterface cell = snapshot.getRetainedFluid();
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("pressureBara", cell.getPressure());
          row.put("retainedMoles", new double[] {cell.getComponent(0).getNumberOfmoles(),
              cell.getComponent(1).getNumberOfmoles()});
          row.put("withdrawnMoles", snapshot.getProducedComponentMoles());
          row.put("volumeM3", cell.getCorrectedVolume());
          List<Object> phases = new ArrayList<>();
          for (int p = 0; p < cell.getNumberOfPhases(); p++) {
            Map<String, Object> phase = new LinkedHashMap<>();
            phase.put("type", cell.getPhase(p).getType().getDesc());
            phase.put("x", new double[] {cell.getPhase(p).getComponent(0).getx(),
                cell.getPhase(p).getComponent(1).getx()});
            phases.add(phase);
          }
          row.put("phases", phases);
          rows.add(row);
        }
        data.put("rows", rows);
        cases.add(data);
      }
    }
    try (FileWriter writer = new FileWriter(args[0])) {
      new Gson().toJson(cases, writer);
    }
  }
}
