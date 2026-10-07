# Issue tracker: GitHub

Issues and specs live in GitHub Issues for A3R0-01/Cloud-Epub.
Use the gh CLI from this repo, or pass --repo A3R0-01/Cloud-Epub.

## Conventions

- Create: gh issue create --title "..." --body-file <file>
- Read: gh issue view <number> --json number,title,body,labels,comments
- List: gh issue list --state open --json number,title,body,labels
- Comment: gh issue comment <number> --body-file <file>
- Apply labels: gh issue edit <number> --add-label "..."
- Remove labels: gh issue edit <number> --remove-label "..."
- Close: gh issue close <number> --comment "..."

Write multiline bodies to a temporary file and pass --body-file.

When a skill says "publish to the issue tracker", create a GitHub issue.
When it says "fetch the relevant ticket", read the referenced issue.

## Pull requests

**PRs as a request surface: no.**

GitHub issues and PRs share a number space. If a reference is a PR,
use gh pr view and gh pr diff to read it.

## Wayfinding operations

- Map: one issue labelled wayfinder:map containing Notes,
  Decisions-so-far, and Fog.
- Child ticket: link it to the map as a GitHub sub-issue.
  If unavailable, add it to the map's task list and put
  "Part of #<map>" at the top of the child body.
- Ticket type: use wayfinder:research, wayfinder:prototype,
  wayfinder:grilling, or wayfinder:task.
- Blocking: use GitHub's native issue dependencies. API operations
  require an issue's numeric database id, not its issue number.
  If unavailable, add "Blocked by: #<number>, #<number>" to the body.
- Frontier: choose the first open, unassigned child in map order
  whose blockers are all closed.
- Claim: assign the ticket to the driving developer before work.
- Resolve: comment with the answer, close the ticket, and append
  a summary and link to the map's Decisions-so-far.
