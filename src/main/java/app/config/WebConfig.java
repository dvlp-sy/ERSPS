package app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 전역 설정 클래스
 * <p>CORS 설정을 통해 모든 출처에서의 GET 요청을 허용한다.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
