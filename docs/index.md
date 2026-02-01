---
layout: default
title: NeqSim Documentation
---

# NeqSim Documentation

**NeqSim** (Non-Equilibrium Simulator) is a comprehensive Java library for thermodynamic, physical property, and process simulation developed by [Equinor](https://www.equinor.com/).

<div class="hero-badges" style="display: flex; gap: 0.5rem; flex-wrap: wrap; justify-content: center; margin: 1rem 0;">
  <a href="https://github.com/equinor/neqsim/actions"><img src="https://github.com/equinor/neqsim/actions/workflows/verify_build.yml/badge.svg" alt="Java CI"></a>
  <a href="https://search.maven.org/search?q=g:%22com.equinor.neqsim%22%20AND%20a:%22neqsim%22"><img src="https://img.shields.io/maven-central/v/com.equinor.neqsim/neqsim.svg?label=Maven%20Central" alt="Maven Central"></a>
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
</div>

<div class="cta-buttons" style="display: flex; gap: 1rem; flex-wrap: wrap; justify-content: center; margin: 2rem 0;">
  <a href="wiki/getting_started.html" class="cta-button primary" style="display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.75rem 1.5rem; border-radius: 6px; text-decoration: none; font-weight: 600; background: #159957; color: white;">🚀 Get Started</a>
  <a href="https://github.com/equinor/neqsim" class="cta-button secondary" style="display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.75rem 1.5rem; border-radius: 6px; text-decoration: none; font-weight: 600; background: #fff; color: #24292e; border: 2px solid #e1e4e8;">⭐ Star on GitHub</a>
  <a href="manual/neqsim_reference_manual.html" class="cta-button secondary" style="display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.75rem 1.5rem; border-radius: 6px; text-decoration: none; font-weight: 600; background: #fff; color: #24292e; border: 2px solid #e1e4e8;">📖 Reference Manual</a>
</div>

<hr class="section-divider" style="border: none; height: 2px; background: linear-gradient(to right, transparent, #159957, transparent); margin: 2rem 0;">

## Quick Navigation

<div class="nav-grid" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 1.5rem; margin: 2rem 0;">

<div class="nav-card" style="background: linear-gradient(135deg, #f8f9fa 0%, #ffffff 100%); border: 1px solid #e1e4e8; border-radius: 12px; padding: 1.5rem; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);">
<h3 style="margin-top: 0; margin-bottom: 1rem; padding-bottom: 0.75rem; border-bottom: 2px solid #159957; color: #24292e; font-size: 1.25rem;">📚 Core Documentation</h3>
<ul style="list-style: none; padding: 0; margin: 0;">
<li style="padding: 0.5rem 0; border-bottom: 1px solid #f0f0f0;"><a href="wiki/getting_started.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Getting Started</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Installation and first steps</span></li>
<li style="padding: 0.5rem 0; border-bottom: 1px solid #f0f0f0;"><a href="modules.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Modules Overview</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Architecture and package structure</span></li>
<li style="padding: 0.5rem 0;"><a href="REFERENCE_MANUAL_INDEX.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Reference Manual</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Complete API documentation</span></li>
</ul>
</div>

<div class="nav-card" style="background: linear-gradient(135deg, #f8f9fa 0%, #ffffff 100%); border: 1px solid #e1e4e8; border-radius: 12px; padding: 1.5rem; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);">
<h3 style="margin-top: 0; margin-bottom: 1rem; padding-bottom: 0.75rem; border-bottom: 2px solid #2196f3; color: #24292e; font-size: 1.25rem;">🔬 Thermodynamics</h3>
<ul style="list-style: none; padding: 0; margin: 0;">
<li style="padding: 0.5rem 0; border-bottom: 1px solid #f0f0f0;"><a href="thermo/README.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Thermo Package</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Equations of state, mixing rules, fluids</span></li>
<li style="padding: 0.5rem 0; border-bottom: 1px solid #f0f0f0;"><a href="thermodynamicoperations/README.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Thermodynamic Operations</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Flash calculations, phase envelopes</span></li>
<li style="padding: 0.5rem 0;"><a href="physical_properties/README.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Physical Properties</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Viscosity, conductivity, diffusivity</span></li>
</ul>
</div>

<div class="nav-card" style="background: linear-gradient(135deg, #f8f9fa 0%, #ffffff 100%); border: 1px solid #e1e4e8; border-radius: 12px; padding: 1.5rem; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);">
<h3 style="margin-top: 0; margin-bottom: 1rem; padding-bottom: 0.75rem; border-bottom: 2px solid #ff9800; color: #24292e; font-size: 1.25rem;">🏭 Process Simulation</h3>
<ul style="list-style: none; padding: 0; margin: 0;">
<li style="padding: 0.5rem 0; border-bottom: 1px solid #f0f0f0;"><a href="process/README.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Process Equipment</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Separators, compressors, heat exchangers</span></li>
<li style="padding: 0.5rem 0; border-bottom: 1px solid #f0f0f0;"><a href="fluidmechanics/README.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Fluid Mechanics</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Pipeline flow, pressure drop</span></li>
<li style="padding: 0.5rem 0;"><a href="safety/README.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Safety Systems</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Relief valves, flare systems</span></li>
</ul>
</div>

<div class="nav-card" style="background: linear-gradient(135deg, #f8f9fa 0%, #ffffff 100%); border: 1px solid #e1e4e8; border-radius: 12px; padding: 1.5rem; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);">
<h3 style="margin-top: 0; margin-bottom: 1rem; padding-bottom: 0.75rem; border-bottom: 2px solid #9c27b0; color: #24292e; font-size: 1.25rem;">📊 Applications</h3>
<ul style="list-style: none; padding: 0; margin: 0;">
<li style="padding: 0.5rem 0; border-bottom: 1px solid #f0f0f0;"><a href="pvtsimulation/README.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>PVT Simulation</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Reservoir fluid characterization</span></li>
<li style="padding: 0.5rem 0; border-bottom: 1px solid #f0f0f0;"><a href="blackoil/README.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Black Oil Models</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Simplified correlations</span></li>
<li style="padding: 0.5rem 0;"><a href="fielddevelopment/README.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Field Development</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Integrated workflows</span></li>
</ul>
</div>

<div class="nav-card" style="background: linear-gradient(135deg, #f8f9fa 0%, #ffffff 100%); border: 1px solid #e1e4e8; border-radius: 12px; padding: 1.5rem; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);">
<h3 style="margin-top: 0; margin-bottom: 1rem; padding-bottom: 0.75rem; border-bottom: 2px solid #e53935; color: #24292e; font-size: 1.25rem;">⚠️ Risk & Reliability</h3>
<ul style="list-style: none; padding: 0; margin: 0;">
<li style="padding: 0.5rem 0; border-bottom: 1px solid #f0f0f0;"><a href="risk/index.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Risk Simulation</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Equipment failure, Monte Carlo analysis</span></li>
<li style="padding: 0.5rem 0; border-bottom: 1px solid #f0f0f0;"><a href="risk/sis-integration.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>SIS/SIF Integration</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">IEC 61508/61511, LOPA, SIL verification</span></li>
<li style="padding: 0.5rem 0;"><a href="risk/bowtie-analysis.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Bow-Tie Analysis</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Barrier analysis, threat visualization</span></li>
</ul>
</div>

<div class="nav-card" style="background: linear-gradient(135deg, #f8f9fa 0%, #ffffff 100%); border: 1px solid #e1e4e8; border-radius: 12px; padding: 1.5rem; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);">
<h3 style="margin-top: 0; margin-bottom: 1rem; padding-bottom: 0.75rem; border-bottom: 2px solid #4caf50; color: #24292e; font-size: 1.25rem;">🎯 Optimization & Cost</h3>
<ul style="list-style: none; padding: 0; margin: 0;">
<li style="padding: 0.5rem 0; border-bottom: 1px solid #f0f0f0;"><a href="process/optimization/OPTIMIZATION_OVERVIEW.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Production Optimization</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Throughput, multi-objective, Pareto</span></li>
<li style="padding: 0.5rem 0; border-bottom: 1px solid #f0f0f0;"><a href="process/COST_ESTIMATION_FRAMEWORK.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Cost Estimation</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">CAPEX, OPEX, financial metrics</span></li>
<li style="padding: 0.5rem 0;"><a href="integration/EXTERNAL_OPTIMIZER_INTEGRATION.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>External Optimizers</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Python/SciPy integration</span></li>
</ul>
</div>

<div class="nav-card" style="background: linear-gradient(135deg, #f8f9fa 0%, #ffffff 100%); border: 1px solid #e1e4e8; border-radius: 12px; padding: 1.5rem; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);">
<h3 style="margin-top: 0; margin-bottom: 1rem; padding-bottom: 0.75rem; border-bottom: 2px solid #00897b; color: #24292e; font-size: 1.25rem;">🌱 Emissions & Sustainability</h3>
<ul style="list-style: none; padding: 0; margin: 0;">
<li style="padding: 0.5rem 0; border-bottom: 1px solid #f0f0f0;"><a href="emissions/index.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Emissions Hub</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Landing page for all emission documentation</span></li>
<li style="padding: 0.5rem 0; border-bottom: 1px solid #f0f0f0;"><a href="emissions/OFFSHORE_EMISSION_REPORTING.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Offshore Reporting Guide</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">Regulatory framework, methods, validation</span></li>
<li style="padding: 0.5rem 0;"><a href="examples/ProducedWaterEmissions_Tutorial.html" style="color: #155799; text-decoration: none; font-weight: 500;"><strong>Emissions Tutorial</strong></a><br><span style="color: #6a737d; font-size: 0.9rem;">CO₂, methane, nmVOC from produced water</span></li>
</ul>
</div>

</div>

<hr class="section-divider" style="border: none; height: 2px; background: linear-gradient(to right, transparent, #159957, transparent); margin: 2rem 0;">

## ⚡ Quick Start Example

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create a natural gas fluid
SystemInterface gas = new SystemSrkEos(298.15, 50.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.03);
gas.addComponent("CO2", 0.02);
gas.setMixingRule("classic");

// Perform flash calculation
ThermodynamicOperations ops = new ThermodynamicOperations(gas);
ops.TPflash();

// Get properties
System.out.println("Density: " + gas.getDensity("kg/m3") + " kg/m³");
System.out.println("Compressibility: " + gas.getZ());
```

<hr class="section-divider" style="border: none; height: 2px; background: linear-gradient(to right, transparent, #159957, transparent); margin: 2rem 0;">

## 📖 Documentation Sections

<div style="overflow-x: auto; margin: 1.5rem 0;">
<table style="width: 100%; border-collapse: separate; border-spacing: 0; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);">
<thead>
<tr>
<th style="background: linear-gradient(135deg, #159957 0%, #127a45 100%); color: white; padding: 1rem; text-align: left; font-weight: 600;">Section</th>
<th style="background: linear-gradient(135deg, #159957 0%, #127a45 100%); color: white; padding: 1rem; text-align: left; font-weight: 600;">Description</th>
</tr>
</thead>
<tbody>
<tr><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #fff;"><a href="thermo/README.html">🧪 Thermodynamics</a></td><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #fff;">Equations of state, phase behavior, component properties</td></tr>
<tr><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #f6f8fa;"><a href="process/README.html">⚙️ Process Simulation</a></td><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #f6f8fa;">Unit operations, process systems, controllers</td></tr>
<tr><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #fff;"><a href="simulation/dynamic_simulation_guide.html">🔄 Dynamic Simulation</a></td><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #fff;">Transient simulation, time-stepping, depressurization</td></tr>
<tr><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #f6f8fa;"><a href="physical_properties/README.html">📊 Physical Properties</a></td><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #f6f8fa;">Transport properties, interfacial tension</td></tr>
<tr><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #fff;"><a href="pvtsimulation/README.html">🛢️ PVT Simulation</a></td><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #fff;">Reservoir fluid characterization, tuning</td></tr>
<tr><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #f6f8fa;"><a href="fluidmechanics/README.html">🌊 Fluid Mechanics</a></td><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #f6f8fa;">Pipeline flow, multiphase modeling</td></tr>
<tr><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #fff;"><a href="process/optimization/OPTIMIZATION_OVERVIEW.html">🎯 Optimization</a></td><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #fff;">Production optimization, multi-objective, constraints</td></tr>
<tr><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #f6f8fa;"><a href="risk/index.html">⚠️ Risk & Reliability</a></td><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #f6f8fa;">Equipment failure, Monte Carlo, SIS/SIF, bow-tie</td></tr>
<tr><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #fff;"><a href="safety/README.html">🛡️ Safety Systems</a></td><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #fff;">ESD, HIPPS, PSV, blowdown, alarms</td></tr>
<tr><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #f6f8fa;"><a href="examples/ProducedWaterEmissions_Tutorial.html">🌱 Emissions & Sustainability</a></td><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #f6f8fa;">CO₂, methane, nmVOC virtual measurement, Norwegian methods</td></tr>
<tr><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #fff;"><a href="process/COST_ESTIMATION_FRAMEWORK.html">💰 Cost Estimation</a></td><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #fff;">CAPEX, OPEX, financial metrics (NPV, ROI)</td></tr>
<tr><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #fff;"><a href="examples/">💡 Examples</a></td><td style="padding: 0.875rem 1rem; border-bottom: 1px solid #e1e4e8; background: #fff;">Tutorials, Jupyter notebooks, code samples</td></tr>
<tr><td style="padding: 0.875rem 1rem; background: #f6f8fa;"><a href="development/README.html">🔧 Development</a></td><td style="padding: 0.875rem 1rem; background: #f6f8fa;">Contributing guidelines, developer setup</td></tr>
</tbody>
</table>
</div>

<hr class="section-divider" style="border: none; height: 2px; background: linear-gradient(to right, transparent, #159957, transparent); margin: 2rem 0;">

## 📚 Interactive Reference Manual

<div style="background: linear-gradient(135deg, #f0f7ff 0%, #fff 100%); border: 1px solid #c8e1ff; border-radius: 12px; padding: 1.5rem; margin: 1.5rem 0;">
<p style="margin: 0 0 1rem 0;">The <a href="manual/neqsim_reference_manual.html"><strong>Interactive Reference Manual</strong></a> provides a searchable, navigable guide to all NeqSim packages:</p>
<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1rem;">
<div style="display: flex; align-items: center; gap: 0.5rem;">✅ Complete package hierarchy</div>
<div style="display: flex; align-items: center; gap: 0.5rem;">✅ Class and interface listings</div>
<div style="display: flex; align-items: center; gap: 0.5rem;">✅ Usage examples and snippets</div>
<div style="display: flex; align-items: center; gap: 0.5rem;">✅ Cross-referenced links</div>
</div>
</div>

<hr class="section-divider" style="border: none; height: 2px; background: linear-gradient(to right, transparent, #159957, transparent); margin: 2rem 0;">

## 🐍 Python Integration

NeqSim is also available for Python through [**neqsim-python**](https://github.com/equinor/neqsim-python):

```python
from neqsim.thermo import TPflash, fluid

# Create and flash a natural gas
gas = fluid("srk")
gas.addComponent("methane", 0.9)
gas.addComponent("ethane", 0.1)
gas.setTemperature(298.15, "K")
gas.setPressure(50.0, "bara")

TPflash(gas)
print(f"Gas density: {gas.getDensity('kg/m3'):.2f} kg/m³")
```

<hr class="section-divider" style="border: none; height: 2px; background: linear-gradient(to right, transparent, #159957, transparent); margin: 2rem 0;">

## 🔗 Resources

<div class="resources-grid" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin: 1.5rem 0;">
<a href="https://github.com/equinor/neqsim" style="display: flex; align-items: center; gap: 0.75rem; padding: 1rem; background: #fff; border: 1px solid #e1e4e8; border-radius: 8px; text-decoration: none; color: #24292e; transition: all 0.2s ease;">
<span style="font-size: 1.5rem;">📦</span>
<span><strong>GitHub Repository</strong><br><small style="color: #6a737d;">Source code & issues</small></span>
</a>
<a href="https://search.maven.org/artifact/com.equinor.neqsim/neqsim" style="display: flex; align-items: center; gap: 0.75rem; padding: 1rem; background: #fff; border: 1px solid #e1e4e8; border-radius: 8px; text-decoration: none; color: #24292e; transition: all 0.2s ease;">
<span style="font-size: 1.5rem;">☕</span>
<span><strong>Maven Central</strong><br><small style="color: #6a737d;">Latest releases</small></span>
</a>
<a href="https://github.com/equinor/neqsim/issues" style="display: flex; align-items: center; gap: 0.75rem; padding: 1rem; background: #fff; border: 1px solid #e1e4e8; border-radius: 8px; text-decoration: none; color: #24292e; transition: all 0.2s ease;">
<span style="font-size: 1.5rem;">🐛</span>
<span><strong>Issue Tracker</strong><br><small style="color: #6a737d;">Report bugs</small></span>
</a>
<a href="https://github.com/equinor/neqsim/discussions" style="display: flex; align-items: center; gap: 0.75rem; padding: 1rem; background: #fff; border: 1px solid #e1e4e8; border-radius: 8px; text-decoration: none; color: #24292e; transition: all 0.2s ease;">
<span style="font-size: 1.5rem;">💬</span>
<span><strong>Discussions</strong><br><small style="color: #6a737d;">Community Q&A</small></span>
</a>
</div>

<hr class="section-divider" style="border: none; height: 2px; background: linear-gradient(to right, transparent, #159957, transparent); margin: 2rem 0;">

<div class="doc-footer" style="margin-top: 2rem; padding-top: 1.5rem; text-align: center; color: #6a737d;">
<p>NeqSim is developed and maintained by <a href="https://www.equinor.com/" style="color: #159957;">Equinor</a> and contributors.</p>
<p>Licensed under the <a href="https://opensource.org/licenses/Apache-2.0" style="color: #159957;">Apache 2.0 License</a>.</p>
</div>
