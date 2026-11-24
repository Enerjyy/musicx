package enerjy.muscxTG.TelegramBot.TGAPI.System;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class WebAPIInstance {
    private final RestTemplate restTemplate;
    private final String telegramApiUrl;

    public WebAPIInstance(RestTemplate restTemplate, @Value("${bot.token}") String botToken) {
        this.restTemplate = restTemplate;
        telegramApiUrl = "https://api.telegram.org/bot" + botToken + "/";
    }

    public String getApiUrl() {
        return telegramApiUrl;
    }

    public RestTemplate getRestTemplate() {
        return restTemplate;
    }
}
