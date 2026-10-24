package dev.pluck.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("file:../frontend/", "file:frontend/", "classpath:/static/")
                .setCachePeriod(0);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/app").setViewName("forward:/app.html");
        registry.addViewController("/auth").setViewName("forward:/auth.html");
        registry.addViewController("/studio").setViewName("forward:/studio.html");
        registry.addViewController("/snippet").setViewName("forward:/snippet.html");
        registry.addViewController("/settings").setViewName("forward:/settings.html");
        registry.addViewController("/team").setViewName("forward:/app.html");
        registry.addViewController("/share").setViewName("forward:/share.html");
    }
}
