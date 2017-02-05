package brainbreaker.cardscanner;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;
import com.microblink.detectors.DetectorResult;
import com.microblink.detectors.points.PointsDetectorResult;
import com.microblink.hardware.camera.VideoResolutionPreset;
import com.microblink.hardware.orientation.Orientation;
import com.microblink.metadata.DetectionMetadata;
import com.microblink.metadata.Metadata;
import com.microblink.metadata.MetadataListener;
import com.microblink.metadata.MetadataSettings;
import com.microblink.metadata.OcrMetadata;
import com.microblink.recognition.InvalidLicenceKeyException;
import com.microblink.recognizers.BaseRecognitionResult;
import com.microblink.recognizers.RecognitionResults;
import com.microblink.recognizers.blinkbarcode.bardecoder.BarDecoderRecognizerSettings;
import com.microblink.recognizers.blinkbarcode.bardecoder.BarDecoderScanResult;
import com.microblink.recognizers.blinkocr.BlinkOCRRecognitionResult;
import com.microblink.recognizers.blinkocr.BlinkOCRRecognizerSettings;
import com.microblink.recognizers.blinkocr.parser.generic.RawParserSettings;
import com.microblink.recognizers.settings.RecognitionSettings;
import com.microblink.recognizers.settings.RecognizerSettings;
import com.microblink.results.ocr.OcrResult;
import com.microblink.util.CameraPermissionManager;
import com.microblink.util.Log;
import com.microblink.view.CameraAspectMode;
import com.microblink.view.CameraEventsListener;
import com.microblink.view.OrientationAllowedListener;
import com.microblink.view.ocrResult.OcrResultCharsView;
import com.microblink.view.recognition.RecognizerView;
import com.microblink.view.recognition.ScanResultListener;
import com.microblink.view.viewfinder.PointSetView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.pedant.SweetAlert.SweetAlertDialog;

import static android.content.ContentValues.TAG;

public class FullScreenOCR extends Activity implements MetadataListener, CameraEventsListener, ScanResultListener {

    // obtain your licence key at http://microblink.com/login or
    // contact us at http://help.microblink.com
    private static final String LICENSE_KEY = "GAS6ZNCH-PKO4DRJC-DRP4RQAR-F6AGCE4N-IESDCPX5-ONFA2Q5K-4N75X6GE-DDJY6KGC";

    /**
     * RecognizerView is the built-in view that controls camera and recognition
     */
    private RecognizerView mRecognizerView;
    /**
     * OcrResultCharsView is built-in view that can display OCR result on top of camera
     */
    private OcrResultCharsView mOcrResultView;
    /**
     * PoinSetView is built-in view that can display points of interest on top of camera
     */
    private PointSetView mPointSetView;
    /**
     * CameraPermissionManager is provided helper class that can be used to obtain the permission to use camera.
     * It is used on Android 6.0 (API level 23) or newer.
     */
    private CameraPermissionManager mCameraPermissionManager;
    OcrResult ocrResult;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;
    String extraString = "";
    Button sendButton;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen_ocr);
        // obtain reference to RecognizerView
        mRecognizerView = (RecognizerView) findViewById(R.id.recognizerView);
        sendButton = (Button) findViewById(R.id.sendButton);
        extraString = getIntent().getStringExtra("someValue");
//        SharedPreferences packagePrefs = getSharedPreferences(this.getPackageName(), Context.MODE_PRIVATE);
//        extraString = packagePrefs.getString("someValue", null);
        if (extraString != null) {
            final SweetAlertDialog sweetAlert = new SweetAlertDialog(this);
            sweetAlert.setTitleText(extraString.replaceAll("_"," ").replaceAll("[0-9]+",""))
                    .setContentText("Label Detected")
                    .show();
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    sweetAlert.dismissWithAnimation();
                }
            }, 1500);
        } else {
            extraString="";
        }

        // set log level to information because ocr results will be passed to Log (information level)
        Log.setLogLevel(Log.LogLevel.LOG_INFORMATION);

        // initialize BlinkOCR recognizer with only raw parser
        BlinkOCRRecognizerSettings ocrSett = new BlinkOCRRecognizerSettings();
        RawParserSettings rawSett = new RawParserSettings();
        // add raw parser with name "Raw" to default parser group
        // parser name is important for obtaining results later
        ocrSett.addParser("Raw", rawSett);

        // initialize 1D barcode recognizer and set it to scan Code39 and Code128 barcodes
        BarDecoderRecognizerSettings barSett = new BarDecoderRecognizerSettings();
        barSett.setScanCode128(true);
        barSett.setScanCode39(true);

        // prepare the recognition settings
        RecognitionSettings recognitionSettings = new RecognitionSettings();
        // BlinkOCRRecognizer and BarDecoderRecognizer will be used in the recognition process
        recognitionSettings.setRecognizerSettingsArray(new RecognizerSettings[]{ocrSett, barSett});

        mRecognizerView.setRecognitionSettings(recognitionSettings);

        // we want each frame to be scanned for both OCR and barcodes so we must
        // allow multiple scan results on single image.
        // If this is not allowed (default), the first recognizer that finds its object
        // of interest stops the recognition chain (for example in that case if barcode is found
        // OCR will not be performed - we do not want this, so we allow multiple scan results
        // on single image).
        recognitionSettings.setAllowMultipleScanResultsOnSingleImage(true);

        // In order for scanning to work, you must enter a valid licence key. Without licence key,
        // scanning will not work. Licence key is bound the the package name of your app, so when
        // obtaining your licence key from Microblink make sure you give us the correct package name
        // of your app. You can obtain your licence key at http://microblink.com/login or contact us
        // at http://help.microblink.com.
        // Licence key also defines which recognizers are enabled and which are not. Since the licence
        // key validation is performed on image processing thread in native code, all enabled recognizers
        // that are disallowed by licence key will be turned off without any error and information
        // about turning them off will be logged to ADB logcat.
        try {
            mRecognizerView.setLicenseKey(LICENSE_KEY);
        } catch (InvalidLicenceKeyException e) {
            e.printStackTrace();
            Toast.makeText(this, "Invalid license key", Toast.LENGTH_SHORT).show();
            finish();
            mRecognizerView = null;
            return;
        }

        // use all available view area for displaying camera, possibly cropping the camera frame
        mRecognizerView.setAspectMode(CameraAspectMode.ASPECT_FILL);
        // use 720p resolution instead of default 1080p to make everything work faster
        mRecognizerView.setVideoResolutionPreset(VideoResolutionPreset.VIDEO_RESOLUTION_720p);

        // configure metadata settings and chose detection metadata
        // that will be passed to metadata listener
        MetadataSettings mdSett = new MetadataSettings();
        // set OCR metadata to be available in metadata listener
        mdSett.setOcrMetadataAllowed(true);
        // enable detection metadata for obtaining points of interest
        mdSett.setDetectionMetadataAllowed(true);
        // metadata listener receives detection metadata during recognition process
        mRecognizerView.setMetadataListener(this, mdSett);
        // camera events listener receives camera events, like when camera preview has started, stopped
        // or if camera error happened
        mRecognizerView.setCameraEventsListener(this);
        // scan result listener receives scan result once it becomes available
        mRecognizerView.setScanResultListener(this);

        // orientation allowed listener is asked whether given orientation
        // is allowed in UI. We keep activity always in portrait, but allow
        // scanning in all orientations.
        mRecognizerView.setOrientationAllowedListener(new OrientationAllowedListener() {
            @Override
            public boolean isOrientationAllowed(Orientation orientation) {
                return true;
            }
        });

        // instantiate the camera permission manager
        mCameraPermissionManager = new CameraPermissionManager(this);
        // get the built-in overlay that should be displayed when camera permission is not given
        View v = mCameraPermissionManager.getAskPermissionOverlay();
        if (v != null) {
            // add it to the current layout that contains the recognizer view
            ViewGroup vg = (ViewGroup) findViewById(R.id.full_screen_root);
            vg.addView(v);
        }

        // all activity lifecycle events must be passed on to RecognizerView
        mRecognizerView.create();

        // create OCR result view
        mOcrResultView = new OcrResultCharsView(this, null, mRecognizerView.getHostScreenOrientation());

        // OCR result view will be added as child of recognizer view. This makes sure that if
        // recognizer view letter-boxes the camera preview (ASPECT_FIT camera mode), the OCR
        // result view will be laid out exactly above camera preview
        // Note that we can add child views to RecognizerView only after we called create on it.
        // The boolean parameter defines whether added view will be rotated with device. Allowed
        // orientations are defined with OrientationAllowedListener.
        mRecognizerView.addChildView(mOcrResultView, false);

        // we do the same with PointSetView
        mPointSetView = new PointSetView(this, null, mRecognizerView.getHostScreenOrientation());
        mRecognizerView.addChildView(mPointSetView, false);

//        new Timer().schedule(new TimerTask() {
//            @Override
//            public void run() {
//                startPaymentActivity(ocrResult.toString());
//            }
//        },5000);
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    @Override
    protected void onStart() {
        super.onStart();// ATTENTION: This was auto-generated to implement the App Indexing API.
// See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        // all activity lifecycle events must be passed on to RecognizerView
        if (mRecognizerView != null) {
            mRecognizerView.start();
        }
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        AppIndex.AppIndexApi.start(client, getIndexApiAction());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // all activity lifecycle events must be passed on to RecognizerView
        if (mRecognizerView != null) {
            mRecognizerView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // all activity lifecycle events must be passed on to RecognizerView
        if (mRecognizerView != null) {
            mRecognizerView.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();// ATTENTION: This was auto-generated to implement the App Indexing API.
// See https://g.co/AppIndexing/AndroidStudio for more information.
        AppIndex.AppIndexApi.end(client, getIndexApiAction());
        // all activity lifecycle events must be passed on to RecognizerView
        if (mRecognizerView != null) {
            mRecognizerView.stop();
        }
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // all activity lifecycle events must be passed on to RecognizerView
        if (mRecognizerView != null) {
            mRecognizerView.destroy();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // all activity lifecycle events must be passed on to RecognizerView
        if (mRecognizerView != null) {
            mRecognizerView.changeConfiguration(newConfig);
            mOcrResultView.setHostActivityOrientation(mRecognizerView.getHostScreenOrientation());
        }
    }

    @Override
    public void onScanningDone(RecognitionResults results) {
        // called when scanning completes. In this example, we first check if dataArray contains
        // barcode result and display a barcode contents in the Toast.
        // We also check if dataArray contains raw parser result and log it to ADB.
        BaseRecognitionResult[] dataArray = results.getRecognitionResults();
        Log.e("Scan", "Scan");
        for (BaseRecognitionResult r : dataArray) {
            Log.e("Scan", "Scan2");
            if (r instanceof BarDecoderScanResult) { // r is barcode scan result
                BarDecoderScanResult bdsr = (BarDecoderScanResult) r;

                // create toast with contents: Barcode type: barcode contents
                String res = bdsr.getBarcodeType().name() + ": " + bdsr.getStringData();
                Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
            } else if (r instanceof BlinkOCRRecognitionResult) {
                BlinkOCRRecognitionResult bocrRes = (BlinkOCRRecognitionResult) r;

                // obtain parse result from the parser named "Raw"
                String rawParsed = bocrRes.getParsedResult("Raw");
                Log.i("Parsed", rawParsed);

                // obtain OCR result that was used for parsing
                ocrResult = bocrRes.getOcrResult();
                Log.i("OcrResult", ocrResult.toString().replaceAll("[^A-Za-z0-9$\\s]+",""));
                sendButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        JSONObject data = new JSONObject();
                        try {
                            data.put("label_data", extraString.replaceAll("_"," ") /*"Hello World"*/);
                            data.put("ocr_data" , ocrResult.toString().replaceAll("[^A-Za-z0-9$\\s]+","") /*"helloworld 123"*/);
                        }catch (JSONException e){
                            e.printStackTrace();
                        }
                        String jsonString = data.toString();
                        System.out.println("JSON STRING: "+ jsonString);
                        try {
                            new CallAPITask().execute(jsonString);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });


//                Pattern p = Pattern.compile("(\\d{4})");
//                Matcher m = p.matcher(ocrResult.toString());
//                while (m.find()) {
//                    if (m.group().length()==4){
//                        System.out.println(m.group());
//                        String cardNumber = "";
//                        cardNumber = cardNumber + " "+ m.group();
//                        if (cardNumber.length() == 20){
//                            Toast.makeText(FullScreenOCR.this,"Your Card Number is:"+ cardNumber,Toast.LENGTH_SHORT).show();
//                        }
//                    }
//                    else if (m.group().length()==2){
//                        System.out.println(m.group());
//                        String expiry = "";
//                        expiry = expiry+ m.group()+"/";
//                        if (expiry.length() == 4){
//                            Toast.makeText(FullScreenOCR.this,"Your card is valid till: "+ expiry, Toast.LENGTH_SHORT).show();
//                            return;
//                        }
//                    }
//                }
            }
        }
        // Finally we reset recognizer's internal state so it will not combine old recognition
        // results with new one. Scanning is resumed automatically after this method ends.
        mRecognizerView.resetRecognitionState();
    }

    // Uses AsyncTask to create a task away from the main UI thread.
    private class CallAPITask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            // params comes from the execute() call: params[0] is the url.
            try {
                return callAPI(params[0]);
            } catch (Exception e) {
                return "Unable to retrieve web page. URL may be invalid.";
            }
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            android.util.Log.d(TAG, "onPost " + result);
            String labelResult = "false";
            String priceResult = "false";
            try {
                JSONObject resultObject = new JSONObject(result);
                labelResult = resultObject.getString("label_match");
                Log.e("labelResult: ", labelResult);
                priceResult = resultObject.getString("prices_match");
                Log.e("priceResult", priceResult);
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("JSON: ", "Couldn't parse JSON");
            }

            if (labelResult.equals("true") && priceResult.equals("true")){
                new SweetAlertDialog(FullScreenOCR.this, SweetAlertDialog.SUCCESS_TYPE)
                        .setTitleText("Success!")
                        .setContentText("Both label and price are correct.")
                        .show();
            }
            else if(labelResult.equals("false") && priceResult.equals("true")){
                new SweetAlertDialog(FullScreenOCR.this, SweetAlertDialog.ERROR_TYPE)
                        .setTitleText("Mismatch!")
                        .setContentText("Label mismatch occurred")
                        .show();
            }
            else if(labelResult.equals("true") && priceResult.equals("false")){
                new SweetAlertDialog(FullScreenOCR.this, SweetAlertDialog.ERROR_TYPE)
                        .setTitleText("Mismatch!")
                        .setContentText("Price mismatch occurred")
                        .show();
            }
            else{
                new SweetAlertDialog(FullScreenOCR.this, SweetAlertDialog.ERROR_TYPE)
                        .setTitleText("Mismatch!")
                        .setContentText("Price and Label mismatch occurred")
                        .show();
            }
        }
    }


    private String callAPI(String jsonString) throws Exception {
//        jsonString = "hello world";
        String stringUrl = "https://hacktheworldagain.herokuapp.com/verify";
        stringUrl = "http://ef3958ea.ngrok.io/verify";
        URL url = new URL(stringUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
//        conn.setRequestProperty("Content-Type", "application/json");
//        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        Writer writer = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream(), "UTF-8"));
        writer.write(jsonString);
        writer.close();
//        OutputStream os = conn.getOutputStream();
//        os.write(jsonString.getBytes());
//        os.flush();

        StringBuilder sb = new StringBuilder();
        int HttpResult = conn.getResponseCode();
        android.util.Log.d(TAG, String.valueOf(HttpResult));
        System.out.println("HTTP Result: "+ String.valueOf(HttpResult));
        if (HttpResult == HttpURLConnection.HTTP_OK) {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            String line = null;
            while ((line = br.readLine()) != null) {
                android.util.Log.d(TAG, line + "\n");
                System.out.println("Lines: "+ line);
                sb.append(line);
            }
            br.close();
//            Intent launchIntent = getPackageManager().getLaunchIntentForPackage("brainbreaker.cardscanner1");
//            launchIntent.putExtra("responseString", sb.toString());
//            if (launchIntent!=null){
//                startActivity(launchIntent);
//            }
            return sb.toString();
        } else {
            android.util.Log.d(TAG, conn.getResponseMessage());
            return conn.getResponseMessage();
        }
    }

    public Runnable runnable = new Runnable() {
        @Override
        public void run() {
            Log.e("this", "scan");

        }
    };

    private void startPaymentActivity(String ocrResult) {
        SystemClock.sleep(9000);
        Intent intent = new Intent(FullScreenOCR.this, MerchantActivity.class);
        intent.putExtra("ocrResult", ocrResult);
        FullScreenOCR.this.startActivity(intent);
    }

    @Override
    public void onCameraPreviewStarted() {
        // called immediately after camera preview has been started. This is useful
        // if you display splash screen in your app while loading camera. In this method
        // you should then remove the splash screen.
    }

    @Override
    public void onCameraPreviewStopped() {
        // called immediately after camera preview has been stopped. This is useful
        // if you want to release some resources that are required only while camera preview
        // is active.
    }

    @Override
    public void onError(Throwable ex) {
        // This method will be called when opening of camera resulted in exception or
        // recognition process encountered an error.
        // The error details will be given in exc parameter.
        Log.e(this, ex, "Error");
        Toast.makeText(this, "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    @TargetApi(23)
    public void onCameraPermissionDenied() {
        // this method is called on Android 6.0 and newer if camera permission was not given
        // by user

        // ask user to give a camera permission. Provided manager asks for
        // permission only if it has not been already granted.
        // on API level < 23, this method does nothing
        mCameraPermissionManager.askForCameraPermission();
    }

    @Override
    public void onMetadataAvailable(Metadata metadata) {
        // This method will be called when metadata becomes available during recognition process.
        // Here, for every metadata type that is allowed through metadata settings,
        // desired actions can be performed.
        if (mOcrResultView != null && metadata instanceof OcrMetadata) {
            // get the ocr result and show it inside ocr result view
            mOcrResultView.setOcrResult(((OcrMetadata) metadata).getOcrResult());
        } else if (mPointSetView != null && metadata instanceof DetectionMetadata) {
            // detection metadata contains detected points of interest
            // points are written inside DetectorResult
            DetectorResult detectorResult = ((DetectionMetadata) metadata).getDetectionResult();
            // DetectorResult can be null - this means that detection has failed
            if (detectorResult == null) {
                // clear points
                mPointSetView.setPointsDetectionResult(null);
            } else if (detectorResult instanceof PointsDetectorResult) {
                // show the points of interest inside point set view
                mPointSetView.setPointsDetectionResult((PointsDetectorResult) detectorResult);
            } // else if (detectorResult instanceof QuadDetectorResult) { ... }
            // detection location (e.g. PDF417 barcode) will be returned as QuadDetectorResult
            // here we expect only points of interest
        }
    }

    @Override
    public void onAutofocusFailed() {
        // This method will be called when camera focusing has failed.
        // Camera manager usually tries different focusing strategies and this method is called when all
        // those strategies fail to indicate that either object on which camera is being focused is too
        // close or ambient light conditions are poor.
    }

    @Override
    public void onAutofocusStarted(Rect[] rects) {
        // This method will be called when camera focusing has started.
        // You can utilize this method to draw focusing animation on UI.
        // Areas parameter is array of rectangles where focus is being measured.
        // It can be null on devices that do not support fine-grained camera control.
    }

    @Override
    public void onAutofocusStopped(Rect[] rects) {
        // This method will be called when camera focusing has stopped.
        // You can utilize this method to remove focusing animation on UI.
        // Areas parameter is array of rectangles where focus is being measured.
        // It can be null on devices that do not support fine-grained camera control.
    }

    @Override
    @TargetApi(23)
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // on API level 23, request permission result should be passed to camera permission manager
        mCameraPermissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    public Action getIndexApiAction() {
        Thing object = new Thing.Builder()
                .setName("FullScreenOCR Page") // TODO: Define a title for the content shown.
                // TODO: Make sure this auto-generated URL is correct.
                .setUrl(Uri.parse("http://[ENTER-YOUR-URL-HERE]"))
                .build();
        return new Action.Builder(Action.TYPE_VIEW)
                .setObject(object)
                .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                .build();
    }
}
