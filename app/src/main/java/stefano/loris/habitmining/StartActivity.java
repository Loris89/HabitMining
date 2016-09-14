package stefano.loris.habitmining;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import stefano.loris.habitmining.app.AppConfig;
import stefano.loris.habitmining.utils.SessionManager;

public class StartActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = StartActivity.class.getSimpleName();

    private static final int REQUEST_READ_PHONE_STATE = 1;

    private ProgressDialog pDialog;
    private Button registerPhone;
    private TextView statusText;

    private SessionManager session;

    private String IMEI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        // Progress dialog
        pDialog = new ProgressDialog(this);
        pDialog.setCancelable(false);

        registerPhone = (Button)findViewById(R.id.register_phone_btn);
        statusText = (TextView)findViewById(R.id.start_status_txtv);

        // Session manager
        session = new SessionManager(getApplicationContext());

        //registerPhone.setEnabled(false);
        registerPhone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerPhone();
            }
        });

        // Check if user is already logged in or not
        if (session.isLoggedIn()) {
            // User is already logged in. Take him to main activity
            Intent intent = new Intent(StartActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission to read phone state not granted");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_READ_PHONE_STATE);
        } else {
            //TODO
            Log.d(TAG, "Permission to read phone state granted");
            findIMEI();
            checkPhone();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_READ_PHONE_STATE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    findIMEI();
                    checkPhone();
                } else {
                    finish();
                }
                break;

            default:
                break;
        }
    }

    private void findIMEI() {
        Log.d(TAG, "findIMEI()");
        TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        IMEI = telephonyManager.getDeviceId();
    }

    private void checkPhone() {
        Log.d(TAG, "checkPhone()");
        Log.d(TAG, "IMEI: " + IMEI);
        // call php script with OkHttp
        // phone already registered --> go to main activity
        // phone isn't registered --> remain here, set "register button" clickable, update status message

        // should be a singleton
        OkHttpClient client = new OkHttpClient();

        RequestBody formBody = new FormBody.Builder()
                .add("imei",IMEI)
                .build();
        Log.d(TAG, "formBody: " + formBody.contentType());
        Request request = new Request.Builder()
                .url(AppConfig.URL_LOGIN)
                .post(formBody)
                .build();

        showDialog("Checking phone registration...");

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
                            session.setLogin(true);
                            session.setIMEI(IMEI);

                            // Launch main activity
                            Intent intent = new Intent(StartActivity.this,
                                    MainActivity.class);
                            intent.putExtra("IMEI",IMEI);
                            startActivity(intent);
                            finish();

                            Log.d(TAG, "Success! Your phone is present");

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
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                hideDialog();
                if (!response.isSuccessful()) {
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
                            session.setLogin(true);

                            // Launch main activity
                            Intent intent = new Intent(StartActivity.this,
                                    MainActivity.class);
                            startActivity(intent);
                            finish();

                            Log.d(TAG, "Success! Your phone has been registered");

                        } else {
                            // Error in registration. Get the error message
                            String errorMsg = jObj.getString("error_msg");
                            Log.d(TAG, errorMsg);
                        }
                    } catch (JSONException e) {
                        // JSON error
                        Log.d(TAG, e.getMessage());
                        //e.printStackTrace();
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
