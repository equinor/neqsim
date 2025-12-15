/**
 * Safety envelope calculations for flow assurance and process safety.
 *
 * <p>
 * This package provides tools for calculating operational safety envelopes including:
 * <ul>
 * <li>{@link neqsim.process.safety.envelope.SafetyEnvelope} - P-T envelope with interpolation and
 * safety checks</li>
 * <li>{@link neqsim.process.safety.envelope.SafetyEnvelopeCalculator} - Calculator for hydrate,
 * WAX, CO2 freezing, MDMT, and phase envelopes</li>
 * </ul>
 *
 * <p>
 * Supported envelope types:
 * <ul>
 * <li><b>Hydrate</b> - Gas hydrate formation temperature vs pressure</li>
 * <li><b>WAX</b> - Wax appearance temperature (WAT) vs pressure</li>
 * <li><b>CO2 Freezing</b> - CO2 solid formation temperature vs pressure</li>
 * <li><b>MDMT</b> - Minimum Design Metal Temperature for blowdown scenarios</li>
 * <li><b>Phase Envelope</b> - Bubble/dew point curves</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * 
 * <pre>
 * SystemInterface gas = new SystemSrkEos(280.0, 50.0);
 * gas.addComponent("methane", 0.85);
 * gas.addComponent("CO2", 0.10);
 * gas.addComponent("water", 0.05);
 * 
 * SafetyEnvelopeCalculator calc = new SafetyEnvelopeCalculator(gas);
 * SafetyEnvelope hydrate = calc.calculateHydrateEnvelope(1.0, 150.0, 20);
 * SafetyEnvelope co2 = calc.calculateCO2FreezingEnvelope(1.0, 100.0, 15);
 * 
 * boolean safe = hydrate.isOperatingPointSafe(50.0, 290.0);
 * </pre>
 *
 * @see neqsim.process.safety.risk Probabilistic risk analysis
 * @see neqsim.process.safety.release Source term generation
 */
package neqsim.process.safety.envelope;
