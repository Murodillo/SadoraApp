# Sadora: working rules for Claude sessions

## Tasks and docs live in "Loyiha"

The team's tasks (`SAD-12`), epics, releases and docs live in the staff panel's **Loyiha**
section on staging (`dev-admin.sadora.app` → Loyiha; API `dev-api.sadora.app`). From a session, use `tools/pm`. It signs in with the
token in `~/.config/sadora/pm_token`; `tools/pm --help` lists the commands.

- **Starting multi-step work:** find the task (`tools/pm task list --q "…"` or
  `tools/pm search "…"`). If there is none, create one
  (`tools/pm task new "…" --type feature --component backend --epic "…"`). Then move it to
  in progress: `tools/pm task move SAD-12 in_progress`.
- **Commits:** end the message's subject or body with the task key, e.g. `… (SAD-12)`. The
  server links the commit to the task by itself. A commit without a key is fine.
- **Finished and pushed:** `tools/pm task move SAD-12 review`, and add a comment saying
  what was done and how it was checked (`tools/pm task comment SAD-12 "…"`).
  **Done is the user's to give.** The token cannot set it, so don't try.
- **Out-of-scope problems you notice:** record each one as a new task in Backlog with
  enough context to act on, instead of fixing it on the side.
- **Specs, audits, decisions, runbooks:** write them as Loyiha docs
  (`tools/pm doc put --title "…" --parent "Speclar" file.md`), not as new files in
  `design/`. Media (videos, images, ad sources) stays in `design/`.
- **Memory:** keep only how-to notes and gotchas there. Open work belongs in Loyiha, and
  a memory line points at it by its `SAD-` key.

If `tools/pm` cannot reach the server (no token, server down), say so. Do the work
anyway, and list the board updates you could not make at the end of your reply.
