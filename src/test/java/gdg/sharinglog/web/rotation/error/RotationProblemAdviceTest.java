package gdg.sharinglog.web.rotation.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

class RotationProblemAdviceTest {

    private final RotationProblemAdvice advice = new RotationProblemAdvice();

    @Test
    void rendersApiExceptionsAsApplicationProblemJson() {
        RotationConflictException exception = new RotationConflictException(
                RotationProblemCode.VERSION_CONFLICT,
                "회차를 다시 조회해 주세요.",
                Map.of(
                        "resourceId", "33333333-3333-4333-8333-333333333333",
                        "currentVersion", 8L
                )
        );
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/groups/group-id/occurrences/occurrence-id/complete"
        );
        request.setAttribute("traceId", "trace-123");

        ResponseEntity<ProblemDetail> response =
                advice.handleRotationApiException(exception, request);

        assertEquals(409, response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());

        ProblemDetail problem = response.getBody();
        assertNotNull(problem);
        assertEquals(
                URI.create("https://sharing-log.example/problems/version-conflict"),
                problem.getType()
        );
        assertEquals("리소스 버전이 변경되었습니다.", problem.getTitle());
        assertEquals("회차를 다시 조회해 주세요.", problem.getDetail());
        assertEquals(
                URI.create("/api/groups/group-id/occurrences/occurrence-id/complete"),
                problem.getInstance()
        );
        assertEquals("VERSION_CONFLICT", problem.getProperties().get("code"));
        assertEquals(8L, problem.getProperties().get("currentVersion"));
        assertEquals("trace-123", problem.getProperties().get("traceId"));
    }

    @Test
    void rendersDtoFieldErrorsUsingTheSameProblemShape() throws Exception {
        InvalidRequest target = new InvalidRequest();
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(target, "request");
        bindingResult.rejectValue("name", "NotBlank", "이름은 필수입니다.");
        Method method = DummyEndpoint.class.getDeclaredMethod("create", InvalidRequest.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new MethodParameter(method, 0),
                bindingResult
        );
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/groups/group-id/chores"
        );

        ResponseEntity<ProblemDetail> response =
                advice.handleBindingException(exception, request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());

        ProblemDetail problem = response.getBody();
        assertNotNull(problem);
        assertEquals("VALIDATION_FAILED", problem.getProperties().get("code"));
        Object rawErrors = problem.getProperties().get("errors");
        List<?> errors = assertInstanceOf(List.class, rawErrors);
        assertEquals(
                List.of(new RotationFieldError("name", "이름은 필수입니다.")),
                errors
        );
    }

    @Test
    void preventsStatusMismatchedProblemCodesAndReservedPropertyOverrides() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RotationConflictException(
                        RotationProblemCode.RESOURCE_NOT_FOUND,
                        "잘못된 상태 조합"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RotationNotFoundException(
                        "찾을 수 없습니다.",
                        Map.of("status", 200)
                )
        );
    }

    private static final class InvalidRequest {

        @SuppressWarnings("unused")
        public String getName() {
            return null;
        }
    }

    private static final class DummyEndpoint {

        @SuppressWarnings("unused")
        void create(InvalidRequest request) {
        }
    }
}
