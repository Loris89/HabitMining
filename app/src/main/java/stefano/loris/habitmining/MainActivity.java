package stefano.loris.habitmining;

import android.app.Activity;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import stefano.loris.habitmining.app.AppConfig;
import stefano.loris.habitmining.helper.NotificationService;
import stefano.loris.habitmining.utils.SessionManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final Long TIMER_TIME = 1200000L; // debug: 1 minuto = 60000L | 20 minuti = 1200000L

    private static final int NOTIFICATION_ID = 001;

    // DATI VISTA

    private Button avviaAttivita;

    private TextView imei;
    private TextView nessuna_attività;
    private TextView notificationStatus;
    private TextView alone_status;

    private CheckBox silenzia;

    private CheckBox alone_switch;

    private ProgressDialog pDialog;

    // DATI TELEFONO

    private String IMEI;
    private SessionManager session;

    // LIST ACTIVITY STUFF

    private ListView attivitaListView;
    private AttivitaAdapter adapter;
    private ArrayList<Attivita> listaAttivita;

    private boolean notificationsEnabled;
    private boolean alone;

    public static Handler my_handler = new Handler();

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        avviaAttivita = (Button)findViewById(R.id.btn_avvia_attività);
        imei = (TextView)findViewById(R.id.imei_txt);
        attivitaListView = (ListView)findViewById(R.id.lista_attivita);
        nessuna_attività = (TextView)findViewById(R.id.nessuna_attivita);
        notificationStatus = (TextView)findViewById(R.id.notification_status);
        silenzia = (CheckBox)findViewById(R.id.disable_sound);
        alone_status = (TextView)findViewById(R.id.alone_switch_status);
        alone_switch = (CheckBox)findViewById(R.id.alone_switch);

        notificationsEnabled = true;
        String mystring = getResources().getString(R.string.notification_status_on);
        notificationStatus.setText(mystring);

        // shared preferences per il servizio di notifica
        sharedPreferences = this.getSharedPreferences("NOTIFY", MODE_PRIVATE);
        updateNotifySharedPreferences(true); // all'inizio le notifiche sono abilitate

        silenzia.setChecked(true);

        // Session manager
        session = new SessionManager(getApplicationContext());
        IMEI = session.getIMEI();

        imei.append(IMEI);

        // Progress dialog
        pDialog = new ProgressDialog(this);
        pDialog.setCancelable(false);

        listaAttivita = new ArrayList<>(3);

        avviaAttivita.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // chiama activity delle attività e attendi l'attività scelta
                Intent intent = new Intent(MainActivity.this, ActionsActivity.class);
                startActivityForResult(intent, 0);
            }
        });

        attivitaListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // no action
            }
        });

        silenzia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(notificationsEnabled) {
                    silenzia.setChecked(false);
                    String mystring = getResources().getString(R.string.notification_status_off);
                    notificationStatus.setText(mystring);
                    notificationsEnabled = false;
                    updateNotifySharedPreferences(false);
                }
                else {
                    silenzia.setChecked(true);
                    String mystring = getResources().getString(R.string.notification_status_on);
                    notificationStatus.setText(mystring);
                    notificationsEnabled = true;
                    if(!listaAttivita.isEmpty()) {
                        updateNotifySharedPreferences(true);
                    }
                }
            }
        });

        alone_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(alone_switch.isChecked()) {
                    String msg = getResources().getString(R.string.alone_dialog_msg);
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage(msg)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    alone = true;
                                    update_alone_switch();
                                }})
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    alone = false;
                                    alone_switch.setChecked(false);
                                    update_alone_switch();
                                }}).show();
                }
                else {
                    String msg = getResources().getString(R.string.not_alone_dialog_msg);
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage(msg)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    alone = false;
                                    update_alone_switch();
                                }})
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    alone = true;
                                    alone_switch.setChecked(true);
                                    update_alone_switch();
                                }}).show();
                }
            }
        });

        adapter = new AttivitaAdapter(MainActivity.this, R.layout.main_activity_row, listaAttivita);
        attivitaListView.setAdapter(adapter);
    }

    private void update_alone_switch() {
        String mystring;
        if(alone_switch.isChecked()) {
            mystring = getResources().getString(R.string.alone);
        } else {
            mystring = getResources().getString(R.string.not_alone);
        }
        alone_status.setText(mystring);
    }

    private void updateNotifySharedPreferences(boolean notify) {
        Log.d(TAG, "notifiche " + notify);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("NOTIFY_VAL", notify);
        editor.commit();
    }

    private void updateActivitiesSharedPreferences(boolean activities) {
        Log.d(TAG, "attività " + activities);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("ACTIVITIES_VAL", activities);
        editor.commit();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Log.d(TAG, "onSaveInstanceState()");

        if(!listaAttivita.isEmpty()) { // ci sono delle attività in corso
            int size = listaAttivita.size();
            savedInstanceState.putInt("DIM", size);
            for(int i = 0; i < listaAttivita.size(); i++) {
                Log.d(TAG, "Attività " + i + " salvata");
                savedInstanceState.putParcelable(String.valueOf(i), listaAttivita.get(i));
            }
        }
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can restore the view hierarchy
        super.onRestoreInstanceState(savedInstanceState);

        Log.d(TAG, "onRestoreInstanceState()");

        // Check whether we're recreating a previously destroyed instance
        if (savedInstanceState != null) {
            int size = savedInstanceState.getInt("DIM");
            for(int i = 0; i < size; i++) {
                Log.d(TAG, "Attività " + i + " ricreata");
                Attivita attivita = savedInstanceState.getParcelable(String.valueOf(i));
                listaAttivita.add(attivita);
            }
            // aggiorna la lista
            adapter.notifyDataSetChanged();
            // make the status invisible
            nessuna_attività.setVisibility(View.INVISIBLE);
            // start the timer again
            // startTimer();
            startService(new Intent(this, NotificationService.class));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == 0) {
            if(resultCode == Activity.RESULT_OK){
                Log.d(TAG, "Activity started");
                // take data from called activity
                String attivitaScelta = data.getStringExtra("result");
                String startTime = data.getStringExtra("start");
                // create activity
                Attivita attivita = new Attivita(attivitaScelta, 0);
                attivita.setTimestampStart(startTime);
                Log.d(TAG, "Attività scelta: " + attivitaScelta);
                // aggiungi alla listview
                if(listaAttivita.contains(attivita)) {
                    Toast.makeText(MainActivity.this, "Attività già avviata", Toast.LENGTH_SHORT).show();
                } else {
                    listaAttivita.add(attivita);
                    adapter.notifyDataSetChanged();
                }
                nessuna_attività.setVisibility(View.INVISIBLE);
                // restart/start timer
                // startTimer();
                updateActivitiesSharedPreferences(true);
                stopService(new Intent(this, NotificationService.class));
                startService(new Intent(this, NotificationService.class));
            }
            if (resultCode == Activity.RESULT_CANCELED) {
                Log.d(TAG, "Scelta attività annullata");
            }
        }
    }

    private String getTime() {
        // formato datetime MySQL 'YYYY-MM-DD HH:MM:SS'
        GregorianCalendar gregorianCalendar = new GregorianCalendar();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        fmt.setCalendar(gregorianCalendar);
        //fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String dateFormatted = fmt.format(gregorianCalendar.getTime());
        Log.d(TAG, "Tempo preso: " + dateFormatted);
        return dateFormatted;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");

        startService(new Intent(this, NotificationService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        update_alone_switch();
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideDialog();
        Log.d(TAG, "onPause()");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop()");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart()");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        updateActivitiesSharedPreferences(false);
        updateNotifySharedPreferences(true);
    }

    private void showDialog(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pDialog.setMessage(message);
            }
        });
        if (!pDialog.isShowing())
            pDialog.show();
    }

    private void hideDialog() {
        if (pDialog.isShowing())
            pDialog.dismiss();
    }

    private class AttivitaAdapter extends ArrayAdapter<Attivita> {

        private List<Attivita> objects;

        public AttivitaAdapter(Context context, int resource, List<Attivita> objects) {
            super(context, resource, objects);
            this.objects = objects;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;

            if(convertView == null) {
                // inflate the GridView item layout
                LayoutInflater inflater = LayoutInflater.from(getContext());
                convertView = inflater.inflate(R.layout.main_activity_row, parent, false);

                // initialize the view holder
                viewHolder = new ViewHolder();
                viewHolder.name = (TextView) convertView.findViewById(R.id.nome_attivita);
                viewHolder.stop = (Button) convertView.findViewById(R.id.stop_button);
                viewHolder.cancel = (Button) convertView.findViewById(R.id.cancel_button);
                convertView.setTag(viewHolder);
            } else {
                // recycle the already inflated view
                viewHolder = (ViewHolder) convertView.getTag();
            }

            // update the item view
            final Attivita attivita = getItem(position);
            viewHolder.name.setText(attivita.getNome());

            Button stopAction = (Button)convertView.findViewById(R.id.stop_button);
            stopAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // inserisci attività
                    storeActivity(attivita, position);
                }
            });

            Button cancelAction = (Button)convertView.findViewById(R.id.cancel_button);
            cancelAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // rimuovi attività
                    objects.remove(position);
                    notifyDataSetChanged();

                    if(listaAttivita.size()==0) {
                        nessuna_attività.setVisibility(View.VISIBLE);
                        //stopTimer();
                        updateActivitiesSharedPreferences(false); // non ci sono più attività
                    }
                }
            });

            return convertView;
        }

        /**
         * The view holder design pattern prevents using findViewById()
         * repeatedly in the getView() method of the adapter.
         */
        private class ViewHolder {
            TextView name;
            Button stop;
            Button cancel;
        }
    }

    private void storeActivity(final Attivita attivita, final int position) {
        Log.d(TAG, "storeActivity()");

        String alones;
        if(alone) {
            alones = "alone";
        } else {
            alones = "not_alone";
        }

        Log.d(TAG, "Alone? " + alones + " " + alone);

        // should be a singleton
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        RequestBody formBody = new FormBody.Builder()
                    .add("imei", IMEI)
                    .add("timestamp_start", attivita.getTimestampStart())
                    .add("timestamp_stop", getTime())
                    .add("activity", attivita.getNome())
                    .add("alone", alones)
                    .build();

        Request request = new Request.Builder()
                .url(AppConfig.URL_STORE_ACTIVITY)
                .post(formBody)
                .build();

        showDialog("Inserting activity...");

        // Get a handler that can be used to post to the main thread
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                hideDialog();
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                hideDialog();
                if (!response.isSuccessful()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Error: " + response + "." + " Try again.", Toast.LENGTH_LONG).show();
                        }
                    });
                    throw new IOException("Unexpected code " + response);
                } else {
                    try {
                        String body = response.body().string();
                        Log.d(TAG, body);
                        JSONObject jObj = new JSONObject(body);
                        boolean error = jObj.getBoolean("error");

                        // Check for error node in json
                        if (!error) {
                            Log.d(TAG, "Success! Activity added");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // rimuovi attività
                                    listaAttivita.remove(position);
                                    adapter.notifyDataSetChanged();
                                    //setListViewHeightBasedOnItems(attivitaListView);
                                    if(listaAttivita.size()==0) {
                                        nessuna_attività.setVisibility(View.VISIBLE);
                                        //stopTimer();
                                        updateActivitiesSharedPreferences(false); // non ci sono più attività
                                    }
                                }
                            });
                        } else {
                            final String errorMsg = jObj.getString("error_msg");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "Error: " + errorMsg + "." + " Try again.", Toast.LENGTH_LONG).show();
                                }
                            });
                            Log.d(TAG, errorMsg + " error = true in result");
                        }
                    } catch (JSONException e) {
                        // JSON error
                        final String msg = e.getMessage();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Error: " + msg + "." + " Try again.", Toast.LENGTH_LONG).show();
                            }
                        });
                        Log.d(TAG, e.getMessage() + " JSON CATCH");
                    }
                }
            }
        });
    }

    // INTERNAL NOTIFICATION SYSTEM

    // Define the code block to be executed
    private Runnable my_runnable = new Runnable() {
        @Override
        public void run() {
            // Do something here on the main thread
            // send a notification to the user
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(MainActivity.this);
            mBuilder.setSmallIcon(R.drawable.notification_icon);
            mBuilder.setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_LIGHTS| Notification.DEFAULT_VIBRATE);
            mBuilder.setContentTitle("E' da un po che non utilizzi l'app!");
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            // notificationID allows you to update the notification later on.
            mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
            Log.d("Handlers", "Called on main thread");

            if(!listaAttivita.isEmpty()) {// ci sono delle attività in corso
                //startTimer();
            }
        }
    };

    public void startTimer() {
        if(notificationsEnabled) {
            Log.d(TAG, "timer started");
            my_handler.removeCallbacks(my_runnable);
            my_handler.postDelayed(my_runnable, TIMER_TIME);
        }
    }

    /*public void stopTimer() {
        Log.d(TAG, "timer stopped");
        my_handler.removeCallbacks(my_runnable);
    }*/

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.d(TAG, "Back pressed");
            finish();
        }
        return true;
    }

    /**
     * Sets ListView height dynamically based on the height of the items.
     *
     * @param listView to be resized
     * @return true if the listView is successfully resized, false otherwise
     */
    public static boolean setListViewHeightBasedOnItems(ListView listView) {

        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter != null) {

            int numberOfItems = listAdapter.getCount();

            // Get total height of all items.
            int totalItemsHeight = 0;
            for (int itemPos = 0; itemPos < numberOfItems; itemPos++) {
                View item = listAdapter.getView(itemPos, null, listView);
                item.measure(0, 0);
                totalItemsHeight += item.getMeasuredHeight();
            }

            // Get total height of all item dividers.
            int totalDividersHeight = listView.getDividerHeight() *
                    (numberOfItems - 1);

            // Set list height.
            ViewGroup.LayoutParams params = listView.getLayoutParams();
            params.height = totalItemsHeight + totalDividersHeight;
            listView.setLayoutParams(params);
            listView.requestLayout();

            return true;

        } else {
            return false;
        }
    }
}
