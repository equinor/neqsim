/**
 * Real-time Risk Monitoring Package for Digital Twin Integration.
 *
 * <p>
 * This package provides real-time risk monitoring capabilities for integration with digital twin
 * platforms, SCADA systems, and control room dashboards. Key features include:
 * </p>
 *
 * <ul>
 * <li><strong>Continuous Monitoring:</strong> Scheduled risk assessments at configurable
 * intervals</li>
 * <li><strong>Alert Generation:</strong> Automatic alerts for threshold breaches and anomalies</li>
 * <li><strong>Trend Analysis:</strong> Track risk trends over time with anomaly detection</li>
 * <li><strong>Dashboard Integration:</strong> JSON output format for easy integration with web
 * dashboards</li>
 * <li><strong>Equipment Status:</strong> Track individual equipment risk levels and health</li>
 * </ul>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.safety.risk.realtime.RealTimeRiskMonitor} - Main monitoring class with
 * alert generation</li>
 * <li>{@link neqsim.process.safety.risk.realtime.RealTimeRiskAssessment} - Single assessment result
 * with metrics</li>
 * </ul>
 *
 * <h2>Integration Patterns</h2>
 *
 * <h3>Dashboard Integration (REST API)</h3>
 * 
 * <pre>
 * RealTimeRiskMonitor monitor = new RealTimeRiskMonitor("Platform-A", processSystem);
 * monitor.setUpdateIntervalSeconds(60);
 * monitor.startMonitoring();
 *
 * // In REST endpoint
 * public String getCurrentRisk() {
 *   return monitor.toJson(); // Returns JSON for dashboard
 * }
 * </pre>
 *
 * <h3>Alert Handling</h3>
 * 
 * <pre>
 * monitor.addAlertListener(new AlertListener() {
 *   public void onAlert(RiskAlert alert) {
 *     if (alert.getSeverity() == RiskAlert.AlertSeverity.CRITICAL) {
 *       sendSMSNotification(alert);
 *       logToHistorian(alert);
 *     }
 *   }
 * });
 * </pre>
 *
 * <h3>OPC-UA Integration</h3>
 * 
 * <pre>
 * // Update process variables from OPC-UA
 * assessment.addProcessVariable("PT-001", opcClient.readValue("PT-001"), 85.0, "bar");
 * assessment.addProcessVariable("TT-001", opcClient.readValue("TT-001"), 45.0, "C");
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see neqsim.process.safety.risk.OperationalRiskSimulator
 */
package neqsim.process.safety.risk.realtime;
