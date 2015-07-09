package com.jsonandroid;

import com.jsonutils.Json;
import com.utils.framework.network.RequestExecutor;
import com.utils.framework.suggestions.NetworkSuggestionsProvider;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by CM on 7/9/2015.
 */
public class StringListJsonSuggestionsProvider extends NetworkSuggestionsProvider<String> {
    private String jsonKey;

    public StringListJsonSuggestionsProvider(String url, Map<String, Object> args, RequestExecutor requestExecutor,
                                             String jsonKey) {
        super(url, args, requestExecutor);
        this.jsonKey = jsonKey;
    }

    public StringListJsonSuggestionsProvider(String url, Map<String, Object> args, String jsonKey) {
        super(url, args);
        this.jsonKey = jsonKey;
    }

    @Override
    protected List<String> parse(String response) {
        try {
            return Json.parseStringArray(response, jsonKey);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
