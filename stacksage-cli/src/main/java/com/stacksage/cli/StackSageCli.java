package com.stacksage.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stacksage.parser.ExceptionDetail;
import com.stacksage.parser.RegexLogParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class StackSageCli {

    private static final String DEFAULT_SERVER = "http://localhost:9090";
    private static final String ENV_SERVER = "STACKSAGE_SERVER";
    private static final int POLL_INTERVAL_MS = 2000;
    private static final int MAX_POLL_ATTEMPTS = 150;
    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024;
    private static final int MAX_CONSECUTIVE_POLL_FAILURES = 3;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final Function<String, String> envLookup;

    public StackSageCli() {
        this(System::getenv);
    }

    StackSageCli(Function<String, String> envLookup) {
        this.envLookup = envLookup;
    }

    public static void main(String[] args) {
        new StackSageCli().run(args);
    }

    void run(String[] args) {
        String serverUrl = null;
        String filePath = null;
        boolean wait = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server" -> {
                    if (i + 1 < args.length) {
                        serverUrl = args[++i];
                    } else {
                        exitWithError("--server requires a URL argument");
                    }
                }
                case "--wait" -> wait = true;
                case "--help", "-h" -> {
                    printUsage();
                    return;
                }
                default -> {
                    if (args[i].startsWith("-")) {
                        exitWithError("Unknown option: " + args[i]);
                    }
                    if (filePath != null) {
                        exitWithError("Multiple log files specified. Only one file is allowed.");
                    }
                    filePath = args[i];
                }
            }
        }

        if (filePath == null) {
            printUsage();
            exitWithError("No log file specified");
            return;
        }

        String resolvedServer = resolveServerUrl(serverUrl);
        Path logFile = Path.of(filePath);

        if (!Files.exists(logFile)) {
            exitWithError("File not found: " + filePath);
            return;
        }

        if (!Files.isRegularFile(logFile)) {
            exitWithError("Not a regular file: " + filePath);
            return;
        }

        try {
            long fileSize = Files.size(logFile);
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                exitWithError("File too large: " + (fileSize / (1024 * 1024)) + " MB (max 100 MB)");
                return;
            }

            String rawLog = Files.readString(logFile, StandardCharsets.UTF_8);
            long fileSizeMb = fileSize / (1024 * 1024);
            String sizeLabel = fileSizeMb > 0 ? "(" + fileSizeMb + " MB)" : "(" + fileSize + " bytes)";
            System.out.println("Parsing " + logFile.getFileName() + " " + sizeLabel + "...");

            RegexLogParser parser = new RegexLogParser();
            List<ExceptionDetail> exceptions = parser.parse(rawLog);

            if (exceptions.isEmpty()) {
                System.out.println("No exceptions found in the log file.");
                return;
            }
            System.out.println("Found " + exceptions.size() + " exception(s)");

            System.out.println("Submitting to StackSage server (" + resolvedServer + ")...");
            String analysisId = submitToServer(resolvedServer, logFile.getFileName().toString(), exceptions);

            if (analysisId == null || analysisId.isBlank()) {
                exitWithError("Server returned empty analysis ID");
                return;
            }

            System.out.println();
            System.out.println("Analysis ID: " + analysisId);
            System.out.println("View results: " + resolvedServer + "/api/v1/analyses/" + analysisId);

            if (wait) {
                System.out.println();
                pollAndPrintResults(resolvedServer, analysisId);
            }
        } catch (IOException e) {
            exitWithError("Failed to read file: " + e.getMessage());
        } catch (Exception e) {
            exitWithError("Error: " + e.getMessage());
        }
    }

    String resolveServerUrl(String flagValue) {
        String url;
        if (flagValue != null && !flagValue.isBlank()) {
            url = stripTrailingSlash(flagValue);
        } else {
            String envValue = envLookup.apply(ENV_SERVER);
            if (envValue != null && !envValue.isBlank()) {
                url = stripTrailingSlash(envValue);
            } else {
                return DEFAULT_SERVER;
            }
        }
        validateUrl(url);
        return url;
    }

    private void validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                exitWithError("Invalid server URL scheme (must be http or https): " + url);
            }
        } catch (IllegalArgumentException e) {
            exitWithError("Invalid server URL: " + url);
        }
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String submitToServer(String serverUrl, String source,
                                  List<ExceptionDetail> exceptions) throws Exception {
        Map<String, Object> body = Map.of(
                "source", source,
                "exceptions", exceptions
        );

        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/api/v1/analyses"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 202 && response.statusCode() != 200) {
            throw new RuntimeException("Server returned HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("analysisId").asText();
    }

    private void pollAndPrintResults(String serverUrl, String analysisId) {
        System.out.print("Waiting for analysis");
        int consecutiveFailures = 0;

        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
                System.out.print(".");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl + "/api/v1/analyses/" + analysisId))
                        .header("Accept", "application/json")
                        .GET()
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    consecutiveFailures++;
                    if (consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
                        System.err.println("\nWarning: " + consecutiveFailures
                                + " consecutive poll failures (HTTP " + response.statusCode() + ")");
                    }
                    continue;
                }

                consecutiveFailures = 0;
                JsonNode root = objectMapper.readTree(response.body());
                String status = root.path("status").asText();

                if ("COMPLETED".equals(status)) {
                    System.out.println(" COMPLETED");
                    System.out.println();
                    printResults(root.path("results"));
                    return;
                } else if ("FAILED".equals(status)) {
                    System.out.println(" FAILED");
                    System.out.println("Error: " + root.path("errorMessage").asText("Unknown error"));
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("\nInterrupted.");
                return;
            } catch (Exception e) {
                consecutiveFailures++;
                if (consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
                    System.err.println("\nWarning: " + consecutiveFailures
                            + " consecutive poll failures: " + e.getMessage());
                }
            }
        }

        System.out.println("\nTimed out waiting for analysis to complete.");
        System.out.println("Check results later: " + serverUrl + "/api/v1/analyses/" + analysisId);
    }

    private void printResults(JsonNode results) {
        if (results == null || !results.isArray() || results.isEmpty()) {
            System.out.println("No results available.");
            return;
        }

        int total = results.size();
        for (int i = 0; i < total; i++) {
            JsonNode result = results.get(i);
            JsonNode exception = result.path("exception");
            JsonNode diagnosis = result.path("diagnosis");

            String type = exception.path("exceptionType").asText("Unknown");
            String severity = diagnosis.path("severity").asText("N/A");

            System.out.println("[" + (i + 1) + "/" + total + "] " + type + " — " + severity);

            if (!diagnosis.isMissingNode()) {
                printField("  Root cause", diagnosis.path("rootCause"));
                printField("  Explanation", diagnosis.path("explanation"));
                printField("  Fix", diagnosis.path("suggestedFix"));
            } else {
                System.out.println("  (AI diagnosis not available)");
            }
            System.out.println();
        }
    }

    private void printField(String label, JsonNode node) {
        if (!node.isMissingNode() && !node.isNull()) {
            System.out.println(label + ": " + node.asText());
        }
    }

    private void printUsage() {
        System.out.println("Usage: stacksage-cli [OPTIONS] <log-file>");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --server <url>  StackSage server URL (default: STACKSAGE_SERVER env or localhost:9090)");
        System.out.println("  --wait          Wait for analysis to complete and print results");
        System.out.println("  --help, -h      Show this help message");
        System.out.println();
        System.out.println("Server URL resolution order:");
        System.out.println("  1. --server flag (highest priority)");
        System.out.println("  2. STACKSAGE_SERVER environment variable");
        System.out.println("  3. http://localhost:9090 (default)");
    }

    void exitWithError(String message) {
        System.err.println("ERROR: " + message);
        System.exit(1);
    }
}
