package com.name.flashlight.integration.toaster;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * author : Android 轮子哥
 * github : https://github.com/getActivity/Toaster
 * time   : 2021/11/13
 * desc   : 通知服务代理对象
 */
final class NotificationServiceProxy implements InvocationHandler {

    /**
     * 被代理的对象
     */
    private final Object mRealObject;

    public NotificationServiceProxy(Object realObject) {
        mRealObject = realObject;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final String methodName = method.getName();
        if (Objects.equals(methodName, ToastConstants.ENQUEUE_TOAST)
                || Objects.equals(methodName, ToastConstants.ENQUEUE_TOAST_EX)
                || Objects.equals(methodName, ToastConstants.CANCEL_TOAST)
        ) {
            // 将包名修改成系统包名，这样就可以绕过系统的拦截
            // 部分华为机将 enqueueToast 方法名修改成了 enqueueToastEx
            args[0] = ToastConstants.TAG_ANDROID;
        }
        // 使用动态代理
        return method.invoke(mRealObject, args);
    }
}