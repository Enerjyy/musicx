package enerjy.musicxService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Планировщик нужен напоминаниям и отложенным удаления (@Scheduled). */
@EnableKafka
@EnableScheduling
@SpringBootApplication
public class JavaService {

	public static void main(String[] args) {
		SpringApplication.run(JavaService.class, args);
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper()
				.registerModule(new JavaTimeModule());
	}
}