ALTER TABLE group_members
    ADD COLUMN activation_generation BIGINT;

UPDATE group_members
SET activation_generation = 1
WHERE activation_generation IS NULL;

ALTER TABLE group_members
    ALTER COLUMN activation_generation SET NOT NULL;

ALTER TABLE chores
    ADD COLUMN eligibility_revision BIGINT;

UPDATE chores
SET eligibility_revision = 0
WHERE eligibility_revision IS NULL;

ALTER TABLE chores
    ALTER COLUMN eligibility_revision SET NOT NULL;

ALTER TABLE chore_eligible_members
    ADD COLUMN enabled BOOLEAN;
ALTER TABLE chore_eligible_members
    ADD COLUMN member_activation_generation BIGINT;
ALTER TABLE chore_eligible_members
    ADD COLUMN enrolled_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE chore_eligible_members
    ADD COLUMN disabled_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE chore_eligible_members
    ADD COLUMN fairness_credit BIGINT;

UPDATE chore_eligible_members
SET enabled = TRUE,
    member_activation_generation = (
        SELECT member.activation_generation
        FROM group_members member
        WHERE member.id = chore_eligible_members.membership_id
    ),
    enrolled_at = (
        SELECT CASE
            WHEN member.joined_at > chore.created_at THEN member.joined_at
            ELSE chore.created_at
        END
        FROM chores chore
        JOIN group_members member
            ON member.id = chore_eligible_members.membership_id
        WHERE chore.id = chore_eligible_members.chore_id
    ),
    fairness_credit = 0;

ALTER TABLE chore_eligible_members
    ALTER COLUMN enabled SET NOT NULL;
ALTER TABLE chore_eligible_members
    ALTER COLUMN member_activation_generation SET NOT NULL;
ALTER TABLE chore_eligible_members
    ALTER COLUMN enrolled_at SET NOT NULL;
ALTER TABLE chore_eligible_members
    ALTER COLUMN fairness_credit SET NOT NULL;

INSERT INTO chore_eligible_members (
    chore_id,
    membership_id,
    enabled,
    member_activation_generation,
    enrolled_at,
    disabled_at,
    fairness_credit
)
SELECT
    chore.id,
    member.id,
    TRUE,
    member.activation_generation,
    CASE
        WHEN member.joined_at > chore.created_at THEN member.joined_at
        ELSE chore.created_at
    END,
    NULL,
    0
FROM chores chore
JOIN group_members member
    ON member.group_id = chore.group_id
WHERE chore.eligibility_mode = 'ALL_ACTIVE_MEMBERS'
  AND member.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM chore_eligible_members existing
      WHERE existing.chore_id = chore.id
        AND existing.membership_id = member.id
  );

CREATE INDEX idx_chore_eligible_members_chore_enabled
    ON chore_eligible_members (chore_id, enabled);

ALTER TABLE occurrence_eligible_members
    ADD COLUMN member_activation_generation BIGINT;

UPDATE occurrence_eligible_members
SET member_activation_generation = (
    SELECT member.activation_generation
    FROM group_members member
    WHERE member.id = occurrence_eligible_members.membership_id
);

ALTER TABLE occurrence_eligible_members
    ALTER COLUMN member_activation_generation SET NOT NULL;
