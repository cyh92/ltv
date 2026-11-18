package com.github.catvod.utils;

import com.github.catvod.runtime.QuickJsRuntime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class Js implements AutoCloseable {

    private final Set<CompletableFuture<String>> pending = new HashSet<>();
    private boolean closed;

    // The QuickJS engine is bundled in the host APK (no runtime module).
    // catvod cannot compile against :quickjs -- that would be a project
    // cycle, since :quickjs already depends on :catvod -- so the entry
    // is loaded by name, the same way RuntimeModule.entry() used to do.
    private static volatile QuickJsRuntime embedded;

    private static QuickJsRuntime embeddedEntry() {
        QuickJsRuntime entry = embedded;
        if (entry == null) {
            synchronized (Js.class) {
                entry = embedded;
                if (entry == null) {
                    try {
                        entry = (QuickJsRuntime) Class.forName("com.fongmi.quickjs.RuntimeEntry").getConstructor().newInstance();
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Unable to load embedded QuickJS runtime", e);
                    }
                    embedded = entry;
                }
            }
        }
        return entry;
    }

    public String eval(String source) throws Exception {
        return eval(source, "{}");
    }

    public String eval(String source, String options) throws Exception {
        CompletableFuture<String> result = evalAsync(source, options);
        try {
            return result.get();
        } catch (InterruptedException error) {
            result.cancel(true);
            Thread.currentThread().interrupt();
            throw error;
        } catch (ExecutionException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            if (error.getCause() instanceof Error cause) throw cause;
            throw new IllegalStateException(error.getCause());
        }
    }

    public synchronized CompletableFuture<String> evalAsync(String source, String options) {
        if (closed) throw new IllegalStateException("Script owner closed");
        CompletableFuture<String> result = embeddedEntry().evaluate(source, options);
        pending.add(result);
        result.whenComplete((value, error) -> release(result));
        return result;
    }

    private synchronized void release(CompletableFuture<String> result) {
        pending.remove(result);
    }

    @Override
    public void close() {
        List<CompletableFuture<String>> active;
        synchronized (this) {
            if (closed) return;
            closed = true;
            active = new ArrayList<>(pending);
            pending.clear();
        }
        for (CompletableFuture<String> result : active) result.cancel(true);
    }
}
