package com.ethtether;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class EthTether {
    // TETHERING_ETHERNET = 5
    private static final int TETHERING_ETHERNET = 5;

    public static void main(String[] args) {
        System.out.println("EthTether: start");
        try {
            // 获取 system context
            Class<?> at = Class.forName("android.app.ActivityThread");
            Context ctx = (Context) at.getMethod("currentApplication").invoke(null);
            if (ctx == null) {
                Object th = at.getMethod("currentActivityThread").invoke(null);
                ctx = (Context) at.getMethod("getSystemContext").invoke(th);
            }
            if (ctx == null) {
                System.err.println("EthTether: no context");
                System.exit(1);
            }

            ConnectivityManager cm = (ConnectivityManager)
                    ctx.getSystemService(Context.CONNECTIVITY_SERVICE);

            // 动态代理 OnStartTetheringCallback
            Class<?> cbClass = Class.forName(
                    "android.net.ConnectivityManager$OnStartTetheringCallback");
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean ok = new AtomicBoolean(false);

            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    cbClass.getClassLoader(),
                    new Class<?>[]{cbClass},
                    (proxy, method, margs) -> {
                        System.out.println("EthTether: cb " + method.getName());
                        if ("onTetheringStarted".equals(method.getName())) {
                            ok.set(true);
                        }
                        latch.countDown();
                        return null;
                    });

            // 查找 startTethering(type, showUi, callback, handler)
            Method m = null;
            for (Method mm : cm.getClass().getDeclaredMethods()) {
                if ("startTethering".equals(mm.getName())
                        && mm.getParameterTypes().length == 4) {
                    m = mm;
                    break;
                }
            }
            if (m == null) {
                System.err.println("EthTether: startTethering not found");
                System.exit(2);
            }

            // 调用
            Handler handler = new Handler(Looper.getMainLooper());
            m.invoke(cm, TETHERING_ETHERNET, false, callback, handler);

            // 等待回调
            latch.await(10, TimeUnit.SECONDS);

            System.out.println(ok.get() ? "RESULT:OK" : "RESULT:NO_CALLBACK");
            System.exit(0);

        } catch (Throwable e) {
            System.err.println("EthTether: ERR " + e);
            e.printStackTrace();
            System.exit(3);
        }
    }
}
