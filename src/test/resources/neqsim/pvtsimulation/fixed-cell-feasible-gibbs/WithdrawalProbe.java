import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DepletionSnapshot;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent withdrawal-mesh sensitivity diagnostic, sharing the accepted EOS flash only.
 * 
 * @author Even Solbraa
 * @version 1.0
 */
public class WithdrawalProbe {
  /** Frozen pressure grid in absolute bara. */
  private static final double[] PRESSURES = {100, 60, 30, 10, 2};
  /** Existing local equilibrium precision gate, shared to isolate withdrawal integration. */
  private static Method precision;
  private static final List<Map<String, Object>> diagnostics = new ArrayList<Map<String, Object>>();

  /**
   * Equilibrates with the same precision gate to isolate protocol discretization.
   * 
   * @param f local fluid
   * @throws Exception if equilibrium fails
   */
  private static void flash(SystemInterface f) throws Exception {
    new ThermodynamicOperations(f).TPflash();
    f.init(1);
    f.initPhysicalProperties(neqsim.physicalproperties.PhysicalPropertyType.MASS_DENSITY);
    SystemInterface before = org.apache.commons.lang3.SerializationUtils.clone(f);
    double beforeGibbs = f.getGibbsEnergy();
    double[] beforeClosure = phaseClosure(f);
    try {
      precision.invoke(null, f);
    } catch (Exception ex) {
      Method residual = ConstantVolumeDepletion.class.getDeclaredMethod("fixedCellFugacityResidual",
          SystemInterface.class);
      residual.setAccessible(true);
      Map<String, Object> d = new LinkedHashMap<String, Object>();
      d.put("T", f.getTemperature());
      d.put("pressure", f.getPressure());
      d.put("inventory", inventory(f));
      d.put("beforeGibbs", beforeGibbs);
      d.put("afterGibbs", f.getGibbsEnergy());
      d.put("identityPhaseEnergy", projectedEnergy(before, false));
      d.put("feasiblePhaseEnergy", projectedEnergy(before, true));
      d.put("phaseClosureBefore", beforeClosure);
      d.put("phaseClosureAfter", phaseClosure(f));
      d.put("residual", residual.invoke(null, f));
      SystemInterface fresh = new SystemSrkEos(f.getTemperature(), f.getPressure());
      for (int i = 0; i < f.getNumberOfComponents(); i++)
        fresh.addComponent(f.getComponentNames()[i], f.getComponent(i).getNumberOfmoles());
      fresh.setMixingRule("classic");
      fresh.useVolumeCorrection(false);
      fresh.setMultiPhaseCheck(true);
      fresh.init(0);
      try {
        new ConstantVolumeDepletion(fresh).runDryPhaseChangingFixedCellDepletion(f.getTemperature(),
            new double[] {f.getPressure(), f.getPressure() * .99});
        d.put("freshPublicReplay", "pass");
      } catch (Exception problem) {
        d.put("freshPublicReplay", problem.toString());
      }
      diagnostics.add(d);
      throw new IllegalStateException("Precision at pressure=" + f.getPressure() + " inventory="
          + java.util.Arrays.toString(inventory(f)) + " phase0=" + f.getPhase(0).getType() + " phases="
          + f.getNumberOfPhases() + " cause=" + ex.getCause() + " residual=" + residual.invoke(null, f)
          + " beforeGibbs=" + beforeGibbs + " afterGibbs=" + f.getGibbsEnergy() + " GibbsAllowance="
          + (1e-10 * Math.max(1.0, Math.abs(beforeGibbs))) + " closureBefore="
          + java.util.Arrays.toString(beforeClosure) + " closureAfter=" + java.util.Arrays.toString(phaseClosure(f)),
          ex);
    }
  }

  /**
   * Reads named total inventories.
   * 
   * @param f local fluid
   * @return component moles
   */
  private static double[] inventory(SystemInterface f) {
    double[] n = new double[f.getNumberOfComponents()];
    for (int i = 0; i < n.length; i++)
      n[i] = f.getComponent(i).getNumberOfmoles();
    return n;
  }

  /**
   * Reconstructs a feasible phase allocation without fitting an EOS.
   * 
   * @param state accepted initial state
   * @param project true scales each component across phases to its stored total
   * @return sum of homogeneous phase Gibbs energies in J
   */
  private static double projectedEnergy(SystemInterface state, boolean project) {
    int np = state.getNumberOfPhases(), nc = state.getNumberOfComponents();
    double[][] amount = new double[np][nc];
    for (int i = 0; i < nc; i++) {
      double sum = 0;
      for (int p = 0; p < np; p++) {
        amount[p][i] = state.getPhase(p).getNumberOfMolesInPhase() * state.getPhase(p).getComponent(i).getx();
        sum += amount[p][i];
      }
      if (project)
        for (int p = 0; p < np; p++)
          amount[p][i] *= state.getComponent(i).getNumberOfmoles() / sum;
    }
    double energy = 0;
    for (int p = 0; p < np; p++) {
      SystemInterface phase = state.phaseToSystem(p);
      for (int i = 0; i < nc; i++)
        phase.addComponent(i, amount[p][i] - phase.getComponent(i).getNumberOfmoles());
      phase.setForceSinglePhase(state.getPhase(p).getType());
      phase.init(3);
      energy += phase.getGibbsEnergy();
    }
    return energy;
  }

  /**
   * Phase-to-total component residuals used only for diagnosis.
   * 
   * @param f state under assessment
   * @return component mole residuals
   */
  private static double[] phaseClosure(SystemInterface f) {
    double[] error = inventory(f);
    for (int i = 0; i < error.length; i++) {
      error[i] = -error[i];
      for (int p = 0; p < f.getNumberOfPhases(); p++)
        error[i] += f.getPhase(p).getNumberOfMolesInPhase() * f.getPhase(p).getComponent(i).getx();
    }
    return error;
  }

  /**
   * Builds a declared synthetic dry SRK case, without parameter fitting.
   * 
   * @param temperature K
   * @param ternary true adds n-heptane
   * @return new sample with one total mole
   */
  private static SystemInterface sample(double temperature, boolean ternary) {
    SystemInterface f = new SystemSrkEos(temperature, 100);
    f.addComponent("methane", ternary ? .90 : .95);
    f.addComponent("n-butane", .05);
    if (ternary)
      f.addComponent("n-heptane", .05);
    f.setMixingRule("classic");
    f.setMultiPhaseCheck(true);
    f.useVolumeCorrection(false);
    f.init(0);
    return f;
  }

  /**
   * Replays the same pressure endpoints with a capped equilibrium vapor parcel. Each parcel is at most cap times the
   * initial sample amount, then the state is re-equilibrated at unchanged pressure. The last parcel is volume-limited.
   * This independent mesh probes a declared slow-equilibrium limit; it does not establish the mixing/withdrawal
   * protocol of an unspecified laboratory.
   * 
   * @param original frozen sample
   * @param cap maximum parcel moles per initial mole
   * @return rows of complete cell inventories
   * @throws Exception if precision, positive inventories or closure fails
   */
  private static List<Map<String, Object>> replay(SystemInterface original, double cap) throws Exception {
    SystemInterface f = original.clone();
    double[] initial = inventory(f);
    double[] cumulative = new double[initial.length];
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    double target = 0;
    for (int row = 0; row < PRESSURES.length; row++) {
      f.setPressure(PRESSURES[row]);
      flash(f);
      if (row == 0)
        target = f.getCorrectedVolume();
      int steps = 0;
      while (Math.abs(f.getCorrectedVolume() / target - 1) > 1e-12) {
        if (++steps > 200000)
          throw new IllegalStateException("Iteration bound");
        double excess = f.getCorrectedVolume() - target;
        if (excess < 0 || !f.hasPhaseType("gas"))
          throw new IllegalStateException("Overshoot or absent gas");
        double gas = f.getPhase("gas").getNumberOfMolesInPhase();
        double volume = f.getPhase("gas").getCorrectedVolume();
        double dn = Math.min(cap, Math.min(.95 * gas, excess / volume * gas));
        double[] removal = new double[initial.length];
        for (int i = 0; i < removal.length; i++)
          removal[i] = dn * f.getPhase("gas").getComponent(i).getx();
        for (int i = 0; i < removal.length; i++) {
          f.addComponent(i, -removal[i]);
          cumulative[i] += removal[i];
        }
        flash(f);
      }
      double[] actual = inventory(f);
      for (int i = 0; i < actual.length; i++)
        if (Math.abs(initial[i] - actual[i] - cumulative[i]) > 1e-10)
          throw new IllegalStateException("Component closure");
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("pressureBara", PRESSURES[row]);
      out.put("retainedMoles", actual);
      out.put("relativeVolume", f.getCorrectedVolume() / target);
      out.put("parcels", steps);
      out.put("oilPresent", f.hasPhaseType("oil"));
      rows.add(out);
    }
    return rows;
  }

  /**
   * Executes the frozen binary control and ternary sensitivity matrix.
   * 
   * @param args output JSON filename
   * @throws Exception on any unsupported or unclosed state
   */
  public static void main(String[] args) throws Exception {
    precision = ConstantVolumeDepletion.class.getDeclaredMethod("refineFixedCellEquilibrium", SystemInterface.class);
    precision.setAccessible(true);
    List<Map<String, Object>> all = new ArrayList<Map<String, Object>>();
    for (boolean ternary : new boolean[] {false, true}) {
      for (double temperature : ternary ? new double[] {300, 325, 350} : new double[] {275}) {
        SystemInterface f = sample(temperature, ternary);
        Map<String, Object> entry = new LinkedHashMap<String, Object>();
        entry.put("temperatureK", temperature);
        entry.put("ternary", ternary);
        entry.put("names", f.getComponentNames());
        DepletionSnapshot[] nativeRows = new ConstantVolumeDepletion(f)
            .runDryPhaseChangingFixedCellDepletion(temperature, PRESSURES).getSnapshots();
        List<double[]> nativeInventory = new ArrayList<double[]>();
        for (DepletionSnapshot row : nativeRows)
          nativeInventory.add(inventory(row.getRetainedFluid()));
        entry.put("nativeInventories", nativeInventory);
        List<Map<String, Object>> meshes = new ArrayList<Map<String, Object>>();
        for (double cap : new double[] {.01, .005, .0025, .00125}) {
          Map<String, Object> mesh = new LinkedHashMap<String, Object>();
          mesh.put("parcelCapMol", cap);
          try {
            mesh.put("rows", replay(f, cap));
          } catch (Exception ex) {
            mesh.put("failure", ex.toString());
          }
          meshes.add(mesh);
        }
        entry.put("meshes", meshes);
        all.add(entry);
      }
    }
    Files.write(Paths.get(args[0]), new Gson().toJson(all).getBytes(StandardCharsets.UTF_8));
    Files.write(Paths.get(args[0] + ".diagnostics.json"),
        new Gson().toJson(diagnostics).getBytes(StandardCharsets.UTF_8));
  }
}
