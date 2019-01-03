package com.example.a36970.myapplication;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        //String i = "ddd";
        System.loadLibrary("native-lib");
    }
    private final Lock lock = new ReentrantLock();
    private final Condition unfall = lock.newCondition();
    private final Condition unempty = lock.newCondition();
    private int lockcount= 0;
    private int putptr = 0;
    private int takeptr = 0;
    private int readptr = 0;
    private int priority = -10;
    private long Ctime = 0;
    private Semaphore semaphore = new Semaphore(0,true);

    private long ret;
    private String mCameraId;
    private Size mPreviewSize;
    private Size mCaptureSize;
    private HandlerThread muxThread;
    private Handler muxHandler;
    private HandlerThread mCameraThread;
    private HandlerThread mEncodeThread;
    private HandlerThread mEncodeimageThread;
    private HandlerThread mAudioThread;
    private Handler mEncodeimageHandler;
    private Handler mCameraHandler;
    private Handler mEncodeHandler;
    private CameraDevice mCameraDevice;
    private TextureView mTextureView;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CaptureRequest mCaptureRequest;
    private CameraCaptureSession mCameraCaptureSession;
    private TextView mTextview;
    private int Jwidth = 1920;
    private int Jheight = 1080;
    private int count = 0;
    private byte[][][] framequeue;
    private byte[] framemux;
    private byte[] h264buffer;
    private int queuecount = 0;
    private int buffersize;
    private int Audiocount = 0;
    private long Audioclassresult;
    private byte[][] AudioData;
    private long AudioTime = 0;
    private HardEncode hard = new HardEncode();
    //    private int Cwidth = 1;
//    private int Cheight;
private AudioRecord recorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        mTextureView = (TextureView)findViewById(R.id.sample);
        mTextview = (TextView)findViewById(R.id.text);


    }
    @Override
    protected void onResume(){
        super.onResume();
       // final JAudiothread Judio = new JAudiothread();
        //开始音频采集
        //startAudio();
        //startAudioThread();
        Log.d("ss", "采集");
        startCameraThread();
        encodeimagethread();
        Log.d(Integer.toString(android.os.Process.myTid()), "oncreat: 当前线程ID");
        if (!mTextureView.isAvailable()) {
            Log.d("ss", "onResume: 进来了 ");
            //可用事件在监听器触发相机
            mTextureView.setSurfaceTextureListener(mTextureListener);
        } else {
            startPreview();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.w("销毁", "onDestroy: ");
        //destroyenclode(ret);
    }
    private void getqueue() throws InterruptedException {
        Log.d("ss", "采集时间"+Long.toString(Ctime));
        Log.i(Integer.toString(semaphore.availablePermits()), "getqueue:队列长度 ");
        semaphore.acquire();
                Log.w("正在编码帧" + Integer.toString(queuecount), "getqueue:%d ");
                Log.w("待编码帧" + Integer.toString(count), "getqueue: ");
                System.arraycopy(framequeue[takeptr][0], 0, framemux, 0, framequeue[takeptr][0].length);
                System.arraycopy(framequeue[takeptr][1], 0, framemux, framequeue[takeptr][0].length, framequeue[takeptr][1].length);
                hard.encode(framemux,Jheight*Jwidth*3/2,h264buffer);
        //EncodeFrame(ret, framequeue[takeptr][0], framequeue[takeptr][1], queuecount,Ctime);
                if(semaphore.availablePermits()>5&&Process.getThreadPriority(android.os.Process.myTid())>-15)
                    Process.setThreadPriority(--priority);
                if (++takeptr == 10)
                    takeptr = 0;
                queuecount++;


    }
    public void startAudio() {
        buffersize =  AudioRecord.getMinBufferSize(44100,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT);
        Log.i("buffersize", "startAudio: "+Integer.toString(buffersize));
        recorder = new AudioRecord(0,44100, AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT,buffersize);
        AudioEncodeInit("/sdcard/110.txt",1);
        Audioclassresult = createAudioencode();
    }
    private void startAudioThread(){
        AudioData = new byte[10][buffersize];

        //int ReadResult = 0;
        mAudioThread = new HandlerThread("AudioThread"){
            @Override
            public void run() {
                recorder.startRecording();
                AudioTime = System.currentTimeMillis();
                Log.d("ss", "onResume: 读取音频循环"+Integer.toString(buffersize));
                while(recorder.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING){
                   int ReadResult =  recorder.read(AudioData[readptr],0,4096);
                   if(ReadResult>0){
                       Log.i("编码音频帧", "run: ");
                       Log.d("ss", "onResume: 读取音频循环"+Integer.toString(ReadResult));
                       Log.d("ss", "采集yinpin时间"+Long.toString(AudioTime));
                       EncodeAudio(Audioclassresult,AudioData[readptr],Audiocount++,AudioTime);

                       if(readptr++ == 9)
                           readptr = 0;
                   }
                }
            }
        };
        mAudioThread.start();
    }
    private void startEncodeThread(){
        mEncodeThread = new HandlerThread("EncodeThread"){
            @Override
            public void run() {
              //  super.run();
                Process.setThreadPriority(priority);
                while(true){
                try {

                        Log.i("dddddddddddddddddd", "run: ");
                    //while(queuecount<count){
                        getqueue();
                //}

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }}
            }
        };
       // mEncodeimageThread.setPriority(10);

        mEncodeThread.start();

    }
    private void startCameraThread() {
        mCameraThread = new HandlerThread("CameraThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
    }
    private void startmuxThread() {
        muxThread = new HandlerThread("muxThread"){
            @Override
            public void run() {
                muxing(Ctime,AudioTime);
            }
        };
        muxThread.start();
        muxHandler = new Handler(mCameraThread.getLooper());
    }
    private void encodeimagethread(){
        mEncodeimageThread = new HandlerThread("imagethread");
        mEncodeimageThread.start();
        mEncodeimageHandler = new Handler(mEncodeimageThread.getLooper());
    }
    //TextureView监听事件
    private TextureView.SurfaceTextureListener mTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.d("监听事件", "onSurfaceTextureAvailable: 哈哈");
            //当SurefaceTexture可用的时候，设置相机参数并打开相机
            setupCamera(width, height);
            setupImageReader();
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };
    //设置camera参数
    private void setupCamera(int width, int height) {
        //获取摄像头的管理者CameraManager
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            //遍历所有摄像头
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                //此处默认打开后置摄像头
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)//若是前置跳过
                    continue;
                //Log.d(characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)[2].toString(), "相机支持帧率列表");;
                //获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                //map.getOutputFormats();
                assert map != null;
                //根据TextureView的尺寸设置预览尺寸
                mPreviewSize = new Size(Jwidth,Jheight);
                //mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), 1920, 1080);

                Log.d("预览尺寸", mPreviewSize.toString());
                Log.d(Integer.toString(width), "setupCamera: 宽度");
                Log.d(Integer.toString(height), "setupCamera: 高度");

                //获取相机支持的最大拍照尺寸
                /*mCaptureSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)), new Comparator<Size>() {
                    @Override
                    public int compare(Size lhs, Size rhs) {
                        return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getHeight() * rhs.getWidth());
                    }
                });*/
                mCaptureSize = mPreviewSize;
                Log.d("拍照尺寸", mCaptureSize.toString());
                {
                    framequeue = new byte[10][2][];

                }

                    //初始化url与宽高
                    encodeinit("/sdcard/109.txt",Jwidth ,Jheight);
                    framemux = new byte[Jwidth*Jheight*2];
                    h264buffer = new byte[Jwidth*Jheight];
                    //创建jni中Encodec对象
                    ret = createEncodeobject();
                    startEncodeThread();
                    //此ImageReader用于编码所需
                    setupImageReader();
                mCameraId = cameraId;

                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void setupImageReader() {
        //2代表ImageReader中最多可以获取两帧图像流
        mImageReader = ImageReader.newInstance(Jwidth, Jheight,
                ImageFormat.YUV_420_888, 2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                //有新图像时通知
            public void onImageAvailable(ImageReader reader) {

                    Image image = reader.acquireNextImage();
                while(image == null){
                    return;
                }
                    if(count == 0){
                        Ctime = System.currentTimeMillis();
                        //startmuxThread();
                }
                //我们可以将这帧数据转成字节数组，类似于Camera1的PreviewCallback回调的预览帧数据
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                ByteBuffer buffer2 =image.getPlanes()[1].getBuffer();

                            framequeue[putptr][0] = new byte[buffer.remaining()];
                   // Log.i("长度", "onImageAvailable: "+Integer.toString(buffer.remaining()));
                            framequeue[putptr][1] = new byte[buffer2.remaining()];
                            // framequeue[count%10][2] = new byte[buffer3.remaining()];
                            buffer.get(framequeue[putptr][0]);
                            buffer2.get(framequeue[putptr][1]);
//                    System.arraycopy(framequeue[putptr][0], 0, framemux, 0, framequeue[putptr][0].length);
//                    System.arraycopy(framequeue[putptr][1], 0, framemux, framequeue[putptr][0].length, framequeue[putptr][1].length);
//                    hard.input(framemux,Jheight*Jwidth*3/2);

                    Log.d(Integer.toString(count), "接受帧数：");
                    image.close();
                            semaphore.release();
                   // EncodeFrame(ret, framequeue[putptr][0], framequeue[putptr][1], count);
                            if(++putptr == 10)
                                putptr = 0;
                            ++lockcount;

                if(count++==2000)
                    onDestroy();

            }
        },mEncodeimageHandler);
    }

    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        List<Size> sizeList = new ArrayList<>();
        for (Size option : sizeMap) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return sizeMap[0];
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            //检查是否拥有权限
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.i("权限获取失败" , "openCamera: ");
                return;
            }
            manager.openCamera(mCameraId, mStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }
    //实现camera的回调方法
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.d("回调方法camera", "onOpened: ");
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };
    //开始预览
    private void startPreview() {
        Log.d("startpreview", "startPreview: 开始预览");

        SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();

        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

        Surface previewSurface = new Surface(mSurfaceTexture);

        try {
            hard.init();
            //createCaptureRequest创建Capturerequest.builder，TEMPLATE_PREVIEW指的是预览，还有拍照等参数
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
           mCaptureRequestBuilder.addTarget(previewSurface);
            //参数一：所有从摄像头获取图片的surface，参数二：监听创建过程，参数三:执行callback的handler
            mCameraDevice.createCaptureSession(Arrays.asList(mImageReader.getSurface(),previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        /*CameraRequest和CameraRequest.Builder：当程序调用setRepeatingRequest()方法进行预览时，
                        或调用capture()方法进行拍照时，都需要传入CameraRequest参数。CameraRequest代表了一次捕获请求，
                        用于描述捕获图片的各种参数设置，比如对焦模式、曝光模式……总之，程序需要对照片所做的各种控制，
                        都通过CameraRequest参数进行设置。CameraRequest.Builder则负责生成CameraRequest对象。*/
                        mCaptureRequest = mCaptureRequestBuilder.build();
                        mCameraCaptureSession = session;
                        mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, mCameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //检查有无摄像头
//    public boolean checkCameraHardware(Context context) {
//        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
//            Log.d("MainActivity", "checkCameraHardware: 存在摄像头");
//                        // 存在
//                        return true;
//                    } else {
//            // 不存在
//            // Log.d("MainActivity", "checkCameraHardware: 不存在摄像头");
//                       return false;
//                    }
//             }
    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
    public native boolean Open(String url,Object handle);
    public native void encodeinit(String url,int width ,int height);
    public native void EncodeFrame(long encodecAddr , byte[] data,byte[] data2,int i,long time);
    private native long createEncodeobject();
    private native long createAudioencode();
    private native void AudioEncodeInit(String url ,int size);
    public native void destroyenclode(long encodecAddr);
    public native void EncodeAudio(long encodedAddr,byte[] data,int i,long time);
    public native void muxing(long timeA,long timeC);
}
