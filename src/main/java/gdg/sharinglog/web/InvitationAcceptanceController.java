package gdg.sharinglog.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import gdg.sharinglog.service.invitation.InvitationAcceptanceService;
import gdg.sharinglog.service.invitation.exception.InvitationNotFoundException;
import gdg.sharinglog.service.invitation.exception.InvitationUnavailableException;
import gdg.sharinglog.service.invitation.result.AcceptedInvitation;
import gdg.sharinglog.service.invitation.result.InvitationPreview;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequiredArgsConstructor
public class InvitationAcceptanceController {

    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final InvitationAcceptanceService acceptanceService;

    @GetMapping("/invite/{code}")
    @ResponseBody
    public ResponseEntity<String> invitationPage(
            @PathVariable String code,
            @RequestParam(name = "result", required = false) String result,
            OAuth2AuthenticationToken authentication,
            HttpServletRequest request) {
        InvitationPreview preview = acceptanceService.preview(
                code,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            throw new IllegalStateException("CSRF 토큰을 가져올 수 없습니다.");
        }

        return html(HttpStatus.OK, invitationHtml(preview, code, result, csrfToken));
    }

    @PostMapping("/invite/{code}/accept")
    public ResponseEntity<Void> acceptInvitation(
            @PathVariable String code,
            OAuth2AuthenticationToken authentication) {
        AcceptedInvitation acceptance = acceptanceService.accept(
                code,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );
        String result = acceptance.joinedNow() ? "joined" : "already-member";
        URI location = UriComponentsBuilder
                .fromPath("/invite/{code}")
                .queryParam("result", result)
                .buildAndExpand(code)
                .encode()
                .toUri();

        return ResponseEntity
                .status(HttpStatus.SEE_OTHER)
                .location(location)
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @ExceptionHandler(InvitationNotFoundException.class)
    @ResponseBody
    public ResponseEntity<String> invitationNotFound(InvitationNotFoundException exception) {
        return html(HttpStatus.NOT_FOUND, errorHtml("초대 링크를 찾을 수 없습니다.", exception.getMessage()));
    }

    @ExceptionHandler(InvitationUnavailableException.class)
    @ResponseBody
    public ResponseEntity<String> invitationUnavailable(InvitationUnavailableException exception) {
        return html(HttpStatus.GONE, errorHtml("사용할 수 없는 초대 링크입니다.", exception.getMessage()));
    }

    private String invitationHtml(InvitationPreview preview, String code, String result,
                                  CsrfToken csrfToken) {
        String safeGroupName = HtmlUtils.htmlEscape(preview.groupName());
        String membershipContent;

        if (preview.alreadyMember()) {
            String message = "joined".equals(result)
                    ? "그룹 가입이 완료되었습니다."
                    : "이미 가입한 그룹입니다.";
            membershipContent = """
                    <p id="membership-status">%s</p>
                    <p>현재 역할: %s</p>
                    <p><a id="member-list-link" class="secondary-link" href="/?groupId=%s#group-members">멤버 목록 보기</a></p>
                    <p><a class="secondary-link" href="/rotation.html?groupId=%s">업무 로테이션 열기</a></p>
                    """.formatted(
                    HtmlUtils.htmlEscape(message),
                    HtmlUtils.htmlEscape(preview.currentRole().name()),
                    preview.groupId(),
                    HtmlUtils.htmlEscape(preview.groupPublicId())
            );
        } else {
            membershipContent = """
                    <p>이 그룹에 MEMBER로 가입합니다.</p>
                    <form method="post" action="/invite/%s/accept">
                        <input type="hidden" name="%s" value="%s">
                        <button id="accept-invitation-button" type="submit">그룹 가입하기</button>
                    </form>
                    """.formatted(
                    HtmlUtils.htmlEscape(code),
                    HtmlUtils.htmlEscape(csrfToken.getParameterName()),
                    HtmlUtils.htmlEscape(csrfToken.getToken())
            );
        }

        return """
                <!doctype html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <meta name="robots" content="noindex,nofollow">
                    <title>Sharing Log 그룹 초대</title>
                    <link rel="stylesheet" href="/css/app.css">
                </head>
                <body>
                    <main class="shell">
                        <section class="panel">
                            <p class="eyebrow">Sharing Log 초대</p>
                            <h1>%s</h1>
                            <p>초대 만료 시각: %s</p>
                            %s
                            <p><a class="secondary-link" href="/">홈으로 이동</a></p>
                        </section>
                    </main>
                </body>
                </html>
                """.formatted(
                safeGroupName,
                HtmlUtils.htmlEscape(preview.expiresAt().toString()),
                membershipContent
        );
    }

    private String errorHtml(String title, String message) {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <meta name="robots" content="noindex,nofollow">
                    <title>Sharing Log 그룹 초대</title>
                    <link rel="stylesheet" href="/css/app.css">
                </head>
                <body>
                    <main class="shell">
                        <section class="panel">
                            <p class="eyebrow">Sharing Log 초대</p>
                            <h1>%s</h1>
                            <p>%s</p>
                            <a class="secondary-link" href="/">홈으로 이동</a>
                        </section>
                    </main>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(title), HtmlUtils.htmlEscape(message));
    }

    private ResponseEntity<String> html(HttpStatus status, String body) {
        return ResponseEntity
                .status(status)
                .contentType(HTML_UTF8)
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(body);
    }
}
