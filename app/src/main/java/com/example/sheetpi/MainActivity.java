package com.example.sheetpi;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;

import com.google.api.services.sheets.v4.SheetsScopes;

import com.google.api.services.sheets.v4.model.*;

import android.Manifest;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends Activity
    implements EasyPermissions.PermissionCallbacks {
    private GoogleAccountCredential mCredential;
    private TextView mOutputText;
    private EditText mWeightInput;
    private EditText mDescriptionInput;
    private EditText mAmountInput;
    private RadioButton mRadioOptionOne;
    private ProgressDialog mProgress;

    private int buttonId = -1;

    private static final int REQUEST_ACCOUNT_PICKER = 1000;
    private static final int REQUEST_AUTHORIZATION = 1001;
    private static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    private static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = { SheetsScopes.SPREADSHEETS };

    /**
     * Create the main activity.
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_layout);

        mOutputText = findViewById(R.id.outputText);
        mWeightInput = findViewById(R.id.weightInput);
        mDescriptionInput = findViewById(R.id.descriptionInput);
        mAmountInput = findViewById(R.id.amountInput);
        mRadioOptionOne = findViewById(R.id.radioOne);

        Button weightButton = findViewById(R.id.weightButton);
        Button debtButton = findViewById(R.id.debtButton);
        weightButton.setOnClickListener(new ButtonClickListener(weightButton, 0));
        debtButton.setOnClickListener(new ButtonClickListener(debtButton, 1));

        findViewById(R.id.viewChartButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showChartWebPage();
            }
        });

        mOutputText.setMovementMethod(new ScrollingMovementMethod());

        mProgress = new ProgressDialog(this);
        mProgress.setMessage(getString(R.string.progress_msg));

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showChartWebPage() {
        Dialog webDialog = new Dialog(this);
        webDialog.setCancelable(true);

        WebView webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.setWebViewClient(new WebViewClient(){
            @Override
            public void onPageFinished(final WebView view, String url) {
                super.onPageFinished(view, url);
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (Build.VERSION.SDK_INT >= 21)
                            view.zoomBy(0.1f);
                    }
                }, 1000);

            }
        });

        webDialog.setContentView(webView);

        webView.loadUrl("file:///android_asset/chart.html");

        webDialog.show();
    }

    private class ButtonClickListener implements View.OnClickListener {
        final Button mButton;
        final int mId;

        ButtonClickListener (Button self, int id) {
            mButton = self;
            mId = id;
        }

        @Override
        public void onClick(View v) {
            mButton.setEnabled(false);
            mOutputText.setText("");
            buttonId = mId;
            getResultsFromApi();
            mButton.setEnabled(true);
        }
    }

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (! isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (! isDeviceOnline()) {
            mOutputText.setText(R.string.network_error);
        } else {
            switch (buttonId) {
                case 0:
                    float weight;
                    try {
                        weight = Float.parseFloat(mWeightInput.getText().toString());
                    } catch (NumberFormatException e) {
                        mOutputText.setText(R.string.weight_error);
                        return;
                    }

                    new WeightTask(mCredential, this).execute(Float.toString(weight));
                    break;
                case 1:
                    String description = mDescriptionInput.getText().toString();
                    if (description.isEmpty()) {
                        mOutputText.setText(R.string.description_error);
                        return;
                    }
                    boolean isOptionOne = mRadioOptionOne.isChecked();
                    float amount;
                    try {
                        amount = Float.parseFloat(mAmountInput.getText().toString());
                    } catch (NumberFormatException e) {
                        mOutputText.setText(R.string.amount_error);
                        return;
                    }

                    new DebtTask(mCredential, this).execute(description, Boolean.toString(isOptionOne), Float.toString(amount));
                    break;
            }
            buttonId = -1;
        }
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mOutputText.setText(
                            R.string.google_play_error);
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr == null)
            return false;
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    private void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    /**
     * An asynchronous task that handles the Google Sheets API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private static abstract class MakeRequestTask extends AsyncTask<String, Void, List<String>> {
        @NonNull
        final com.google.api.services.sheets.v4.Sheets mService;
        @Nullable
        private Exception mLastError = null;
        @NonNull
        final WeakReference<MainActivity> mMainActivity;
        @NonNull
        final String spreadsheetId;

        MakeRequestTask(GoogleAccountCredential credential, MainActivity activity, int spreadsheetResId) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.sheets.v4.Sheets.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Google Sheets API Android Quickstart")
                    .build();
            mMainActivity = new WeakReference<>(activity);
            spreadsheetId = mMainActivity.get().getString(spreadsheetResId);
        }

        /**
         * Background task to call Google Sheets API.
         * @param params no parameters needed for this task.
         */
        @Nullable
        @Override
        protected List<String> doInBackground(String... params) {
            try {
                return task(params);
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch spreadsheet:
         * https://docs.google.com/spreadsheets/d/1DPmqoIzKe9sUA7-xwAc0aWJMNAe29h9UrjoubEu3bw8/edit
         * @return List of names and majors
         * @throws IOException If spreadsheet can't be read
         */
        private int getNextID(@NonNull String spreadsheetId) throws IOException {
            String range = "A1:A100";

            ValueRange response = this.mService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

            List<List<Object>> values = response.getValues();
            int nextID = 100;
            if (values != null) {
                Log.d("SheetPI", "Received:" + values.toString());

                nextID = values.size() + 1;
            }

            Log.d("SheetPI", "Got NextID: " + nextID);

            return nextID;
        }

        @NonNull
        abstract List<String> task(String... params) throws IOException;

        @NonNull
        private List<String> appendToSheet(@NonNull String spreadsheetId, @NonNull String range, List<List<Object>> values) throws IOException {
            Log.d("SheetPI", "Attempting to write: values=" + values.toString());

            ValueRange content = new ValueRange();
            content.setValues(values);

            UpdateValuesResponse response = this.mService.spreadsheets().values()
                    .update(spreadsheetId, range, content).setIncludeValuesInResponse(true).setValueInputOption("USER_ENTERED")
                    .execute();

            List<String> text = new ArrayList<>();
            text.add(response.toString());
            return text;
        }


        @Override
        protected void onPreExecute() {
            mMainActivity.get().mOutputText.setText("");
            mMainActivity.get().mProgress.show();
        }

        @Override
        protected void onPostExecute(@Nullable List<String> output) {
            mMainActivity.get().mProgress.hide();
            if (output == null || output.size() == 0) {
                mMainActivity.get().mOutputText.setText(R.string.empty_result_error);
            } else {
                output.add(0, "Data retrieved using the Sheets API:");
                output.add("Done.");
                String text = TextUtils.join("<br><br>", output);

                // Highlight our data (w/ HTML)
                @SuppressWarnings({"RegExpRedundantEscape"})
                String highlight = "\\[\\[\".*\\\"\\]\\]";
                String replace = "<font color='red'>$0</font>";
                text = text.replaceAll(highlight, replace);

                if (Build.VERSION.SDK_INT >= 24)
                    mMainActivity.get().mOutputText.setText(Html.fromHtml(text, Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH));
                else
                    mMainActivity.get().mOutputText.setText(Html.fromHtml(text));
            }
        }

        @Override
        protected void onCancelled() {
            mMainActivity.get().mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    mMainActivity.get().showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    mMainActivity.get().startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    mMainActivity.get().mOutputText.setText(String.format(mMainActivity.get().getString(R.string.exception_error), mLastError.toString()));
                    Log.e("SheetPI", "Error:", mLastError);
                }
            } else {
                mMainActivity.get().mOutputText.setText(R.string.cancelled);
            }
        }
    }

    private static class WeightTask extends MakeRequestTask {
        WeightTask(GoogleAccountCredential credentials, MainActivity activity) {
            super(credentials, activity, R.string.weight_sheet_id);
        }

        @NonNull
        List<String> task(String... params) throws IOException {
            float weight = Float.parseFloat(params[0]);

            int nextID = super.getNextID(spreadsheetId);
            String range = "A"+nextID+":B"+nextID;

            String dateTime = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US).format(new Date());

            List<List<Object>> values = new ArrayList<>();
            ArrayList<Object> row = new ArrayList<>();
            row.add(0, dateTime);
            row.add(1, weight);
            values.add(row);

            return super.appendToSheet(spreadsheetId, range, values);
        }
    }

    private static class DebtTask extends MakeRequestTask {
        DebtTask(GoogleAccountCredential credentials, MainActivity activity) {
            super(credentials, activity, R.string.debt_sheet_id);
        }

        @NonNull
        List<String> task(String... params) throws IOException {
            String description = params[0];
            String isOptionOne_str = params[1];
            String amount_str = params[2];

            boolean isFirstOption = isOptionOne_str.toLowerCase().equals("true");
            float amount = Float.parseFloat(amount_str);

            int nextID = super.getNextID(spreadsheetId);
            String range = "A"+nextID+":C"+nextID;

            List<List<Object>> values = new ArrayList<>();
            ArrayList<Object> row = new ArrayList<>();
            row.add(0, description);
            row.add(1, isFirstOption ? amount : "");
            row.add(2, !isFirstOption ? amount : "");
            values.add(row);

            super.appendToSheet(spreadsheetId, range, values);

            // Read difference
            ValueRange response = this.mService.spreadsheets().values()
                    .get(spreadsheetId, "D5:E6")
                    .execute();

            List<List<Object>> _values = response.getValues();
            ArrayList<String> output = new ArrayList<>();
            if (_values != null) {
                Log.d("SheetPI", "Received:" + _values.toString());

                output.add("");
                output.add(_values.get(0).get(0) + ":   " + _values.get(0).get(1));
                output.add(_values.get(1).get(0) + ":   " + _values.get(1).get(1));
                output.add("");
            } else
                output.add("No output available :(");

            return output;
        }
    }
}