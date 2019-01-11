#include <jni.h>
#include <string>
#include <stdio.h>

#include <pthread.h>
#include <x264.h>
//#include <x264.h>

#include <android/log.h>
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,"testff",__VA_ARGS__)
#define XLOGE(...)  __android_log_print(ANDROID_LOG_ERROR,"testff",__VA_ARGS__)
extern "C"{
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/opt.h>
#include <libavutil/dict.h>
#include <libavutil/imgutils.h>
#include <libavutil/log.h>
#include <sys/syscall.h>
#include <x264.h>
#include "libavutil/frame.h"
#include "libswresample/swresample.h"
#include "libavutil/channel_layout.h"
}
JavaVM *j_vm;
const char *Curl = NULL;
const char *Aurl = NULL;
unsigned char *chars = (unsigned char*)malloc(200);
int Cwidth,Cheight = 0;
int flag = 0;
int size = 0;
jobject hardObject;
int64_t Atime = 0;
int64_t Ctime = 0;
FILE *fp;
AVPacket Aud[10];
AVPacket Cam[10];
extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_a36970_myapplication_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    hello+=avcodec_configuration();

    return env->NewStringUTF(hello.c_str());
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_a36970_myapplication_MainActivity_Open(JNIEnv *env, jobject instance, jstring url_,
                                                        jobject handle) {
    const char *url = env->GetStringUTFChars(url_, 0);

    // TODO
    FILE *fp = fopen(url,"rb");
    if(!fp){
        LOGW("%s open failed!",url);
    }
    else{

        LOGW("%s open successed!",url);
        fclose(fp);
    }
    return true;
    env->ReleaseStringUTFChars(url_, url);
}
void *write(void *arg){
    while(1){
    while(!flag);
    LOGW("写入文件");
    if(fp==NULL){
        LOGW("文件打开失败");
    }
    fwrite(chars, 1,size, fp);
    LOGW("写入成功");
    flag = 0;}
}
void muxingAC(long timeA, long timeC){
    AVFormatContext *oc = NULL;
    avformat_alloc_output_context2(&oc, NULL, NULL, "/sdcard/a.mpeg");
    if (!oc) {
        avformat_alloc_output_context2(&oc, NULL, "mpeg", "/sdcard/a.mpeg");
    }
    if (!oc)
        return;

}
class ffAudioencodec{
private:
    AVCodec *pcodec = NULL;
    AVCodecContext *pCodecCtx =NULL;
    AVFrame *pFrame = NULL;
    AVPacket *pkt = NULL;
//    uint16_t *samples;
    int Asize;
    uint8_t* frame_buf = NULL;
    FILE *fpA = NULL;


public:
    ffAudioencodec(){

        fpA = fopen(Aurl,"wb+");
        int ret = 0;
        //注册编码器
        av_register_all();
        pcodec = avcodec_find_encoder_by_name("libfdk_aac");
        if (!pcodec) {
            LOGW("Codec  not found\n");
            //exit(1);
        }
        pCodecCtx =  avcodec_alloc_context3(pcodec);
        if (!pCodecCtx) {
            LOGW("Could not allocate video codec context\n");
            //  exit(1);
        }
        pCodecCtx->codec = pcodec;
        //pCodecCtx = audio_st->codec;
        //pCodecCtx->codec_id = fmt->audio_codec;
        pCodecCtx->codec_id = AV_CODEC_ID_AAC;
        pCodecCtx->codec_type = AVMEDIA_TYPE_AUDIO;
        pCodecCtx->sample_fmt = AV_SAMPLE_FMT_S16;
        pCodecCtx->sample_rate= 44100;
        pCodecCtx->channel_layout=AV_CH_LAYOUT_STEREO;
        pCodecCtx->channels = 2;
//        pCodecCtx->channels = av_get_channel_layout_nb_channels(pCodecCtx->channel_layout);
        pCodecCtx->bit_rate = 64000;
//
        if (avcodec_open2(pCodecCtx, pcodec, NULL) < 0) {
            LOGW("AVcodec初始化失败");
            return ;
        }

        pkt = av_packet_alloc();
        if (!pkt) {
            LOGW("packet初始化失败");
           // fprintf(stderr, "could not allocate the packet\n");
            return ;
        }

        /* frame containing input raw audio */
        pFrame = av_frame_alloc();
        if (!pFrame) {
            LOGW("frame初始化失败");
           // fprintf(stderr, "Could not allocate audio frame\n");
            return;
        }

        pFrame->nb_samples     = pCodecCtx->frame_size;
        pFrame->format         = pCodecCtx->sample_fmt;
        pFrame->channel_layout = pCodecCtx->channel_layout;
        pFrame->channels = 2;
        av_frame_get_buffer(pFrame, 0);
        Asize = av_samples_get_buffer_size(NULL, pCodecCtx->channels, pCodecCtx->frame_size, pCodecCtx->sample_fmt, 0);
        LOGW("音频初始化成功");

    }
    void startencode(jbyte* data,int i){
        //Asize  = av_samples_get_buffer_size(NULL,pCodecCtx->channels,pCodecCtx->frame_size,pCodecCtx->sample_fmt, 1);

       // Asize = 4096;
        //samples = (uint16_t*)pFrame->data[0];
        if(Asize < 0 ){
            LOGW("音频调试信息 获取size失败");
        }
        frame_buf = (uint8_t *)av_malloc(Asize);
        int ret = 0;

       // avcodec_fill_audio_frame(pFrame, pCodecCtx->channels, pCodecCtx->sample_fmt,samples, Asize, 1);
        //(uint16_t*)pFrame->data[0];
        LOGW("大小%d",Asize);
//        for(int w =0 ; w <Asize; w=w+2){
//            frame_buf[w] = data[w];
//        }
       // LOGW("%d",sizeof(data)/sizeof(data[0])) ;
        memcpy(frame_buf,data,4096);
      //  pFrame->data[0] = frame_buf;
        avcodec_fill_audio_frame(pFrame , pCodecCtx->channels, pCodecCtx->sample_fmt , (const uint8_t*)frame_buf, size, 0);
        pFrame->data[0] = frame_buf;
//        pFrame->data[0] = frame_buf;
       // pFrame->extended_data[0] = frame_buf+2048;
        LOGW("pts:%d",i);
        pFrame->pts=(int64_t)(i*1024);
//        LOGW("pts%d",(pCodecCtx->frame_size * 1000 / pCodecCtx->sample_rate));
////        /* send the frame for encoding */
        ret = avcodec_send_frame(pCodecCtx, pFrame);
        if (ret < 0) {
            LOGW("send frame failed");
            return ;
        }
        /* read all the available output packets (in general there may be any
         * number of them */
        while (ret >= 0) {
            ret = avcodec_receive_packet(pCodecCtx, pkt);
            if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
            {
                LOGW("无数据");
                return;
            }
            else if(ret<0) {
                LOGW("encode fail");
            } else LOGW("encode success 1 frame");
//            fwrite(chars,1,7,fpA);
            fwrite(pkt->data, 1, pkt->size, fpA);
            LOGW("pts:%llu",pkt->pts);
            av_packet_unref(pkt);
        }
    }

};
class x264encodec{
private:
    int iNal   = 0;
    x264_nal_t* pNals = NULL;
    x264_t* pHandle   = NULL;
    x264_picture_t* pPic_in = (x264_picture_t*)malloc(sizeof(x264_picture_t));
    x264_picture_t* pPic_out = (x264_picture_t*)malloc(sizeof(x264_picture_t));
    x264_param_t param;
    int y_size1;
    unsigned char * h = NULL;
    //x264_param_t* pParam = (x264_param_t*)malloc(sizeof(x264_param_t));
public:
    x264encodec(){

        //LOGW("x264当前线程ID为%lu",pthread_self());
        fp = fopen(Curl,"wb+");
        //ultrafast cpu占用最小，zerolatency 不缓存帧
       //
        x264_param_default_preset(&param,"ultrafast","zerolatency");
        //x264_param_default_preset(&param,"preset","");
        param.i_csp = X264_CSP_NV12;
        //配置x264编码器参数
//        param.i_sync_lookahead = 1;
//        param.rc.b_stat_read == 0;
//        param.rc.b_stat_write == 1;
        param.i_width = Cwidth;
        param.i_height = Cheight;
        param.i_keyint_max = 20;
        param.i_keyint_min = 10;
        param.i_fps_num = 25;
        param.i_fps_den = 1;
        param.i_threads = 0; // 建议为cpu个数
        //param.i_slice_count = 4;
        param.b_sliced_threads = 0;
        param.i_scenecut_threshold = 40;
        //param.i_level_idc = 30;
        param.b_repeat_headers = 1;//复制sps和pps放在每个关键帧的前面 该参数设置是让每个关键帧(I帧)都附带sps/pps。
//    param.b_cabac = 1; //自适应上下文算术编码，baseline 不支持
        //param.
        param.rc.i_bitrate = 4000000/1000;//码率(比特率,单位Kbps)
        param.rc.i_rc_method = X264_RC_CRF;//参数i_rc_method表示码率控制，CQP(恒定质量)，CRF(恒定码率)，ABR(平均码率)
        param.rc.i_vbv_buffer_size = 4000000/1000;//设置了i_vbv_max_bitrate必须设置此参数，码率控制区大小,单位kbps
       //
         param.rc.i_vbv_max_bitrate = (int) (4000000/ 1000 * 1.2);//瞬时最大码率
       // param.rc.f_rf_constant_max  = 30;
        ///param.rc.i_qp_constant;
        param.i_bframe = 0;//b帧数
        //param.analyse.b_psy= 0;
      //  param.rc.i_lookahead = 0;//
        param.i_frame_reference = 1;//参考帧数
        param.b_intra_refresh = 1;
       // param.b_cabac = 0;
        //param.analyse.
       // param.rc.b_mb_tree = 0;
        //param.i_frame_reference = 0;
        //param.analyse.intra = 0;
        //param.analyse.b_chroma_me =X264_ME_DIA;
        //param.analyse.i_me_method = X264_ME_DIA;
       // param.analyse.i_subpel_refine = 1;
       // param.analyse.b_mixed_references = 1;
        //x264_param_apply_profile(&param,x264_profile_names[0]);
        pHandle = x264_encoder_open(&param);
      //  LOGW("llllllllllllll%d",param.analyse.i_weighted_pred);
        x264_picture_init(pPic_out);
        y_size1  = Cwidth*Cheight;

    }
    void encode(jbyte* data,jbyte* data2,int i){
        x264_picture_alloc(pPic_in, X264_CSP_NV12, param.i_width, param.i_height);
        int m = 0 ;
        int k = 0;
        int n = 0 ;
        LOGW( "转换开始");
        memcpy(pPic_in->img.plane[0],data,y_size1);
        LOGW("转换中");
        memcpy(pPic_in->img.plane[1],data2,y_size1/2);
        //循环展开优化
//        for(int w = y_size1;w<(y_size1+y_size1/2);w= w +16){
//            pPic_in->img.plane[1][k] = (unsigned char)data2[n++];
//            pPic_in->img.plane[2][k++] = (unsigned char)data2[n++];
//            pPic_in->img.plane[1][k] = (unsigned char)data2[n++];
//            pPic_in->img.plane[2][k++] = (unsigned char)data2[n++];
//            pPic_in->img.plane[1][k] = (unsigned char)data2[n++];
//            pPic_in->img.plane[2][k++] = (unsigned char)data2[n++];
//            pPic_in->img.plane[1][k] = (unsigned char)data2[n++];
//            pPic_in->img.plane[2][k++] = (unsigned char)data2[n++];
//            pPic_in->img.plane[1][k] = (unsigned char)data2[n++];
//            pPic_in->img.plane[2][k++] = (unsigned char)data2[n++];
//            pPic_in->img.plane[1][k] = (unsigned char)data2[n++];
//            pPic_in->img.plane[2][k++] = (unsigned char)data2[n++];
//            pPic_in->img.plane[1][k] = (unsigned char)data2[n++];
//            pPic_in->img.plane[2][k++] = (unsigned char)data2[n++];
//            pPic_in->img.plane[1][k] = (unsigned char)data2[n++];
//            pPic_in->img.plane[2][k++] = (unsigned char)data2[n++];
//
//            //  k++;
//        }
        LOGW("转换结束");
        pPic_in->i_pts = i;

        LOGW("开始解码第%d帧",i);
        x264_encoder_encode(pHandle, &pNals, &iNal, pPic_in, pPic_out);
        LOGW("llllllllllllll%d",param.analyse.i_weighted_pred);
        LOGW("结束解码第%d帧",i);
        size = 0;
        LOGW("写入文件");
        for(int j = 0;j<iNal;j++)
        {
            fwrite(pNals[j].p_payload, 1,pNals[j].i_payload, fp);
            size+=pNals[j].i_payload;
        }
        LOGW("解码%5d大小的帧",size);
        LOGW("写入成功");
        x264_picture_clean(pPic_in);
        //x264_encoder_close(pHandle);
        //fwrite(chars, 1,size, fp);
        //flag = 1 ;
    };
    void destoryencode(){
        x264_encoder_close(pHandle);
        pHandle = NULL;
        free(pPic_in);
        free(pPic_out);
        free(&param);
        fclose(fp);
    }
};
class encodec{
private:
    AVCodec *pcodec;
    AVCodecContext *pCodecCtx;
    AVFrame *pFrame;
    AVPacket *pkt;
    AVDictionary *param = NULL;

    //OutputStream video_st = { 0 }, audio_st = { 0 };
    const char *filename;
    AVOutputFormat *fmt;
    int y_size = 0;
public:
    encodec(){


        y_size = Cwidth * Cheight;
        fp = fopen(Curl,"wb+");

        int ret = 0;
        //注册编码器
        avcodec_register_all();
        //根据codecid找解码器
        pcodec = avcodec_find_encoder(AV_CODEC_ID_H264);
        if (!pcodec) {
            LOGW("Codec  not found\n");
            //exit(1);
        }
        pCodecCtx =  avcodec_alloc_context3(pcodec);
        if (!pCodecCtx) {
            LOGW("Could not allocate video codec context\n");
            //  exit(1);
        }

        pCodecCtx->codec_type = AVMEDIA_TYPE_VIDEO;
        pCodecCtx->bit_rate = 4000000;
        pCodecCtx->thread_type = FF_THREAD_FRAME;
      //  pCodecCtx->thread_count = 4;
          pCodecCtx->thread_count = 0 ;
          pCodecCtx->slice_count = 0;
        pCodecCtx->width = Cwidth;
        pCodecCtx->height = Cheight;
       // pCodecCtx->refs = 1;
       // pCodecCtx->rc_max_available_vbv_use = 6000000;
        //pCodecCtx->bit

        //
        /* frames per second */
        //pCodecCtx->rc
        pCodecCtx->time_base = (AVRational){1, 25};
        pCodecCtx->framerate = (AVRational){25, 1};

        /* emit one intra frame every ten frames
         * check frame pict_type before passing frame
         * to encoder, if frame->pict_type is AV_PICTURE_TYPE_I
         * then gop_size is ignored and the output of encoder
         * will always be I frame irrespective to gop_size
         */
        pCodecCtx->gop_size = 10;

        pCodecCtx->max_b_frames = 0;
       // pCodecCtx->slice_count = 4;

        pCodecCtx->pix_fmt = AV_PIX_FMT_YUV420P;

        //av_opt_set(pCodecCtx->priv_data, "x264opts", "tune=zerolatency", 0);
//         av_opt_set(pCodecCtx->priv_data, "i_rc_method", "1", 0);
       //  av_opt_set(pCodecCtx->priv_data, "tune", "zerolatency", 0);

        av_dict_set(&param, "preset", "ultrafast", 0);  //设置字典参数"preset"
        av_dict_set(&param, "tune", "zerolatency", 0); //设置字典参数"tune"
//        av_dict_set(&param,"rc-lookahead","0",0);
//        av_dict_set(&param,"mbtree","0",0);
//        av_dict_set(&param, "sc_threshold", "0", 0);
//        av_dict_set(&param,"mbtree","0",0);
       // av_dict_set(&param," rc_strategy","0",0);

        //av_opt_set(pCodecCtx->priv_data,"preset",)

        //offsetof()
        // param->
        if (avcodec_open2(pCodecCtx, pcodec, &param) < 0) {
            //chars = "ssss";
            // LOGW("%s",av_err2str(ret));
            return;
        }
        pFrame = av_frame_alloc();
        if (!pFrame) {
            LOGW("Could not allocate video frame\n");
            return;
        }
        pFrame->format = pCodecCtx->pix_fmt;
        pFrame->width  = pCodecCtx->width;
        pFrame->height = pCodecCtx->height;
       // pFrame->pict_type = AV_PICTURE_TYPE_I;


        // pFrame->pict_type = AV_PICTURE_TYPE_I;
        //ret = av_image_alloc(pFrame->data, pFrame->linesize, pCodecCtx->width, pCodecCtx->height,
        //                    pCodecCtx->pix_fmt, 16);




        av_frame_get_buffer(pFrame, 0);
        LOGW("初始化完成");
        if(pCodecCtx->codec->encode2 == NULL){
            LOGW("codec->send_frame 为空");
        }
    }
    void encode(jbyte* data,jbyte* data2,int i)
    {


        int ret = 0;
        /*******************************************************************/
        pkt = av_packet_alloc();
        //LOGW("进入编码");
       // LOGW("%d",y_size);
        if(pFrame == NULL){
            LOGW("初始化失败");
        }

        av_frame_make_writable(pFrame);
        //LOGW(Cdata[0]);8
//        if(i%5 == 0){
//            pFrame->pict_type = AV_PICTURE_TYPE_I;
//        }
//        else{
//            pFrame->pict_type = AV_PICTURE_TYPE_P;
//        }
        int m = 0 ;
        int k = 0;
        int n = 0 ;
        LOGW( "转换开始");
        memcpy(pFrame->data[0],data,y_size);
        LOGW("转换中");
        for(int w = y_size;w<(y_size+y_size/2);w= w +16){
            pFrame->data[1][k] = (unsigned char)data2[n++];
            pFrame->data[2][k++] = (unsigned char)data2[n++];
            pFrame->data[1][k] = (unsigned char)data2[n++];
            pFrame->data[2][k++] = (unsigned char)data2[n++];
            pFrame->data[1][k] = (unsigned char)data2[n++];
            pFrame->data[2][k++] = (unsigned char)data2[n++];
            pFrame->data[1][k] = (unsigned char)data2[n++];
            pFrame->data[2][k++] = (unsigned char)data2[n++];
            pFrame->data[1][k] = (unsigned char)data2[n++];
            pFrame->data[2][k++] = (unsigned char)data2[n++];
            pFrame->data[1][k] = (unsigned char)data2[n++];
            pFrame->data[2][k++] = (unsigned char)data2[n++];
            pFrame->data[1][k] = (unsigned char)data2[n++];
            pFrame->data[2][k++] = (unsigned char)data2[n++];
            pFrame->data[1][k] = (unsigned char)data2[n++];
            pFrame->data[2][k++] = (unsigned char)data2[n++];
            //  k++;
        }


        LOGW("转换结束，解码第 %d 帧",i);
        pFrame ->pts = i;
        if(pFrame->pict_type == X264_TYPE_AUTO){
            LOGW("自动帧类型");
        }
        if(avcodec_send_frame(pCodecCtx, pFrame)<0){
            fprintf(stderr, "Error sending a frame for encoding\n");
        }
        LOGW("send Frame");
//        while (ret >= 0) {
        ret=avcodec_receive_packet(pCodecCtx, pkt);
            LOGW("接受");
//            }
//        if(avcodec_receive_packet(pCodecCtx, pkt)<0){
//            fprintf(stderr, "Error during encoding\n");
//        }
        LOGW("解码完第%d帧",i);
        fwrite(pkt->data, 1,pkt->size, fp);

        //LOGW("等待flag");
       // while(flag);
        LOGW("pkt的大小是%d",pkt->size);
//        free(chars);
//        chars = (unsigned char *)malloc(pkt->size);
//        memcpy(chars,pkt->data,pkt->size);
//        free(chars);
//        //LOGW("传给写入线程");
         // size  = pkt->size;
//        flag = 1;
        //fwrite(pkt->data, 1, pkt->size, fp);
//        if(i!=0&&i%pCodecCtx->gop_size==pCodecCtx->gop_size-1){
//            avcodec_send_frame(pCodecCtx,NULL);
//        }
        av_packet_unref(pkt);



        av_packet_free(&pkt);


    }
    void destoryencode(){
        LOGW("销毁");
        av_frame_free(&pFrame);
        avcodec_free_context(&pCodecCtx);
        fclose(fp);

    }
};


class hardencode{
private:
    jclass jcls;
    jmethodID jmid;
    jobject hardObject;
    jobject hardObjectemp;
    jmethodID java_method_encode;
    jmethodID java_method_release;
    JNIEnv *Env;
    jclass jclassref;

 public:
    void Hardcodec_init(JNIEnv *env,jobject instance,int width,int height ,int bit_rate ,int frame_rate,int gop){

        jcls  = env->FindClass("com/example/a36970/myapplication/HardEncode");
        // = (jclass)env->NewGlobalRef(jclassref);
        jmid = env->GetMethodID(jcls,"<init>","()V");
        hardObjectemp = env->NewObject(jcls,jmid);
        hardObject = env->NewGlobalRef(hardObjectemp);
//        hardObject = env->NewGlobalRef(hardObjectemp);

        jmethodID java_method_init = env->GetMethodID(jcls, "init", "(IIIII)V");

        java_method_encode = env->GetMethodID(jcls, "encode", "([B[B)I");
        java_method_release = env->GetMethodID(jcls, "release", "()V");
        env->CallVoidMethod(hardObject,java_method_init,width,height,bit_rate,frame_rate,gop);
        //return hardObject;
    }

    int Hardcodec_encode(JNIEnv *env, jbyte* YUVbuffer,jbyte* h264buffer){

//        JavaVM* g_vm;
       // Env = NULL;
//        jbyteArray
        //g_vm->GetEnv((void **)&Env,JNI_VERSION_1_4);
        env->CallIntMethod(hardObject,java_method_encode,YUVbuffer,h264buffer);
//        return env->CallIntMethod(hardObject,java_method_encode,YUVbuffer,h264buffer);
        return 0;
    }

    void Hardcodec_release(){
        Env->CallVoidMethod(hardObject,java_method_release);
    }
};
extern "C"
JNIEXPORT void JNICALL
Java_com_example_a36970_myapplication_MainActivity_encodeinit(JNIEnv *env, jobject instance,
                                                              jstring url_, jint width,
                                                              jint height) {
    const char *url = env->GetStringUTFChars(url_, 0);
    Curl = url;
    Cwidth = width;
    Cheight = height;


    // TODO

    env->ReleaseStringUTFChars(url_, url);
}extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_a36970_myapplication_MainActivity_createEncodeobject(JNIEnv *env,
                                                                      jobject instance) {
    LOGW("初始化");
        jlong result;
//       result  = (jlong)new x264encodec();
//        result  = (jlong)new encodec();
    result = (jlong)new hardencode();
    ((hardencode *)result)->Hardcodec_init(env,instance,Cwidth,Cheight,40000000,30,5);

      LOGW("初始化结束");
        return result;


}extern "C"
JNIEXPORT void JNICALL
Java_com_example_a36970_myapplication_MainActivity_EncodeFrame(JNIEnv *env, jobject instance,
                                                               jlong encodecAddr, jbyteArray data_,
                                                               jbyteArray data2_,
                                                               jint i,jlong time) {
    jbyte *data = env->GetByteArrayElements(data_, NULL);
    jbyte *data2 = env->GetByteArrayElements(data2_, NULL);

//    jbyte *data3 = env->GetByteArrayElements(data3_, NULL);
//    ((x264encodec *)encodecAddr)->encode(data,data2,i);
    Ctime = (int64_t)time;
    LOGW("视频时间%d",(int)(Ctime - Atime));
    //((encodec *)encodecAddr)->encode(data,data2,i);
    int b = ((hardencode *)encodecAddr)->Hardcodec_encode(env,data,data2);
    env->ReleaseByteArrayElements(data_, data, 0);
    env->ReleaseByteArrayElements(data2_, data2, 0);
    //env->ReleaseByteArrayElements(data3_, data3, 0);
}extern "C"
JNIEXPORT void JNICALL
Java_com_example_a36970_myapplication_MainActivity_destroyenclode(JNIEnv *env, jobject instance,
                                                                  jlong encodecAddr) {

//   ((x264encodec *)encodecAddr)->destoryencode();
    ((encodec *)encodecAddr)->destoryencode();
}extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_a36970_myapplication_MainActivity_createAudioencode(JNIEnv *env,
                                                                     jobject instance) {

    // TODO
    jlong result;
    result  = (jlong)new ffAudioencodec();
    LOGW("初始化结束");
    return result;

}extern "C"
JNIEXPORT void JNICALL
Java_com_example_a36970_myapplication_MainActivity_EncodeAudio(JNIEnv *env, jobject instance,
                                                               jlong encodedAddr, jbyteArray data_,
                                                               jint i, jlong time) {
    jbyte *data = env->GetByteArrayElements(data_, NULL);

    // TODO
    Atime = (int64_t)time;
    LOGW("音频时间%lld",Atime);
    LOGW("pts:%d",i);
    ((ffAudioencodec *)encodedAddr)->startencode(data,i);


    env->ReleaseByteArrayElements(data_, data, 0);
}extern "C"
JNIEXPORT void JNICALL
Java_com_example_a36970_myapplication_MainActivity_AudioEncodeInit(JNIEnv *env, jobject instance,
                                                                   jstring url_, jint size) {
    const char *url = env->GetStringUTFChars(url_, 0);

    // TODO
    Aurl = url;

    env->ReleaseStringUTFChars(url_, url);
}extern "C"
JNIEXPORT void JNICALL
Java_com_example_a36970_myapplication_MainActivity_muxing(JNIEnv *env, jobject instance,
                                                          jlong timeA, jlong timeC) {

    // TODO

}