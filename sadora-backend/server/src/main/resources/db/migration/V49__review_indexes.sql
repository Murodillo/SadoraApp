-- Indexes the 2026-10-10 review found missing.

-- The scheduler reads every course with reminders on, every minute.
CREATE INDEX medications_reminders_idx ON medications (user_id) WHERE active AND reminders_enabled;

-- The badge board counts her consultations on every read, and nothing indexed the patient.
CREATE INDEX consultation_sessions_patient_idx ON consultation_sessions (patient_id);

-- The closing job's work list: open windows whose time is up.
CREATE INDEX consultation_sessions_open_expiry_idx ON consultation_sessions (expires_at)
    WHERE opened_at IS NOT NULL AND closed_at IS NULL;
