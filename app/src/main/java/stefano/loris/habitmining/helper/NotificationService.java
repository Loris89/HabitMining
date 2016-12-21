package stefano.loris.habitmining.helper;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import stefano.loris.habitmining.R;

public class NotificationService extends Service {

    private static final String TAG = NotificationService.class.getSimpleName();

    private static final int NOTIFICATION_ID = 001;

    private static Handler timerHandler = new Handler();

    private static final int INTERVAL = 1200000; // debug: 1 minuto = 60000 | 20 minuti = 1200000

    public void onCreate() {
        Log.d(TAG, "Servizio avviato");

        // avvia il timer
        startTimer();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Servizio distrutto");
        stopTimer();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private Runnable my_runnable = new Runnable() {
        @Override
        public void run() {
            SharedPreferences sharedPreferences = getSharedPreferences("NOTIFY", MODE_PRIVATE);
            if(sharedPreferences.getBoolean("NOTIFY_VAL", false) == true) { // notifiche abilitate
                if(sharedPreferences.getBoolean("ACTIVITIES_VAL", false) == true) { // ci sono attività
                    Log.d(TAG, "Notifica ricevuta");
                    // send a notification to the user
                    NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(NotificationService.this);
                    mBuilder.setSmallIcon(R.drawable.notification_icon);
                    mBuilder.setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_LIGHTS| Notification.DEFAULT_VIBRATE);
                    mBuilder.setContentTitle("E' da un po che non utilizzi l'app!");
                    NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    // notificationID allows you to update the notification later on.
                    mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
                } else {
                    Log.d(TAG, "Timer chiamato ma non ci sono attività in corso");
                }
            } else {
                Log.d(TAG, "Timer chiamato ma notifiche non abilitate");
            }

            startTimer(); // avvia di nuovo il timer
        }
    };

    private void startTimer() {
        timerHandler.postDelayed(my_runnable, INTERVAL);
    }

    private void stopTimer() {
        timerHandler.removeCallbacks(my_runnable);
    }
}
