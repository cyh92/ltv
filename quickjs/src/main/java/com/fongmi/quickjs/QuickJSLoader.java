package com.fongmi.quickjs;

public final class QuickJSLoader {

    private QuickJSLoader() {
    }

    public static void init() {
        System.loadLibrary("quickjs-wrapper");
    }
}
