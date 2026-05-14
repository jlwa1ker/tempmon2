package com.tempmon.controller;

import net.jqwik.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for GlobalExceptionHandler error response sanitization.
 *
 * <p>Feature: json-http-ingestion
 * <p>Property 7: error response never exposes internals
 *
 * <p><b>Validates: Requirements 6.1</b>
 */
@Tag("json-http-ingestion")
@Tag("error-response-never-exposes-internals")
class GlobalExceptionHandlerErrorResponsePropertyTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // Patterns that indicate internal details leaking into the response
    private static final Pattern JAVA_EXCEPTION_CLASS = Pattern.compile("[A-Z][a-zA-Z]+Exception");
    private static final Pattern STACK_TRACE_LINE = Pattern.compile("at com\\.");
    private static final Pattern UNIX_FILE_PATH = Pattern.compile("/[a-zA-Z][a-zA-Z0-9_/]*\\.[a-z]+");
    private static final Pattern WINDOWS_FILE_PATH = Pattern.compile("[A-Z]:\\\\");

    /**
     * Property 7: Error response never exposes internals
     *
     * For any RuntimeException with an arbitrary message (including class names,
     * file paths, stack trace fragments), invoking handleUnexpected SHALL produce
     * a response body that does not contain any Java class name pattern, stack trace
     * line, or file path separator, and conforms to the error response schema.
     */
    @Property(tries = 200)
    void errorResponseNeverExposesInternals(
            @ForAll("arbitraryRuntimeException") RuntimeException exception) {

        // Action: invoke the catch-all handler
        ResponseEntity<Map<String, Object>> response = handler.handleUnexpected(exception);

        // Assert: HTTP 500 status
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // Assert: response body conforms to error response schema
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKey("status");
        assertThat(body).containsKey("error");
        assertThat(body).containsKey("message");
        assertThat(body.get("status")).isEqualTo("error");
        assertThat(body.get("error")).isEqualTo("INTERNAL_ERROR");
        assertThat(body.get("message")).isInstanceOf(String.class);

        // Assert: response body values do not contain internal details
        String responseJson = body.toString();
        String messageValue = (String) body.get("message");

        assertThat(messageValue)
                .as("Message should not contain Java exception class names")
                .doesNotMatch(".*" + JAVA_EXCEPTION_CLASS.pattern() + ".*");

        assertThat(messageValue)
                .as("Message should not contain stack trace lines")
                .doesNotMatch(".*" + STACK_TRACE_LINE.pattern() + ".*");

        assertThat(messageValue)
                .as("Message should not contain Unix file paths")
                .doesNotMatch(".*" + UNIX_FILE_PATH.pattern() + ".*");

        assertThat(messageValue)
                .as("Message should not contain Windows file paths")
                .doesNotMatch(".*" + WINDOWS_FILE_PATH.pattern() + ".*");

        // Also check the full body serialization for leaked internals
        assertThat(responseJson)
                .as("Full response body should not contain Java exception class names")
                .doesNotMatch(".*" + JAVA_EXCEPTION_CLASS.pattern() + ".*");

        assertThat(responseJson)
                .as("Full response body should not contain stack trace lines")
                .doesNotMatch(".*" + STACK_TRACE_LINE.pattern() + ".*");

        assertThat(responseJson)
                .as("Full response body should not contain Unix file paths")
                .doesNotMatch(".*" + UNIX_FILE_PATH.pattern() + ".*");

        assertThat(responseJson)
                .as("Full response body should not contain Windows file paths")
                .doesNotMatch(".*" + WINDOWS_FILE_PATH.pattern() + ".*");
    }

    /**
     * Generator: arbitrary RuntimeException instances with messages that include
     * class names, file paths, stack trace fragments, and other internal details.
     */
    @Provide
    Arbitrary<RuntimeException> arbitraryRuntimeException() {
        return Arbitraries.oneOf(
                // Messages containing Java exception class names
                exceptionClassNameMessage(),
                // Messages containing stack trace fragments
                stackTraceFragmentMessage(),
                // Messages containing file paths
                filePathMessage(),
                // Messages combining multiple internal details
                combinedInternalMessage(),
                // Random arbitrary strings (may or may not contain patterns)
                arbitraryStringMessage()
        ).map(RuntimeException::new);
    }

    private Arbitrary<String> exceptionClassNameMessage() {
        Arbitrary<String> className = Arbitraries.of(
                "NullPointerException",
                "IllegalArgumentException",
                "ArrayIndexOutOfBoundsException",
                "ClassCastException",
                "ConcurrentModificationException",
                "StackOverflowException",
                "OutOfMemoryException",
                "FileNotFoundException",
                "IOException",
                "RuntimeException",
                "CustomServiceException"
        );
        Arbitrary<String> prefix = Arbitraries.of(
                "Caused by: ", "Exception in thread \"main\" ",
                "java.lang.", "com.tempmon.service.", ""
        );
        Arbitrary<String> suffix = Arbitraries.of(
                ": null", ": Connection refused",
                " at line 42", ": invalid state", ""
        );
        return Combinators.combine(prefix, className, suffix)
                .as((p, c, s) -> p + c + s);
    }

    private Arbitrary<String> stackTraceFragmentMessage() {
        return Arbitraries.of(
                "at com.tempmon.service.StorageService.persist(StorageService.java:45)",
                "at com.tempmon.controller.IngestController.ingest(IngestController.java:78)",
                "at com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient.putItem(AmazonDynamoDBClient.java:123)",
                "at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:97)",
                "at com.tempmon.HygrometerApplication.main(HygrometerApplication.java:10)\n\tat org.springframework.boot.SpringApplication.run(SpringApplication.java:1300)",
                "java.lang.NullPointerException\n\tat com.tempmon.service.PayloadParser.parse(PayloadParser.java:32)"
        );
    }

    private Arbitrary<String> filePathMessage() {
        return Arbitraries.of(
                "/home/user/app/src/main/java/com/tempmon/service/StorageService.java",
                "/var/log/tempmon/application.log",
                "C:\\Users\\deploy\\tempmon\\target\\classes\\com\\tempmon\\Application.class",
                "/opt/tempmon/config/application.properties",
                "src/main/java/com/tempmon/controller/IngestController.java:78",
                "C:\\Program Files\\Java\\jdk-21\\lib\\modules"
        );
    }

    private Arbitrary<String> combinedInternalMessage() {
        return Combinators.combine(
                exceptionClassNameMessage(),
                stackTraceFragmentMessage()
        ).as((exc, trace) -> exc + "\n" + trace);
    }

    private Arbitrary<String> arbitraryStringMessage() {
        return Arbitraries.strings()
                .withCharRange('!', '~')  // printable ASCII
                .ofMinLength(0)
                .ofMaxLength(500);
    }
}
