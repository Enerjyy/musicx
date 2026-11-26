package enerjy.muscxTG.telegram.commands;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Best-effort дедупликация команд по commandId на случай редоставки:
 * LRU-набор ограниченной ёмкости. Не замена идемпотентности ядра.
 */
final class LruCommandCache {

    private final Set<String> ids;
    private final int capacity;

    LruCommandCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.ids = Collections.newSetFromMap(new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > capacity;
            }
        });
    }

    /** false — команда уже исполнялась (повторная доставка). */
    synchronized boolean add(String commandId) {
        return ids.add(commandId);
    }

    /** Освободить id после устранимого сбоя, чтобы редоставка могла повторить команду. */
    synchronized void remove(String commandId) {
        ids.remove(commandId);
    }

    int capacity() {
        return capacity;
    }
}
