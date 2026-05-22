package com.itheima.agent.pojo;

/**
 * 跨线程传递 memoryId — InheritableThreadLocal 保证子线程（工具调用）也能读到。
 */
public class MemoryIdContext {
    private static final InheritableThreadLocal<String> CTX = new InheritableThreadLocal<>();

    public static void set(String id) { CTX.set(id); }
    public static String get() { return CTX.get(); }
    public static void clear() { CTX.remove(); }
}
