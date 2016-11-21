package com.example.gerard.babycam;

import android.app.IntentService;
import android.content.Intent;

/**
 * Created by gerard on 14/07/2016.
 */
public class ServerIntentService extends IntentService {
    public ServerIntentService() {
        super("ServerIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        MyServer server = new MyServer();
    }
}
