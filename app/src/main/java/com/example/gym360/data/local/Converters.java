package com.example.gym360.data.local;

import androidx.room.TypeConverter;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

public class Converters {
    private Converters() {}
    @TypeConverter
    public static String fromList(List<String> list) {
        JSONArray array = new JSONArray();
        if (list != null) {
            for (String benefit : list) array.put(benefit);
        }
        return array.toString();
    }

    @TypeConverter
    public static List<String> fromString(String serialized) {
        List<String> result = new ArrayList<>();
        if (serialized == null) return result;
        try {
            JSONArray array = new JSONArray(serialized);
            for (int i = 0; i < array.length(); i++) result.add(array.getString(i));
            return result;
        } catch (JSONException ex) {
            throw new IllegalArgumentException("Beneficios JSON inválidos", ex);
        }
    }
}
