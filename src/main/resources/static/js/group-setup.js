(() => {
    const groupForm = document.querySelector("#group-form");
    if (!groupForm) {
        return;
    }

    const groupNameInput = document.querySelector("#group-name");
    const groupAddressInput = document.querySelector("#group-address");
    const createGroupButton = document.querySelector("#create-group-button");
    const issueInvitationButton = document.querySelector("#issue-invitation-button");
    const groupResult = document.querySelector("#group-result");
    const invitationResult = document.querySelector("#invitation-result");
    const inviteUrlInput = document.querySelector("#invite-url");
    const inviteLink = document.querySelector("#invite-link");
    const membersForm = document.querySelector("#members-form");
    const memberGroupIdInput = document.querySelector("#member-group-id");
    const loadMembersButton = document.querySelector("#load-members-button");
    const memberListResult = document.querySelector("#member-list-result");
    const rotationLink = document.querySelector("#rotation-link");
    const joinInvitationForm = document.querySelector("#join-invitation-form");
    const joinInvitationInput = document.querySelector("#join-invitation-input");
    const acceptInvitationApiButton = document.querySelector("#accept-invitation-api-button");
    const joinInvitationResult = document.querySelector("#join-invitation-result");
    const loadMyGroupButton = document.querySelector("#load-my-group-button");
    const myGroupResult = document.querySelector("#my-group-result");

    let createdGroupId = null;
    const invitationCodePattern = /^[A-Za-z0-9_-]{22}$/;

    joinInvitationForm.addEventListener("submit", (event) => {
        event.preventDefault();
        joinInvitationResult.textContent = "";

        try {
            const code = parseInvitationCode(joinInvitationInput.value);
            window.location.assign(`/invite/${encodeURIComponent(code)}`);
        } catch (error) {
            joinInvitationResult.textContent = errorMessage(error);
        }
    });

    acceptInvitationApiButton.addEventListener("click", async () => {
        let code;
        try {
            code = parseInvitationCode(joinInvitationInput.value);
        } catch (error) {
            joinInvitationResult.textContent = errorMessage(error);
            return;
        }

        acceptInvitationApiButton.disabled = true;
        joinInvitationResult.textContent = "초대 수락 API 호출 중...";

        try {
            const acceptance = await postJson(
                `/api/invitations/${encodeURIComponent(code)}/accept`
            );
            joinInvitationResult.textContent = [
                "초대 수락 성공",
                `groupPublicId: ${acceptance.groupPublicId}`,
                `groupName: ${acceptance.groupName}`,
                `membershipPublicId: ${acceptance.membershipPublicId}`,
                `role: ${acceptance.role}`,
                `joinedNow: ${acceptance.joinedNow}`
            ].join("\n");
            rotationLink.href = `/rotation.html?groupId=${encodeURIComponent(acceptance.groupPublicId)}`;
            rotationLink.hidden = false;
            void loadMyGroup();
        } catch (error) {
            joinInvitationResult.textContent = [
                "초대 수락 실패",
                errorMessage(error)
            ].join("\n");
        } finally {
            acceptInvitationApiButton.disabled = false;
        }
    });

    loadMyGroupButton.addEventListener("click", () => {
        void loadMyGroup();
    });

    groupForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        const groupName = groupNameInput.value.trim();
        const groupAddress = groupAddressInput.value.trim();
        if (!groupName) {
            groupResult.textContent = "그룹 이름을 입력해 주세요.";
            return;
        }

        createGroupButton.disabled = true;
        issueInvitationButton.disabled = true;
        groupResult.textContent = "그룹 생성 중...";
        clearInvitationResult();

        try {
            const request = {
                name: groupName
            };
            if (groupAddress) {
                request.address = groupAddress;
            }
            const group = await postJson("/api/groups", request);

            createdGroupId = group.groupId;
            groupResult.textContent = [
                "그룹 생성 완료",
                `그룹 ID: ${group.groupId}`,
                `로테이션 그룹 ID: ${group.groupPublicId}`,
                `그룹 이름: ${group.name}`,
                `그룹 주소: ${group.address ?? "(없음)"}`,
                `내 역할: ${group.role}`,
                `멤버십 ID: ${group.membershipId}`,
                `로테이션 멤버십 ID: ${group.membershipPublicId}`
            ].join("\n");
            rotationLink.href = `/rotation.html?groupId=${encodeURIComponent(group.groupPublicId)}`;
            rotationLink.hidden = false;
            issueInvitationButton.disabled = false;
            memberGroupIdInput.value = String(group.groupId);
            void loadMembers(group.groupId);
            void loadMyGroup();
        } catch (error) {
            groupResult.textContent = [
                "그룹 생성 실패",
                errorMessage(error)
            ].join("\n");
        } finally {
            createGroupButton.disabled = false;
            issueInvitationButton.disabled = createdGroupId === null;
        }
    });

    membersForm.addEventListener("submit", (event) => {
        event.preventDefault();
        const groupId = parseGroupId(memberGroupIdInput.value);
        if (groupId === null) {
            memberListResult.textContent = "올바른 그룹 ID를 입력해 주세요.";
            return;
        }
        void loadMembers(groupId);
    });

    issueInvitationButton.addEventListener("click", async () => {
        if (createdGroupId === null) {
            return;
        }

        issueInvitationButton.disabled = true;
        invitationResult.textContent = "초대 링크 발급 중...";
        inviteUrlInput.value = "";
        inviteLink.hidden = true;

        try {
            const invitation = await postJson(
                `/api/groups/${encodeURIComponent(String(createdGroupId))}/invitations`
            );

            invitationResult.textContent = [
                "초대 링크 발급 완료",
                `초대 코드: ${invitation.code}`,
                `만료 시각: ${invitation.expiresAt}`
            ].join("\n");
            const inviteUrl = new URL(invitation.inviteUrl, window.location.origin);
            if (inviteUrl.protocol !== "http:" && inviteUrl.protocol !== "https:") {
                throw new Error("초대 링크 주소가 올바르지 않습니다.");
            }
            inviteUrlInput.value = inviteUrl.href;
            inviteLink.href = inviteUrl.href;
            inviteLink.hidden = false;
        } catch (error) {
            invitationResult.textContent = errorMessage(error);
        } finally {
            issueInvitationButton.disabled = createdGroupId === null;
        }
    });

    async function postJson(url, body) {
        const csrf = await getCsrfToken();
        const headers = {
            Accept: "application/json",
            [csrf.headerName]: csrf.token
        };
        const options = {
            method: "POST",
            headers
        };

        if (body !== undefined) {
            headers["Content-Type"] = "application/json";
            options.body = JSON.stringify(body);
        }

        return requestJson(url, options);
    }

    async function loadMembers(groupId) {
        loadMembersButton.disabled = true;
        memberListResult.textContent = "멤버 목록 조회 중...";

        try {
            const group = await requestJson(
                `/api/groups/${encodeURIComponent(String(groupId))}/members`
            );
            renderMembers(group);
        } catch (error) {
            memberListResult.textContent = errorMessage(error);
        } finally {
            loadMembersButton.disabled = false;
        }
    }

    async function loadMyGroup() {
        loadMyGroupButton.disabled = true;
        myGroupResult.textContent = "내 그룹 조회 중...";

        try {
            const group = await requestJson("/api/groups/me");
            myGroupResult.textContent = [
                "내 그룹 조회 성공",
                `groupPublicId: ${group.groupPublicId}`,
                `membershipPublicId: ${group.membershipPublicId}`,
                `membershipVersion: ${group.membershipVersion}`,
                `groupName: ${group.groupName}`,
                `groupAddress: ${group.groupAddress ?? "(없음)"}`,
                `role: ${group.role}`
            ].join("\n");
            rotationLink.href = `/rotation.html?groupId=${encodeURIComponent(group.groupPublicId)}`;
            rotationLink.hidden = false;
        } catch (error) {
            myGroupResult.textContent = [
                "내 그룹 조회 실패",
                errorMessage(error)
            ].join("\n");
        } finally {
            loadMyGroupButton.disabled = false;
        }
    }

    function renderMembers(group) {
        const title = document.createElement("p");
        title.textContent = [
            `${group.groupName} (그룹 ID: ${group.groupId})`,
            `내 역할: ${group.myRole}`
        ].join(" | ");

        const list = document.createElement("ul");
        for (const member of group.members) {
            const item = document.createElement("li");
            const email = member.email || "이메일 미제공 사용자";
            item.textContent = [
                member.me ? `${email} (나)` : email,
                member.role,
                `가입: ${member.joinedAt}`
            ].join(" | ");
            list.append(item);
        }

        if (group.members.length === 0) {
            const empty = document.createElement("li");
            empty.textContent = "등록된 멤버가 없습니다.";
            list.append(empty);
        }

        memberListResult.replaceChildren(title, list);
    }

    function parseGroupId(value) {
        if (typeof value !== "string" || !/^[1-9]\d*$/.test(value)) {
            return null;
        }
        const groupId = Number(value);
        return Number.isSafeInteger(groupId) && groupId > 0 ? groupId : null;
    }

    function parseInvitationCode(value) {
        const candidate = typeof value === "string" ? value.trim() : "";
        if (invitationCodePattern.test(candidate)) {
            return candidate;
        }

        let invitationUrl;
        try {
            invitationUrl = new URL(candidate, window.location.origin);
        } catch (error) {
            throw new Error("올바른 초대 링크나 22자 초대 코드를 입력해 주세요.");
        }

        if (invitationUrl.origin !== window.location.origin) {
            throw new Error("다른 서비스의 초대 링크는 사용할 수 없습니다.");
        }
        if (invitationUrl.search || invitationUrl.hash
                || invitationUrl.username || invitationUrl.password) {
            throw new Error("올바른 초대 링크나 22자 초대 코드를 입력해 주세요.");
        }

        const pathMatch = invitationUrl.pathname.match(
            /^\/invite\/([A-Za-z0-9_-]{22})$/
        );
        if (!pathMatch) {
            throw new Error("올바른 초대 링크나 22자 초대 코드를 입력해 주세요.");
        }
        return pathMatch[1];
    }

    async function getCsrfToken() {
        const csrf = await requestJson("/api/auth/csrf");
        if (!csrf.headerName || !csrf.token) {
            throw new Error("보안 토큰을 가져오지 못했습니다. 페이지를 새로고침해 주세요.");
        }
        return csrf;
    }

    async function requestJson(url, options = {}) {
        const response = await fetch(url, {
            credentials: "same-origin",
            cache: "no-store",
            headers: {Accept: "application/json"},
            ...options
        });
        const contentType = response.headers.get("content-type") || "";

        if (response.redirected) {
            throw new Error("로그인 세션이 만료되었습니다. 다시 로그인해 주세요.");
        }

        const isJson = contentType.toLowerCase().includes("json");
        const data = isJson
            ? await response.json()
            : {message: (await response.text()).trim()};

        if (!response.ok) {
            const status = `HTTP ${response.status}${response.statusText ? ` ${response.statusText}` : ""}`;
            const detail = data.detail || data.message || data.error;
            throw new Error(detail ? `${status}\n${detail}` : status);
        }
        if (!isJson) {
            throw new Error("서버가 JSON이 아닌 응답을 반환했습니다.");
        }
        return data;
    }

    function clearInvitationResult() {
        invitationResult.textContent = "";
        inviteUrlInput.value = "";
        inviteLink.href = "#";
        inviteLink.hidden = true;
    }

    function errorMessage(error) {
        return error instanceof Error ? error.message : "요청 처리 중 오류가 발생했습니다.";
    }

    const initialGroupId = parseGroupId(
        new URLSearchParams(window.location.search).get("groupId")
    );
    if (initialGroupId !== null) {
        memberGroupIdInput.value = String(initialGroupId);
        void loadMembers(initialGroupId);
    }
})();
