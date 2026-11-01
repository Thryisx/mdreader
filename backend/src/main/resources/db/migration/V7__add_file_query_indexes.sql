ALTER TABLE `files`
    ADD KEY `idx_files_public_feed` (`visibility`, `status`, `node_type`, `created_at`, `id`),
    ADD KEY `idx_files_user_type_updated` (`user_id`, `node_type`, `updated_at`, `id`);
