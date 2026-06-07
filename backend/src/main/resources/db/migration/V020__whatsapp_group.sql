-- V020: Community WhatsApp group link + two-level visibility.
--   pool.whatsapp_group_url      — the invite link (null/blank = unset)
--   pool.whatsapp_group_enabled  — admin master switch ("available to captains")
--   users.whatsapp_group_visible — per-player opt-in, flipped by their inviter
-- Default hidden everywhere: the feature is dark until the admin enables it AND
-- (for players) their captain turns them on.
ALTER TABLE pool  ADD COLUMN whatsapp_group_url     VARCHAR(255);
ALTER TABLE pool  ADD COLUMN whatsapp_group_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN whatsapp_group_visible BOOLEAN NOT NULL DEFAULT false;
