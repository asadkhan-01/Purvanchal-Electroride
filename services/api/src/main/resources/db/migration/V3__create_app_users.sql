CREATE TABLE app_users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  role VARCHAR(30) NOT NULL CHECK (role IN ('ADMIN', 'BRANCH_MANAGER', 'SALESPERSON')),
  branch VARCHAR(100),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT non_admin_branch_required CHECK (role = 'ADMIN' OR branch IS NOT NULL)
);
CREATE INDEX idx_app_users_branch_role ON app_users(branch, role);
