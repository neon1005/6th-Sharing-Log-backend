ALTER TABLE group_members
    ADD COLUMN activation_generation BIGINT NULL;

UPDATE group_members
SET activation_generation = 1
WHERE activation_generation IS NULL;

ALTER TABLE group_members
    MODIFY COLUMN activation_generation BIGINT NOT NULL;

ALTER TABLE chores
    ADD COLUMN eligibility_revision BIGINT NULL;

UPDATE chores
SET eligibility_revision = 0
WHERE eligibility_revision IS NULL;

ALTER TABLE chores
    MODIFY COLUMN eligibility_revision BIGINT NOT NULL;

ALTER TABLE chore_eligible_members
    ADD COLUMN enabled BIT NULL,
    ADD COLUMN member_activation_generation BIGINT NULL,
    ADD COLUMN enrolled_at DATETIME(6) NULL,
    ADD COLUMN disabled_at DATETIME(6) NULL,
    ADD COLUMN fairness_credit BIGINT NULL;

UPDATE chore_eligible_members enrollment
JOIN chores chore
    ON chore.id = enrollment.chore_id
JOIN group_members member
    ON member.id = enrollment.membership_id
SET enrollment.enabled = 1,
    enrollment.member_activation_generation = member.activation_generation,
    enrollment.enrolled_at = CASE
        WHEN member.joined_at > chore.created_at THEN member.joined_at
        ELSE chore.created_at
    END,
    enrollment.fairness_credit = 0;

ALTER TABLE chore_eligible_members
    MODIFY COLUMN enabled BIT NOT NULL,
    MODIFY COLUMN member_activation_generation BIGINT NOT NULL,
    MODIFY COLUMN enrolled_at DATETIME(6) NOT NULL,
    MODIFY COLUMN fairness_credit BIGINT NOT NULL;

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
    1,
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
    ADD COLUMN member_activation_generation BIGINT NULL;

UPDATE occurrence_eligible_members snapshot
JOIN group_members member
    ON member.id = snapshot.membership_id
SET snapshot.member_activation_generation = member.activation_generation;

ALTER TABLE occurrence_eligible_members
    MODIFY COLUMN member_activation_generation BIGINT NOT NULL;
