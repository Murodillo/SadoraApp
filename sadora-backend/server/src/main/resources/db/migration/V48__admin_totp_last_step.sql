-- The time step of the last TOTP code an admin signed in with. A code is valid for about
-- 90 seconds (its own step and one either side); remembering the step it matched means a
-- phished or shoulder-surfed code cannot be used a second time inside that window.
ALTER TABLE admin_users ADD COLUMN totp_last_step BIGINT;
