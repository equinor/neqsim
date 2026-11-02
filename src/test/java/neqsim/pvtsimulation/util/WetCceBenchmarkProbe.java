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
public class WetCceBenchmarkProbe {
  /**
   * Exports all complete pressure paths, rejecting failed calculations.
   *
   * @param args output JSON path
   * @throws Exception if a calculation or file write fails
   */
  public static void main(String[] args) throws Exception {
    List<Object> data = new ArrayList<>();
    for (double t : new double[] {340, 350, 360}) {
      SystemInterface f = new SystemSrkEos(t, 300);
      f.addComponent("methane", .5);
      f.addComponent("water", .5);
      f.setMixingRule("classic");
      f.useVolumeCorrection(false);
      f.setMultiPhaseCheck(true);
      f.init(0);
      Map<String, Object> c = new LinkedHashMap<>();
      c.put("temperatureK", t);
      c.put("initial", new double[] {.5, .5});
      double[][] pars = new double[2][3];
      for (int i = 0; i < 2; i++) {
        pars[i] = new double[] {f.getComponent(i).getTC(), f.getComponent(i).getPC(),
            f.getComponent(i).getAcentricFactor()};
      }
      c.put("parameters", pars);
      c.put("kij", ((PhaseEosInterface) f.getPhase(0)).getEosMixingRule().getBinaryInteractionParameter(0, 1));
      ConstantMassExpansion sim = new ConstantMassExpansion(f);
      sim.setPressures(new double[] {300, 150, 50});
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
          phase.put("x", new double[] {s.getPhase(p).getComponent(0).getx(), s.getPhase(p).getComponent(1).getx()});
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
