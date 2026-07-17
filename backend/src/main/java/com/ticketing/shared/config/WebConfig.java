package com.ticketing.shared.config;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Prefixes every app REST controller with the configured API base path (single source of truth). */
@Configuration
class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;

    WebConfig(AppProperties props) {
        this.props = props;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // only our controllers get the prefix; springdoc/actuator keep their own paths
        configurer.addPathPrefix(props.api().basePath(),
                type -> type.getPackageName().startsWith("com.ticketing")
                        && type.isAnnotationPresent(RestController.class));
    }
}
