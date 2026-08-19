package gdg.sharinglog.web;

import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

@Controller
public class HomeController {

    @GetMapping("/")
    @ResponseBody
    public String home(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            return LoginPageRenderer.render(
                    "Sharing Log", "공동생활을 조금 더 가볍게",
                    "로그인하면 쉐어하우스 멤버와 공동 업무를 관리할 수 있습니다."
            );
        }

        String safeName = HtmlUtils.htmlEscape(attribute(user, "name").orElse("사용자"));
        String safeEmail = HtmlUtils.htmlEscape(attribute(user, "email").orElse(""));

        return """
                <!doctype html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Sharing Log</title>
                    <link rel="stylesheet" href="/css/app.css">
                    <script src="/js/group-setup.js" defer></script>
                    <script src="/js/logout.js" defer></script>
                </head>
                <body>
                    <main class="shell">
                        <section class="panel">
                            <p class="eyebrow">Sharing Log</p>
                            <h1>%s님, 로그인되었습니다.</h1>
                            <p class="copy">%s</p>

                            <hr>
                            <h2>초대 링크로 참가</h2>
                            <p>초대 화면을 열거나 API로 바로 수락해 409 응답을 확인할 수 있습니다.</p>
                            <form id="join-invitation-form">
                                <p>
                                    <label for="join-invitation-input">초대 링크 또는 코드</label><br>
                                    <input id="join-invitation-input" name="invitation" type="text"
                                           autocomplete="off" required>
                                    <button id="join-invitation-button" type="submit">초대 화면 열기</button>
                                    <button id="accept-invitation-api-button" type="button">초대 수락 API 호출</button>
                                </p>
                            </form>
                            <pre id="join-invitation-result" role="status" aria-live="polite"></pre>

                            <hr>
                            <h2>내 그룹 조회</h2>
                            <p>그룹이 없을 때의 404와 그룹·멤버십·주소 정보를 확인합니다.</p>
                            <button id="load-my-group-button" type="button">GET /api/groups/me</button>
                            <pre id="my-group-result" role="status" aria-live="polite"></pre>

                            <hr>
                            <h2>그룹 생성 및 초대 링크 확인</h2>
                            <p>선택 주소와 생성 결과를 확인하고, 이미 그룹이 있다면 409 응답을 확인합니다.</p>
                            <form id="group-form">
                                <p>
                                    <label for="group-name">그룹 이름</label><br>
                                    <input id="group-name" name="name" type="text" maxlength="50"
                                           autocomplete="off" required>
                                </p>
                                <p>
                                    <label for="group-address">그룹 주소 (선택)</label><br>
                                    <input id="group-address" name="address" type="text" maxlength="255"
                                           autocomplete="street-address">
                                </p>
                                <p>
                                    <button id="create-group-button" type="submit">그룹 생성</button>
                                </p>
                            </form>
                            <pre id="group-result" role="status" aria-live="polite"></pre>
                            <p>
                                <a id="rotation-link" class="secondary-link" href="/rotation.html" hidden>
                                    업무 로테이션 열기
                                </a>
                            </p>

                            <button id="issue-invitation-button" type="button" disabled>초대 링크 발급</button>
                            <pre id="invitation-result" role="status" aria-live="polite"></pre>
                            <p>
                                <label for="invite-url">발급된 초대 링크</label><br>
                                <input id="invite-url" type="text" readonly>
                            </p>
                            <a id="invite-link" href="#" target="_blank" rel="noopener noreferrer" hidden>초대 링크 열기</a>

                            <hr>
                            <section id="group-members">
                            <h2>그룹 멤버 목록</h2>
                            <form id="members-form">
                                <p>
                                    <label for="member-group-id">그룹 ID</label><br>
                                    <input id="member-group-id" name="groupId" type="number" min="1" required>
                                    <button id="load-members-button" type="submit">멤버 조회</button>
                                </p>
                            </form>
                            <div id="member-list-result" role="status" aria-live="polite"></div>
                            </section>

                            <hr>
                            <button id="logout-button" type="button" aria-describedby="logout-status">
                                로그아웃
                            </button>
                            <p id="logout-status" role="status" aria-live="polite"></p>
                        </section>
                    </main>
                </body>
                </html>
                """.formatted(safeName, safeEmail);
    }

    private Optional<String> attribute(OAuth2User user, String name) {
        Object value = user.getAttribute(name);
        if (value == null || !StringUtils.hasText(value.toString())) {
            return Optional.empty();
        }
        return Optional.of(value.toString());
    }
}
