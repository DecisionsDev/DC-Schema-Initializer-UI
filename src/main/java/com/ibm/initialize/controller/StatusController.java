package com.ibm.initialize.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class StatusController {

    public static List<SseEmitter> emitters = new ArrayList<>();
    public static List<String> statusMessage = new ArrayList<>();
    public static List<String> pdfMessage = new ArrayList<>();

    @GetMapping("/status-stream")
    public SseEmitter streamStatus() {
        SseEmitter emitter = new SseEmitter();
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
        	emitters.remove(emitter);
        	emitter.complete();
        });
        

        emitter.onError((ex) -> {
        	emitters.remove(emitter);
        	emitter.completeWithError(ex);
});


        return emitter;
    }

    public static void addStatus(String message) {
        statusMessage.add(message);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(message));
            } catch (IOException e) {
                emitter.complete();
            }
        }
    }
}

