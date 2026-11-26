package enerjy.musicxService.api.applicationAPIs;

import enerjy.musicxService.security.AuthProvider;
import enerjy.musicxService.user.TimezoneStatus;
import enerjy.musicxService.user.UserPrincipal;
import enerjy.musicxService.user.userEntity;
import enerjy.musicxService.user.userIdentityRepo;
import enerjy.musicxService.user.userRepo;
import enerjy.musicxService.user.userSchedulesRepo;
import enerjy.musicxService.user.userService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class usersControllerTest {

    private static final String URI = "/users/me/timezone";
    private static final UserPrincipal PRINCIPAL =
            new UserPrincipal(7L, AuthProvider.TELEGRAM);

    @Mock
    private userService userService;

    @Mock
    private userRepo userRepo;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new usersController(userService)).build();
    }

    private userEntity userWith(String timezone, TimezoneStatus status) {
        var user = new userEntity();
        user.setUserId(7L);
        user.setTimezone(timezone);
        user.setTimezoneStatus(status);
        return user;
    }

    /** Зона из Mini App сохраняется со статусом AUTO. */
    @Test
    void savesDeviceZoneAsAuto() throws Exception {
        when(userService.setTimezone(eq(7L), eq(ZoneId.of("Europe/Berlin")), eq(TimezoneStatus.AUTO)))
                .thenReturn(userWith("Europe/Berlin", TimezoneStatus.AUTO));

        mockMvc.perform(put(URI)
                        .requestAttr("principal", PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Europe/Berlin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/Berlin"))
                .andExpect(jsonPath("$.status").value("AUTO"));

        verify(userService).setTimezone(7L, ZoneId.of("Europe/Berlin"), TimezoneStatus.AUTO);
    }

    /** Ручная зона не перетирается: сервис возвращает её как есть, а контроллер отдаёт факт. */
    @Test
    void doesNotOverrideManualZone() throws Exception {
        when(userService.setTimezone(anyLong(), any(ZoneId.class), any(TimezoneStatus.class)))
                .thenReturn(userWith("Europe/Moscow", TimezoneStatus.MANUAL));

        mockMvc.perform(put(URI)
                        .requestAttr("principal", PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Europe/Berlin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/Moscow"))
                .andExpect(jsonPath("$.status").value("MANUAL"));

        verify(userService).setTimezone(7L, ZoneId.of("Europe/Berlin"), TimezoneStatus.AUTO);
    }

    /** Неверная зона — 400, сервис не вызывается. */
    @Test
    void invalidZoneIsBadRequest() throws Exception {
        mockMvc.perform(put(URI)
                        .requestAttr("principal", PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Mars/Olympus\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    /** Пустая зона — 400. */
    @Test
    void blankZoneIsBadRequest() throws Exception {
        mockMvc.perform(put(URI)
                        .requestAttr("principal", PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    /** Сервисный контракт: AUTO не перетирает MANUAL — зона и статус остаются прежними. */
    @Test
    void setTimezoneKeepsManualZone() {
        var manual = userWith("Europe/Moscow", TimezoneStatus.MANUAL);
        when(userRepo.findByUserId(7L)).thenReturn(Optional.of(manual));
        var service = new userService(userRepo, mock(userIdentityRepo.class), mock(userSchedulesRepo.class));

        var result = service.setTimezone(7L, ZoneId.of("Europe/Berlin"), TimezoneStatus.AUTO);

        assertThat(result.getTimezone()).isEqualTo("Europe/Moscow");
        assertThat(result.getTimezoneStatus()).isEqualTo(TimezoneStatus.MANUAL);
        verify(userRepo, never()).save(any());
    }

    /** Сервисный контракт: AUTO поверх AUTO (или пустой зоны) сохраняется. */
    @Test
    void setTimezoneSavesAutoOverAuto() {
        var auto = userWith("Europe/Moscow", TimezoneStatus.AUTO);
        when(userRepo.findByUserId(7L)).thenReturn(Optional.of(auto));
        when(userRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new userService(userRepo, mock(userIdentityRepo.class), mock(userSchedulesRepo.class));

        var result = service.setTimezone(7L, ZoneId.of("Europe/Berlin"), TimezoneStatus.AUTO);

        assertThat(result.getTimezone()).isEqualTo("Europe/Berlin");
        assertThat(result.getTimezoneStatus()).isEqualTo(TimezoneStatus.AUTO);
        verify(userRepo).save(auto);
    }
}
