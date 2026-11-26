package enerjy.musicxService.Kafka.CFG;

import enerjy.musicxService.messaging.contract.Topics;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Map;

/** Явное создание топиков контракта и их DLT. Топики создаёт только ядро. */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    public KafkaAdmin kafkaAdmin(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${app.kafka.topics.auto-create:true}") boolean autoCreate
    ) {
        var admin = new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
        admin.setAutoCreate(autoCreate);
        admin.setFatalIfBrokerNotAvailable(false);
        return admin;
    }

    @Bean
    public KafkaAdmin.NewTopics contractTopics(
            @Value("${app.kafka.topics.partitions:3}") int partitions,
            @Value("${app.kafka.topics.replicas:1}") short replicas
    ) {
        return new KafkaAdmin.NewTopics(contractTopicDefinitions(partitions, replicas).toArray(NewTopic[]::new));
    }

    static java.util.List<NewTopic> contractTopicDefinitions(int partitions, short replicas) {
        return Topics.contractTopics().stream()
                .map(name -> TopicBuilder.name(name).partitions(partitions).replicas(replicas).build())
                .toList();
    }
}
