package gdg.sharinglog.web.rotation.error;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import gdg.sharinglog.service.rotation.access.RotationAccessDeniedException;
import gdg.sharinglog.service.rotation.access.RotationGroupNotFoundException;
import gdg.sharinglog.service.rotation.access.RotationMemberNotFoundException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "gdg.sharinglog.web.rotation")
public class RotationProblemAdvice {

    private static final String GENERIC_INVALID_VALUE = "올바르지 않은 값입니다.";

    @ExceptionHandler(RotationAccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            RotationAccessDeniedException exception,
            HttpServletRequest request) {
        return handleRotationApiException(
                new RotationForbiddenException(exception.getMessage()),
                request
        );
    }

    @ExceptionHandler({
            RotationGroupNotFoundException.class,
            RotationMemberNotFoundException.class
    })
    public ResponseEntity<ProblemDetail> handleAccessResourceNotFound(
            RuntimeException exception,
            HttpServletRequest request) {
        return handleRotationApiException(
                new RotationNotFoundException(exception.getMessage()),
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        return handleRotationApiException(
                new RotationBadRequestException(
                        RotationProblemCode.VALIDATION_FAILED,
                        exception.getMessage()
                ),
                request
        );
    }

    @ExceptionHandler(RotationApiException.class)
    public ResponseEntity<ProblemDetail> handleRotationApiException(
            RotationApiException exception,
            HttpServletRequest request) {
        RotationProblemCode problemCode = exception.problem();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                problemCode.status(),
                exception.getMessage()
        );
        problem.setType(problemCode.type());
        problem.setTitle(problemCode.title());
        URI instance = instanceOf(request);
        if (instance != null) {
            problem.setInstance(instance);
        }
        problem.setProperty("code", problemCode.name());
        exception.properties().forEach(problem::setProperty);

        String traceId = traceIdOf(request);
        if (StringUtils.hasText(traceId)) {
            problem.setProperty("traceId", traceId);
        }

        return ResponseEntity
                .status(problemCode.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ProblemDetail> handleBindingException(
            BindException exception,
            HttpServletRequest request) {
        return validationProblem(
                RotationProblemCode.VALIDATION_FAILED,
                "요청 필드를 확인해 주세요.",
                errorsFrom(exception.getAllErrors()),
                request
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidationException(
            HandlerMethodValidationException exception,
            HttpServletRequest request) {
        List<RotationFieldError> errors = new ArrayList<>();
        exception.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors parameterErrors) {
                errors.addAll(errorsFrom(parameterErrors.getAllErrors()));
                return;
            }

            String parameterName = result.getMethodParameter().getParameterName();
            if (!StringUtils.hasText(parameterName)) {
                parameterName = "arg" + result.getMethodParameter().getParameterIndex();
            }
            for (MessageSourceResolvable resolvable : result.getResolvableErrors()) {
                errors.add(new RotationFieldError(
                        parameterName,
                        reasonOf(resolvable)
                ));
            }
        });
        exception.getCrossParameterValidationResults().forEach(error ->
                errors.add(new RotationFieldError("request", reasonOf(error)))
        );

        return validationProblem(
                RotationProblemCode.VALIDATION_FAILED,
                "요청 필드를 확인해 주세요.",
                sortedDistinct(errors),
                request
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolationException(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        List<RotationFieldError> errors = exception.getConstraintViolations().stream()
                .map(violation -> {
                    String path = violation.getPropertyPath().toString();
                    return new RotationFieldError(
                            StringUtils.hasText(path) ? path : "request",
                            fallbackReason(violation.getMessage())
                    );
                })
                .sorted(fieldErrorComparator())
                .distinct()
                .toList();
        return validationProblem(
                RotationProblemCode.VALIDATION_FAILED,
                "요청 필드를 확인해 주세요.",
                errors,
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatchException(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return validationProblem(
                RotationProblemCode.INVALID_QUERY,
                "쿼리 파라미터의 형식을 확인해 주세요.",
                List.of(new RotationFieldError(
                        exception.getName(),
                        "지원하지 않는 형식의 값입니다."
                )),
                request
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingRequestParameterException(
            MissingServletRequestParameterException exception,
            HttpServletRequest request) {
        return validationProblem(
                RotationProblemCode.INVALID_QUERY,
                "필수 쿼리 파라미터를 보내 주세요.",
                List.of(new RotationFieldError(
                        exception.getParameterName(),
                        "필수 값입니다."
                )),
                request
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingRequestHeaderException(
            MissingRequestHeaderException exception,
            HttpServletRequest request) {
        if ("If-Match".equalsIgnoreCase(exception.getHeaderName())) {
            return handleRotationApiException(
                    new RotationPreconditionRequiredException(
                            "기존 리소스를 변경하려면 If-Match 헤더가 필요합니다."
                    ),
                    request
            );
        }
        return validationProblem(
                RotationProblemCode.VALIDATION_FAILED,
                "필수 요청 헤더를 보내 주세요.",
                List.of(new RotationFieldError(
                        exception.getHeaderName(),
                        "필수 값입니다."
                )),
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableMessageException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return validationProblem(
                RotationProblemCode.VALIDATION_FAILED,
                "요청 본문의 JSON 형식과 필드 타입을 확인해 주세요.",
                List.of(new RotationFieldError(
                        "body",
                        "읽을 수 없는 요청 본문입니다."
                )),
                request
        );
    }

    private ResponseEntity<ProblemDetail> validationProblem(
            RotationProblemCode code,
            String detail,
            List<RotationFieldError> errors,
            HttpServletRequest request) {
        RotationBadRequestException exception = new RotationBadRequestException(
                code,
                detail,
                Map.of("errors", errors)
        );
        return handleRotationApiException(exception, request);
    }

    private static List<RotationFieldError> errorsFrom(List<ObjectError> objectErrors) {
        return objectErrors.stream()
                .map(RotationProblemAdvice::toFieldError)
                .sorted(fieldErrorComparator())
                .distinct()
                .toList();
    }

    private static RotationFieldError toFieldError(ObjectError error) {
        String field = error instanceof FieldError fieldError
                ? fieldError.getField()
                : error.getObjectName();
        return new RotationFieldError(field, reasonOf(error));
    }

    private static String reasonOf(MessageSourceResolvable error) {
        return fallbackReason(error.getDefaultMessage());
    }

    private static String fallbackReason(String reason) {
        return StringUtils.hasText(reason) ? reason : GENERIC_INVALID_VALUE;
    }

    private static List<RotationFieldError> sortedDistinct(List<RotationFieldError> errors) {
        return errors.stream()
                .sorted(fieldErrorComparator())
                .distinct()
                .toList();
    }

    private static Comparator<RotationFieldError> fieldErrorComparator() {
        return Comparator.comparing(RotationFieldError::field)
                .thenComparing(RotationFieldError::reason);
    }

    private static URI instanceOf(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return StringUtils.hasText(requestUri) ? URI.create(requestUri) : null;
    }

    private static String traceIdOf(HttpServletRequest request) {
        Object traceId = request.getAttribute("traceId");
        if (traceId != null && StringUtils.hasText(traceId.toString())) {
            return traceId.toString();
        }
        return request.getRequestId();
    }
}
