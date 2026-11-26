package enerjy.musicxService.user;

/**
 * Кто задал часовой пояс пользователя.
 *
 * AUTO — зона пришла от Mini App при входе (детект устройства), MANUAL —
 * пользователь выставил её сам командой /timezone. Ручная зона важнее:
 * автодетект её не перетирает.
 */
public enum TimezoneStatus {
    AUTO,
    MANUAL
}
