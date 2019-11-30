package com.example.dustbin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.dustbin.tools.Codes;
import com.example.dustbin.tools.bluetooth_Pref;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.iflytek.cloud.util.ResourceUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import androidx.appcompat.app.AppCompatActivity;
import speech.setting.IatSettings;
import speech.util.JsonParser;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private final UUID MY_UUID = UUID
            .fromString("00001101-0000-1000-8000-00805F9B34FB");
    private OutputStream os;
    private ConnectedThread thread;
    boolean commected = true;
    private TextView tv_recive, tvBandBluetooth;
    private bluetooth_Pref blue_sp;
    // 获取到蓝牙适配器
    public BluetoothAdapter mBluetoothAdapter;
    public Button yuyin = null, drytrush, wettrush, recycletrush, hazaroustrush;
    private TextToSpeech texttospeech;
    private static String TAG = "IatDemo";
    // 语音听写对象
    private SpeechRecognizer mIat;
    // 语音听写UI
    private RecognizerDialog mIatDialog;
    // 用HashMap存储听写结果
    private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();
    BluetoothDevice lvDevice = null;
    private Toast mToast;
    BluetoothSocket lvSocket = null;
    private SharedPreferences mSharedPreferences;
    private boolean mTranslateEnable = false;
    private String mEngineType = "cloud";
    private boolean bldrytrush = true, blwettrush = true, blrecycletrush = true, blhazaroustrush = true;
    int ret = 0;// 函数调用返回值

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        yuyin = (Button) findViewById(R.id.yuyin);
        tv_recive = findViewById(R.id.tvrecive);
        tvBandBluetooth = (TextView) findViewById(R.id.tvBandBluetooth);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        blue_sp = bluetooth_Pref.getInstance(this);
        tvBandBluetooth.setText(String.format("已绑定设备：  %s  %s", blue_sp.getBluetoothName(), blue_sp.getBluetoothAd()));

        drytrush = (Button) findViewById(R.id.drytrush);
        wettrush = (Button) findViewById(R.id.wettrush);
        recycletrush = (Button) findViewById(R.id.recycletrush);
        hazaroustrush = (Button) findViewById(R.id.hazaroustrush);
        drytrush.setOnClickListener(this);
        wettrush.setOnClickListener(this);
                recycletrush.setOnClickListener(this);
        hazaroustrush.setOnClickListener(this);
        // 初始化识别无UI识别对象
        // 使用SpeechRecognizer对象，可根据回调消息自定义界面；

        mIat = SpeechRecognizer.createRecognizer(MainActivity.this, mInitListener);

        // 初始化听写Dialog，如果只使用有UI听写功能，无需创建SpeechRecognizer
        // 使用UI听写功能，请根据sdk文件目录下的notice.txt,放置布局文件和图片资源
        mIatDialog = new RecognizerDialog(MainActivity.this, mInitListener);

        mSharedPreferences = getSharedPreferences(IatSettings.PREFER_NAME, Activity.MODE_PRIVATE);
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        mEngineType = SpeechConstant.TYPE_CLOUD;
        // 初始化TextToSpeech对象
        texttospeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {

            @Override
            public void onInit(int status) {
                // 如果装载TTS引擎成功
                if (status == TextToSpeech.SUCCESS) {
                    // 设置使用美式英语朗读
                    int result = texttospeech.setLanguage(Locale.US);
                    // 如果不支持所设置的语言
                    if (result != TextToSpeech.LANG_COUNTRY_AVAILABLE
                            && result != TextToSpeech.LANG_AVAILABLE) {
                        Log.d("ff", "TTS暂时不支持这种语言的朗读！");
                    }
                }
            }
        });
        yuyin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {


                //TODO  语音控制

                mIatResults.clear();
                // 设置参数
                setParam();
                boolean isShowDialog = mSharedPreferences.getBoolean(getString(R.string.pref_key_iat_show), true);
                if (isShowDialog) {
                    // 显示听写对话框
                    mIatDialog.setListener(mRecognizerDialogListener);
                    mIatDialog.show();
                    showTip(getString(R.string.text_begin));
                } else {
                    // 不显示听写对话框
                    ret = mIat.startListening(mRecognizerListener);
                    if (ret != ErrorCode.SUCCESS) {
                        showTip("听写失败,错误码：" + ret);
                    } else {
                        showTip(getString(R.string.text_begin));
                    }
                }


            }


        });

    }


    //右上角三个点
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /***
     * 向指定的蓝牙设备发送数据
     */
    public void send(String pvsMac, byte[] pvsContent) throws IOException {

        // 如果选择设备为空则代表还没有选择设备
        if (lvDevice == null) {
            //通过地址获取到该设备
            lvDevice = mBluetoothAdapter.getRemoteDevice(pvsMac);
        }
        // 这里需要try catch一下，以防异常抛出
        try {
            // 判断客户端接口是否为空
            if (lvSocket == null) {
                // 获取到客户端接口
                lvSocket = lvDevice
                        .createRfcommSocketToServiceRecord(MY_UUID);
                // 向服务端发送连接
                lvSocket.connect();

                // 获取到输出流，向外写数据
                os = lvSocket.getOutputStream();
                if (commected) {
                    commected = false;
                    // 实例接收客户端传过来的数据线程
                    thread = new ConnectedThread(lvSocket);
                    // 线程开始
                    thread.start();
                }
            }
            // 判断是否拿到输出流
            if (os != null) {
                // 需要发送的信息
                // 以utf-8的格式发送出去
                os.write(pvsContent);
            }
            // 吐司一下，告诉用户发送成功
            Toast.makeText(this, "发送信息成功，请查收", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            // 如果发生异常则告诉用户发送失败
            Toast.makeText(this, "发送信息失败", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_1:
                Toast.makeText(this, "连接蓝牙", Toast.LENGTH_SHORT).show();


                break;
            case R.id.menu_2:
                Toast.makeText(this, "绑定蓝牙", Toast.LENGTH_SHORT).show();
                Intent intent1 = new Intent(MainActivity.this, Bluetooth_band.class);
                startActivity(intent1);
                break;
            case R.id.menu_3:
                Toast.makeText(this, "正在开发", Toast.LENGTH_SHORT).show();
                break;
            case R.id.menu_4:
                Toast.makeText(this, "正在开发", Toast.LENGTH_SHORT).show();
                break;
        }
        return true;
    }

    private boolean isNeedRequestPermissions(List<String> permissions) {
        // 定位精确位置
        addPermission(permissions, Manifest.permission.ACCESS_FINE_LOCATION);
        // 存储权限
        addPermission(permissions, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        // 读取手机状态
        addPermission(permissions, Manifest.permission.READ_PHONE_STATE);
        return permissions.size() > 0;
    }

    private void addPermission(List<String> permissionsList, String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 适配android M，检查权限
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isNeedRequestPermissions(permissions)) {
            requestPermissions(permissions.toArray(new String[permissions.size()]), 0);
        }
    }

    @Override
    protected void onResume() {
        tvBandBluetooth.setText(String.format("已绑定设备：  %s  %s", blue_sp.getBluetoothName(), blue_sp.getBluetoothAd()));
        super.onResume();
    }


    public void yuyin(View view) {
        if (blue_sp.getBluetoothAd() == null) {
            Toast.makeText(getApplicationContext(), "未连接到蓝牙设备！请重新连接！", Toast.LENGTH_SHORT).show();
        } else {

            //TODO  语音控制

            mIatResults.clear();
            // 设置参数
            setParam();
            boolean isShowDialog = mSharedPreferences.getBoolean(getString(R.string.pref_key_iat_show), true);
            if (isShowDialog) {
                // 显示听写对话框
                mIatDialog.setListener(mRecognizerDialogListener);
                mIatDialog.show();
                showTip(getString(R.string.text_begin));
            } else {
                // 不显示听写对话框
                ret = mIat.startListening(mRecognizerListener);
                if (ret != ErrorCode.SUCCESS) {
                    showTip("听写失败,错误码：" + ret);
                } else {
                    showTip(getString(R.string.text_begin));
                }
            }


        }

    }
    /////////////////////////////////
    /////////////////////////////////
    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败，错误码：" + code);
            }
        }
    };

    /**
     * 听写监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            if (mTranslateEnable && error.getErrorCode() == 14002) {
                showTip(error.getPlainDescription(true) + "\n请确认是否已开通翻译功能");
            } else {
                showTip(error.getPlainDescription(true));
            }
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            if (mTranslateEnable) {
                printTransResult(results);
            } else {
                String text = JsonParser.parseIatResult(results.getResultString());


            }

            if (isLast) {
                //TODO 最后的结果
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
            Log.d(TAG, "返回音频数据：" + data.length);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }
    };

    /**
     * 听写UI监听器
     */
    //TODO 判断听写内容
    private final RecognizerDialogListener mRecognizerDialogListener = new RecognizerDialogListener() {
        public void onResult(RecognizerResult results, boolean isLast) {
            Log.d(TAG, "recognizer result：" + results.getResultString());

            if (mTranslateEnable) {
                printTransResult(results);
            } else {
                String text = JsonParser.parseIatResult(results.getResultString());
                //TODO
                // 朗读
//                drytrush
//                wettrush
//                recycletrush
//                        hazaroustrush
                int i = 0;
                for (i = 0; i < Codes.DryGarbage.length; i++) {
                    if (text.contains(Codes.DryGarbage[i])) {
                        texttospeech.speak(Codes.DryGarbage[i] + "是干垃圾", TextToSpeech.QUEUE_ADD,
                                null);

                        Log.d("aa", Codes.DryGarbage[i] + "    " + text);
                        drytrush.performClick();
                    }
                }
                for (i = 0; i < Codes.WetGarbage.length; i++) {
                    if (text.contains(Codes.WetGarbage[i])) {
                        texttospeech.speak(Codes.WetGarbage[i] + "是湿垃圾", TextToSpeech.QUEUE_ADD,
                                null);
                        Log.d("aa", Codes.WetGarbage[i] + "    " + text);
                        wettrush.performClick();
                    }
                }
                for (i = 0; i < Codes.RecyGarbage.length; i++) {
                    if (text.contains(Codes.RecyGarbage[i])) {
                        texttospeech.speak(Codes.RecyGarbage[i] + "是可回收垃圾", TextToSpeech.QUEUE_ADD,
                                null);
                        Log.d("aa", Codes.RecyGarbage[i] + "    " + text);
                        recycletrush.performClick();
                    }
                }
                for (i = 0; i < Codes.UnRecyGarbage.length; i++) {
                    if (text.contains(Codes.UnRecyGarbage[i])) {
                        texttospeech.speak(Codes.UnRecyGarbage[i] + "是不可回收垃圾", TextToSpeech.QUEUE_ADD,
                                null);
                        Log.d("aa", Codes.UnRecyGarbage[i] + "    " + text);
                        hazaroustrush.performClick();

                    }
                }


                if (text.contains("关闭") && text.contains("干垃圾")) {
                    texttospeech.speak("关闭干垃圾箱", TextToSpeech.QUEUE_ADD,
                            null);
                    drytrush.performClick();

                }

                if (text.contains("关闭") && text.contains("湿垃圾")) {
                    texttospeech.speak("关闭湿垃圾箱", TextToSpeech.QUEUE_ADD,
                            null);
                    wettrush.performClick();

                }

                if (text.contains("关闭") && text.contains("可回收垃圾")) {
                    texttospeech.speak("关闭可回收垃圾箱", TextToSpeech.QUEUE_ADD,
                            null);
                    recycletrush.performClick();

                }

                if (text.contains("关闭") && text.contains("不可回收垃圾")) {
                    texttospeech.speak("关闭不可回收垃圾箱", TextToSpeech.QUEUE_ADD,
                            null);
                    hazaroustrush.performClick();


                }

                if (text.contains("关闭") && text.contains("所有垃圾箱")) {
                    texttospeech.speak("关闭所有垃圾箱", TextToSpeech.QUEUE_ADD,
                            null);
                    drytrush.performClick();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    wettrush.performClick();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    recycletrush.performClick();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    hazaroustrush.performClick();


                }

            }
        }


        /**
         * 识别回调错误.
         */
        public void onError(SpeechError error) {
            if (mTranslateEnable && error.getErrorCode() == 14002) {
                showTip(error.getPlainDescription(true) + "\n请确认是否已开通翻译功能");
            } else {
                showTip(error.getPlainDescription(true));
            }
        }

    };


    private void showTip(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }

    /**
     * 参数设置
     *
     * @return
     */
    public void setParam() {
        // 清空参数
        mIat.setParameter(SpeechConstant.PARAMS, null);
        String lag = mSharedPreferences.getString("iat_language_preference", "mandarin");
        // 设置引擎
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        // 设置返回结果格式
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");

        this.mTranslateEnable = mSharedPreferences.getBoolean(this.getString(R.string.pref_key_translate), false);
        if (mEngineType.equals(SpeechConstant.TYPE_LOCAL)) {
            // 设置本地识别资源
            mIat.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
        }
        if (mEngineType.equals(SpeechConstant.TYPE_CLOUD) && mTranslateEnable) {
            Log.i(TAG, "translate enable");
            mIat.setParameter(SpeechConstant.ASR_SCH, "1");
            mIat.setParameter(SpeechConstant.ADD_CAP, "translate");
            mIat.setParameter(SpeechConstant.TRS_SRC, "its");
        }
        //设置语言，目前离线听写仅支持中文
        if (lag.equals("en_us")) {
            // 设置语言
            mIat.setParameter(SpeechConstant.LANGUAGE, "en_us");
            mIat.setParameter(SpeechConstant.ACCENT, null);


            if (mEngineType.equals(SpeechConstant.TYPE_CLOUD) && mTranslateEnable) {
                mIat.setParameter(SpeechConstant.ORI_LANG, "en");
                mIat.setParameter(SpeechConstant.TRANS_LANG, "cn");
            }
        } else {
            // 设置语言
            mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
            // 设置语言区域
            mIat.setParameter(SpeechConstant.ACCENT, lag);

            if (mEngineType.equals(SpeechConstant.TYPE_CLOUD) && mTranslateEnable) {
                mIat.setParameter(SpeechConstant.ORI_LANG, "cn");
                mIat.setParameter(SpeechConstant.TRANS_LANG, "en");
            }
        }

        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mIat.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("iat_vadbos_preference", "4000"));

        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mIat.setParameter(SpeechConstant.VAD_EOS, mSharedPreferences.getString("iat_vadeos_preference", "1000"));

        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT, mSharedPreferences.getString("iat_punc_preference", "1"));

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        mIat.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/iat.wav");
    }


    private String getResourcePath() {
        StringBuffer tempBuffer = new StringBuffer();
        //识别通用资源
        tempBuffer.append(ResourceUtil.generateResourcePath(this, ResourceUtil.RESOURCE_TYPE.assets, "asr/common.jet"));
        tempBuffer.append(";");
        tempBuffer.append(ResourceUtil.generateResourcePath(this, ResourceUtil.RESOURCE_TYPE.assets, "asr/sms.jet"));
        //识别8k资源-使用8k的时候请解开注释
        return tempBuffer.toString();
    }

    private void printTransResult(RecognizerResult results) {
        String trans = JsonParser.parseTransResult(results.getResultString(), "dst");
        String oris = JsonParser.parseTransResult(results.getResultString(), "src");

        if (TextUtils.isEmpty(trans) || TextUtils.isEmpty(oris)) {
            showTip("解析结果失败，请确认是否已开通翻译功能。");
        } else {

        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (null != mIat) {
            // 退出时释放连接
            mIat.cancel();
            mIat.destroy();
        }
        thread.cancel();
    }


    // 创建handler，因为我们接收是采用线程来接收的，在线程中无法操作UI，所以需要handler
    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            super.handleMessage(msg);
            // 通过msg传递过来的信息，吐司一下收到的信息
            // Toast.makeText(BuletoothClientActivity.this, (String) msg.obj, Toast.LENGTH_SHORT).show();
            tv_recive.setText((String) msg.obj);
        }
    };

    //按钮
//    public void drytrush(View view) {
//
//    }
//
//    public void wettrush(View view) {
//
//    }
//
//    public void recycletrush(View view) {
//
//    }
//
//    public void hazaroustrush(View view) {
//
//    }

    @Override
    public void onClick(View view) {
        //TODO 按钮点击
        switch (view.getId()) {
            case R.id.drytrush:
                try {
                    if (bldrytrush) {
                        send(blue_sp.getBluetoothAd(), Codes.openDryTrush);
                        drytrush.setBackgroundColor(Color.GRAY);
                        bldrytrush = false;
                    } else {
                        send(blue_sp.getBluetoothAd(), Codes.closeDryTrush);
                        drytrush.setBackgroundColor(Color.parseColor("#4CAF50"));
                        bldrytrush = true;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.wettrush:
                try {
                    if (blwettrush) {
                        send(blue_sp.getBluetoothAd(), Codes.openwettrush);
                        wettrush.setBackgroundColor(Color.GRAY);
                        blwettrush = false;
                    } else {
                        send(blue_sp.getBluetoothAd(), Codes.closewettrush);
                        wettrush.setBackgroundColor(Color.parseColor("#4CAF50"));
                        blwettrush = true;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.recycletrush:
                try {
                    if (blrecycletrush) {
                        send(blue_sp.getBluetoothAd(), Codes.openrecycletrush);
                        recycletrush.setBackgroundColor(Color.GRAY);
                        blrecycletrush = false;
                    } else {
                        send(blue_sp.getBluetoothAd(), Codes.closerecycletrush);
                        recycletrush.setBackgroundColor(Color.parseColor("#4CAF50"));
                        blrecycletrush = true;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.hazaroustrush:
                try {
                    if (blhazaroustrush) {
                        send(blue_sp.getBluetoothAd(), Codes.openhazaroustrush);
                        hazaroustrush.setBackgroundColor(Color.GRAY);
                        blhazaroustrush = false;
                    } else {
                        send(blue_sp.getBluetoothAd(), Codes.closehazaroustrush);
                        hazaroustrush.setBackgroundColor(Color.parseColor("#4CAF50"));
                        blhazaroustrush = true;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
        }

    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d("aa", "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.d("aa", "temp sockets not created" + e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            if (Thread.interrupted()) {
                Log.d("aa", "return");
                return;
            }
            Log.d("aa", "BEGIN mConnectedThread");
            byte[] buffer = new byte[128];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                synchronized (this) {

                    try {
                        while (mmInStream.available() == 0) {
                        }
                        try {
                            Thread.sleep(100);  //当有数据流入时，线程休眠一段时间，默认100ms
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        bytes = mmInStream.read(buffer);  //从字节流中读取数据填充到字节数组，返回读取数据的长度

                        Log.d("aa", "count   " + bytes);
                        // 创建Message类，向handler发送数据
                        Message msg = new Message();
                        // 发送一个String的数据，让他向上转型为obj类型
                        msg.obj = new String(buffer, 0, bytes, "utf-8");
                        // 发送数据
                        Log.d("aa", "data   " + msg.obj);
                        handler.sendMessage(msg);
                    } catch (IOException e) {
                        Log.e("aa", "disconnected", e);

                        break;
                    }
                }


            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
            } catch (IOException e) {
                Log.e("aa", "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("aa", "close() of connect socket failed", e);
            }
        }
    }
}