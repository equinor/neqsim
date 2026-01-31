/**
 * Multi-Asset Portfolio Risk Analysis Package.
 *
 * <p>
 * This package provides portfolio-level risk analysis capabilities for multiple oil and gas assets,
 * including common cause failure modeling, correlated events, and diversification analysis.
 * Essential for corporate risk management and insurance negotiations.
 * </p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.safety.risk.portfolio.PortfolioRiskAnalyzer} - Main analyzer for
 * multi-asset simulation</li>
 * <li>{@link neqsim.process.safety.risk.portfolio.PortfolioRiskResult} - Aggregated portfolio
 * results with VaR</li>
 * </ul>
 *
 * <h2>Features</h2>
 * <ul>
 * <li>Monte Carlo simulation across multiple assets</li>
 * <li>Common cause failure scenarios (weather, infrastructure, etc.)</li>
 * <li>Diversification benefit calculation</li>
 * <li>Value at Risk (VaR) metrics for insurance/finance</li>
 * <li>Asset contribution analysis</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * PortfolioRiskAnalyzer analyzer = new PortfolioRiskAnalyzer("North Sea Portfolio");
 *
 * // Add assets
 * PortfolioRiskAnalyzer.Asset assetA = analyzer.addAsset("A", "Platform Alpha", 50000);
 * assetA.setRegion("Northern North Sea");
 * assetA.setSystemAvailability(0.95);
 *
 * PortfolioRiskAnalyzer.Asset assetB = analyzer.addAsset("B", "Platform Beta", 30000);
 * assetB.setRegion("Northern North Sea");
 * assetB.setSystemAvailability(0.92);
 *
 * // Add common cause scenario
 * analyzer.createRegionalWeatherScenario("Northern North Sea", 0.5, 5.0);
 *
 * // Run analysis
 * analyzer.setNumberOfSimulations(10000);
 * PortfolioRiskResult result = analyzer.run();
 *
 * // Get results
 * System.out.println(result.toReport());
 * double var99 = result.getValueAtRisk(99);
 * double diversification = result.getDiversificationBenefit();
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see neqsim.process.safety.risk.OperationalRiskSimulator
 */
package neqsim.process.safety.risk.portfolio;
