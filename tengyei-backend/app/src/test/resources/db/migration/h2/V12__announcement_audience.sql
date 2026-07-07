ALTER TABLE sys_announcement ADD COLUMN audience_type VARCHAR(16) NOT NULL DEFAULT 'ALL';
ALTER TABLE sys_announcement ADD COLUMN audience_ids VARCHAR(512);
