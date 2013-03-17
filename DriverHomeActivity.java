package com.miniride.activities;

import java.util.List;

import android.widget.RelativeLayout;
import com.miniride.controllers.*;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.message.BasicNameValuePair;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;
import com.miniride.Miniride;
import com.miniride.R;
import com.miniride.api.ApiRequest;
import com.miniride.asyncTasks.SimpleApiRequestAsyncTask;
import com.miniride.map.MapItemizedOverlay;
import com.miniride.models.CurrentUser;
import com.miniride.models.Driver;
import com.miniride.models.Order;
import com.miniride.models.Role;
import com.miniride.pushNotifications.PushReceiver;
import com.miniride.utilities.Utils;

public class DriverHomeActivity extends LocationActivity implements OnClickListener {
    private static final float minDistance = 100;

    private class DriverActionsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent intent) {
            final PushReceiver.Event event = PushReceiver.Event.valueOf(intent.getAction());
            switch (event) {
                case ORDER_RIDE_REQUESTED: {
                    driverRideRequestController.start();
                    drawCurrentLocationOnMap(R.drawable.pin_driver_location);
                    displayTransferEndpointsController.start();
                    Utils.displayMsg(DriverHomeActivity.this, getString(R.string.orderRideRequested));
                    break;
                }
                case ORDER_CANCELLED_BY_PASSENGER: {
                    driverRideRequestController.stop();
                    displayTransferEndpointsController.stop();
                    Utils.displayMsg(DriverHomeActivity.this, getString(R.string.orderCancelledByPassenger));
                    break;
                }
                case ORDER_UNAVAILABLE: {
                    driverRideRequestController.stop();
                    displayTransferEndpointsController.stop();
                    Utils.displayMsg(DriverHomeActivity.this, getString(R.string.orderUnavailable));
                    break;
                }
            }
        }
    }

    private DriverActionsReceiver driverActionsReceiver;
    private IntentFilter driverActionsIntentFilter;

    private DriverRideRequestController driverRideRequestController;
    private DriverAcceptsOrderController driverAcceptsOrderController;
    private DriverPassengerBoardsController driverPassengerBoardsController;
    private DriverRideEndedController driverRideEndedController;
    private DisplayTransferEndpointsController displayTransferEndpointsController;

    private Button buttonGetCurrentLocation;
    private CheckBox buttonPauseApp;
    private double lat, lng;
    private RelativeLayout layoutDriverHomeFooter;

    @Override
    protected void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.driver_home);
        Miniride.setDriverHomeActivity(this);
        CurrentUser.setAsDriver();

        buttonGetCurrentLocation = (Button) findViewById(R.id.buttonUpdateCurrentLocation);
        buttonPauseApp = (CheckBox) findViewById(R.id.imageButtonPauseApp);
        layoutDriverHomeFooter = (RelativeLayout) findViewById(R.id.layoutDriverHomeFooter);

        this.driverMap = (MapView) findViewById(R.id.mapViewDriverHome);
        this.driverMap.getController().setZoom(getResources().getInteger(R.integer.driverMapZoom));

        buttonPauseApp.setOnClickListener(this);
        buttonGetCurrentLocation.setOnClickListener(this);

        setMapToCurrentLocation();
        initControllers();
        initReceivers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Miniride.getDriverHomeFragmentActivityInstance().setRadiusDisplay();
        if (CurrentUser.driverHasPaused())
            buttonPauseApp.setChecked(true);
        Utils.displayFlashMessage(this, getIntent());
        requestLocationUpdates(minDistance);

        if (driverRideRequestController.shouldHandleCurrentOrder()) {
            driverRideRequestController.start();
            drawCurrentLocationOnMap(R.drawable.pin_driver_location);
        } else if (driverAcceptsOrderController.shouldHandleCurrentOrder()) {
            driverAcceptsOrderController.start();
            displayTransferEndpointsController.startOrderAccepted();
            drawCurrentLocationOnMap(R.drawable.pin_driver_location);
        } else if (driverPassengerBoardsController.shouldHandleCurrentOrder()) {
            driverPassengerBoardsController.start();
            displayTransferEndpointsController.startOrderAccepted();
            drawCurrentLocationOnMap(R.drawable.pin_driver_location);
        } else if (driverRideEndedController.shouldHandleCurrentOrder()) {
            driverRideEndedController.start();
            drawCurrentLocationOnMap(R.drawable.pin_driver_location);
        } else {
            drawCurrentLocationOnMap(R.drawable.pin_driver_free);
        }
        if (displayTransferEndpointsController.shouldHandleCurrentOrder()) {
            displayTransferEndpointsController.start();
        }

        registerReceiver(driverActionsReceiver, driverActionsIntentFilter);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(driverActionsReceiver);

        super.onPause();
        stopLocationUpdates();

        //TODO UI Conflict when Driver is taking an order and then the phone automatically locks
        driverRideRequestController.stop();
        driverAcceptsOrderController.stop();
        driverRideEndedController.stop();
        driverPassengerBoardsController.stop();
        displayTransferEndpointsController.stop();
    }

    @Override
    public void updateWithLocation(final Location loc) {
        this.driverMap.getController().animateTo(new GeoPoint((int) (loc.getLatitude() * 1E6), (int) (loc.getLongitude() * 1E6)));
        new SimpleApiRequestAsyncTask(ApiRequest.DRIVER_UPDATE_LOCATION_PATH, HttpPost.METHOD_NAME, null)
                .execute(new BasicNameValuePair("token", Driver.loadCurrent().getToken()),
                         new BasicNameValuePair("driver[latitude]", String.valueOf(loc.getLatitude())),
                         new BasicNameValuePair("driver[longitude]", String.valueOf(loc.getLongitude())));
        lat = loc.getLatitude();
        lng = loc.getLongitude();
        drawCurrentLocationOnMap(R.drawable.pin_driver_free);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onClick(final View v) {
        int id = v.getId();
        switch (id) {
            case R.id.buttonUpdateCurrentLocation:
                setMapToCurrentLocation();
                break;
            case R.id.imageButtonPauseApp:
                String token = Driver.loadCurrent().getToken();
                if (token == null) {
                    Utils.displayMsg(this, R.string.registrationNotFinishedPleaseRetry);
                } else {
                    if(CurrentUser.driverHasPaused()) {
                        Utils.displayMsgShort(this, R.string.resumingActivity);
                        markDriverAsActiveAndUpdateUI();
                        new SimpleApiRequestAsyncTask(ApiRequest.DRIVER_RESUME_PATH, HttpPut.METHOD_NAME, null)
                            .execute(new BasicNameValuePair("token", token));
                    } else {
                        if (Order.noCurrentOrderFor(Role.DRIVER)){
                            Utils.displayMsgShort(this, R.string.pausingTheApp);
                            markDriverAsPausedAndUpdateUI();
                            new SimpleApiRequestAsyncTask(ApiRequest.DRIVER_PAUSE_PATH, HttpPut.METHOD_NAME, null).
                                execute(new BasicNameValuePair("token", token));
                        } else Utils.displayMsgShort(this, R.string.canNotPauseWithOrderPresent);
                    }
                }
                break;
            default:
                break;
        }
    }

    public void toggleFooterLayout(boolean isRequested){
        if (isRequested)
            layoutDriverHomeFooter.setVisibility(View.GONE);
        else
            layoutDriverHomeFooter.setVisibility(View.VISIBLE);
    }

    private void markDriverAsActiveAndUpdateUI() {
        CurrentUser.toggleDriverHasPaused();
        updateDisplayOnPauseAction(R.drawable.pin_driver_free);
    }

    private void markDriverAsPausedAndUpdateUI() {
        CurrentUser.toggleDriverHasPaused();
        updateDisplayOnPauseAction(R.drawable.pin_driver_busy);
    }

    private void updateDisplayOnPauseAction(int drawableId) {
        drawCurrentLocationOnMap(drawableId);
        Miniride.getDriverHomeFragmentActivityInstance().setRadiusDisplay();
    }

    public void drawCurrentLocationOnMap(int driverPinLocation) {
        List<Overlay> mapOverlays = driverMap.getOverlays();
        mapOverlays.clear();
        Drawable drawable = null;
        MapItemizedOverlay itemizedOverlay = null;

        if (CurrentUser.driverHasPaused())
            drawable = this.getResources().getDrawable(R.drawable.pin_driver_busy);
        else
            drawable = this.getResources().getDrawable(driverPinLocation);

        itemizedOverlay = new MapItemizedOverlay(drawable);

        GeoPoint point = new GeoPoint((int) (lat * 1E6), (int) (lng * 1E6));
        OverlayItem overlayItem = new OverlayItem(point, "Location", "I'm at");
        itemizedOverlay.addOverlay(overlayItem);

        mapOverlays.add(itemizedOverlay);

        driverMap.postInvalidate();
     }


    public void initControllers() {
        driverRideRequestController = new DriverRideRequestController(this, new DriverRideRequestController.OnDriverAcceptedListener() {
            @Override
            public void onAccepted() {
                driverAcceptsOrderController.start();
                displayTransferEndpointsController.startOrderAccepted();
            }
        }, new DriverRideRequestController.OnDriverCancelledListener() {
            @Override
            public void onCancel() {
                displayTransferEndpointsController.stop();
            }
        });
        driverAcceptsOrderController = new DriverAcceptsOrderController(this, new DriverAcceptsOrderController.OnDriverArrivedListener() {
            @Override
            public void onArrive() {
                driverPassengerBoardsController.start();
                displayTransferEndpointsController.startOrderAccepted();
            }
        }, new DriverAcceptsOrderController.OnDriverCancelledListener() {
            @Override
            public void onCancel() {
                displayTransferEndpointsController.stop();
            }
        });
        driverPassengerBoardsController = new DriverPassengerBoardsController(this, new DriverPassengerBoardsController.OnPassengerBoardsListener() {
            @Override
            public void onBoarded() {
                driverRideEndedController.start();
                displayTransferEndpointsController.stop();
            }
        }, new DriverPassengerBoardsController.OnDriverAbortsListener() {
            @Override
            public void onAborted() {
                displayTransferEndpointsController.stop();
            }
        });
        driverRideEndedController = new DriverRideEndedController(this);
        displayTransferEndpointsController = new DisplayTransferEndpointsController(this);
    }

    private void initReceivers() {
        driverActionsReceiver = new DriverActionsReceiver();
        driverActionsIntentFilter = new IntentFilter();
        driverActionsIntentFilter.addAction(PushReceiver.Event.ORDER_RIDE_REQUESTED.name());
        driverActionsIntentFilter.addAction(PushReceiver.Event.ORDER_CANCELLED_BY_PASSENGER.name());
        driverActionsIntentFilter.addAction(PushReceiver.Event.ORDER_UNAVAILABLE.name());
    }


}
