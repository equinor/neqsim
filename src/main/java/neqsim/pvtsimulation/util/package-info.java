/**
 * Utility classes for PVT simulations.
 *
 * <p>
 * This package provides:
 * <ul>
 * <li>{@link neqsim.pvtsimulation.util.BlackOilCorrelations} - Industry-standard black oil
 * correlations (Standing, Vasquez-Beggs, Glaso, Beggs-Robinson, Lee-Gonzalez-Eakin) with support
 * for field units (psia, °F, scf/STB) and SI units (bara, °C, Sm³/Sm³)</li>
 * <li>{@link neqsim.pvtsimulation.util.BlackOilUnits} - Unit system enum for converting between
 * field, SI, and NeqSim internal units</li>
 * <li>{@link neqsim.pvtsimulation.util.PVTReportGenerator} - PVT report generation utilities for
 * CCE, CVD, DLE, and separator test results</li>
 * <li>{@link neqsim.pvtsimulation.util.MMPCalculator} - Minimum Miscibility Pressure (MMP)
 * calculation using slim-tube and key tie-line methods</li>
 * </ul>
 *
 * <h2>Black Oil Correlations Usage</h2>
 * 
 * <pre>{@code
 * // Field units (default)
 * double Pb = BlackOilCorrelations.bubblePointStanding(500, 0.8, 35, 200, true);
 *
 * // SI units (bara, °C, Sm³/Sm³)
 * double Pb_si = BlackOilCorrelations.bubblePointStandingSI(89, 0.8, 35, 93);
 *
 * // Explicit unit system
 * double Pb = BlackOilCorrelations.bubblePointStanding(89, 0.8, 35, 93, BlackOilUnits.SI);
 * }</pre>
 */
package neqsim.pvtsimulation.util;
