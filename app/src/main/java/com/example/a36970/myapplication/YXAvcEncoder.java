package com.example.a36970.myapplication;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

/**
 * Created by 36970 on 2019/1/10.
 */
@SuppressWarnings("unused")
@SuppressLint("NewApi")
public class YXAvcEncoder {
    private static final String TAG 				= 	"AvcMediaEncoder";
    private static final String VIDEO_MIME_TYPE 	= 	"video/avc";
    private int 	TIMEOUT_USEC 		= 	-1;
    private static final int	maskBitRate			=	(0x1<<0);
    private static final int	maskFrameRate		=	(0x1<<1);
    private static final int	maskForceRestart	=	(0x1<<2);
    private static final int    minFrameRate        =   7;
    private static final int    maxFrameRate        =   2000;
    /*
    HuaweiP9AndHonor8:这两款手机用FFMPEG录出来的MP4 ts.m3u8在iphone6s以下的手机播放硬解码错误，
    和系统自带MUX录出来的文件对比发现，每个包少了8个字节，每一帧的第二个slice为XX XX 00 00 00 00 00 00
    编码端手动去除这8个字节，iphones播放没有问题，目前不知道这8字节是什么东西，等官方回应！！
    */
    private static boolean isHuaweiP9AndHonor8 = false;


    // 日志开关;
    private static boolean m_verbose = true;
    private static boolean m_testRuntime = false;
    private static boolean m_bSaveAvc = false;

    // 编码起始纳秒系统时间;
    private long        m_startTime = 0;

    private  boolean    m_bSuccessInit = false;


    // 编码器相关信息;
    private MediaCodec m_mediaCodec 	= null;
    private MediaFormat m_codecFormat	= null;
    private MediaCodecInfo m_codecInfo 	= null;
    private Surface m_surface		= null;
    private Boolean m_useInputSurface = false;
    private long			m_getnerateIndex = 0;
    private boolean m_bSignalEndOfStream = false;
    private boolean m_bNeedSingalEnd = false;

//    // TODO:
//    private CodecInputSurface mCodecInputSurface = null;
//    private S2DTextureRender	mTextureRender = null;
//    //    private S3DTextureRender	mTextureRender = null;
//    private EglStateSaver mEglStateSaver = null;
//    private YXTextureCacheManager mTextureManager = null;
//    private YXCodecInputSurface mNativeCodecInputSurface =  null;

    private HashMap seiMap = new HashMap();
    // 线程运行相关参数；
    public Thread encoderThread	= null;
    public Boolean isRunning	= false;

    // 异步编码纹理输入队列;
    private static int inputpacketsize = 10;
    private static ArrayBlockingQueue<int[]> inputQueue = new ArrayBlockingQueue<>(inputpacketsize);

    // 异步编码内存输入队列;
    private static ArrayBlockingQueue<byte[]> inputQueue2 = new ArrayBlockingQueue<>(inputpacketsize);

    // 编码数据输出队列;
    private static int avcqueuesize = 25;
    public static ArrayBlockingQueue<CodecData> AVCQueue = new ArrayBlockingQueue<CodecData>(avcqueuesize);
    private CodecData  mLastCodecData		= null;


    public byte[]	configbyte	= null;
    public Boolean isNeedReconfigure	= false;
    public int		configStatus	= 0;
    private byte[]  sps;
    private byte[]  pps;


    // 根据以下参数生成编码格式信息；
    public int 	width				= 640;
    public int	height				= 480;
    public int  frameRate			= 25;
    public int	bitRate				= 2500000;
    public int  iFrameInternal		= 1;
    public int  colorFormat			= MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
    public int  profile             = 0;
    public boolean skipGenKey       = true;

    private static YXAvcEncoder curObj = null;
    private static class CodecData
    {
        public byte[] data = null;
        public long	pts = 0;
        public int flag;
    }
    public int initEncoder( int _width, int _height, int _frameRate, int _colorFormat, int _iFrameInternal, int _bitRate, int _profile, boolean _bUseInputSurface)
    {
        Log.d(TAG, "initEncoder: 初始化YX");
        m_bSuccessInit = false;
        if( m_useInputSurface && Build.VERSION.SDK_INT<18 )
        {
            return -1;
        }

        int err = 0;
        configbyte = null;
        m_bSignalEndOfStream = false;
        m_bNeedSingalEnd = false;
        if ( skipGenKey)
        {
            m_useInputSurface = _bUseInputSurface;
            if ( m_useInputSurface)
            {
                _colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
            }
            setEncoder(_width, _height, _frameRate, _bitRate, _iFrameInternal, _colorFormat, _profile);
            isNeedReconfigure = true;
            m_bSuccessInit = true;
            Log.e(TAG, "Java call initEncoder finished [skip generate key info]!!! err:" + err);
        }
        else
        {
            // 先用模拟数据生成关键数据;
            int lv_iColorFormat = getSupportedColorFormat();
            m_useInputSurface = false;

            setEncoder(_width, _height, _frameRate, _bitRate, _iFrameInternal, lv_iColorFormat, _profile);
            startEncoder();
            err = generateExtraData();
            Log.i(TAG, "initEncoder: 结束初始化1");
            stopEncoder();
            Log.i(TAG, "initEncoder: 结束初始化2");
            if ( err >=0 )
            {
                // 生成关键数据需要模拟内存数据，所以在模拟内存数据生成完成后置为目标状态；
                m_useInputSurface = _bUseInputSurface;
                if( true==m_useInputSurface)
                {
                    _colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
                }
                setEncoder(_width, _height, _frameRate, _bitRate, _iFrameInternal, _colorFormat, _profile);
                AVCQueue.clear();

                if( true==m_useInputSurface)
                {
                    err = exceptionCheck();
                }
                else
                {
                    err = 0;
                }

                m_bSuccessInit = err<0?false:true;

            }
            Log.e(TAG, "Java call initEncoder finished!!! err:" + err);
        }


        return err;
    }


    // 设置编码参数；
    public int setEncoder( int _width, int _height, int _frameRate, int _bitRate, int _iFrameInternal, int _iColorFormat, int _profile)
    {
        configStatus = 0;
        if( _width>0)
        {
            width 		= 	_width;
        }

        if( _height>0)
        {
            height		= 	_height;
        }


        if ( _frameRate > 0)
        {
            if ( _frameRate < minFrameRate)
            {
                String str = String.format( Locale.getDefault(), "_frameRate:[%d] is too small, change to %d", _frameRate, minFrameRate);
                Log.e( TAG, str);
                _frameRate = minFrameRate;
            }
            else if( _frameRate > maxFrameRate)
            {
                String str = String.format( Locale.getDefault(), "_frameRate:[%d] is too large, change to %d", _frameRate, maxFrameRate);
                Log.e( TAG, str);
                _frameRate = maxFrameRate;
            }

            if( frameRate!=_frameRate)
            {
                frameRate	=	_frameRate;
                isNeedReconfigure = true;
                configStatus |= maskFrameRate;
            }
        }


        if( _bitRate>0 && bitRate != _bitRate)
        {
            bitRate		=	_bitRate;
            isNeedReconfigure = true;
            configStatus |= maskBitRate;
        }

        if( _iFrameInternal>0)
            iFrameInternal	= _iFrameInternal;

        if( _iColorFormat>0)
            colorFormat	=	_iColorFormat;

        if( _profile>=0)
            profile = _profile;

        return 0;
    }
    // 获取extradata；
    public int getExtraData( byte[] output)
    {
        int length = 0;
        if( null != output && null != configbyte)
        {
            System.arraycopy(configbyte, 0, output, 0, configbyte.length);
            length = configbyte.length;
        }

        YXLog(String.format("output%c=null configbyte%c=null", output==null?'=':'!', configbyte==null?'=':'!'));

        return length;
    }

    // 输出日志
    public static void YXLog(String info)
    {
        Log.i(TAG, info);
    }
    // 生成extradata
    private int generateExtraData()
    {
        int lv_iYSize = width * height;
        int lv_iYUVSize = lv_iYSize * 3 / 2;
        byte[] yuvData = new byte[lv_iYUVSize];
        byte[] avcData = new byte[lv_iYUVSize];
        int lv_iCount = 0;
        int err = 0;
        while( configbyte==null)
        {
            err = encodeVideoFromBuffer(yuvData, avcData);
            if ( err<0)
            {
                break;
            }

            if ( configbyte==null)
            {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            ++lv_iCount;
            if( lv_iCount>=10)
            {
                break;
            }

        }
        YXLog(String.format("generateExtraData %s !!!", configbyte==null?"failed":"succeed"));

        isNeedReconfigure = true;
        configStatus |= maskForceRestart;
        return err;
    }

    private int exceptionCheck()
    {

        int err = configbyte==null ? -1 : 0;
        try {
            reconfigureMediaFormat();
            m_mediaCodec = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
            m_mediaCodec.configure(m_codecFormat, null, null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE);
            m_surface = m_mediaCodec.createInputSurface();
            m_mediaCodec.start();
            m_mediaCodec.stop();
            m_mediaCodec.release();
            m_mediaCodec = null;
            m_surface.release();
            m_surface = null;
            YXLog(String.format("exceptionCheck succeed !!!"));

        } catch (Exception e) {
            e.printStackTrace();
            err = -1;
            YXLog(String.format("exceptionCheck failed !!!"));
        }
        return err;

    }


    // 放入图像数据并取出编码后的数据；
    @SuppressLint( "NewApi")
    public int encodeVideoFromBuffer( byte[] input, byte[] output)
    {
        Log.i(TAG, "encodeVideoFromBuffer: 编码开始");
        // 重新配置编码器;
        if (true == isNeedReconfigure)
        {
            Log.d(TAG, "encodeVideoFromBuffer: 重新配置");
            if( configStatus==maskBitRate && Build.VERSION.SDK_INT>=19)// SDK_INT >= 19 支持动态码率设置，不需要重新配置编码器
            {
                Log.d(TAG, "encodeVideoFromBuffer: 修改参数");
                Bundle config = new Bundle();
                config.putInt( MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitRate);
                m_mediaCodec.setParameters(config);
                configStatus = 0;
            }
            else
            {
                Log.d(TAG, "encodeVideoFromBuffer: 重启编码器");
                restartEncoder();
                Log.i(TAG, "encodeVideoFromBuffer: 结束重启");
            }

            isNeedReconfigure = false;
        }

//        drainOutputBuffer();

        int inputBufferIndex;

        try
        {
            inputBufferIndex = m_mediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
            if (inputBufferIndex >= 0) {
                long pts = computePresentationTime(m_getnerateIndex);
                ByteBuffer inputBuffer = getInputBufferByIdx(inputBufferIndex);
                inputBuffer.clear();
                Log.d(TAG, "encodeVideoFromBuffer: 大小"+Integer.toString(inputBuffer.remaining()));
                inputBuffer.put(input,0,width*height*3/2);
                Log.i(TAG, "encodeVideoFromBuffer: "+Long.toString(pts));
                m_mediaCodec.queueInputBuffer(inputBufferIndex, 0, width*height*3/2, pts, 0);
                ++m_getnerateIndex;
            }

            drainOutputBuffer();
            mLastCodecData = AVCQueue.poll();

            int length = 0;
            if( null != output && null != mLastCodecData)
            {
                length = mLastCodecData.data.length;
                System.arraycopy(mLastCodecData.data, 0, output, 0, length);
            }
            Log.i(TAG, "encodeVideoFromBuffer: 编码结束");
            return length;

        }
        catch (Exception e)
        {
            e.printStackTrace();
            return -1;
        }
    }
    private long computePresentationTime(long frameIndex) {

        long timestamp = m_startTime + (frameIndex * 1000000000 / frameRate);
        return timestamp;
    }
    @SuppressWarnings("deprecation")
    private ByteBuffer getInputBufferByIdx(int _idx)
    {
        if( Build.VERSION.SDK_INT>=21)
        {
            return m_mediaCodec.getInputBuffer(_idx);
        }
        else
        {
            ByteBuffer[] inputBuffers = m_mediaCodec.getInputBuffers();
            return inputBuffers[_idx];
        }
    }

    @SuppressWarnings("deprecation")
    private ByteBuffer getOutputBufferByIdx(int _idx)
    {
        if( Build.VERSION.SDK_INT>=21)
        {
            return m_mediaCodec.getOutputBuffer(_idx);
        }
        else
        {
            ByteBuffer[] outputBuffers = m_mediaCodec.getOutputBuffers();
            return outputBuffers[_idx];
        }
    }

    private void addOutputData( byte[] _data, long _pts, int _flag)
    {
        int l = _data.length;
        byte[] tmpdata;
        CodecData data = new CodecData();

        if (isHuaweiP9AndHonor8 && (_data[l - 1] == 0) && (_data[l - 2] == 0) && (_data[l - 3] == 0) && (_data[l - 4] == 0)) {
            tmpdata = new byte[_data.length - 8];
            System.arraycopy(_data, 0, tmpdata, 0, _data.length - 8);
            data.data 	= tmpdata;
        }else{
            data.data 	= _data;
        }

        data.pts	= _pts * 1000; //input: nano output:milli
        data.flag   = _flag;

        try
        {
            AVCQueue.add(data);
        }
        catch ( Exception e)
        {
            e.printStackTrace();
        }
    }
    // 根据编码参数生成编码格式信息；
    private int reconfigureMediaFormat()
    {
        if( m_verbose)
        {
            Log.d(TAG, "call reconfigureMediaFormat !!!");
        }

        m_codecFormat = MediaFormat.createVideoFormat( VIDEO_MIME_TYPE, width, height);
        m_codecFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatYUV420Flexible);
        m_codecFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        m_codecFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        m_codecFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInternal);

        Log.i(TAG, String.format( "width:[%d] height:[%d] frameRate:[%d] iFrameInternal:[%d] bitRate:[%d] colorFormat:[%d]", width, height, frameRate, iFrameInternal, bitRate, colorFormat));

        return 0;
    }
    @SuppressLint({"NewApi", "WrongConstant"})
    private void drainOutputBuffer()
    {
        Log.i(TAG, "drainOutputBuffer: enter drainoutputbuffer");
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        int outputBufferIndex =0 ;
        try
        {
            outputBufferIndex = m_mediaCodec.dequeueOutputBuffer( bufferInfo, TIMEOUT_USEC);

        }
        catch ( Exception e)
        {
            e.printStackTrace();
        }
        Log.d(TAG, "drainOutputBuffer: id"+Integer.toString(outputBufferIndex));
        int m = 0;
        if( outputBufferIndex== MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
        {
            MediaFormat format =  m_mediaCodec.getOutputFormat();
            ByteBuffer csd0 = format.getByteBuffer("csd-0");
            ByteBuffer csd1 = format.getByteBuffer("csd-1");
            if ( csd0 != null && csd1 != null)
            {
                int configLength = 0;
                sps = csd0.array().clone();
                pps = csd1.array().clone();
                configLength = sps.length + pps.length;
                configbyte = new byte[configLength];
                System.arraycopy( sps, 0, configbyte, 0, sps.length);
                System.arraycopy( pps, 0, configbyte, sps.length, pps.length);
            }
        }
        if(outputBufferIndex == -2){

//            m_mediaCodec.releaseOutputBuffer(outputBufferIndex,	false);
            try
            {
                outputBufferIndex = m_mediaCodec.dequeueOutputBuffer( bufferInfo, TIMEOUT_USEC);
//                m_mediaCodec.releaseOutputBuffer(outputBufferIndex,	false);
            }
            catch ( Exception e)
            {
                e.printStackTrace();
            }

            m = bufferInfo.size;}

        while (outputBufferIndex >= 0 )
        {
//            while(bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                // Log.i("AvcEncoder",
                // "Get H264 Buffer Success! flag = "+bufferInfo.flags+",pts = "+bufferInfo.presentationTimeUs+"");
                ByteBuffer outputBuffer = getOutputBufferByIdx(outputBufferIndex);
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.position(bufferInfo.offset);
                outputBuffer.limit(bufferInfo.offset
                        + bufferInfo.size);
                outputBuffer.get(outData);
                Log.i(TAG, "drainOutputBuffer: " + Integer.toString(bufferInfo.size));
                if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    Log.i("bufferinfo帧类型", "encode: 配置帧");
                    configbyte = outData;
                    m_mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex =m_mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                    Log.d(TAG, "drainOutputBuffer: id"+Integer.toString(outputBufferIndex));
//                    TIMEOUT_USEC = 50;
                    continue;

                } else if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                    Log.i("bufferinfo帧类型", "encode: 关键帧");
                    if (configbyte != null) {
                        // I帧数据里面包含关键头数据，为了统一输出格式，在这里将其去掉；
                        if (outData[4] == configbyte[4] && (outData[configbyte.length + 4] & 0x1f) == 5) {
                            Log.i(TAG, "drainOutputBuffer: 去掉I帧头");
                            byte[] clipData = new byte[outData.length - configbyte.length];
                            System.arraycopy(outData, configbyte.length, clipData, 0, clipData.length);
                            outData = clipData;
                        }
                    } else {
                        // TODO:可能某种些手机通过两种方式都未获取到关键数据，那么这个时候关键数据一定存放在I帧里面
                        // TODO:这个时候需要我们直接从I帧里面提取；
                        Log.e(TAG, "I can't find configbyte!!!! NEED extract from I frame!!!");
                    }

                    addOutputData(outData, bufferInfo.presentationTimeUs, bufferInfo.flags);

                } else if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    break;
                } else {
                    Log.i("bufferinfo帧类型", "encode: 普通帧");
                    addOutputData(outData, bufferInfo.presentationTimeUs, bufferInfo.flags);
                }

                m_mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                break;
            }

            //outputBufferIndex = m_mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);

//        }

//        }

        Log.i(TAG, "drainOutputBuffer: leave drainoutputbuffer");
    }
    public int startEncoder()
    {
        String model = android.os.Build.MODEL;
        String manufacturer = android.os.Build.MANUFACTURER;
        String radioVersion = Build.getRadioVersion();

        if ( (manufacturer.trim().contains("HUAWEI") && model.trim().contains("EVA-AL00"))
                || (manufacturer.trim().contains("HUAWEI") && model.trim().contains("FRD-AL00"))
                ){
            isHuaweiP9AndHonor8 = true;
        }else {
            isHuaweiP9AndHonor8 = false;

        }

        if( m_verbose)
        {
            Log.d(TAG, "call startEncoder !!!");
        }

        try {
            reconfigureMediaFormat();
            m_mediaCodec = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
            m_mediaCodec.configure(m_codecFormat, null, null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE);
            m_mediaCodec.start();
            m_startTime = 0;////////System.nanoTime();
            isNeedReconfigure = false;

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }

        return 0;
    }

    // 停止编码功能；
    public void stopEncoder()
    {
        if( m_verbose)
        {
            Log.d(TAG, "call stopEncoder !!!");
        }
        try {


            if (null != m_mediaCodec) {
                m_mediaCodec.stop();
                m_mediaCodec.release();
                m_mediaCodec = null;
            }

            if ( null != m_surface)
            {
                m_surface.release();;
                m_surface=null;
            }




        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // 重启编码器;
    public int restartEncoder()
    {
        if( m_verbose)
        {
            Log.d(TAG, "call restartEncoder !!!");
        }
        m_bNeedSingalEnd = false;
        stopEncoder();
        startEncoder();
        return 0;
    }
    public int getSupportedColorFormat() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;

        if (m_verbose)
            Log.d(TAG, "manufacturer = " + manufacturer + " model = " + model);

        if( Build.VERSION.SDK_INT>=21)
        {
            m_codecInfo = findCodec(VIDEO_MIME_TYPE);
        }
        else
        {
            m_codecInfo = selectCodec(VIDEO_MIME_TYPE);
        }

        if ((manufacturer.compareTo("Xiaomi") == 0) // for Xiaomi MI 2SC,
                // selectCodec methord is
                // too slow
                && (model.compareTo("MI 2SC") == 0))
            return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;

        if ((manufacturer.compareTo("Xiaomi") == 0) // for Xiaomi MI 2,
                // selectCodec methord is
                // too slow
                && (model.compareTo("MI 2") == 0))
            return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;

        if ((manufacturer.compareTo("samsung") == 0) // for samsung S4,
                // COLOR_FormatYUV420Planar
                // will write green
                // frames
                && (model.compareTo("GT-I9500") == 0))
            return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;

        if ((manufacturer.compareTo("samsung") == 0) // for samsung 混手机,
                // COLOR_FormatYUV420Planar
                // will write green
                // frames
                && (model.compareTo("GT-I9300") == 0))
            return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;


        if (m_codecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for "
                    + VIDEO_MIME_TYPE);

            return -1;
        }

        try {
            MediaCodecInfo.CodecCapabilities capabilities = m_codecInfo
                    .getCapabilitiesForType(VIDEO_MIME_TYPE);

            MediaCodecInfo.CodecProfileLevel[] levels = capabilities.profileLevels;
            Log.e( TAG, "CodecProfileLevel" + levels.toString());

//            for (int i = 0; i < capabilities.colorFormats.length; i++) {
//                int colorFormat = capabilities.colorFormats[i];
//                Log.e( TAG, "colorFormats:[" + i + "]=" + capabilities.colorFormats[i]);
//            }


            for (int i = 0; i < capabilities.colorFormats.length; i++) {
                int colorFormat = capabilities.colorFormats[i];

                if (isRecognizedFormat(colorFormat))
                    return colorFormat;
            }
        } catch (Exception e) {
            if (m_verbose)
                Log.d(TAG, "getSupportedColorFormat exception");

            return -1;
        }

        return -1;
    }
    // support these color space currently
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                return true;
            default:
                return false;
        }
    }
    @SuppressWarnings("deprecation")
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();

        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    Log.e(TAG, "codecInfo[" + i + "].name=" + codecInfo.getName());
                    return codecInfo;
                }
            }
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static MediaCodecInfo findCodec(String mimeType)
    {
        MediaCodecList mediaLst = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaCodecInfo[] codecInfos = mediaLst.getCodecInfos();
        for( int i=0; i<codecInfos.length; ++i)
        {
            MediaCodecInfo codecInfo = codecInfos[i];
            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++)
            {
                if (types[j].equalsIgnoreCase(mimeType))
                {
                    Log.e(TAG, "codecInfo[" + i + "].name=" + codecInfo.getName());
                    return codecInfo;
                }
            }

        }
        return null;
    }

    public static boolean isInNotSupportedList() {
        return false;
    }
    public int closeEncoder()
    {

        stopEncoder();


        Log.e(TAG, "Java call closeEncoder finished!!!");

        return 0;
    }



}
