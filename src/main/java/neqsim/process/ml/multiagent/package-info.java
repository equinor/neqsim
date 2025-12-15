/**
 * Multi-agent reinforcement learning infrastructure for coordinated process control.
 *
 * <p>
 * This package provides infrastructure for multi-agent systems where multiple RL agents coordinate
 * to control interconnected process equipment:
 * <ul>
 * <li>{@link neqsim.process.ml.multiagent.Agent} - Individual agent interface</li>
 * <li>{@link neqsim.process.ml.multiagent.MultiAgentEnvironment} - Coordinated environment</li>
 * <li>{@link neqsim.process.ml.multiagent.SharedConstraintManager} - Global constraint handling</li>
 * <li>{@link neqsim.process.ml.multiagent.CommunicationChannel} - Inter-agent messaging</li>
 * </ul>
 *
 * <h2>Use Cases:</h2>
 * <ul>
 * <li>Train separation with downstream compression (level agent + anti-surge agent)</li>
 * <li>Heat integration networks (multiple heat exchanger agents)</li>
 * <li>Gas processing plants (multiple unit operations)</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
package neqsim.process.ml.multiagent;
