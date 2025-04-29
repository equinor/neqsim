# ProcessSystem Documentation

## Overview
The `ProcessSystem` class in NeqSim is a core component for simulating and managing process systems. It allows users to define, configure, and run simulations of various unit operations and their interactions.

## Features
- Add and manage unit operations.
- Run steady-state and transient simulations.
- Export process flow diagrams.
- Create process systems from YAML/JSON descriptions.
- Monitor and report results.

## Getting Started

### Creating a Process System
```java
ProcessSystem processSystem = new ProcessSystem("My Process System");
```

### Adding Unit Operations
```java
processSystem.addUnit("Compressor1", ProcessSystem.EquipmentEnum.COMPRESSOR);
processSystem.addUnit("Heater1", ProcessSystem.EquipmentEnum.HEATER);
```

### Connecting Streams
```java
ProcessEquipmentInterface stream = processSystem.addUnit(ProcessSystem.EquipmentEnum.STREAM);
ProcessEquipmentInterface compressor = processSystem.getUnit("Compressor1");
processSystem.connectStreams(stream, compressor);
```

### Running the Simulation
```java
processSystem.run(UUID.randomUUID());
```

## API Documentation

### Methods

#### `addUnit`
- **Description**: Adds a unit operation to the process system.
- **Parameters**:
  - `name` (String): Name of the unit.
  - `equipmentType` (EquipmentEnum): Type of the equipment.
- **Example**:
  ```java
  processSystem.addUnit("Pump1", ProcessSystem.EquipmentEnum.PUMP);
  ```

#### `connectStreams`
- **Description**: Connects a source stream to a target unit.
- **Parameters**:
  - `source` (ProcessEquipmentInterface): The source stream.
  - `target` (ProcessEquipmentInterface): The target unit.
- **Example**:
  ```java
  processSystem.connectStreams(stream, compressor);
  ```

#### `run`
- **Description**: Runs the process system simulation.
- **Parameters**:
  - `id` (UUID): Unique identifier for the simulation.
- **Example**:
  ```java
  processSystem.run(UUID.randomUUID());
  ```

#### `exportProcessFlowDiagram`
- **Description**: Exports the process flow diagram to a file.
- **Parameters**:
  - `outputFilePath` (String): Path to save the diagram.
- **Example**:
  ```java
  processSystem.exportProcessFlowDiagram("diagram.png");
  ```

## Tutorials

### Tutorial 1: Building a Simple Process System
1. Create a `ProcessSystem` instance.
2. Add unit operations (e.g., streams, compressors).
3. Connect the units using streams.
4. Run the simulation.
5. Export the process flow diagram.

### Tutorial 2: Creating a Process System from YAML
1. Prepare a YAML file describing the units and connections.
2. Use the `createFromDescription` method to load the process system.
   ```java
   processSystem.createFromDescription("process.yaml");
   ```
3. Run the simulation and analyze results.

## FAQ

### How do I add a custom unit operation?
Extend the `ProcessEquipmentInterface` class and implement the required methods. Then, add it to the process system using the `add` method.

### How do I handle errors during simulation?
Check the logs for detailed error messages. Ensure all units are properly configured and connected.

## Suggested New Documentation Files

To expand the NeqSim documentation, consider adding the following files to the wiki:

1. **Thermodynamic Operations**
   - Overview of thermodynamic operations in NeqSim.
   - Examples of phase equilibrium calculations.
   - API documentation for key classes like `ThermodynamicOperations`.

2. **Mechanical Design**
   - Explanation of mechanical design features.
   - Tutorials for designing pipelines, compressors, and separators.
   - Integration with standards and compliance.

3. **Visualization Tools**
   - Guide to using NeqSim's visualization tools.
   - Examples of creating process flow diagrams and 3D visualizations.

4. **Standards and Compliance**
   - Overview of supported standards (e.g., ISO, ASTM).
   - Examples of using NeqSim for compliance testing.

5. **Data Handling**
   - Guide to importing and exporting data.
   - Examples of using CSV and database integrations.

6. **Advanced Tutorials**
   - Case studies for specific industries (e.g., oil and gas).
   - Advanced simulation techniques.

## Next Steps

- Create the suggested files in the `docs/wiki` folder.
- Populate each file with detailed content and examples.
- Link the new files in the main `README.md` for easy navigation.