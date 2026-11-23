package enerjy.musicxService.user;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Data
public class userSchedules {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sceduleId;

    @NotNull
    private Long userId;

    @NotNull
    private String title;

    @NotNull
    private String note;

    @NotNull
    private LocalDate date;

    @NotNull
    private LocalTime time;


}
