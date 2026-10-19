-- SOS is switched from the admin panel (2026-10-19). Off closes the alert and the contacts;
-- the app keeps the 103 and 112 buttons, which never depend on the server.
INSERT INTO feature_flags (key, description, enabled, default_value)
VALUES ('sos', 'SOS — o''chirilsa yaqinlarga signal va kontaktlar yopiladi, 103/112 tugmalari qoladi', TRUE, TRUE)
ON CONFLICT (key) DO NOTHING;
