package com.fsck.k9.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import com.fsck.k9.K9;

public abstract class CoreService extends Service
{

    protected static void addWakeLockId(Intent i, Integer wakeLockId)
    {
        if (wakeLockId != null)
        {
            i.putExtra(BootReceiver.WAKE_LOCK_ID, wakeLockId);
        }
    }

    @Override
    public void onStart(Intent intent, int startId)
    {

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "K9");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(K9.MAIL_SERVICE_WAKE_LOCK_TIMEOUT);

        Log.i(K9.LOG_TAG, "CoreService: " + this.getClass().getName() + ".onStart(" + intent + ", " + startId);

        int wakeLockId = intent.getIntExtra(BootReceiver.WAKE_LOCK_ID, -1);
        if (wakeLockId != -1)
        {
            BootReceiver.releaseWakeLock(this, wakeLockId);
        }

        try
        {
            super.onStart(intent, startId);
            startService(intent, startId);
        }
        finally
        {
            if (wakeLock != null)
            {
                wakeLock.release();
            }
        }

    }


    public abstract void startService(Intent intent, int startId);

    @Override
    public IBinder onBind(Intent arg0)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onDestroy()
    {
        Log.i(K9.LOG_TAG, "CoreService: " + this.getClass().getName() + ".onDestroy()");
        super.onDestroy();
        //     MessagingController.getInstance(getApplication()).removeListener(mListener);
    }
}