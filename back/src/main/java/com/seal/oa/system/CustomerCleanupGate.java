package com.seal.oa.system;

import com.seal.oa.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/** Local deployment: drain in-flight HTTP writes before a cleanup transaction starts. */
@Component
public class CustomerCleanupGate implements HandlerInterceptor {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private static final String HELD = CustomerCleanupGate.class.getName();
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws InterruptedException {
        if (java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())
                || request.getRequestURI().startsWith("/system/customer-cleanup")) return true;
        if (!lock.readLock().tryLock(5, TimeUnit.SECONDS)) throw new ApiException(409, "正在清理客户数据，请稍后重试");
        request.setAttribute(HELD, true);
        return true;
    }
    @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception error) {
        if (Boolean.TRUE.equals(request.getAttribute(HELD))) {
            request.removeAttribute(HELD);
            lock.readLock().unlock();
        }
    }
    public <T> T exclusive(Supplier<T> work) {
        try {
            if (!lock.writeLock().tryLock(30, TimeUnit.SECONDS)) throw new ApiException(409, "仍有业务正在保存，请稍后重新预览并清理");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiException(409, "清理已中断，请重试"); }
        try { return work.get(); } finally { lock.writeLock().unlock(); }
    }
    public void backgroundWrite(Runnable work) {
        lock.readLock().lock();
        try { work.run(); } finally { lock.readLock().unlock(); }
    }
}
