/**
 * Sustainability and emissions tracking for process systems.
 *
 * <p>
 * This package provides tools for environmental impact assessment, emissions accounting, and
 * regulatory compliance. Key capabilities:
 * </p>
 *
 * <ul>
 * <li><b>CO2e Emissions:</b> Calculate greenhouse gas emissions per equipment and system-wide</li>
 * <li><b>Energy Tracking:</b> Monitor power consumption and heat integration opportunities</li>
 * <li><b>Flaring Minimization:</b> Track and report flaring emissions for reduction
 * initiatives</li>
 * <li><b>Regulatory Export:</b> Generate reports for EU ETS, EPA, and other frameworks</li>
 * </ul>
 *
 * <h2>Emission Categories:</h2>
 * <ul>
 * <li><b>Direct (Scope 1):</b> Flaring, combustion, venting</li>
 * <li><b>Indirect (Scope 2):</b> Electricity consumption for compression, pumping, cooling</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * ProcessSystem process = new ProcessSystem();
 * // ... configure process with compressors, heaters, flares, etc. ...
 * process.run();
 *
 * EmissionsTracker tracker = new EmissionsTracker(process);
 * tracker.setGridEmissionFactor(0.3); // kg CO2 per kWh (region-specific)
 *
 * EmissionsReport report = tracker.calculateEmissions();
 * System.out.println(report.getSummary());
 *
 * // Export for regulatory submission
 * report.exportToCSV("q4_2024_emissions.csv");
 * </pre>
 *
 * @see neqsim.process.sustainability.EmissionsTracker
 * @author ESOL
 * @version 1.0
 */
package neqsim.process.sustainability;
