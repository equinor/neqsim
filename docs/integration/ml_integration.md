# NeqSim ML Integration Guide

This module provides infrastructure for integrating NeqSim with modern AI/ML systems including Reinforcement Learning, neural network surrogates, and multi-agent control.

## Package Structure

```
neqsim.process.ml/
├── StateVector.java           - Normalized state representation
├── StateVectorProvider.java   - Interface for equipment state export
├── ActionVector.java          - Bounded action representation
├── Constraint.java            - Physical/safety constraints
├── ConstraintManager.java     - Unified constraint handling
├── RLEnvironment.java         - RL base class
├── GymEnvironment.java        - Gymnasium-compatible abstract class
├── EpisodeRunner.java         - Java-based episode execution & benchmarking
├── EquipmentStateAdapter.java - Extract states from equipment
├── TrainingDataCollector.java - Surrogate model data export
├── controllers/
│   ├── Controller.java               - Controller interface
│   ├── ProportionalController.java   - P controller
│   ├── PIDController.java            - PID with anti-windup
│   ├── BangBangController.java       - On-off with hysteresis
│   └── RandomController.java         - Random baseline
├── examples/
│   ├── SeparatorLevelControlEnv.java         - RL example
│   ├── SeparatorGymEnv.java                  - Gym-compatible single agent
│   ├── SeparatorCompressorMultiAgentEnv.java - Multi-agent example
│   └── FlashSurrogateDataGenerator.java      - Surrogate training
└── multiagent/
    ├── Agent.java                    - Agent interface
    ├── ProcessAgent.java             - Base process control agent
    ├── SeparatorAgent.java           - Separator control agent
    ├── CompressorAgent.java          - Compressor control agent
    └── MultiAgentEnvironment.java    - Multi-agent coordinator
```

## Design Principles

1. **Physics First** - ML augments, never replaces, thermodynamic rigor
2. **Safety by Design** - Constraints enforced before any action execution
3. **Explainability** - All decisions traceable to physical constraints
4. **Multi-fidelity** - Fast surrogates for training, full physics for deployment
5. **Gymnasium Compatible** - Direct integration with Python RL frameworks
6. **Java-Testable** - Test RL infrastructure without Python dependencies

## Quick Start

### 1. Gymnasium-Compatible Environment

The `GymEnvironment` class provides a Gymnasium-compatible API that works directly with Python:

```java
import neqsim.process.ml.examples.SeparatorGymEnv;
import neqsim.process.ml.GymEnvironment.ResetResult;
import neqsim.process.ml.GymEnvironment.StepResult;

// Create environment
SeparatorGymEnv env = new SeparatorGymEnv();
env.setMaxEpisodeSteps(500);
env.setLevelSetpoint(0.5);

// Standard Gym API
ResetResult reset = env.reset();
double[] obs = reset.observation;  // 8-dimensional state

while (!env.isDone()) {
    double[] action = new double[] {0.02};  // Valve position delta
    StepResult result = env.step(action);
    
    obs = result.observation;
    double reward = result.reward;
    boolean terminated = result.terminated;
    boolean truncated = result.truncated;
    Map<String, Object> info = result.info;
}
```

**Python Usage (via JPype):**

```python
import jpype
from jpype import JClass
import numpy as np

jpype.startJVM(classpath=['neqsim.jar'])

SeparatorGymEnv = JClass('neqsim.process.ml.examples.SeparatorGymEnv')
env = SeparatorGymEnv()
env.setMaxEpisodeSteps(500)

# Gymnasium-compatible API
reset_result = env.reset()
obs = np.array(reset_result.observation)

done = False
total_reward = 0
while not done:
    action = policy.predict(obs)  # Your RL policy
    result = env.step([float(action)])
    
    obs = np.array(result.observation)
    reward = result.reward
    done = result.terminated or result.truncated
    total_reward += reward

print(f"Episode reward: {total_reward}")
```

### 2. Multi-Agent Environments

For coordinated control of multiple process units:

```java
import neqsim.process.ml.examples.SeparatorCompressorMultiAgentEnv;
import neqsim.process.ml.multiagent.MultiAgentEnvironment;
import java.util.Map;
import java.util.HashMap;

// Create multi-agent environment
SeparatorCompressorMultiAgentEnv env = new SeparatorCompressorMultiAgentEnv();
env.setCoordinationMode(MultiAgentEnvironment.CoordinationMode.COOPERATIVE);
env.setMaxEpisodeSteps(1000);

// Reset - get observations for all agents
Map<String, double[]> obs = env.reset();
double[] sepObs = obs.get("separator");
double[] compObs = obs.get("compressor");

// Training loop
while (!env.isDone()) {
    // Get actions from policies
    Map<String, double[]> actions = new HashMap<>();
    actions.put("separator", separatorPolicy.predict(sepObs));
    actions.put("compressor", compressorPolicy.predict(compObs));
    
    // Step
    StepResult result = env.step(actions);
    
    // Get rewards (shared in cooperative mode)
    double sepReward = result.rewards.get("separator");
    double compReward = result.rewards.get("compressor");
    
    // Next observations
    sepObs = result.observations.get("separator");
    compObs = result.observations.get("compressor");
}
```

**Coordination Modes:**

| Mode | Description |
|------|-------------|
| `INDEPENDENT` | Each agent has local reward |
| `COOPERATIVE` | All agents share team reward |
| `CTDE` | Centralized training, decentralized execution |
| `COMMUNICATING` | Agents exchange messages before acting |

### 3. Custom Process Agent

```java
import neqsim.process.ml.multiagent.ProcessAgent;
import neqsim.process.ml.StateVector;

public class MyValveAgent extends ProcessAgent {
    private ThrottlingValve valve;
    
    public MyValveAgent(String id, ThrottlingValve valve) {
        super(id, valve);
        this.valve = valve;
        
        // Define spaces
        observationNames = new String[]{"upstream_pressure", "downstream_pressure", "flow"};
        actionNames = new String[]{"valve_delta"};
        actionLow = new double[]{-0.1};
        actionHigh = new double[]{0.1};
        
        // Set control setpoint
        setSetpoint("downstream_pressure", 50.0, 10.0);
    }
    
    @Override
    public double[] getLocalObservation(StateVector globalState) {
        return new double[]{
            globalState.getValue("upstream_pressure") / 100.0,
            globalState.getValue("downstream_pressure") / 100.0,
            globalState.getValue("flow") / 1000.0
        };
    }
    
    @Override
    public void applyAction(double[] action) {
        double currentPos = valve.getPercentValveOpening() / 100.0;
        double newPos = Math.max(0, Math.min(1, currentPos + action[0]));
        valve.setPercentValveOpening(newPos * 100.0);
    }
}
```

### 2. Surrogate Model Training Data

```java
import neqsim.process.ml.TrainingDataCollector;

// Create collector
TrainingDataCollector collector = new TrainingDataCollector("flash_model");
collector.defineInput("temperature", "K", 200.0, 500.0);
collector.defineInput("pressure", "bar", 1.0, 100.0);
collector.defineOutput("vapor_fraction", "mole_frac", 0.0, 1.0);

// Collect samples
for (double T = 200; T <= 500; T += 10) {
    for (double P = 1; P <= 100; P += 5) {
        // Run flash calculation
        fluid.setTemperature(T, "K");
        fluid.setPressure(P, "bar");
        ops.TPflash();
        
        // Record
        collector.startSample();
        collector.recordInput("temperature", T);
        collector.recordInput("pressure", P);
        collector.recordOutput("vapor_fraction", fluid.getPhase("gas").getBeta());
        collector.endSample();
    }
}

// Export for Python
collector.exportCSV("training_data.csv");
```

### 3. Constraint Management

```java
import neqsim.process.ml.ConstraintManager;
import neqsim.process.ml.Constraint;
import neqsim.process.ml.StateVector;

// Setup constraints
ConstraintManager cm = new ConstraintManager();
cm.addHardRange("max_pressure", "pressure", 0.0, 150.0, "bar");
cm.addSoftRange("optimal_temp", "temperature", 280.0, 320.0, "K");

// Evaluate against state
StateVector state = ...;  // From equipment
cm.evaluate(state);

// Check for violations
if (cm.hasHardViolation()) {
    System.out.println("SAFETY VIOLATION: " + cm.explainViolations());
}

// Get penalty for RL reward
double penalty = cm.getTotalViolationPenalty();
```

## Python Integration

The Java classes are designed to work with [neqsim-python](https://github.com/equinor/neqsim-python) via JPype:

```python
import jpype
import jpype.imports
from jpype.types import *

# Start JVM with NeqSim
jpype.startJVM(classpath=['path/to/neqsim.jar'])

from neqsim.process.ml.examples import SeparatorLevelControlEnv
from neqsim.process.ml import StateVector, ActionVector

# Create environment
env = SeparatorLevelControlEnv()

# Use with stable-baselines3 (requires wrapper)
obs = env.reset()
obs_array = obs.toNormalizedArray()

# ... training loop ...
```

## Creating Custom RL Environments

Extend `RLEnvironment` and override:

```java
public class MyControlEnv extends RLEnvironment {
    
    @Override
    protected void applyAction(ActionVector action) {
        // Apply control action to equipment
        double valvePos = action.get("valve");
        myValve.setOpening(valvePos);
    }
    
    @Override
    protected StateVector getObservation() {
        StateVector state = new StateVector();
        state.add("level", separator.getLevel(), 0.0, 1.0, "fraction");
        state.add("pressure", separator.getPressure(), 0.0, 100.0, "bar");
        return state;
    }
    
    @Override
    protected double computeReward(StateVector state, ActionVector action, StepInfo info) {
        double reward = super.computeReward(state, action, info);
        
        // Add task-specific reward
        double levelError = state.getValue("level") - setpoint;
        reward -= 10.0 * levelError * levelError;
        
        return reward;
    }
}
```

## Architecture for Production

```
┌─────────────────────────────────────────────────────────┐
│                    Python Agent                         │
│  (stable-baselines3, RLlib, custom algorithms)         │
└─────────────────────┬───────────────────────────────────┘
                      │ JPype/Py4J
┌─────────────────────▼───────────────────────────────────┐
│                   neqsim.process.ml                             │
│  ┌─────────────┐  ┌─────────────┐  ┌────────────────┐  │
│  │RLEnvironment│  │StateVector  │  │ConstraintMgr   │  │
│  └──────┬──────┘  └──────┬──────┘  └───────┬────────┘  │
│         │                │                  │           │
│  ┌──────▼──────────────────▼──────────────────▼──────┐  │
│  │              ProcessSystem                        │  │
│  │  (Separator, Valve, Stream, etc.)                │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
│  ┌────────────────────────────────────────────────────┐ │
│  │           Thermodynamic Engine                     │ │
│  │  (SRK, PR, CPA equations of state)                │ │
│  └────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

## Next Steps

1. ~~**Implement `StateVectorProvider`**~~ - ✅ Added to Separator, Compressor, HeatExchanger
2. ~~**Gymnasium-compatible API**~~ - ✅ `GymEnvironment` base class
3. ~~**Multi-agent infrastructure**~~ - ✅ `multiagent` package with cooperative/CTDE modes
4. ~~**Java-only testing**~~ - ✅ `controllers` package with P, PID, BangBang, Random + EpisodeRunner
5. **Python wrapper (neqsim-gym)** - Create PyPI package wrapping Java RL environments
6. **Dynamic simulation integration** - Connect to time-stepping solver
7. **More example environments** - Distillation column control, heat integration
8. **Pre-trained surrogates** - Ship common surrogate models (flash, property estimation)

## Multi-Agent Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    Python Multi-Agent Framework              │
│           (MAPPO, QMIX, MADDPG via RLlib/EPyMARL)           │
└──────────────────────┬───────────────────────────────────────┘
                       │ JPype/Py4J
┌──────────────────────▼───────────────────────────────────────┐
│                 MultiAgentEnvironment                        │
│  ┌─────────────┐  ┌─────────────┐  ┌────────────────────┐   │
│  │SeparatorAgent│  │CompressorAgent│  │SharedConstraints │   │
│  └──────┬──────┘  └──────┬──────┘  └────────┬───────────┘   │
│         │                │                   │               │
│  ┌──────▼────────────────▼───────────────────▼───────────┐  │
│  │                  ProcessSystem                         │  │
│  │      Separator ────► Compressor ────► Cooler          │  │
│  └───────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

## Equipment with StateVectorProvider

The following equipment classes implement `StateVectorProvider`:

### Separator
| State Variable | Unit | Description |
|---------------|------|-------------|
| pressure | bar | Separator pressure |
| temperature | K | Separator temperature |
| liquid_level | fraction | Liquid level (0-1) |
| gas_density | kg/m³ | Gas phase density |
| liquid_density | kg/m³ | Liquid phase density |
| gas_flow | kg/s | Gas outlet flow |
| liquid_flow | kg/s | Liquid outlet flow |
| gas_load_factor | - | Gas load factor |

### Compressor
| State Variable | Unit | Description |
|---------------|------|-------------|
| inlet_pressure | bar | Inlet pressure |
| outlet_pressure | bar | Outlet pressure |
| inlet_temperature | K | Inlet temperature |
| outlet_temperature | K | Outlet temperature |
| compression_ratio | - | Compression ratio |
| polytropic_efficiency | fraction | Polytropic efficiency |
| isentropic_efficiency | fraction | Isentropic efficiency |
| power | kW | Shaft power |
| speed | rpm | Rotational speed |
| surge_fraction | fraction | Distance from surge |
| polytropic_head | kJ/kg | Polytropic head |
| inlet_flow | kg/s | Inlet mass flow |

### HeatExchanger
| State Variable | Unit | Description |
|---------------|------|-------------|
| hot_inlet_temp | K | Hot side inlet temperature |
| hot_outlet_temp | K | Hot side outlet temperature |
| cold_inlet_temp | K | Cold side inlet temperature |
| cold_outlet_temp | K | Cold side outlet temperature |
| duty | kW | Heat duty |
| ua_value | W/K | UA value |
| effectiveness | fraction | Thermal effectiveness |
| hot_flow | kg/s | Hot side mass flow |
| cold_flow | kg/s | Cold side mass flow |

## Java-Only Testing (No Python Required)

For testing and benchmarking RL environments without Python dependencies, NeqSim provides simple controllers and an episode runner entirely in Java.

### Available Controllers

The `neqsim.process.ml.controllers` package provides:

| Controller | Description | Use Case |
|------------|-------------|----------|
| `ProportionalController` | P control: u = -Kp × error | Simple feedback |
| `PIDController` | Full PID with anti-windup | Industrial control baseline |
| `BangBangController` | On-off with hysteresis | Simple thermostatic control |
| `RandomController` | Uniform random actions | Baseline for RL comparison |

### Controller Interface

```java
public interface Controller {
    String getName();
    double[] computeAction(double[] observation);
    void reset();
}
```

### Example: PID Controller

```java
import neqsim.process.ml.controllers.*;
import neqsim.process.ml.examples.SeparatorGymEnv;

// Create environment
SeparatorGymEnv env = new SeparatorGymEnv();

// Create PID controller
// Args: name, observationIndex (level error=6), Kp, Ki, Kd, actionMin, actionMax, dt
PIDController pid = new PIDController("LevelPID", 6, 0.3, 0.1, 0.05, -0.1, 0.1, 1.0);

// Run one step
GymEnvironment.ResetResult reset = env.reset();
double[] action = pid.computeAction(reset.observation.toNormalizedArray());
GymEnvironment.StepResult step = env.step(action);
```

### EpisodeRunner for Benchmarking

The `EpisodeRunner` class runs complete episodes and collects statistics:

```java
import neqsim.process.ml.EpisodeRunner;
import neqsim.process.ml.EpisodeRunner.*;

// Create runner
EpisodeRunner runner = new EpisodeRunner(env);

// Run single episode
EpisodeResult result = runner.runEpisode(pid, 500);
System.out.printf("Episode: reward=%.2f, steps=%d%n", 
    result.totalReward, result.steps);

// Benchmark with statistics
BenchmarkResult benchmark = runner.benchmark(pid, 10, 500);
System.out.printf("Benchmark: mean=%.2f, std=%.2f%n",
    benchmark.meanReward, benchmark.stdReward);
```

### Comparing Controllers

```java
import java.util.*;

// Create multiple controllers
List<Controller> controllers = Arrays.asList(
    new ProportionalController("P-only", 6, 0.5, -0.1, 0.1),
    new PIDController("PID", 6, 0.3, 0.1, 0.05, -0.1, 0.1, 1.0),
    new BangBangController("BangBang", 6, 0.1, 0.05, -0.05, 1.0),
    new RandomController("Random", 1, -0.1, 0.1)
);

// Compare all controllers
List<BenchmarkResult> results = runner.compareControllers(controllers, 10, 500);
EpisodeRunner.printComparison(results);
```

**Sample Output:**
```
╔══════════════════════════════════════════════════════════════════════════════╗
║                          Controller Comparison Results                        ║
╠═══════════════╦════════════╦═══════════╦════════════╦═══════════╦════════════╣
║ Controller    ║ Mean Reward║ Std Reward║ Mean Steps ║ Terminations ║ Best Run ║
╠═══════════════╬════════════╬═══════════╬════════════╬═══════════╬════════════╣
║ PID           ║    247.34  ║    12.45  ║    500.0   ║   0 / 10  ║   268.21  ║
║ P-only        ║    189.23  ║    34.67  ║    498.2   ║   1 / 10  ║   231.45  ║
║ BangBang      ║    156.78  ║    45.23  ║    487.5   ║   2 / 10  ║   198.34  ║
║ Random        ║     23.45  ║    78.90  ║    234.7   ║   8 / 10  ║    89.12  ║
╚═══════════════╩════════════╩═══════════╩════════════╩═══════════╩════════════╝
```

### Testing Without Python

This enables:
- **Unit testing** - Verify environments work correctly in pure Java
- **Baseline comparison** - Compare RL agents against classical controllers
- **Performance profiling** - Benchmark simulation speed without Python overhead
- **CI/CD integration** - Run tests in Maven without Python setup

## References

- [Gymnasium (Gym) API](https://gymnasium.farama.org/)
- [stable-baselines3](https://stable-baselines3.readthedocs.io/)
- [NeqSim Documentation](https://equinor.github.io/neqsim/)
- [Physics-Informed Neural Networks](https://maziarraissi.github.io/PINNs/)
