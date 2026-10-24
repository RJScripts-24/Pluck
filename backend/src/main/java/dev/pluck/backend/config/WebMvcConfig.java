package dev.pluck.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/app").setViewName("forward:/app.html");
        registry.addViewController("/auth").setViewName("forward:/auth.html");
        registry.addViewController("/studio").setViewName("forward:/studio.html");
        registry.addViewController("/snippet").setViewName("forward:/snippet.html");
        registry.addViewController("/share").setViewName("forward:/share.html");
        registry.addViewController("/settings").setViewName("forward:/settings.html");
        registry.addViewController("/team").setViewName("forward:/team.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("file:../frontend/", "classpath:/static/");
    }
}
