(() => {
    const groupId = new URLSearchParams(window.location.search).get("groupId");
    const groupPath = groupId
        ? `/api/groups/${encodeURIComponent(groupId)}`
        : null;
    const list = document.querySelector("#occurrence-list");
    const status = document.querySelector("#rotation-status");
    const tabs = [...document.querySelectorAll(".rotation-tab")];

    const createDialog = document.querySelector("#create-chore-dialog");
    const createForm = document.querySelector("#create-chore-form");
    const frequencyInput = document.querySelector("#chore-frequency");
    const weeklyField = document.querySelector("#weekly-day-field");
    const biweeklyField = document.querySelector("#biweekly-anchor-field");
    const eligibilityMode = document.querySelector("#chore-eligibility-mode");
    const selectedMembersField = document.querySelector("#selected-members-field");
    const eligibleMemberList = document.querySelector("#eligible-member-list");
    const createStatus = document.querySelector("#create-chore-status");

    const openManageButton = document.querySelector("#open-manage-rotation");
    const manageDialog = document.querySelector("#manage-rotation-dialog");
    const manageForm = document.querySelector("#manage-rotation-form");
    const manageMemberSelect = document.querySelector("#manage-member-select");
    const manageMemberSummary = document.querySelector("#manage-member-summary");
    const manageChoreOptions = document.querySelector("#manage-chore-options");
    const manageChoreList = document.querySelector("#manage-chore-list");
    const manageStatus = document.querySelector("#manage-rotation-status");
    const saveParticipationsButton = document.querySelector("#save-member-participations");
    const removeMemberButton = document.querySelector("#remove-group-member");
    const reissueInvitationButton = document.querySelector("#reissue-invitation-button");
    const reissuedInvitationUrl = document.querySelector("#reissued-invitation-url");
    const reissuedInvitationLink = document.querySelector("#reissued-invitation-link");
    const reissueInvitationStatus = document.querySelector("#reissue-invitation-status");

    const editChoreDialog = document.querySelector("#edit-chore-dialog");
    const editChoreForm = document.querySelector("#edit-chore-form");
    const editChoreFrequency = document.querySelector("#edit-chore-frequency");
    const editWeeklyField = document.querySelector("#edit-weekly-day-field");
    const editBiweeklyField = document.querySelector("#edit-biweekly-anchor-field");
    const editChoreStatus = document.querySelector("#edit-chore-status");
    const syncScheduleFields = syncSchedule.bind(null, frequencyInput, weeklyField, biweeklyField);
    const syncEditScheduleFields =
        syncSchedule.bind(null, editChoreFrequency, editWeeklyField, editBiweeklyField);

    const substituteDialog = document.querySelector("#substitute-requests-dialog");
    const substituteBox = document.querySelector("#substitute-request-box");
    const substituteList = document.querySelector("#substitute-request-list");
    const substituteStatus = document.querySelector("#substitute-request-status");

    const notificationButton = document.querySelector("#open-notifications");
    const notificationCount = document.querySelector("#notification-count");
    const notificationDialog = document.querySelector("#notifications-dialog");
    const notificationStatus = document.querySelector("#notification-status");
    const notificationDueSoonCount = document.querySelector("#notification-due-soon-count");
    const notificationSubstituteCount = document.querySelector("#notification-substitute-count");
    const notificationTotalCount = document.querySelector("#notification-total-count");
    const notificationDueSoonList = document.querySelector("#notification-due-soon-list");
    const notificationSubstituteList = document.querySelector("#notification-substitute-list");

    const completedHistoryDialog = document.querySelector("#completed-history-dialog");
    const completedHistoryList = document.querySelector("#completed-history-list");
    const completedHistoryStatus = document.querySelector("#completed-history-status");

    let frequency = "DAILY";
    let actorMembershipId = null;
    let canManage = false;
    let managementMembers = [];
    let managementChores = [];
    let editingChore = null;

    if (!groupId) {
        status.textContent = "주소에 groupId가 필요합니다. 그룹 생성 화면에서 로테이션을 열어 주세요.";
        return;
    }

    document.querySelector("#open-create-chore").addEventListener("click", () => {
        createStatus.textContent = "";
        createDialog.showModal();
        void loadEligibleMembers();
    });
    openManageButton.addEventListener("click", () => void openManagement());
    document.querySelector("#open-substitute-requests")
        .addEventListener("click", () => void openSubstituteRequests());
    notificationButton.addEventListener("click", () => void openNotifications());
    document.querySelector("#refresh-notifications")
        .addEventListener("click", () => void loadNotifications());
    document.querySelector("#open-completed-history")
        .addEventListener("click", () => void openCompletedHistory());
    document.querySelectorAll(".rotation-dialog .icon-button").forEach((button) =>
        button.addEventListener("click", () => button.closest("dialog").close())
    );
    manageMemberSelect.addEventListener("change", renderSelectedMemberManagement);
    removeMemberButton.addEventListener("click", () => void removeSelectedMember());
    reissueInvitationButton.addEventListener("click", () => void reissueInvitation());

    frequencyInput.addEventListener("change", syncScheduleFields);
    editChoreFrequency.addEventListener("change", syncEditScheduleFields);
    eligibilityMode.addEventListener("change", syncEligibilityField);
    substituteBox.addEventListener("change", () => void loadSubstituteRequests());
    tabs.forEach((tab) => tab.addEventListener("click", () => {
        activateFrequencyTab(tab.dataset.frequency);
        void loadOccurrences();
    }));
    createForm.addEventListener("submit", createChore);
    manageForm.addEventListener("submit", saveMemberParticipations);
    editChoreForm.addEventListener("submit", updateChore);

    document.querySelector("#chore-biweekly-anchor").value = mondayOfCurrentWeek();
    syncScheduleFields();
    syncEditScheduleFields();
    syncEligibilityField();
    void loadOccurrences();
    void loadManagementCapabilities();
    void loadNotificationSummary();

    async function loadOccurrences() {
        status.textContent = "업무를 불러오는 중...";
        list.replaceChildren();
        try {
            const response = await requestJson(
                `${groupPath}/occurrences?frequency=${encodeURIComponent(frequency)}`
            );
            const items = Array.isArray(response.items) ? response.items : [];
            renderOccurrences(items);
            status.textContent = items.length
                ? `${response.query.activeOn} 기준 ${items.length}개`
                : "이 주기의 현재 업무가 없습니다.";
        } catch (error) {
            status.textContent = errorMessage(error);
        }
    }

    function renderOccurrences(items) {
        const cards = items.map((item) => {
            const card = document.createElement("article");
            card.className = `occurrence-card status-${item.status.toLowerCase()}`;

            const heading = document.createElement("div");
            heading.className = "occurrence-heading";
            const title = document.createElement("div");
            const name = document.createElement("h2");
            name.textContent = item.choreName;
            const period = document.createElement("p");
            period.textContent = `${frequencyLabel(item.frequency)} · `
                + `${item.periodStart} — ${item.periodEndExclusive}`;
            title.append(name, period);
            const badge = document.createElement("span");
            badge.className = "status-badge";
            badge.textContent = statusLabel(item.status);
            heading.append(title, badge);

            const assignee = document.createElement("p");
            assignee.className = "assignee-line";
            assignee.textContent = item.currentAssignee
                ? `담당 · ${item.currentAssignee.displayName}`
                : item.lastAssignee
                    ? `마지막 담당 · ${item.lastAssignee.displayName}`
                    : "담당자 없음";

            const due = document.createElement("p");
            due.className = "due-line";
            due.textContent = `마감 · ${new Date(item.dueAt).toLocaleString("ko-KR")}`;

            const actions = document.createElement("div");
            actions.className = "occurrence-actions";
            for (const action of item.availableActions || []) {
                const button = document.createElement("button");
                button.type = "button";
                button.textContent = actionLabel(action);
                button.addEventListener("click", () => runAction(item, action, button));
                actions.append(button);
            }

            card.append(heading, assignee, due);
            if (item.attention) {
                const attention = document.createElement("p");
                attention.className = "attention-line";
                attention.textContent = "자동 배정 가능한 멤버가 없어 관리가 필요합니다.";
                card.append(attention);
            }
            if (item.substituteRequestNotice) {
                const substituteNotice = document.createElement("p");
                substituteNotice.className = "attention-line";
                substituteNotice.textContent = item.substituteRequestNotice;
                card.append(substituteNotice);
            }
            card.append(actions);
            return card;
        });
        list.replaceChildren(...cards);
    }

    async function runAction(item, action, button) {
        if (action === "REQUEST_SUBSTITUTE") {
            const reason = window.prompt("대타가 필요한 이유를 입력해 주세요.", "");
            if (reason === null) {
                return;
            }
            if (!reason.trim()) {
                status.textContent = "대타 요청 사유를 입력해 주세요.";
                return;
            }
            button.disabled = true;
            status.textContent = "대타를 요청하는 중...";
            try {
                await mutate(
                    `${groupPath}/occurrences/${encodeURIComponent(item.occurrenceId)}`
                        + "/substitute-requests",
                    {
                        method: "POST",
                        body: {reason: reason.trim()},
                        ifMatch: strongEtag(item.version)
                    }
                );
                status.textContent = "대타 요청을 보냈습니다.";
                substituteBox.value = "OUTBOX";
                await loadOccurrences();
                await openSubstituteRequests();
            } catch (error) {
                status.textContent = errorMessage(error);
                button.disabled = false;
            }
            return;
        }

        const paths = {
            COMPLETE: "complete",
            RETRY_ASSIGNMENT: "retry-assignment",
            UNDO_COMPLETE: "undo-complete"
        };
        let body = {};
        if (action === "UNDO_COMPLETE") {
            const note = window.prompt("완료 취소 메모를 남길 수 있어요.", "");
            if (note === null) {
                return;
            }
            body = {note: note || null};
        }
        if (action === "RETRY_ASSIGNMENT") {
            body = {eligibilitySource: "OCCURRENCE_SNAPSHOT", sourceChoreVersion: null};
        }
        button.disabled = true;
        status.textContent = "처리 중...";
        try {
            await mutate(
                `${groupPath}/occurrences/${encodeURIComponent(item.occurrenceId)}/${paths[action]}`,
                {
                    method: "POST",
                    body,
                    ifMatch: strongEtag(item.version)
                }
            );
            await loadOccurrences();
            if (completedHistoryDialog.open) {
                await loadCompletedHistory();
            }
        } catch (error) {
            status.textContent = errorMessage(error);
            button.disabled = false;
        }
    }

    async function createChore(event) {
        event.preventDefault();
        createStatus.textContent = "생성 중...";
        const selectedFrequency = frequencyInput.value;
        const body = {
            name: document.querySelector("#chore-name").value.trim(),
            schedule: {
                frequency: selectedFrequency,
                dueTime: document.querySelector("#chore-due-time").value,
                weeklyDueDay: selectedFrequency === "WEEKLY"
                    ? document.querySelector("#chore-weekly-day").value
                    : null,
                biweeklyAnchorDate: selectedFrequency === "BIWEEKLY"
                    ? document.querySelector("#chore-biweekly-anchor").value
                    : null
            },
            eligibility: {mode: "ALL_ACTIVE_MEMBERS", membershipIds: []}
        };
        body.eligibility.mode = eligibilityMode.value;
        body.eligibility.membershipIds = eligibilityMode.value === "SELECTED_MEMBERS"
            ? [...eligibleMemberList.querySelectorAll("input:checked")]
                .map((input) => input.value)
            : [];
        try {
            await mutate(`${groupPath}/chores`, {method: "POST", body});
            frequency = selectedFrequency;
            tabs.forEach((tab) => tab.classList.toggle(
                "is-active",
                tab.dataset.frequency === frequency
            ));
            createForm.reset();
            document.querySelector("#chore-due-time").value = "20:00";
            document.querySelector("#chore-biweekly-anchor").value = mondayOfCurrentWeek();
            syncScheduleFields();
            syncEligibilityField();
            createDialog.close();
            managementChores = [];
            await loadOccurrences();
        } catch (error) {
            createStatus.textContent = errorMessage(error);
        }
    }

    async function loadManagementCapabilities() {
        try {
            const response = await requestJson(`${groupPath}/rotation-members`);
            applyMemberResponse(response);
        } catch {
            openManageButton.hidden = true;
        }
    }

    function applyMemberResponse(response) {
        actorMembershipId = response.actorMembershipId || null;
        canManage = response.canManage === true;
        managementMembers = Array.isArray(response.items) ? response.items : [];
        openManageButton.hidden = !canManage;
        reissueInvitationButton.disabled = !canManage;
    }

    async function reissueInvitation() {
        if (!canManage) {
            return;
        }

        reissueInvitationButton.disabled = true;
        reissuedInvitationUrl.value = "";
        reissuedInvitationLink.href = "#";
        reissuedInvitationLink.hidden = true;
        reissueInvitationStatus.textContent = "새 초대 코드를 발급하는 중...";

        try {
            const invitation = await mutate(`${groupPath}/invitations/reissue`);
            const inviteUrl = new URL(invitation.inviteUrl, window.location.origin);
            if (inviteUrl.protocol !== "http:" && inviteUrl.protocol !== "https:") {
                throw new Error("초대 링크 주소가 올바르지 않습니다.");
            }

            reissuedInvitationUrl.value = inviteUrl.href;
            reissuedInvitationLink.href = inviteUrl.href;
            reissuedInvitationLink.hidden = false;
            reissueInvitationStatus.textContent =
                `새 초대 코드가 발급되었습니다. `
                + `${new Date(invitation.expiresAt).toLocaleString("ko-KR")}까지 사용할 수 있습니다.`;
        } catch (error) {
            reissueInvitationStatus.textContent = errorMessage(error);
        } finally {
            reissueInvitationButton.disabled = !canManage;
        }
    }

    async function openManagement() {
        if (!canManage) {
            return;
        }
        manageStatus.textContent = "";
        manageDialog.showModal();
        await loadManagementData(manageMemberSelect.value || actorMembershipId);
    }

    async function loadManagementData(preferredMembershipId) {
        manageStatus.textContent = "멤버와 업무를 불러오는 중...";
        manageMemberSelect.disabled = true;
        saveParticipationsButton.disabled = true;
        removeMemberButton.disabled = true;
        manageChoreOptions.textContent = "참여 업무를 불러오는 중...";
        manageChoreList.textContent = "업무를 불러오는 중...";
        try {
            const [memberResponse, choreResponse] = await Promise.all([
                requestJson(`${groupPath}/rotation-members`),
                requestJson(`${groupPath}/chores?active=all`)
            ]);
            applyMemberResponse(memberResponse);
            managementChores = Array.isArray(choreResponse.items) ? choreResponse.items : [];

            if (!canManage) {
                manageMemberSelect.replaceChildren();
                manageMemberSummary.textContent = "";
                manageChoreOptions.textContent = "로테이션 관리 권한이 없습니다.";
                manageChoreList.textContent = "로테이션 관리 권한이 없습니다.";
                manageStatus.textContent = "로테이션 관리 권한이 없습니다.";
                return;
            }

            const activeMembers = selectableMembers();
            const selectedMembershipId = activeMembers.some(
                (member) => member.membershipId === preferredMembershipId
            )
                ? preferredMembershipId
                : activeMembers[0]?.membershipId;
            renderMemberSelect(activeMembers, selectedMembershipId);
            renderSelectedMemberManagement();
            renderChoreManagementList();
            manageStatus.textContent = activeMembers.length
                ? ""
                : "관리할 활성 멤버가 없습니다.";
        } catch (error) {
            manageMemberSelect.replaceChildren();
            manageMemberSummary.textContent = "";
            manageChoreOptions.textContent = "참여 업무를 불러오지 못했습니다.";
            manageChoreList.textContent = "업무를 불러오지 못했습니다.";
            manageStatus.textContent = errorMessage(error);
        } finally {
            manageMemberSelect.disabled = selectableMembers().length === 0;
            saveParticipationsButton.disabled = selectableMembers().length === 0;
            removeMemberButton.disabled = selectableMembers().length === 0;
        }
    }

    function selectableMembers() {
        return managementMembers.filter(
            (member) => !member.status || member.status === "ACTIVE"
        );
    }

    function renderMemberSelect(members, selectedMembershipId) {
        const options = members.map((member) => {
            const option = document.createElement("option");
            option.value = member.membershipId;
            option.textContent = `${member.displayName} (${roleLabel(member.role)})`;
            option.selected = member.membershipId === selectedMembershipId;
            return option;
        });
        if (!options.length) {
            const option = document.createElement("option");
            option.textContent = "관리할 활성 멤버가 없습니다.";
            option.disabled = true;
            option.selected = true;
            options.push(option);
        }
        manageMemberSelect.replaceChildren(...options);
    }

    function selectedManagementMember() {
        return selectableMembers().find(
            (member) => member.membershipId === manageMemberSelect.value
        ) || null;
    }

    function renderSelectedMemberManagement() {
        const member = selectedManagementMember();
        if (!member) {
            manageMemberSummary.textContent = "";
            manageChoreOptions.textContent = "관리할 활성 멤버가 없습니다.";
            saveParticipationsButton.disabled = true;
            removeMemberButton.disabled = true;
            return;
        }

        const activeChores = managementChores.filter((chore) => chore.active !== false);
        const participationCount = activeChores.filter(
            (chore) => memberParticipates(chore, member.membershipId)
        ).length;
        manageMemberSummary.textContent =
            `${member.displayName} · ${roleLabel(member.role)} · 활성 업무 ${participationCount}개 참여`;
        removeMemberButton.textContent = member.membershipId === actorMembershipId
            ? "그룹에서 탈퇴"
            : "그룹에서 내보내기";
        saveParticipationsButton.disabled = false;
        removeMemberButton.disabled = false;

        if (!activeChores.length) {
            manageChoreOptions.textContent = "참여 설정을 변경할 활성 업무가 없습니다.";
            return;
        }

        const labels = activeChores.map((chore, index) => {
            const label = document.createElement("label");
            const checkbox = document.createElement("input");
            checkbox.type = "checkbox";
            checkbox.id = `manage-chore-option-${index}`;
            checkbox.value = chore.choreId;
            checkbox.checked = memberParticipates(chore, member.membershipId);

            const copy = document.createElement("span");
            copy.className = "choice-copy";
            const name = document.createElement("strong");
            name.textContent = chore.name;
            const schedule = document.createElement("small");
            schedule.textContent = scheduleDescription(chore.schedule);
            copy.append(name, schedule);
            label.append(checkbox, copy);
            return label;
        });
        manageChoreOptions.replaceChildren(...labels);
    }

    function renderChoreManagementList() {
        if (!managementChores.length) {
            manageChoreList.textContent = "등록된 업무가 없습니다.";
            return;
        }

        const rows = managementChores.map((chore) => {
            const row = document.createElement("div");
            row.className = "chore-management-row";
            const copy = document.createElement("div");
            const name = document.createElement("h4");
            name.textContent = chore.name;
            const meta = document.createElement("p");
            meta.className = "chore-management-meta";
            meta.textContent = `${scheduleDescription(chore.schedule)} · `
                + `${chore.active === false ? "비활성" : "활성"}`;
            copy.append(name, meta);
            row.append(copy);

            if (chore.active === false) {
                const badge = document.createElement("span");
                badge.className = "inactive-badge";
                badge.textContent = "비활성";
                row.append(badge);
            } else {
                const actions = document.createElement("div");
                actions.className = "management-row-actions";

                const editButton = document.createElement("button");
                editButton.type = "button";
                editButton.className = "secondary-button";
                editButton.textContent = "수정";
                editButton.setAttribute("aria-label", `${chore.name} 업무 수정`);
                editButton.addEventListener(
                    "click",
                    () => openEditChore(chore)
                );

                const deactivateButton = document.createElement("button");
                deactivateButton.type = "button";
                deactivateButton.className = "danger-outline-button";
                deactivateButton.textContent = "비활성화";
                deactivateButton.setAttribute("aria-label", `${chore.name} 업무 비활성화`);
                deactivateButton.addEventListener(
                    "click",
                    () => void deactivateChore(chore, deactivateButton)
                );
                actions.append(editButton, deactivateButton);
                row.append(actions);
            }
            return row;
        });
        manageChoreList.replaceChildren(...rows);
    }

    function memberParticipates(chore, membershipId) {
        const membershipIds = eligibilityMembershipIds(chore);
        return membershipIds.includes(membershipId);
    }

    function eligibilityMembershipIds(chore) {
        if (Array.isArray(chore.eligibility?.membershipIds)) {
            return chore.eligibility.membershipIds;
        }
        if (Array.isArray(chore.eligibility?.members)) {
            return chore.eligibility.members
                .map((member) => member.membershipId)
                .filter(Boolean);
        }
        return [];
    }

    async function saveMemberParticipations(event) {
        event.preventDefault();
        const member = selectedManagementMember();
        if (!member) {
            manageStatus.textContent = "관리할 멤버를 선택해 주세요.";
            return;
        }

        const checkedChoreIds = new Set(
            [...manageChoreOptions.querySelectorAll("input:checked")]
                .map((input) => input.value)
        );
        const addChoreIds = [];
        const removeChoreIds = [];
        const expectedVersions = {};
        for (const chore of managementChores.filter((item) => item.active !== false)) {
            const participated = memberParticipates(chore, member.membershipId);
            const shouldParticipate = checkedChoreIds.has(chore.choreId);
            if (participated === shouldParticipate) {
                continue;
            }
            (shouldParticipate ? addChoreIds : removeChoreIds).push(chore.choreId);
            expectedVersions[chore.choreId] = chore.version;
        }

        if (!addChoreIds.length && !removeChoreIds.length) {
            manageStatus.textContent = "변경된 참여 업무가 없습니다.";
            return;
        }

        const applicationScope = manageForm.querySelector(
            'input[name="application-scope"]:checked'
        ).value;
        saveParticipationsButton.disabled = true;
        manageMemberSelect.disabled = true;
        manageStatus.textContent = "참여 업무를 변경하는 중...";
        try {
            await mutate(
                `${groupPath}/rotation-members/`
                    + `${encodeURIComponent(member.membershipId)}/chore-participations`,
                {
                    method: "PATCH",
                    body: {
                        addChoreIds,
                        removeChoreIds,
                        applicationScope,
                        expectedVersions
                    }
                }
            );
            await Promise.all([
                loadOccurrences(),
                loadManagementData(member.membershipId)
            ]);
            const scopeText = applicationScope === "CURRENT_AND_FUTURE"
                ? "현재 회차부터"
                : "다음 회차부터";
            manageStatus.textContent =
                `${scopeText} 적용했습니다. 추가 ${addChoreIds.length}개, 제외 ${removeChoreIds.length}개`;
        } catch (error) {
            manageStatus.textContent = errorMessage(error);
        } finally {
            manageMemberSelect.disabled = selectableMembers().length === 0;
            saveParticipationsButton.disabled = selectableMembers().length === 0;
        }
    }

    async function removeSelectedMember() {
        const member = selectedManagementMember();
        if (!member) {
            manageStatus.textContent = "관리할 멤버를 선택해 주세요.";
            return;
        }

        const self = member.membershipId === actorMembershipId;
        const confirmed = window.confirm(
            self
                ? "그룹에서 탈퇴할까요?\n현재 담당 중인 모든 미완료 업무는 즉시 재배정됩니다."
                : `${member.displayName}님을 그룹에서 내보낼까요?\n`
                    + "현재 담당 중인 모든 미완료 업무는 즉시 재배정됩니다."
        );
        if (!confirmed) {
            return;
        }

        removeMemberButton.disabled = true;
        saveParticipationsButton.disabled = true;
        manageStatus.textContent = self ? "그룹에서 탈퇴하는 중..." : "멤버를 내보내는 중...";
        try {
            const response = await mutate(
                `${groupPath}/members/${encodeURIComponent(member.membershipId)}/leave`,
                {
                    method: "POST",
                    body: {},
                    ifMatch: strongEtag(member.version)
                }
            );
            const message = leaveResultMessage(response, member.displayName, self);
            if (self) {
                canManage = false;
                openManageButton.hidden = true;
                manageDialog.close();
                status.textContent = message;
                return;
            }
            await Promise.all([
                loadOccurrences(),
                loadManagementData(actorMembershipId)
            ]);
            manageStatus.textContent = message;
        } catch (error) {
            manageStatus.textContent = errorMessage(error);
        } finally {
            removeMemberButton.disabled = selectableMembers().length === 0;
            saveParticipationsButton.disabled = selectableMembers().length === 0;
        }
    }

    function leaveResultMessage(response, displayName, self) {
        const base = self
            ? "그룹에서 탈퇴했습니다."
            : `${displayName}님을 그룹에서 내보냈습니다.`;
        const summary = response?.reassignmentSummary;
        if (!summary || !summary.processedCount) {
            return base;
        }
        return `${base} 미완료 업무 ${summary.processedCount}건 중 `
            + `${summary.reassignedCount}건 재배정, `
            + `${summary.needsAttentionCount}건 관리 필요`;
    }

    function openEditChore(chore) {
        editingChore = chore;
        editChoreStatus.textContent =
            "일정 변경은 현재 진행 중인 회차와 다음 회차부터 바로 반영됩니다.";
        document.querySelector("#edit-chore-name").value = chore.name;
        editChoreFrequency.value = chore.schedule.frequency;
        document.querySelector("#edit-chore-due-time").value =
            (chore.schedule.dueTime || "20:00").slice(0, 5);
        document.querySelector("#edit-chore-weekly-day").value =
            chore.schedule.weeklyDueDay || "SUNDAY";
        document.querySelector("#edit-chore-biweekly-anchor").value =
            chore.schedule.biweeklyAnchorDate || mondayOfCurrentWeek();
        syncEditScheduleFields();
        editChoreDialog.showModal();
    }

    async function updateChore(event) {
        event.preventDefault();
        if (!editingChore) {
            editChoreStatus.textContent = "수정할 업무를 다시 선택해 주세요.";
            return;
        }

        const selectedFrequency = editChoreFrequency.value;
        const body = {
            name: document.querySelector("#edit-chore-name").value.trim(),
            schedule: {
                frequency: selectedFrequency,
                dueTime: document.querySelector("#edit-chore-due-time").value,
                weeklyDueDay: selectedFrequency === "WEEKLY"
                    ? document.querySelector("#edit-chore-weekly-day").value
                    : null,
                biweeklyAnchorDate: selectedFrequency === "BIWEEKLY"
                    ? document.querySelector("#edit-chore-biweekly-anchor").value
                    : null
            }
        };
        editChoreStatus.textContent = "업무를 수정하는 중...";
        try {
            await mutate(
                `${groupPath}/chores/${encodeURIComponent(editingChore.choreId)}`,
                {
                    method: "PATCH",
                    body,
                    ifMatch: strongEtag(editingChore.version)
                }
            );
            const selectedMembershipId = manageMemberSelect.value;
            editChoreDialog.close();
            editingChore = null;
            activateFrequencyTab(selectedFrequency);
            await Promise.all([
                loadOccurrences(),
                loadManagementData(selectedMembershipId)
            ]);
            manageStatus.textContent = "업무명과 일정을 수정했습니다.";
        } catch (error) {
            editChoreStatus.textContent = errorMessage(error);
        }
    }

    async function loadNotificationSummary() {
        try {
            const summary = await requestJson(`${groupPath}/notifications/summary`);
            renderNotificationSummary(summary);
        } catch (error) {
            notificationCount.textContent = "!";
            notificationCount.setAttribute("aria-label", "알림 요약 조회 실패");
            notificationButton.title = errorMessage(error);
        }
    }

    function renderNotificationSummary(summary) {
        const dueSoonCount = Number(summary.dueSoonCount) || 0;
        const substituteCount = Number(summary.pendingSubstituteRequestCount) || 0;
        const totalCount = Number(summary.unreadCount) || 0;

        notificationDueSoonCount.textContent = String(dueSoonCount);
        notificationSubstituteCount.textContent = String(substituteCount);
        notificationTotalCount.textContent = String(totalCount);
        notificationCount.textContent = totalCount > 99 ? "99+" : String(totalCount);
        notificationCount.setAttribute("aria-label", `알림 대상 ${totalCount}건`);
        notificationButton.title = `마감 임박 ${dueSoonCount}건, 받은 대타 요청 ${substituteCount}건`;
    }

    async function openNotifications() {
        notificationStatus.textContent = "";
        notificationDialog.showModal();
        await loadNotifications();
    }

    async function loadNotifications() {
        notificationStatus.textContent = "알림 API 3개를 확인하는 중...";
        notificationDueSoonList.replaceChildren();
        notificationSubstituteList.replaceChildren();
        try {
            const [summary, dueSoonResponse, substituteResponse] = await Promise.all([
                requestJson(`${groupPath}/notifications/summary`),
                requestJson(`${groupPath}/occurrences/due-soon`),
                requestJson(`${groupPath}/substitute-requests?box=INBOX&status=PENDING`)
            ]);
            const dueSoonItems = Array.isArray(dueSoonResponse.items)
                ? dueSoonResponse.items
                : [];
            const substituteItems = Array.isArray(substituteResponse.items)
                ? substituteResponse.items
                : [];

            renderNotificationSummary(summary);
            renderDueSoonNotifications(dueSoonItems);
            renderPendingSubstituteNotifications(substituteItems);
            notificationStatus.textContent =
                `API 응답 확인 완료 · 마감 임박 ${dueSoonItems.length}건 · 대타 요청 ${substituteItems.length}건`;
        } catch (error) {
            notificationStatus.textContent = errorMessage(error);
        }
    }

    function renderDueSoonNotifications(items) {
        if (!items.length) {
            renderNotificationEmpty(notificationDueSoonList, "마감 임박 업무가 없습니다.");
            return;
        }

        const cards = items.map((item) => {
            const card = document.createElement("article");
            card.className = "workflow-card";
            const heading = document.createElement("div");
            heading.className = "occurrence-heading";
            const title = document.createElement("div");
            const name = document.createElement("h3");
            name.textContent = item.choreName;
            const period = document.createElement("p");
            period.textContent = `${item.periodStart} ~ ${item.periodEndExclusive}`;
            title.append(name, period);
            const badge = document.createElement("span");
            badge.className = "status-badge";
            badge.textContent = statusLabel(item.status);
            heading.append(title, badge);

            const assignee = document.createElement("p");
            assignee.className = "workflow-meta";
            assignee.textContent = `담당 · ${item.currentAssignee?.displayName || "담당자 없음"}`;
            const due = document.createElement("p");
            due.className = "workflow-meta";
            due.textContent = `마감 · ${new Date(item.dueAt).toLocaleString("ko-KR")}`;
            card.append(heading, assignee, due);
            return card;
        });
        notificationDueSoonList.replaceChildren(...cards);
    }

    function renderPendingSubstituteNotifications(items) {
        if (!items.length) {
            renderNotificationEmpty(notificationSubstituteList, "받은 대타 요청이 없습니다.");
            return;
        }

        const cards = items.map((item) => {
            const card = document.createElement("article");
            card.className = "workflow-card";
            const heading = document.createElement("div");
            heading.className = "occurrence-heading";
            const title = document.createElement("div");
            const name = document.createElement("h3");
            name.textContent = item.choreName;
            const requester = document.createElement("p");
            requester.textContent = `요청자 · ${item.requester?.displayName || "알 수 없음"}`;
            title.append(name, requester);
            const badge = document.createElement("span");
            badge.className = "status-badge";
            badge.textContent = substituteRequestStatusLabel(item.status);
            heading.append(title, badge);

            const reason = document.createElement("p");
            reason.className = "workflow-reason";
            reason.textContent = item.reason || "사유 없음";
            const due = document.createElement("p");
            due.className = "workflow-meta";
            due.textContent = `마감 · ${new Date(item.dueAt).toLocaleString("ko-KR")}`;
            card.append(heading, reason, due);
            return card;
        });
        notificationSubstituteList.replaceChildren(...cards);
    }

    function renderNotificationEmpty(target, message) {
        const empty = document.createElement("p");
        empty.className = "notification-empty";
        empty.textContent = message;
        target.replaceChildren(empty);
    }

    async function openSubstituteRequests() {
        substituteStatus.textContent = "";
        substituteDialog.showModal();
        await loadSubstituteRequests();
    }

    async function loadSubstituteRequests() {
        substituteStatus.textContent = "대타 요청을 불러오는 중...";
        substituteList.replaceChildren();
        try {
            const box = substituteBox.value;
            const response = await requestJson(
                `${groupPath}/substitute-requests?box=${encodeURIComponent(box)}`
            );
            const items = Array.isArray(response.items) ? response.items : [];
            renderSubstituteRequests(items, box);
            substituteStatus.textContent = items.length
                ? `${items.length}건`
                : "표시할 대타 요청이 없습니다.";
        } catch (error) {
            substituteStatus.textContent = errorMessage(error);
        }
    }

    function renderSubstituteRequests(items, box) {
        const cards = items.map((request) => {
            const card = document.createElement("article");
            card.className = "workflow-card";

            const heading = document.createElement("div");
            heading.className = "occurrence-heading";
            const title = document.createElement("div");
            const name = document.createElement("h3");
            name.textContent = request.choreName;
            const period = document.createElement("p");
            period.textContent =
                `${request.periodStart} — ${request.periodEndExclusive}`;
            title.append(name, period);
            const badge = document.createElement("span");
            badge.className = "status-badge";
            badge.textContent = substituteRequestStatusLabel(request.status);
            heading.append(title, badge);

            const requester = document.createElement("p");
            requester.className = "workflow-meta";
            requester.textContent =
                `요청자 · ${request.requester?.displayName || "알 수 없음"}`;
            const reason = document.createElement("p");
            reason.className = "workflow-reason";
            reason.textContent = request.reason;
            const recipients = document.createElement("p");
            recipients.className = "workflow-meta";
            recipients.textContent = (request.recipients || [])
                .map((item) =>
                    `${item.member.displayName}(${substituteRecipientStatusLabel(item.status)})`
                )
                .join(", ");

            card.append(heading, requester, reason, recipients);
            if (box === "INBOX" && request.status === "PENDING") {
                const actions = document.createElement("div");
                actions.className = "occurrence-actions";
                const accept = document.createElement("button");
                accept.type = "button";
                accept.textContent = "수락";
                accept.addEventListener(
                    "click",
                    () => void respondToSubstituteRequest(request, "accept", accept)
                );
                const reject = document.createElement("button");
                reject.type = "button";
                reject.className = "secondary-button";
                reject.textContent = "거절";
                reject.addEventListener(
                    "click",
                    () => void respondToSubstituteRequest(request, "reject", reject)
                );
                actions.append(accept, reject);
                card.append(actions);
            }
            return card;
        });
        substituteList.replaceChildren(...cards);
    }

    async function respondToSubstituteRequest(request, response, button) {
        button.disabled = true;
        substituteStatus.textContent =
            response === "accept" ? "대타 요청을 수락하는 중..." : "대타 요청을 거절하는 중...";
        try {
            await mutate(
                `${groupPath}/substitute-requests/`
                    + `${encodeURIComponent(request.requestId)}/${response}`,
                {
                    method: "POST",
                    ifMatch: strongEtag(request.version)
                }
            );
            await Promise.all([
                loadSubstituteRequests(),
                loadOccurrences()
            ]);
        } catch (error) {
            substituteStatus.textContent = errorMessage(error);
            button.disabled = false;
        }
    }

    async function openCompletedHistory() {
        completedHistoryStatus.textContent = "";
        completedHistoryDialog.showModal();
        await loadCompletedHistory();
    }

    async function loadCompletedHistory() {
        completedHistoryStatus.textContent = "완료 이력을 불러오는 중...";
        completedHistoryList.replaceChildren();
        try {
            const response = await requestJson(
                `${groupPath}/occurrences/completed-history?mineOnly=false`
            );
            const items = Array.isArray(response.items) ? response.items : [];
            renderCompletedHistory(items);
            completedHistoryStatus.textContent = items.length
                ? `완료 ${response.totalCount}건`
                : "완료된 업무가 없습니다.";
        } catch (error) {
            completedHistoryStatus.textContent = errorMessage(error);
        }
    }

    function renderCompletedHistory(items) {
        const cards = items.map((item) => {
            const card = document.createElement("article");
            card.className = "workflow-card";
            const heading = document.createElement("div");
            heading.className = "occurrence-heading";
            const title = document.createElement("div");
            const name = document.createElement("h3");
            name.textContent = item.choreName;
            const period = document.createElement("p");
            period.textContent = `${item.periodStart} — ${item.periodEndExclusive}`;
            title.append(name, period);
            const badge = document.createElement("span");
            badge.className = "status-badge";
            badge.textContent = "완료";
            heading.append(title, badge);

            const assignee = document.createElement("p");
            assignee.className = "workflow-meta";
            assignee.textContent =
                `완료자 · ${item.lastAssignee?.displayName || "알 수 없음"}`;
            const completedAt = document.createElement("p");
            completedAt.className = "workflow-meta";
            completedAt.textContent =
                `완료 시각 · ${new Date(item.closedAt).toLocaleString("ko-KR")}`;
            card.append(heading, assignee, completedAt);

            if ((item.availableActions || []).includes("UNDO_COMPLETE")) {
                const actions = document.createElement("div");
                actions.className = "occurrence-actions";
                const undo = document.createElement("button");
                undo.type = "button";
                undo.textContent = "완료 취소";
                undo.addEventListener(
                    "click",
                    () => void undoCompletedOccurrence(item, undo)
                );
                actions.append(undo);
                card.append(actions);
            }
            return card;
        });
        completedHistoryList.replaceChildren(...cards);
    }

    async function undoCompletedOccurrence(item, button) {
        const note = window.prompt("완료 취소 메모를 남길 수 있어요.", "");
        if (note === null) {
            return;
        }
        button.disabled = true;
        completedHistoryStatus.textContent = "완료를 취소하는 중...";
        try {
            await mutate(
                `${groupPath}/occurrences/${encodeURIComponent(item.occurrenceId)}`
                    + "/undo-complete",
                {
                    method: "POST",
                    body: {note: note || null},
                    ifMatch: strongEtag(item.version)
                }
            );
            await Promise.all([
                loadCompletedHistory(),
                loadOccurrences()
            ]);
        } catch (error) {
            completedHistoryStatus.textContent = errorMessage(error);
            button.disabled = false;
        }
    }

    async function deactivateChore(chore, button) {
        const confirmed = window.confirm(
            `${chore.name} 업무를 비활성화할까요?\n`
                + "새 회차 생성은 중단되지만 이미 생성된 회차와 과거 이력은 유지됩니다."
        );
        if (!confirmed) {
            return;
        }

        button.disabled = true;
        manageStatus.textContent = "업무를 비활성화하는 중...";
        try {
            await mutate(
                `${groupPath}/chores/${encodeURIComponent(chore.choreId)}`,
                {
                    method: "DELETE",
                    ifMatch: strongEtag(chore.version)
                }
            );
            await Promise.all([
                loadOccurrences(),
                loadManagementData(manageMemberSelect.value)
            ]);
            manageStatus.textContent =
                `${chore.name} 업무를 비활성화했습니다. 이미 생성된 회차는 유지됩니다.`;
        } catch (error) {
            manageStatus.textContent = errorMessage(error);
            button.disabled = false;
        }
    }

    async function mutate(url, {method = "POST", body, ifMatch} = {}) {
        const csrf = await requestJson("/api/auth/csrf");
        const headers = {
            Accept: "application/json",
            "Idempotency-Key": newIdempotencyKey(),
            [csrf.headerName]: csrf.token
        };
        if (body !== undefined) {
            headers["Content-Type"] = "application/json";
        }
        if (ifMatch) {
            headers["If-Match"] = ifMatch;
        }
        return requestJson(url, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
    }

    async function requestJson(url, options = {}) {
        const response = await fetch(url, {
            credentials: "same-origin",
            cache: "no-store",
            ...options
        });
        if (response.redirected) {
            throw new Error("로그인 세션이 만료되었습니다.");
        }
        if (response.status === 204 || response.status === 205) {
            if (!response.ok) {
                throw new Error(`요청 실패 (${response.status})`);
            }
            return null;
        }

        const contentType = response.headers.get("content-type") || "";
        if (!contentType.toLowerCase().includes("json")) {
            if (!response.ok) {
                throw new Error(`요청 실패 (${response.status})`);
            }
            throw new Error("로그인 세션이 만료되었습니다.");
        }

        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.detail || `요청 실패 (${response.status})`);
        }
        return data;
    }

    function syncSchedule(input, weekly, biweekly) {
        weekly.hidden = input.value !== "WEEKLY";
        biweekly.hidden = input.value !== "BIWEEKLY";
    }

    function syncEligibilityField() {
        selectedMembersField.hidden = eligibilityMode.value !== "SELECTED_MEMBERS";
    }

    async function loadEligibleMembers() {
        eligibleMemberList.textContent = "멤버를 불러오는 중...";
        try {
            const response = await requestJson(`${groupPath}/rotation-members`);
            applyMemberResponse(response);
            const items = selectableMembers();
            if (!items.length) {
                eligibleMemberList.textContent = "담당 가능한 활성 멤버가 없습니다.";
                return;
            }
            const labels = items.map((member) => {
                const label = document.createElement("label");
                const checkbox = document.createElement("input");
                checkbox.type = "checkbox";
                checkbox.value = member.membershipId;
                const text = document.createElement("span");
                text.textContent = `${member.displayName} (${roleLabel(member.role)})`;
                label.append(checkbox, text);
                return label;
            });
            eligibleMemberList.replaceChildren(...labels);
        } catch (error) {
            eligibleMemberList.textContent = errorMessage(error);
        }
    }

    function mondayOfCurrentWeek() {
        const date = new Date();
        const day = date.getDay() || 7;
        date.setDate(date.getDate() - day + 1);
        return date.toISOString().slice(0, 10);
    }

    function activateFrequencyTab(nextFrequency) {
        frequency = nextFrequency;
        tabs.forEach((tab) => tab.classList.toggle(
            "is-active",
            tab.dataset.frequency === nextFrequency
        ));
    }

    function strongEtag(version) {
        return version === undefined || version === null ? null : `"${version}"`;
    }

    function newIdempotencyKey() {
        const browserCrypto = globalThis.crypto;
        if (browserCrypto && typeof browserCrypto.randomUUID === "function") {
            return browserCrypto.randomUUID();
        }
        if (browserCrypto && typeof browserCrypto.getRandomValues === "function") {
            const bytes = new Uint8Array(16);
            browserCrypto.getRandomValues(bytes);
            bytes[6] = (bytes[6] & 0x0f) | 0x40;
            bytes[8] = (bytes[8] & 0x3f) | 0x80;
            const hex = [...bytes].map((value) =>
                value.toString(16).padStart(2, "0")
            );
            return [
                hex.slice(0, 4).join(""),
                hex.slice(4, 6).join(""),
                hex.slice(6, 8).join(""),
                hex.slice(8, 10).join(""),
                hex.slice(10, 16).join("")
            ].join("-");
        }
        return `fallback-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    }

    const LABELS = {
        status: {ASSIGNED: "배정됨", COMPLETED: "완료", SKIPPED: "생략", NEEDS_ATTENTION: "관리 필요"},
        action: {
            COMPLETE: "업무 완료", REQUEST_SUBSTITUTE: "대타 요청",
            RETRY_ASSIGNMENT: "자동 배정 다시 시도", UNDO_COMPLETE: "완료 취소"
        },
        requestStatus: {PENDING: "응답 대기", ACCEPTED: "수락됨", EXHAUSTED: "전원 거절", CANCELLED: "취소됨"},
        recipientStatus: {PENDING: "대기", ACCEPTED: "수락", DECLINED: "거절", INELIGIBLE: "응답 종료"},
        frequency: {DAILY: "매일", WEEKLY: "매주", BIWEEKLY: "격주"},
        weekday: {
            MONDAY: "월요일", TUESDAY: "화요일", WEDNESDAY: "수요일",
            THURSDAY: "목요일", FRIDAY: "금요일", SATURDAY: "토요일", SUNDAY: "일요일"
        },
        role: {OWNER: "소유자", MEMBER: "멤버"}
    };

    function label(labels, value, fallback = value) {
        return labels[value] || fallback;
    }

    function statusLabel(value) {
        return label(LABELS.status, value);
    }

    function actionLabel(value) {
        return label(LABELS.action, value);
    }

    function substituteRequestStatusLabel(value) {
        return label(LABELS.requestStatus, value);
    }

    function substituteRecipientStatusLabel(value) {
        return label(LABELS.recipientStatus, value);
    }

    function frequencyLabel(value) {
        return label(LABELS.frequency, value, value || "주기 미정");
    }

    function scheduleDescription(schedule) {
        const frequencyValue = schedule?.frequency;
        const dueTime = (schedule?.dueTime || "").slice(0, 5) || "시간 미정";
        if (frequencyValue === "WEEKLY") {
            return `${frequencyLabel(frequencyValue)} · `
                + `${label(LABELS.weekday, schedule.weeklyDueDay, "요일 미정")} `
                + `${dueTime} 마감`;
        }
        if (frequencyValue === "BIWEEKLY") {
            return `${frequencyLabel(frequencyValue)} · `
                + `${schedule.biweeklyAnchorDate || "시작일 미정"} 시작 · `
                + `${dueTime} 마감`;
        }
        return `${frequencyLabel(frequencyValue)} · ${dueTime} 마감`;
    }

    function roleLabel(value) {
        return label(LABELS.role, value);
    }

    function errorMessage(error) {
        return error instanceof Error ? error.message : "요청 처리 중 오류가 발생했습니다.";
    }
})();
