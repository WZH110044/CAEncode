package com.example.a36970.myapplication;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaFormat.MIMETYPE_VIDEO_AVC;

/**
 * Created by wzh on 2018/12/28.
 */

public class HardEncode {
    private MediaCodec hardcodec ;
    private MediaFormat mformat = new MediaFormat();
    private ByteBuffer outputBuffer;
    private ByteBuffer inputBuffer;
    private byte[] dst ;
    private int pts= 0;
    private static int size = 0;
    private FileOutputStream fos;
    private MediaCodec.BufferInfo bufferInfo;
    HardEncode(){

    }
    /* 示例参数
    * 宽：1920，高：1080，比特率：40000000，帧率：30，关键帧间隔：5
    */
    public void init(int width,int height ,int bit_rate ,int frame_rate,int gop) throws IOException {
        //Log.i("init", "init: ");
        Log.i("init", "init: 硬编码初始化qq");
        dst = new byte[width * height];
        size = width * height *3/2;
        //创建mediacodec
        try{
            hardcodec = MediaCodec.createEncoderByType(MIMETYPE_VIDEO_AVC);
        }catch(IOException e){
            e.printStackTrace();
            return;
        }
        //创建mediaformat
        mformat=MediaFormat.createVideoFormat(MIMETYPE_VIDEO_AVC,width,height);
        //设置mediacode信息（格式），状态变为configured
//        MediaFormat outputformat = hardcodec.getOutputFormat();
        mformat.setInteger(MediaFormat.KEY_BIT_RATE, bit_rate);
        mformat.setInteger(MediaFormat.KEY_FRAME_RATE, frame_rate);
        mformat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
//        mformat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, gop);
        mformat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);

        hardcodec.configure(mformat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        bufferInfo = new MediaCodec.BufferInfo();
       //打开文件
       fos = new FileOutputStream("/sdcard/111.264",true);
        hardcodec.start();
        Log.i("init", "init: 硬编码结束");
    }
    public void release() throws IOException {
        Log.i("init", "init: 硬解码释放");
        hardcodec.release();
        //文件释放
        if(fos!=null) {
            fos.close();
        }
    }
    public static void Nv21ToYuv420SP(byte[] data, byte[] dstData) {
        System.arraycopy(data, 0, dstData, 0, size);

        for (int i = 0; i < size / 4; i++) {
            dstData[size + i * 2] = data[size + i * 2 + 1] ;//U
            dstData[size + i * 2 + 1] = data[size + i * 2]; //V
        }
    }
    /*参数说明
    * YUV420SP的byte数组，264数据byte数组
    * 返回值
    * 返回有效数据的大小（编码后帧大小）,负数即为失败
    * */
    public int encode(byte[] YUVbuffer,byte[] h264buffer) throws IOException {
        Log.i("encode", "encode: 硬编码编码");
        //转换，NV21-420sp
        //Nv21ToYuv420SP(YUVbuffer,dst,1920,1080);
        //Log.i("hardencode", "encode: 送入缓冲区");
        int inputBufferId = hardcodec.dequeueInputBuffer(-1);
        if (inputBufferId >= 0) {
           // Log.i("input", "encode: input");
            inputBuffer = hardcodec.getInputBuffer(inputBufferId);
            inputBuffer.clear();
            //填入数据
            inputBuffer.put(YUVbuffer,0,size);
            hardcodec.queueInputBuffer(inputBufferId,0,size,pts,0);
            pts= pts+33000;
        }else{
            //获取缓冲区失败
            return -2;
        }
        Log.i("encode", "encode: 输入成功");
        /******************************************************************************************/
        //阻塞等待编码完成
        int outputBufferId = hardcodec.dequeueOutputBuffer(bufferInfo,-1);
        //编码完成，取数据
        Log.i("bufferinfo帧大小", "encode: "+ bufferInfo.size);
        if(outputBufferId == -2){
//            hardcodec.releaseOutputBuffer(outputBufferId, false);
            outputBufferId = hardcodec.dequeueOutputBuffer(bufferInfo,-1);
        }

        while (outputBufferId >= 0) {
            //Log.i("循环获取缓冲区数据", "encode: ff");
            outputBuffer = hardcodec.getOutputBuffer(outputBufferId);
            outputBuffer.get(h264buffer,0, bufferInfo.size);
            //写文件
            if(bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME){
                Log.i("bufferinfo帧类型", "encode: 关键帧");
                fos.write(h264buffer,0,bufferInfo.size);
            }else if(bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG){
                Log.i("bufferinfo帧类型", "encode: 配置帧");
                fos.write(h264buffer,0,bufferInfo.size);
                hardcodec.releaseOutputBuffer(outputBufferId, false);
                 outputBufferId = hardcodec.dequeueOutputBuffer(bufferInfo,-1);
                Log.i("bufferinfo帧大小", "encode: "+ bufferInfo.size);
                outputBuffer = hardcodec.getOutputBuffer(outputBufferId);
                outputBuffer.get(h264buffer,0, bufferInfo.size);
                fos.write(h264buffer,0,bufferInfo.size);
                break;
//                fos.write(h264buffer,0,bufferInfo.size);
            }else{
                Log.i("bufferinfo帧类型", "encode: 其他");
                fos.write(h264buffer,0,bufferInfo.size);
            }
            hardcodec.releaseOutputBuffer(outputBufferId, false);
            //outputBufferId = hardcodec.dequeueOutputBuffer(bufferInfo, -1);
            Log.i("hardencode", "encode: 结束");
            return bufferInfo.size;
        }
//        else{
//            //获取缓冲区失败
//            Log.i("hardencode", "encode: 结束");
//            return -1;
//        }

//
        return bufferInfo.size;
    }

}

