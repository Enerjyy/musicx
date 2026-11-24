package enerjy.musicxService.user;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Data
public class userSchedules {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sceduleId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime time;

    @Column(length = 200)
    private String title;

    @Column(length = 255)
    private String note;
}