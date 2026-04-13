package com.stacksage.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI stackSageOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("StackSage API")
                        .version("0.1.0")
                        .description("AI-powered Java debugging platform. Upload log files or submit "
                                + "pre-parsed exception data for automated root cause analysis.")
                        .contact(new Contact().name("StackSage")));
    }
}
