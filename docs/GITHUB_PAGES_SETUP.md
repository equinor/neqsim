---
title: GitHub Pages Setup Guide for NeqSim Documentation
description: This guide explains how to enable GitHub Pages for the NeqSim repository to host the documentation at `https://equinor.github.io/neqsim/`.
---

# GitHub Pages Setup Guide for NeqSim Documentation

This guide explains how to enable GitHub Pages for the NeqSim repository to host the documentation at `https://equinor.github.io/neqsim/`.

## Quick Setup (Repository Admin)

1. Go to the [NeqSim repository settings](https://github.com/equinor/neqsim/settings)
2. Navigate to **Pages** in the left sidebar
3. Under **Source**, select:
   - **Branch**: `main` (or `master`)
   - **Folder**: `/docs`
4. Click **Save**

GitHub will automatically build and deploy the documentation. The site will be available at:
**https://equinor.github.io/neqsim/**

---

## Configuration Files

The following files have been added to enable GitHub Pages:

### `_config.yml`
Jekyll configuration file that defines:
- Site title and description
- Base URL and repository settings
- Theme (jekyll-theme-cayman)
- Markdown processing options
- Navigation structure
- Plugin settings for relative links

### `index.md`
The documentation home page with:
- Quick navigation to all documentation sections
- Code examples
- Links to external resources

---

## How It Works

1. **Jekyll Processing**: GitHub Pages uses Jekyll to process Markdown files into HTML
2. **Theme**: The Cayman theme provides a clean, responsive design
3. **Relative Links**: The `jekyll-relative-links` and `jekyll-optional-front-matter` plugins ensure internal links work correctly
4. **Automatic Build**: Every push to the `/docs` folder triggers a rebuild

---

## URL Structure

Once enabled, documentation will be accessible at:

| Local Path | GitHub Pages URL |
|------------|------------------|
| `docs/index.md` | `https://equinor.github.io/neqsim/` |
| `docs/thermo/README.md` | `https://equinor.github.io/neqsim/thermo/README` |
| `docs/process/README.md` | `https://equinor.github.io/neqsim/process/README` |
| `docs/safety/SAFETY_SIMULATION_ROADMAP.md` | `https://equinor.github.io/neqsim/safety/SAFETY_SIMULATION_ROADMAP` |
| `docs/manual/neqsim_reference_manual.html` | `https://equinor.github.io/neqsim/manual/neqsim_reference_manual.html` |
| `docs/wiki/getting_started.md` | `https://equinor.github.io/neqsim/wiki/getting_started` |

**Important**: Jekyll converts `.md` files to `.html`. Links should **not** include the `.md` extension when hosted.

---

## Testing Locally

To test the documentation site locally before pushing:

```bash
# Install Jekyll (requires Ruby)
gem install bundler jekyll

# Navigate to docs folder
cd docs

# Install dependencies
bundle install

# Serve locally
bundle exec jekyll serve

# Open http://localhost:4000/neqsim/ in browser
```

Or use Docker:

```bash
cd docs
docker run --rm -v "$PWD:/srv/jekyll" -p 4000:4000 jekyll/jekyll jekyll serve
```

---

## Customization Options

### Changing the Theme

Edit `_config.yml` to use a different [GitHub Pages supported theme](https://pages.github.com/themes/):

```yaml
theme: jekyll-theme-minimal  # or jekyll-theme-slate, etc.
```

### Adding Custom CSS

Create `assets/css/style.scss`:

```scss
---
---

@import "{{ site.theme }}";

// Custom styles
.grid-container {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}
```

### Custom Layouts

Create a `_layouts` folder with custom HTML templates.

---

## Troubleshooting

### Build Failures

Check the Actions tab in GitHub for build logs. Common issues:
- Invalid YAML in `_config.yml`
- Broken Liquid template syntax in Markdown files
- Missing front matter in pages that need it

### Links Not Working

Ensure you're using relative paths without the `/docs` prefix:
- ✅ `[Thermo](thermo/README)`
- ❌ `[Thermo](/docs/thermo/README)`

### Images Not Loading

Place images in the docs folder and reference them relatively:
```markdown
![Diagram](images/architecture.png)
```

---

## Alternative: GitHub Actions Deployment

For more control, you can use GitHub Actions instead of the built-in GitHub Pages:

```yaml
# .github/workflows/docs.yml
name: Deploy Documentation

on:
  push:
    branches: [main]
    paths: ['docs/**']

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Ruby
        uses: ruby/setup-ruby@v1
        with:
          ruby-version: '3.2'
          bundler-cache: true
          working-directory: docs
          
      - name: Build site
        run: |
          cd docs
          bundle exec jekyll build
          
      - name: Deploy to GitHub Pages
        uses: peaceiris/actions-gh-pages@v3
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./docs/_site
```

---

## Benefits of GitHub Pages

1. **Free Hosting**: No cost for public repositories
2. **Automatic Deployment**: Pushes to `/docs` automatically rebuild the site
3. **HTTPS**: Secure by default
4. **Custom Domain**: Can configure a custom domain if desired
5. **Working Links**: All internal Markdown links work correctly
6. **Search Engine Indexing**: Documentation becomes searchable on the web
7. **Professional Appearance**: Clean, themed documentation site

---

## Next Steps

1. Enable GitHub Pages in repository settings
2. Verify the site builds successfully
3. Update the main README.md to link to the hosted documentation
4. Consider adding a custom domain (optional)
5. Set up CI/CD to validate documentation on pull requests
