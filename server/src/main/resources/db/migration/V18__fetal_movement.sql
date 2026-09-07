-- The pregnancy check-in asks about foetal movement and had nowhere to put the answer.
--
-- It is the one question in the app that escalates: a marked drop is told to see a
-- doctor without delay. Asking it and discarding it was the worst of both — the user
-- believes it was recorded, and nothing ever looks at it again.
--
-- Nullable, and on the day row rather than a table of its own: it is one answer per
-- day, edited as part of the day, exactly like mood.
ALTER TABLE daily_logs ADD COLUMN fetal_movement TEXT;
