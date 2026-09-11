ALTER TABLE audit_event
    ADD COLUMN actor_display_name VARCHAR(160),
    ADD COLUMN entity_display_name VARCHAR(200),
    ADD COLUMN entity_display_reference VARCHAR(120),
    ADD COLUMN event_summary VARCHAR(300),
    ADD COLUMN origin VARCHAR(20),
    ADD COLUMN support_context VARCHAR(200),
    ADD COLUMN correlation_id VARCHAR(100);
