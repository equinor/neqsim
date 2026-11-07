package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseWaterIAPWS;
import neqsim.thermo.util.steam.SeawaterProperties;

/**
 * Fixed-salinity bulk seawater utility fluid using IF97 water plus IAPWS-08 saline Gibbs energy.
 *
 * <p>
 * One nominal water component represents the entire solution mass. Nominal moles use the water molar mass; mass flow,
 * enthalpy and heat capacity are per kg of solution. This model is intended for indirect heat exchange and
 * same-salinity utility circuits. It does not track salt as a component and must not be used for dilution, mixing
 * different salinities, evaporation, gas dissolution, pH or mineral scale. Use electrolyte models for those operations.
 * Inputs use kelvin, bara and Reference-Composition Salinity in g/kg solution.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SystemSeawater extends SystemWaterIF97 {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Creates normal seawater at 298.15 K and 1.01325 bara. */
  public SystemSeawater() {
    this(298.15, 1.01325, 35.16504);
  }

  /**
   * Creates a fixed-salinity utility fluid with one nominal mole of bulk solution.
   *
   * @param temperature temperature [K]
   * @param pressure absolute pressure [bara]
   * @param salinity reference-composition salinity [g/kg solution]
   * @throws IllegalArgumentException if inputs are outside the cooling envelope
   */
  public SystemSeawater(double temperature, double pressure, double salinity) {
    super(temperature, pressure);
    SeawaterProperties.validate(temperature, pressure, salinity);
    modelName = "IAPWS-IF97 + IAPWS-08 seawater";
    setSalinity(salinity);
  }

  /**
   * Sets the composition parameter of a bulk solution. Reinitialize and rerun dependent equipment afterwards.
   *
   * @param salinity reference-composition salinity [g/kg solution]
   * @throws IllegalArgumentException if the current state or salinity is outside the cooling envelope
   */
  public void setSalinity(double salinity) {
    SeawaterProperties.validate(getTemperature(), getPressure(), salinity);
    for (PhaseInterface phase : phaseArray) {
      if (phase instanceof PhaseWaterIAPWS) {
        ((PhaseWaterIAPWS) phase).setSalinity(salinity);
      }
    }
  }

  /**
   * Returns the fixed-salinity composition parameter.
   *
   * @return reference-composition salinity [g/kg solution]
   */
  public double getSalinity() {
    return ((PhaseWaterIAPWS) getPhase(0)).getSalinity();
  }

  /** {@inheritDoc} */
  @Override
  public SystemSeawater clone() {
    return (SystemSeawater) super.clone();
  }
}
