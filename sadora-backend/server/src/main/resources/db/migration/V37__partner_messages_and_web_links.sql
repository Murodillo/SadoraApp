-- Yaqinim, stage three: what the two of them send each other, and the browser link.
--
-- A message belongs to a link and dies with it: ending the link makes its messages
-- unreachable (every read goes through a live link), and deleting either account removes
-- them with the link. The text is short and only ever read by the two people on it.

CREATE TABLE partner_messages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    link_id    UUID        NOT NULL REFERENCES partner_links (id) ON DELETE CASCADE,
    sender_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind       TEXT        NOT NULL,
    body       TEXT CHECK (body IS NULL OR char_length(body) BETWEEN 1 AND 200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at    TIMESTAMPTZ
);

CREATE INDEX partner_messages_link_idx ON partner_messages (link_id, created_at DESC);

-- The browser link for someone without the app. Like a doctor's QR code: a random token
-- stored hashed, a life of days, revocable, counted. The parts it shows are chosen when
-- it is made and kept with it, so changing what her person sees does not quietly widen
-- a link that is already out.
CREATE TABLE partner_web_links (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash     TEXT        NOT NULL UNIQUE,
    permissions    JSONB       NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    view_count     INTEGER     NOT NULL DEFAULT 0 CHECK (view_count >= 0),
    last_viewed_at TIMESTAMPTZ,
    CHECK (expires_at > created_at)
);

CREATE INDEX partner_web_links_owner_idx ON partner_web_links (owner_id, created_at DESC);
