package enerjy.muscxTG;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;

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
}
