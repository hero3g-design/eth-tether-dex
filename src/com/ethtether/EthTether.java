package com.ethtether;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class EthTether {
    private static final int TETHERING_ETHERNET = 5;

    public static void main(String[] args) {
        System.out.println("EthTether: start");
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Context ctx = (Context) at.getMethod("currentApplication").invoke(null);
            if (ctx == null) {
                Object th = at.getMethod("currentActivityThread").invoke(null);
                ctx = (Context) at.getMethod("getSystemContext").invoke(th);
            }
            if (ctx == null) {
                System.err.println("no context");
                System.exit(1);
            }

            ConnectivityManager cm = (ConnectivityManager)
                    ctx.getSystemService(Context.CONNECTIVITY_SERVICE);

            Class<?> cbClass = Class.forName(
                    "android.net.ConnectivityManager$OnStartTetheringCallback");

            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean ok = new AtomicBoolean(false);

            Object callback = Proxy.newProxyInstance(
                    cbClass.getClassLoader(),
                    new Class<?>[]{cbClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] margs) {
                            System.out.println("cb:" + method.getName());
                            if ("onTetheringStarted".equals(method.getName())) {
                                ok.set(true);
                            }
                            latch.countDown();
                            return null;
                        }
                    });

            Method m = null;
            for (Method mm : cm.getClass().getDeclaredMethods()) {
                if ("startTethering".equals(mm.getName())
                        && mm.getParameterTypes().length == 4) {
                    m = mm;
                    break;
                }
            }
            if (m == null) {
                System.err.println("startTethering not found");
                System.exit(2);
            }

            m.invoke(cm, TETHERING_ETHERNET, false, callback,
                    new Handler(Looper.getMainLooper()));

            latch.await(10, TimeUnit.SECONDS);

            System.out.println(ok.get() ? "RESULT:OK" : "RESULT:NO_CALLBACK");
            System.exit(0);

        } catch (Throwable e) {
            System.err.println("ERR:" + e);
            e.printStackTrace();
            System.exit(3);
        }
    }
}
