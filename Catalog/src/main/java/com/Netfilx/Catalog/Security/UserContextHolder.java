package com.Netfilx.Catalog.Security;

public class UserContextHolder {

    // ThreadLocal ensures thread-safety. Each concurrent request gets its own isolated instance.
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_PLAN = new ThreadLocal<>();

    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }

    public static String getUserId() {
        return USER_ID.get();
    }

    public static void setUserPlan(String plan) {
        USER_PLAN.set(plan);
    }

    public static String getUserPlan() {
        return USER_PLAN.get();
    }

    // 🔥 CRITICAL FOR VIRTUAL THREADS 🔥
    // This must be called at the end of the request to prevent memory leaks and data bleeding.
    public static void clear() {
        USER_ID.remove();
        USER_PLAN.remove();
    }
}