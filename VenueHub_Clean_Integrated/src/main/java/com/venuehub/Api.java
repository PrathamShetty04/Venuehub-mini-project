package com.venuehub;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Api {
    private Api() { }

    public static Map<String, Object> ok(Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", data);
        return result;
    }

    public static Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", (message == null || message.isBlank()) ? "Request failed." : message);
        return result;
    }
}
