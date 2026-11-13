package enerjy.musicxService.api.applicationAPIs;

import enerjy.musicxService.Kafka.Producer.TGInteractionProducer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class userController {

    TGInteractionProducer tgInteraction;

    public userController(TGInteractionProducer tgInteraction) {
        this.tgInteraction = tgInteraction;
    }

    @GetMapping
    public ResponseEntity<String> getAllUsers() {
        tgInteraction.sendNotification(123L, "123");
        return ResponseEntity.ok("Notification sent successfully");
    }


}
