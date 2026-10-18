package com.remotepocket.util;

import java.io.Serializable;

public class TextHistoryItem implements Serializable {
    private String id;
    private String text;
    private long timestamp;
    private long sessionId;

    public TextHistoryItem() {
    }

    public TextHistoryItem(String id, String text, long timestamp, long sessionId) {
        this.id = id;
        this.text = text;
        this.timestamp = timestamp;
        this.sessionId = sessionId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }
}
