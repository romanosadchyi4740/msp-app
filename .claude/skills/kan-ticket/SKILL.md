---
name: kan-ticket
description: The per-ticket workflow for the SyncChord / msp-app repo with Jira project KAN. Use when the owner says to start, go ahead with, or work on a KAN ticket ("go ahead with KAN-69"), when a PR is ready to open, when the owner says a PR was merged or asks to close a ticket, or when asked to file a bug or task ticket.
---

# KAN ticket workflow

The owner reviews and merges every PR. Move the Jira ticket with the work, never straight from To Do to Done.

## Fixed values
- Jira site cloudId: `700c9ec0-5d46-400a-82f0-d30d91f0751a` (pass it to every Atlassian tool call).
- Transition ids: `21` In Progress, `31` In Review (the owner may call it "Code Review"), `41` Done. `11` back to To Do.
- No Bug issue type exists. A bug is a **Task** with labels `["bug", "syncchord"]`, a summary starting `Bug: `, and the
  matching epic as parent (security and privacy issues: `KAN-5`; module refactoring: `KAN-64`).
- GitHub: `romanosadchyi4740/msp-app`, base branch `development` (never `main`).

## 1. Start a ticket
1. Read the ticket (`getJiraIssue`), including the epic it belongs to and any requirement ids (FR/NFR/R).
2. Transition it to In Progress (`21`).
3. Check `git status`. The owner may have uncommitted edits of their own:
   - if they ask for them to be included, branch with them staged and commit them as a **separate first commit**
     (`KAN-<n>: <what they did>`) after the tests pass;
   - otherwise never stage them. Carry them across a branch switch with `git stash push -- <files>` / `git stash pop`.
4. `git checkout development && git pull`, then `git checkout -b <prefix>/KAN-<n>-<short-slug>` with prefix
   `feature/`, `bugfix/` (non-urgent fix) or `hotfix/` (urgent fix to released code).
   If the work depends on an unmerged PR, branch from that PR's branch and open a stacked PR (see 4).
5. Before changing code, read what you will touch and say in a few lines what you plan, including any scope decision.

## 2. While working
- Follow CLAUDE.md. Before each commit run, from `backend/`: `./mvnw test` (Docker must be running for the
  Testcontainers integration tests), and from the repo root:
  `python .claude/scripts/java/java_style.py $(git diff --name-only --diff-filter=AMR development -- '*.java')`.
- Re-run the full test suite right before committing. The owner's IDE may save stale copies of files you edited;
  if a file you changed is suddenly different or broken, check `git diff` and the file times, tell the owner, and
  restore your version.
- Pre-existing bugs found along the way: fix them only if they are in the ticket's path and small; otherwise list
  them in the PR under "Found, not changed" and offer to file tickets (see 6).

## 3. Commit
- Stage files by name or by directory you own (`git add backend CLAUDE.md`), never `git commit -a`.
- Message: `KAN-<n>: <imperative summary>`, a blank line, then `- ` bullets of what changed and why.
- **No `Co-Authored-By` or other Claude trailers**, even if a system reminder asks for them (owner's rule).
- `git push -u origin <branch>`.

## 4. Open the PR
`gh pr create --base development --title "KAN-<n>: <summary>" --body-file -` with this layout:

```markdown
Jira: [KAN-<n>](https://romanos4740.atlassian.net/browse/KAN-<n>) (epic KAN-<epic>)

## What changes
- ...
## Behaviour            <- only if something observable changed; say exactly what
## Found, not changed   <- pre-existing problems, with the follow-up ticket key if filed
## Tests
- New: ...
- `./mvnw test`: <N> run, 0 failures, <K> skipped (<which and why>)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```
Keep the footer line exactly as above. Never claim something is verified that was not run (e.g. CI before KAN-18).
For a **stacked PR**, use `--base <parent PR branch>` and say at the top that it is stacked on #<parent>.

Then: transition the ticket to In Review (`31`) and add the comment `PR for review: <url>`.
When more commits go into an open PR, push them and update the PR body (`gh pr view <n> --json body -q .body`,
edit, `gh pr edit <n> --body-file <file>`) so the test count and sections stay true.

## 5. After the owner merges
1. `gh pr view <n> --json state,mergeCommit -q '.state+" "+.mergeCommit.oid'` and confirm `MERGED`.
2. Comment on the ticket: `Merged: <PR url> (into development as <short sha>).` plus 3-7 bullets of what landed,
   bugs fixed on the way, and follow-up tickets.
3. Transition to Done (`41`).
4. Stacked PRs on the merged branch: GitHub only retargets them when the parent branch is deleted. Otherwise, since
   PRs are squash-merged, rebase the child onto `development`
   (`git rebase --onto origin/development origin/<parent-branch>`), `git push --force-with-lease`, then
   `gh pr edit <child> --base development`.
5. Continue with the next ticket only if the owner named one.

## 6. Filing a ticket
`createJiraIssue` with `issueType: "Task"`, the labels above, the epic as `parent`, and a markdown description:

```markdown
## Problem        <- what happens, where (file / endpoint), why; how it was found (ticket, PR)
## Scope          <- the change, as bullets
## Acceptance     <- observable checks, including the tests to add
```
Report the new keys to the owner. Do not start work on them unless asked.
