package enerjy.muscxTG.TelegramBot.TGAPI.System;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class ApiConfig {

    /**
     * Таймауты обязательны: без них зависший вызов Telegram держит поток
     * консьюмера команд. Read-таймаут согласован с TTL черновика (30 с).
     */
    @Bean
    public RestTemplate restTemplate(
            @Value("${app.telegram.connect-timeout-seconds:5}") long connectTimeoutSeconds,
            @Value("${app.telegram.read-timeout-seconds:12}") long readTimeoutSeconds
    ) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return new RestTemplate(factory);
    }
}
