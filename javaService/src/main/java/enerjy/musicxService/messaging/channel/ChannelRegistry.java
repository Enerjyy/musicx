package enerjy.musicxService.messaging.channel;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Выбор канала по идентификатору из контракта. */
@Component
public class ChannelRegistry {

    private final Map<String, OutboundChannel> channels = new LinkedHashMap<>();

    public ChannelRegistry(List<OutboundChannel> implementations) {
        for (OutboundChannel channel : implementations) {
            var previous = channels.put(channel.id(), channel);
            if (previous != null) {
                throw new IllegalStateException("duplicate channel implementation: " + channel.id());
            }
        }
    }

    public OutboundChannel by(String channelId) {
        var channel = channels.get(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("unsupported channel: " + channelId);
        }
        return channel;
    }
}
