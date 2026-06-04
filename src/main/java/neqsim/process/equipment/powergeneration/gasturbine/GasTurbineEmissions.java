package neqsim.process.equipment.powergeneration.gasturbine;

import java.io.Serializable;
import neqsim.thermo.system.SystemInterface;

/**
 * Compute combustion emissions for a gas turbine running on a hydrocarbon fuel stream.
 *
 * <p>
 * CO2 is computed from a full carbon balance on the fuel composition (every component's carbon
 * atoms become CO2 at complete combustion). NOx is estimated from a vendor ppm @ 15 % O2 figure
 * converted to a mass flow on the actual exhaust. Methane slip is an optional flat percentage of
 * the fuel methane content.
 * </p>
 *
 * <p>
 * The class makes no assumption about the air-side stoichiometry; it just needs the fuel
 * composition and an exhaust mass flow.
 * </p>
 *
 * @author neqsim
 * @version $Id: $Id
 */
public class GasTurbineEmissions implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Molar mass of CO2 [kg/kmol]. */
  public static final double MW_CO2 = 44.01;
  /** Molar mass of NOx, expressed as NO2 [kg/kmol]. */
  public static final double MW_NO2 = 46.0055;
  /** Molar mass of methane [kg/kmol]. */
  public static final double MW_CH4 = 16.043;

  private double methaneSlipFraction = 0.0;

  /**
   * Set the methane slip — fraction of fuel methane that passes through unburned.
   *
   * @param slipFraction methane slip fraction (0–1)
   */
  public void setMethaneSlipFraction(double slipFraction) {
    this.methaneSlipFraction = slipFraction;
  }

  /**
   * Get the methane slip fraction.
   *
   * @return methane slip fraction (0–1)
   */
  public double getMethaneSlipFraction() {
    return methaneSlipFraction;
  }

  /**
   * Compute the CO2 mass flow from a hydrocarbon fuel stream.
   *
   * @param fuel fuel system
   * @param fuelMolarFlowMolPerS fuel molar flow [mol/s]
   * @return CO2 mass flow [kg/s]
   */
  public double computeCO2KgPerS(SystemInterface fuel, double fuelMolarFlowMolPerS) {
    if (fuel == null || fuelMolarFlowMolPerS <= 0.0) {
      return 0.0;
    }
    double carbonAtomsPerMolFuel = 0.0;
    int n = fuel.getNumberOfComponents();
    for (int i = 0; i < n; i++) {
      double z = fuel.getComponent(i).getz();
      double carbon = countCarbon(fuel.getComponent(i).getComponentName());
      carbonAtomsPerMolFuel += z * carbon;
    }
    double effectiveCarbon = carbonAtomsPerMolFuel;
    // subtract slipped methane (1 C atom per CH4 molecule)
    if (methaneSlipFraction > 0.0) {
      double methaneFraction = 0.0;
      for (int i = 0; i < n; i++) {
        if ("methane".equalsIgnoreCase(fuel.getComponent(i).getComponentName())) {
          methaneFraction = fuel.getComponent(i).getz();
          break;
        }
      }
      effectiveCarbon -= methaneSlipFraction * methaneFraction;
    }
    if (effectiveCarbon < 0.0) {
      effectiveCarbon = 0.0;
    }
    double co2MolPerS = effectiveCarbon * fuelMolarFlowMolPerS;
    // mol → kmol → kg
    return co2MolPerS * 1.0e-3 * MW_CO2;
  }

  /**
   * Compute the methane slip mass flow.
   *
   * @param fuel fuel system
   * @param fuelMolarFlowMolPerS fuel molar flow [mol/s]
   * @return methane slip mass flow [kg/s]
   */
  public double computeMethaneSlipKgPerS(SystemInterface fuel, double fuelMolarFlowMolPerS) {
    if (fuel == null || methaneSlipFraction <= 0.0 || fuelMolarFlowMolPerS <= 0.0) {
      return 0.0;
    }
    double methaneFraction = 0.0;
    int n = fuel.getNumberOfComponents();
    for (int i = 0; i < n; i++) {
      if ("methane".equalsIgnoreCase(fuel.getComponent(i).getComponentName())) {
        methaneFraction = fuel.getComponent(i).getz();
        break;
      }
    }
    return methaneFraction * methaneSlipFraction * fuelMolarFlowMolPerS * 1.0e-3 * MW_CH4;
  }

  /**
   * Compute the NOx mass flow expressed as NO2 from a ppmv-on-exhaust figure.
   *
   * @param noxPpm NOx concentration [ppmv at 15 % O2]
   * @param exhaustMassFlowKgPerS exhaust mass flow [kg/s]
   * @param exhaustMolarMassKgPerKmol exhaust gas molar mass [kg/kmol] (typical ≈ 28.7)
   * @return NOx mass flow [kg/s]
   */
  public double computeNOxKgPerS(double noxPpm, double exhaustMassFlowKgPerS,
      double exhaustMolarMassKgPerKmol) {
    if (noxPpm <= 0.0 || exhaustMassFlowKgPerS <= 0.0 || exhaustMolarMassKgPerKmol <= 0.0) {
      return 0.0;
    }
    double exhaustKmolPerS = exhaustMassFlowKgPerS / exhaustMolarMassKgPerKmol;
    double noxKmolPerS = exhaustKmolPerS * noxPpm * 1.0e-6;
    return noxKmolPerS * MW_NO2;
  }

  /**
   * Count carbon atoms in a NeqSim component name. Supports nC1–nC30 paraffin naming,
   * methane–decane longhand, CO2, and CO. Other components return 0.
   *
   * @param componentName NeqSim component name
   * @return number of carbon atoms (0 if not a hydrocarbon)
   */
  static double countCarbon(String componentName) {
    if (componentName == null) {
      return 0.0;
    }
    String n = componentName.trim().toLowerCase();
    if (n.equals("co2") || n.equals("co")) {
      return 1.0;
    }
    if (n.equals("methane")) {
      return 1.0;
    }
    if (n.equals("ethane")) {
      return 2.0;
    }
    if (n.equals("propane")) {
      return 3.0;
    }
    if (n.equals("n-butane") || n.equals("i-butane") || n.equals("ibutane")
        || n.equals("nbutane")) {
      return 4.0;
    }
    if (n.equals("n-pentane") || n.equals("i-pentane") || n.equals("ipentane")
        || n.equals("npentane")) {
      return 5.0;
    }
    if (n.equals("n-hexane") || n.equals("nhexane")) {
      return 6.0;
    }
    if (n.equals("n-heptane")) {
      return 7.0;
    }
    if (n.equals("n-octane")) {
      return 8.0;
    }
    if (n.equals("n-nonane")) {
      return 9.0;
    }
    if (n.equals("n-decane")) {
      return 10.0;
    }
    // Plus / pseudo fractions like "C7+", "C20", "nC15"
    if (n.startsWith("c") && n.length() >= 2) {
      StringBuilder digits = new StringBuilder();
      for (int i = 1; i < n.length(); i++) {
        char c = n.charAt(i);
        if (Character.isDigit(c)) {
          digits.append(c);
        } else if (digits.length() > 0) {
          break;
        }
      }
      if (digits.length() > 0) {
        try {
          return Double.parseDouble(digits.toString());
        } catch (NumberFormatException ignore) {
          return 0.0;
        }
      }
    }
    return 0.0;
  }
}
