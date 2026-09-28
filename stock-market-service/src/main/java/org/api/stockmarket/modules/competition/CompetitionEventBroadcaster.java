package org.api.stockmarket.modules.competition;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class CompetitionEventBroadcaster {
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long competitionId) {
        SseEmitter emitter = new SseEmitter(0L);
        List<SseEmitter> list = emitters.computeIfAbsent(competitionId, ignored -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());
        try { emitter.send(SseEmitter.event().name("connected").data(Map.of("competitionId", competitionId))); }
        catch (IOException e) { remove.run(); }
        return emitter;
    }

    public void broadcast(Long competitionId, String event, Object payload) {
        List<SseEmitter> list = emitters.getOrDefault(competitionId, new CopyOnWriteArrayList<>());
        for (SseEmitter emitter : list) {
            try { emitter.send(SseEmitter.event().name(event).data(payload)); }
            catch (IOException | IllegalStateException e) { list.remove(emitter); }
        }
    }
}
