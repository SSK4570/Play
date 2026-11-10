package com.nex.player.ssk.provider;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nex.player.ssk.utils.Channel;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ChannelCache {

    private static final Gson GSON = new Gson();

    public static void save(Context context, long providerId, List<Channel> channels) {
        try {
            File f = getCacheFile(context, providerId);
            FileWriter fw = new FileWriter(f);
            GSON.toJson(channels, fw);
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<Channel> load(Context context, long providerId) {
        File f = getCacheFile(context, providerId);
        if (!f.exists()) return new ArrayList<>();
        try {
            FileReader fr = new FileReader(f);
            Type type = new TypeToken<List<Channel>>() {}.getType();
            List<Channel> list = GSON.fromJson(fr, type);
            fr.close();
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static boolean hasCached(Context context, long providerId) {
        return getCacheFile(context, providerId).exists();
    }

    public static void clear(Context context, long providerId) {
        getCacheFile(context, providerId).delete();
    }

    private static File getCacheFile(Context context, long providerId) {
        return new File(context.getCacheDir(), "channels_" + providerId + ".json");
    }
}
