package enerjy.musicxService.Kafka.CFG;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigTest {

    @Test
    void producerIsIdempotentAndWaitsForAllReplicas() {
        var props = new KafkaProducerConfig().producerFactory("localhost:9092").getConfigurationProperties();

        assertThat(props.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isEqualTo(true);
        assertThat(props.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
    }

    @Test
    void allContractTopicsAreDeclaredWithRequestedPartitions() {
        var topics = KafkaTopicsConfig.contractTopicDefinitions(3, (short) 1);

        assertThat(topics).extracting(NewTopic::name).containsExactlyInAnyOrder(
                "inbound.messages", "inbound.messages.dlt",
                "outbound.results", "outbound.results.dlt",
                "outbound.commands.telegram", "outbound.commands.telegram.dlt");
        assertThat(topics).allSatisfy(topic -> {
            assertThat(topic.numPartitions()).isEqualTo(3);
            assertThat(topic.replicationFactor()).isEqualTo((short) 1);
        });
    }

    @Test
    void deadLetterGoesToDltTopicAndLetsKafkaChoosePartition() {
        var record = new ConsumerRecord<>("inbound.messages", 2, 15L, "TELEGRAM:1", "v");

        var destination = ContractKafkaConfig.deadLetterDestination(record, new IllegalStateException("x"));

        assertThat(destination.topic()).isEqualTo("inbound.messages.dlt");
        assertThat(destination.partition()).isEqualTo(-1);
    }
}
