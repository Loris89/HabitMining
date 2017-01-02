package stefano.loris.habitmining;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import stefano.loris.habitmining.app.AppConfig;
import stefano.loris.habitmining.helper.DBHelper;
import stefano.loris.habitmining.utils.ConnectivityReceiver;
import stefano.loris.habitmining.utils.SessionManager;
import stefano.loris.habitmining.utils.Utils;

import static android.hardware.Sensor.TYPE_ACCELEROMETER;
import static android.hardware.Sensor.TYPE_GYROSCOPE;
import static android.hardware.Sensor.TYPE_LINEAR_ACCELERATION;
import static android.hardware.Sensor.TYPE_MAGNETIC_FIELD;
import static android.hardware.Sensor.TYPE_ROTATION_VECTOR;
import static android.hardware.Sensor.TYPE_STEP_COUNTER;

public class ActionsActivity extends AppCompatActivity {

    private static final String TAG = ActionsActivity.class.getSimpleName();

    // DATI VISTA
    private ListView listaAttivita;
    private Button annulla;
    private Button addActivity;
    private Button refresh;
    private ProgressDialog pDialog;
    private AutoCompleteTextView autoCompleteTextView;

    private ArrayList<Attivita> attivitaA;
    private AttivitaAdapter attivitaAdapter;

    private String IMEI;
    private SessionManager session;

    private DecimalFormat decimalFormat;

    // dati database locale
    private DBHelper localDatabase;
    private ArrayList<String> attivitaRemote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actions);

        listaAttivita = (ListView)findViewById(R.id.lista_attività);
        annulla = (Button)findViewById(R.id.annulla_lista_attività);
        addActivity = (Button)findViewById(R.id.add_action);
        refresh = (Button)findViewById(R.id.refresh);
        autoCompleteTextView = (AutoCompleteTextView)findViewById(R.id.aggiungi_attività_txtv);

        session = new SessionManager(getApplicationContext());

        // Progress dialog
        pDialog = new ProgressDialog(this);
        pDialog.setCancelable(false);

        // OTTIENI ATTIVITA'
        localDatabase = new DBHelper(ActionsActivity.this);
        /*attivitaRemote = localDatabase.getActivities();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line, attivitaRemote
        );
        autoCompleteTextView.setAdapter(adapter);*/
        loadActivitiesIntoArray(true);
        autoCompleteTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View view, int pos,long id) {
                String selected = (String)adapter.getItemAtPosition(pos);
                Toast.makeText(
                        getApplicationContext(),
                        "hai selezionato "+selected,
                        Toast.LENGTH_LONG
                ).show();
            }
        });

        // get the IMEI
        IMEI = session.getIMEI();

        decimalFormat = new DecimalFormat("##.##");

        attivitaA = new ArrayList<>();
        attivitaAdapter = new AttivitaAdapter(this, R.layout.activity_row, attivitaA);

        listaAttivita.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // ottengo l'attività cliccata
                Attivita result = (Attivita) parent.getItemAtPosition(position);
                // dati da ritornare
                Intent returnIntent = new Intent();
                returnIntent.putExtra("result", result.getNome());
                returnIntent.putExtra("start", getTime());
                setResult(Activity.RESULT_OK, returnIntent);
                finish();
            }
        });

        annulla.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent returnIntent = new Intent();
                setResult(Activity.RESULT_CANCELED, returnIntent);
                finish();
            }
        });

        addActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String actionName = autoCompleteTextView.getText().toString().trim();
                if(actionName.isEmpty()) {
                    Toast.makeText(ActionsActivity.this, "Please specify an activity name", Toast.LENGTH_SHORT).show();
                } else {
                    aggiungiAttivita(actionName);
                }
            }
        });

        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                caricaAttivita();
                loadActivitiesIntoDB();
            }
        });

        listaAttivita.setAdapter(attivitaAdapter);
    }

    private void loadActivitiesIntoArray(boolean start) {
        attivitaRemote = localDatabase.getActivities();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line, attivitaRemote
        );
        autoCompleteTextView.setAdapter(adapter);
        if(!start)
            adapter.notifyDataSetChanged();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        caricaAttivita();
    }

    private void rimuoviAttivita(String attivita) {
        // should be a singleton
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        RequestBody formBody = new FormBody.Builder()
                .add("imei", IMEI)
                .add("action", attivita)
                .build();
        Log.d(TAG, "formBody: " + formBody.contentType());

        Request request = new Request.Builder()
                .url(AppConfig.URL_REMOVE_ACTIVITY)
                .post(formBody)
                .build();

        showDialog("Rimuovendo l'attività...");

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
                            Toast.makeText(ActionsActivity.this, "Error: " + response + "." + " Try again.", Toast.LENGTH_LONG).show();
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
                            Log.d(TAG, "Success! Activity removed");
                            // ricarico le attività (forse devo prima resettare il tutto)
                            caricaAttivita();
                        } else {
                            // Errore aggiunta attività per questo telefono
                            Log.e(TAG, "Errore rimozione attività");
                        }
                    } catch (JSONException e) {
                        // JSON error
                        final String msg = e.getMessage();
                        Log.e(TAG, msg);
                    }
                }
            }
        });
    }

    private void aggiungiAttivita(String attivita) {
        Log.d(TAG, "Aggiungi attività: " + attivita + " IMEI: " + IMEI);
        // should be a singleton
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        RequestBody formBody = new FormBody.Builder()
                .add("imei", IMEI)
                .add("activity", attivita)
                .build();
        Log.d(TAG, "formBody: " + formBody.contentType());

        Request request = new Request.Builder()
                .url(AppConfig.URL_ADD_ACTIVITY)
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
                            Toast.makeText(ActionsActivity.this, "Error: " + response + "." + " Try again.", Toast.LENGTH_LONG).show();
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
                            caricaAttivita();
                        } else {
                            // Errore aggiunta attività per questo telefono
                            Log.e(TAG, "Error inserting activities");
                        }
                    } catch (JSONException e) {
                        // JSON error
                        final String msg = e.getMessage();
                        Log.e(TAG, msg);
                    }
                }
            }
        });
    }

    /**
     * Loads activities ordered by relevance
     */
    private void caricaAttivita() {
        Log.d(TAG, IMEI + " " + getTime());
        // should be a singleton
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        RequestBody formBody = new FormBody.Builder()
                .add("imei", IMEI)
                .add("timestamp", getTime())
                .build();
        Log.d(TAG, "formBody: " + formBody.contentType());
        Request request = new Request.Builder()
                .url(AppConfig.URL_GET_ACTIVITIES)
                .post(formBody)
                .build();

        showDialog("Getting activities...");

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
                            Toast.makeText(ActionsActivity.this, "Error: " + response + "." + " Try again.", Toast.LENGTH_LONG).show();
                        }
                    });
                    // server error
                    throw new IOException("Unexpected code " + response);
                } else {
                    try {
                        String body = response.body().string();
                        Log.d(TAG, "BODY: " + body);
                        JSONObject jObj = new JSONObject(body);
                        Log.d(TAG, "JSON: " + jObj.toString());
                        boolean error = jObj.getBoolean("error");

                        // Check for error node in json
                        if (!error) {
                            JSONArray activities = jObj.getJSONArray("activities");
                            Log.d(TAG, "Activities: " + activities.toString());
                            attivitaA.clear();
                            if(activities.length()==0) {
                                // carica attività di default
                                Log.d(TAG, "Non ci sono attività per te");
                                // caricaAttivitaDefault();
                            } else {
                                for(int i = 0; i < activities.length(); i++) {
                                    JSONObject actObj = activities.getJSONObject(i);
                                    String name = actObj.getString("attivita");
                                    double probabilita = actObj.getDouble("probabilita");
                                    double prob100 = probabilita*100;
                                    String val = decimalFormat.format(prob100);
                                    String valFixed = val.replace(",",".");
                                    double probFinal = Double.parseDouble(valFixed);
                                    Attivita attivita = new Attivita(name, probFinal);
                                    attivitaA.add(attivita);
                                }
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.d(TAG, "Aggiornamento lista");
                                        attivitaAdapter.notifyDataSetChanged();
                                        attivitaAdapter.sort(new Comparator<Attivita>() {
                                            @Override
                                            public int compare(Attivita lhs, Attivita rhs) {
                                                Double p1 = new Double(lhs.getProbabilita());
                                                Double p2 = new Double(rhs.getProbabilita());
                                                return p2.compareTo(p1);
                                            }
                                        });
                                    }
                                });

                                loadActivitiesIntoDB();
                            }
                        } else {
                            // Error in getting activities. Get the error message
                            final String errorMsg = jObj.getString("error_msg");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(ActionsActivity.this, "Error: " + errorMsg + "." + " Try again.", Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    } catch (JSONException e) {
                        // JSON error
                        Log.e(TAG, "JSON ERROR: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void caricaAttivitaDefault() {
        Log.d(TAG, IMEI + " " + getTime());
        // should be a singleton
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        RequestBody formBody = new FormBody.Builder()
                .add("imei", IMEI)
                .build();
        Log.d(TAG, "formBody: " + formBody.contentType());
        Request request = new Request.Builder()
                .url(AppConfig.URL_LOAD_DEFAULT_ACTIVITIES)
                .post(formBody)
                .build();

        showDialog("Getting default activities...");

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
                            Toast.makeText(ActionsActivity.this, "Error: " + response + "." + " Try again.", Toast.LENGTH_LONG).show();
                        }
                    });
                    // server error
                    throw new IOException("Unexpected code " + response);
                } else {
                    try {
                        String body = response.body().string();
                        Log.d(TAG, "BODY: " + body);
                        JSONObject jObj = new JSONObject(body);
                        Log.d(TAG, "JSON: " + jObj.toString());
                        boolean error = jObj.getBoolean("error");

                        // Check for error node in json
                        if (!error) {
                            JSONArray activities = jObj.getJSONArray("activities");
                            Log.d(TAG, "Activities: " + activities.toString());
                            attivitaA.clear();
                            // get activities
                            for (int i = 0; i < activities.length(); i++) {
                                String name = activities.getString(i);
                                Attivita attivita = new Attivita(name, 0);
                                attivitaA.add(attivita);
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Log.d(TAG, "Aggiornamento lista");
                                    attivitaAdapter.notifyDataSetChanged();
                                    attivitaAdapter.sort(new Comparator<Attivita>() {
                                        @Override
                                        public int compare(Attivita lhs, Attivita rhs) {
                                            Double p1 = new Double(lhs.getProbabilita());
                                            Double p2 = new Double(rhs.getProbabilita());
                                            return p2.compareTo(p1);
                                        }
                                    });
                                }
                            });

                            loadActivitiesIntoDB();

                        } else {
                            // Error in getting activities. Get the error message
                            final String errorMsg = jObj.getString("error_msg");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(ActionsActivity.this, "Error: " + errorMsg + "." + " Try again.", Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    } catch (JSONException e) {
                        // JSON error
                        Log.e(TAG, "JSON ERROR: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void loadActivitiesIntoDB() {
        // should be a singleton
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        RequestBody formBody = new FormBody.Builder()
                .build();
        Log.d(TAG, "formBody: " + formBody.contentType());
        Request request = new Request.Builder()
                .url(AppConfig.URL_LOAD_ACTIVITIES)
                .post(formBody)
                .build();

        //showDialog("Loading activities...");

        // Get a handler that can be used to post to the main thread
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //hideDialog();
                e.printStackTrace();

                // I TIMEOUT VENGONO CHIAMATI QUI
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                //hideDialog();
                if (!response.isSuccessful()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ActionsActivity.this, "Error: " + response + "." + " Try again.", Toast.LENGTH_LONG).show();
                        }
                    });
                    throw new IOException("Unexpected code " + response);
                }
                else {
                    try {
                        String body = response.body().string();
                        Log.d(TAG, body);
                        JSONObject jObj = new JSONObject(body);
                        boolean error = jObj.getBoolean("error");

                        // Check for error node in json
                        if (!error) {

                            // get activities
                            JSONArray activities = jObj.getJSONArray("activities");

                            // remove all the activities from local database
                            localDatabase.reset();
                            for(int i = 0; i < activities.length(); i++) {
                                // store new activities into the remote database
                                localDatabase.storeActivity(activities.getString(i));
                            }

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    loadActivitiesIntoArray(false);
                                }
                            });

                            Log.d(TAG, "Activities has been loaded");

                        } else {
                            // Error in getting activities
                            final String errorMsg = jObj.getString("error_msg");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(ActionsActivity.this, "Error: " + errorMsg + "." + " Try again.", Toast.LENGTH_LONG).show();
                                }
                            });
                            Log.e(TAG, errorMsg);
                        }
                    } catch (JSONException e) {
                        // JSON error
                        final String msg = e.getMessage();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ActionsActivity.this, "Error: " + msg + "." + " Try again.", Toast.LENGTH_LONG).show();
                            }
                        });
                        Log.e(TAG, e.getMessage());
                    }
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideDialog();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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

    public void onNetworkConnectionChanged(boolean isConnected) {

    }

    private class AttivitaAdapter extends ArrayAdapter<Attivita> {

        public AttivitaAdapter(Context context, int resource, List<Attivita> objects) {
            super(context, resource, objects);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;

            if(convertView == null) {
                // inflate the GridView item layout
                LayoutInflater inflater = LayoutInflater.from(getContext());
                convertView = inflater.inflate(R.layout.activity_row, parent, false);

                // initialize the view holder
                viewHolder = new ViewHolder();
                viewHolder.name = (TextView)convertView.findViewById(R.id.action_name);
                viewHolder.probability = (TextView)convertView.findViewById(R.id.probability_val);
                convertView.setTag(viewHolder);
            } else {
                // recycle the already inflated view
                viewHolder = (ViewHolder) convertView.getTag();
            }

            // update the item view
            Attivita item = getItem(position);
            viewHolder.name.setText(item.getNome());
            viewHolder.probability.setText(String.valueOf(item.getProbabilita()));

            Button removeAction = (Button)convertView.findViewById(R.id.remove_action);
            removeAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String name = getItem(position).getNome();
                    new AlertDialog.Builder(ActionsActivity.this)
                            .setTitle("Remove activity")
                            .setMessage("Do you really want to remove the activity " + name + " ?")
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    rimuoviAttivita(name);
                                    Toast.makeText(ActionsActivity.this, name + " removed", Toast.LENGTH_SHORT).show();
                                }})
                            .setNegativeButton(android.R.string.no, null).show();
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
            TextView probability;
        }
    }

    private void showDialog(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pDialog.setMessage(message);
                if (!pDialog.isShowing())
                    pDialog.show();
            }
        });
    }

    private void hideDialog() {
        if (pDialog.isShowing())
            pDialog.dismiss();
    }
}
