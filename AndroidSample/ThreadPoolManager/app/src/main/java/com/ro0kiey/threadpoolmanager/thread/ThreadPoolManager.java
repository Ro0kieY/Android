package com.ro0kiey.threadpoolmanager.thread;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
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

    private ThreadPoolExecutor mWorkThreadPool;
    private RejectedExecutionHandler mRejectedExecutionHandler;
    private BlockingQueue<Runnable> mWorkQueue;
    private Object mLock = new Object();
    private String mName;

    private ThreadPoolManager() {
        this(DEFAULT_CORE_POOL_SIZE, DEFAULT_MAX_POOL_SIZE, DEFAULT_KEEP_ALIVE_TIME, DEFAULT_TIME_UNIT, false);
    }

    private ThreadPoolManager(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, boolean isPriority){
        mWorkQueue = isPriority
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
                    mWorkQueue.offer(r);
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
        if (corePoolSize < 0 || maximumPoolSize <= 0 || keepAliveTime < 0 || corePoolSize > maximumPoolSize || "".equals(threadPoolManagerName.trim())){
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
                if (mWorkQueue.contains(r)){
                    mWorkQueue.remove(r);
                }
            }
        }
        mWorkThreadPool.remove(r);
    }
}
