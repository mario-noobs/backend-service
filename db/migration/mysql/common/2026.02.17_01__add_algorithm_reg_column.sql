-- Add algorithm_reg column to face_features table
-- Tracks which detection+recognition algorithm pair was used during registration
ALTER TABLE face_features
ADD COLUMN algorithm_reg VARCHAR(50) DEFAULT 'facenet_mobilenet' AFTER feature_vector;
