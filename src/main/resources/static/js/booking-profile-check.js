(() => {
    "use strict";

    const groupIdInput = document.querySelector("#group-id");
    const spaceSelect = document.querySelector("#space-id");
    const dateInput = document.querySelector("#reservation-date");
    const profileResult = document.querySelector("#profile-result");
    const spaceResult = document.querySelector("#space-result");
    const reservationResult = document.querySelector("#reservation-result");
    const reservationActionResult = document.querySelector("#reservation-action-result");
    const embeddedDialog = document.querySelector("#booking-profile-dialog");
    const openEmbeddedDialogButton = document.querySelector("#open-booking-profile");
    const closeEmbeddedDialogButton = document.querySelector("#close-booking-profile");
    let csrfPromise;

    if (embeddedDialog && openEmbeddedDialogButton) {
        openEmbeddedDialogButton.addEventListener("click", () => embeddedDialog.showModal());
    }
    if (embeddedDialog && closeEmbeddedDialogButton) {
        closeEmbeddedDialogButton.addEventListener("click", () => embeddedDialog.close());
    }

    document.querySelector("#load-profile-button").addEventListener("click", () => {
        void loadProfile();
    });

    document.querySelector("#nickname-form").addEventListener("submit", (event) => {
        event.preventDefault();
        void updateNickname();
    });

    document.querySelector("#space-list-form").addEventListener("submit", (event) => {
        event.preventDefault();
        void loadSpaces();
    });

    document.querySelector("#space-create-form").addEventListener("submit", (event) => {
        event.preventDefault();
        void createSpace();
    });

    document.querySelector("#reservation-list-form").addEventListener("submit", (event) => {
        event.preventDefault();
        void loadReservations();
    });

    document.querySelector("#reservation-create-form").addEventListener("submit", (event) => {
        event.preventDefault();
        void createReservation();
    });

    async function loadProfile() {
        profileResult.textContent = "프로필 조회 중...";
        try {
            const profile = await requestJson("/api/auth/me");
            renderProfile(profile);
            showJson(profileResult, "프로필 조회 성공", profile);
        } catch (error) {
            showError(profileResult, error);
        }
    }

    async function updateNickname() {
        const nicknameInput = document.querySelector("#nickname");
        const nickname = nicknameInput.value.trim();
        if (!nickname || nickname.length > 20) {
            profileResult.textContent = "닉네임은 1자 이상 20자 이하로 입력해 주세요.";
            return;
        }

        profileResult.textContent = "닉네임 변경 중...";
        try {
            const profile = await mutateJson("PATCH", "/api/auth/me", {nickname});
            renderProfile(profile);
            showJson(profileResult, "닉네임 변경 성공", profile);
        } catch (error) {
            showError(profileResult, error);
        }
    }

    function renderProfile(profile) {
        document.querySelector("#profile-email").textContent = profile.email || "-";
        document.querySelector("#profile-nickname").textContent = profile.nickname || "-";
        document.querySelector("#nickname").value = profile.nickname || "";
    }

    async function loadSpaces(preferredSpaceId = "") {
        let groupId;
        try {
            groupId = requireGroupId();
        } catch (error) {
            showError(spaceResult, error);
            return null;
        }

        rememberGroupId(groupId);
        spaceResult.textContent = "공간 조회 중...";
        try {
            const data = await requestJson(`/api/groups/${encodeURIComponent(groupId)}/spaces`);
            renderSpaces(data.items || [], preferredSpaceId);
            showJson(spaceResult, `공간 ${data.items.length}개 조회 성공`, data);
            return data;
        } catch (error) {
            renderSpaces([]);
            showError(spaceResult, error);
            return null;
        }
    }

    async function createSpace() {
        const nameInput = document.querySelector("#space-name");
        const name = nameInput.value.trim();
        if (!name) {
            spaceResult.textContent = "공간 이름을 입력해 주세요.";
            return;
        }

        try {
            const groupId = requireGroupId();
            rememberGroupId(groupId);
            spaceResult.textContent = "공간 추가 중...";
            const created = await mutateJson(
                "POST",
                `/api/groups/${encodeURIComponent(groupId)}/spaces`,
                {name}
            );
            nameInput.value = "";
            await loadSpaces(created.spaceId);
            showJson(spaceResult, "공간 추가 성공", created);
        } catch (error) {
            showError(spaceResult, error);
        }
    }

    function renderSpaces(spaces, preferredSpaceId = "") {
        const previous = preferredSpaceId || spaceSelect.value;
        const options = [];
        const list = document.querySelector("#space-list");
        const items = [];

        for (const space of spaces) {
            const option = document.createElement("option");
            option.value = space.spaceId;
            option.textContent = `${space.name} (${space.spaceId})`;
            options.push(option);

            const item = document.createElement("li");
            item.textContent = `${space.name} | ${space.spaceId}`;
            items.push(item);
        }

        if (options.length === 0) {
            const option = document.createElement("option");
            option.value = "";
            option.textContent = "등록된 공간이 없습니다";
            options.push(option);

            const item = document.createElement("li");
            item.textContent = "등록된 공간이 없습니다.";
            items.push(item);
        }

        spaceSelect.replaceChildren(...options);
        spaceSelect.disabled = spaces.length === 0;
        if (spaces.some((space) => space.spaceId === previous)) {
            spaceSelect.value = previous;
        }
        list.replaceChildren(...items);
    }

    async function loadReservations() {
        reservationActionResult.textContent = "";
        try {
            const groupId = requireGroupId();
            const spaceId = requireSpaceId();
            const date = requireDate();
            rememberGroupId(groupId);
            reservationResult.textContent = "예약 현황 조회 중...";
            const data = await requestJson(
                `/api/groups/${encodeURIComponent(groupId)}`
                + `/spaces/${encodeURIComponent(spaceId)}/reservations`
                + `?date=${encodeURIComponent(date)}`
            );
            renderReservations(data);
            showJson(reservationResult, `예약 ${data.items.length}개 조회 성공`, data);
            return data;
        } catch (error) {
            showError(reservationResult, error);
            return null;
        }
    }

    async function createReservation() {
        try {
            const groupId = requireGroupId();
            const spaceId = requireSpaceId();
            const date = requireDate();
            const startTime = document.querySelector("#start-time").value;
            const endTime = document.querySelector("#end-time").value;
            if (!startTime || !endTime || startTime >= endTime) {
                throw new Error("종료 시간은 시작 시간보다 늦어야 합니다.");
            }

            reservationActionResult.textContent = "예약 생성 중...";
            const created = await mutateJson(
                "POST",
                `/api/groups/${encodeURIComponent(groupId)}`
                + `/spaces/${encodeURIComponent(spaceId)}/reservations`,
                {date, startTime, endTime}
            );
            await loadReservations();
            showJson(reservationActionResult, "예약 생성 성공", created);
        } catch (error) {
            showError(reservationActionResult, error);
        }
    }

    function renderReservations(data) {
        const rows = [];
        for (const reservation of data.items || []) {
            const row = document.createElement("tr");
            appendCell(row, reservation.reservationId);
            appendCell(row, `${reservation.startTime} ~ ${reservation.endTime}`);
            appendCell(
                row,
                `${reservation.member?.nickname || "닉네임 없음"}${reservation.member?.me ? " (나)" : ""}`
            );
            appendCell(row, reservation.status);
            appendCell(row, String(reservation.version));

            const actionCell = document.createElement("td");
            if (reservation.member?.me && reservation.status === "ACTIVE") {
                const button = document.createElement("button");
                button.type = "button";
                button.textContent = "취소";
                button.addEventListener("click", () => {
                    void cancelReservation(data.groupId, reservation, button);
                });
                actionCell.append(button);
            } else {
                actionCell.textContent = "-";
            }
            row.append(actionCell);
            rows.push(row);
        }

        if (rows.length === 0) {
            const row = document.createElement("tr");
            const cell = document.createElement("td");
            cell.colSpan = 6;
            cell.textContent = "해당 날짜의 활성 예약이 없습니다.";
            row.append(cell);
            rows.push(row);
        }
        document.querySelector("#reservation-list").replaceChildren(...rows);
    }

    async function cancelReservation(groupId, reservation, button) {
        button.disabled = true;
        reservationActionResult.textContent = "예약 취소 중...";
        try {
            const cancelled = await mutateJson(
                "POST",
                `/api/groups/${encodeURIComponent(groupId)}`
                + `/reservations/${encodeURIComponent(reservation.reservationId)}/cancel`,
                undefined,
                {"If-Match": String(reservation.version)}
            );
            await loadReservations();
            showJson(reservationActionResult, "예약 취소 성공", cancelled);
        } catch (error) {
            showError(reservationActionResult, error);
            button.disabled = false;
        }
    }

    function appendCell(row, value) {
        const cell = document.createElement("td");
        cell.textContent = value ?? "-";
        row.append(cell);
    }

    function requireGroupId() {
        const groupId = groupIdInput.value.trim();
        if (!groupId) {
            throw new Error("공개 그룹 ID를 입력해 주세요.");
        }
        return groupId;
    }

    function requireSpaceId() {
        if (!spaceSelect.value) {
            throw new Error("먼저 공간을 조회하거나 추가해 주세요.");
        }
        return spaceSelect.value;
    }

    function requireDate() {
        if (!dateInput.value) {
            throw new Error("예약 날짜를 선택해 주세요.");
        }
        return dateInput.value;
    }

    function rememberGroupId(groupId) {
        const url = new URL(window.location.href);
        url.searchParams.set("groupId", groupId);
        window.history.replaceState(null, "", url);
    }

    async function mutateJson(method, url, body, extraHeaders = {}) {
        const csrf = await getCsrfToken();
        const headers = {
            Accept: "application/json",
            [csrf.headerName]: csrf.token,
            ...extraHeaders
        };
        const options = {method, headers};
        if (body !== undefined) {
            headers["Content-Type"] = "application/json";
            options.body = JSON.stringify(body);
        }
        return requestJson(url, options);
    }

    async function getCsrfToken() {
        if (!csrfPromise) {
            csrfPromise = requestJson("/api/auth/csrf").then((csrf) => {
                if (!csrf?.headerName || !csrf?.token) {
                    throw new Error("CSRF 보안 토큰을 가져오지 못했습니다.");
                }
                return csrf;
            });
        }
        return csrfPromise;
    }

    async function requestJson(url, options = {}) {
        const response = await fetch(url, {
            credentials: "same-origin",
            cache: "no-store",
            ...options
        });
        const contentType = (response.headers.get("content-type") || "").toLowerCase();
        if (response.redirected || !contentType.includes("json")) {
            throw new Error("로그인 세션이 만료되었거나 JSON이 아닌 응답을 받았습니다.");
        }

        const data = await response.json();
        if (!response.ok) {
            throw new Error(
                data.detail || data.message || data.error || `요청 실패 (${response.status})`
            );
        }
        return data;
    }

    function showJson(element, title, value) {
        element.textContent = `${title}\n${JSON.stringify(value, null, 2)}`;
    }

    function showError(element, error) {
        element.textContent = error instanceof Error
            ? `오류: ${error.message}`
            : "오류: 요청 처리 중 문제가 발생했습니다.";
    }

    function localDateValue(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");
        return `${year}-${month}-${day}`;
    }

    dateInput.value = localDateValue(new Date());
    const initialGroupId = new URLSearchParams(window.location.search).get("groupId");
    if (initialGroupId) {
        groupIdInput.value = initialGroupId;
        void loadSpaces();
    }
    void loadProfile();
})();
