package com.seal.oa.web;

import com.seal.oa.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final ModuleActionInterceptor moduleActionInterceptor;
    private final OperationLogInterceptor operationLogInterceptor;
    private final com.seal.oa.workflow.WorkflowApiGuardInterceptor workflowApiGuard;

    public WebConfig(
            AuthInterceptor authInterceptor,
            ModuleActionInterceptor moduleActionInterceptor,
            OperationLogInterceptor operationLogInterceptor,
            com.seal.oa.workflow.WorkflowApiGuardInterceptor workflowApiGuard
    ) {
        this.authInterceptor = authInterceptor;
        this.moduleActionInterceptor = moduleActionInterceptor;
        this.operationLogInterceptor = operationLogInterceptor;
        this.workflowApiGuard = workflowApiGuard;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/captchaImage",
                        "/login",
                        "/login/pwd",
                        "/logout",
                        "/crm/user/register",
                        "/profile/**",
                        "/common/download/**",
                        "/error",
                        "/actuator/health",
                        "/favicon.ico"
                );
        registry.addInterceptor(moduleActionInterceptor)
                .addPathPatterns("/**");
        registry.addInterceptor(workflowApiGuard).addPathPatterns("/**");
        registry.addInterceptor(operationLogInterceptor)
                .addPathPatterns("/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                        "https://oa.ouopen.com",
                        "http://oa.ouopen.com",
                        "http://localhost:*",
                        "http://127.0.0.1:*"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/profile/**")
                .addResourceLocations("file:./data/uploads/");
    }
}
