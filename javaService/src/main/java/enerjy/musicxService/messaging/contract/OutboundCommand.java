package enerjy.musicxService.messaging.contract;

/** Команда ядра транспортному сервису. Топик: {@link Topics#outboundCommands(String)}. */
public sealed interface OutboundCommand extends KafkaMessage permits ShowProgress, SendMessage, DeleteMessage {
}
