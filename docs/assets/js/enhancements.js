/**
 * NeqSim Documentation Enhancements
 * - Copy button for code blocks
 * - Auto-generated table of contents
 * - Edit on GitHub link
 * - Mobile-friendly dropdown menus
 */

(function() {
  'use strict';

  document.addEventListener('DOMContentLoaded', function() {
    addCopyButtons();
    generateTableOfContents();
    addEditLink();
    initMobileDropdowns();
  });

  /**
   * Initialize mobile-friendly dropdown menus
   * Adds click/touch support for dropdown buttons since :hover doesn't work on touch devices
   */
  function initMobileDropdowns() {
    var dropdownBtns = document.querySelectorAll('.nav-dropdown-btn');
    var isTouchDevice = 'ontouchstart' in window || navigator.maxTouchPoints > 0;
    
    dropdownBtns.forEach(function(btn) {
      var dropdown = btn.closest('.nav-dropdown');
      var content = dropdown.querySelector('.nav-dropdown-content');
      
      // Function to toggle dropdown
      function toggleDropdown(e) {
        e.preventDefault();
        e.stopPropagation();
        
        // Close all other dropdowns first
        document.querySelectorAll('.nav-dropdown').forEach(function(d) {
          if (d !== dropdown) {
            d.classList.remove('is-open');
          }
        });
        
        // Toggle this dropdown
        dropdown.classList.toggle('is-open');
      }
      
      // Handle both click and touch events
      btn.addEventListener('click', toggleDropdown);
      
      // For touch devices, also listen for touchend to ensure responsiveness
      if (isTouchDevice) {
        btn.addEventListener('touchend', function(e) {
          // Prevent the click event from also firing
          e.preventDefault();
          toggleDropdown(e);
        }, { passive: false });
      }
    });
    
    // Close dropdowns when clicking/touching outside
    function closeAllDropdowns(e) {
      if (!e.target.closest('.nav-dropdown')) {
        document.querySelectorAll('.nav-dropdown').forEach(function(d) {
          d.classList.remove('is-open');
        });
      }
    }
    
    document.addEventListener('click', closeAllDropdowns);
    if (isTouchDevice) {
      document.addEventListener('touchend', closeAllDropdowns);
    }
    
    // Close dropdowns when pressing Escape
    document.addEventListener('keydown', function(e) {
      if (e.key === 'Escape') {
        document.querySelectorAll('.nav-dropdown').forEach(function(d) {
          d.classList.remove('is-open');
        });
      }
    });
    
    // Close dropdown when a link inside is clicked/touched
    document.querySelectorAll('.nav-dropdown-content a').forEach(function(link) {
      link.addEventListener('click', function() {
        document.querySelectorAll('.nav-dropdown').forEach(function(d) {
          d.classList.remove('is-open');
        });
      });
    });
  }

  /**
   * Add copy buttons to all code blocks
   */
  function addCopyButtons() {
    var codeBlocks = document.querySelectorAll('pre');
    
    codeBlocks.forEach(function(pre) {
      // Skip if already has button
      if (pre.querySelector('.copy-button')) return;
      
      // Create wrapper for positioning
      var wrapper = document.createElement('div');
      wrapper.className = 'code-block-wrapper';
      pre.parentNode.insertBefore(wrapper, pre);
      wrapper.appendChild(pre);
      
      // Create copy button
      var button = document.createElement('button');
      button.className = 'copy-button';
      button.setAttribute('aria-label', 'Copy code');
      button.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>';
      
      button.addEventListener('click', function() {
        var code = pre.querySelector('code');
        var text = code ? code.textContent : pre.textContent;
        
        navigator.clipboard.writeText(text).then(function() {
          button.classList.add('copied');
          button.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"></polyline></svg>';
          
          setTimeout(function() {
            button.classList.remove('copied');
            button.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>';
          }, 2000);
        }).catch(function(err) {
          console.error('Failed to copy:', err);
        });
      });
      
      wrapper.appendChild(button);
    });
  }

  /**
   * Generate table of contents for pages with multiple headings
   */
  function generateTableOfContents() {
    var content = document.querySelector('.main-content');
    if (!content) return;
    
    var headings = content.querySelectorAll('h2, h3');
    if (headings.length < 3) return; // Only show TOC if 3+ headings
    
    // Create TOC container
    var toc = document.createElement('nav');
    toc.className = 'toc';
    toc.setAttribute('aria-label', 'Table of Contents');
    
    var tocTitle = document.createElement('div');
    tocTitle.className = 'toc-title';
    tocTitle.textContent = 'On this page';
    toc.appendChild(tocTitle);
    
    var tocList = document.createElement('ul');
    tocList.className = 'toc-list';
    
    headings.forEach(function(heading, index) {
      // Ensure heading has an ID
      if (!heading.id) {
        heading.id = 'heading-' + index;
      }
      
      var li = document.createElement('li');
      li.className = 'toc-item toc-' + heading.tagName.toLowerCase();
      
      var link = document.createElement('a');
      link.href = '#' + heading.id;
      link.textContent = heading.textContent;
      link.addEventListener('click', function(e) {
        // Smooth scroll
        e.preventDefault();
        heading.scrollIntoView({ behavior: 'smooth' });
        history.pushState(null, null, '#' + heading.id);
      });
      
      li.appendChild(link);
      tocList.appendChild(li);
    });
    
    toc.appendChild(tocList);
    
    // Insert TOC at the beginning of content
    var firstHeading = content.querySelector('h1, h2');
    if (firstHeading && firstHeading.tagName === 'H1') {
      firstHeading.parentNode.insertBefore(toc, firstHeading.nextSibling);
    } else {
      content.insertBefore(toc, content.firstChild);
    }
    
    // Highlight current section on scroll
    var tocLinks = toc.querySelectorAll('a');
    var observer = new IntersectionObserver(function(entries) {
      entries.forEach(function(entry) {
        if (entry.isIntersecting) {
          tocLinks.forEach(function(link) {
            link.classList.remove('active');
            if (link.getAttribute('href') === '#' + entry.target.id) {
              link.classList.add('active');
            }
          });
        }
      });
    }, { rootMargin: '-20% 0px -80% 0px' });
    
    headings.forEach(function(heading) {
      observer.observe(heading);
    });
  }

  /**
   * Add "Edit this page on GitHub" link
   */
  function addEditLink() {
    var footer = document.querySelector('.site-footer');
    if (!footer) return;
    
    // Try to determine the file path from the URL
    var path = window.location.pathname;
    var baseUrl = document.querySelector('meta[name="baseurl"]');
    var base = baseUrl ? baseUrl.content : '/neqsim';
    
    // Remove base URL
    if (path.startsWith(base)) {
      path = path.substring(base.length);
    }
    
    // Convert to source file path
    if (path === '/' || path === '') {
      path = '/index.md';
    } else if (path.endsWith('/')) {
      path = path + 'index.md';
    } else if (path.endsWith('.html')) {
      path = path.replace('.html', '.md');
    } else if (!path.endsWith('.md')) {
      path = path + '.md';
    }
    
    var editUrl = 'https://github.com/equinor/neqsim/edit/master/docs' + path;
    
    var editLink = document.createElement('div');
    editLink.className = 'edit-link';
    editLink.innerHTML = '<a href="' + editUrl + '" target="_blank" rel="noopener">' +
      '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">' +
      '<path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>' +
      '<path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>' +
      '</svg> Edit this page on GitHub</a>';
    
    footer.insertBefore(editLink, footer.firstChild);
  }
})();
