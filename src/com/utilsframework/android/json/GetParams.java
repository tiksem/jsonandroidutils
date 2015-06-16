package com.utilsframework.android.json;

import com.utilsframework.android.threading.OnFinish;

import java.util.SortedMap;

/**
 * Created by CM on 12/21/2014.
 */
public class GetParams<T> {
    public String url;
    public SortedMap<String, Object> params;
    public Class<T> aClass;
    public long cachingTime = 0;
    public OnFinished onFinish;
    public OnSuccess<T> onSuccess;
    public OnRequestError onError;

    public static <T> GetParams<T> create(Class<T> tClass) {
        GetParams<T> result = new GetParams<T>();
        result.aClass = tClass;
        return result;
    }
}
