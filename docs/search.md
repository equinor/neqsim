---
title: Search Documentation
description: Search across all NeqSim documentation — thermodynamics, process simulation, PVT, standards, safety, field development, and more.
permalink: /search/
---

<div id="search-page">

  <div class="sp-search-box">
    <div class="sp-input-wrapper">
      <svg class="sp-search-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="11" cy="11" r="8"></circle>
        <path d="m21 21-4.35-4.35"></path>
      </svg>
      <input type="text" id="search-page-input" placeholder="Search all documentation..." autocomplete="off" autofocus>
      <span class="sp-shortcut">Ctrl+K</span>
    </div>
  </div>

  <div id="search-facets" class="sp-facets">
    <!-- Populated by JS after search -->
  </div>

  <div id="search-page-results" class="sp-page-results">
    <div class="sp-initial">
      <h3>Search the NeqSim documentation</h3>
      <p>Type at least 2 characters to start searching. Results update as you type.</p>

      <div class="sp-browse">
        <h4>Browse by topic</h4>
        <div class="sp-topic-grid">
          <a href="{{ '/thermo/' | relative_url }}" class="sp-topic-card">
            <span class="sp-topic-icon" style="background:#2196F3;">T</span>
            <span class="sp-topic-label">Thermodynamics</span>
            <span class="sp-topic-desc">EOS, flash, fluids, properties</span>
          </a>
          <a href="{{ '/process/' | relative_url }}" class="sp-topic-card">
            <span class="sp-topic-icon" style="background:#4CAF50;">P</span>
            <span class="sp-topic-label">Process Simulation</span>
            <span class="sp-topic-desc">Equipment, flowsheets, dynamic</span>
          </a>
          <a href="{{ '/pvtsimulation/' | relative_url }}" class="sp-topic-card">
            <span class="sp-topic-icon" style="background:#9C27B0;">V</span>
            <span class="sp-topic-label">PVT Simulation</span>
            <span class="sp-topic-desc">CME, CVD, saturation, swelling</span>
          </a>
          <a href="{{ '/standards/' | relative_url }}" class="sp-topic-card">
            <span class="sp-topic-icon" style="background:#795548;">S</span>
            <span class="sp-topic-label">Standards</span>
            <span class="sp-topic-desc">ISO, API, NORSOK, gas quality</span>
          </a>
          <a href="{{ '/safety/' | relative_url }}" class="sp-topic-card">
            <span class="sp-topic-icon" style="background:#F44336;">!</span>
            <span class="sp-topic-label">Safety Systems</span>
            <span class="sp-topic-desc">Depressuring, PSV, fire cases</span>
          </a>
          <a href="{{ '/fielddevelopment/' | relative_url }}" class="sp-topic-card">
            <span class="sp-topic-icon" style="background:#00BCD4;">F</span>
            <span class="sp-topic-label">Field Development</span>
            <span class="sp-topic-desc">NPV, concept selection, SURF</span>
          </a>
          <a href="{{ '/risk/' | relative_url }}" class="sp-topic-card">
            <span class="sp-topic-icon" style="background:#E91E63;">R</span>
            <span class="sp-topic-label">Risk & Reliability</span>
            <span class="sp-topic-desc">RAM, bowtie, Monte Carlo</span>
          </a>
          <a href="{{ '/examples/' | relative_url }}" class="sp-topic-card">
            <span class="sp-topic-icon" style="background:#8BC34A;">E</span>
            <span class="sp-topic-label">Examples</span>
            <span class="sp-topic-desc">Notebooks, code samples</span>
          </a>
        </div>
      </div>

      <div class="sp-tips">
        <h4>Search tips</h4>
        <ul>
          <li><strong>Single words</strong> work best: <code>separator</code>, <code>hydrate</code>, <code>compressor</code></li>
          <li><strong>Synonyms are expanded</strong> automatically: searching <em>unisim</em> also finds <em>hysys</em> and <em>converter</em></li>
          <li><strong>Filter by section</strong> using the category buttons that appear above results</li>
          <li><strong>Keyboard shortcuts:</strong> <kbd>Ctrl+K</kbd> to focus search, <kbd>↑↓</kbd> to navigate, <kbd>Enter</kbd> to open</li>
        </ul>
      </div>
    </div>
  </div>

</div>
