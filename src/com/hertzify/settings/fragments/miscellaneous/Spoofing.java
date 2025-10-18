/*
 * Copyright (C) 2019-2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hertzify.settings.fragments.miscellaneous;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.hertzify.KeyProviderManager;
import com.android.internal.util.hertzify.SystemRestartUtils;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.search.SearchIndexable;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.hertzify.settings.preferences.KeyboxDataPreference;
import com.hertzify.settings.preferences.SystemPropertySwitchPreference;
import com.hertzify.settings.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@SearchIndexable
public class Spoofing extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "Spoofing";

    private static final String KEY_PIF_JSON_FILE_PREFERENCE = "pif_json_file_preference";
    private static final String KEY_GAME_PROPS_JSON_FILE_PREFERENCE = "game_props_json_file_preference";
    private static final String KEY_SYSTEM_WIDE_CATEGORY = "spoofing_system_wide_category";
    private static final String KEY_UPDATE_JSON_BUTTON = "update_pif_json";
    private static final String SYS_GMS_SPOOF = "persist.sys.pixelprops.gms";
    private static final String SYS_GMS_CERT_SPOOF = "persist.sys.pixelprops.gmscertchain";
    private static final String SYS_GOOGLE_SPOOF = "persist.sys.pixelprops";
    private static final String SYS_GPHOTOS_SPOOF = "persist.sys.pixelprops.gphotos";
    private static final String SYS_QSB_SPOOF = "persist.sys.pixelprops.qsb";
    private static final String SYS_SNAP_SPOOF = "persist.sys.pixelprops.snap";
    private static final String SYS_TENSOR_SPOOF = "persist.sys.features.tensor";
    private static final String SYS_GAMEPROP_ENABLED = "persist.sys.gameprops.enabled";
    private static final String KEYBOX_DATA_KEY = "keybox_data_setting";

    private ActivityResultLauncher<Intent> mKeyboxFilePickerLauncher;
    private KeyboxDataPreference mKeyboxDataPreference;
    private Preference mPifJsonFilePreference;
    private Preference mGamePropsJsonFilePreference;
    private Preference mUpdateJsonButton;
    private PreferenceCategory mSystemWideCategory;
    private SystemPropertySwitchPreference mDisableForceIntegrity;
    private SystemPropertySwitchPreference mGmsSpoof;
    private SystemPropertySwitchPreference mGoogleSpoof;
    private SystemPropertySwitchPreference mGphotosSpoof;
    private SystemPropertySwitchPreference mQsbSpoof;
    private SystemPropertySwitchPreference mSnapSpoof;
    private SystemPropertySwitchPreference mTensorSpoof;
    private SystemPropertySwitchPreference mGamePropsEnabled;

    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        addPreferencesFromResource(R.xml.spoofing);

        final Context context = getContext();
        final ContentResolver resolver = context.getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();
        final Resources resources = context.getResources();

        mSystemWideCategory = (PreferenceCategory) findPreference(KEY_SYSTEM_WIDE_CATEGORY);
        mGphotosSpoof = (SystemPropertySwitchPreference) findPreference(SYS_GPHOTOS_SPOOF);
        mGmsSpoof = (SystemPropertySwitchPreference) findPreference(SYS_GMS_SPOOF);
        mGoogleSpoof = (SystemPropertySwitchPreference) findPreference(SYS_GOOGLE_SPOOF);
        mPifJsonFilePreference = findPreference(KEY_PIF_JSON_FILE_PREFERENCE);
        mGamePropsJsonFilePreference = findPreference(KEY_GAME_PROPS_JSON_FILE_PREFERENCE);
        mQsbSpoof = (SystemPropertySwitchPreference) findPreference(SYS_QSB_SPOOF);
        mSnapSpoof = (SystemPropertySwitchPreference) findPreference(SYS_SNAP_SPOOF);
        mTensorSpoof = (SystemPropertySwitchPreference) findPreference(SYS_TENSOR_SPOOF);
        mUpdateJsonButton = findPreference(KEY_UPDATE_JSON_BUTTON);
        mGamePropsEnabled = (SystemPropertySwitchPreference) findPreference(SYS_GAMEPROP_ENABLED);

        String model = SystemProperties.get("ro.product.model");
        boolean isTensorDevice = model.matches("Pixel (6|7|8|9|10)[a-zA-Z ]*");
        boolean isPixelGmsEnabled = SystemProperties.getBoolean(SYS_GMS_SPOOF, true); // Default to Pixel GMS

        if (Utils.isCurrentlySupportedPixel()) {
            mGoogleSpoof.setDefaultValue(false);
            if (isMainlineTensorModel(model)) {
                mSystemWideCategory.removePreference(mGoogleSpoof);
            }
        }

        if (isTensorDevice) {
            mSystemWideCategory.removePreference(mTensorSpoof);
        }

        mGmsSpoof.setOnPreferenceChangeListener(this);
        mGoogleSpoof.setOnPreferenceChangeListener(this);
        mGphotosSpoof.setOnPreferenceChangeListener(this);
        mQsbSpoof.setOnPreferenceChangeListener(this);
        mSnapSpoof.setOnPreferenceChangeListener(this);
        mTensorSpoof.setOnPreferenceChangeListener(this);
        if (mGamePropsEnabled != null) {
            mGamePropsEnabled.setOnPreferenceChangeListener(this);
        }

        mDisableForceIntegrity = findPreference(SYS_GMS_CERT_SPOOF);
        if (mDisableForceIntegrity != null) {
            mDisableForceIntegrity.setEnabled(KeyProviderManager.isKeyboxAvailable());
        }

        mKeyboxFilePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    Preference pref = findPreference(KEYBOX_DATA_KEY);
                    if (pref instanceof KeyboxDataPreference) {
                        ((KeyboxDataPreference) pref).handleFileSelected(uri);
                    }
                    if (mDisableForceIntegrity != null) {
                        mDisableForceIntegrity.setEnabled(KeyProviderManager.isKeyboxAvailable());
                    }
                }
            }
        );

        mPifJsonFilePreference.setOnPreferenceClickListener(preference -> {
            openFileSelector(10001);
            return true;
        });

        if (mGamePropsJsonFilePreference != null) {
            mGamePropsJsonFilePreference.setOnPreferenceClickListener(preference -> {
                openFileSelector(10002);
                return true;
            });
        }

        mUpdateJsonButton.setOnPreferenceClickListener(preference -> {
            updatePropertiesFromUrl("https://raw.githubusercontent.com/HertzifyOS/android_vendor_spoof/refs/heads/16/gms_certified_props.json");
            return true;
        });

        Preference showPropertiesPref = findPreference("show_pif_properties");
        if (showPropertiesPref != null) {
            showPropertiesPref.setOnPreferenceClickListener(preference -> {
                showPropertiesDialog();
                return true;
            });
        }
    }

    private boolean isMainlineTensorModel(String model) {
        return model.matches("Pixel (8|9|10)[a-zA-Z ]*");
    }

    private void openFileSelector(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        startActivityForResult(intent, requestCode);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mKeyboxDataPreference = findPreference(KEYBOX_DATA_KEY);
        if (mKeyboxDataPreference != null) {
            mKeyboxDataPreference.setFilePickerLauncher(mKeyboxFilePickerLauncher);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                if (requestCode == 10001) {
                    loadPifJson(uri);
                } else if (requestCode == 10002) {
                    loadGameSpoofingJson(uri);
                }
            }
        }
    }

    private void showPropertiesDialog() {
        StringBuilder properties = new StringBuilder();
        try {
            JSONObject jsonObject = new JSONObject();
            String[] keys = {
                "persist.sys.pihooks_ID",
                "persist.sys.pihooks_BRAND",
                "persist.sys.pihooks_DEVICE",
                "persist.sys.pihooks_FINGERPRINT",
                "persist.sys.pihooks_MANUFACTURER",
                "persist.sys.pihooks_MODEL",
                "persist.sys.pihooks_PRODUCT",
                "persist.sys.pihooks_SECURITY_PATCH",
                "persist.sys.pihooks_DEVICE_INITIAL_SDK_INT",
                "persist.sys.pihooks_TYPE",
                "persist.sys.pihooks_TAG",
                "persist.sys.pihooks_RELEASE",
                "persist.sys.pihooks_SDK_INT",
                "persist.sys.pihooks_DEBUG"
            };
            for (String key : keys) {
                String value = SystemProperties.get(key, null);
                if (value != null) {
                    String buildKey = key.replace("persist.sys.pihooks_", "");
                    jsonObject.put(buildKey, value);
                }
            }
            properties.append(jsonObject.toString(4));
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON from properties", e);
            properties.append(getString(R.string.error_loading_properties));
        }
        new AlertDialog.Builder(getContext())
            .setTitle(R.string.show_pif_properties_title)
            .setMessage(properties.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    /**
     * Kill packages that need to be restarted to pick up new PIF properties
     */
    private void killGMSPackages() {
        try {
            ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            String[] packages = {
                "com.google.android.apps.photos",
                "com.google.android.gms",
                "com.google.android.googlequicksearchbox",
                "com.android.vending",
                "com.snapchat.android"
            };
            for (String pkg : packages) {
                am.getClass()
                  .getMethod("forceStopPackage", String.class)
                  .invoke(am, pkg);
                Log.i(TAG, pkg + " process killed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to kill packages", e);
        }
    }

    /**
     * Kill specific game packages to apply new game properties
     */
    private int killGamePackages(Set<String> gamePackages) {
        int killedCount = 0;
        try {
            ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            PackageManager pm = getContext().getPackageManager();

            for (String packageName : gamePackages) {
                try {
                    pm.getPackageInfo(packageName, 0); // check if installed
                    am.getClass()
                    .getMethod("forceStopPackage", String.class)
                    .invoke(am, packageName);
                    Log.i(TAG, "Game package killed: " + packageName);
                    killedCount++;
                } catch (PackageManager.NameNotFoundException e) {
                    Log.d(TAG, "Game package not installed: " + packageName);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to kill game package: " + packageName, e);
                }
            }
            Log.i(TAG, "Successfully killed " + killedCount + " game packages out of " + gamePackages.size());
        } catch (Exception e) {
            Log.e(TAG, "Failed to kill game packages", e);
        }
        return killedCount;
    }

    private void updatePropertiesFromUrl(String urlString) {
        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                try (InputStream inputStream = urlConnection.getInputStream()) {
                    String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    Log.d(TAG, "Downloaded JSON data: " + json);
                    JSONObject jsonObject = new JSONObject(json);
                    String spoofedModel = jsonObject.optString("MODEL", "Unknown model");
                    for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                        String key = it.next();
                        String value = jsonObject.getString(key);
                        Log.d(TAG, "Setting property: persist.sys.pihooks_" + key + " = " + value);
                        SystemProperties.set("persist.sys.pihooks_" + key, value);
                    }
                    mHandler.post(() -> {
                        String toastMessage = getString(R.string.toast_spoofing_success, spoofedModel);
                        Toast.makeText(getContext(), toastMessage, Toast.LENGTH_LONG).show();
                        killGMSPackages();
                    });

                } finally {
                    urlConnection.disconnect();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error downloading JSON or setting properties", e);
                mHandler.post(() -> {
                    Toast.makeText(getContext(), R.string.toast_spoofing_failure, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void loadPifJson(Uri uri) {
        Log.d(TAG, "Loading PIF JSON from URI: " + uri.toString());
        try (InputStream inputStream = getActivity().getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                Log.d(TAG, "PIF JSON data: " + json);
                JSONObject jsonObject = new JSONObject(json);
                for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                    String key = it.next();
                    String value = jsonObject.getString(key);
                    Log.d(TAG, "Setting PIF property: persist.sys.pihooks_" + key + " = " + value);
                    SystemProperties.set("persist.sys.pihooks_" + key, value);
                }
                killGMSPackages();
                Toast.makeText(getContext(), "PIF JSON loaded and packages refreshed", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading PIF JSON or setting properties", e);
            Toast.makeText(getContext(), "Error loading PIF JSON", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadGameSpoofingJson(Uri uri) {
        Log.d(TAG, "Loading Game Props JSON from URI: " + uri.toString());
        try (InputStream inputStream = getActivity().getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                Log.d(TAG, "Game Props JSON data: " + json);
                JSONObject jsonObject = new JSONObject(json);

                Set<String> packagesToKill = new HashSet<>();

                for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                    String key = it.next();
                    if (key.startsWith("PACKAGES_") && !key.endsWith("_DEVICE")) {
                        String deviceKey = key + "_DEVICE";
                        if (jsonObject.has(deviceKey)) {
                            JSONObject deviceProps = jsonObject.getJSONObject(deviceKey);
                            JSONArray packages = jsonObject.getJSONArray(key);
                            for (int i = 0; i < packages.length(); i++) {
                                String packageName = packages.getString(i);
                                Log.d(TAG, "Spoofing package: " + packageName);
                                setGameProps(packageName, deviceProps);
                                packagesToKill.add(packageName);
                            }
                        }
                    }
                }

                int killed = 0;
                if (!packagesToKill.isEmpty()) {
                    killed = killGamePackages(packagesToKill);
                }

                Toast.makeText(getContext(),
                    "Game Json loaded, Killed " + killed + " out of " + packagesToKill.size() + " game apps",
                    Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading Game Props JSON or setting properties", e);
        }
        mHandler.postDelayed(() -> {
            SystemRestartUtils.showSystemRestartDialog(getContext());
        }, 1250);
    }

    private void setGameProps(String packageName, JSONObject deviceProps) {
        try {
            for (Iterator<String> it = deviceProps.keys(); it.hasNext(); ) {
                String key = it.next();
                String value = deviceProps.getString(key);
                String systemPropertyKey = "persist.sys.gameprops." + packageName + "." + key;
                SystemProperties.set(systemPropertyKey, value);
                Log.d(TAG, "Set system property: " + systemPropertyKey + " = " + value);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing device properties", e);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final Context context = getContext();
        final ContentResolver resolver = context.getContentResolver();
        if (preference == mGmsSpoof
            || preference == mGphotosSpoof
            || preference == mQsbSpoof
            || preference == mSnapSpoof) {
            killGMSPackages();
            return true;
        }
        if (preference == mGoogleSpoof) {
            SystemRestartUtils.showSystemRestartDialog(getContext());
            return true;
        }
        if (preference == mTensorSpoof) {
            boolean enabled = (Boolean) newValue;
            SystemProperties.set(SYS_TENSOR_SPOOF, enabled ? "true" : "false");
            SystemRestartUtils.showSystemRestartDialog(getContext());
            return true;
        }
        if (preference == mGamePropsEnabled) {
            SystemRestartUtils.showSystemRestartDialog(getContext());
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.HERTZIFY;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider(R.xml.spoofing) {

            @Override
            public List<String> getNonIndexableKeys(Context context) {
                List<String> keys = super.getNonIndexableKeys(context);
                final Resources resources = context.getResources();

                return keys;
            }
        };
}
