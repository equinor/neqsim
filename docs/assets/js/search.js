/**
 * NeqSim Documentation Search
 * Client-side search using Lunr.js with synonyms, category facets,
 * and a dedicated search-page mode.
 * Compatible with iOS Safari and all modern browsers.
 */

(function () {
  'use strict';

  /* -------------------------------------------------- */
  /*  Synonym / alias map                               */
  /* -------------------------------------------------- */
  var SYNONYMS = {
    'unisim': ['hysys', 'converter', 'com automation'],
    'hysys': ['unisim', 'converter', 'com automation'],
    'heat exchanger': ['heater', 'cooler', 'heatexchanger', 'shell and tube', 'tema'],
    'heater': ['heat exchanger', 'cooler'],
    'cooler': ['heat exchanger', 'heater'],
    'compressor': ['centrifugal', 'anti-surge', 'antisurge', 'compression'],
    'separator': ['three phase', 'threephase', 'scrubber', 'vessel'],
    'valve': ['throttling', 'choke', 'throttlingvalve'],
    'pipeline': ['pipe', 'beggs', 'brills', 'flowline'],
    'pipe': ['pipeline', 'beggs', 'brills', 'flowline'],
    'distillation': ['column', 'tray', 'deethanizer', 'debutanizer', 'fractionation'],
    'eos': ['equation of state', 'srk', 'peng robinson', 'cpa'],
    'equation of state': ['eos', 'srk', 'peng robinson', 'cpa'],
    'srk': ['eos', 'equation of state', 'soave'],
    'flash': ['tpflash', 'phflash', 'psflash', 'equilibrium'],
    'hydrate': ['hydrates', 'clathrate', 'inhibitor', 'meg', 'methanol'],
    'wax': ['paraffin', 'cloud point', 'pour point', 'deposition'],
    'corrosion': ['erosion', 'co2 corrosion', 'h2s'],
    'pvt': ['cme', 'cvd', 'differential liberation', 'separator test', 'swelling'],
    'co2': ['carbon dioxide', 'carbon capture', 'ccs', 'injection'],
    'dehydration': ['teg', 'glycol', 'water removal', 'dew point'],
    'teg': ['dehydration', 'glycol', 'triethylene glycol'],
    'norsok': ['standard', 'norwegian'],
    'asme': ['pressure vessel', 'boiler'],
    'api': ['standard', 'petroleum'],
    'dnv': ['standard', 'subsea', 'pipeline'],
    'process system': ['processsystem', 'flowsheet', 'simulation'],
    'dynamic': ['transient', 'dynamic simulation', 'time step'],
    'recycle': ['iteration', 'convergence', 'loop'],
    'subsea': ['tieback', 'manifold', 'tree', 'riser', 'umbilical', 'flowline'],
    'well': ['casing', 'tubing', 'drilling', 'completion', 'wellbore'],
    'npv': ['net present value', 'economics', 'irr', 'cashflow'],
    'economics': ['npv', 'irr', 'capex', 'opex', 'cost'],
    'safety': ['depressuring', 'blowdown', 'relief', 'psv', 'fire case'],
    'depressuring': ['blowdown', 'safety', 'relief valve'],
    'json': ['api', 'builder', 'fromjson'],
    'dexpi': ['pfd', 'pid', 'topology', 'proteus', 'xml'],
    'fluid': ['thermo', 'system', 'mixture', 'composition'],
    'mixing rule': ['classic', 'huron vidal', 'mixing'],
    'density': ['specific gravity', 'molar volume', 'costald', 'peneloux', 'rackett', 'volume translation'],
    'costald': ['density', 'liquid density', 'hankinson', 'thomson', 'characteristic volume'],
    'liquid density': ['costald', 'density', 'peneloux', 'rackett', 'specific gravity'],
    'peneloux': ['density', 'volume translation', 'volume shift'],
    'volume translation': ['peneloux', 'density', 'volume shift'],
    'viscosity': ['transport property', 'dynamic viscosity'],
    'thermal conductivity': ['transport property', 'heat transfer'],
    'enthalpy': ['energy', 'heat capacity', 'cp'],
    'phase envelope': ['cricondenbar', 'cricondentherm', 'two phase'],
    'oil': ['crude', 'petroleum', 'characterization', 'plus fraction'],
    'gas': ['natural gas', 'methane', 'lean gas', 'rich gas'],
    'water': ['aqueous', 'brine', 'h2o'],
    'multiphase': ['two phase', 'three phase', 'liquid', 'vapor']
  };

  /* -------------------------------------------------- */
  /*  Section display names & colours                   */
  /* -------------------------------------------------- */
  var SECTION_LABELS = {
    'thermo': 'Thermodynamics',
    'process': 'Process Simulation',
    'pvtsimulation': 'PVT Simulation',
    'physical_properties': 'Physical Properties',
    'fluidmechanics': 'Fluid Mechanics',
    'standards': 'Standards',
    'safety': 'Safety Systems',
    'risk': 'Risk & Reliability',
    'fielddevelopment': 'Field Development',
    'simulation': 'Simulation Guides',
    'integration': 'Integration',
    'examples': 'Examples',
    'tutorials': 'Tutorials',
    'cookbook': 'Cookbook',
    'troubleshooting': 'Troubleshooting',
    'development': 'Development',
    'blackoil': 'Black Oil',
    'emissions': 'Emissions',
    'chemicalreactions': 'Chemical Reactions',
    'calibration': 'Calibration',
    'mathlib': 'Math Library',
    'statistics': 'Statistics',
    'thermodynamicoperations': 'Thermo Operations',
    'quickstart': 'Quick Start',
    'manual': 'Reference Manual',
    'util': 'Utilities',
    'wiki': 'Wiki'
  };

  var SECTION_COLORS = {
    'thermo': '#2196F3',
    'process': '#4CAF50',
    'pvtsimulation': '#9C27B0',
    'physical_properties': '#FF9800',
    'standards': '#795548',
    'safety': '#F44336',
    'risk': '#E91E63',
    'fielddevelopment': '#00BCD4',
    'simulation': '#3F51B5',
    'integration': '#607D8B',
    'examples': '#8BC34A',
    'tutorials': '#009688',
    'cookbook': '#FF5722',
    'troubleshooting': '#FFC107'
  };

  /* -------------------------------------------------- */
  /*  State                                             */
  /* -------------------------------------------------- */
  var searchIndex = null;
  var searchData = null;
  var searchInput = null;
  var searchResults = null;
  var searchOverlay = null;
  var isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) && !window.MSStream;
  var isSearchPage = false;
  var activeFilter = 'all';

  /* -------------------------------------------------- */
  /*  Polyfill                                          */
  /* -------------------------------------------------- */
  if (!Element.prototype.closest) {
    Element.prototype.closest = function (s) {
      var el = this;
      do { if (el.matches(s)) return el; el = el.parentElement || el.parentNode; }
      while (el !== null && el.nodeType === 1);
      return null;
    };
  }

  /* -------------------------------------------------- */
  /*  Initialization                                    */
  /* -------------------------------------------------- */
  document.addEventListener('DOMContentLoaded', function () {
    isSearchPage = !!document.getElementById('search-page');

    searchInput = document.getElementById(isSearchPage ? 'search-page-input' : 'search-input');
    searchResults = document.getElementById(isSearchPage ? 'search-page-results' : 'search-results');
    searchOverlay = document.getElementById('search-overlay');

    if (!searchInput || !searchResults) {
      if (isSearchPage) {
        searchInput = document.getElementById('search-page-input');
        searchResults = document.getElementById('search-page-results');
      }
      if (!searchInput || !searchResults) return;
    }

    loadSearchIndex();

    searchInput.addEventListener('input', debounce(performSearch, 200));
    searchInput.addEventListener('focus', function () {
      if (searchInput.value.trim().length >= 2) performSearch();
    });

    if (!isSearchPage) {
      document.addEventListener('click', handleOutsideClick);
      document.addEventListener('touchend', handleOutsideClick);
    }

    searchInput.addEventListener('keydown', handleKeyboard);

    if (!isIOS) {
      document.addEventListener('keydown', function (e) {
        if ((e.ctrlKey && e.key === 'k') || (e.key === '/' && !isInputFocused())) {
          e.preventDefault();
          searchInput.focus();
          searchInput.select();
        }
        if (e.key === 'Escape' && !isSearchPage) {
          hideResults();
          searchInput.blur();
        }
      });
    }

    if (isSearchPage) {
      var params = new URLSearchParams(window.location.search);
      var q = params.get('q');
      if (q) searchInput.value = q;

      document.addEventListener('click', function (e) {
        var btn = e.target.closest('.search-facet-btn');
        if (btn) {
          activeFilter = btn.getAttribute('data-section') || 'all';
          updateFacetButtons();
          performSearch();
        }
        var sug = e.target.closest('.sp-suggestion-link');
        if (sug) {
          e.preventDefault();
          searchInput.value = sug.getAttribute('data-query');
          activeFilter = 'all';
          performSearch();
        }
      });
    }
  });

  /* -------------------------------------------------- */
  /*  Helpers                                           */
  /* -------------------------------------------------- */
  function handleOutsideClick(e) {
    var c = document.querySelector('.search-container');
    if (c && !c.contains(e.target)) hideResults();
  }

  function isInputFocused() {
    var a = document.activeElement;
    return a && (a.tagName === 'INPUT' || a.tagName === 'TEXTAREA');
  }

  /* -------------------------------------------------- */
  /*  Index loading                                     */
  /* -------------------------------------------------- */
  function loadSearchIndex() {
    var baseEl = document.querySelector('meta[name="baseurl"]');
    var base = baseEl ? baseEl.content : '/neqsim';

    fetch(base + '/search-index.json')
      .then(function (r) { if (!r.ok) throw new Error('not found'); return r.json(); })
      .then(function (data) {
        searchData = data;
        buildIndex(data);
        console.log('Search index loaded: ' + data.length + ' docs');
        if (isSearchPage && searchInput.value.trim().length >= 2) performSearch();
      })
      .catch(function (err) { console.error('Search index error:', err); });
  }

  function buildIndex(data) {
    searchIndex = lunr(function () {
      this.ref('url');
      this.field('title', { boost: 10 });
      this.field('description', { boost: 5 });
      this.field('keywords', { boost: 8 });
      this.field('headings', { boost: 6 });
      this.field('content');

      this.pipeline.remove(lunr.stemmer);
      this.searchPipeline.remove(lunr.stemmer);

      var self = this;
      data.forEach(function (doc) { self.add(doc); });
    });
  }

  /* -------------------------------------------------- */
  /*  Synonym expansion                                 */
  /* -------------------------------------------------- */
  function expandQuery(query) {
    var lower = query.toLowerCase().trim();
    var extra = [];

    Object.keys(SYNONYMS).forEach(function (key) {
      if (lower.indexOf(key) !== -1) {
        SYNONYMS[key].forEach(function (syn) {
          if (extra.indexOf(syn) === -1 && syn !== lower) extra.push(syn);
        });
      }
    });

    lower.split(/\s+/).forEach(function (word) {
      if (SYNONYMS[word]) {
        SYNONYMS[word].forEach(function (syn) {
          if (extra.indexOf(syn) === -1 && syn !== word) extra.push(syn);
        });
      }
    });

    return extra;
  }

  /* -------------------------------------------------- */
  /*  Core search                                       */
  /* -------------------------------------------------- */
  function performSearch() {
    var query = searchInput.value.trim();
    if (!query || query.length < 2 || !searchIndex) { hideResults(); return; }

    var results = runSearch(query);

    if (isSearchPage && activeFilter !== 'all') {
      results = results.filter(function (r) {
        var doc = findDoc(r.ref);
        return doc && doc.section === activeFilter;
      });
    }

    if (isSearchPage) {
      displaySearchPage(results, query);
    } else {
      displayDropdown(results, query);
    }
  }

  function runSearch(query) {
    var results = [];

    try { results = searchIndex.search(query); } catch (e) { /* syntax error */ }

    if (results.length === 0) {
      try {
        var wq = query.split(/\s+/).map(function (t) { return t + '*'; }).join(' ');
        results = searchIndex.search(wq);
      } catch (e) { /* */ }
    }

    if (results.length === 0) {
      try {
        var fq = query.split(/\s+/).map(function (t) { return t + '~1'; }).join(' ');
        results = searchIndex.search(fq);
      } catch (e) { /* */ }
    }

    if (results.length === 0 && query.indexOf(' ') !== -1) {
      results = orSearch(query);
    }

    // Synonym expansion
    var synonyms = expandQuery(query);
    if (synonyms.length > 0) {
      var existing = {};
      results.forEach(function (r) { existing[r.ref] = r.score; });

      synonyms.forEach(function (syn) {
        try {
          var sr = searchIndex.search(syn + '*');
          sr.forEach(function (r) {
            if (!existing[r.ref]) {
              r.score = r.score * 0.6;
              results.push(r);
              existing[r.ref] = r.score;
            }
          });
        } catch (e) { /* */ }
      });

      results.sort(function (a, b) { return b.score - a.score; });
    }

    return results;
  }

  function orSearch(query) {
    var terms = query.split(/\s+/);
    var all = {};
    terms.forEach(function (t) {
      try {
        searchIndex.search(t + '*').forEach(function (r) {
          if (!all[r.ref] || all[r.ref].score < r.score) all[r.ref] = r;
        });
      } catch (e) { /* */ }
    });
    var arr = Object.keys(all).map(function (k) { return all[k]; });
    arr.sort(function (a, b) { return b.score - a.score; });
    return arr;
  }

  function findDoc(url) {
    return searchData && searchData.find(function (d) { return d.url === url; });
  }

  /* -------------------------------------------------- */
  /*  Dropdown display (header bar)                     */
  /* -------------------------------------------------- */
  function displayDropdown(results, query) {
    if (results.length === 0) {
      var syns = expandQuery(query);
      var hint = syns.length > 0 ? '<div class="search-hint">Try: ' + syns.slice(0, 3).join(', ') + '</div>' : '';
      searchResults.innerHTML = '<div class="search-no-results">No results for "<strong>' + escapeHtml(query) + '</strong>"' + hint + '</div>';
      showResults();
      return;
    }

    var top = results.slice(0, 8);
    var html = '<ul class="search-results-list">';
    top.forEach(function (r, i) {
      var doc = findDoc(r.ref);
      if (!doc) return;
      html += renderDropdownItem(doc, query, i);
    });
    html += '</ul>';

    if (results.length > 8) {
      var baseEl = document.querySelector('meta[name="baseurl"]');
      var base = baseEl ? baseEl.content : '/neqsim';
      html += '<a class="search-see-all" href="' + base + '/search/?q=' + encodeURIComponent(query) + '">'
            + 'See all ' + results.length + ' results &rarr;</a>';
    }

    searchResults.innerHTML = html;
    showResults();
  }

  function renderDropdownItem(doc, query, index) {
    var title = doc.title || 'Untitled';
    var desc = doc.description || '';
    var section = doc.section || '';
    var badge = sectionBadge(section);

    var h = '<li class="search-result-item" data-index="' + index + '">';
    h += '<a href="' + doc.url + '">';
    h += '<div class="search-result-header">'
       + '<span class="search-result-title">' + highlightTerms(escapeHtml(title), query) + '</span>'
       + badge + '</div>';
    if (desc) h += '<div class="search-result-description">' + highlightTerms(escapeHtml(truncate(desc, 120)), query) + '</div>';
    h += '</a></li>';
    return h;
  }

  /* -------------------------------------------------- */
  /*  Full search page display                          */
  /* -------------------------------------------------- */
  function displaySearchPage(results, query) {
    if (window.history && window.history.replaceState) {
      var baseEl = document.querySelector('meta[name="baseurl"]');
      var base = baseEl ? baseEl.content : '/neqsim';
      window.history.replaceState(null, '', base + '/search/?q=' + encodeURIComponent(query));
    }

    var container = document.getElementById('search-page-results');
    if (!container) return;

    // Update facets from unfiltered results
    var unfiltered = runSearch(query);
    updateFacetCounts(unfiltered, query);

    if (results.length === 0) {
      container.innerHTML = '<div class="sp-no-results">'
        + '<h3>No results for "' + escapeHtml(query) + '"</h3>'
        + '<p>Try different keywords, check spelling, or browse the categories above.</p>'
        + suggestionsHtml(query) + '</div>';
      return;
    }

    var html = '<div class="sp-result-count">' + results.length + ' result' + (results.length !== 1 ? 's' : '') + ' for "<strong>' + escapeHtml(query) + '</strong>"</div>';
    html += '<div class="sp-results-list">';
    results.forEach(function (r) {
      var doc = findDoc(r.ref);
      if (!doc) return;
      html += renderPageItem(doc, query);
    });
    html += '</div>';
    container.innerHTML = html;
  }

  function renderPageItem(doc, query) {
    var title = doc.title || 'Untitled';
    var desc = doc.description || '';
    var content = doc.content || '';
    var section = doc.section || '';
    var snippet = createSnippet(content, query, 220);
    var badge = sectionBadge(section);

    var h = '<div class="sp-result-item">';
    h += '<a href="' + doc.url + '" class="sp-result-link">';
    h += '<div class="sp-result-header">'
       + '<span class="sp-result-title">' + highlightTerms(escapeHtml(title), query) + '</span>'
       + badge + '</div>';
    if (desc) h += '<div class="sp-result-desc">' + highlightTerms(escapeHtml(desc), query) + '</div>';
    if (snippet) h += '<div class="sp-result-snippet">' + snippet + '</div>';
    h += '<div class="sp-result-url">' + escapeHtml(doc.url) + '</div>';
    h += '</a></div>';
    return h;
  }

  function updateFacetCounts(results, query) {
    var counts = { 'all': results.length };
    results.forEach(function (r) {
      var doc = findDoc(r.ref);
      if (doc && doc.section) counts[doc.section] = (counts[doc.section] || 0) + 1;
    });

    var fc = document.getElementById('search-facets');
    if (!fc) return;

    var html = '<button class="search-facet-btn' + (activeFilter === 'all' ? ' active' : '') + '" data-section="all">All <span class="facet-count">' + (counts['all'] || 0) + '</span></button>';

    var secs = Object.keys(counts).filter(function (k) { return k !== 'all'; });
    secs.sort(function (a, b) { return counts[b] - counts[a]; });

    secs.forEach(function (sec) {
      var label = SECTION_LABELS[sec] || sec.charAt(0).toUpperCase() + sec.slice(1);
      html += '<button class="search-facet-btn' + (activeFilter === sec ? ' active' : '') + '" data-section="' + sec + '">'
            + label + ' <span class="facet-count">' + counts[sec] + '</span></button>';
    });

    fc.innerHTML = html;
  }

  function updateFacetButtons() {
    var btns = document.querySelectorAll('.search-facet-btn');
    btns.forEach(function (btn) {
      btn.classList.toggle('active', btn.getAttribute('data-section') === activeFilter);
    });
  }

  function suggestionsHtml(query) {
    var syns = expandQuery(query);
    if (!syns.length) return '';
    var h = '<div class="sp-suggestions"><strong>Try searching for:</strong> ';
    syns.slice(0, 5).forEach(function (s, i) {
      if (i > 0) h += ', ';
      h += '<a href="#" class="sp-suggestion-link" data-query="' + escapeHtml(s) + '">' + escapeHtml(s) + '</a>';
    });
    return h + '</div>';
  }

  /* -------------------------------------------------- */
  /*  Section badge                                     */
  /* -------------------------------------------------- */
  function sectionBadge(section) {
    if (!section) return '';
    var label = SECTION_LABELS[section] || section;
    var color = SECTION_COLORS[section] || '#607D8B';
    return ' <span class="search-section-badge" style="background:' + color + ';">' + label + '</span>';
  }

  /* -------------------------------------------------- */
  /*  Snippet & highlight                               */
  /* -------------------------------------------------- */
  function createSnippet(content, query, len) {
    if (!content) return '';
    len = len || 200;
    var words = query.toLowerCase().split(/\s+/);
    var cl = content.toLowerCase();
    var best = -1;
    for (var i = 0; i < words.length; i++) {
      var idx = cl.indexOf(words[i]);
      if (idx !== -1 && (best === -1 || idx < best)) best = idx;
    }
    if (best === -1) return escapeHtml(content.substring(0, len)) + '...';
    var start = Math.max(0, best - 60);
    var end = Math.min(content.length, best + len);
    var snippet = '';
    if (start > 0) snippet += '...';
    snippet += content.substring(start, end);
    if (end < content.length) snippet += '...';
    return highlightTerms(escapeHtml(snippet), query);
  }

  function highlightTerms(text, query) {
    query.split(/\s+/).filter(function (w) { return w.length > 1; }).forEach(function (word) {
      text = text.replace(new RegExp('(' + escapeRegex(word) + ')', 'gi'), '<mark>$1</mark>');
    });
    return text;
  }

  function escapeHtml(text) {
    var d = document.createElement('div');
    d.textContent = text;
    return d.innerHTML;
  }
  function escapeRegex(s) { return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }
  function truncate(s, n) { return s.length > n ? s.substring(0, n) + '...' : s; }

  /* -------------------------------------------------- */
  /*  Show / hide (dropdown only)                       */
  /* -------------------------------------------------- */
  function showResults() {
    if (!isSearchPage && searchResults.innerHTML) {
      searchResults.classList.add('visible');
      if (searchOverlay) searchOverlay.classList.add('visible');
      if (isIOS) searchResults.style.transform = 'translateZ(0)';
    }
  }

  function hideResults() {
    if (!isSearchPage) {
      searchResults.classList.remove('visible');
      if (searchOverlay) searchOverlay.classList.remove('visible');
    }
  }

  /* -------------------------------------------------- */
  /*  Keyboard navigation                               */
  /* -------------------------------------------------- */
  function handleKeyboard(e) {
    var sel = isSearchPage ? '.sp-result-item' : '.search-result-item';
    var items = searchResults.querySelectorAll(sel);
    if (!items.length) return;

    var cls = isSearchPage ? 'sp-active' : 'active';
    var curIdx = -1;
    items.forEach(function (el, i) { if (el.classList.contains(cls)) curIdx = i; });

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive(items, Math.min(curIdx + 1, items.length - 1), cls);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive(items, Math.max(curIdx - 1, 0), cls);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      var target = (curIdx >= 0 ? items[curIdx] : items[0]);
      if (target) { var lnk = target.querySelector('a'); if (lnk) lnk.click(); }
    }
  }

  function setActive(items, idx, cls) {
    items.forEach(function (el, i) { el.classList.toggle(cls, i === idx); });
    try { items[idx].scrollIntoView({ block: 'nearest', behavior: 'auto' }); }
    catch (e) { items[idx].scrollIntoView(false); }
  }

  function debounce(fn, wait) {
    var t;
    return function () {
      var ctx = this, args = arguments;
      clearTimeout(t);
      t = setTimeout(function () { fn.apply(ctx, args); }, wait);
    };
  }
})();
