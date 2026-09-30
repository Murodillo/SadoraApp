-- The windows V30 carried over from before sessions existed have no first reply, though
-- the doctor may well have answered in them (2026-09-30). Without it they count as
-- unanswered in her numbers and the patient cannot rate them. Her first line inside each
-- window is its first reply.
UPDATE consultation_sessions s
SET first_reply_at = (
    SELECT min(m.created_at)
    FROM community_messages m
    JOIN doctor_profiles d ON d.id = s.doctor_id
    WHERE m.conversation_id = s.conversation_id
      AND m.sender_id = d.user_id
      AND m.created_at >= s.opened_at
      AND m.created_at <= COALESCE(s.closed_at, s.expires_at)
)
WHERE s.first_reply_at IS NULL
  AND s.opened_at IS NOT NULL
  AND s.payment_state = 'free';
