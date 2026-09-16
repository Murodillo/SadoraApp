-- The room is called "Chat" in the app now, not "Maxfiy chat"; the flag's description in
-- the admin panel follows so an operator reads the same name the users see.
UPDATE feature_flags
SET description = 'Chat — o''chirilsa lenta va yozish butunlay yopiladi',
    updated_at  = now()
WHERE key = 'community';
