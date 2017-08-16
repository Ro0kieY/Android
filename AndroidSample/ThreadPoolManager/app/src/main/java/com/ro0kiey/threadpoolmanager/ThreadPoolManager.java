package com.ro0kiey.threadpoolmanager;

import android.util.Log;

import java.util.Collection;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Ro0kieY on 2017/8/14.
 */

public class ThreadPoolManager {

    private static HashMap<String, ThreadPoolManager> sThreadPoolManagerHashMap = new HashMap<String, ThreadPoolManager>();

    private final static int DEFAULT_CORE_POOL_SIZE = 4;
    private final static int DEFAULT_MAX_POOL_SIZE = 4;
    private final static long DEFAULT_KEEP_ALIVE_TIME = 0;
    private final static TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;
    private static final String TAG = "Thread";

    public final static int HIGH_PRIORITY = 2;
    public final static int LOW_PRIORITY = 1;

    private static ScheduledExecutorService sScheduledExecutorService;
    private static ScheduledRunnable sScheduledRunnable;

    private ThreadPoolExecutor mWorkThreadPool;
    private RejectedExecutionHandler mRejectedExecutionHandler;
    private Queue<Runnable> mWaitTaskQueue;
    private Object mLock = new Object();
    private String mName;

    private ThreadPoolManager() {
        this(DEFAULT_CORE_POOL_SIZE, DEFAULT_MAX_POOL_SIZE, DEFAULT_KEEP_ALIVE_TIME, DEFAULT_TIME_UNIT, false);
    }

    private ThreadPoolManager(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, boolean isPriority){
        mWaitTaskQueue = new ConcurrentLinkedQueue<Runnable>();
        if (sScheduledRunnable == null){
            sScheduledRunnable = new ScheduledRunnable();
            sScheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            sScheduledExecutorService.scheduleAtFixedRate(sScheduledRunnable, 0, 5000, TimeUnit.MILLISECONDS);
        }
        BlockingQueue<Runnable> mWorkQueue = isPriority
                ? new PriorityBlockingQueue<Runnable>(16)
                : new LinkedBlockingQueue<Runnable>(16);
        initRejectedExecutionHandler();
        mWorkThreadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, mWorkQueue, mRejectedExecutionHandler);
    }

    private void initRejectedExecutionHandler() {
        mRejectedExecutionHandler = new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                //把被拒绝的任务重新放回队列
                synchronized (mLock){
                    mWaitTaskQueue.offer(r);
                }
                Log.d(TAG, "拒绝执行任务，任务将被放回队列。");
            }
        };
    }

    public static ThreadPoolManager buildInstance(String threadPoolManagerName){
        return buildInstance(DEFAULT_CORE_POOL_SIZE, DEFAULT_MAX_POOL_SIZE, DEFAULT_KEEP_ALIVE_TIME, DEFAULT_TIME_UNIT, false, threadPoolManagerName);
    }

    public static ThreadPoolManager buildInstance(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, boolean isPriority){
        return buildInstance(corePoolSize, maximumPoolSize, keepAliveTime, unit, isPriority, null);
    }

    public static ThreadPoolManager buildInstance(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, boolean isPriority, String threadPoolManagerName){
        if (corePoolSize < 0 || maximumPoolSize <= 0 || keepAliveTime < 0 || corePoolSize > maximumPoolSize){
            return null;
        } else {
            ThreadPoolManager threadPoolManager = new ThreadPoolManager(corePoolSize, maximumPoolSize, keepAliveTime, unit, isPriority);
            threadPoolManager.mName = threadPoolManagerName;
            synchronized (sThreadPoolManagerHashMap){
                sThreadPoolManagerHashMap.put(threadPoolManagerName, threadPoolManager);
            }
            return threadPoolManager;
        }
    }

    public static ThreadPoolManager getInstance(String threadPoolManagerName){
        ThreadPoolManager threadPoolManager = null;
        if (!"".equals(threadPoolManagerName.trim())){
            synchronized (sThreadPoolManagerHashMap){
                threadPoolManager = sThreadPoolManagerHashMap.get(threadPoolManagerName);
                if (threadPoolManager == null){
                    threadPoolManager = new ThreadPoolManager();
                    threadPoolManager.mName = threadPoolManagerName;
                    sThreadPoolManagerHashMap.put(threadPoolManagerName, threadPoolManager);
                }
            }
        }
        return threadPoolManager;
    }

    public void execute(Runnable r){
        if (r != null){
            mWorkThreadPool.execute(r);
        }
    }

    public void cancel(Runnable r){
        if (r != null){
            synchronized (mLock){
                if (mWaitTaskQueue.contains(r)){
                    mWaitTaskQueue.remove(r);
                }
            }
        }
        mWorkThreadPool.remove(r);
    }

    public void clean(){
        if (!mWorkThreadPool.isShutdown()){
            mWorkThreadPool.shutdownNow();
        }
        mRejectedExecutionHandler = null;
        synchronized (mLock){
            mWaitTaskQueue.clear();
        }
    }

    public static void destroy(String threadPoolManagerName){
        synchronized (sThreadPoolManagerHashMap){
            ThreadPoolManager manager = sThreadPoolManagerHashMap.get(threadPoolManagerName);
            if (manager != null){
                manager.clean();
                sThreadPoolManagerHashMap.remove(threadPoolManagerName);
            }
            Collection<ThreadPoolManager> values = sThreadPoolManagerHashMap.values();
            for (ThreadPoolManager m : values){
                Log.d(TAG, "HashMap中的线程池： " + m.mName);
            }
        }
    }

    private static class ScheduledRunnable implements Runnable {
        @Override
        public void run() {
            synchronized (sThreadPoolManagerHashMap){
                for (ThreadPoolManager manager : sThreadPoolManagerHashMap.values()){
                    manager.executeWaitTask(manager);
                }
            }
        }
    }

    private void executeWaitTask(ThreadPoolManager manager) {
        if (!mWaitTaskQueue.isEmpty()){
            Runnable r;
            synchronized (mLock){
                r = mWaitTaskQueue.poll();
            }
            manager.execute(r);
        }
    }
}
