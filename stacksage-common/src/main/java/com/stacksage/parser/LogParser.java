package com.stacksage.parser;

import java.util.List;

public interface LogParser {

    List<ExceptionDetail> parse(String rawLog);
}
