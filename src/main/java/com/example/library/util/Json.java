package com.example.library.util;
import com.google.gson.*;
import java.time.*;
public final class Json {
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, (JsonSerializer<LocalDateTime>)(v,t,c) -> new JsonPrimitive(v.toString()))
            .registerTypeAdapter(LocalDate.class, (JsonSerializer<LocalDate>)(v,t,c) -> new JsonPrimitive(v.toString()))
            .create();
    private Json() {}
}
