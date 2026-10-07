package com.valaryrpg.core.module.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-flood / anti-spam messaggi ripetuti.
 */
final class ChatFloodGuard {

    private final Map<UUID, Deque<Long>> timestamps = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> repeatCount = new ConcurrentHashMap<>();

    private int maxMessages;
    private long windowMs;
    private long minDelayMs;
    private int maxRepeats;
    private int maxSameChar;

    void reload(int maxMessages, long windowMs, long minDelayMs, int maxRepeats, int maxSameChar) {
        this.maxMessages = Math.max(0, maxMessages);
        this.windowMs = Math.max(0L, windowMs);
        this.minDelayMs = Math.max(0L, minDelayMs);
        this.maxRepeats = Math.max(0, maxRepeats);
        this.maxSameChar = Math.max(0, maxSameChar);
    }

    Optional<ChatFilter.Hit> check(UUID uuid, String text) {
        if (uuid == null || text == null) {
            return Optional.empty();
        }
        if (this.maxSameChar > 0 && sameCharSpam(text, this.maxSameChar)) {
            return Optional.of(ChatFilter.Hit.REPEAT);
        }
        long now = System.currentTimeMillis();
        if (this.minDelayMs > 0) {
            Deque<Long> times = this.timestamps.get(uuid);
            if (times != null && !times.isEmpty() && now - times.peekLast() < this.minDelayMs) {
                return Optional.of(ChatFilter.Hit.FLOOD);
            }
        }
        if (this.maxMessages > 0 && this.windowMs > 0) {
            Deque<Long> times = this.timestamps.computeIfAbsent(uuid, ignored -> new ArrayDeque<>());
            while (!times.isEmpty() && now - times.peekFirst() > this.windowMs) {
                times.pollFirst();
            }
            if (times.size() >= this.maxMessages) {
                return Optional.of(ChatFilter.Hit.FLOOD);
            }
            times.addLast(now);
        } else if (this.minDelayMs > 0) {
            this.timestamps.computeIfAbsent(uuid, ignored -> new ArrayDeque<>()).addLast(now);
        }
        if (this.maxRepeats > 0) {
            String normalized = ChatFilter.normalize(text).trim();
            String previous = this.lastMessage.put(uuid, normalized);
            if (previous != null && previous.equals(normalized)) {
                int count = this.repeatCount.merge(uuid, 1, Integer::sum);
                if (count >= this.maxRepeats) {
                    return Optional.of(ChatFilter.Hit.REPEAT);
                }
            } else {
                this.repeatCount.put(uuid, 1);
            }
        }
        return Optional.empty();
    }

    void clear(UUID uuid) {
        this.timestamps.remove(uuid);
        this.lastMessage.remove(uuid);
        this.repeatCount.remove(uuid);
    }

    private static boolean sameCharSpam(String text, int max) {
        int run = 1;
        for (int i = 1; i < text.length(); i++) {
            if (text.charAt(i) == text.charAt(i - 1) && !Character.isWhitespace(text.charAt(i))) {
                run++;
                if (run > max) {
                    return true;
                }
            } else {
                run = 1;
            }
        }
        return false;
    }
}
