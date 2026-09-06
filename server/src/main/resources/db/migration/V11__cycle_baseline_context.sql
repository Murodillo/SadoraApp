-- Two answers the onboarding flow now collects from users who are trying to conceive.
-- Both are nullable: every existing row predates the questions, and both questions can
-- be skipped, so "not answered" has to stay distinguishable from any real answer.
ALTER TABLE cycle_baselines
    ADD COLUMN conception_window TEXT,
    ADD COLUMN birth_control     TEXT;
