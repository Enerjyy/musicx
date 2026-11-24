package enerjy.muscxTG;

import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.muscxTG.TelegramBot.BotInit.MusicxBot;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@EnableKafka
@SpringBootApplication
public class MuscxTgApplication {

	public static void main(String[] args) {
		SpringApplication.run(MuscxTgApplication.class, args);
	}


	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

	@Bean
	public TelegramBotsApi telegramBotsApi(MusicxBot bot) throws TelegramApiException {
		TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
		api.registerBot(bot);
		return api;
	}
}
