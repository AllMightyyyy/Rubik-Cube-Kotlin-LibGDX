package com.mycompany.myrubikscube.android;

import static androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.common.util.concurrent.ListenableFuture;
import com.mycompany.myrubikscube.R;
import com.mycompany.myrubikscube.cs.min2phase.Search;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.ml.KNearest;
import org.opencv.ml.Ml;
import org.opencv.utils.Converters;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Math.min;

/**
 * Scans each face of the Rubik’s Cube. Forces the center square to match
 * the expected color (0=Yellow, 1=Orange, 2=Green, 3=White, 4=Red, 5=Blue).
 */
public class CubeScanActivity extends AppCompatActivity {
    static {
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Initialization failed for OpenCV");
        } else {
            Log.e("OpenCV", "Initialization success");
        }
    }

    private static final String TAG = "CubeScanActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 1234;
    private final String[] REQUIRED_PERMISSIONS = { Manifest.permission.CAMERA };

    // Views
    private LinearProgressIndicator scanIndicator;
    private ImageView imageView;
    private CubeView cubeView;
    private Button resetButton;
    private Button scanButton;

    // For CameraX
    private Camera camera = null;
    private ImageAnalysis imageAnalysis = null;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();

    // KNN logic
    private Mat trainData = new Mat(6, 4, CvType.CV_32F);
    private KNearest knn = KNearest.create();

    // For scanning logic
    // "Scan Order": 0=Yellow,1=Orange,2=Green,3=White,4=Red,5=Blue
    // We store detected colors in row-major order: detectedColor[row][col]
    private final int[][] detectedColor = {
        {0, 0, 0},
        {0, 0, 0},
        {0, 0, 0}
    };
    private Mat[][] aveColor;
    private static final float ALPHA = 0.75f;

    /** Accumulates 9 chars per face (6 faces => 54 total). */
    private String scannedCube = "";

    /** Which face index we’re scanning now (0..5). */
    private int currentFaceIdx = 0;

    private static final int TOTAL_FACES = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cube_scan);

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed!");
            handleOpenCvInitFailure();
        } else {
            Log.d(TAG, "OpenCV initialized successfully.");
            initializeOpenCvComponents();
        }

        scanIndicator = findViewById(R.id.scanIndicator);
        imageView     = findViewById(R.id.imageView);
        cubeView      = findViewById(R.id.cubeView);
        resetButton   = findViewById(R.id.resetButton);
        scanButton    = findViewById(R.id.scanButton);

        // show scanning progress
        scanIndicator.show();
        updateIndicator();

        // Setup KNN
        for (int i = 0; i < 6; i++) {
            trainData.put(i, 0, ImageUtil.colorData[i]);
        }
        knn.train(trainData, Ml.ROW_SAMPLE,
            Converters.vector_int_to_Mat(ImageUtil.colorResponse));

        // "Scan" button
        scanButton.setOnClickListener(v -> {
            synchronized (detectedColor) {
                Log.d(TAG, "Raw detected colors for face " + currentFaceIdx + ":");
                for (int row = 0; row < 3; row++) {
                    Log.d(TAG, Arrays.toString(detectedColor[row]));
                }

                // Build a 9-character string for this face in row-major order
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        if (row == 1 && col == 1) {
                            // Force the center in the final string
                            scannedCube += ImageUtil.colorLabel[currentFaceIdx];
                        } else {
                            scannedCube += ImageUtil.colorLabel[detectedColor[row][col]];
                        }
                    }
                }
            }

            // Move to next face or finish
            if (currentFaceIdx < 5) {
                currentFaceIdx++;
                display();
            } else {
                // All 6 faces have been scanned => build final 54-char notation
                currentFaceIdx++;
                updateIndicator();
                sendCubeStringToCubeApp(scannedCube);
            }
        });

        // Reset
        resetButton.setOnClickListener(v -> scanReset());

        // custom back press
        OnBackPressedCallback cb = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentFaceIdx > 0) {
                    scanRollback();
                } else {
                    new MaterialAlertDialogBuilder(CubeScanActivity.this)
                        .setTitle("Confirm Quit")
                        .setMessage("Finish scanning?")
                        .setPositiveButton("Finish", (d, i) -> finish())
                        .setNegativeButton("Stay", (d, i) -> d.dismiss())
                        .setCancelable(false)
                        .show();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, cb);

        // Request camera permissions
        if (checkPermissions()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        // (Optional) Initialize Min2Phase
        Search.init();
    }

    private void handleOpenCvInitFailure() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Error")
            .setMessage("OpenCV failed to initialize. The app will close.")
            .setPositiveButton("OK", (d, i) -> finish())
            .setCancelable(false)
            .show();
    }

    private void initializeOpenCvComponents() {
        trainData = new Mat(6, 4, CvType.CV_32F);
        knn = KNearest.create();
        aveColor = new Mat[3][3]; // for the moving average

        // Train KNN
        for (int i = 0; i < 6; i++) {
            trainData.put(i, 0, ImageUtil.colorData[i]);
        }
        knn.train(trainData, Ml.ROW_SAMPLE,
            Converters.vector_int_to_Mat(ImageUtil.colorResponse));
    }

    @Override
    protected void onResume() {
        super.onResume();
        display();
    }

    /**
     * Shows the current face’s “center color” in the UI,
     * plus side colors, etc.
     */
    private void display() {
        resetButton.setEnabled(currentFaceIdx > 0);

        // Show the side colors (if you have an array of side-colors)
        cubeView.setSideColors(ImageUtil.arrSideColors[currentFaceIdx]);

        // Show the 3x3 face. But we want the center forced
        // to the "currentFaceIdx" color in the UI:
        int[][] tempForUI = new int[3][3];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                tempForUI[row][col] = detectedColor[row][col];
            }
        }
        // forcibly set center color in that temp array
        tempForUI[1][1] = currentFaceIdx;

        // set that to the cubeView
        cubeView.setFrontColors(tempForUI);

        updateIndicator();
    }

    /**
     * Step back one face (remove the last 9 chars),
     * so user can rescan.
     */
    private void scanRollback() {
        if (currentFaceIdx > 0) {
            currentFaceIdx--;
            // remove last 9 chars from scannedCube
            scannedCube = scannedCube.substring(0, scannedCube.length() - 9);
            display();
        }
    }

    /**
     * Reset scanning from scratch.
     */
    private void scanReset() {
        currentFaceIdx = 0;
        scannedCube = "";
        display();
    }

    private void updateIndicator() {
        scanIndicator.setIndeterminate(false);
        int progress = (int) ((100.0 / TOTAL_FACES) * currentFaceIdx);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            scanIndicator.setProgress(progress, true);
        }
    }

    private boolean checkPermissions() {
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) !=
                PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (checkPermissions()) {
                startCamera();
            } else {
                finish();
            }
        }
    }

    // The camera
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
            ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                imageAnalysis = new ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                    .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new MyImageAnalyzer());

                CameraSelector sel = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();

                provider.unbindAll();
                camera = provider.bindToLifecycle(this, sel, imageAnalysis);
            } catch (Exception e) {
                Log.e(TAG, "Camera init error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Analyzes each frame, does color detection, and updates detectedColor[][].
     */
    private class MyImageAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy image) {
            Mat mat = ImageUtil.getMatFromImage(image);
            mat = fixMatRotation(mat);

            int h = mat.rows();
            int w = mat.cols();
            Mat matOutput = mat.clone();

            double cLen = min(h, w) * 0.8;
            int boxLen = (int) (cLen / 3);
            int startX = (int) ((w - cLen) / 2);
            int startY = (int) ((h - cLen) / 2);

            // detect color in row-major order:
            // row => Y direction, col => X direction
            synchronized (detectedColor) {
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        int x = startX + boxLen * col;
                        int y = startY + boxLen * row;

                        Mat color = ImageUtil.calcBoxColorAve(mat, x, y, boxLen);

                        // Weighted average over time
                        color = ImageUtil.calcMovingAveColor(aveColor[row][col], color, ALPHA);
                        aveColor[row][col] = color;

                        // KNN classify
                        Mat res = new Mat();
                        knn.findNearest(color, 1, res);
                        int colorIdx = (int) res.get(0, 0)[0];
                        res.release();

                        // store it
                        detectedColor[row][col] = colorIdx;

                        // draw bounding box
                        Imgproc.rectangle(matOutput,
                            new Rect(x, y, boxLen, boxLen),
                            new Scalar(255, 0, 0), 2);

                        // put color label
                        String label = ImageUtil.colorLabel[colorIdx];
                        Imgproc.putText(matOutput,
                            label,
                            new Point(x + 10, y + 30),
                            Imgproc.FONT_HERSHEY_SIMPLEX,
                            1.0,
                            new Scalar(ImageUtil.colorData[colorIdx][0],
                                ImageUtil.colorData[colorIdx][1],
                                ImageUtil.colorData[colorIdx][2]),
                            2);
                    }
                }
            }

            // make the preview square
            if (h > w) {
                // portrait
                int bX = 0;
                int bY = startY - startX;
                matOutput = matOutput.submat(new Rect(bX, bY, w, w));
            } else {
                // landscape
                int bX = startX - startY;
                int bY = 0;
                matOutput = matOutput.submat(new Rect(bX, bY, h, h));
            }

            // convert to bitmap
            Bitmap bmp = Bitmap.createBitmap(
                matOutput.cols(), matOutput.rows(), Bitmap.Config.ARGB_8888);
            org.opencv.android.Utils.matToBitmap(matOutput, bmp);

            runOnUiThread(() -> {
                // Show live preview
                imageView.setImageBitmap(bmp);
                // Also update the little 3x3 color squares
                // but forcibly keep the center color = currentFaceIdx in the UI
                display();
            });

            matOutput.release();
            mat.release();
            image.close();
        }

        private Mat fixMatRotation(Mat matOrg) {
            Mat mat;
            switch (imageView.getDisplay().getRotation()) {
                case Surface.ROTATION_0:
                default:
                    mat = new Mat(matOrg.cols(), matOrg.rows(), matOrg.type());
                    Core.transpose(matOrg, mat);
                    Core.flip(mat, mat, 1);
                    break;
                case Surface.ROTATION_90:
                    mat = matOrg;
                    break;
                case Surface.ROTATION_270:
                    mat = matOrg;
                    Core.flip(mat, mat, -1);
                    break;
            }
            return mat;
        }
    }

    // solve the scanned cube in background if needed
    private class SolveTask extends AsyncTask<String, Void, String> {
        private int lastErr = 0;

        @Override
        protected void onPreExecute() {
            disableButtons();
            scanIndicator.setIndeterminate(true);
        }

        @Override
        protected String doInBackground(String... strings) {
            String scCube = strings[0];
            Log.i(TAG, "Scanned: " + scCube);

            // Example: parse or verify with ColorInputActivity logic
            // or convert to a different notation
            String scrCube = ImageUtil.convertCubeAnnotation(scCube);
            return null;
        }

        @Override
        protected void onPostExecute(String moves) {
            if (lastErr == 0) {
                new MaterialAlertDialogBuilder(CubeScanActivity.this)
                    .setTitle("Solved!")
                    .setMessage("Solution: " + moves)
                    .setPositiveButton("OK", null)
                    .setCancelable(false)
                    .show();
                scanReset();
            } else {
                int msgIdx = (lastErr * -1) - 1;
                String[] msgs = ImageUtil.verifyMsg;
                new MaterialAlertDialogBuilder(CubeScanActivity.this)
                    .setTitle("Invalid Cube")
                    .setMessage("Error code: " + lastErr + "\n" + msgs[msgIdx])
                    .setPositiveButton("Reset", (d, i) -> scanReset())
                    .setNeutralButton("Ignore", null)
                    .setCancelable(false)
                    .show();
                scanRollback();
                Log.e(TAG, "[solver] invalid cube code=" + lastErr);
            }
            enableButtons();
        }
    }

    private void enableButtons() {
        resetButton.setEnabled(true);
        scanButton.setEnabled(true);
    }

    private void disableButtons() {
        resetButton.setEnabled(false);
        scanButton.setEnabled(false);
    }

    /**
     * Called after all 6 faces scanned, passes final string.
     */
    private void sendCubeStringToCubeApp(String scannedCube) {
        // scannedCube has 54 chars: faces 0..5 (each 9 chars)
        // face0(Y)=0..8, face1(O)=9..17, face2(G)=18..26,
        // face3(W)=27..35, face4(R)=36..44, face5(B)=45..53
        String face0 = scannedCube.substring(0, 9);
        String face1 = scannedCube.substring(9, 18);
        String face2 = scannedCube.substring(18, 27);
        String face3 = scannedCube.substring(27, 36);
        String face4 = scannedCube.substring(36, 45);
        String face5 = scannedCube.substring(45, 54);

        // reorder to U, R, F, D, L, B
        String reorder = face3 + face4 + face2 + face0 + face1 + face5;

        // map color letters to Min2Phase notation
        StringBuilder fixed54 = new StringBuilder(54);
        for (char c : reorder.toCharArray()) {
            switch (c) {
                case 'Y': fixed54.append('D'); break; // Yellow -> D
                case 'O': fixed54.append('L'); break; // Orange -> L
                case 'G': fixed54.append('F'); break; // Green  -> F
                case 'W': fixed54.append('U'); break; // White  -> U
                case 'R': fixed54.append('R'); break; // Red    -> R
                case 'B': fixed54.append('B'); break; // Blue   -> B
                default:
                    fixed54.append('?'); // unexpected
            }
        }

        String finalCubeString = fixed54.toString();
        Log.i(TAG, "Final 54-char string for Min2Phase: " + finalCubeString);

        Intent resultIntent = new Intent();
        resultIntent.putExtra("CUBE_STRING", finalCubeString);
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
