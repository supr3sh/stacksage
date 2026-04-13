package com.stacksage.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegexLogParserTest {

    private RegexLogParser parser;

    @BeforeEach
    void setUp() {
        parser = new RegexLogParser();
    }

    @Test
    void parse_singleExceptionWithMessage() {
        String log = """
                java.lang.NullPointerException: Cannot invoke method on null reference
                    at com.example.UserService.getUser(UserService.java:42)
                    at com.example.UserController.handleRequest(UserController.java:18)
                    at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:97)
                """;

        List<ExceptionDetail> results = parser.parse(log);

        assertThat(results).hasSize(1);
        ExceptionDetail detail = results.get(0);
        assertThat(detail.getExceptionType()).isEqualTo("java.lang.NullPointerException");
        assertThat(detail.getMessage()).isEqualTo("Cannot invoke method on null reference");
        assertThat(detail.getStackTrace()).hasSize(3);
        assertThat(detail.getStackTrace().get(0)).contains("UserService.getUser");
    }

    @Test
    void parse_exceptionWithoutMessage() {
        String log = """
                java.lang.NullPointerException
                    at com.example.Main.run(Main.java:10)
                """;

        List<ExceptionDetail> results = parser.parse(log);

        assertThat(results).hasSize(1);
        ExceptionDetail detail = results.get(0);
        assertThat(detail.getExceptionType()).isEqualTo("java.lang.NullPointerException");
        assertThat(detail.getMessage()).isNull();
        assertThat(detail.getStackTrace()).hasSize(1);
    }

    @Test
    void parse_multipleExceptionsInOneLog() {
        String log = """
                2024-01-15 10:00:01 ERROR Application started
                java.lang.IllegalArgumentException: Invalid input
                    at com.example.Validator.validate(Validator.java:25)
                    at com.example.Service.process(Service.java:50)
                2024-01-15 10:05:30 ERROR Another failure
                java.io.FileNotFoundException: config.yml not found
                    at com.example.ConfigLoader.load(ConfigLoader.java:12)
                """;

        List<ExceptionDetail> results = parser.parse(log);

        assertThat(results).hasSize(2);

        assertThat(results.get(0).getExceptionType()).isEqualTo("java.lang.IllegalArgumentException");
        assertThat(results.get(0).getMessage()).isEqualTo("Invalid input");
        assertThat(results.get(0).getStackTrace()).hasSize(2);

        assertThat(results.get(1).getExceptionType()).isEqualTo("java.io.FileNotFoundException");
        assertThat(results.get(1).getMessage()).isEqualTo("config.yml not found");
        assertThat(results.get(1).getStackTrace()).hasSize(1);
    }

    @Test
    void parse_causedByChain() {
        String log = """
                org.springframework.beans.factory.BeanCreationException: Error creating bean
                    at org.springframework.beans.factory.support.AbstractBeanFactory.getBean(AbstractBeanFactory.java:300)
                Caused by: java.lang.IllegalStateException: Service not initialized
                    at com.example.MyService.init(MyService.java:55)
                    at com.example.MyService.<init>(MyService.java:20)
                Caused by: java.lang.NullPointerException: config is null
                    at com.example.MyService.loadConfig(MyService.java:70)
                """;

        List<ExceptionDetail> results = parser.parse(log);

        assertThat(results).hasSize(3);

        assertThat(results.get(0).getExceptionType())
                .isEqualTo("org.springframework.beans.factory.BeanCreationException");
        assertThat(results.get(0).getMessage()).isEqualTo("Error creating bean");
        assertThat(results.get(0).getStackTrace()).hasSize(1);

        assertThat(results.get(1).getExceptionType()).isEqualTo("java.lang.IllegalStateException");
        assertThat(results.get(1).getMessage()).isEqualTo("Service not initialized");
        assertThat(results.get(1).getStackTrace()).hasSize(2);

        assertThat(results.get(2).getExceptionType()).isEqualTo("java.lang.NullPointerException");
        assertThat(results.get(2).getMessage()).isEqualTo("config is null");
        assertThat(results.get(2).getStackTrace()).hasSize(1);
    }

    @Test
    void parse_noExceptions_returnsEmptyList() {
        String log = """
                2024-01-15 10:00:01 INFO Application started successfully
                2024-01-15 10:00:02 INFO Listening on port 8080
                2024-01-15 10:00:05 DEBUG Health check passed
                """;

        List<ExceptionDetail> results = parser.parse(log);

        assertThat(results).isEmpty();
    }

    @Test
    void parse_nullInput_returnsEmptyList() {
        assertThat(parser.parse(null)).isEmpty();
    }

    @Test
    void parse_blankInput_returnsEmptyList() {
        assertThat(parser.parse("   ")).isEmpty();
    }

    @Test
    void parse_interleavedLogLinesWithException() {
        String log = """
                2024-01-15 10:00:01 INFO Processing request #1234
                2024-01-15 10:00:01 ERROR Request failed:
                java.lang.RuntimeException: Database connection timeout
                    at com.example.DbPool.getConnection(DbPool.java:88)
                    at com.example.Repository.query(Repository.java:45)
                    ... 15 more
                2024-01-15 10:00:02 INFO Retrying request #1234
                """;

        List<ExceptionDetail> results = parser.parse(log);

        assertThat(results).hasSize(1);
        ExceptionDetail detail = results.get(0);
        assertThat(detail.getExceptionType()).isEqualTo("java.lang.RuntimeException");
        assertThat(detail.getMessage()).isEqualTo("Database connection timeout");
        assertThat(detail.getStackTrace()).hasSize(3);
        assertThat(detail.getStackTrace().get(2)).isEqualTo("... 15 more");
    }

    @Test
    void parse_errorTypeException() {
        String log = """
                java.lang.OutOfMemoryError: Java heap space
                    at java.base/java.util.Arrays.copyOf(Arrays.java:3512)
                    at java.base/java.lang.AbstractStringBuilder.ensureCapacityInternal(AbstractStringBuilder.java:227)
                """;

        List<ExceptionDetail> results = parser.parse(log);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExceptionType()).isEqualTo("java.lang.OutOfMemoryError");
        assertThat(results.get(0).getMessage()).isEqualTo("Java heap space");
        assertThat(results.get(0).getStackTrace()).hasSize(2);
    }

    @Test
    void parse_stackOverflowWithDeepTrace() {
        String log = """
                java.lang.StackOverflowError
                    at com.example.Recursive.call(Recursive.java:10)
                    at com.example.Recursive.call(Recursive.java:10)
                    at com.example.Recursive.call(Recursive.java:10)
                    ... 1024 more
                """;

        List<ExceptionDetail> results = parser.parse(log);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExceptionType()).isEqualTo("java.lang.StackOverflowError");
        assertThat(results.get(0).getMessage()).isNull();
        assertThat(results.get(0).getStackTrace()).hasSize(4);
    }

    @Test
    void parse_suppressedException() {
        String log = """
                java.io.IOException: Stream closed
                    at com.example.StreamHandler.read(StreamHandler.java:30)
                    Suppressed: java.io.IOException: Broken pipe
                        at com.example.StreamHandler.close(StreamHandler.java:55)
                """;

        List<ExceptionDetail> results = parser.parse(log);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getExceptionType()).isEqualTo("java.io.IOException");
        assertThat(results.get(0).getMessage()).isEqualTo("Stream closed");

        assertThat(results.get(1).getExceptionType()).isEqualTo("java.io.IOException");
        assertThat(results.get(1).getMessage()).isEqualTo("Broken pipe");
    }

    @Test
    void parse_loggerPrefixedExceptionLine() {
        String log = """
                2024-01-15 10:00:01 ERROR c.e.MyController - java.lang.NullPointerException: value is null
                    at com.example.MyController.handle(MyController.java:42)
                    at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:97)
                """;

        List<ExceptionDetail> results = parser.parse(log);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExceptionType()).isEqualTo("java.lang.NullPointerException");
        assertThat(results.get(0).getMessage()).isEqualTo("value is null");
        assertThat(results.get(0).getStackTrace()).hasSize(2);
    }

    @Test
    void parse_multiLineExceptionMessage() {
        String log = """
                javax.persistence.PersistenceException: Unable to build entity manager
                    [PersistenceUnit: default]
                    at org.hibernate.jpa.HibernatePersistenceProvider.create(HibernatePersistenceProvider.java:50)
                """;

        List<ExceptionDetail> results = parser.parse(log);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExceptionType()).isEqualTo("javax.persistence.PersistenceException");
        assertThat(results.get(0).getMessage()).isEqualTo("Unable to build entity manager [PersistenceUnit: default]");
        assertThat(results.get(0).getStackTrace()).hasSize(1);
    }
}
