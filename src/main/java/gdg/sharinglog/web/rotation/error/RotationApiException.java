package gdg.sharinglog.web.rotation.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;

public abstract class RotationApiException extends RuntimeException {

    private static final Set<String> RESERVED_PROPERTIES = Set.of(
            "type", "title", "status", "detail", "instance", "code", "traceId"
    );

    private final RotationProblemCode problem;
    private final Map<String, Object> properties;

    protected RotationApiException(
            HttpStatus expectedStatus,
            RotationProblemCode problem,
            String detail,
            Map<String, ?> properties) {
        super(resolveDetail(problem, detail));
        this.problem = Objects.requireNonNull(problem, "problem must not be null");
        if (problem.status() != Objects.requireNonNull(expectedStatus, "expectedStatus must not be null")) {
            throw new IllegalArgumentException(
                    problem.name() + " is not a " + expectedStatus.value() + " problem"
            );
        }
        this.properties = copyProperties(properties);
    }

    public final RotationProblemCode problem() {
        return problem;
    }

    public final Map<String, Object> properties() {
        return properties;
    }

    private static String resolveDetail(RotationProblemCode problem, String detail) {
        Objects.requireNonNull(problem, "problem must not be null");
        return detail == null || detail.isBlank() ? problem.defaultDetail() : detail;
    }

    private static Map<String, Object> copyProperties(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        source.forEach((name, value) -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Problem property name must not be blank");
            }
            if (RESERVED_PROPERTIES.contains(name)) {
                throw new IllegalArgumentException("Problem property is reserved: " + name);
            }
            copy.put(name, value);
        });
        return Collections.unmodifiableMap(copy);
    }
}
