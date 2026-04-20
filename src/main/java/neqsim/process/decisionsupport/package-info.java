/**
 * Engineering decision support for control room operators.
 *
 * <p>
 * This package provides a framework for answering operator engineering questions ("Can we run at X
 * with today's gas quality?", "What is the safest derate option now?") using validated NeqSim
 * process simulations. Every recommendation is auditable and traceable.
 * </p>
 *
 * <p>
 * Core classes:
 * </p>
 * <ul>
 * <li>{@link neqsim.process.decisionsupport.OperatorQuery} — structured operator question</li>
 * <li>{@link neqsim.process.decisionsupport.EngineeringRecommendation} — auditable recommendation
 * </li>
 * <li>{@link neqsim.process.decisionsupport.DecisionSupportEngine} — query dispatcher and audit
 * coordinator</li>
 * <li>{@link neqsim.process.decisionsupport.OperatingSpecification} — plant-specific limits (loaded
 * from JSON)</li>
 * <li>{@link neqsim.process.decisionsupport.QueryWorkflow} — pluggable workflow interface</li>
 * <li>{@link neqsim.process.decisionsupport.AuditLogger} — audit trail persistence interface</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
package neqsim.process.decisionsupport;
