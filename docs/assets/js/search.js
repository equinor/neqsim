/**
 * NeqSim Documentation Search
 * Client-side search using Lunr.js
 * Compatible with iOS Safari and all modern browsers
 */

(function() {
  'use strict';

  var searchIndex = null;
  var searchData = null;
  var searchInput = null;
  var searchResults = null;
  var searchOverlay = null;
  var isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) && !window.MSStream;

  // Polyfill for Element.closest (for older iOS versions)
  if (!Element.prototype.closest) {
    Element.prototype.closest = function(s) {
      var el = this;
      do {
        if (el.matches(s)) return el;
        el = el.parentElement || el.parentNode;
      } while (el !== null && el.nodeType === 1);
      return null;
    };
  }

  // Initialize search when DOM is ready
  document.addEventListener('DOMContentLoaded', function() {
    searchInput = document.getElementById('search-input');
    searchResults = document.getElementById('search-results');
    searchOverlay = document.getElementById('search-overlay');

    if (!searchInput || !searchResults) {
      console.log('Search elements not found');
      return;
    }

    // Load search index
    loadSearchIndex();

    // Set up event listeners - use 'input' event for real-time search
    searchInput.addEventListener('input', debounce(performSearch, 250));
    searchInput.addEventListener('focus', function() {
      if (searchInput.value.trim().length >= 2) {
        performSearch();
      }
    });
    
    // iOS-friendly touch handling - close results when tapping outside
    document.addEventListener('click', handleOutsideClick);
    document.addEventListener('touchend', handleOutsideClick);

    // Keyboard navigation
    searchInput.addEventListener('keydown', handleKeyboard);

    // Keyboard shortcut: Ctrl+K or / to focus search (desktop only)
    if (!isIOS) {
      document.addEventListener('keydown', function(e) {
        if ((e.ctrlKey && e.key === 'k') || (e.key === '/' && !isInputFocused())) {
          e.preventDefault();
          searchInput.focus();
          searchInput.select();
        }
        if (e.key === 'Escape') {
          hideResults();
          searchInput.blur();
        }
      });
    }
  });

  function handleOutsideClick(e) {
    var target = e.target;
    var searchContainer = document.querySelector('.search-container');
    if (searchContainer && !searchContainer.contains(target)) {
      hideResults();
    }
  }

  function isInputFocused() {
    var active = document.activeElement;
    return active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA');
  }

  function loadSearchIndex() {
    var baseUrl = document.querySelector('meta[name="baseurl"]');
    var base = baseUrl ? baseUrl.content : '/neqsim';
    
    fetch(base + '/search-index.json')
      .then(function(response) {
        if (!response.ok) throw new Error('Search index not found');
        return response.json();
      })
      .then(function(data) {
        searchData = data;
        buildIndex(data);
        console.log('Search index loaded: ' + data.length + ' documents');
      })
      .catch(function(error) {
        console.error('Failed to load search index:', error);
      });
  }

  function buildIndex(data) {
    searchIndex = lunr(function() {
      this.ref('url');
      this.field('title', { boost: 10 });
      this.field('description', { boost: 5 });
      this.field('content');
      this.field('tags', { boost: 3 });

      // Remove stemmer from both pipelines so exact words are matched.
      // Without this, the index stores unstemmed tokens while the search
      // pipeline stems query terms, causing mismatches (e.g. "valve" -> "valv").
      this.pipeline.remove(lunr.stemmer);
      this.searchPipeline.remove(lunr.stemmer);
      
      var self = this;
      data.forEach(function(doc) {
        self.add(doc);
      });
    });
  }

  function performSearch() {
    var query = searchInput.value.trim();
    
    if (!query || !searchIndex) {
      hideResults();
      return;
    }

    if (query.length < 2) {
      hideResults();
      return;
    }

    try {
      // Try exact match first, then fuzzy
      var results = searchIndex.search(query);
      
      // If no results, try with wildcards
      if (results.length === 0) {
        results = searchIndex.search(query + '*');
      }
      
      // If still no results, try fuzzy matching
      if (results.length === 0) {
        results = searchIndex.search(query + '~1');
      }

      displayResults(results, query);
    } catch (e) {
      // Handle search syntax errors
      try {
        var escaped = query.replace(/[+\-:^~*()[\]{}]/g, '\\$&');
        var results = searchIndex.search(escaped);
        displayResults(results, query);
      } catch (e2) {
        hideResults();
      }
    }
  }

  function displayResults(results, query) {
    if (results.length === 0) {
      searchResults.innerHTML = '<div class="search-no-results">No results found for "' + escapeHtml(query) + '"</div>';
      showResults();
      return;
    }

    var html = '<ul class="search-results-list">';
    
    // Limit to top 10 results
    var topResults = results.slice(0, 10);
    
    topResults.forEach(function(result, index) {
      var doc = searchData.find(function(d) { return d.url === result.ref; });
      if (!doc) return;

      var title = doc.title || 'Untitled';
      var description = doc.description || '';
      var content = doc.content || '';
      
      // Create snippet with highlighted terms
      var snippet = createSnippet(content, query);
      
      html += '<li class="search-result-item" data-index="' + index + '">';
      html += '<a href="' + doc.url + '">';
      html += '<div class="search-result-title">' + highlightTerms(escapeHtml(title), query) + '</div>';
      if (description) {
        html += '<div class="search-result-description">' + highlightTerms(escapeHtml(description), query) + '</div>';
      }
      if (snippet) {
        html += '<div class="search-result-snippet">' + snippet + '</div>';
      }
      html += '<div class="search-result-url">' + escapeHtml(doc.url) + '</div>';
      html += '</a>';
      html += '</li>';
    });
    
    html += '</ul>';
    
    if (results.length > 10) {
      html += '<div class="search-more-results">' + (results.length - 10) + ' more results...</div>';
    }
    
    searchResults.innerHTML = html;
    showResults();
  }

  function createSnippet(content, query) {
    if (!content) return '';
    
    var words = query.toLowerCase().split(/\s+/);
    var contentLower = content.toLowerCase();
    var snippetLength = 150;
    
    // Find first occurrence of any search term
    var firstIndex = -1;
    for (var i = 0; i < words.length; i++) {
      var idx = contentLower.indexOf(words[i]);
      if (idx !== -1 && (firstIndex === -1 || idx < firstIndex)) {
        firstIndex = idx;
      }
    }
    
    if (firstIndex === -1) {
      return content.substring(0, snippetLength) + '...';
    }
    
    // Extract snippet around the match
    var start = Math.max(0, firstIndex - 50);
    var end = Math.min(content.length, firstIndex + snippetLength);
    
    var snippet = '';
    if (start > 0) snippet += '...';
    snippet += content.substring(start, end);
    if (end < content.length) snippet += '...';
    
    return highlightTerms(escapeHtml(snippet), query);
  }

  function highlightTerms(text, query) {
    var words = query.split(/\s+/).filter(function(w) { return w.length > 1; });
    
    words.forEach(function(word) {
      var regex = new RegExp('(' + escapeRegex(word) + ')', 'gi');
      text = text.replace(regex, '<mark>$1</mark>');
    });
    
    return text;
  }

  function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  function escapeRegex(string) {
    return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  function showResults() {
    if (searchResults.innerHTML) {
      searchResults.classList.add('visible');
      if (searchOverlay) searchOverlay.classList.add('visible');
      
      // iOS Safari fix: force repaint for position:fixed elements
      if (isIOS) {
        searchResults.style.transform = 'translateZ(0)';
      }
    }
  }

  function hideResults() {
    searchResults.classList.remove('visible');
    if (searchOverlay) searchOverlay.classList.remove('visible');
    
    // iOS Safari: blur input to hide keyboard when closing results
    if (isIOS && document.activeElement === searchInput) {
      // Don't blur on iOS - let user control keyboard
    }
  }

  function handleKeyboard(e) {
    var items = searchResults.querySelectorAll('.search-result-item');
    var current = searchResults.querySelector('.search-result-item.active');
    var currentIndex = current ? parseInt(current.dataset.index) : -1;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      var next = Math.min(currentIndex + 1, items.length - 1);
      setActiveResult(items, next);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      var prev = Math.max(currentIndex - 1, 0);
      setActiveResult(items, prev);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      // If a result is selected, go to it
      if (current) {
        var link = current.querySelector('a');
        if (link) link.click();
      } else if (items.length > 0) {
        // Otherwise go to the first result
        var firstLink = items[0].querySelector('a');
        if (firstLink) firstLink.click();
      }
    }
  }

  function setActiveResult(items, index) {
    items.forEach(function(item, i) {
      if (i === index) {
        item.classList.add('active');
      } else {
        item.classList.remove('active');
      }
    });
    // Use simpler scroll for iOS compatibility
    if (items[index]) {
      try {
        items[index].scrollIntoView({ block: 'nearest', behavior: 'auto' });
      } catch (e) {
        // Fallback for older browsers
        items[index].scrollIntoView(false);
      }
    }
  }

  function debounce(func, wait) {
    var timeout;
    return function() {
      var context = this;
      var args = arguments;
      clearTimeout(timeout);
      timeout = setTimeout(function() {
        func.apply(context, args);
      }, wait);
    };
  }
})();
