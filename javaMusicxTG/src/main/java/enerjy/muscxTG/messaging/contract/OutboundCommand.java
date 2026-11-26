package enerjy.muscxTG.messaging.contract;

/** РљРѕРјР°РЅРґР° СЏРґСЂР° С‚СЂР°РЅСЃРїРѕСЂС‚РЅРѕРјСѓ СЃРµСЂРІРёСЃСѓ. РўРѕРїРёРє: {@link Topics#outboundCommands(String)}. */
public sealed interface OutboundCommand extends KafkaMessage permits ShowProgress, SendMessage, DeleteMessage {
}
