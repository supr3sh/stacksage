package com.stacksage.parser;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexLogParser implements LogParser {

    private static final Logger log = Logger.getLogger(RegexLogParser.class.getName());

    static final int MAX_EXCEPTIONS = 50;
    private static final int MAX_LINE_LENGTH = 2000;

    private static final Pattern EXCEPTION_HEADER = Pattern.compile(
            "^(?:Caused by:\\s*|Suppressed:\\s*)?" +
            "([^\\s.]+(?:\\.[^\\s.]+)*(?:Exception|Error|Throwable))" +
            "(?:\\s*:\\s*(.*))?$"
    );

    private static final Pattern PREFIXED_EXCEPTION_HEADER = Pattern.compile(
            "^.+?[-\\u2013]\\s+" +
            "([^\\s.]+(?:\\.[^\\s.]+)*(?:Exception|Error|Throwable))" +
            "(?:\\s*:\\s*(.*))?$"
    );

    private static final Pattern STACK_FRAME = Pattern.compile(
            "^\\s+(?:at\\s+.*|\\.\\.\\.\\s+\\d+\\s+more)$"
    );

    @Override
    public List<ExceptionDetail> parse(String rawLog) {
        if (rawLog == null || rawLog.isBlank()) {
            return List.of();
        }

        List<ExceptionDetail> results = new ArrayList<>();

        String currentType = null;
        String currentMessage = null;
        List<String> currentStack = new ArrayList<>();
        boolean collectingMessage = false;

        try (BufferedReader reader = new BufferedReader(new StringReader(rawLog))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (results.size() >= MAX_EXCEPTIONS) {
                    log.warning("Parsing capped at " + MAX_EXCEPTIONS + " exceptions");
                    break;
                }

                if (line.length() > MAX_LINE_LENGTH) {
                    continue;
                }

                String trimmed = line.trim();

                Matcher headerMatcher = EXCEPTION_HEADER.matcher(trimmed);
                if (!headerMatcher.matches()) {
                    headerMatcher = PREFIXED_EXCEPTION_HEADER.matcher(trimmed);
                }

                if (headerMatcher.matches()) {
                    flushCurrent(results, currentType, currentMessage, currentStack);
                    currentType = headerMatcher.group(1);
                    currentMessage = headerMatcher.group(2);
                    currentStack = new ArrayList<>();
                    collectingMessage = true;
                    continue;
                }

                if (currentType != null && STACK_FRAME.matcher(line).matches()) {
                    currentStack.add(trimmed);
                    collectingMessage = false;
                    continue;
                }

                if (collectingMessage && currentType != null && !trimmed.isEmpty()
                        && currentStack.isEmpty()) {
                    if (currentMessage == null) {
                        currentMessage = trimmed;
                    } else {
                        currentMessage = currentMessage + " " + trimmed;
                    }
                    continue;
                }

                if (currentType != null && !trimmed.isEmpty()) {
                    flushCurrent(results, currentType, currentMessage, currentStack);
                    currentType = null;
                    currentMessage = null;
                    currentStack = new ArrayList<>();
                    collectingMessage = false;
                }
            }
        } catch (java.io.IOException e) {
            log.warning("Error reading log input: " + e.getMessage());
        }

        if (results.size() < MAX_EXCEPTIONS) {
            flushCurrent(results, currentType, currentMessage, currentStack);
        }

        return results;
    }

    private void flushCurrent(List<ExceptionDetail> results, String type,
                              String message, List<String> stack) {
        if (type != null) {
            results.add(ExceptionDetail.builder()
                    .exceptionType(type)
                    .message(message)
                    .stackTrace(List.copyOf(stack))
                    .build());
        }
    }
}
