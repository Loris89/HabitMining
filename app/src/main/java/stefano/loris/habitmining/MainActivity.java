package stefano.loris.habitmining;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import stefano.loris.habitmining.app.AppConfig;
import stefano.loris.habitmining.utils.SessionManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private Button avviaAttivita;
    private Button terminaAttivita;
    private Button annullaAttivita;
    private Button esci;

    private TextView status;

    private ProgressBar progressBar;

    private String IMEI;
    private String attivitaScelta;
    private String startTime;
    private String endTime;

    private SessionManager session;

    private ProgressDialog pDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        avviaAttivita = (Button)findViewById(R.id.btn_avvia_attività);
        terminaAttivita = (Button)findViewById(R.id.btn_termina_attività);
        annullaAttivita = (Button)findViewById(R.id.btn_annulla_attività);
        esci = (Button)findViewById(R.id.btn_esci);
        status = (TextView)findViewById(R.id.status_text_main);

        progressBar = (ProgressBar)findViewById(R.id.main_progressbar);
        progressBar.setVisibility(View.INVISIBLE);

        // Session manager
        session = new SessionManager(getApplicationContext());
        IMEI = session.getIMEI();

        // Progress dialog
        pDialog = new ProgressDialog(this);
        pDialog.setCancelable(false);

        avviaAttivita.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // reset variables
                reset();
                // prendi il timestamp
                setTime(true);
                // chiama activity delle attività e attendi l'attività scelta
                Intent intent = new Intent(MainActivity.this, ActionsActivity.class);
                intent.putExtra("timestamp", startTime);
                startActivityForResult(intent, 0);
            }
        });

        terminaAttivita.setEnabled(false);
        terminaAttivita.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setTime(false);

                // salva l'attività nel database remoto
                //
                storeActivity();
                //
                ///////////////////////////////////////

                // reset data
                reset();
            }
        });

        annullaAttivita.setEnabled(false);
        annullaAttivita.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // attività corrente annullata, reset dei dati
                reset();
            }
        });

        esci.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //session.setLogin(false);
                //session.setIMEI("");
                finish();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == 0) {
            if(resultCode == Activity.RESULT_OK){
                attivitaScelta = data.getStringExtra("result");
                Log.d(TAG, "Attività scelta: " + attivitaScelta);
                //setTime(true);
                status.setText("Attività " + attivitaScelta + " in corso");
                progressBar.setVisibility(View.VISIBLE);
                avviaAttivita.setEnabled(false);
                terminaAttivita.setEnabled(true);
                annullaAttivita.setEnabled(true);
            }
            if (resultCode == Activity.RESULT_CANCELED) {
                Log.d(TAG, "Scelta attività annullata");
                reset();
            }
        }
    }

    private void reset() {
        status.setText("Nessuna attività in corso");
        progressBar.setVisibility(View.INVISIBLE);
        avviaAttivita.setEnabled(true);
        terminaAttivita.setEnabled(false);
        annullaAttivita.setEnabled(false);
    }

    private void setTime(boolean start) {
        // formato datetime MySQL 'YYYY-MM-DD HH:MM:SS'
        GregorianCalendar gregorianCalendar = new GregorianCalendar();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        fmt.setCalendar(gregorianCalendar);
        String dateFormatted = fmt.format(gregorianCalendar.getTime());

        if(start) {
            startTime = dateFormatted;
            Log.d(TAG, "START TIME: " + startTime);
        }
        else {
            endTime = dateFormatted;
            Log.d(TAG, "END TIME: " + endTime);
        }
    }

    private void storeActivity() {
        Log.d(TAG, "storeActivity()");

        // should be a singleton
        OkHttpClient client = new OkHttpClient();

        RequestBody formBody = new FormBody.Builder()
                .add("imei",IMEI)
                .add("timestamp_start", startTime)
                .add("timestamp_stop", endTime)
                .add("action", attivitaScelta)
                .build();
        Log.d(TAG, "attività scelta: " + attivitaScelta);
        Request request = new Request.Builder()
                .url(AppConfig.URL_PUT_ACTIVITY)
                .post(formBody)
                .build();

        showDialog("Inserimento attività...");

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
                        } else {
                            // Error in login. Get the error message
                            String errorMsg = jObj.getString("error_msg");
                            //registerPhone.setEnabled(true);
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
