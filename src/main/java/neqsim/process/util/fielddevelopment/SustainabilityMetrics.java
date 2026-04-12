package neqsim.process.util.fielddevelopment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Sustainability and life-cycle metrics tracker for biorefinery processes.
 *
 * <p>
 * Computes CO2-equivalent emissions, carbon intensity, renewable energy fraction, and other
 * sustainability key performance indicators (KPIs) from process simulation results. Designed to
 * integrate with NeqSim process equipment and the emissions agent infrastructure.
 * </p>
 *
 * <h2>Tracked Metrics</h2>
 *
 * <table>
 * <caption>Sustainability metrics computed by this class</caption>
 * <tr>
 * <th>Metric</th>
 * <th>Unit</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>CO2 equivalent emissions</td>
 * <td>tCO2eq/yr</td>
 * <td>Total GHG footprint including CH4 and N2O</td>
 * </tr>
 * <tr>
 * <td>Carbon intensity</td>
 * <td>kgCO2eq/MWh</td>
 * <td>Emissions per unit of energy produced</td>
 * </tr>
 * <tr>
 * <td>Renewable energy fraction</td>
 * <td>%</td>
 * <td>Fraction of total energy from renewable sources</td>
 * </tr>
 * <tr>
 * <td>Fossil fuel displacement</td>
 * <td>tCO2eq/yr</td>
 * <td>GHG avoided by replacing fossil fuel</td>
 * </tr>
 * <tr>
 * <td>Net carbon balance</td>
 * <td>tCO2eq/yr</td>
 * <td>Net emissions after credits (biogenic carbon neutral)</td>
 * </tr>
 * </table>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * SustainabilityMetrics metrics = new SustainabilityMetrics();
 * metrics.setBiogasProductionNm3PerYear(4.0e6);
 * metrics.setMethaneContentFraction(0.60);
 * metrics.setElectricityProductionMWhPerYear(8000.0);
 * metrics.setHeatProductionMWhPerYear(10000.0);
 * metrics.setParasiticElectricityMWhPerYear(1200.0);
 * metrics.setMethaneSlipPercent(1.5);
 * metrics.setFossilReferenceEmissionFactor(0.450); // kgCO2/kWh natural gas
 * metrics.calculate();
 *
 * double carbonIntensity = metrics.getCarbonIntensityKgCO2PerMWh();
 * double fossilDisplaced = metrics.getFossilFuelDisplacementTCO2PerYear();
 * double netBalance = metrics.getNetCarbonBalanceTCO2PerYear();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SustainabilityMetrics implements Serializable {
  private static final long serialVersionUID = 1001L;

  /**
   * Emission source type for itemised tracking.
   */
  public enum EmissionSource {
    /** Methane slip (uncombusted CH4). */
    METHANE_SLIP,
    /** Flaring of excess biogas. */
    FLARING,
    /** Grid electricity import. */
    GRID_ELECTRICITY,
    /** Diesel for transport or machinery. */
    DIESEL_TRANSPORT,
    /** N2O from digestate application. */
    N2O_DIGESTATE,
    /** Upstream feedstock transport emissions. */
    FEEDSTOCK_TRANSPORT,
    /** Other custom emission source. */
    CUSTOM
  }

  // ── Global warming potentials (100-yr, IPCC AR6) ──
  /** GWP100 for methane. */
  private static final double GWP_CH4 = 29.8;
  /** GWP100 for nitrous oxide. */
  private static final double GWP_N2O = 273.0;

  // ── Energy balance inputs ──
  /** Biogas production in Nm3/year. */
  private double biogasProductionNm3PerYear = 0.0;
  /** Methane content fraction (0-1) in biogas. */
  private double methaneContentFraction = 0.60;
  /** Gross electricity production in MWh/year. */
  private double electricityProductionMWhPerYear = 0.0;
  /** Gross heat production in MWh/year. */
  private double heatProductionMWhPerYear = 0.0;
  /** Parasitic electricity consumption in MWh/year. */
  private double parasiticElectricityMWhPerYear = 0.0;
  /** Parasitic heat consumption in MWh/year. */
  private double parasiticHeatMWhPerYear = 0.0;
  /** External (grid) electricity imported in MWh/year. */
  private double importedElectricityMWhPerYear = 0.0;
  /** Production of biomethane for grid injection in Nm3/year. */
  private double biomethaneProductionNm3PerYear = 0.0;

  // ── Emission inputs ──
  /** Methane slip as percent of total methane produced. */
  private double methaneSlipPercent = 1.0;
  /** Diesel consumption for feedstock transport in litres/year. */
  private double dieselConsumptionLPerYear = 0.0;
  /** N2O emission from digestate field application as fraction of N content. */
  private double n2oEmissionFraction = 0.01;
  /** Nitrogen content in digestate in kg N/year. */
  private double digestateNitrogenKgPerYear = 0.0;
  /** Grid electricity emission factor in kgCO2/kWh. */
  private double gridElectricityEmissionFactor = 0.0;
  /** Feedstock transport distance in km. */
  private double feedstockTransportDistanceKm = 0.0;
  /** Feedstock transport tonnage per year. */
  private double feedstockTransportTonnesPerYear = 0.0;
  /** Emission factor for transport in kgCO2/(tonne*km). */
  private double transportEmissionFactor = 0.062;

  // ── Fossil reference ──
  /** Emission factor for displaced fossil energy in kgCO2/kWh. */
  private double fossilReferenceEmissionFactor = 0.450;
  /** Emission factor for displaced fossil heat in kgCO2/kWh. */
  private double fossilHeatEmissionFactor = 0.250;
  /** Emission factor for displaced fossil gas (natural gas) in kgCO2/Nm3. */
  private double fossilGasEmissionFactor = 2.0;

  // ── Custom emission sources ──
  /** List of custom emission entries. */
  private List<EmissionEntry> customEmissions = new ArrayList<EmissionEntry>();

  // ── Results ──
  /** Total CO2-equivalent emissions in tCO2eq/year. */
  private double totalEmissionsTCO2eqPerYear = 0.0;
  /** Carbon intensity in kgCO2eq/MWh. */
  private double carbonIntensityKgCO2PerMWh = 0.0;
  /** Renewable energy fraction (0-1). */
  private double renewableEnergyFraction = 0.0;
  /** Net energy production in MWh/year. */
  private double netEnergyProductionMWhPerYear = 0.0;
  /** Fossil fuel displacement in tCO2eq/year. */
  private double fossilFuelDisplacementTCO2PerYear = 0.0;
  /** Net carbon balance in tCO2eq/year. */
  private double netCarbonBalanceTCO2PerYear = 0.0;
  /** Energy return on investment (EROI). */
  private double energyReturnOnInvestment = 0.0;
  /** Whether metrics have been calculated. */
  private boolean calculated = false;

  /**
   * Creates a new sustainability metrics tracker.
   */
  public SustainabilityMetrics() {
    // default constructor
  }

  // ── Input setters ──

  /**
   * Sets the annual biogas production.
   *
   * @param nm3PerYear biogas production in Nm3/year
   */
  public void setBiogasProductionNm3PerYear(double nm3PerYear) {
    this.biogasProductionNm3PerYear = nm3PerYear;
  }

  /**
   * Sets the methane content fraction.
   *
   * @param fraction methane volume fraction in biogas (0-1)
   */
  public void setMethaneContentFraction(double fraction) {
    this.methaneContentFraction = Math.max(0.0, Math.min(1.0, fraction));
  }

  /**
   * Sets the annual electricity production.
   *
   * @param mwhPerYear electricity production in MWh/year
   */
  public void setElectricityProductionMWhPerYear(double mwhPerYear) {
    this.electricityProductionMWhPerYear = mwhPerYear;
  }

  /**
   * Sets the annual heat production.
   *
   * @param mwhPerYear heat production in MWh/year
   */
  public void setHeatProductionMWhPerYear(double mwhPerYear) {
    this.heatProductionMWhPerYear = mwhPerYear;
  }

  /**
   * Sets the parasitic electricity consumption.
   *
   * @param mwhPerYear parasitic electricity in MWh/year
   */
  public void setParasiticElectricityMWhPerYear(double mwhPerYear) {
    this.parasiticElectricityMWhPerYear = mwhPerYear;
  }

  /**
   * Sets the parasitic heat consumption.
   *
   * @param mwhPerYear parasitic heat in MWh/year
   */
  public void setParasiticHeatMWhPerYear(double mwhPerYear) {
    this.parasiticHeatMWhPerYear = mwhPerYear;
  }

  /**
   * Sets the imported grid electricity.
   *
   * @param mwhPerYear imported grid electricity in MWh/year
   */
  public void setImportedElectricityMWhPerYear(double mwhPerYear) {
    this.importedElectricityMWhPerYear = mwhPerYear;
  }

  /**
   * Sets the biomethane production for grid injection.
   *
   * @param nm3PerYear biomethane production in Nm3/year
   */
  public void setBiomethaneProductionNm3PerYear(double nm3PerYear) {
    this.biomethaneProductionNm3PerYear = nm3PerYear;
  }

  /**
   * Sets the methane slip percentage.
   *
   * @param percent methane slip as percent of total methane produced
   */
  public void setMethaneSlipPercent(double percent) {
    this.methaneSlipPercent = Math.max(0.0, percent);
  }

  /**
   * Sets diesel consumption for transport.
   *
   * @param litresPerYear diesel consumption in litres/year
   */
  public void setDieselConsumptionLPerYear(double litresPerYear) {
    this.dieselConsumptionLPerYear = litresPerYear;
  }

  /**
   * Sets the N2O emission fraction from digestate application.
   *
   * @param fraction fraction of nitrogen emitted as N2O
   */
  public void setN2OEmissionFraction(double fraction) {
    this.n2oEmissionFraction = fraction;
  }

  /**
   * Sets the nitrogen content in digestate.
   *
   * @param kgNPerYear nitrogen in digestate in kg N/year
   */
  public void setDigestateNitrogenKgPerYear(double kgNPerYear) {
    this.digestateNitrogenKgPerYear = kgNPerYear;
  }

  /**
   * Sets the grid electricity emission factor.
   *
   * @param kgCO2PerKWh emission factor in kgCO2/kWh
   */
  public void setGridElectricityEmissionFactor(double kgCO2PerKWh) {
    this.gridElectricityEmissionFactor = kgCO2PerKWh;
  }

  /**
   * Sets feedstock transport parameters.
   *
   * @param distanceKm one-way transport distance in km
   * @param tonnesPerYear feedstock tonnage per year
   */
  public void setFeedstockTransport(double distanceKm, double tonnesPerYear) {
    this.feedstockTransportDistanceKm = distanceKm;
    this.feedstockTransportTonnesPerYear = tonnesPerYear;
  }

  /**
   * Sets the transport emission factor.
   *
   * @param kgCO2PerTonneKm emission factor in kgCO2/(tonne*km)
   */
  public void setTransportEmissionFactor(double kgCO2PerTonneKm) {
    this.transportEmissionFactor = kgCO2PerTonneKm;
  }

  /**
   * Sets the fossil reference emission factor for electricity.
   *
   * @param kgCO2PerKWh emission factor in kgCO2/kWh
   */
  public void setFossilReferenceEmissionFactor(double kgCO2PerKWh) {
    this.fossilReferenceEmissionFactor = kgCO2PerKWh;
  }

  /**
   * Sets the fossil heat emission factor.
   *
   * @param kgCO2PerKWh emission factor in kgCO2/kWh
   */
  public void setFossilHeatEmissionFactor(double kgCO2PerKWh) {
    this.fossilHeatEmissionFactor = kgCO2PerKWh;
  }

  /**
   * Sets the fossil gas emission factor.
   *
   * @param kgCO2PerNm3 emission factor in kgCO2/Nm3
   */
  public void setFossilGasEmissionFactor(double kgCO2PerNm3) {
    this.fossilGasEmissionFactor = kgCO2PerNm3;
  }

  /**
   * Adds a custom emission source.
   *
   * @param source emission source type
   * @param description description of the emission
   * @param tCO2eqPerYear annual CO2-equivalent emissions in tonnes
   */
  public void addEmission(EmissionSource source, String description, double tCO2eqPerYear) {
    customEmissions.add(new EmissionEntry(source, description, tCO2eqPerYear));
  }

  /**
   * Clears all custom emission entries.
   */
  public void clearCustomEmissions() {
    customEmissions.clear();
  }

  // ── Calculation ──

  /**
   * Calculates all sustainability metrics.
   */
  public void calculate() {
    // ── 1. Total methane produced ──
    double methaneNm3PerYear = biogasProductionNm3PerYear * methaneContentFraction;
    double methaneEnergyMWhPerYear = methaneNm3PerYear * 9.97 / 1000.0; // 9.97 kWh/Nm3 CH4

    // ── 2. Net energy production ──
    double netElectricity = electricityProductionMWhPerYear - parasiticElectricityMWhPerYear;
    double netHeat = heatProductionMWhPerYear - parasiticHeatMWhPerYear;
    double biomethaneEnergyMWh = biomethaneProductionNm3PerYear * 9.97 / 1000.0;
    netEnergyProductionMWhPerYear = netElectricity + netHeat + biomethaneEnergyMWh;

    // ── 3. Renewable energy fraction ──
    double totalEnergyInput = methaneEnergyMWhPerYear + importedElectricityMWhPerYear;
    renewableEnergyFraction =
        totalEnergyInput > 0 ? methaneEnergyMWhPerYear / totalEnergyInput : 1.0;

    // ── 4. EROI ──
    double totalEnergyOutput =
        electricityProductionMWhPerYear + heatProductionMWhPerYear + biomethaneEnergyMWh;
    double totalEnergyConsumed =
        parasiticElectricityMWhPerYear + parasiticHeatMWhPerYear + importedElectricityMWhPerYear;
    energyReturnOnInvestment =
        totalEnergyConsumed > 0 ? totalEnergyOutput / totalEnergyConsumed : 0.0;

    // ── 5. Emissions calculation (tCO2eq/year) ──
    double emissionsMethaneSlip =
        methaneNm3PerYear * (methaneSlipPercent / 100.0) * 0.678 * GWP_CH4 / 1000.0;
    // 0.678 kg/Nm3 methane density at 0C

    double emissionsGridElectricity = importedElectricityMWhPerYear * gridElectricityEmissionFactor; // MWh
                                                                                                     // *
                                                                                                     // kgCO2/kWh
                                                                                                     // ->
                                                                                                     // tCO2

    double emissionsDiesel = dieselConsumptionLPerYear * 2.68 / 1000.0; // 2.68 kgCO2/L diesel

    double emissionsN2O =
        digestateNitrogenKgPerYear * n2oEmissionFraction * (44.0 / 28.0) * GWP_N2O / 1000.0;

    double emissionsTransport = feedstockTransportDistanceKm * feedstockTransportTonnesPerYear
        * transportEmissionFactor * 2.0 / 1000.0; // round trip factor 2

    double emissionsCustom = 0.0;
    for (EmissionEntry entry : customEmissions) {
      emissionsCustom += entry.tCO2eqPerYear;
    }

    totalEmissionsTCO2eqPerYear = emissionsMethaneSlip + emissionsGridElectricity + emissionsDiesel
        + emissionsN2O + emissionsTransport + emissionsCustom;

    // ── 6. Carbon intensity ──
    carbonIntensityKgCO2PerMWh = netEnergyProductionMWhPerYear > 0
        ? totalEmissionsTCO2eqPerYear * 1000.0 / netEnergyProductionMWhPerYear
        : 0.0;

    // ── 7. Fossil fuel displacement ──
    double displacedElectricity = netElectricity * fossilReferenceEmissionFactor; // MWh * kgCO2/kWh
    double displacedHeat = netHeat * fossilHeatEmissionFactor;
    double displacedGas = biomethaneProductionNm3PerYear * fossilGasEmissionFactor / 1000.0;
    fossilFuelDisplacementTCO2PerYear = displacedElectricity + displacedHeat + displacedGas;

    // ── 8. Net carbon balance ──
    netCarbonBalanceTCO2PerYear = totalEmissionsTCO2eqPerYear - fossilFuelDisplacementTCO2PerYear;

    calculated = true;
  }

  // ── Results access ──

  /**
   * Returns total CO2-equivalent emissions.
   *
   * @return total emissions in tCO2eq/year
   */
  public double getTotalEmissionsTCO2eqPerYear() {
    return totalEmissionsTCO2eqPerYear;
  }

  /**
   * Returns the carbon intensity.
   *
   * @return carbon intensity in kgCO2eq/MWh
   */
  public double getCarbonIntensityKgCO2PerMWh() {
    return carbonIntensityKgCO2PerMWh;
  }

  /**
   * Returns the renewable energy fraction.
   *
   * @return renewable energy fraction (0-1)
   */
  public double getRenewableEnergyFraction() {
    return renewableEnergyFraction;
  }

  /**
   * Returns the net energy production.
   *
   * @return net energy in MWh/year
   */
  public double getNetEnergyProductionMWhPerYear() {
    return netEnergyProductionMWhPerYear;
  }

  /**
   * Returns the fossil fuel displacement.
   *
   * @return fossil fuel displacement in tCO2eq/year
   */
  public double getFossilFuelDisplacementTCO2PerYear() {
    return fossilFuelDisplacementTCO2PerYear;
  }

  /**
   * Returns the net carbon balance.
   *
   * @return net carbon balance in tCO2eq/year (negative means net carbon saving)
   */
  public double getNetCarbonBalanceTCO2PerYear() {
    return netCarbonBalanceTCO2PerYear;
  }

  /**
   * Returns the energy return on investment.
   *
   * @return EROI ratio
   */
  public double getEnergyReturnOnInvestment() {
    return energyReturnOnInvestment;
  }

  /**
   * Returns whether metrics have been calculated.
   *
   * @return true if calculated
   */
  public boolean isCalculated() {
    return calculated;
  }

  /**
   * Returns a results map.
   *
   * @return map of metric names to values
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("totalEmissions_tCO2eq_per_year", totalEmissionsTCO2eqPerYear);
    results.put("carbonIntensity_kgCO2eq_per_MWh", carbonIntensityKgCO2PerMWh);
    results.put("renewableEnergyFraction", renewableEnergyFraction);
    results.put("netEnergyProduction_MWh_per_year", netEnergyProductionMWhPerYear);
    results.put("fossilFuelDisplacement_tCO2eq_per_year", fossilFuelDisplacementTCO2PerYear);
    results.put("netCarbonBalance_tCO2eq_per_year", netCarbonBalanceTCO2PerYear);
    results.put("energyReturnOnInvestment", energyReturnOnInvestment);
    results.put("methaneSlipPercent", methaneSlipPercent);

    // Input summary
    Map<String, Object> inputs = new LinkedHashMap<String, Object>();
    inputs.put("biogasProduction_Nm3_per_year", biogasProductionNm3PerYear);
    inputs.put("methaneContentFraction", methaneContentFraction);
    inputs.put("electricityProduction_MWh_per_year", electricityProductionMWhPerYear);
    inputs.put("heatProduction_MWh_per_year", heatProductionMWhPerYear);
    inputs.put("parasiticElectricity_MWh_per_year", parasiticElectricityMWhPerYear);
    inputs.put("biomethaneProduction_Nm3_per_year", biomethaneProductionNm3PerYear);
    results.put("inputs", inputs);

    // Emission breakdown
    if (!customEmissions.isEmpty()) {
      List<Map<String, Object>> emissionList = new ArrayList<Map<String, Object>>();
      for (EmissionEntry entry : customEmissions) {
        Map<String, Object> eMap = new LinkedHashMap<String, Object>();
        eMap.put("source", entry.source.name());
        eMap.put("description", entry.description);
        eMap.put("tCO2eq_per_year", entry.tCO2eqPerYear);
        emissionList.add(eMap);
      }
      results.put("customEmissions", emissionList);
    }

    return results;
  }

  /**
   * Returns a JSON string of all metrics.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getResults());
  }

  /**
   * Internal emission entry record.
   */
  private static class EmissionEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Emission source type. */
    private final EmissionSource source;
    /** Description of the emission. */
    private final String description;
    /** Annual CO2-equivalent emissions in tonnes. */
    private final double tCO2eqPerYear;

    /**
     * Creates an emission entry.
     *
     * @param source emission source type
     * @param description description
     * @param tCO2eqPerYear annual emissions in tCO2eq
     */
    EmissionEntry(EmissionSource source, String description, double tCO2eqPerYear) {
      this.source = source;
      this.description = description;
      this.tCO2eqPerYear = tCO2eqPerYear;
    }
  }
}
