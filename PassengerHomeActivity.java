package com.miniride.activities;

import android.app.ActivityManager;
import android.content.*;
import com.miniride.Miniride;
import com.miniride.controllers.*;
import com.miniride.widget.MultiDirectionSlidingDrawer;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;

import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.*;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.OverlayItem;
import com.miniride.R;
import com.miniride.api.ApiRequest;
import com.miniride.api.ApiResponse;
import com.miniride.asyncTasks.GetDirectionsAsyncTask;
import com.miniride.asyncTasks.SimpleApiRequestAsyncTask;
import com.miniride.map.MapItemizedOverlay;
import com.miniride.map.ShowRouteOverlay;
import com.miniride.models.*;
import com.miniride.pushNotifications.PushReceiver;
import com.miniride.utilities.*;
import com.miniride.utilities.OnMoveOverlay.OnMapMoveListener;

import java.text.DecimalFormat;
import java.util.List;

public class PassengerHomeActivity extends LocationActivity implements OnClickListener {
    private class PassengerActionsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, Intent intent) {
            final PushReceiver.Event event = PushReceiver.Event.valueOf(intent.getAction());
            switch (event) {
                case ORDER_ACCEPTED_BY_DRIVER: {
                    passengerSearchesDriverController.stop();
                    passengerOrderIsAcceptedController.start();
                    passengerDisplayDriverLocationController.start();
                    Utils.displayMsg(PassengerHomeActivity.this, getString(R.string.orderAcceptedByDriver));
                    break;
                }
                case ORDER_CANCELLED_BY_DRIVER: {
                    passengerOrderIsAcceptedController.stop();
                    passengerDisplayDriverLocationController.stop();
                    createOrderController.start();
                    Utils.displayMsg(PassengerHomeActivity.this, getString(R.string.orderCancelledByDriver));
                    break;
                }
                case ORDER_ABORTED_BY_DRIVER: {
                    passengerDriverHasArrivedController.stop();
                    passengerDisplayDriverLocationController.stop();
                    Utils.displayMsg(PassengerHomeActivity.this, getString(R.string.orderCancelledByDriver));
                    break;
                }
                case ORDER_DRIVER_ARRIVED: {
                    passengerOrderIsAcceptedController.stop();
                    passengerDriverHasArrivedController.start();
                    for (int i=0; i < 2; i++)
                        Utils.displayMsg(PassengerHomeActivity.this, getString(R.string.orderDriverArrived));
                    break;
                }
                case ORDER_PASSENGER_HAS_BOARDED: {
                    passengerDriverHasArrivedController.stop();
                    break;
                }
                case ORDER_RIDE_ENDED: {
                    passengerDisplayDriverLocationController.stop();
                    Utils.displayMsg(PassengerHomeActivity.this, getString(R.string.orderRideEnded));
                    startActivity(new Intent(PassengerHomeActivity.this, PassengerPaysActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .putExtra("driverUuid",orderDriverToken));
                    clearAddressesBars();
                    break;
                }
                case ORDER_NO_DRIVER_AVAILABLE: {
                    passengerSearchesDriverController.stop();
                    Utils.displayMsg(PassengerHomeActivity.this, getString(R.string.orderAllDriversAreBusy));
                    break;
                }
            }
        }
    }

    private PassengerActionsReceiver passengerActionsReceiver;
    private IntentFilter passengerActionsIntentFilter;
    boolean fromAddressChange;

    private Button menu;
    private RelativeLayout barChangePickupAddress, barChangeDestinationAddress;
    private ImageButton imageButtonOrderNow, imageButtonHomeSearch;
    private ImageView centerOverlay;

    private ShowRouteOverlay showRouteOverlay;
    private MapItemizedOverlay itemizedOverlay;

    private final static int becomeADriverDialogDelay = 10000;

    private final OnMapMoveListener mapListener = new OnMapMoveListener() {
        @Override
        public void mapMovingFinishedEvent(final double latitude, final double longitude) {
            PassengerHomeActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (createOrderController.destinationAddressNotSet())
                        setTextViewPickupAddress(GeoHelper.getAddressFromCoordinates(latitude / 1e6, longitude / 1e6));
                }
            });
        }
    };

    private final SimpleApiRequestAsyncTask.OnApiRequestFinishedListener driversInVicinityListener = new SimpleApiRequestAsyncTask.OnApiRequestFinishedListener() {
        @Override
        public void onFinished(ApiResponse response) {
            boolean enoughDriversInVicinity = false;
            try {
                enoughDriversInVicinity = response.getJson().getBoolean("enough_drivers_in_vicinity");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if(!enoughDriversInVicinity) showBecomeADriverDialog();
        }
    };

    public String orderDriverToken;
    private RelativeLayout relativeLayoutHome,relativeLayoutCreateOrder, relativeLayoutOrderAndNext;
    private TextView textViewDestinationAddress, textViewPickupAddress, textViewNext;

    public AddressData pickupAddressData, destinationAddressData;

    public PassengerCreatesOrderController createOrderController;
    private PassengerSearchesDriverController passengerSearchesDriverController;
    public PassengerOrderIsAcceptedController passengerOrderIsAcceptedController;
    private PassengerDriverHasArrivedController passengerDriverHasArrivedController;
    private PassengerDisplayDriverLocationController passengerDisplayDriverLocationController;

    private boolean isFlashMessageShown;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CurrentUser.setAsPassenger();
        setContentView(R.layout.passenger_home);
        Miniride.setPassengerHomeActivity(this);

        initViews();
        initControllers();
        setListeners();
        initializeLayoutAndControls();

        isFlashMessageShown = false;
        if (savedInstanceState != null){
            isFlashMessageShown = savedInstanceState.getBoolean("flashMessageShown", false);
            if (isFlashMessageShown){
               Utils.removeFlashMessage(getIntent());
            }
        }

        initReceivers();

        if (showStartBecomeADriverDialog()) startBecomeADriverDialogHandler();
    }

    public void clearOrderScreen(){
        if (this.relativeLayoutCreateOrder.getVisibility()!=View.GONE)
            this.createOrderController.stop();
        else
            finish();
    }

    public void setOrderDriverUuid(String driverUuid){
        orderDriverToken = driverUuid;
    }

    private void startBecomeADriverDialogHandler() {
        this.handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                resetAppStartFlag();
                new SimpleApiRequestAsyncTask(ApiRequest.DRIVER_IN_VICINITY_PATH, HttpGet.METHOD_NAME, driversInVicinityListener)
                    .execute(new BasicNameValuePair("latitude", pickupAddressData.latitude.toString()),
                             new BasicNameValuePair("longitude", pickupAddressData.longitude.toString()));
            }
        }, becomeADriverDialogDelay);
    }

    private boolean showStartBecomeADriverDialog() {
        // DISABLED FOR NOW. To reenable:
        // return appHasJustBeenStarted() && Driver.noDriverAccountCreatedYet() && Passenger.currentPassengerExists();
        return false;
    }

    private boolean appHasJustBeenStarted() {
        return Prefs.getBool("appStart");
    }

    private void resetAppStartFlag() {
        Prefs.saveBool("appStart", false);
    }

    private void showBecomeADriverDialog() {
        ActivityManager am = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        List< ActivityManager.RunningTaskInfo > taskInfo = am.getRunningTasks(1);
        if (taskInfo.get(0).topActivity.getClassName().equals("com.miniride.activities.PassengerHomeFragmentActivity")){
            Utils.showYesNoAlert(this, R.string.becomeADriver, R.string.incentiveForDriverSignUp, R.string.becomeADriver, R.string.close,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (Passenger.currentPassengerExists()) {
                                startActivity(new Intent(PassengerHomeActivity.this, DriverCreateAccountActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                            } else {
                                startActivity(new Intent(PassengerHomeActivity.this, PassengerCreateAccountActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                            }
                        }
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    });
        }
    }

    @Override
    protected void onSaveInstanceState (Bundle outState){
        super.onSaveInstanceState(outState);
        outState.putBoolean("flashMessageShown", isFlashMessageShown);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(passengerActionsReceiver);
        super.onPause();
        stopControllers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestLocationUpdates(DEFAULT_MIN_DISTANCE);
        fromAddressChange = false;
        Miniride.getPassengerHomeFragmentActivityInstance().handleAddressChange();
        String message = Utils.getFlashMessage(getIntent());
        if (message != null) {
            Utils.displayMsg(this, message);
            Utils.removeFlashMessage(getIntent());
            isFlashMessageShown = true;
        }
        startOrderProcessController();
        registerReceiver(passengerActionsReceiver, passengerActionsIntentFilter);
    }

    private void initReceivers() {
        passengerActionsReceiver = new PassengerActionsReceiver();
        passengerActionsIntentFilter = new IntentFilter();
        passengerActionsIntentFilter.addAction(PushReceiver.Event.ORDER_ACCEPTED_BY_DRIVER.name());
        passengerActionsIntentFilter.addAction(PushReceiver.Event.ORDER_CANCELLED_BY_DRIVER.name());
        passengerActionsIntentFilter.addAction(PushReceiver.Event.ORDER_ABORTED_BY_DRIVER.name());
        passengerActionsIntentFilter.addAction(PushReceiver.Event.ORDER_DRIVER_ARRIVED.name());
        passengerActionsIntentFilter.addAction(PushReceiver.Event.ORDER_PASSENGER_HAS_BOARDED.name());
        passengerActionsIntentFilter.addAction(PushReceiver.Event.ORDER_RIDE_ENDED.name());
        passengerActionsIntentFilter.addAction(PushReceiver.Event.ORDER_NO_DRIVER_AVAILABLE.name());
    }

    private void initializeLayoutAndControls() {
        FrameLayout.LayoutParams zoomParams = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        ZoomControls controlls = (ZoomControls) this.map.getZoomButtonsController().getZoomControls();
        controlls.setGravity(Gravity.CENTER);
        int density = (int) (getResources().getInteger(R.integer.controllsPadding) * this.getResources().getDisplayMetrics().density);
        controlls.setPadding(0, 0, 0, density);
        controlls.setLayoutParams(zoomParams);
        this.map.getController().setZoom(getResources().getInteger(R.integer.passengerMapZoom));
        this.map.getOverlays().add(new OnMoveOverlay(this.mapListener, null));
        this.showRouteOverlay = new ShowRouteOverlay();
        this.map.getOverlays().add(this.showRouteOverlay);
        this.itemizedOverlay = new MapItemizedOverlay(getResources().getDrawable(R.drawable.pin_passenger_start));
        this.map.getOverlays().add(this.itemizedOverlay);
        showHomeMap();
        clearAddressesBars();
    }

    private void initViews() {
        this.map = (UserMapView) findViewById(R.id.mapViewHome);
        this.imageButtonHomeSearch = (ImageButton) findViewById(R.id.imageButtonHomeSearch);
        this.barChangePickupAddress = (RelativeLayout) findViewById(R.id.bar_change_accept_pickup_address);
        this.relativeLayoutCreateOrder = (RelativeLayout) findViewById(R.id.relativeLayoutCreateOrder);
        this.barChangeDestinationAddress = (RelativeLayout) findViewById(R.id.bar_change_accept_dest_address);
        this.relativeLayoutHome = (RelativeLayout) findViewById(R.id.relativeLayoutHome);
        this.menu = (Button) findViewById(R.id.buttonMenu);
        this.imageButtonOrderNow = (ImageButton) findViewById(R.id.imageButtonOrderNow);
        this.textViewDestinationAddress = (TextView) findViewById(R.id.txt_destAddress);
        this.textViewPickupAddress = (TextView) findViewById(R.id.txt_pickupAddress);
        this.centerOverlay = (ImageView) findViewById(R.id.center_overlay);
        this.pickupAddressData = null;
        this.destinationAddressData = null;

        this.relativeLayoutOrderAndNext = (RelativeLayout)findViewById(R.id.relativeLayoutOrderAndNext);
        this.textViewNext = (TextView)findViewById(R.id.textViewNext);
        this.relativeLayoutHome = (RelativeLayout)findViewById(R.id.relativeLayoutHome);
    }

    private void initControllers() {
        createOrderController = new PassengerCreatesOrderController(this, new PassengerCreatesOrderController.OnOrderCreatedListener() {
            @Override
            public void onCreated() {
                PassengerHomeActivity.this.showRouteOverlay.updateDirections(null);
                clearTransferPositionsOnMap();
                passengerSearchesDriverController.start();
            }
        });
        passengerSearchesDriverController = new PassengerSearchesDriverController(this, null);
        passengerOrderIsAcceptedController = new PassengerOrderIsAcceptedController(this, new PassengerOrderIsAcceptedController.OnCancelledOrderListener() {
            @Override
            public void onCancelled() {
                passengerDisplayDriverLocationController.stop();
            }
        });
        passengerDriverHasArrivedController = new PassengerDriverHasArrivedController(this);
        passengerDisplayDriverLocationController = new PassengerDisplayDriverLocationController(this);
    }

    private void setListeners() {
        this.imageButtonOrderNow.setOnClickListener(this);
        this.menu.setOnClickListener(this);
        this.barChangeDestinationAddress.setOnClickListener(this);
        this.barChangePickupAddress.setOnClickListener(this);
        this.imageButtonHomeSearch.setOnClickListener(this);

        this.map.setBuiltInZoomControls(true);
    }

    @Override
    public void onClick(final View view) {
        switch (view.getId()) {
            case R.id.bar_change_accept_pickup_address:
                if (!Passenger.currentPassengerExists())
                    startActivity(new Intent(this, PassengerCreateAccountActivity.class));
                else {
                    startActivity(buildIntentForSelectingPickupAddress());
                }
                break;
            case R.id.bar_change_accept_dest_address:
                startActivity(buildIntentForSelectingDestinationAddress());
                break;
            case R.id.imageButtonHomeSearch:
                Location location = getCurrentLocation();
                setMapToLocation(location);
                setPickupAddressToLocation(location);
                break;
            case R.id.imageButtonOrderNow:
                if (!Passenger.currentPassengerExists()) {
                    startActivity(new Intent(this, PassengerCreateAccountActivity.class));
                } else if (!Order.noCurrentOrderFor(Role.PASSENGER)){
                    Utils.displayMsg(this, R.string.alreadyHaveAnOrder);
                } else if(createOrderController.validateTransferPoints()) {
                    createOrderController.start();
                } else {
                    startActivity(buildIntentForSelectingDestinationAddress());
                }
                break;
            case R.id.buttonMenu:
        }
    }

    private Intent buildIntentForSelectingDestinationAddress() {
        Intent i = new Intent(this, SelectAddressActivity.class);
        i.putExtra("pickupAddressData", pickupAddressData.toJson());
        i.putExtra("startedForPickupAddress", false);
        i.putExtra("headerTitle",getString(R.string.giveDestination));
        return i;
    }

    private Intent buildIntentForSelectingPickupAddress() {
        Intent i = new Intent(this, SelectAddressActivity.class);
        if (!destinationAddressData.empty()){
            i.putExtra("startedForPickupAddressWithDestinationPresent", true);
            i.putExtra("destinationAddressData", destinationAddressData.toJson());
        } else {
            i.putExtra("startedForPickupAddress", true);
        }
        i.putExtra("headerTitle",getString(R.string.givePickupAddress));
        return i;
    }

    public void updateMap(final double lat, final double lng) {
        this.map.getController().animateTo(new GeoPoint((int) (lat * 1E6), (int) (lng * 1E6)));
    }

    private void setTextViewPickupAddress(final Address address) {
        this.pickupAddressData.updateFromAddress(address);
        this.textViewPickupAddress.setText(this.pickupAddressData.street);
    }

    private void setTextViewDestinationAddress(final Address address) {
        this.destinationAddressData.updateFromAddress(address);
        this.textViewDestinationAddress.setText(this.destinationAddressData.street);
    }

    private void showHomeMap() {
        this.relativeLayoutHome.setVisibility(View.VISIBLE);
    }

    public Location getCurrentLocation() {
        Location location = null;
        LocationRequest lRequest = new LocationRequest();
        if (lRequest.hasKnownLocation()) {
            location = lRequest.getLocation(getResources().getInteger(R.integer.homeLocationSenderInterval));
        }
        return location;
    }

    private void setMapToLocation(Location location) {
        if (location != null){
            updateMap(location.getLatitude(), location.getLongitude());
        }
    }

    public void setPickupAddressToLocation(Location location) {
        if (location != null){
            setTextViewPickupAddress(GeoHelper.getAddressFromCoordinates(location.getLatitude(), location.getLongitude()));
        }
    }

    public void setDestinationAddressToLocation(Location location) {
        if (location != null){
            setTextViewDestinationAddress(GeoHelper.getAddressFromCoordinates(location.getLatitude(), location.getLongitude()));
        }
    }

    @Override
    public void updateWithLocation(Location location){
        if(!this.fromAddressChange) {
            setMapToLocation(location);
            setPickupAddressToLocation(location);
        }
        stopLocationUpdates();
    }

    private void startOrderProcessController() {
        if (Passenger.loadCurrent().hasCurrentOrder()) {
            if (this.passengerSearchesDriverController.shouldHandleCurrentOrder()) {
                this.passengerSearchesDriverController.start();
            } else if (this.passengerOrderIsAcceptedController.shouldHandleCurrentOrder()) {
                this.passengerOrderIsAcceptedController.start();
            } else if (this.passengerDriverHasArrivedController.shouldHandlerCurrentOrder()) {
              this.passengerDriverHasArrivedController.start();
            }if (this.passengerDisplayDriverLocationController.shouldHandleCurrentOrder()) {
                this.passengerDisplayDriverLocationController.start();
            }
        }
    }

    private void stopControllers() {
        this.createOrderController.stop();
        this.passengerSearchesDriverController.stop();
        this.passengerOrderIsAcceptedController.stop();
        this.passengerDriverHasArrivedController.stop();
        this.passengerDisplayDriverLocationController.stop();
    }

    public void updateRouteOnMap() {
        if (createOrderController.validateTransferPoints()) {
            // DISPLAYING THE ROUTE IS DISABLED FOR NOW.
            // Reason: Feature does not work reliably, sometimes we get bogus routes.
            /*
            new GetDirectionsAsyncTask(PassengerHomeActivity.this.showRouteOverlay, pickupAddressData.latitude, pickupAddressData.longitude,
                                       destinationAddressData.latitude, destinationAddressData.longitude)
                                       .execute();
            */
            clearTransferPositionsOnMap();
            addTransferPointsOnMap();
            this.centerOverlay.setVisibility(View.INVISIBLE);
        }
    }

    private void addTransferPointsOnMap() {
        final GeoPoint pickupGeoPoint = new GeoPoint((int) (pickupAddressData.latitude * 1E6), (int) (pickupAddressData.longitude * 1E6));
        final GeoPoint destinationGeoPoint = new GeoPoint((int) (destinationAddressData.latitude * 1E6), (int) (destinationAddressData.longitude * 1E6));
        this.itemizedOverlay.addOverlay(new OverlayItem(pickupGeoPoint, "", ""));
        final OverlayItem finishItem = new OverlayItem(destinationGeoPoint, "", "");
        final Drawable finishIcon = getResources().getDrawable(R.drawable.pin_finish);
        finishIcon.setBounds(-finishIcon.getIntrinsicWidth() / 2, -finishIcon.getIntrinsicHeight(), finishIcon.getIntrinsicWidth() / 2, 0);
        finishItem.setMarker(finishIcon);
        this.itemizedOverlay.addOverlay(finishItem);

        int minLatitude = (int)(+81 * 1E6);
        int maxLatitude = (int)(-81 * 1E6);
        int minLongitude  = (int)(+181 * 1E6);
        int maxLongitude  = (int)(-181 * 1E6);

        minLatitude = (minLatitude > pickupGeoPoint.getLatitudeE6()) ? pickupGeoPoint.getLatitudeE6() : minLatitude;
        minLatitude = (minLatitude > destinationGeoPoint.getLatitudeE6()) ? destinationGeoPoint.getLatitudeE6() : minLatitude;

        maxLatitude = (maxLatitude < pickupGeoPoint.getLatitudeE6()) ? pickupGeoPoint.getLatitudeE6() : maxLatitude;
        maxLatitude = (maxLatitude < destinationGeoPoint.getLatitudeE6()) ? destinationGeoPoint.getLatitudeE6() : maxLatitude;

        minLongitude = (minLongitude > pickupGeoPoint.getLongitudeE6()) ? pickupGeoPoint.getLongitudeE6() : minLongitude;
        minLongitude = (minLongitude > destinationGeoPoint.getLongitudeE6()) ? destinationGeoPoint.getLongitudeE6() : minLongitude;

        maxLongitude = (maxLongitude < pickupGeoPoint.getLongitudeE6()) ? pickupGeoPoint.getLongitudeE6() : maxLongitude;
        maxLongitude = (maxLongitude < destinationGeoPoint.getLongitudeE6()) ? destinationGeoPoint.getLongitudeE6() : maxLongitude;

        this.map.getController().zoomToSpan((maxLatitude - minLatitude), (maxLongitude - minLongitude));
        this.map.getController().animateTo(new GeoPoint((maxLatitude + minLatitude)/2, (maxLongitude + minLongitude)/2 ));
    }

    private void clearTransferPositionsOnMap() {
        this.centerOverlay.setVisibility(View.VISIBLE);
        this.itemizedOverlay.clearOverlays();
    }

    public void clearPassengerHomeUI(){
        this.imageButtonOrderNow.setVisibility(View.GONE);
        this.textViewNext.setVisibility(View.GONE);
        this.relativeLayoutHome.setVisibility(View.GONE);
        this.relativeLayoutOrderAndNext.setVisibility(View.GONE);
    }

    public void displayPassengerHomeUI(){
        this.imageButtonHomeSearch.setVisibility(View.VISIBLE);
        this.imageButtonOrderNow.setVisibility(View.VISIBLE);
        this.textViewNext.setVisibility(View.VISIBLE);
        this.relativeLayoutHome.setVisibility(View.VISIBLE);
        this.relativeLayoutOrderAndNext.setVisibility(View.VISIBLE);
    }

    private void clearAddressesBars() {
        this.pickupAddressData = new AddressData();
        this.destinationAddressData = new AddressData();
        this.textViewPickupAddress.setText(this.pickupAddressData.street);
        this.textViewDestinationAddress.setText(this.destinationAddressData.street);
        barChangeDestinationAddress.setVisibility(View.INVISIBLE);
    }

    public void showDestinationAddressBar() {
        barChangeDestinationAddress.setVisibility(View.VISIBLE);
    }
}
