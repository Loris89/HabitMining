package stefano.loris.habitmining.app;

import android.app.Application;

import stefano.loris.habitmining.utils.ConnectivityReceiver;

public class AppController extends Application {

    public static final String TAG = AppController.class.getSimpleName();

    private static AppController mInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
    }

    public static synchronized AppController getInstance() {
        return mInstance;
    }

    public void setConnectivityListener(ConnectivityReceiver.ConnectivityReceiverListener listener) {
        ConnectivityReceiver.connectivityReceiverListener = listener;
    }

    public void removeConnectivityListener() {
        ConnectivityReceiver.connectivityReceiverListener = null;
    }
}
