package enerjy.musicxService.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Составной ключ processed_events: событие × консьюмер. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEventId implements Serializable {

    private String eventId;
    private String consumer;
}
