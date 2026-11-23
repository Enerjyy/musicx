package enerjy.musicxService.security;

import enerjy.musicxService.user.UserPrincipal;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Достаёт пользователя из `Authorization: Bearer <jwt>` и кладёт его в
 * request attribute, чтобы контроллеры не парсили токен сами.
 *
 * Фильтр не знает ничего про способ входа: он проверяет подпись и срок, а
 * провайдера просто переносит из токена в principal. Поэтому добавление
 * второго провайдера этот класс не затронет.
 *
 * Пути логина и OPTIONS не трогаем: на логин токена ещё нет, а OPTIONS —
 * это CORS-preflight, он всегда без заголовка авторизации.
 *
 * Отказ — 401 с телом, без проброса исключения наружу: иначе Spring отдаст
 * свой HTML-ответ, а фронт ждёт JSON.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL_ATTRIBUTE = "principal";

    private static final String BEARER_PREFIX = "Bearer ";

    /** Эндпоинты, на которых токен не нужен: там его ещё не выдали. */
    private static final String[] PUBLIC_PATHS = {
            "/auth/telegram"
    };

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(@Nonnull HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String path = request.getRequestURI();
        if (path.startsWith("/error")) return true;

        for (String publicPath : PUBLIC_PATHS) {
            if (path.equals(publicPath)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            reject(response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            reject(response);
            return;
        }

        final AuthenticatedUser authenticated;
        try {
            authenticated = jwtService.parse(token);
        } catch (TokenException e) {
            reject(response);
            return;
        }

        request.setAttribute(
                PRINCIPAL_ATTRIBUTE,
                new UserPrincipal(authenticated.userId(), authenticated.provider()));
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        // Не логируем сам токен и не раскрываем причину клиенту: повод для
        // 401 всегда один — «перелогинься».
        response.getWriter().write("{\"error\":\"unauthorized\"}");
    }
}