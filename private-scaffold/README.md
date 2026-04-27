# INTERNAL-ONLY notice for this directory tree

`private-scaffold/neqsim-eqn-scrubber/` is **not** part of the public
NeqSim build. It is a self-contained Maven project intended to be moved
to a **separate, internal Equinor repository** before any code is added
beyond the SPI scaffolding currently checked in.

## How to extract this scaffold to its own repo

```bash
cd c:\Appl\projects\neqsim\private-scaffold\neqsim-eqn-scrubber
git init
git branch -M main
git remote add origin https://github.com/equinor/neqsim-eqn-scrubber.git
git add .
git commit -m "Initial commit: NeqSim EQN scrubber plug-in scaffold"
git push -u origin main
```

The target repo `equinor/neqsim-eqn-scrubber` should be created on
github.com/equinor first, with visibility set to **Internal** (so any
Equinor org member can clone & `mvn install` without per-person invites).

After that, **delete `private-scaffold/` from the public NeqSim repo** so
no part of it ever ships in the public build:

```bash
cd c:\Appl\projects\neqsim
git rm -r private-scaffold
git commit -m "Remove private-scaffold (moved to internal repo)"
git push origin scrubbers_kollsnes
```

Until the move is done, this folder is gitignored from the public build
artifacts via the explicit `.gitignore` at this directory's root.

## Why it lives here temporarily

It is easier to review the public-side SPI changes and the private-side
plug-in shape together in one branch. Once you (the human reviewer)
confirm the API surface, move the folder to its own repo and delete it
from the public tree.
