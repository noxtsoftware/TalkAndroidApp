package com.moyn.talk.jobs;

import android.content.Context;
import androidx.annotation.NonNull;
import android.util.Log;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class FirebaseWorker extends Worker {

    private static final String TAG = "MyWorker";

    public FirebaseWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Performing long running task in scheduled job");
        return Result.success();
    }
}