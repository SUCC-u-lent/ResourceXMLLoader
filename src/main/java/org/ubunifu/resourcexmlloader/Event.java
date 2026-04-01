package org.ubunifu.resourcexmlloader;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Event
{
    private Event(){}
    public static class ParameterizedEvent<T> {
        ParameterizedEvent(){}
        List<Consumer<T>> listeners = new ArrayList<>();
        public void addListener(Consumer<T> listener)
        {
            listeners.add(listener);
        }
        void invokeEvent(@Nullable T param)
        {
            this.listeners.forEach(l->l.accept(param));
        }
    }
    public static class ParameterlessEvent {
        ParameterlessEvent(){}
        List<Runnable> listeners = new ArrayList<>();
        public void addListener(Runnable listener)
        {
            listeners.add(listener);
        }
        void invokeEvent()
        {
            this.listeners.forEach(Runnable::run);
        }
    }
}
