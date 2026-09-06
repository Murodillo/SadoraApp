-- What each AI answer cost, and nothing about what it said.
--
-- The row deliberately has no question and no answer column. The chat is not stored —
-- that is the promise the privacy screen makes — and a "cost log" is the obvious place
-- for that promise to quietly break, so the schema simply has nowhere to put the text.
-- What is kept is what an operator needs to see a budget run away: which model answered,
-- how many tokens, what it cost, how long it took, and whether it worked.

CREATE TABLE ai_usage_log (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Null once the account is deleted: the spend stays in the totals, the person does not.
    user_id           UUID REFERENCES users (id) ON DELETE SET NULL,
    feature           TEXT        NOT NULL DEFAULT 'ai_chat',
    -- 'model' when the model answered, 'rules' when the rule engine did by design,
    -- 'fallback' when the model was tried and could not answer.
    source            TEXT        NOT NULL,
    model             TEXT,
    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    -- USD micros. Integer because money in a floating point column is a bug waiting.
    cost_micros       BIGINT      NOT NULL DEFAULT 0,
    latency_ms        INTEGER,
    outcome           TEXT        NOT NULL DEFAULT 'ok',
    error_code        TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ai_usage_log_day_idx ON ai_usage_log (created_at DESC);
CREATE INDEX ai_usage_log_user_idx ON ai_usage_log (user_id, created_at DESC);

-- The model's own kill switch, separate from the chat's: turning this off leaves the
-- chat open and answers from the rule engine, which is the switch you want at 3am when
-- the bill or the provider misbehaves.
INSERT INTO feature_flags (key, description, enabled, default_value) VALUES
    ('ai_model_enabled', 'AI javoblarini model yozadi — o''chirilsa qoidalar javob beradi', TRUE, TRUE)
ON CONFLICT (key) DO NOTHING;
