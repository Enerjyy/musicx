package enerjy.muscxTG.TelegramBot.TGAPI.Sending;

import enerjy.muscxTG.TelegramBot.TGAPI.System.WebAPIInstance;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class SendAPI {

    private final WebAPIInstance webAPIInstance;
    private final RestTemplate restTemplate;

    public SendAPI(WebAPIInstance webAPIInstance, RestTemplate restTemplate) {
        this.webAPIInstance = webAPIInstance;
        this.restTemplate = restTemplate;
    }

    public void sendMessage(Long chatId, String text) {
        String url = webAPIInstance.getApiUrl() + "sendMessage";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("chat_id", chatId);
        requestBody.put("text", text);

        restTemplate.postForObject(url, requestBody, String.class);
    }

}
