-- A paid consultation's thread exists from its checkout, before the money opens a window
-- (2026-09-30), so a doctor's thread may now have no window yet. What V28 required of
-- every consultation now holds only once one was opened: a window has both its ends.
ALTER TABLE community_conversations DROP CONSTRAINT community_conversations_consultation_window;

ALTER TABLE community_conversations
    ADD CONSTRAINT community_conversations_consultation_window CHECK (
        doctor_id IS NULL OR (opened_at IS NULL) = (expires_at IS NULL)
    );
