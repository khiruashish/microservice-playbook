-- expand: a new column with a default, so instances running V1's code keep working during the rollout (ADR-033)
ALTER TABLE menu_item ADD COLUMN available boolean NOT NULL DEFAULT true;
