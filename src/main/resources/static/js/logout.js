(() => {
    "use strict";

    const logoutButton = document.querySelector("#logout-button");
    const logoutStatus = document.querySelector("#logout-status");

    if (!logoutButton) {
        return;
    }

    logoutButton.addEventListener("click", () => {
        void logout();
    });

    async function logout() {
        logoutButton.disabled = true;
        showStatus("로그아웃 중...");

        try {
            const response = await fetch("/api/auth/logout", {
                method: "POST",
                credentials: "include",
                cache: "no-store"
            });

            if (!response.ok) {
                throw new Error(`로그아웃 요청 실패 (${response.status})`);
            }

            window.location.assign("/login");
        } catch (error) {
            showStatus(error instanceof Error
                ? error.message
                : "로그아웃 중 오류가 발생했습니다.");
            logoutButton.disabled = false;
        }
    }

    function showStatus(message) {
        if (logoutStatus) {
            logoutStatus.textContent = message;
        }
    }
})();
