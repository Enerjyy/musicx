package enerjy.musicxService.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Пользователь musicx — «кто это», без привязки к способу входа.
 *
 * Идентификаторы провайдеров (Telegram ID, Google sub и прочие) живут в
 * {@link userIdentityEntity}. Это разделение нужно затем, чтобы появление
 * второго способа входа не требовало менять эту таблицу: пользователь
 * остаётся один, а связок у него может быть несколько.
 *
 * `userId` — внутренний ключ, на него ссылаются identity и все сущности.
 * `name` — отображаемое имя, `nickname` — username у провайдера.
 */
@Entity
@Table(name = "user_entity")
@Data
public class userEntity {

    public userEntity() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(length = 255)
    private String name;

    @Column(length = 255)
    private String nickname;
}