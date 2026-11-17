ALTER TABLE oa_commission_attribution ALTER COLUMN application_id DROP NOT NULL;
ALTER TABLE oa_commission_attribution ALTER COLUMN job_id DROP NOT NULL;
ALTER TABLE oa_commission_attribution ADD COLUMN IF NOT EXISTS source_type VARCHAR(24) NOT NULL DEFAULT 'application';
ALTER TABLE oa_commission_attribution ADD COLUMN IF NOT EXISTS contract_source_id BIGINT UNIQUE;
ALTER TABLE oa_commission_attribution ADD COLUMN IF NOT EXISTS parent_id BIGINT;
ALTER TABLE oa_commission_attribution ADD COLUMN IF NOT EXISTS contract_version INT;
ALTER TABLE oa_commission_attribution ADD COLUMN IF NOT EXISTS settlement_mode VARCHAR(24) NOT NULL DEFAULT 'entry';
ALTER TABLE oa_commission_attribution ADD COLUMN IF NOT EXISTS acceptance_reference VARCHAR(1000);
