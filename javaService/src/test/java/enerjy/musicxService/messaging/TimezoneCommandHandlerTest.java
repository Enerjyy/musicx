package enerjy.musicxService.messaging;

import enerjy.musicxService.messaging.contract.Channels;
import enerjy.musicxService.messaging.contract.IncomingMessage;
import enerjy.musicxService.messaging.contract.SendMessage;
import enerjy.musicxService.messaging.channel.ChannelRegistry;
import enerjy.musicxService.messaging.channel.OutboundChannel;
import enerjy.musicxService.security.AuthProvider;
import enerjy.musicxService.user.TimezoneStatus;
import enerjy.musicxService.user.userEntity;
import enerjy.musicxService.user.userService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimezoneCommandHandlerTest {

    @Mock
    private OutboundChannel channel;

    @Mock
    private userService userService;

    private TimezoneCommandHandler handler;

    @BeforeEach
    void setUp() {
        when(channel.id()).thenReturn(Channels.TELEGRAM);
        handler = new TimezoneCommandHandler(new ChannelRegistry(List.of(channel)), userService);
    }

    private IncomingMessage message(String text) {
        return IncomingMessage.create(Channels.TELEGRAM, "42", "1001", "42", text, "ru", null);
    }

    private userEntity userWith(String timezone, TimezoneStatus status) {
        var user = new userEntity();
        user.setUserId(7L);
        user.setTimezone(timezone);
        user.setTimezoneStatus(status);
        return user;
    }

    private String publishedText() {
        var captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(channel).publish(captor.capture());
        return captor.getValue().text();
    }

    /** /timezone без аргумента: показывает сохранённую зону, ничего не меняет. */
    @Test
    void withoutArgumentShowsCurrentZone() {
        when(userService.upsertByIdentity(eq(AuthProvider.TELEGRAM), eq("42"), any(), any()))
                .thenReturn(userWith("Europe/Berlin", TimezoneStatus.MANUAL));

        handler.handle(message("/timezone"));

        assertThat(publishedText()).contains("Europe/Berlin").contains("вручную");
        verify(userService, never()).setTimezone(anyLong(), any(ZoneId.class), any());
    }

    /** Зоны ещё нет: подсказываем формат вместо пустого ответа. */
    @Test
    void withoutSavedZoneSuggestsExample() {
        when(userService.upsertByIdentity(eq(AuthProvider.TELEGRAM), eq("42"), any(), any()))
                .thenReturn(userWith(null, null));

        handler.handle(message("/timezone"));

        assertThat(publishedText()).contains("не задан").contains(TimezoneCommandHandler.EXAMPLE);
        verify(userService, never()).setTimezone(anyLong(), any(ZoneId.class), any());
    }

    /** /timezone <зона>: зона сохраняется со статусом MANUAL. */
    @Test
    void validArgumentIsSavedAsManual() {
        when(userService.upsertByIdentity(eq(AuthProvider.TELEGRAM), eq("42"), any(), any()))
                .thenReturn(userWith("Europe/Moscow", TimezoneStatus.AUTO));

        handler.handle(message("/timezone Europe/Berlin"));

        verify(userService).setTimezone(7L, ZoneId.of("Europe/Berlin"), TimezoneStatus.MANUAL);
        assertThat(publishedText()).contains("Europe/Berlin");
    }

    /** Неверный ввод: зона не меняется, ответ содержит пример. */
    @Test
    void invalidArgumentRepliesWithExample() {
        when(userService.upsertByIdentity(eq(AuthProvider.TELEGRAM), eq("42"), any(), any()))
                .thenReturn(userWith("Europe/Moscow", TimezoneStatus.MANUAL));

        handler.handle(message("/timezone Mars/Olympus"));

        verify(userService, never()).setTimezone(anyLong(), any(ZoneId.class), any());
        assertThat(publishedText()).contains("Mars/Olympus").contains(TimezoneCommandHandler.EXAMPLE);
    }
}
