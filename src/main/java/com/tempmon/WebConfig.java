package com.tempmon;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Configures Spring MVC to serve the React SPA from static resources.
 * <p>
 * Unknown paths (i.e., paths that don't match an API controller or a real static file)
 * are forwarded to index.html so that client-side routing works on page refresh.
 * <p>
 * API routes (/ingest, /readings, /health) are handled by their respective controllers
 * and take priority over static resource serving because Spring MVC resolves controller
 * mappings before resource handlers.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        // If the requested resource exists (e.g., JS, CSS, images), serve it directly.
                        // Otherwise, fall back to index.html for SPA client-side routing.
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
