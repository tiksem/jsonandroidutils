package com.utilsframework.android.json;

import com.jsonutils.ExceptionInfo;

import java.io.IOException;

/**
 * Created by CM on 12/26/2014.
 */
public interface OnRequestError {
    void onError(IOException e, ExceptionInfo info);
}
