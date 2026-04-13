package com.stacksage.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExceptionDetail {

    private String exceptionType;
    private String message;
    private List<String> stackTrace;
}
