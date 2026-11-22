# Produced-water compatibility with NeqSim and PHREEQC

Two synthetic produced-water compatibility cases cover three-phase process conditions and supplied station conditions. The example uses
NeqSim 3.29.0 and PhreeqPy 0.6.0 with its bundled IPhreeqc 3.7.3. It was exercised
on Linux with Java 21 and Python 3.12. The Python study is a stand-alone example.
The native NeqSim API computes silica SI from externally speciated activities.

## Run

Install the dependencies in a Python environment that has a working Java runtime.
Download the [PHREEQC v3.7.3 Pitzer database](https://raw.githubusercontent.com/phreeqc-dev/phreeqc3/v3.7.3/database/pitzer.dat)
as `pitzer.dat`. The program checks the SHA-256 stored in `study.py`.

```bash
python -m pip install -r requirements.txt
python study.py case.json --database pitzer.dat --output process-results
python study.py station_case.json --database pitzer.dat --output station-results
PRODUCED_WATER_PITZER_DATABASE="$PWD/pitzer.dat" python -m unittest discover -v
```

The CLI writes `result.json` with the exact input and provenance and
`blend_results.csv` with one row per blend, station and carbon boundary. To use
the same calculation as a local MCP stdio server, run `mcp_server.py` with the
same interpreter and the `PRODUCED_WATER_PITZER_DATABASE` environment variable.
The two calls expose the schema and a study summary. Supply up to five blend IDs
to retrieve detailed rows through MCP. The CLI writes the complete dataset. The MCP entry point routes native library
diagnostics to stderr so stdout carries the protocol.

## Study modes

| Mode | Required basis | CO₂ boundaries and results |
|------|----------------|---------------------------|
| `process` (default) | Water assays, gas and oil component inventories for each formation, incoming solvent-water rate and ordered process stations | Closed aqueous DIC and fixed EOS-derived gas fugacity at every station. Reports gas, oil and aqueous phase flows and NeqSim component balance. |
| `station` | Water assays and station temperature and pressure. Gas and oil inventories are omitted. | Closed aqueous DIC at each station. A supplied `co2_fugacity_bar` adds a fixed-fugacity scenario. A supplied `aqueous_solvent_water_fraction` accounts for water removed from the brine. The default is full water retention. |

Station mode executes PHREEQC without starting the JVM. Distinct stations can be
independent measurements or scenarios. Use a condition-matched CO₂ fugacity in bar.
The result records the water-retention source. If `water_kg_s` is absent, the
mass-normalized silica potential is still calculated and `equilibrium_solid_kg_h`
is null. NeqSim phase flow and component-balance outputs are null in station mode.
The MCP tool summarizes only the boundaries that ran.

## Inputs and boundaries

- Define gas and oil in mol per kg of incoming **solvent water**. Use the listed
  components. This surrogate uses n-heptane and n-decane for the oil cuts.
- Supply dissolved silica in mg as SiO₂ per kg of solvent water. Convert assays
  first if they are reported as elemental Si, per litre of solution or per kg
  of solution. For example, 1 mg as Si corresponds to about 2.139 mg as SiO₂.
- Enter an ion inventory in mol/kg solvent water and alkalinity in mmol of charge
  per kg solvent water. Record pH at its sampling temperature and absolute pressure.
  Resolve ionic or sampling discrepancies explicitly. The tool checks PHREEQC
  charge error and applies no automatic charge-balancing adjustment.
- Fractions are fractions of solvent-water feed mass, with one fraction per
  formation. The sweep includes all pairwise blends with pure end members and any
  user-supplied blends for three formations. Every station reports its own result.
- The NeqSim flowsheet uses an SRK-CPA mixer, prescribed intermediate and outlet
  states, and a three-phase separator at the last station. The prescribed states
  represent operating conditions. Salts affect the aqueous calculations in PHREEQC.
  NeqSim sends the equilibrium gas CO₂ fugacity and solvent-water retention to the
  brine calculation. There is no material transfer back to NeqSim.
- In both modes, PHREEQC mixes the speciated feed assays, applies the local T/P and
  solvent-water change, and calculates aqueous speciation. The closed aqueous DIC
  mode conserves aqueous inorganic carbon. The fixed-fugacity mode exchanges CO₂
  with an external reservoir. The code iterates the PHREEQC imposed CO₂ partial
  pressure until the returned fugacity agrees with the EOS value or station input.
- First evaluate saturation indices with solids absent. Then allow only amorphous
  silica to precipitate. This is a local equilibrium removal potential. It is
  calculated from the original blended inventory at every node and must not be
  summed along the flow path. The reported gas/oil/aqueous stream rates come from
  the neutral NeqSim flash.

## Interpretation and validation

A positive SI is supersaturation. Amorphous silica, quartz, and chalcedony
have different equilibrium constants. Silica gel kinetics and competing
precipitation need separate evidence. Magnesium silicates here are equilibrium
SI indicators. Aluminum, iron, solids carry-over, scale inhibition, emulsions
and reinjection damage require more data and are outside this example.
The database and computational checks support screening. They do not establish
Pitzer parameter completeness or model accuracy for an asset's multicomponent brine.

The study tests process component closure, dissolved-ion and elemental silicon
closure, aqueous DIC conservation in the closed mode, CO₂ fugacity agreement in the
fixed-fugacity mode, and saturation after amorphous silica precipitation. The station
mode test runs with and without a water rate and verifies that no process flash is called. The 25 °C pure-water
solubility sanity check compares with the measured average of 0.00218 mol/kg H₂O
reported by [Marshall (1980)](https://doi.org/10.1016/0016-7037(80)90281-1).
The [USGS PHREEQC manual](https://doi.org/10.3133/tm6A43) describes input and conventions.
For high-salinity silica sensitivity, see [Fournier and Williams (1983)](https://doi.org/10.1016/0016-7037(83)90280-6).
