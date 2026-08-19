package gdg.sharinglog.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LoginController {

    @GetMapping("/login")
    @ResponseBody
    public String login() {
        return LoginPageRenderer.render(
                "Sharing Log 로그인", "쉐어링 로그 시작하기",
                "공동생활 그룹을 관리하려면 Google 또는 네이버 계정으로 로그인하세요."
        );
    }
}
