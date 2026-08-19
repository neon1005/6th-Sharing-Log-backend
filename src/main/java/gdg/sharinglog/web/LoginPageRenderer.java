package gdg.sharinglog.web;

final class LoginPageRenderer {

    private static final String TEMPLATE = """
            <!doctype html>
            <html lang="ko">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <link rel="stylesheet" href="/css/app.css">
            </head>
            <body>
                <main class="shell">
                    <section class="panel">
                        <p class="eyebrow">Sharing Log</p>
                        <h1>%s</h1>
                        <p class="copy">%s</p>
                        <div class="login-buttons">
                            <a class="google-button" href="/oauth2/authorization/google">
                                <span class="google-mark" aria-hidden="true">G</span>
                                <span>구글로 로그인하기</span>
                            </a>
                            <a class="naver-button" href="/oauth2/authorization/naver">
                                <span class="naver-mark" aria-hidden="true">N</span>
                                <span>네이버로 로그인하기</span>
                            </a>
                        </div>
                    </section>
                </main>
            </body>
            </html>
            """;

    private LoginPageRenderer() {
    }

    static String render(String title, String heading, String copy) {
        return TEMPLATE.formatted(title, heading, copy);
    }
}
