package baimo.minecraft.plugins.authshield.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PerformanceUtils {
    private static final Logger LOGGER = LogManager.getLogger("authshield");
    
    // 创建自定义的线程工厂
    private static class AuthShieldThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        AuthShieldThreadFactory(String poolName) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            namePrefix = "authshield-" + poolName + "-thread-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    // 密码哈希计算线程池
    private static final ExecutorService hashExecutor = Executors.newFixedThreadPool(
        2, new AuthShieldThreadFactory("hash"));
        
    // 异步IO操作线程池
    private static final ExecutorService ioExecutor = Executors.newCachedThreadPool(
        new AuthShieldThreadFactory("io"));

    public static ExecutorService getHashExecutor() {
        return hashExecutor;
    }

    public static ExecutorService getIoExecutor() {
        return ioExecutor;
    }

    // 优雅关闭线程池
    public static void shutdownExecutors() {
        LOGGER.info("Shutting down AuthShield thread pools...");
        
        shutdownExecutor("Hash", hashExecutor);
        shutdownExecutor("IO", ioExecutor);
    }
    
    private static void shutdownExecutor(String name, ExecutorService executor) {
        try {
            LOGGER.debug("Attempting to shutdown {} executor", name);
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("{} executor did not terminate in time, forcing shutdown", name);
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.error("{} executor did not terminate", name);
                }
            }
            LOGGER.debug("{} executor shutdown successfully", name);
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while shutting down {} executor", name);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
} 