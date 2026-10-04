#!/usr/bin/env python3
"""
file_neqsim_issue.py - Offer to file a NeqSim gap as a GitHub issue, and
optionally open a PR, straight from a solved task folder.

This is the standard "close the loop" step for agentic task solving: when a
task's ``neqsim_improvements.md`` (or ``results.json`` ``improvements`` array)
records a NeqSim capability gap, this tool turns that record into a GitHub
issue on ``equinor/neqsim`` -- and, if the gap was already implemented in this
checkout, offers to push the Java change as a pull request.

Nothing is ever created without explicit consent: every issue and every PR is
confirmed interactively (or requires ``--yes``), and ``--dry-run`` never calls
``gh``/``git`` at all. This mirrors the existing CI workflow
(``.github/workflows/task_nip_issues.yml``) that auto-opens issues for NIPs
pushed to ``task_solve/`` inside this repo, but works for ANY task folder
(including ones outside this git checkout, e.g. under a configured task root)
and adds the PR half of the loop.

Two very different audiences use this tool, and it degrades gracefully between
them:

- **Full neqsim checkout, `gh` installed and authenticated** (most contributors
  working in this repo): gets the fully automated path -- issue creation,
  duplicate detection, and (with ``--pr``) a Spotless/Checkstyle-gated branch,
  push and PR for a gap that was actually implemented here.
- **NeqSim Copilot plugin users with no local neqsim checkout** (most
  engineering-task users): have no source tree to implement a Java fix in, and
  often have no `gh` CLI either. For them the *issue* half is still the point
  -- every gap still gets a pre-filled ``github.com/.../issues/new`` link
  (see ``github_new_issue_url``) that opens in a normal browser and files the
  issue under the user's own GitHub account, no `gh` install, auth, or repo
  write access required. This fallback fires automatically whenever `gh` is
  missing, unauthenticated, or fails for any other reason -- it is never a
  dead end.

Usage:
    neqsim file-issue TASK_DIR              # list gaps, offer to file each
    neqsim file-issue TASK_DIR --list        # list only, no prompts, no network
    neqsim file-issue TASK_DIR --dry-run     # show exact gh/git commands only
    neqsim file-issue TASK_DIR --pr          # also offer to open a PR
    neqsim file-issue TASK_DIR --yes         # skip confirmation prompts
    neqsim file-issue TASK_DIR --no-browser  # print fallback links, never open one
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import webbrowser
from urllib.parse import quote, urlencode

DEVTOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(DEVTOOLS_DIR)

DEFAULT_REPO = "equinor/neqsim"
DEFAULT_ISSUE_LABELS = ["nip", "enhancement"]
DEFAULT_PR_LABELS = ["ai-assisted"]

NIP_HEADING_RE = re.compile(r"^###\s+(NIP-\S+):\s*(.+?)\s*$", re.MULTILINE)
ISSUE_MARKER_RE = re.compile(r"^\*\*GitHub issue:\*\*\s*(\S+)\s*$", re.MULTILINE)
FIELD_RE = {
    "gap": re.compile(r"^\*\*Gap:\*\*\s*(.+)$", re.MULTILINE),
    "impact": re.compile(r"^\*\*Impact on task:\*\*\s*(.+)$", re.MULTILINE),
    "priority": re.compile(r"^\*\*Priority:\*\*\s*(.+)$", re.MULTILINE),
}


# --------------------------------------------------------------------------
# Parsing the gap record out of a task folder
# --------------------------------------------------------------------------

def _read_text(path):
    if not os.path.isfile(path):
        return None
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


def nip_file_path(task_dir):
    """Return the canonical NIP file path for a task, preferring the new layout."""
    preferred = os.path.join(task_dir, "step1_scope_and_research", "neqsim_improvements.md")
    legacy = os.path.join(task_dir, "neqsim_improvements.md")
    return preferred if os.path.isfile(preferred) or not os.path.isfile(legacy) else legacy


def parse_nips(markdown_text):
    """Split a neqsim_improvements.md body into one dict per NIP-XX section.

    Parameters
    ----------
    markdown_text : str
        Full contents of the NIP file.

    Returns
    -------
    list of dict
        Each item has ``id``, ``title``, ``gap``, ``impact``, ``priority``,
        ``body`` (full section text) and ``issue_url`` (``None`` if not yet
        filed).
    """
    if not markdown_text:
        return []
    headings = list(NIP_HEADING_RE.finditer(markdown_text))
    items = []
    for i, match in enumerate(headings):
        start = match.start()
        end = headings[i + 1].start() if i + 1 < len(headings) else len(markdown_text)
        block = markdown_text[start:end].rstrip() + "\n"
        existing = ISSUE_MARKER_RE.search(block)
        fields = {}
        for key, pattern in FIELD_RE.items():
            found = pattern.search(block)
            fields[key] = found.group(1).strip() if found else ""
        items.append({
            "kind": "nip",
            "id": match.group(1),
            "title": match.group(2).strip(),
            "gap": fields["gap"],
            "impact": fields["impact"],
            "priority": fields["priority"],
            "body": block,
            "issue_url": existing.group(1) if existing else None,
        })
    return items


def load_result_improvements(task_dir):
    """Load ``results.json`` ``improvements`` entries that target NeqSim.

    These are retrospective records of a gap that was *already* fixed in the
    same task (no full NIP write-up needed) -- still worth offering as an
    issue/PR so the fix reaches upstream NeqSim.
    """
    results_path = os.path.join(task_dir, "results.json")
    text = _read_text(results_path)
    if not text:
        return []
    try:
        data = json.loads(text)
    except ValueError:
        return []
    items = []
    for i, entry in enumerate(data.get("improvements", []) or []):
        if not isinstance(entry, dict) or entry.get("target") != "neqsim":
            continue
        gap = entry.get("gap", "").strip()
        if not gap:
            continue
        title = gap if len(gap) <= 80 else gap[:77] + "..."
        body_lines = ["**Gap:** " + gap]
        if entry.get("change"):
            body_lines.append("**Change made in this task:** " + entry["change"])
        if entry.get("evidence"):
            body_lines.append("**Evidence:** " + entry["evidence"])
        items.append({
            "kind": "result",
            "id": "RESULT-{}".format(i),
            "title": title,
            "gap": gap,
            "impact": entry.get("change", ""),
            "priority": "",
            "body": "\n".join(body_lines) + "\n",
            "issue_url": entry.get("issue_url"),
        })
    return items


def collect_items(task_dir, force=False):
    """Return every fileable gap for a task, NIPs first then results.json entries."""
    nip_path = nip_file_path(task_dir)
    items = parse_nips(_read_text(nip_path))
    items += load_result_improvements(task_dir)
    if not force:
        items = [item for item in items if not item["issue_url"]]
    return items


# --------------------------------------------------------------------------
# GitHub CLI helpers (kept thin and mockable for tests)
# --------------------------------------------------------------------------

def gh_available():
    return shutil.which("gh") is not None


def gh_authenticated():
    if not gh_available():
        return False
    result = subprocess.run(["gh", "auth", "status"], capture_output=True, text=True)
    return result.returncode == 0


def run_command(cmd, cwd=None):
    """Run a command and return (returncode, stdout, stderr). Thin wrapper for tests."""
    result = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    return result.returncode, result.stdout, result.stderr


def slugify(text, max_len=60):
    slug = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return (slug[:max_len]).strip("-") or "gap"


def issue_title(item, task_slug):
    return "[NIP] {}: {}".format(task_slug, item["title"])


def issue_body(item, task_dir, task_slug):
    parts = [
        "Filed automatically from the NeqSim task-solving workflow "
        "(`neqsim file-issue`).",
        "",
        "---",
        "",
        item["body"],
    ]
    return "\n".join(parts)


def existing_open_issue(repo, title):
    """Return an open issue number matching ``title`` exactly, or None."""
    code, out, _ = run_command([
        "gh", "issue", "list", "--repo", repo, "--state", "open",
        "--search", '"{}" in:title'.format(title),
        "--json", "number,title",
    ])
    if code != 0 or not out.strip():
        return None
    try:
        candidates = json.loads(out)
    except ValueError:
        return None
    for candidate in candidates:
        if candidate.get("title") == title:
            return candidate.get("number")
    return None


def create_github_issue(repo, title, body, labels, dry_run):
    """Create (or, in dry-run, describe) a GitHub issue. Returns the issue URL or None.

    Returns ``None`` on any failure -- including dry-run, where nothing is
    actually created. The caller decides what to do next (e.g. fall back to
    ``offer_browser_fallback``); this function only ever talks to `gh`.
    """
    cmd = ["gh", "issue", "create", "--repo", repo, "--title", title, "--body", body]
    for label in labels:
        cmd += ["--label", label]
    if dry_run:
        print("  [dry-run] would run: {}".format(" ".join(_quote(part) for part in cmd)))
        return None
    code, out, err = run_command(cmd)
    if code != 0:
        print("  Could not create the issue with 'gh': {}".format(err.strip() or out.strip()))
        return None
    url = out.strip().splitlines()[-1] if out.strip() else ""
    print("  Created issue: {}".format(url))
    return url or None


def _quote(part):
    return '"{}"'.format(part) if " " in part else part


# Roughly the point GitHub starts truncating a prefilled issue body in practice;
# keep well under the ~8KB browsers/servers tolerate for a full request-URI.
MAX_URL_BODY_LEN = 4000


def github_new_issue_url(repo, title, body, labels=None):
    """Build a pre-filled 'New issue' URL -- the path that needs no `gh` at all.

    This is the primary path for NeqSim Copilot plugin users: they rarely have
    a local neqsim checkout or an authenticated `gh` CLI, but opening this URL
    in any browser files the issue under their own GitHub account with the
    title/body/labels already filled in.

    Parameters
    ----------
    repo : str
        ``owner/name``, e.g. ``equinor/neqsim``.
    title : str
        Issue title.
    body : str
        Issue body (markdown). Truncated for the URL if very long -- the task
        folder still holds the full text.
    labels : list of str, optional
        Labels to pre-select.

    Returns
    -------
    str
        A ``https://github.com/<repo>/issues/new?...`` URL.
    """
    body_for_url = body
    if len(body_for_url) > MAX_URL_BODY_LEN:
        body_for_url = (body_for_url[:MAX_URL_BODY_LEN]
                         + "\n\n…(truncated for the URL; see the task folder for the "
                           "full text)…")
    params = [("title", title), ("body", body_for_url)]
    if labels:
        params.append(("labels", ",".join(labels)))
    return "https://github.com/{}/issues/new?{}".format(
        repo, urlencode(params, quote_via=quote))


def open_in_browser(url):
    """Best-effort browser open; never raises (headless boxes just get the URL printed)."""
    try:
        return webbrowser.open(url)
    except Exception:
        return False


def offer_browser_fallback(repo, title, body, labels, assume_yes, dry_run, open_browser=True):
    """Print (and, with consent, open) the pre-filled issue URL for one gap.

    Always returns the ``issues/new`` URL itself -- this is a *form* link, not
    a created issue, so callers must never treat it as evidence the issue was
    actually filed (``record_issue_url`` is only called for a real `gh`-created
    URL).
    """
    url = github_new_issue_url(repo, title, body, labels)
    print("  Pre-filled 'New issue' page (no 'gh' or repo write access needed):")
    print("    {}".format(url))
    if dry_run:
        print("  [dry-run] would open this in your browser")
        return url
    if open_browser and confirm("Open it in your browser now?", assume_yes, dry_run=False):
        if not open_in_browser(url):
            print("  Could not open a browser automatically -- copy the link above.")
    return url


def confirm(prompt, assume_yes, dry_run):
    """Ask the user y/N unless --yes/--dry-run already answered the question."""
    if dry_run:
        return True
    if assume_yes:
        return True
    try:
        answer = input("  {} [y/N]: ".format(prompt)).strip().lower()
    except (EOFError, KeyboardInterrupt):
        print()
        return False
    return answer in ("y", "yes")


# --------------------------------------------------------------------------
# Recording the filed issue back into the task folder
# --------------------------------------------------------------------------

def record_issue_url(task_dir, item, url):
    """Write the created issue URL back into whichever source produced the item."""
    if not url:
        return
    if item["kind"] == "nip":
        _insert_issue_line_in_nip_file(task_dir, item, url)
    else:
        _record_issue_url_in_results(task_dir, item, url)


def _insert_issue_line_in_nip_file(task_dir, item, url):
    path = nip_file_path(task_dir)
    text = _read_text(path)
    if text is None:
        return
    heading = "### {}:".format(item["id"])
    idx = text.find(heading)
    if idx == -1:
        return
    line_end = text.find("\n", idx)
    if line_end == -1:
        line_end = len(text)
    insertion = "\n\n**GitHub issue:** {}".format(url)
    text = text[:line_end] + insertion + text[line_end:]
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


def _record_issue_url_in_results(task_dir, item, url):
    results_path = os.path.join(task_dir, "results.json")
    text = _read_text(results_path)
    if text is None:
        return
    try:
        data = json.loads(text)
    except ValueError:
        return
    index = int(item["id"].split("-")[1])
    improvements = data.get("improvements", [])
    if 0 <= index < len(improvements):
        improvements[index]["issue_url"] = url
        with open(results_path, "w", encoding="utf-8") as handle:
            json.dump(data, handle, indent=2)
            handle.write("\n")


# --------------------------------------------------------------------------
# PR half of the loop: push an already-implemented gap fix
# --------------------------------------------------------------------------

ALLOWED_PR_FILE_RE = re.compile(
    r"^(src/main/java/neqsim/.+\.java|src/test/java/neqsim/.+\.java)$"
)


def changed_java_files(project_root):
    """Return staged+unstaged NeqSim Java files, or [] if the tree is clean."""
    code, out, _ = run_command(["git", "status", "--porcelain"], cwd=project_root)
    if code != 0:
        return []
    files = []
    for line in out.splitlines():
        path = line[3:].strip()
        if ALLOWED_PR_FILE_RE.match(path.replace("\\", "/")):
            files.append(path)
    return files


def run_quality_checks(project_root, dry_run):
    """Run spotless:apply, spotless:check and checkstyle:check before a PR."""
    wrapper = os.path.join(project_root, "mvnw.cmd" if os.name == "nt" else "mvnw")
    if dry_run:
        print("  [dry-run] would run: {} spotless:apply / spotless:check / checkstyle:check"
              .format(wrapper))
        return True
    for goal in ("spotless:apply", "spotless:check", "checkstyle:check"):
        cmd = [wrapper, goal] if os.name == "nt" else ["./" + os.path.basename(wrapper), goal]
        print("  $ {} {}".format(wrapper, goal))
        code, out, err = run_command(cmd, cwd=project_root)
        if code != 0:
            print(out[-4000:])
            print(err[-4000:])
            print("  {} failed -- fix the reported issues before opening a PR.".format(goal))
            return False
    return True


def create_pull_request(project_root, repo, branch, base, title, body, files, dry_run):
    """Commit the given files on ``branch``, push, and open a PR. Returns the PR URL."""
    if dry_run:
        print("  [dry-run] would commit {} file(s) on branch '{}' and run: "
              "gh pr create --repo {} --base {} --title {!r}"
              .format(len(files), branch, repo, base, title))
        return None

    code, current_branch, _ = run_command(
        ["git", "rev-parse", "--abbrev-ref", "HEAD"], cwd=project_root)
    current_branch = current_branch.strip()
    if code != 0 or not current_branch or current_branch == "HEAD":
        print("  Cannot publish from an unknown or detached branch.")
        return None
    if current_branch in (base, "master", "main"):
        code, _, err = run_command(["git", "checkout", "-b", branch], cwd=project_root)
        if code != 0:
            print("  ERROR creating branch {}: {}".format(branch, err.strip()))
            return None
    else:
        branch = current_branch

    code, _, err = run_command(["git", "add", "--"] + files, cwd=project_root)
    if code != 0:
        print("  ERROR staging files: {}".format(err.strip()))
        return None

    code, _, err = run_command(["git", "commit", "--only", "-m", title, "--"] + files,
                                cwd=project_root)
    if code != 0:
        print("  Nothing to commit or commit failed: {}".format(err.strip()))
        return None

    code, _, err = run_command(["git", "push", "-u", "origin", branch], cwd=project_root)
    if code != 0:
        print("  ERROR pushing branch {}: {}".format(branch, err.strip()))
        print("  If you do not have push access to {}, fork the repo first.".format(repo))
        return None

    cmd = ["gh", "pr", "create", "--repo", repo, "--base", base, "--head", branch,
           "--title", title, "--body", body]
    for label in DEFAULT_PR_LABELS:
        cmd += ["--label", label]
    code, out, err = run_command(cmd, cwd=project_root)
    if code != 0:
        # Retry without labels -- a missing label must not block the PR.
        cmd_no_labels = [part for part in cmd
                          if part not in DEFAULT_PR_LABELS and part != "--label"]
        code, out, err = run_command(cmd_no_labels, cwd=project_root)
    if code != 0:
        print("  ERROR creating PR: {}".format(err.strip() or out.strip()))
        return None
    url = out.strip().splitlines()[-1] if out.strip() else ""
    print("  Opened PR: {}".format(url))
    return url or None


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------

def _print_item(item):
    print("  {} [{}] {}".format(item["id"], item.get("priority") or "-", item["title"]))
    if item.get("gap"):
        print("      Gap: {}".format(item["gap"]))
    if item.get("issue_url"):
        print("      Already filed: {}".format(item["issue_url"]))


def build_arg_parser():
    parser = argparse.ArgumentParser(
        prog="neqsim file-issue",
        description="Offer to file a NeqSim capability gap as a GitHub issue, "
                     "and optionally a PR, from a solved task folder.")
    parser.add_argument("task_dir", nargs="?", default=".",
                         help="Task folder (default: current directory)")
    parser.add_argument("--repo", default=DEFAULT_REPO,
                         help="GitHub repo to file against (default: %(default)s)")
    parser.add_argument("--project-root", default=PROJECT_ROOT,
                         help="Local NeqSim git checkout used for the PR half of the loop")
    parser.add_argument("--list", action="store_true",
                         help="List gaps only -- no prompts, no gh/git calls")
    parser.add_argument("--dry-run", action="store_true",
                         help="Print the exact gh/git commands without running them")
    parser.add_argument("-y", "--yes", action="store_true",
                         help="Skip confirmation prompts (still requires --pr for PRs)")
    parser.add_argument("--pr", action="store_true",
                         help="Also offer to open a PR for gaps already implemented here")
    parser.add_argument("--base", default="master", help="PR base branch (default: master)")
    parser.add_argument("--branch", default=None,
                         help="Feature branch name (default: task/<slug>)")
    parser.add_argument("--force", action="store_true",
                         help="Re-offer items that already carry a recorded issue URL")
    parser.add_argument("--no-browser", action="store_true",
                         help="Print fallback 'New issue' links instead of opening a browser")
    return parser


def main(argv=None):
    args = build_arg_parser().parse_args(argv)
    task_dir = os.path.abspath(args.task_dir)
    if not os.path.isdir(task_dir):
        print("ERROR: not a folder: {}".format(task_dir))
        return 2

    items = collect_items(task_dir, force=args.force)
    if not items:
        print("No unfiled NeqSim gaps found under {}".format(task_dir))
        print("(looked for step1_scope_and_research/neqsim_improvements.md "
              "and results.json improvements[].target == 'neqsim')")
        return 0

    task_slug = os.path.basename(task_dir.rstrip(os.sep))
    print("Found {} NeqSim gap(s) in {}:".format(len(items), task_dir))
    for item in items:
        _print_item(item)

    if args.list:
        return 0

    # `gh` is the fast path (used by most contributors working in this repo).
    # Anyone without it -- most notably NeqSim Copilot plugin users, who
    # typically have neither a local checkout nor `gh` installed -- still gets
    # a working path: a pre-filled browser link per gap (see
    # ``offer_browser_fallback``). Nothing here is a hard stop.
    gh_ready = args.dry_run or (gh_available() and gh_authenticated())
    if not gh_ready and not args.dry_run:
        print()
        if not gh_available():
            print("The GitHub CLI ('gh') is not installed on this machine.")
        else:
            print("'gh' is installed but not authenticated ('gh auth login' to fix).")
        print("Falling back to a pre-filled 'New issue' browser link per gap instead --")
        print("this works without installing anything or having repo write access.")

    print()
    for item in items:
        title = issue_title(item, task_slug)
        body = issue_body(item, task_dir, task_slug)
        if gh_ready and not args.dry_run and existing_open_issue(args.repo, title):
            print("Already open on {}: {}".format(args.repo, title))
            continue
        if not confirm("File '{}' as a GitHub issue on {}?".format(title, args.repo),
                        args.yes, args.dry_run):
            continue

        url = None
        if gh_ready:
            url = create_github_issue(args.repo, title, body, DEFAULT_ISSUE_LABELS, args.dry_run)
        if not url and not args.dry_run:
            # Either gh isn't ready, or it just failed (rate limit, network, ...) --
            # either way, hand the user a link they can finish in any browser.
            offer_browser_fallback(args.repo, title, body, DEFAULT_ISSUE_LABELS, args.yes,
                                    dry_run=False, open_browser=not args.no_browser)
        elif args.dry_run:
            offer_browser_fallback(args.repo, title, body, DEFAULT_ISSUE_LABELS, True,
                                    dry_run=True, open_browser=False)
        if url:
            record_issue_url(task_dir, item, url)

        if args.pr and gh_ready:
            _maybe_offer_pr(item, args, task_slug)

    return 0


def _maybe_offer_pr(item, args, task_slug):
    if args.dry_run:
        print("  [dry-run] would inspect changed NeqSim Java files, run quality checks, "
              "then offer to commit, push and open a PR; Git inspection is skipped.")
        return
    files = changed_java_files(args.project_root)
    if not files:
        return
    branch = args.branch or "task/{}".format(slugify(task_slug))
    title = "{}: {}".format(task_slug, item["title"])
    body = item["body"] + "\n\nImplements the gap filed as the companion issue above."
    print()
    print("  Found {} NeqSim Java file(s) changed in {}:".format(len(files), args.project_root))
    for path in files:
        print("    {}".format(path))
    if not confirm("Run Spotless/Checkstyle and open a PR on {} for this change?"
                   .format(args.repo), args.yes, args.dry_run):
        return
    if not run_quality_checks(args.project_root, args.dry_run):
        return
    create_pull_request(args.project_root, args.repo, branch, args.base, title, body,
                         files, args.dry_run)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
