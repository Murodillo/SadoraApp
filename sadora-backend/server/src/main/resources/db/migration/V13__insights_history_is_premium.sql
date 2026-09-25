-- Insights depth is a Premium feature, and the entitlement row now says so.
--
-- The app has always drawn the 30- and 90-day ranges locked for a free account, but
-- `insights_history` was seeded free-enabled, so the server would have served them to
-- anyone who asked. The lock was a drawing, not a rule. This makes the row mean what the
-- screen shows: seven days for everyone, longer windows behind the subscription.
UPDATE feature_definitions
SET free_enabled = FALSE,
    description  = 'Insights: 7 kundan uzoq tarix (30 va 90 kun)',
    updated_at   = now()
WHERE key = 'insights_history';
