package com.smartplugins.test;

import com.smartannotations.RegisterService;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

@RegisterService
public class HelloAndroidService extends Service
{

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

}
