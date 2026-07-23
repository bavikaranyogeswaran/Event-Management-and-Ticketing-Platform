-- an organizer's logo, uploaded through the file pipeline like an event banner
ALTER TABLE organizer_profiles
    ADD COLUMN image_file_id UUID REFERENCES file_assets (id);
