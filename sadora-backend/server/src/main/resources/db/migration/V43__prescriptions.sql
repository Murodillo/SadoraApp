-- A doctor's prescription (2026-10-09), sent into a consultation as a message.
--
-- The message row carries the kind 'prescription' and the prescription as plain text in
-- its body, so moderation, previews and any app that does not know the kind still read
-- a sensible line. The structured copy lives here, one row per message, and never
-- changes after it is written: the items are a JSON document rather than a child table
-- for that reason. Only the cancellation and the patient's "added" are set later.

CREATE TABLE prescriptions (
    id              UUID PRIMARY KEY,
    message_id      UUID        NOT NULL UNIQUE REFERENCES community_messages (id) ON DELETE CASCADE,
    conversation_id UUID        NOT NULL REFERENCES community_conversations (id) ON DELETE CASCADE,
    doctor_id       UUID        NOT NULL REFERENCES doctor_profiles (id) ON DELETE CASCADE,
    patient_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    items           JSONB       NOT NULL,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at    TIMESTAMPTZ,
    cancel_reason   TEXT,
    added_at        TIMESTAMPTZ,
    CONSTRAINT prescriptions_cancel_reason CHECK (cancelled_at IS NULL OR cancel_reason IS NOT NULL)
);

CREATE INDEX prescriptions_patient_idx ON prescriptions (patient_id, created_at DESC);
CREATE INDEX prescriptions_conversation_idx ON prescriptions (conversation_id, created_at DESC);
CREATE INDEX prescriptions_doctor_idx ON prescriptions (doctor_id, created_at DESC);

-- A medication the patient added from a prescription. Cancelling the prescription
-- archives every active one that points here; the course outlives the prescription
-- row only as history, so a deleted prescription leaves the medication unlinked.
ALTER TABLE medications
    ADD COLUMN prescription_id UUID REFERENCES prescriptions (id) ON DELETE SET NULL;

CREATE INDEX medications_prescription_idx ON medications (prescription_id) WHERE prescription_id IS NOT NULL;
