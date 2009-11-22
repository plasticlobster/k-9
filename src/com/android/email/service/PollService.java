package com.android.email.service;

import java.util.HashMap;

import com.android.email.Account;
import com.android.email.Email;
import com.android.email.MessagingController;
import com.android.email.MessagingListener;
import com.android.email.Preferences;
import com.android.email.mail.MessagingException;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

public class PollService extends CoreService
{
    private static String START_SERVICE = "com.android.email.service.PollService.startService";
    private static String STOP_SERVICE = "com.android.email.service.PollService.stopService";

    private Listener mListener = new Listener();
 
    public static void startService(Context context) {
        Intent i = new Intent();
        i.setClass(context, PollService.class);
        i.setAction(PollService.START_SERVICE);
        context.startService(i);
    }
    
    public static void stopService(Context context) {
        Intent i = new Intent();
        i.setClass(context, PollService.class);
        i.setAction(PollService.STOP_SERVICE);
        context.startService(i);
    }
    
    @Override
    public void startService(Intent intent, int startId)
    {
        if (START_SERVICE.equals(intent.getAction())) 
        {
            Log.i(Email.LOG_TAG, "PollService started with startId = " + startId);
            
            MessagingController controller = MessagingController.getInstance(getApplication());
            Listener listener = (Listener)controller.getCheckMailListener();
            if (listener == null)
            {
              MessagingController.getInstance(getApplication()).log("***** PollService *****: starting new check");
              mListener.setStartId(startId);
              mListener.wakeLockAcquire();
              controller.setCheckMailListener(mListener);
              controller.checkMail(this, null, false, false, mListener);
            }
            else
            {
              MessagingController.getInstance(getApplication()).log("***** PollService *****: renewing WakeLock");
              listener.setStartId(startId);
              listener.wakeLockAcquire();
            }
        }
        else if (STOP_SERVICE.equals(intent.getAction()))
        {
            Log.i(Email.LOG_TAG, "PollService stopping");
            stopSelf();
        }
        
    }
    
    @Override
    public IBinder onBind(Intent arg0)
    {
        return null;
    }
    
    class Listener extends MessagingListener {
        HashMap<String, Integer> accountsChecked = new HashMap<String, Integer>();
        private WakeLock wakeLock = null;
        private int startId = -1;

        // wakelock strategy is to be very conservative.  If there is any reason to release, then release
        // don't want to take the chance of running wild
        public synchronized void wakeLockAcquire()
        {
          WakeLock oldWakeLock = wakeLock;

            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Email");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(Email.WAKE_LOCK_TIMEOUT);

            if (oldWakeLock != null)
            {
              oldWakeLock.release();
            }

        }
        public synchronized void wakeLockRelease()
        {
            if (wakeLock != null)
            {
                wakeLock.release();
                wakeLock = null;
            }
        }
        @Override
        public void checkMailStarted(Context context, Account account) {
            accountsChecked.clear();
        }

        @Override
        public void checkMailFailed(Context context, Account account, String reason) {
            release();
        }

        @Override
        public void synchronizeMailboxFinished(
                Account account,
                String folder,
                int totalMessagesInMailbox,
                int numNewMessages) {
            if (account.isNotifyNewMail()) {
              Integer existingNewMessages = accountsChecked.get(account.getUuid());
              if (existingNewMessages == null)
              {
                existingNewMessages = 0;
              }
              accountsChecked.put(account.getUuid(), existingNewMessages + numNewMessages);
            }
        }

        private void checkMailDone(Context context, Account doNotUseaccount)
        {
            if (accountsChecked.isEmpty())
            {
                return;
            }

            for (Account thisAccount : Preferences.getPreferences(context).getAccounts()) {
                Integer newMailCount = accountsChecked.get(thisAccount.getUuid());
                if (newMailCount != null)
                {
                    try
                    {
                        int  unreadMessageCount = thisAccount.getUnreadMessageCount(context, getApplication());
                        MessagingController.getInstance(getApplication()).notifyAccount(context, thisAccount, 
                                newMailCount, unreadMessageCount);
                        
                    }
                    catch (MessagingException me)
                    {
                        Log.e(Email.LOG_TAG, "***** PollService *****: couldn't get unread count for account " +
                            thisAccount.getDescription(), me);
                    }
                }
            }//for accounts
        }//checkMailDone
        
        
        private void release()
        {
          MessagingController controller = MessagingController.getInstance(getApplication());
          controller.setCheckMailListener(null);
          MailService.rescheduleCheck(PollService.this, null);
          wakeLockRelease();
          Log.i(Email.LOG_TAG, "PollService stopping with startId = " + startId);
          
          stopSelf(startId);
        }

        @Override
        public void checkMailFinished(Context context, Account account) {

            Log.v(Email.LOG_TAG, "***** PollService *****: checkMailFinished");
            try
            {
                checkMailDone(context, account);
            }
            finally
            {
              release();
            }
        }
        public int getStartId()
        {
            return startId;
        }
        public void setStartId(int startId)
        {
            this.startId = startId;
        }
    }
    
}