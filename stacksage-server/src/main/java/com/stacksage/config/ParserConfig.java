package com.stacksage.config;

import com.stacksage.parser.LogParser;
import com.stacksage.parser.RegexLogParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ParserConfig {

    @Bean
    public LogParser logParser() {
        return new RegexLogParser();
    }
}
