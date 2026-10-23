-- "Loyiha": the team's own tasks and docs for building Sadora, inside the staff panel.
-- Nothing here is about the app's users; the DEVELOPER role reaches these tables and nothing else.

-- A task's public key is SAD-<number>; the number is never reused, even after a hard delete.
CREATE SEQUENCE pm_task_number_seq START 1;

CREATE TABLE pm_epics (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       TEXT        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 160),
    description TEXT        NOT NULL DEFAULT '',
    status      TEXT        NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'done')),
    archived_at TIMESTAMPTZ,
    created_by  UUID        REFERENCES admin_users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A store release ("Play Store 1.0"), or any other date the work is aimed at.
CREATE TABLE pm_releases (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 120),
    description TEXT        NOT NULL DEFAULT '',
    target_date DATE,
    status      TEXT        NOT NULL DEFAULT 'planned' CHECK (status IN ('planned', 'released')),
    released_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,
    created_by  UUID        REFERENCES admin_users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pm_tasks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    number      INTEGER     NOT NULL UNIQUE DEFAULT nextval('pm_task_number_seq'),
    title       TEXT        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    description TEXT        NOT NULL DEFAULT '',
    type        TEXT        NOT NULL CHECK (type IN ('bug', 'feature', 'task')),
    status      TEXT        NOT NULL CHECK (status IN ('backlog', 'todo', 'in_progress', 'review', 'done')),
    priority    TEXT        NOT NULL CHECK (priority IN ('critical', 'high', 'medium', 'low')),
    -- Fixed keys (android, ios, doctor_app, backend, admin, doctor_admin, landing, infra, design); the server checks them.
    components  JSONB       NOT NULL DEFAULT '[]',
    labels      JSONB       NOT NULL DEFAULT '[]',
    assignee_id UUID        REFERENCES admin_users (id) ON DELETE SET NULL,
    reporter_id UUID        REFERENCES admin_users (id) ON DELETE SET NULL,
    epic_id     UUID        REFERENCES pm_epics (id) ON DELETE SET NULL,
    release_id  UUID        REFERENCES pm_releases (id) ON DELETE SET NULL,
    -- One level only: a subtask has no subtasks of its own.
    parent_id   UUID        REFERENCES pm_tasks (id) ON DELETE CASCADE,
    due_date    DATE,
    -- Order inside a board column; a move lands between its neighbours' positions.
    position    DOUBLE PRECISION NOT NULL DEFAULT 0,
    archived_at TIMESTAMPTZ,
    done_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX pm_tasks_board_idx ON pm_tasks (status, position) WHERE archived_at IS NULL;
CREATE INDEX pm_tasks_epic_idx ON pm_tasks (epic_id);
CREATE INDEX pm_tasks_release_idx ON pm_tasks (release_id);
CREATE INDEX pm_tasks_parent_idx ON pm_tasks (parent_id);

CREATE TABLE pm_comments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id    UUID        NOT NULL REFERENCES pm_tasks (id) ON DELETE CASCADE,
    author_id  UUID        REFERENCES admin_users (id) ON DELETE SET NULL,
    body       TEXT        NOT NULL CHECK (char_length(body) BETWEEN 1 AND 20000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX pm_comments_task_idx ON pm_comments (task_id, created_at);

-- What happened to a task: created, moved between columns, given to someone.
CREATE TABLE pm_task_events (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id    UUID        NOT NULL REFERENCES pm_tasks (id) ON DELETE CASCADE,
    actor_id   UUID        REFERENCES admin_users (id) ON DELETE SET NULL,
    kind       TEXT        NOT NULL,
    from_value TEXT,
    to_value   TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX pm_task_events_task_idx ON pm_task_events (task_id, created_at);

-- Commits whose message names the task's key, read from GitHub by the server.
CREATE TABLE pm_task_commits (
    task_id      UUID        NOT NULL REFERENCES pm_tasks (id) ON DELETE CASCADE,
    sha          TEXT        NOT NULL,
    branch       TEXT        NOT NULL,
    message      TEXT        NOT NULL,
    author       TEXT        NOT NULL,
    url          TEXT        NOT NULL,
    committed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (task_id, sha)
);

CREATE TABLE pm_docs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id   UUID        REFERENCES pm_docs (id) ON DELETE CASCADE,
    title       TEXT        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 160),
    body        TEXT        NOT NULL DEFAULT '',
    position    DOUBLE PRECISION NOT NULL DEFAULT 0,
    -- Bumped on every save; a save names the version it started from, so two editors cannot overwrite each other.
    version     INTEGER     NOT NULL DEFAULT 1,
    archived_at TIMESTAMPTZ,
    created_by  UUID        REFERENCES admin_users (id) ON DELETE SET NULL,
    updated_by  UUID        REFERENCES admin_users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX pm_docs_parent_idx ON pm_docs (parent_id);

-- Every saved version, the current one included.
CREATE TABLE pm_doc_versions (
    doc_id     UUID        NOT NULL REFERENCES pm_docs (id) ON DELETE CASCADE,
    version    INTEGER     NOT NULL,
    title      TEXT        NOT NULL,
    body       TEXT        NOT NULL,
    edited_by  UUID        REFERENCES admin_users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (doc_id, version)
);

-- A picture pasted into a doc. The bytes live on disk under MEDIA_DIR/pm/<id>, never served without a PM sign-in.
CREATE TABLE pm_images (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type TEXT        NOT NULL,
    size_bytes   INTEGER     NOT NULL,
    uploaded_by  UUID        REFERENCES admin_users (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A long-lived key for tools/pm and Claude sessions. Only its SHA-256 is kept; it opens /admin/pm and nothing else.
CREATE TABLE pm_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id     UUID        NOT NULL REFERENCES admin_users (id) ON DELETE CASCADE,
    name         TEXT        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 80),
    token_hash   TEXT        NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX pm_tokens_admin_idx ON pm_tokens (admin_id);
