package com.jsonandroid;

import com.jsonutils.Json;
import com.utils.framework.KeyProvider;
import com.utils.framework.OnError;
import com.utils.framework.collections.NavigationList;
import com.utils.framework.collections.OnLoadingFinished;
import com.utils.framework.collections.UniqueNavigationList;
import com.utils.framework.network.GetRequestExecutor;
import com.utils.framework.network.RequestExecutor;
import com.utilsframework.android.threading.Threading;

import java.io.IOException;
import java.util.*;

/**
 * Created by CM on 6/16/2015.
 */
public class JsonAsyncNavigationList<T> extends UniqueNavigationList<T> {
    private int limit = 10;
    private String limitParamName = "limit";
    private String offsetParamName = "offset";
    private String url;
    private Class<T> aClass;
    private RequestExecutor requestExecutor;
    private Map<String, Object> args;
    private String jsonKey;

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

    public JsonAsyncNavigationList(Class<T> aClass, String url, String jsonKey,
                                   Map<String, Object> args, RequestExecutor requestExecutor,
                                   List<T> preloadedElements) {
        super(preloadedElements, Integer.MAX_VALUE);
        this.aClass = aClass;
        this.url = url;
        this.jsonKey = jsonKey;
        this.requestExecutor = requestExecutor;
        this.args = args != null ? new HashMap<>(args) : new HashMap<String, Object>();
    }

    public JsonAsyncNavigationList(Class<T> aClass, String url, String jsonKey,
                                   Map<String, Object> args, RequestExecutor requestExecutor) {
        this(aClass, url, jsonKey, args, requestExecutor, null);
    }

    public JsonAsyncNavigationList(Class<T> aClass, String url, String jsonKey, Map<String, Object> args) {
        this(aClass, url, jsonKey, args, new GetRequestExecutor());
    }

    protected int getOffset() {
        return getElementsCount();
    }

    @Override
    public void getElementsOfPage(int pageNumber, final OnLoadingFinished<T> onPageLoadingFinished,
                                  final OnError onError) {
        int offset = getOffset();
        args.put(offsetParamName, offset);

        Threading.executeAsyncTask(new Threading.Task<IOException, List<T>>() {
            @Override
            public List<T> runOnBackground() throws IOException {
                args.put(limitParamName, limit);
                return getElements(url, args, requestExecutor, aClass);
            }

            @Override
            public void onComplete(List<T> elements, IOException error) {
                if (error == null) {
                    boolean isLastPage = isLastPage(elements, limit);
                    onPageLoadingFinished.onLoadingFinished(elements, isLastPage);
                } else {
                    onError.onError(error);
                }
            }
        }, IOException.class);
    }

    protected boolean isLastPage(List<T> elements, int limit) {
        return elements.size() < limit;
    }

    protected List<T> getElements(String url, Map<String, Object> args,
                                  RequestExecutor requestExecutor,
                                  Class<T> aClass) throws IOException {
        String response = request(url, args, requestExecutor);
        return parse(response, aClass);
    }

    protected List<T> parse(String response, Class<T> aClass) throws IOException {
        if (jsonKey == null) {
            return Json.parseJsonArray(response, aClass);
        } else {
            return Json.readList(response, jsonKey, aClass);
        }
    }

    protected String request(String url, Map<String, Object> args, RequestExecutor requestExecutor) throws IOException {
        return requestExecutor.executeRequest(url, args);
    }

    protected final Map<String, Object> getArgs() {
        return args;
    }

    public String getJsonKey() {
        return jsonKey;
    }

    @Override
    protected boolean shouldAddElement(T element) {
        return super.shouldAddElement(element);
    }

    @Override
    protected KeyProvider<Object, T> getKeyProvider() {
        return null;
    }
}
