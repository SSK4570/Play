package com.nex.player.ssk.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AppPrefs {

    private static final String PREFS_NAME = "nex_player_prefs";
    private static final String KEY_PROVIDERS = "providers";
    private static final String KEY_ACTIVE_PROVIDER = "active_provider_id";
    private static final String KEY_FROM_CACHE = "from_cache";

    private final SharedPreferences prefs;
    private final Gson gson;

    private static AppPrefs instance;

    public static AppPrefs get(Context context) {
        if (instance == null) {
            instance = new AppPrefs(context.getApplicationContext());
        }
        return instance;
    }

    private AppPrefs(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public List<Provider> getProviders() {
        String json = prefs.getString(KEY_PROVIDERS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<Provider>>() {}.getType();
        try {
            List<Provider> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveProviders(List<Provider> providers) {
        prefs.edit().putString(KEY_PROVIDERS, gson.toJson(providers)).apply();
    }

    public void addProvider(Provider provider) {
        List<Provider> list = getProviders();
        provider.setId(System.currentTimeMillis());
        list.add(provider);
        saveProviders(list);
    }

    public void removeProvider(long id) {
        List<Provider> list = getProviders();
        list.removeIf(p -> p.getId() == id);
        saveProviders(list);
    }

    public boolean hasProviders() {
        return !getProviders().isEmpty();
    }

    public long getActiveProviderId() {
        return prefs.getLong(KEY_ACTIVE_PROVIDER, -1);
    }

    public void setActiveProviderId(long id) {
        prefs.edit().putLong(KEY_ACTIVE_PROVIDER, id).apply();
    }

    public Provider getActiveProvider() {
        long id = getActiveProviderId();
        if (id == -1) {
            List<Provider> list = getProviders();
            return list.isEmpty() ? null : list.get(0);
        }
        for (Provider p : getProviders()) {
            if (p.getId() == id) return p;
        }
        List<Provider> list = getProviders();
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean isFromCache() {
        return prefs.getBoolean(KEY_FROM_CACHE, false);
    }

    public void setFromCache(boolean fromCache) {
        prefs.edit().putBoolean(KEY_FROM_CACHE, fromCache).apply();
    }
}
