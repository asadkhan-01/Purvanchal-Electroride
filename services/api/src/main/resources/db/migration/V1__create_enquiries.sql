CREATE TABLE enquiries (
    id BIGSERIAL PRIMARY KEY,
    customer_name VARCHAR(160) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    branch VARCHAR(100) NOT NULL,
    salesperson VARCHAR(120) NOT NULL,
    source VARCHAR(80) NOT NULL,
    interested_model VARCHAR(180) NOT NULL,
    stage VARCHAR(30) NOT NULL DEFAULT 'New',
    follow_up_date DATE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT enquiries_stage_valid CHECK (stage IN ('New','Contacted','Interested','Booked','Lost'))
);
CREATE INDEX idx_enquiries_branch ON enquiries (branch);
CREATE INDEX idx_enquiries_salesperson ON enquiries (salesperson);
CREATE INDEX idx_enquiries_follow_up ON enquiries (follow_up_date);
