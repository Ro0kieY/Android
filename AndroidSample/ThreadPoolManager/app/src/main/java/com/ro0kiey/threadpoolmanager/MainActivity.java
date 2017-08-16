package com.ro0kiey.threadpoolmanager;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Thread";
    private ThreadPoolManager manager, priorityManager;
    private Button btn, btn1 ,btn2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn = (Button)findViewById(R.id.btn);
        btn1 = (Button)findViewById(R.id.btn1);
        btn2 = (Button)findViewById(R.id.btn2);
        manager = ThreadPoolManager.buildInstance("mainThreadPoolManager");
        priorityManager = ThreadPoolManager.buildInstance(1, 1, 0, TimeUnit.SECONDS, true, "priorityThreadPoolManager");
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < 21; i++){
                    MyRunnable r = new MyRunnable(i);
                    manager.execute(r);
                }
            }
        });

        btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < 4; i++){
                    MyRunnable r = null;
                    if (i > 1){
                        r = new MyRunnable(i, ThreadPoolManager.HIGH_PRIORITY);
                    } else {
                        r = new MyRunnable(i, ThreadPoolManager.LOW_PRIORITY);
                    }
                    priorityManager.execute(r);
                }
            }
        });

        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ThreadPoolManager.destroy("mainThreadPoolManager");
            }
        });
    }

    private class MyRunnable implements Runnable, Comparable {

        private int index;
        private int priority;

        public MyRunnable(int index) {
            this.index = index;
            this.priority = ThreadPoolManager.HIGH_PRIORITY;
        }

        public MyRunnable(int index, int priority) {
            this.index = index;
            this.priority = priority;
        }

        @Override
        public void run() {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            try {
                Log.d(TAG, "第" + index + "个Runnable的优先级是： " + this.priority);
                Log.d(TAG, "第" + index + "个Runnable开始运行于" + df.format(new Date()));
                Thread.sleep(3000);
                Log.d(TAG, "第" + index + "个Runnable结束运行于" + df.format(new Date()));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public int compareTo(@NonNull Object o) {
            MyRunnable r = (MyRunnable) o;
            return this.priority > r.priority ? -1 : 1;
        }
    }
}
