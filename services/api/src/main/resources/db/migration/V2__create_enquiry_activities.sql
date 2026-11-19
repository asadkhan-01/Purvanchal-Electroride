CREATE TABLE enquiry_activities (
    id BIGSERIAL PRIMARY KEY,
    enquiry_id BIGINT NOT NULL REFERENCES enquiries(id) ON DELETE RESTRICT,
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT enquiry_activity_message_nonempty CHECK (length(trim(message)) > 0)
);
CREATE INDEX idx_enquiry_activities_lookup ON enquiry_activities (enquiry_id, created_at DESC, id DESC);
