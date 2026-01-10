#!/usr/bin/env python3
"""
NeqSim Reference Manual Generator

This script combines all markdown documentation files into a single
beautifully formatted HTML document with proper equation rendering.
"""

import os
import re
from pathlib import Path
import markdown2

# Configuration
DOCS_DIR = Path(__file__).parent
OUTPUT_DIR = DOCS_DIR / "manual"

# MathJax configuration for equation rendering
MATHJAX_CONFIG = """
<script>
MathJax = {
  tex: {
    inlineMath: [['$', '$'], ['\\\\(', '\\\\)']],
    displayMath: [['$$', '$$'], ['\\\\[', '\\\\]']],
    processEscapes: true,
    processEnvironments: true
  },
  options: {
    skipHtmlTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code']
  }
};
</script>
<script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js" async></script>
"""

# GitHub Pages link handler - converts .md links to work correctly
GITHUB_PAGES_SCRIPT = """
<script>
// GitHub Pages Link Handler
// Converts .md links to work correctly on GitHub Pages
(function() {
    'use strict';
    
    // Detect if we're on GitHub Pages
    var isGitHubPages = window.location.hostname.endsWith('.github.io');
    
    // Handle all clicks on links
    document.addEventListener('click', function(e) {
        var target = e.target.closest('a');
        if (!target) return;
        
        var href = target.getAttribute('href');
        if (!href) return;
        
        // Only handle relative .md links
        if (href.endsWith('.md') && !href.startsWith('http')) {
            e.preventDefault();
            
            if (isGitHubPages) {
                // On GitHub Pages, remove .md extension (Jekyll converts to .html)
                var newHref = href.replace(/\\.md$/, '');
                window.location.href = newHref;
            } else {
                // For local file viewing, open on GitHub
                var basePath = 'https://github.com/equinor/neqsim/blob/master/docs/';
                
                // Resolve relative paths
                var resolvedPath = href;
                if (href.startsWith('../')) {
                    // Remove ../ and adjust path
                    resolvedPath = href.replace(/^(\\.\\.\\/)+/, '');
                }
                
                window.open(basePath + resolvedPath, '_blank');
            }
        }
    });
    
    // Add visual indicator that links will open externally when viewing locally
    if (!isGitHubPages && window.location.protocol === 'file:') {
        var links = document.querySelectorAll('a[href$=".md"]');
        links.forEach(function(link) {
            if (!link.querySelector('.external-icon')) {
                link.insertAdjacentHTML('beforeend', ' <span class="external-icon" style="font-size:0.8em;opacity:0.6;">↗</span>');
                link.title = 'Opens on GitHub (external link)';
            }
        });
        
        // Add info banner
        var banner = document.createElement('div');
        banner.style.cssText = 'position:fixed;bottom:0;left:0;right:0;background:#0066cc;color:white;padding:10px 20px;font-size:14px;text-align:center;z-index:1000;';
        banner.innerHTML = '📄 Viewing locally - Documentation links will open on GitHub. <a href="https://equinor.github.io/neqsim/manual/neqsim_reference_manual.html" style="color:#fff;text-decoration:underline;">View hosted version</a> for seamless navigation.';
        document.body.appendChild(banner);
    }
})();
</script>
"""

# CSS for professional PDF-ready styling
CSS_STYLE = """
<style>
    @import url('https://fonts.googleapis.com/css2?family=Source+Sans+Pro:wght@300;400;600;700&family=Source+Code+Pro:wght@400;500&display=swap');
    
    :root {
        --primary-color: #0066cc;
        --secondary-color: #004a94;
        --accent-color: #ff6b35;
        --text-color: #333333;
        --light-gray: #f5f5f5;
        --border-color: #e0e0e0;
        --code-bg: #f8f9fa;
    }
    
    * {
        box-sizing: border-box;
    }
    
    body {
        font-family: 'Source Sans Pro', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        line-height: 1.7;
        color: var(--text-color);
        max-width: 900px;
        margin: 0 auto;
        padding: 40px 60px;
        background: white;
    }
    
    /* Cover Page */
    .cover-page {
        text-align: center;
        padding: 100px 40px;
        page-break-after: always;
        min-height: 100vh;
        display: flex;
        flex-direction: column;
        justify-content: center;
        border-bottom: 4px solid var(--primary-color);
    }
    
    .cover-page h1 {
        font-size: 3.5em;
        font-weight: 700;
        color: var(--primary-color);
        margin-bottom: 20px;
        letter-spacing: -1px;
    }
    
    .cover-page .subtitle {
        font-size: 1.5em;
        color: #666;
        margin-bottom: 60px;
    }
    
    .cover-page .version {
        font-size: 1.2em;
        color: #888;
        margin-top: 40px;
    }
    
    .cover-page .logo {
        font-size: 4em;
        margin-bottom: 30px;
    }
    
    /* Table of Contents */
    .toc {
        page-break-after: always;
        padding: 40px 0;
    }
    
    .toc h2 {
        color: var(--primary-color);
        border-bottom: 3px solid var(--primary-color);
        padding-bottom: 10px;
    }
    
    .toc ul {
        list-style: none;
        padding: 0;
    }
    
    .toc li {
        padding: 8px 0;
        border-bottom: 1px dotted var(--border-color);
    }
    
    .toc a {
        color: var(--text-color);
        text-decoration: none;
    }
    
    .toc a:hover {
        color: var(--primary-color);
    }
    
    /* Headings */
    h1 {
        font-size: 2.5em;
        font-weight: 700;
        color: var(--primary-color);
        margin-top: 60px;
        margin-bottom: 20px;
        page-break-after: avoid;
        border-bottom: 3px solid var(--primary-color);
        padding-bottom: 15px;
    }
    
    h2 {
        font-size: 1.8em;
        font-weight: 600;
        color: var(--secondary-color);
        margin-top: 45px;
        margin-bottom: 15px;
        page-break-after: avoid;
        border-bottom: 2px solid var(--border-color);
        padding-bottom: 10px;
    }
    
    h3 {
        font-size: 1.4em;
        font-weight: 600;
        color: var(--text-color);
        margin-top: 35px;
        margin-bottom: 12px;
        page-break-after: avoid;
    }
    
    h4 {
        font-size: 1.2em;
        font-weight: 600;
        color: #555;
        margin-top: 25px;
        margin-bottom: 10px;
    }
    
    /* Paragraphs and text */
    p {
        margin: 15px 0;
        text-align: justify;
    }
    
    /* Links */
    a {
        color: var(--primary-color);
        text-decoration: none;
    }
    
    a:hover {
        text-decoration: underline;
    }
    
    /* Code blocks */
    pre {
        background: var(--code-bg);
        border: 1px solid var(--border-color);
        border-left: 4px solid var(--primary-color);
        border-radius: 4px;
        padding: 20px;
        overflow-x: auto;
        font-family: 'Source Code Pro', 'Consolas', 'Monaco', monospace;
        font-size: 0.9em;
        line-height: 1.5;
        margin: 20px 0;
        page-break-inside: avoid;
    }
    
    code {
        font-family: 'Source Code Pro', 'Consolas', 'Monaco', monospace;
        background: var(--code-bg);
        padding: 2px 6px;
        border-radius: 3px;
        font-size: 0.9em;
    }
    
    pre code {
        background: none;
        padding: 0;
    }
    
    /* Tables */
    table {
        width: 100%;
        border-collapse: collapse;
        margin: 25px 0;
        font-size: 0.95em;
        page-break-inside: avoid;
    }
    
    th {
        background: var(--primary-color);
        color: white;
        font-weight: 600;
        padding: 14px 12px;
        text-align: left;
    }
    
    td {
        padding: 12px;
        border-bottom: 1px solid var(--border-color);
    }
    
    tr:nth-child(even) {
        background: var(--light-gray);
    }
    
    tr:hover {
        background: #e8f4fc;
    }
    
    /* Lists */
    ul, ol {
        margin: 15px 0;
        padding-left: 30px;
    }
    
    li {
        margin: 8px 0;
    }
    
    /* Blockquotes */
    blockquote {
        border-left: 4px solid var(--accent-color);
        margin: 20px 0;
        padding: 15px 25px;
        background: #fff8f5;
        font-style: italic;
        color: #555;
    }
    
    /* Horizontal rules */
    hr {
        border: none;
        height: 2px;
        background: linear-gradient(to right, var(--primary-color), var(--accent-color));
        margin: 50px 0;
    }
    
    /* Section dividers */
    .section-divider {
        page-break-before: always;
        padding-top: 30px;
    }
    
    /* Chapter headers */
    .chapter-header {
        background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
        color: white;
        padding: 40px;
        margin: 40px -60px;
        text-align: center;
        page-break-before: always;
    }
    
    .chapter-header h1 {
        color: white;
        border: none;
        margin: 0;
        padding: 0;
    }
    
    /* Info boxes */
    .note {
        background: #e7f3ff;
        border-left: 4px solid var(--primary-color);
        padding: 15px 20px;
        margin: 20px 0;
        border-radius: 0 4px 4px 0;
    }
    
    .warning {
        background: #fff3cd;
        border-left: 4px solid #ffc107;
        padding: 15px 20px;
        margin: 20px 0;
        border-radius: 0 4px 4px 0;
    }
    
    /* Print styles */
    @media print {
        body {
            padding: 20px 40px;
        }
        
        pre {
            white-space: pre-wrap;
            word-wrap: break-word;
        }
        
        a {
            color: var(--text-color);
        }
        
        .no-print {
            display: none;
        }
    }
    
    /* Page numbers (for PDF) */
    @page {
        margin: 2cm;
        @bottom-center {
            content: counter(page);
        }
    }
</style>
"""


def read_markdown_file(filepath):
    """Read a markdown file and return its content."""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            return f.read()
    except FileNotFoundError:
        print(f"Warning: File not found: {filepath}")
        return None
    except Exception as e:
        print(f"Error reading {filepath}: {e}")
        return None


def process_markdown(content, base_path=None):
    """Process markdown content and fix relative links."""
    if content is None:
        return ""
    
    # Remove the first H1 heading if present (we'll add chapter headers)
    # content = re.sub(r'^# .+\n', '', content, count=1)
    
    return content


def create_cover_page(title, subtitle, version):
    """Generate HTML cover page."""
    return f"""
    <div class="cover-page">
        <div class="logo">⚗️</div>
        <h1>{title}</h1>
        <p class="subtitle">{subtitle}</p>
        <p class="version">Version {version}</p>
        <p class="version">Generated: January 2026</p>
    </div>
    """


def create_toc(sections):
    """Generate table of contents."""
    toc_html = '<div class="toc"><h2>Table of Contents</h2><ul>'
    
    for i, (title, _) in enumerate(sections, 1):
        anchor = title.lower().replace(' ', '-').replace('&', 'and')
        toc_html += f'<li><strong>{i}.</strong> <a href="#{anchor}">{title}</a></li>'
    
    toc_html += '</ul></div>'
    return toc_html


def markdown_to_html(md_content):
    """Convert markdown to HTML with syntax highlighting."""
    return markdown2.markdown(
        md_content,
        extras=[
            'fenced-code-blocks',
            'tables',
            'header-ids',
            'toc',
            'code-friendly',
            'cuddled-lists'
        ]
    )


def generate_manual(sections, title, subtitle, version, output_file):
    """Generate the complete HTML manual."""
    
    # Start building HTML
    html_parts = [
        '<!DOCTYPE html>',
        '<html lang="en">',
        '<head>',
        '<meta charset="UTF-8">',
        f'<title>{title}</title>',
        CSS_STYLE,
        MATHJAX_CONFIG,
        '</head>',
        '<body>',
        create_cover_page(title, subtitle, version),
        create_toc(sections)
    ]
    
    # Add each section
    for section_title, content in sections:
        anchor = section_title.lower().replace(' ', '-').replace('&', 'and')
        html_parts.append(f'<div class="section-divider" id="{anchor}">')
        html_parts.append(f'<div class="chapter-header"><h1>{section_title}</h1></div>')
        html_parts.append(markdown_to_html(content))
        html_parts.append('</div>')
    
    # Add GitHub Pages link handler script before closing body
    html_parts.append(GITHUB_PAGES_SCRIPT)
    html_parts.extend(['</body>', '</html>'])
    
    # Write output
    output_path = OUTPUT_DIR / output_file
    OUTPUT_DIR.mkdir(exist_ok=True)
    
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(html_parts))
    
    print(f"Generated: {output_path}")
    return output_path


# Complete document structure from REFERENCE_MANUAL_INDEX.md
FULL_MANUAL_STRUCTURE = {
    "Part I: Getting Started": {
        "Chapter 1: Introduction": [
            ("Overview", "README.md"),
            ("Modules", "modules.md"),
        ],
        "Chapter 2: Installation & Setup": [
            ("Getting Started", "wiki/getting_started.md"),
            ("GitHub Setup", "wiki/Getting-started-with-NeqSim-and-Github.md"),
            ("Developer Setup", "development/DEVELOPER_SETUP.md"),
        ],
        "Chapter 3: Quick Start Examples": [
            ("Usage Examples", "wiki/usage_examples.md"),
            ("FAQ", "wiki/faq.md"),
            ("Wiki Index", "wiki/index.md"),
        ],
    },
    "Part II: Thermodynamics": {
        "Chapter 4: Fundamentals": [
            ("Thermo Overview", "thermo/README.md"),
            ("Thermodynamics Guide", "wiki/thermodynamics_guide.md"),
            ("System Types", "thermo/system/README.md"),
        ],
        "Chapter 5: Fluid Creation & Components": [
            ("Fluid Creation Guide", "thermo/fluid_creation_guide.md"),
            ("Component Database", "thermo/component_database_guide.md"),
            ("Component Package", "thermo/component/README.md"),
            ("Mathematical Models", "thermo/mathematical_models.md"),
        ],
        "Chapter 6: Equations of State": [
            ("Thermodynamic Models", "thermo/thermodynamic_models.md"),
            ("GERG-2008", "thermo/gerg2008_eoscg.md"),
            ("Mixing Rules", "thermo/mixing_rules_guide.md"),
            ("Mixing Rule Package", "thermo/mixingrule/README.md"),
            ("Phase Package", "thermo/phase/README.md"),
            ("Electrolyte CPA", "thermo/ElectrolyteCPAModel.md"),
        ],
        "Chapter 7: Flash Calculations": [
            ("Flash Guide", "thermo/flash_calculations_guide.md"),
            ("Flash Equations", "wiki/flash_equations_and_tests.md"),
            ("Thermo Operations", "thermo/thermodynamic_operations.md"),
            ("TP Flash Algorithm", "thermodynamicoperations/TPflash_algorithm.md"),
            ("Thermo Ops Overview", "thermodynamicoperations/README.md"),
        ],
        "Chapter 8: Fluid Characterization": [
            ("Characterization", "wiki/fluid_characterization.md"),
            ("TBP Fractions", "wiki/tbp_fraction_models.md"),
            ("PVT Characterization", "thermo/pvt_fluid_characterization.md"),
            ("Characterization Package", "thermo/characterization/README.md"),
            ("Combining Methods", "thermo/characterization/fluid_characterization_combining.md"),
            ("Char Mathematics", "pvtsimulation/fluid_characterization_mathematics.md"),
        ],
        "Chapter 9: Physical Properties": [
            ("Properties Overview", "thermo/physical_properties.md"),
            ("Physical Props Module", "physical_properties/README.md"),
            ("Viscosity Models", "wiki/viscosity_models.md"),
            ("Viscosity Detailed", "physical_properties/viscosity_models.md"),
            ("Density Models", "physical_properties/density_models.md"),
            ("Thermal Conductivity", "physical_properties/thermal_conductivity_models.md"),
            ("Diffusivity", "physical_properties/diffusivity_models.md"),
            ("Interfacial Props", "physical_properties/interfacial_properties.md"),
            ("Scale Potential", "physical_properties/scale_potential.md"),
            ("Steam Tables", "wiki/steam_tables_if97.md"),
            ("Thermodynamic Workflows", "thermo/thermodynamic_workflows.md"),
            ("Interaction Tables", "thermo/inter_table_guide.md"),
        ],
        "Chapter 10: Hydrates & Flow Assurance": [
            ("Hydrate Models", "thermo/hydrate_models.md"),
            ("Hydrate Flash", "thermodynamicoperations/hydrate_flash_operations.md"),
        ],
    },
    "Part III: Process Simulation": {
        "Chapter 11: Process Fundamentals": [
            ("Process Overview", "process/README.md"),
            ("Process Guide", "wiki/process_simulation.md"),
            ("Advanced Process", "wiki/advanced_process_simulation.md"),
            ("Logical Operations", "wiki/logical_unit_operations.md"),
            ("Process Design", "process/process_design_guide.md"),
        ],
        "Chapter 12: Process Systems & Models": [
            ("ProcessModel Overview", "process/processmodel/README.md"),
            ("ProcessSystem", "process/processmodel/process_system.md"),
            ("ProcessModel", "process/processmodel/process_model.md"),
            ("ProcessModule", "process/processmodel/process_module.md"),
            ("Graph Simulation", "process/processmodel/graph_simulation.md"),
            ("Diagram Export", "process/processmodel/diagram_export.md"),
            ("DEXPI Architecture", "process/processmodel/DIAGRAM_ARCHITECTURE_DEXPI_SYNERGY.md"),
        ],
        "Chapter 13: Streams & Mixers": [
            ("Streams", "process/equipment/streams.md"),
            ("Mixers/Splitters", "process/equipment/mixers_splitters.md"),
            ("Equipment Overview", "process/equipment/README.md"),
        ],
        "Chapter 14: Separation Equipment": [
            ("Separators", "process/equipment/separators.md"),
            ("Distillation", "process/equipment/distillation.md"),
            ("Distillation Wiki", "wiki/distillation_column.md"),
            ("Absorbers", "process/equipment/absorbers.md"),
            ("Membrane", "wiki/membrane_separation.md"),
            ("Membrane Equipment", "process/equipment/membranes.md"),
            ("Filters", "process/equipment/filters.md"),
        ],
        "Chapter 15: Rotating Equipment": [
            ("Compressors", "process/equipment/compressors.md"),
            ("Compressor Curves", "process/equipment/compressor_curves.md"),
            ("Compressor Design", "process/CompressorMechanicalDesign.md"),
            ("Pumps", "process/equipment/pumps.md"),
            ("Pump Guide", "wiki/pump_usage_guide.md"),
            ("Pump Theory", "wiki/pump_theory_and_implementation.md"),
            ("Expanders", "process/equipment/expanders.md"),
            ("Turboexpander", "simulation/turboexpander_compressor_model.md"),
            ("Ejectors", "process/equipment/ejectors.md"),
        ],
        "Chapter 16: Heat Transfer Equipment": [
            ("Heat Exchangers", "process/equipment/heat_exchangers.md"),
            ("Air Cooler", "wiki/air_cooler.md"),
            ("Water Cooler", "wiki/water_cooler.md"),
            ("Steam Heater", "wiki/steam_heater.md"),
            ("Mechanical Design", "wiki/heat_exchanger_mechanical_design.md"),
        ],
        "Chapter 17: Valves & Flow Control": [
            ("Valves", "process/equipment/valves.md"),
            ("Valve Design", "process/ValveMechanicalDesign.md"),
            ("Flow Meters", "wiki/flow_meter_models.md"),
            ("Venturi", "wiki/venturi_calculation.md"),
            ("Tanks", "process/equipment/tanks.md"),
        ],
        "Chapter 18: Special Equipment": [
            ("Reactors", "process/equipment/reactors.md"),
            ("Gibbs Reactor", "wiki/gibbs_reactor.md"),
            ("Electrolyzers", "process/equipment/electrolyzers.md"),
            ("CO2 Electrolyzer", "pvtsimulation/CO2ElectrolyzerExample.md"),
            ("Flares", "process/equipment/flares.md"),
            ("Adsorbers", "process/equipment/adsorbers.md"),
            ("Power Generation", "process/equipment/power_generation.md"),
            ("Diff. Pressure", "process/equipment/differential_pressure.md"),
            ("Manifolds", "process/equipment/manifolds.md"),
            ("Battery Storage", "wiki/battery_storage.md"),
            ("Solar Panel", "wiki/solar_panel.md"),
        ],
        "Chapter 19: Wells, Pipelines & Subsea": [
            ("Wells", "process/equipment/wells.md"),
            ("Well Simulation", "simulation/well_simulation_guide.md"),
            ("Well & Choke", "simulation/well_and_choke_simulation.md"),
            ("Pipelines", "process/equipment/pipelines.md"),
            ("Beggs & Brill", "process/PipeBeggsAndBrills.md"),
            ("Networks", "process/equipment/networks.md"),
            ("Reservoirs", "process/equipment/reservoirs.md"),
            ("Subsea Systems", "process/equipment/subsea_systems.md"),
        ],
        "Chapter 20: Utility Equipment": [
            ("Utility Overview", "process/equipment/util/README.md"),
            ("Adjusters", "process/equipment/util/adjusters.md"),
            ("Recycles", "process/equipment/util/recycles.md"),
            ("Calculators", "process/equipment/util/calculators.md"),
        ],
        "Chapter 21: Process Control": [
            ("Controllers", "process/controllers.md"),
            ("Process Control", "wiki/process_control.md"),
            ("Dynamic Simulation Guide", "simulation/dynamic_simulation_guide.md"),
            ("Transient Simulation", "wiki/process_transient_simulation_guide.md"),
        ],
        "Chapter 22: Mechanical Design": [
            ("Mechanical Design", "process/mechanical_design.md"),
            ("Design Standards", "process/mechanical_design_standards.md"),
            ("Design Database", "process/mechanical_design_database.md"),
            ("TORG Integration", "process/torg_integration.md"),
            ("Field Development", "process/field_development_orchestration.md"),
        ],
        "Chapter 23: Serialization & Persistence": [
            ("Process Serialization", "simulation/process_serialization.md"),
        ],
    },
    "Part IV: Pipeline & Multiphase Flow": {
        "Chapter 24: Pipeline Fundamentals": [
            ("Fluid Mechanics Overview", "fluidmechanics/README.md"),
            ("Pipeline Index", "wiki/pipeline_index.md"),
            ("Flow Equations", "wiki/pipeline_flow_equations.md"),
            ("Single Phase Flow", "fluidmechanics/single_phase_pipe_flow.md"),
        ],
        "Chapter 25: Pressure Drop Calculations": [
            ("Pressure Drop", "wiki/pipeline_pressure_drop.md"),
            ("Beggs & Brill", "wiki/beggs_and_brill_correlation.md"),
            ("Friction Factors", "wiki/friction_factor_models.md"),
        ],
        "Chapter 26: Heat Transfer in Pipelines": [
            ("Heat Transfer", "wiki/pipeline_heat_transfer.md"),
            ("Heat Transfer Module", "fluidmechanics/heat_transfer.md"),
            ("Pipe Wall", "wiki/pipe_wall_heat_transfer.md"),
            ("Interphase", "fluidmechanics/InterphaseHeatMassTransfer.md"),
            ("Mass Transfer", "fluidmechanics/mass_transfer.md"),
        ],
        "Chapter 27: Two-Phase & Multiphase Flow": [
            ("Two-Phase Model", "fluidmechanics/TwoPhasePipeFlowModel.md"),
            ("Two-Fluid Model", "wiki/two_fluid_model.md"),
            ("Multiphase Transient", "wiki/multiphase_transient_model.md"),
            ("Transient Pipe Wiki", "wiki/transient_multiphase_pipe.md"),
            ("Development Plan", "fluidmechanics/TwoPhasePipeFlowSystem_Development_Plan.md"),
        ],
        "Chapter 28: Transient Pipeline Simulation": [
            ("Transient Simulation", "wiki/pipeline_transient_simulation.md"),
            ("Model Recommendations", "wiki/pipeline_model_recommendations.md"),
            ("Water Hammer", "wiki/water_hammer_implementation.md"),
        ],
    },
    "Part V: Safety & Reliability": {
        "Chapter 29: Safety Overview": [
            ("Safety Overview", "safety/README.md"),
            ("Safety Roadmap", "safety/SAFETY_SIMULATION_ROADMAP.md"),
            ("Layered Architecture", "safety/layered_safety_architecture.md"),
            ("Process Safety", "process/safety/README.md"),
        ],
        "Chapter 30: Alarm Systems": [
            ("Alarm System Guide", "safety/alarm_system_guide.md"),
            ("Alarm Logic Example", "safety/alarm_triggered_logic_example.md"),
            ("ESD Fire Alarm", "wiki/esd_fire_alarm_system.md"),
        ],
        "Chapter 31: Pressure Relief Systems": [
            ("PSV Dynamic Sizing Wiki", "wiki/psv_dynamic_sizing_example.md"),
            ("PSV Dynamic Sizing", "safety/psv_dynamic_sizing_example.md"),
            ("PSD Valve Trip", "wiki/psd_valve_hihi_trip.md"),
            ("Rupture Disks", "safety/rupture_disk_dynamic_behavior.md"),
        ],
        "Chapter 32: HIPPS Systems": [
            ("HIPPS Summary", "safety/HIPPS_SUMMARY.md"),
            ("HIPPS Implementation", "safety/hipps_implementation.md"),
            ("HIPPS Safety Logic", "safety/hipps_safety_logic.md"),
        ],
        "Chapter 33: ESD & Fire Systems": [
            ("ESD Blowdown", "safety/ESD_BLOWDOWN_SYSTEM.md"),
            ("Pressure Monitoring", "safety/PRESSURE_MONITORING_ESD.md"),
            ("Fire Heat Transfer", "safety/fire_heat_transfer_enhancements.md"),
            ("Fire Blowdown", "safety/fire_blowdown_capabilities.md"),
        ],
        "Chapter 34: Integrated Safety Systems": [
            ("Integrated Safety", "safety/INTEGRATED_SAFETY_SYSTEMS.md"),
            ("SIS Logic", "safety/sis_logic_implementation.md"),
            ("Choke Protection", "wiki/choke_collapse_psd_protection.md"),
            ("Safety Chain Tests", "safety/integration_safety_chain_tests.md"),
            ("Scenario Generation", "process/safety/scenario-generation.md"),
        ],
    },
    "Part VI: PVT & Flow Assurance": {
        "Chapter 35: PVT Simulation": [
            ("PVT Overview", "pvtsimulation/README.md"),
            ("PVT Workflows", "wiki/pvt_simulation_workflows.md"),
            ("PVT Workflow Module", "pvtsimulation/pvt_workflow.md"),
            ("Property Flash", "wiki/property_flash_workflows.md"),
            ("Whitson Reader", "pvtsimulation/whitson_pvt_reader.md"),
            ("Solution Gas-Water Ratio", "pvtsimulation/SolutionGasWaterRatio.md"),
        ],
        "Chapter 36: Black Oil Models": [
            ("Black Oil Overview", "blackoil/README.md"),
            ("Flash Playbook", "wiki/black_oil_flash_playbook.md"),
            ("Black Oil Export", "pvtsimulation/blackoil_pvt_export.md"),
        ],
        "Chapter 37: Flow Assurance": [
            ("Flow Assurance", "pvtsimulation/flowassurance/README.md"),
            ("Asphaltene Modeling", "pvtsimulation/flowassurance/asphaltene_modeling.md"),
            ("Asphaltene CPA", "pvtsimulation/flowassurance/asphaltene_cpa_calculations.md"),
            ("De Boer Screening", "pvtsimulation/flowassurance/asphaltene_deboer_screening.md"),
            ("Method Comparison", "pvtsimulation/flowassurance/asphaltene_method_comparison.md"),
            ("Parameter Fitting", "pvtsimulation/flowassurance/asphaltene_parameter_fitting.md"),
            ("Validation", "pvtsimulation/flowassurance/asphaltene_validation.md"),
        ],
        "Chapter 38: Gas Quality": [
            ("Gas Quality Standards", "wiki/gas_quality_standards_from_tests.md"),
            ("Humid Air", "wiki/humid_air_math.md"),
        ],
    },
    "Part VII: Standards & Quality": {
        "Chapter 39: ISO Standards": [
            ("Standards Overview", "standards/README.md"),
            ("ISO 6976", "standards/iso6976_calorific_values.md"),
            ("ISO 6578", "standards/iso6578_lng_density.md"),
            ("ISO 15403", "standards/iso15403_cng_quality.md"),
            ("Dew Point", "standards/dew_point_standards.md"),
            ("ASTM D6377", "standards/astm_d6377_rvp.md"),
            ("Sales Contracts", "standards/sales_contracts.md"),
        ],
    },
    "Part VIII: Advanced Topics": {
        "Chapter 40: Future Infrastructure": [
            ("Future Infrastructure", "process/future-infrastructure.md"),
            ("API Reference", "process/future-api-reference.md"),
        ],
        "Chapter 41: Integration & APIs": [
            ("Digital Twins", "integration/README.md"),
            ("AI Platform Integration", "integration/ai_platform_integration.md"),
            ("AI Validation Framework", "integration/ai_validation_framework.md"),
        ],
        "Chapter 42: Chemical Reactions": [
            ("Chemical Reactions", "chemicalreactions/README.md"),
        ],
        "Chapter 43: Statistics & Fitting": [
            ("Statistics Overview", "statistics/README.md"),
        ],
        "Chapter 44: Utilities": [
            ("Utilities Overview", "util/README.md"),
        ],
        "Chapter 45: Math Library": [
            ("Math Library", "mathlib/README.md"),
        ],
    },
}


def load_all_sections():
    """Load all documentation sections from the manual structure."""
    sections = []
    file_count = 0
    missing_count = 0
    
    for part_name, chapters in FULL_MANUAL_STRUCTURE.items():
        print(f"\n{part_name}")
        
        for chapter_name, documents in chapters.items():
            print(f"  {chapter_name}")
            chapter_content = []
            
            for doc_title, doc_path in documents:
                full_path = DOCS_DIR / doc_path
                content = read_markdown_file(full_path)
                
                if content:
                    # Add sub-section header
                    chapter_content.append(f"\n\n## {doc_title}\n\n")
                    chapter_content.append(content)
                    file_count += 1
                    print(f"    ✓ {doc_title}")
                else:
                    missing_count += 1
                    print(f"    ✗ {doc_title} (missing)")
            
            if chapter_content:
                combined_content = "\n".join(chapter_content)
                sections.append((chapter_name, combined_content))
    
    print(f"\n{'='*40}")
    print(f"Loaded: {file_count} files")
    print(f"Missing: {missing_count} files")
    
    return sections


def main():
    """Main function to generate the full reference manual."""
    
    print("=" * 60)
    print("  NeqSim Reference Manual Generator")
    print("  Full Manual with Equation Support")
    print("=" * 60)
    
    # Load all sections
    sections = load_all_sections()
    
    if not sections:
        print("Error: No content found!")
        return
    
    # Generate the complete manual
    output_file = generate_manual(
        sections=sections,
        title="NeqSim Reference Manual",
        subtitle="Complete Technical Documentation",
        version="3.0.0",
        output_file="neqsim_reference_manual.html"
    )
    
    print(f"\n{'='*60}")
    print(f"SUCCESS! Full reference manual generated.")
    print(f"Output: {output_file}")
    print(f"\nOpen in browser to view. Use Ctrl+P to print/save as PDF.")
    print("=" * 60)


if __name__ == "__main__":
    main()
