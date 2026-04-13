package com.stacksage.model;

import com.stacksage.parser.ExceptionDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisRequest {

    private String source;

    private List<ExceptionDetail> exceptions;
}
