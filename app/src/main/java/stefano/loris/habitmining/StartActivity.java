package stefano.loris.habitmining;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import stefano.loris.habitmining.app.AppConfig;
import stefano.loris.habitmining.app.AppController;
import stefano.loris.habitmining.helper.DBHelper;
import stefano.loris.habitmining.utils.ConnectivityReceiver;
import stefano.loris.habitmining.utils.SessionManager;
import stefano.loris.habitmining.utils.Utils;

public class StartActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback, ConnectivityReceiver.ConnectivityReceiverListener {

    private static final String TAG = StartActivity.class.getSimpleName();

    private static final int REQUEST_READ_PHONE_STATE = 1;

    private ProgressDialog pDialog;
    private Button registerPhone;
    private Button enter;
    private TextView statusText;

    private SessionManager session;

    // imei of this phone
    private String IMEI;

    // if the phone is connected to the wifi or not
    private boolean connected;
    private boolean start;

    private DBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        // Progress dialog
        pDialog = new ProgressDialog(this);
        pDialog.setCancelable(false);

        registerPhone = (Button)findViewById(R.id.register_phone_btn);
        enter = (Button)findViewById(R.id.enter_button);
        statusText = (TextView)findViewById(R.id.start_status_txtv);

        // Session manager
        session = new SessionManager(getApplicationContext());

        dbHelper = new DBHelper(StartActivity.this);

        start = true;

        registerPhone.setEnabled(false);
        registerPhone.setVisibility(View.INVISIBLE);
        registerPhone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerPhone();
            }
        });

        enter.setEnabled(false);
        registerPhone.setVisibility(View.INVISIBLE);
        enter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPhone();
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

        // register connection status listener
        AppController.getInstance().setConnectivityListener(this);

        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission to read phone state not granted");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_READ_PHONE_STATE);
        } else {
            Log.d(TAG, "Permission to read phone state granted");
            findIMEI();
        }

        if(checkConnection()) {
            checkPhone();
        } else {
            registerPhone.setVisibility(View.INVISIBLE);
            enter.setVisibility(View.INVISIBLE);
            statusText.setText("Please turn on Internet");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_READ_PHONE_STATE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    findIMEI();
                    if(checkConnection()) {
                        checkPhone();
                    } else {
                        registerPhone.setVisibility(View.INVISIBLE);
                        statusText.setText("Please turn on Internet");
                    }
                } else {
                    finish();
                }
                break;

            default:
                break;
        }
    }

    /**
     * Gets the imei of this phone
     */
    private void findIMEI() {
        Log.d(TAG, "findIMEI()");
        TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        IMEI = telephonyManager.getDeviceId();
    }

    /**
     * Checks if the IMEI of this phone is already present in the database
     */
    private void checkPhone() {
        Log.d(TAG, "checkPhone()");
        Log.d(TAG, "IMEI: " + IMEI);

        // should be a singleton
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        RequestBody formBody = new FormBody.Builder()
                .add("imei",IMEI)
                .build();
        Log.d(TAG, "formBody: " + formBody.contentType());
        Request request = new Request.Builder()
                .url(AppConfig.URL_LOGIN)
                .post(formBody)
                .build();

        showDialog("Checking phone presence...");

        // Get a handler that can be used to post to the main thread
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                hideDialog();
                e.printStackTrace();

                // I TIMEOUT VENGONO CHIAMATI QUI
                enter.setEnabled(true);
                enter.setVisibility(View.VISIBLE);
                statusText.setText("Try again");
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                hideDialog();
                if (!response.isSuccessful()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(StartActivity.this, "Error: " + response + "." + " Try again.", Toast.LENGTH_LONG).show();
                        }
                    });
                }
                else {
                    try {
                        String body = response.body().string();
                        Log.d(TAG, body);
                        JSONObject jObj = new JSONObject(body);
                        boolean error = jObj.getBoolean("error");

                        // Check for error node in json
                        if (!error) {
                            // Save IMEI
                            session.setIMEI(IMEI);

                            Log.d(TAG, "Success! Your IMEI is present in the DB");

                            // carica le attività ed entra nella main activity
                            loadActivities();

                        } else {
                            // Error in login. Get the error message
                            final String errorMsg = jObj.getString("error_msg");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    statusText.setText(errorMsg);
                                    registerPhone.setEnabled(true);
                                }
                            });
                            Log.e(TAG, errorMsg);
                        }
                    } catch (JSONException e) {
                        final String msg = e.getMessage();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(StartActivity.this, "Error: " + msg + "." + " Try again.", Toast.LENGTH_LONG).show();
                                statusText.setText("Error: " + msg);
                                registerPhone.setEnabled(true);
                            }
                        });
                        Log.e(TAG, e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Stores the IMEI of this phone on the database
     */
    private void registerPhone() {
        // should be a singleton
        OkHttpClient client = new OkHttpClient();

        RequestBody formBody = new FormBody.Builder()
                .add("imei", IMEI)
                .build();
        Log.d(TAG, "formBody: " + formBody.contentType());
        Request request = new Request.Builder()
                .url(AppConfig.URL_REGISTER)
                .post(formBody)
                .build();

        showDialog("Registering the phone...");

        // Get a handler that can be used to post to the main thread
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                hideDialog();
                e.printStackTrace();

                // I TIMEOUT VENGONO CHIAMATI QUI
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                hideDialog();
                if (!response.isSuccessful()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(StartActivity.this, "Error: " + response + "." + " Try again.", Toast.LENGTH_LONG).show();
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
                            // user successfully logged in
                            // Create login session
                            // session.setLogin(true);
                            session.setIMEI(IMEI);

                            // carica le attività ed entra nella main activity
                            loadActivities();

                            Log.d(TAG, "Success! Your phone has been registered");

                        } else {
                            // Error in registration. Get the error message
                            final String errorMsg = jObj.getString("error_msg");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(StartActivity.this, "Error: " + errorMsg + "." + " Try again.", Toast.LENGTH_LONG).show();
                                    statusText.setText("Error: " + errorMsg);
                                    registerPhone.setEnabled(true);
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
                                Toast.makeText(StartActivity.this, "Error: " + msg + "." + " Try again.", Toast.LENGTH_LONG).show();
                                statusText.setText("Error: " + msg);
                                registerPhone.setEnabled(true);
                            }
                        });
                        Log.e(TAG, e.getMessage());
                    }
                }
            }
        });
    }

    private void loadActivities() {
        // should be a singleton
        OkHttpClient client = new OkHttpClient();

        RequestBody formBody = new FormBody.Builder()
                .build();
        Log.d(TAG, "formBody: " + formBody.contentType());
        Request request = new Request.Builder()
                .url(AppConfig.URL_LOAD_ACTIVITIES)
                .post(formBody)
                .build();

        showDialog("Loading activities...");

        // Get a handler that can be used to post to the main thread
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                hideDialog();
                e.printStackTrace();

                // I TIMEOUT VENGONO CHIAMATI QUI
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                hideDialog();
                if (!response.isSuccessful()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(StartActivity.this, "Error: " + response + "." + " Try again.", Toast.LENGTH_LONG).show();
                        }
                    });;
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
                            for(int i = 0; i < activities.length(); i++) {
                                dbHelper.storeActivity(activities.getString(i));
                            }

                            // ENTRA NELLA MAIN ACTIVITY
                            Intent intent = new Intent(StartActivity.this, MainActivity.class);
                            startActivity(intent);
                            finish();

                            Log.d(TAG, "Success! Your phone has been registered");

                        } else {
                            // Error in registration. Get the error message
                            final String errorMsg = jObj.getString("error_msg");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(StartActivity.this, "Error: " + errorMsg + "." + " Try again.", Toast.LENGTH_LONG).show();
                                    statusText.setText("Error: " + errorMsg);
                                    registerPhone.setEnabled(true);
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
                                Toast.makeText(StartActivity.this, "Error: " + msg + "." + " Try again.", Toast.LENGTH_LONG).show();
                                statusText.setText("Error: " + msg);
                                registerPhone.setEnabled(true);
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
    }

    @Override
    protected void onStop() {
        super.onStop();

        // remove connection listener
        AppController.getInstance().removeConnectivityListener();
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
                if (!pDialog.isShowing())
                    pDialog.show();
            }
        });
    }

    private void hideDialog() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (pDialog.isShowing())
                    pDialog.dismiss();
            }
        });
    }

    private void updateConnection() {
        connected = checkConnection();

        // serve per far partire il controllo iniziale sull'imei
        if(connected == true && start) {
            registerPhone.setVisibility(View.VISIBLE);
            checkPhone();
        }

        String message;
        if (connected) {
            message = "Good! Connected to internet";
        } else {
            message = "Sorry! Not connected to internet";
        }

        statusText.setText(message);
    }

    // Method to manually check connection status
    private boolean checkConnection() {
        boolean isConnected = ConnectivityReceiver.isConnected();
        return isConnected;
    }

    @Override
    public void onNetworkConnectionChanged(boolean isConnected) {
        updateConnection();
    }
}
