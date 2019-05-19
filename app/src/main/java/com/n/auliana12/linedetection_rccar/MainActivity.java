package com.n.auliana12.linedetection_rccar;


import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import app.akexorcist.bluetotohspp.library.BluetoothSPP;
import app.akexorcist.bluetotohspp.library.BluetoothState;
import app.akexorcist.bluetotohspp.library.DeviceList;


import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class MainActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2, Runnable {

    public enum State { GO_FORWARD, GO_BACKWARD, TURN_LEFT, TURN_RIGHT, TURN_LEFT_REVERSE, TURN_RIGHT_REVERSE, STOP, OVERTAKE_START, OVERTAKE_END };

    private int screenWidth, screenHeight;
    private boolean trajectoryCorrection = false, isOvertakeActive = false, isOvertakeStartActive = false, isOvertakeEndActive = false, firstLateralSensorActivation = false;

    private JavaCameraView mCameraView = null;
    private MainActivity.State mState = MainActivity.State.STOP;

    final String FORWARD = "FORWARD";
    final String BACKWARD = "BACKWARD";
    final String RIGHT = "RIGHT";
    final String LEFT = "LEFT";


    final String ON = "1";
    final String OFF = "0";

    BluetoothSPP bluetooth;

    Button connect;
//    Button on;
//    Button off;
//    Button auto;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    mCameraView.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        screenWidth =  getWindowManager().getDefaultDisplay().getWidth();
        screenHeight = getWindowManager().getDefaultDisplay().getHeight();
        setContentView(R.layout.activity_main);

        mCameraView = (JavaCameraView) findViewById(R.id.CameraActivity);
        mCameraView.setVisibility(SurfaceView.VISIBLE);
        mCameraView.setCvCameraViewListener(this);

        bluetooth = new BluetoothSPP(this);

//        connect = (Button) findViewById(R.id.connect);
//        on = (Button) findViewById(R.id.on);
//        off = (Button) findViewById(R.id.off);
//        auto = (Button) findViewById(R.id.autodrive);

        if (!bluetooth.isBluetoothAvailable()) {
            Toast.makeText(getApplicationContext(), "Bluetooth is not available", Toast.LENGTH_SHORT).show();
            finish();
        }
        Intent intent = new Intent(getApplicationContext(), DeviceList.class);
        startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);

//        bluetooth.setBluetoothConnectionListener(new BluetoothSPP.BluetoothConnectionListener() {
//            public void onDeviceConnected(String name, String address) {
//                connect.setText("Connected to " + name);
//            }
//
//            public void onDeviceDisconnected() {
//                connect.setText("Connection lost");
//            }
//
//            public void onDeviceConnectionFailed() {
//                connect.setText("Unable to connect");
//            }
//        });

//        connect.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (bluetooth.getServiceState() == BluetoothState.STATE_CONNECTED) {
//                    bluetooth.disconnect();
//                } else {
//                    Intent intent = new Intent(getApplicationContext(), DeviceList.class);
//                    startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);
//                }
//            }
//        });

//        on.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                bluetooth.send(ON, true);
//            }
//        });
//
//        off.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                bluetooth.send(OFF, true);
//            }
//        });
//
//        auto.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Intent camera=  new Intent(getApplicationContext(), Vision.class);
//                startActivity(camera);
//            }
//        });

    }

    public void onStart() {
        super.onStart();
        if (!bluetooth.isBluetoothEnabled()) {
            bluetooth.enable();
        } else {
            if (!bluetooth.isServiceAvailable()) {
                bluetooth.setupService();
                bluetooth.startService(BluetoothState.DEVICE_OTHER);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);

        if (!bluetooth.isBluetoothEnabled()) {
            bluetooth.enable();
        } else {
            if (!bluetooth.isServiceAvailable()) {
                bluetooth.setupService();
                bluetooth.startService(BluetoothState.DEVICE_OTHER);
            }
        }
    }


    public void onDestroy() {
        super.onDestroy();
        bluetooth.stopService();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BluetoothState.REQUEST_CONNECT_DEVICE) {
            if (resultCode == Activity.RESULT_OK)
                bluetooth.connect(data);
        } else if (requestCode == BluetoothState.REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                bluetooth.setupService();
            } else {
                Toast.makeText(getApplicationContext()
                        , "Bluetooth was not enabled."
                        , Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat originalImage = inputFrame.rgba(),
                tmpInputFrame = inputFrame.gray().submat(screenHeight - 100, screenHeight, 0, screenWidth),
                lines = new Mat();

        boolean straightLeftLine = false, straightRightLine = false, turnLeft = false, turnRight = false;

        Imgproc.Canny(tmpInputFrame, tmpInputFrame, 40, 120, 3, true);
        Imgproc.HoughLinesP(tmpInputFrame, lines, 1, Math.PI / 180, 50, 20, 20);

        for (int i = 0; i < lines.rows(); i++) {
            double[] points = lines.get(i, 0);
            double slope = (points[3] - points[1]) / (points[2] - points[0]); // (y2 - y1) / (x2 - x1)
            double degrees = Math.toDegrees(Math.atan(slope));

            // RIGHT STRAIGHT LINE
            if ((degrees < 60 && degrees > 20)) {
                if (points[0] >= screenWidth / 2) {
                    straightRightLine = true;
                    Imgproc.line(originalImage, new Point(points[0], points[1] + screenHeight - 100), new Point(points[2], points[3] + screenHeight - 100), new Scalar(0, 255, 0), 6);

                } else {
                    turnLeft = true;
                    Imgproc.line(originalImage, new Point(points[0], points[1] + screenHeight - 100), new Point(points[2], points[3] + screenHeight - 100), new Scalar(255, 0, 0), 6);
                }

                // LEFT STRAIGHT LINE
            } else if (degrees > -60 && degrees < -20) {
                if (points[2] < screenWidth / 2) {
                    straightLeftLine = true;
                    Imgproc.line(originalImage, new Point(points[0], points[1] + screenHeight - 100), new Point(points[2], points[3] + screenHeight - 100), new Scalar(0, 255, 0), 6);

                } else {
                    turnRight = true;
                    Imgproc.line(originalImage, new Point(points[0], points[1] + screenHeight - 100), new Point(points[2], points[3] + screenHeight - 100), new Scalar(255, 0, 0), 6);
                }

                // TURN LEFT LINE
            } else if (degrees < 20 && degrees > 5) {
                if ((points[2] > screenWidth / 2 && points[3] > 80) || (isOvertakeEndActive && points[3] > 80)) {
                    turnLeft = true;
                    Imgproc.line(originalImage, new Point(points[0], points[1] + screenHeight - 100), new Point(points[2], points[3] + screenHeight - 100), new Scalar(255, 0, 0), 6);
                }

                // TURN RIGHT LINE
            } else if (degrees > -20 && degrees < -5) {
                if ((points[0] < screenWidth / 2 && points[1] > 80) || (isOvertakeStartActive && points[3] > 80)) {
                    turnRight = true;
                    Imgproc.line(originalImage, new Point(points[0], points[1] + screenHeight - 100), new Point(points[2], points[3] + screenHeight - 100), new Scalar(255, 0, 0), 6);
                }
            }
        }


        // OVERTAKE START CONDITION
        if (isOvertakeStartActive) {
            if (straightRightLine && straightLeftLine && mState == MainActivity.State.TURN_RIGHT) bluetooth.send(FORWARD, true);
            else if (turnRight) bluetooth.send(RIGHT, true);
            else bluetooth.send(LEFT, true);

            // OVERTAKE END CONDITION
        } else if (isOvertakeEndActive) {
            if (straightRightLine || straightLeftLine) bluetooth.send(RIGHT, true);
            else if (turnLeft) {
                bluetooth.send(LEFT, true);
                isOvertakeEndActive = false;
            }

            // NORMAL CONDITION
        } else {
            if (turnLeft || (straightRightLine && !straightLeftLine)) bluetooth.send(LEFT, true);
            else if (turnRight || (straightLeftLine && !straightRightLine))
                bluetooth.send(RIGHT, true);
            else if (straightLeftLine && straightRightLine) bluetooth.send(FORWARD, true);
            else if (mState == MainActivity.State.TURN_LEFT && trajectoryCorrection) bluetooth.send(RIGHT, true);
            else if (mState == MainActivity.State.TURN_RIGHT && trajectoryCorrection) bluetooth.send(LEFT, true);
            else bluetooth.send(FORWARD, true);
        }

        trajectoryCorrection = (straightRightLine && !straightLeftLine) || (straightLeftLine && !straightRightLine);

        return originalImage;
    }

    @Override
    public void run() {
        String dataReceived = "N";

        while (bluetooth.isBluetoothAvailable()) {
            if (bluetooth.isBluetoothAvailable()) {

                switch (dataReceived) {
                    case "N":
                        if (isOvertakeStartActive) {
                            if (firstLateralSensorActivation) {
                                isOvertakeActive = true;
                                isOvertakeStartActive = firstLateralSensorActivation = false;
                            } else firstLateralSensorActivation = true;
                        }
                        break;
                    case "Y":
                        if (isOvertakeActive) {
                            isOvertakeEndActive = true;
                            isOvertakeActive = false;
                        }
                        break;
                    default:
                        if (!isOvertakeStartActive && !isOvertakeActive && !isOvertakeEndActive && (Integer.valueOf(dataReceived) == MainActivity.State.OVERTAKE_START.ordinal())) {
                            isOvertakeStartActive = true;
                        }
                        break;
                }
            }
        }
    }

}

