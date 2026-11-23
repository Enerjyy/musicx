package enerjy.musicxService.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS для фронта.
 *
 * В dev Vite живёт на своём порту, в prod фронт под своим доменом — в обоих
 * случаях это кросс-домен, и без явного разрешения браузер заблокирует
 * запросы с `Authorization`.
 *
 * Список доменов — из `spring.web.cors.allowed-origins`, не `*`: со схемой
 * заголовка авторизации wildcard не имеет смысла, и браузеры его отвергают.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(
            @Value("${spring.web.cors.allowed-origins}") String allowedOrigins
    ) {
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .maxAge(3600);
    }
}