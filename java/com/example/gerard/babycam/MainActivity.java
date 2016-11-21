package com.example.gerard.babycam;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends AppCompatActivity {
    public static byte[] currentByteArray = "null".getBytes();
    public static int jpegQuality = 100;

    public static String SERVER_IP;
    public static int SERVER_PORT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        super.requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_main);
        if (null == savedInstanceState) {
            SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
            jpegQuality = sharedPref.getInt("videoquality", 100);

            getFragmentManager().beginTransaction()
                    .replace(R.id.container, Camera2BasicFragment.newInstance())
                    .commit();

            Intent mServiceIntent = new Intent(this, ServerIntentService.class);
            startService(mServiceIntent);
        }
    }
}