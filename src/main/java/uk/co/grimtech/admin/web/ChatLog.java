package uk.co.grimtech.admin.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatLog {
    private static final int MAX_MESSAGES = 100;
    private static final List<ChatEntry> messages = new CopyOnWriteArrayList<>();

    public static void addMessage(String sender, String message) {
        messages.add(new ChatEntry(System.currentTimeMillis(), sender, message));
        if (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
    }

    public static List<ChatEntry> getMessages() {
        return new ArrayList<>(messages);
    }

    public static class ChatEntry {
        public long timestamp;
        public String sender;
        public String message;

        public ChatEntry(long timestamp, String sender, String message) {
            this.timestamp = timestamp;
            this.sender = sender;
            this.message = message;
        }
    }
}
