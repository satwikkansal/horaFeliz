package brainbreaker.cardscanner;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.paytm.pgsdk.PaytmMerchant;
import com.paytm.pgsdk.PaytmOrder;
import com.paytm.pgsdk.PaytmPGService;
import com.paytm.pgsdk.PaytmPaymentTransactionCallback;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is the sample app which will make use of the PG SDK. This activity will
 * show the usage of Paytm PG SDK API's.
 **/

public class MerchantActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.merchantapp);
		initOrderId();
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        Button button = (Button) findViewById(R.id.start_transaction);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onStartTransaction();
            }
        });
//        String ocrResult = getIntent().getStringExtra("ocrResult");
//        Pattern p = Pattern.compile("-?\\d+");
//        Matcher m = p.matcher(ocrResult);
//        while (m.find()) {
//            if (m.group().length()==4){
//                System.out.println(m.group());
//                String cardNumber = "";
//                cardNumber = cardNumber + " "+ m.group();
//                if (cardNumber.length() == 20){
//                    Toast.makeText(this,"Your Card Number is:"+ cardNumber,Toast.LENGTH_SHORT).show();
//                }
//            }
//            else if (m.group().length()==2){
//                System.out.println(m.group());
//                String expiry = "";
//                expiry = expiry+ m.group()+"/";
//                if (expiry.length() == 4){
//                    Toast.makeText(this,"Your card is valid till: "+ expiry, Toast.LENGTH_SHORT).show();
//                    return;
//                }
//            }
//        }
	}
	
	//This is to refresh the order id: Only for the Sample App's purpose.
	@Override
	protected void onStart(){
		super.onStart();
		initOrderId();
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        onStartTransaction();
	}
	

	private void initOrderId() {
		Random r = new Random(System.currentTimeMillis());
		String orderId = "4HACK3" + (1 + r.nextInt(2)) * 10000
				+ r.nextInt(10000);
		EditText orderIdEditText = (EditText) findViewById(R.id.order_id);
		orderIdEditText.setText(orderId);
	}

	public void onStartTransaction() {
        PaytmPGService Service = PaytmPGService.getStagingService();
        Map<String, String> paramMap = new HashMap<String, String>();

		// these are mandatory parameters
		paramMap.put("REQUEST_TYPE","DEFAULT");
//		paramMap.put("ORDER_ID", "4HACK374781212");
//		paramMap.put("MID", ((EditText) findViewById(R.id.merchant_id)).getText().toString());
        paramMap.put("ORDER_ID", ((EditText) findViewById(R.id.order_id)).getText().toString());
        System.out.println(((EditText) findViewById(R.id.order_id)).getText().toString());
        paramMap.put("MID", "PayAdd32357802476407");
		paramMap.put("CUST_ID", "1234x");
		paramMap.put("CHANNEL_ID", "WEB");
		paramMap.put("INDUSTRY_TYPE_ID", "Retail");
		paramMap.put("WEBSITE", "AddandPay");
		paramMap.put("TXN_AMOUNT","1000");
		paramMap.put("THEME", "merchant");
		paramMap.put("EMAIL", "gautam@paytm.com");
		paramMap.put("MOBILE_NO", "7777777777");
//        paramMap.put("PAYMENT_DETAILS")
		PaytmOrder Order = new PaytmOrder(paramMap);

		PaytmMerchant Merchant = new PaytmMerchant(
				"https://pguat.paytm.com/paytmchecksum/paytmCheckSumGenerator.jsp",
				"https://pguat.paytm.com/paytmchecksum/paytmCheckSumVerify.jsp");

		Service.initialize(Order, Merchant, null);
        Log.e("Yaha","Tak");
		Service.startPaymentTransaction(MerchantActivity.this, true, true,
				new PaytmPaymentTransactionCallback() {
					@Override
					public void someUIErrorOccurred(String inErrorMessage) {
						// Some UI Error Occurred in Payment Gateway Activity.
						// // This may be due to initialization of views in
						// Payment Gateway Activity or may be due to //
						// initialization of webview. // Error Message details
						// the error occurred.
                        System.out.println("inError "+inErrorMessage);
					}

					@Override
					public void onTransactionSuccess(Bundle inResponse) {
						// After successful transaction this method gets called.
						// // Response bundle contains the merchant response
						// parameters.

						Log.d("LOG", "Payment Transaction is successful " + inResponse);
						Toast.makeText(getApplicationContext(), "Payment Transaction is successful ", Toast.LENGTH_LONG).show();
					}

					@Override
					public void onTransactionFailure(String inErrorMessage,
							Bundle inResponse) {
						// This method gets called if transaction failed. //
						// Here in this case transaction is completed, but with
						// a failure. // Error Message describes the reason for
						// failure. // Response bundle contains the merchant
						// response parameters.
						Log.d("LOG", "Payment Transaction Failed " + inErrorMessage);
						Toast.makeText(getBaseContext(), "Payment Transaction Failed ", Toast.LENGTH_LONG).show();
					}

					@Override
					public void networkNotAvailable() { // If network is not
														// available, then this
														// method gets called.
                        Log.d("LOG", "Payment Transaction Failed due to Network");

                    }

					@Override
					public void clientAuthenticationFailed(String inErrorMessage) {
						// This method gets called if client authentication
						// failed. // Failure may be due to following reasons //
						// 1. Server error or downtime. // 2. Server unable to
						// generate checksum or checksum response is not in
						// proper format. // 3. Server failed to authenticate
						// that client. That is value of payt_STATUS is 2. //
						// Error Message describes the reason for failure.
                        Log.d("LOG", "Payment Auth Failed " + inErrorMessage);
                    }

					@Override
					public void onErrorLoadingWebPage(int iniErrorCode,
													  String inErrorMessage, String inFailingUrl) {

                        Log.d("LOG", "Payment WEbpage " + inErrorMessage);
                    }

					// had to be added: NOTE
					@Override
					public void onBackPressedCancelTransaction() {
						// TODO Auto-generated method stub
					}

				});
	}

    @Override
    protected void onDestroy(){
        super.onDestroy();
    }
}
