package com.example.a36970.myapplication;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static android.media.MediaFormat.MIMETYPE_VIDEO_AVC;

/**
 * Created by 36970 on 2018/12/28.
 */

public class HardEncode {
    private MediaCodecList list;
    private MediaCodecInfo info;
    private MediaCodec hardcodec ;
    private MediaFormat mformat = new MediaFormat();
    private ByteBuffer outputBuffer;
    private ByteBuffer inputBuffer;
    private byte[] dst = new byte[5000000];
    private int pts= 1;
    MediaCodec.BufferInfo bufferInfo;
    public void init() throws IOException {
        //创建mediacodec
        hardcodec = MediaCodec.createEncoderByType(MIMETYPE_VIDEO_AVC);
        //创建mediaformat
        mformat=MediaFormat.createVideoFormat(MIMETYPE_VIDEO_AVC,1920,1080);
        //设置mediacode信息（格式），状态变为configured，指定关联surface


//        MediaFormat outputformat = hardcodec.getOutputFormat();
        mformat.setInteger(MediaFormat.KEY_BIT_RATE, 4000000);
        mformat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        mformat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        mformat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
        hardcodec.configure(mformat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
       bufferInfo = new MediaCodec.BufferInfo();
        hardcodec.start();
    }
    public void release(){

    }
    public static void Nv21ToYuv420SP(byte[] data, byte[] dstData, int w, int h) {
        int size = w * h;
        // Y
        System.arraycopy(data, 0, dstData, 0, size);

        for (int i = 0; i < size / 4; i++) {
            dstData[size + i * 2] =  data[size + i * 2];//U
            dstData[size + i * 2 + 1] = data[size + i * 2 + 1]; //V
        }
    }
//    public void input(byte[] YUVbuffer,int size){
//
//    }

    public void encode(byte[] YUVbuffer,int size,byte[] h264buffer){
        Nv21ToYuv420SP(YUVbuffer,dst,1920,1080);
        Log.i("hardencode", "encode: 开始");
        int inputBufferId = hardcodec.dequeueInputBuffer(-1);
        if (inputBufferId >= 0) {
            Log.i("input", "encode: input");
            inputBuffer = hardcodec.getInputBuffer(inputBufferId);
            inputBuffer.clear();
            inputBuffer.put(dst,0,size);
            hardcodec.queueInputBuffer(inputBufferId,0,size,pts++,BUFFER_FLAG_CODEC_CONFIG);
        }
        int outputBufferId = hardcodec.dequeueOutputBuffer(bufferInfo,-1);
        Log.i("bufferinfo", "encode: "+ bufferInfo.size);
        if (outputBufferId >= 0) {
            Log.i("ff", "encode: ff");
            outputBuffer = hardcodec.getOutputBuffer(outputBufferId);
            outputBuffer.get(h264buffer);

            hardcodec.releaseOutputBuffer(outputBufferId, false);
            //outputBufferId = hardcodec.dequeueOutputBuffer(bufferInfo, -1);
        }
        Log.i("hardencode", "encode: 结束");
    }
}

