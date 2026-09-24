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
    var dropdowns = Array.prototype.slice.call(
      document.querySelectorAll('.nav-dropdown')
    );
    var finePointer = window.matchMedia(
      '(hover: hover) and (pointer: fine)'
    ).matches;

    function buttonFor(dropdown) {
      return dropdown.querySelector('.nav-dropdown-btn');
    }

    function setOpen(dropdown, isOpen) {
      var btn = buttonFor(dropdown);
      dropdown.classList.toggle('is-open', isOpen);
      btn.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
    }

    function closeOthers(current) {
      dropdowns.forEach(function(dropdown) {
        if (dropdown !== current) {
          setOpen(dropdown, false);
        }
      });
    }

    function closeAndFocus(dropdown) {
      setOpen(dropdown, false);
      buttonFor(dropdown).focus();
    }

    dropdowns.forEach(function(dropdown) {
      var btn = buttonFor(dropdown);
      var content = dropdown.querySelector('.nav-dropdown-content');
      var links = Array.prototype.slice.call(content.querySelectorAll('a'));

      setOpen(dropdown, false);

      btn.addEventListener('click', function(event) {
        event.preventDefault();
        var shouldOpen = btn.getAttribute('aria-expanded') !== 'true';
        closeOthers(dropdown);
        setOpen(dropdown, shouldOpen);
      });

      btn.addEventListener('keydown', function(event) {
        if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') {
          return;
        }
        event.preventDefault();
        closeOthers(dropdown);
        setOpen(dropdown, true);
        if (links.length) {
          links[event.key === 'ArrowDown' ? 0 : links.length - 1].focus();
        }
      });

      content.addEventListener('keydown', function(event) {
        if (event.key === 'Escape') {
          event.preventDefault();
          event.stopPropagation();
          closeAndFocus(dropdown);
          return;
        }
        if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') {
          return;
        }

        var current = links.indexOf(document.activeElement);
        if (current === -1) {
          return;
        }
        event.preventDefault();
        var direction = event.key === 'ArrowDown' ? 1 : -1;
        var next = (current + direction + links.length) % links.length;
        links[next].focus();
      });

      dropdown.addEventListener('focusout', function(event) {
        if (!dropdown.contains(event.relatedTarget)) {
          setOpen(dropdown, false);
        }
      });

      if (finePointer) {
        dropdown.addEventListener('mouseenter', function() {
          closeOthers(dropdown);
          setOpen(dropdown, true);
        });
        dropdown.addEventListener('mouseleave', function() {
          if (!dropdown.contains(document.activeElement)) {
            setOpen(dropdown, false);
          }
        });
      }

      links.forEach(function(link) {
        link.addEventListener('click', function() {
          setOpen(dropdown, false);
        });
      });
    });

    document.addEventListener('click', function(event) {
      if (!event.target.closest('.nav-dropdown')) {
        closeOthers(null);
      }
    });

    document.addEventListener('keydown', function(event) {
      if (event.key !== 'Escape') {
        return;
      }
      var openDropdown = document.querySelector('.nav-dropdown.is-open');
      if (openDropdown) {
        event.preventDefault();
        closeAndFocus(openDropdown);
      }
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
