package enerjy.muscxTG.TGTests.Bot;

import enerjy.muscxTG.TelegramBot.BotInit.MusicxBot;
import enerjy.muscxTG.TelegramBot.TGAPI.System.WebAPIInstance;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class BotAwaken {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WebAPIInstance webAPIInstance;

    @Autowired
    private MusicxBot musicxBot;

    @Autowired
    RestTemplate restTemplate;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    @DisplayName("Value checker. Checks if values in webAPIInstance and in musicxBot's token are not null and valid")
    void testTelegramGetMeApiWithoutNet() {
        String apiUrl = webAPIInstance.getApiUrl();

        assertThat(apiUrl).isNotNull();
        assertThat(apiUrl).contains("api.telegram.org");

        String token = musicxBot.getBotToken();
        String tokenStyle = "^\\d{8,15}:[A-Za-z0-9_-]{30,50}$";

        assertThat(token).isNotNull();
        assertThat(token).matches(tokenStyle);
    }

    @Test
    @DisplayName("Network checker. Checks if the token is alive and if it has a real access to api.telegram.org")
    @Disabled("Needs net connection, too slow, no reason to enable right now")
    void testTelegramGetMeApi() {
        String url = webAPIInstance.getApiUrl() + "getMe";
        try {
            String response = restTemplate.getForObject(url, String.class);

            assertThat(response).isNotNull();
            assertThat(response).contains("\"ok\":true");
            System.out.println("TG API response: " + response);
        }
        catch (Exception e) {
            throw new AssertionError("Unable to access Telegram API(Restful)");
        }
    }

}
