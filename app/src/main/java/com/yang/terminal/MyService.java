package com.yang.terminal;


import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import com.yang.amapmoudle.AmapLocationUtil;
import com.yang.amapmoudle.Utils;

import javax.crypto.Cipher;

public class MyService extends Service {
    public static final int NOTIFICATION_START_FLAG = 2;
    public static final String ACTION_START = "action_start";
    public static final String ACTION_STOP = "action_stop";

    ContentResolver resolver;
    private DynamicReceiver receiverSMS = new DynamicReceiver();
    private AmapLocationUtil amapLocationUtil;

    public MyService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            //关闭服务
            stopForeground(true);
            stopSelf();
        } else {
            amapLocationUtil = AmapLocationUtil.getInstance(this);
            initView();
            startForeground(NOTIFICATION_START_FLAG,
                    amapLocationUtil.buildNotification(new Intent(ACTION_STOP).setClass(this, MyService.class)));
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void initView() {
        IntentFilter filterSMS = new IntentFilter(
                "android.provider.Telephony.SMS_RECEIVED");
        registerReceiver(receiverSMS, filterSMS);
        //        getContentResolver().registerContentObserver(
        //                Uri.parse("content://sms"), true, Observer);

    }


    /**
     * 判断GPS是否开启，GPS或者AGPS开启一个就认为是开启的
     *
     * @param context
     * @return true 表示开启
     */
    public static boolean isOPen(Context context) {
        LocationManager locationManager = (LocationManager) context
                .getSystemService(Context.LOCATION_SERVICE);
        // 通过GPS卫星定位，定位级别可以精确到街（通过24颗卫星定位，在室外和空旷的地方定位准确、速度快）
        boolean gps = locationManager
                .isProviderEnabled(LocationManager.GPS_PROVIDER);
        // 通过WLAN或移动网络(3G/2G)确定的位置（也称作AGPS，辅助GPS定位。主要用于在室内或遮盖物（建筑群或茂密的深林等）密集的地方定位）
        boolean network = locationManager
                .isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (gps || network) {
            return true;
        }

        return false;
    }

    // 对收到的短信内容进行提取
    public class DynamicReceiver extends BroadcastReceiver {
        public static final String SMS_ACTION = "android.provider.Telephony.SMS_RECEIVED";

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (SMS_ACTION.equals(action)) {
                Bundle bundle = intent.getExtras();
                Object messages[] = (Object[]) bundle.get("pdus");
                final SmsMessage smsMessage[] = new SmsMessage[messages.length];
                Log.d(Utils.LOG_TAG, "收到短信内容8888888888");
                for (int n = 0; n < messages.length; n++) {
                    smsMessage[n] = SmsMessage
                            .createFromPdu((byte[]) messages[n]);
                    String body = smsMessage[n].getMessageBody();
                    try {
                        body = AESTool.des(body, Cipher.DECRYPT_MODE);
                        if (body.startsWith("#DW")) {
                            final String num = smsMessage[n].getOriginatingAddress();
                            //收到短信指令后  开始定位 获取经纬度坐标  然后以短信的形式回发回去
                            amapLocationUtil.startLocation(new AmapLocationUtil.OnLocSuccess() {
                                @Override
                                public void locSuccess(double lat, double lng) {
                                    SmsManager smsManager = SmsManager.getDefault();
                                    String msg = "#RDW," + lat + "," + lng;
                                    try {
                                        msg = AESTool.des(msg,Cipher.ENCRYPT_MODE);
                                        smsManager.sendTextMessage(num, null, msg, null,
                                                null);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                }
                            });

                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }

            }
        }

    }
    /*

     * Delete all SMS one by one

     */

    public void deleteSMS() {
        try {
            ContentResolver CR = getContentResolver();

            // Query SMS

            Uri uriSms = Uri.parse("content://sms/sent");

            Cursor c = CR.query(uriSms,

                    new String[]{"_id", "thread_id"}, null, null, null);

            if (null != c && c.moveToFirst()) {
                do {
                    // Delete SMS

                    long threadId = c.getLong(1);

                    CR.delete(Uri.parse("content://sms/conversations/" + threadId),

                            null, null);

                    Log.d("deleteSMS", "threadId:: " + threadId);

                } while (c.moveToNext());

            }

        } catch (Exception e) {
            // TODO: handle exception

            Log.d("deleteSMS", "Exception:: " + e);

        }

    }

    // 删除收到的#DW
    ContentObserver Observer = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            resolver = getContentResolver();
            // 删除收件箱的"DW"
            Cursor cursor = resolver.query(Uri.parse("content://sms/inbox"),
                    new String[]{"_id", "address", "body"}, null, null,
                    "_id desc");
            if (cursor.getCount() > 0 && cursor.moveToFirst()) {
                String saddress = cursor.getString(1);
                String sbody = cursor.getString(2);
                if (sbody.startsWith("#DW")) {
                    deleteSMS();
                }
            }
            cursor.close();
            //            // 删除发件箱的"RDW"
            //            Cursor cursor2 = resolver.query(Uri.parse("content://sms/sent"),
            //                    new String[]{"_id", "address", "body"}, null, null,
            //                    "_id desc");
            //            if (cursor2.getCount() > 0 && cursor2.moveToFirst()) {
            //                String saddress2 = cursor2.getString(1);
            //                String sbody2 = cursor2.getString(2);
            //                if (sbody2.startsWith("#RDW")) {
            //                    long id2 = cursor2.getLong(0);
            //                    resolver.delete(Telephony.Sms.CONTENT_URI, "_id=" + id2, null);
            //                }
            //            }
            //            cursor2.close();
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}