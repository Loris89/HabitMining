package stefano.loris.habitmining;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
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
import android.widget.ListAdapter;
import android.widget.ListView;
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
import stefano.loris.habitmining.utils.ConnectivityReceiver;
import stefano.loris.habitmining.utils.SessionManager;
import stefano.loris.habitmining.utils.Utils;

import static android.hardware.Sensor.TYPE_ACCELEROMETER;
import static android.hardware.Sensor.TYPE_GYROSCOPE;
import static android.hardware.Sensor.TYPE_LINEAR_ACCELERATION;
import static android.hardware.Sensor.TYPE_MAGNETIC_FIELD;
import static android.hardware.Sensor.TYPE_ROTATION_VECTOR;
import static android.hardware.Sensor.TYPE_STEP_COUNTER;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final Long TIMER_TIME = 1200000L; // debug: 1 minuto = 60000L | 20 minuti = 1200000L

    private static final int NOTIFICATION_ID = 001;

    // DATI VISTA

    private Button avviaAttivita;
    private TextView imei;
    private TextView nessuna_attività;
    private ProgressDialog pDialog;

    // DATI TELEFONO

    private String IMEI;
    private SessionManager session;

    // LIST ACTIVITY STUFF

    private ListView attivitaListView;
    private AttivitaAdapter adapter;
    private ArrayList<Attivita> listaAttivita;

    // Create the Handler object (on the main thread by default)
    public static Handler my_handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        avviaAttivita = (Button)findViewById(R.id.btn_avvia_attività);
        imei = (TextView)findViewById(R.id.imei_txt);
        attivitaListView = (ListView)findViewById(R.id.lista_attivita);
        nessuna_attività = (TextView)findViewById(R.id.nessuna_attivita);

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
                // reset variables
                if(listaAttivita.size() == 3) {
                    Toast.makeText(MainActivity.this, "Hai selezionato già 3 attività", Toast.LENGTH_LONG).show();
                } else {
                    // chiama activity delle attività e attendi l'attività scelta
                    Intent intent = new Intent(MainActivity.this, ActionsActivity.class);
                    startActivityForResult(intent, 0);
                }
            }
        });

        attivitaListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // no action
            }
        });

        adapter = new AttivitaAdapter(MainActivity.this, R.layout.main_activity_row, listaAttivita);
        attivitaListView.setAdapter(adapter);

        setListViewHeightBasedOnItems(attivitaListView);

        // starts the timer
        startTimer();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == 0) {
            if(resultCode == Activity.RESULT_OK){
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
                setListViewHeightBasedOnItems(attivitaListView);
                nessuna_attività.setVisibility(View.INVISIBLE);
                // restartTimer timer
                restartTimer();
            }
            if (resultCode == Activity.RESULT_CANCELED) {
                Log.d(TAG, "Scelta attività annullata");
            }
        }
    }

    private String getTime() {
        // formato datetime MySQL 'YYYY-MM-DD HH:MM:SS'
        GregorianCalendar gregorianCalendar = new GregorianCalendar();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        fmt.setCalendar(gregorianCalendar);
        String dateFormatted = fmt.format(gregorianCalendar.getTime());
        return dateFormatted;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
                    // riavvio timer utilizzo app
                    restartTimer();
                }
            });

            Button cancelAction = (Button)convertView.findViewById(R.id.cancel_button);
            cancelAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // rimuovi attività
                    objects.remove(position);
                    notifyDataSetChanged();
                    setListViewHeightBasedOnItems(attivitaListView);

                    if(listaAttivita.size()==0) {
                        nessuna_attività.setVisibility(View.VISIBLE);
                    }

                    // riavvio timer utilizzo app
                    restartTimer();
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
                                    setListViewHeightBasedOnItems(attivitaListView);
                                    if(listaAttivita.size()==0) {
                                        nessuna_attività.setVisibility(View.VISIBLE);
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
                            Log.d(TAG, errorMsg);
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
                        Log.d(TAG, e.getMessage());
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

            restartTimer();
        }
    };

    public void startTimer() {
        my_handler.postDelayed(my_runnable, TIMER_TIME);
    }

    public void restartTimer() {
        my_handler.removeCallbacks(my_runnable);
        my_handler.postDelayed(my_runnable, TIMER_TIME);
    }

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
