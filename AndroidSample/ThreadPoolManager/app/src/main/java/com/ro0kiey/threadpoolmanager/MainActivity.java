package com.ro0kiey.threadpoolmanager;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.ro0kiey.threadpoolmanager.thread.ThreadPoolManager;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Thread";
    private ThreadPoolManager manager;
    private Button btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn = (Button)findViewById(R.id.btn);
        manager = ThreadPoolManager.buildInstance("mainThreadPoolManager");
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < 10; i++){
                    MyRunnable r = new MyRunnable(i);
                    manager.execute(r);
                }
            }
        });
    }

    private class MyRunnable implements Runnable {

        private int index;

        public MyRunnable(int index) {
            this.index = index;
        }

        @Override
        public void run() {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            try {
                Log.d(TAG, "第" + index + "个Runnable开始运行于" + df.format(new Date()));
                Thread.sleep(3000);
                Log.d(TAG, "第" + index + "个Runnable结束运行于" + df.format(new Date()));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
