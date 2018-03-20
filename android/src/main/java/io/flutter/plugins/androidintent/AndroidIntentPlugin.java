// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.androidintent;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Map;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.net.URI;

/** AndroidIntentPlugin */
@SuppressWarnings("unchecked")
public class AndroidIntentPlugin implements MethodCallHandler, ActivityResultListener {
  private static final String TAG = AndroidIntentPlugin.class.getCanonicalName();
  private final Registrar mRegistrar;

  /** Plugin registration. */
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel =
        new MethodChannel(registrar.messenger(), "plugins.flutter.io/android_intent");
    final AndroidIntentPlugin instance = new AndroidIntentPlugin(registrar);
    registrar.addActivityResultListener(instance);
    channel.setMethodCallHandler(instance);
  }

  private AndroidIntentPlugin(Registrar registrar) {
    this.mRegistrar = registrar;
  }

  private String convertAction(String action) {
    switch (action) {
      case "action_get_content":
        return Intent.ACTION_OPEN_DOCUMENT;
        //return Intent.ACTION_GET_CONTENT;
      default:
        return action;
    }
  }

  private Bundle convertArguments(Map<String, ?> arguments) {
    Bundle bundle = new Bundle();
    for (String key : arguments.keySet()) {
      Object value = arguments.get(key);
      if (value instanceof Integer) {
        bundle.putInt(key, (Integer) value);
      } else if (value instanceof String) {
        bundle.putString(key, (String) value);
      } else if (value instanceof Boolean) {
        bundle.putBoolean(key, (Boolean) value);
      } else if (value instanceof Double) {
        bundle.putDouble(key, (Double) value);
      } else if (value instanceof Long) {
        bundle.putLong(key, (Long) value);
      } else if (value instanceof byte[]) {
        bundle.putByteArray(key, (byte[]) value);
      } else if (value instanceof int[]) {
        bundle.putIntArray(key, (int[]) value);
      } else if (value instanceof long[]) {
        bundle.putLongArray(key, (long[]) value);
      } else if (value instanceof double[]) {
        bundle.putDoubleArray(key, (double[]) value);
      } else if (isTypedArrayList(value, Integer.class)) {
        bundle.putIntegerArrayList(key, (ArrayList<Integer>) value);
      } else if (isTypedArrayList(value, String.class)) {
        bundle.putStringArrayList(key, (ArrayList<String>) value);
      } else if (isStringKeyedMap(value)) {
        bundle.putBundle(key, convertArguments((Map<String, ?>) value));
      } else {
        throw new UnsupportedOperationException("Unsupported type " + value);
      }
    }
    return bundle;
  }

  private boolean isTypedArrayList(Object value, Class<?> type) {
    if (!(value instanceof ArrayList)) {
      return false;
    }
    ArrayList list = (ArrayList) value;
    for (Object o : list) {
      if (!(o == null || type.isInstance(o))) {
        return false;
      }
    }
    return true;
  }

  private boolean isStringKeyedMap(Object value) {
    if (!(value instanceof Map)) {
      return false;
    }
    Map map = (Map) value;
    for (Object key : map.keySet()) {
      if (!(key == null || key instanceof String)) {
        return false;
      }
    }
    return true;
  }

  private Context getActiveContext() {
    return (mRegistrar.activity() != null) ? mRegistrar.activity() : mRegistrar.context();
  }

  final int ACTIVITY_CHOOSE_FILE = 1;
  private Result pendingResult;

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    Context context = getActiveContext();
    String action = convertAction((String) call.argument("action"));

    // Build intent
    Intent intent = new Intent(action);
    /*if (mRegistrar.activity() == null) {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }*/
    if (call.argument("type") != null) {
      intent.setType("*/*");
      //intent.setType((String) call.argument("type"));
    }

    //intent.addCategory(Intent.CATEGORY_OPENABLE);

    Log.i(TAG, "Sending intent " + intent);

    //Intent intent2 = Intent.createChooser(intent, "Choose a file");
    //startActivityForResult(intent, PICKFILE_RESULT_CODE);
    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION );
    mRegistrar.activity().startActivityForResult(intent, 1);
    //((Activity)context).startActivityForResult(intent2, ACTIVITY_CHOOSE_FILE);
    pendingResult = result;
    Log.i(TAG, "sending start activity");

    //result.success(null);
  }

  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.i(TAG, "Activity Called");
    switch(requestCode){
      case 1:
        if(resultCode == Activity.RESULT_OK){
          Uri uri = data.getData();

          String fileOriginalName = getOriginalFileName(uri);
          Context context = getActiveContext();

          //File sourceFile = new File(uri.getPath()+"/BISTRO-MENU-NOVEMBER-2016.pdf");
          ContextWrapper c = new ContextWrapper(context);
          String fileDir = null;
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            fileDir = c.getDataDir().getPath();
            Log.i(TAG, "#path is "+fileDir);
          }

          String path = context.getCacheDir().getPath();
          Log.i(TAG, "#**#path2 is "+path);
          File DestFile = new File(path+"/"+fileOriginalName);

          //File DestFile = new File("/data/user/0/com.blocks31.fluttermobile/app_flutter/sample.pdf");
          Log.i(TAG, "Attempting to copy!!! ");
          try {
            copy(uri, DestFile);
            Log.i(TAG, "File Copied!!! ");
          } catch (IOException e) {
            e.printStackTrace();
          }

          pendingResult.success(DestFile.getPath());
          Log.i(TAG, "Path is "+DestFile.getPath());
        }
        break;
    }
    return true;
  }

  public void copy(Uri uri, File dst) throws IOException {
    Log.i(TAG, "1 ");
    InputStream in = mRegistrar.activity().getContentResolver().openInputStream(uri);
    Log.i(TAG, "2 ");
    try {
      OutputStream out = new FileOutputStream(dst);
      Log.i(TAG, "3 ");
      try {
        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
          out.write(buf, 0, len);
        }
      } finally {
        out.close();
      }
    } finally {
      in.close();
    }
  }

  public String getOriginalFileName(Uri uri) {

    // The query, since it only applies to a single document, will only return
    // one row. There's no need to filter, sort, or select fields, since we want
    // all fields for one document.
    Cursor cursor = mRegistrar.activity().getContentResolver()
            .query(uri, null, null, null, null, null);

    String displayName = "";
    try {
      // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
      // "if there's anything to look at, look at it" conditionals.
      if (cursor != null && cursor.moveToFirst()) {

        // Note it's called "Display Name".  This is
        // provider-specific, and might not necessarily be the file name.
        cursor.moveToFirst();
        String testPath = cursor.getString(0);
        Log.i(TAG, "****** Cursor Path is "+testPath);

        displayName = cursor.getString(
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        Log.i(TAG, "Display Name: " + displayName);

        int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
        // If the size is unknown, the value stored is null.  But since an
        // int can't be null in Java, the behavior is implementation-specific,
        // which is just a fancy term for "unpredictable".  So as
        // a rule, check if it's null before assigning to an int.  This will
        // happen often:  The storage API allows for remote files, whose
        // size might not be locally known.
        String size = null;
        if (!cursor.isNull(sizeIndex)) {
          // Technically the column stores an int, but cursor.getString()
          // will do the conversion automatically.
          size = cursor.getString(sizeIndex);
        } else {
          size = "Unknown";
        }
        Log.i(TAG, "Size: " + size);
      }
    } finally {
      cursor.close();
      return displayName;
    }
  }


  /*@Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.i(TAG, "Activity Called");
    // TODO Auto-generated method stub
    *//*switch(requestCode){
      case 1:
        if(resultCode==RESULT_OK){
          //String FilePath = data.getData().getPath();
          Log.i(TAG, "Path is ");
        }
        break;

    }*//*
  }*/
}
