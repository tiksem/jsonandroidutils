package com.jsonandroid;

import com.jsonutils.Json;
import com.utils.framework.OnError;
import com.utils.framework.collections.NavigationList;
import com.utils.framework.collections.OnLoadingFinished;
import com.utilsframework.android.threading.Threading;

import java.io.IOException;
import java.util.*;

/**
 * Created by CM on 6/16/2015.
 */
public class JsonAsyncNavigationList<T> extends NavigationList<T> {
    private int limit = 10;
    private String limitParamName = "limit";
    private String offsetParamName = "offset";
    private String url;
    private Class<T> aClass;
    private RequestExecutor requestExecutor;
    private Map<String, Object> args;

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
        args.put(limitParamName, limit);
    }

    public String getLimitParamName() {
        return limitParamName;
    }

    public void setLimitParamName(String limitParamName) {
        this.limitParamName = limitParamName;
        args.put(limitParamName, limit);
    }

    public String getOffsetParamName() {
        return offsetParamName;
    }

    public void setOffsetParamName(String offsetParamName) {
        this.offsetParamName = offsetParamName;
    }

    public JsonAsyncNavigationList(Class<T> aClass, String url,
                                   Map<String, String> args, RequestExecutor requestExecutor) {
        this.aClass = aClass;
        this.url = url;
        this.requestExecutor = requestExecutor;
        this.args = new HashMap<>(args);
    }

    public JsonAsyncNavigationList(Class<T> aClass, String url, Map<String, String> args) {
        this(aClass, url, args, new GetRequestExecutor());
    }

    @Override
    public void getElementsOfPage(int pageNumber, OnLoadingFinished<T> onPageLoadingFinished,
                                  OnError onError) {
        int offset = pageNumber * limit;
        args.put(offsetParamName, offset);

        Threading.executeAsyncTask(new Threading.Task<IOException, List<T>>() {
            @Override
            public List<T> runOnBackground() throws IOException {
                args.put(limitParamName, limit);
                return getElements(url, args, requestExecutor, aClass);
            }

            @Override
            public void onSuccess(List<T> elements) {
                boolean isLastPage = elements.size() < limit;
                onPageLoadingFinished.onLoadingFinished(elements, isLastPage);
            }

            @Override
            public void onError(IOException error) {
                onError.onError(error);
            }
        });
    }

    protected List<T> getElements(String url, Map<String, Object> args,
                                  RequestExecutor requestExecutor,
                                  Class<T> aClass) throws IOException {
        String response = request(url, args, requestExecutor);
        return parse(response, aClass);
    }

    protected List<T> parse(String response, Class<T> aClass) throws IOException {
        return Json.parseJsonArray(response, aClass);
    }

    protected String request(String url, Map<String, Object> args, RequestExecutor requestExecutor) throws IOException {
        return requestExecutor.executeRequest(url, args);
    }
}