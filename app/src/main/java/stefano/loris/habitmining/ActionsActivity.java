package stefano.loris.habitmining;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import stefano.loris.habitmining.R;
import stefano.loris.habitmining.app.AppConfig;
import stefano.loris.habitmining.utils.SessionManager;

public class ActionsActivity extends AppCompatActivity {

    private static final String TAG = ActionsActivity.class.getSimpleName();

    private ListView listaAttivita;
    private Button annulla;

    private ProgressDialog pDialog;

    private ArrayList<Attivita> attivitaA;
    private AttivitaAdapter attivitaAdapter;

    private String IMEI;

    private SessionManager session;

    private String timestamp;

    private DecimalFormat decimalFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actions);

        listaAttivita = (ListView)findViewById(R.id.lista_attività);
        annulla = (Button)findViewById(R.id.annulla_lista_attività);

        session = new SessionManager(getApplicationContext());

        // Progress dialog
        pDialog = new ProgressDialog(this);
        pDialog.setCancelable(false);

        // get the IMEI
        IMEI = session.getIMEI();

        Intent intent = getIntent();
        timestamp = intent.getStringExtra("timestamp");

        decimalFormat = new DecimalFormat("##.##");

        attivitaA = new ArrayList<>();
        attivitaAdapter = new AttivitaAdapter(this, R.layout.activity_row, attivitaA);

        listaAttivita.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Attivita result = (Attivita) parent.getItemAtPosition(position);
                Intent returnIntent = new Intent();
                returnIntent.putExtra("result", result.getNome());
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

        listaAttivita.setAdapter(attivitaAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // ottieni e mostra tutte le attività per questo telefono
        caricaAttivita();
    }

    private void caricaAttivita() {
        // should be a singleton
        OkHttpClient client = new OkHttpClient();

        RequestBody formBody = new FormBody.Builder()
                .add("imei", IMEI)
                .add("timestamp", timestamp)
                .build();
        Log.d(TAG, "formBody: " + formBody.contentType());
        Request request = new Request.Builder()
                .url(AppConfig.URL_GET_ACTIVITIES)
                .post(formBody)
                .build();

        showDialog("Ottenendo le attività...");

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
                    // server error
                    throw new IOException("Unexpected code " + response);
                } else {
                    try {
                        String body = response.body().string();
                        Log.d(TAG, body);
                        JSONObject jObj = new JSONObject(body);
                        boolean error = jObj.getBoolean("error");

                        // Check for error node in json
                        if (!error) {
                            JSONArray activities = jObj.getJSONArray("activities");
                            Log.d(TAG, "Activities: " + activities.toString());
                            attivitaA.clear();
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
                        } else {
                            // Error in getting activities. Get the error message
                            String errorMsg = jObj.getString("error_msg");
                            Log.d(TAG, errorMsg);
                        }
                    } catch (JSONException e) {
                        // JSON error
                        Log.d(TAG, e.getMessage());
                    }
                }
            }
        });
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

    private static class AttivitaAdapter extends ArrayAdapter<Attivita> {

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

            return convertView;
        }

        /**
         * The view holder design pattern prevents using findViewById()
         * repeatedly in the getView() method of the adapter.
         */
        private static class ViewHolder {
            TextView name;
            TextView probability;
        }
    }

    private void showDialog(String message) {
        pDialog.setMessage(message);
        if (!pDialog.isShowing())
            pDialog.show();
    }

    private void hideDialog() {
        if (pDialog.isShowing())
            pDialog.dismiss();
    }
}
