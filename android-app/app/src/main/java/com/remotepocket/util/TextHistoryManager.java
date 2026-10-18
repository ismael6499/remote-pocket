package com.remotepocket.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TextHistoryManager {
    private static final String PREF_NAME = "remote_pocket_text_history";
    private static final String KEY_ITEMS = "history_items";
    private static final int MAX_ITEMS = 60;

    private static TextHistoryManager instance;
    private final SharedPreferences prefs;
    private final Gson gson;
    private final long currentSessionId;
    private List<TextHistoryItem> cachedItems;

    public static synchronized TextHistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new TextHistoryManager(context.getApplicationContext());
        }
        return instance;
    }

    private TextHistoryManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.currentSessionId = System.currentTimeMillis();
        loadItems();
    }

    private synchronized void loadItems() {
        String json = prefs.getString(KEY_ITEMS, null);
        if (json != null) {
            try {
                Type type = new TypeToken<ArrayList<TextHistoryItem>>() {}.getType();
                cachedItems = gson.fromJson(json, type);
            } catch (Exception e) {
                cachedItems = new ArrayList<>();
            }
        }
        if (cachedItems == null) {
            cachedItems = new ArrayList<>();
        }
    }

    private synchronized void saveItems() {
        try {
            String json = gson.toJson(cachedItems);
            prefs.edit().putString(KEY_ITEMS, json).apply();
        } catch (Exception ignored) {
        }
    }

    public synchronized void recordSentText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        // Deduplicate if identical to the immediate previous sent text
        if (!cachedItems.isEmpty() && text.equals(cachedItems.get(0).getText())) {
            cachedItems.remove(0);
        }

        TextHistoryItem item = new TextHistoryItem(
                UUID.randomUUID().toString(),
                text,
                System.currentTimeMillis(),
                currentSessionId
        );
        cachedItems.add(0, item);

        while (cachedItems.size() > MAX_ITEMS) {
            cachedItems.remove(cachedItems.size() - 1);
        }

        saveItems();
    }

    public synchronized TextHistoryItem getLastSentItem() {
        if (cachedItems != null && !cachedItems.isEmpty()) {
            return cachedItems.get(0);
        }
        return null;
    }

    public synchronized List<TextHistoryItem> getAllItems() {
        return new ArrayList<>(cachedItems);
    }

    public synchronized List<TextHistoryItem> getCurrentSessionItems() {
        List<TextHistoryItem> list = new ArrayList<>();
        for (TextHistoryItem item : cachedItems) {
            if (item.getSessionId() == currentSessionId) {
                list.add(item);
            }
        }
        return list;
    }

    public synchronized void deleteItem(String id) {
        if (id == null) return;
        for (int i = 0; i < cachedItems.size(); i++) {
            if (id.equals(cachedItems.get(i).getId())) {
                cachedItems.remove(i);
                saveItems();
                break;
            }
        }
    }

    public synchronized void clearHistory() {
        cachedItems.clear();
        prefs.edit().remove(KEY_ITEMS).apply();
    }

    public long getCurrentSessionId() {
        return currentSessionId;
    }
}
