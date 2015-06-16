package com.utilsframework.android.json;

import android.os.Handler;
import android.util.Log;
import com.example.json.BuildConfig;
import com.jsonutils.ExceptionInfo;
import com.jsonutils.Json;
import com.jsonutils.RequestException;
import com.utils.framework.OnError;
import com.utils.framework.collections.NavigationList;
import com.utils.framework.collections.OnLoadingFinished;
import com.utils.framework.collections.cache.LruCache;
import com.utils.framework.io.Network;
import com.utilsframework.android.ExecuteTimeLogger;
import com.utilsframework.android.IOErrorListener;
import com.utilsframework.android.threading.OnFinish;
import com.utilsframework.android.threading.Threading;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

/**
 * Created by CM on 12/21/2014.
 */
public class JsonHttpClient {
    private static final String TAG = "JsonHttpClient";
    private static final boolean LOG_JSON = BuildConfig.DEBUG;

    private LruCache<String, CacheResult> cache = new LruCache<String, CacheResult>(5 * 1024 * 1024);
    private long defaultCachingTime = 60 * 1000;
    private int defaultEntitySize = 500;
    private HttpClient httpClient;
    private IOErrorListener ioErrorListener;
    private Handler handler = new Handler();
    private boolean binaryServerResponse = false;

    public JsonHttpClient() {
        cache = new LruCache<String, CacheResult>(5 * 1024 * 1024){
            @Override
            public int sizeOf(String key, CacheResult value) {
                int result = 64;
                result += key.length() * 2.5 + 32;
                int entitySize = getEntitySize(value.aClass);
                if(entitySize < 0){
                    entitySize = defaultEntitySize;
                }

                if(value.value instanceof Collection){
                    result += 64;
                    result += entitySize * ((Collection) value.value).size();
                } else {
                    result += entitySize;
                }

                return result;
            }
        };

        httpClient = new DefaultHttpClient();
    }

    public long getDefaultCachingTime() {
        return defaultCachingTime;
    }

    public void setDefaultCachingTime(long defaultCachingTime) {
        this.defaultCachingTime = defaultCachingTime;
    }

    public int getDefaultEntitySize() {
        return defaultEntitySize;
    }

    public void setDefaultEntitySize(int defaultEntitySize) {
        this.defaultEntitySize = defaultEntitySize;
    }

    protected int getEntitySize(Class aClass) {
        return -1;
    }

    private void addDeleteFromCacheCallback(long cachingTime, CacheResult cacheResult, final String finalUrl) {
        long time = cachingTime;
        if(time < 0){
            time = getDefaultCachingTime();
        }

        Runnable deleteCallback = cacheResult.deleteCallback = new Runnable() {
            @Override
            public void run() {
                cache.remove(finalUrl);
            }
        };
        handler.postDelayed(deleteCallback, time);
    }

    public <T> void getList(GetListParams<T> params) {
        get(params.url, params.params, params.aClass, params.key, params.cachingTime,
                params.onFinish, params.onSuccess, params.onError, true);
    }

    public <T> void get(GetParams<T> params) {
        get(params.url, params.params, params.aClass, null,
                params.cachingTime, params.onFinish, params.onSuccess, params.onError, false);
    }

    public <T> List<T> getListSync(String url, Map<String, Object> params, String key,
                                   Class<T> aClass) throws IOException {
        String json = Network.executeGetRequest(httpClient, url, params);
        return Json.readList(json, key, aClass);
    }

    private void get(String url,
                            SortedMap<String, Object> params,
                            final Class aClass,
                            final String key,
                            long cachingTime,
                            final OnFinished onFinish,
                            final OnSuccess onSuccess,
                            final OnRequestError onError,
                            final boolean list) {
        try {
            url = Network.getUrl(url, params);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final String finalUrl = url;
        CacheResult cacheResult = cache.get(url);
        if(cachingTime < 0){
            cachingTime = getDefaultCachingTime();
        }

        if(cacheResult != null){
            if (onSuccess != null) {
                onSuccess.onSuccess(cacheResult.value);
            }
            if (onFinish != null) {
                onFinish.onFinished();
            }

            if (cachingTime > 0) {
                handler.removeCallbacks(cacheResult.deleteCallback);
                addDeleteFromCacheCallback(cachingTime, cacheResult, finalUrl);
            } else {
                cache.remove(finalUrl);
            }

            return;
        }

        final long finalCachingTime = cachingTime;
        Threading.getResultAsync(new Threading.ResultProvider<Object>() {
            @Override
            public Object get() {
                try {
                    ExecuteTimeLogger.timeStart("request " + finalUrl);
                    String json = Network.executeGetRequest(httpClient, finalUrl);
                    ExecuteTimeLogger.timeEnd("request " + finalUrl);

                    if(LOG_JSON){
                        try {
                            Log.i(TAG, finalUrl + ":\n" + new JSONObject(json).toString(1));
                        } catch (JSONException e) {

                        }
                    }

                    ExecuteTimeLogger.timeStart("parse " + finalUrl);
                    Object result;
                    if (!list) {
                        result = Json.read(json, aClass);
                    } else {
                        result = Json.readList(json, key, aClass);
                    }
                    return result;
                } catch (IOException e) {
                    executeIoErrorListener(e);
                    return e;
                } finally {
                    ExecuteTimeLogger.timeEnd("parse " + finalUrl);
                }
            }
        }, new OnFinish<Object>() {
            @Override
            public void onFinish(Object result) {
                if(result instanceof IOException){
                    if (onError != null) {
                        IOException exception = (IOException) result;
                        ExceptionInfo info = null;
                        if(result instanceof RequestException){
                            RequestException requestException = (RequestException) exception;
                            info = requestException.getExceptionInfo();
                        }

                        onError.onError(exception, info);
                    }
                } else {
                    if (finalCachingTime != 0) {
                        long cachingTime = finalCachingTime;
                        if(cachingTime < 0){
                            cachingTime = getDefaultCachingTime();
                        }

                        CacheResult cacheResult = new CacheResult();
                        cacheResult.value = result;
                        cacheResult.aClass = aClass;

                        addDeleteFromCacheCallback(cachingTime, cacheResult, finalUrl);
                        cache.put(finalUrl, cacheResult);
                    }

                    if(onSuccess != null){
                        onSuccess.onSuccess(result);
                    }
                }

                if (onFinish != null) {
                    onFinish.onFinished();
                }
            }
        });
    }

    private SortedMap<String, Object> getLimitOffsetMap(long offset, long limit) {
        SortedMap<String, Object> map = new TreeMap<String, Object>();
        map.put("offset", offset);
        map.put("limit", limit);
        return map;
    }

    public <T> NavigationList<T> getNavigationList(final GetNavigationListParams<T> params) {
        return new NavigationList<T>(500) {
            @Override
            public void getElementsOfPage(int pageNumber,
                                             final OnLoadingFinished<T> onPageLoadingFinished, final OnError onError) {
                long offset = params.offset + params.limit * pageNumber;
                SortedMap<String, Object> urlParams = getLimitOffsetMap(offset, params.limit);
                if(params.params != null){
                    urlParams.putAll(params.params);
                }

                JsonHttpClient.this.get(params.url, urlParams, params.aClass,
                        params.key, params.cachingTime, null, new OnSuccess() {
                            @Override
                            public void onSuccess(Object result) {
                                List<T> list = (List<T>) result;
                                onPageLoadingFinished.onLoadingFinished(list, list.size() < params.limit);
                            }
                        }, new OnRequestError() {
                            @Override
                            public void onError(IOException e, ExceptionInfo info) {
                                if(params.onError != null){
                                    params.onError.onError(e, info);
                                    onError.onError(e);
                                }
                            }
                        }, true);
            }

            @Override
            protected boolean shouldAddElement(T element) {
                if (params.addElementPredicate == null) {
                    return super.shouldAddElement(element);
                } else {
                    return params.addElementPredicate.check(element);
                }
            }
        };
    }

    public void removeFromCache(String url, SortedMap<String, Object> params) {
        try {
            url = Network.getUrl(url, params);
            cache.remove(url);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeFromCache(String url, Map<String, Object> params, int offset, int limit) {
        SortedMap<String, Object> urlParams = getLimitOffsetMap(offset, limit);
        urlParams.putAll(params);
        removeFromCache(url, urlParams);
    }

    private void executeIoErrorListener(final IOException e) {
        if (ioErrorListener != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    ioErrorListener.onIOError(e);
                }
            });
        }
        Log.e("JsonHttpRequest", "", e);
    }

    public boolean isBinaryServerResponse() {
        return binaryServerResponse;
    }

    public void setBinaryServerResponse(boolean binaryServerResponse) {
        this.binaryServerResponse = binaryServerResponse;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public IOErrorListener getIoErrorListener() {
        return ioErrorListener;
    }

    public void setIoErrorListener(IOErrorListener ioErrorListener) {
        this.ioErrorListener = ioErrorListener;
    }
}
