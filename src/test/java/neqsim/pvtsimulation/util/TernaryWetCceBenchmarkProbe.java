package neqsim.pvtsimulation.util;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;
import neqsim.pvtsimulation.simulation.DepletionSnapshot;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Exports declared synthetic wet SRK closed-mass states without fitting.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class TernaryWetCceBenchmarkProbe {
  /**
   * Exports all complete pressure paths, rejecting failed calculations.
   *
   * @param args output JSON path
   * @throws Exception if a calculation or file write fails
   */
  public static void main(String[] args) throws Exception {
    List<Object> data = new ArrayList<>();
    for (double t : new double[] {350}) {
      SystemInterface f = new SystemSrkEos(t, 300);
      f.addComponent("methane", .6);
      f.addComponent("n-decane", .2);
      f.addComponent("water", .2);
      f.setMixingRule("classic");
      f.useVolumeCorrection(false);
      f.setMultiPhaseCheck(true);
      f.init(0);
      Map<String, Object> c = new LinkedHashMap<>();
      c.put("temperatureK", t);
      c.put("initial", new double[] {.6, .2, .2});
      double[][] pars = new double[3][3];
      for (int i = 0; i < 3; i++) {
        pars[i] = new double[] {f.getComponent(i).getTC(), f.getComponent(i).getPC(),
            f.getComponent(i).getAcentricFactor()};
      }
      c.put("parameters", pars);
      double[][] kij = new double[3][3];
      for (int i = 0; i < 3; i++) {
        for (int j = 0; j < 3; j++) {
          kij[i][j] = ((PhaseEosInterface) f.getPhase(0)).getEosMixingRule().getBinaryInteractionParameter(i, j);
        }
      }
      c.put("kij", kij);
      ConstantMassExpansion sim = new ConstantMassExpansion(f);
      sim.setPressures(new double[] {200, 100, 50});
      List<Object> rows = new ArrayList<>();
      for (DepletionSnapshot snap : sim.runNativeExpansion()) {
        SystemInterface s = snap.getRetainedFluid();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("pressureBara", s.getPressure());
        r.put("volumeM3", s.getCorrectedVolume());
        List<Object> phases = new ArrayList<>();
        for (int p = 0; p < s.getNumberOfPhases(); p++) {
          Map<String, Object> phase = new LinkedHashMap<>();
          phase.put("type", s.getPhase(p).getType().getDesc());
          phase.put("beta", s.getPhase(p).getBeta());
          phase.put("x", new double[] {s.getPhase(p).getComponent(0).getx(), s.getPhase(p).getComponent(1).getx(),
              s.getPhase(p).getComponent(2).getx()});
          phases.add(phase);
        }
        r.put("phases", phases);
        rows.add(r);
      }
      c.put("rows", rows);
      data.add(c);
    }
    try (FileWriter writer = new FileWriter(args[0])) {
      new Gson().toJson(data, writer);
    }
  }
}
