package com.kooritea.fcmfix.xposed;

import android.content.Context;

import com.kooritea.fcmfix.util.XposedUtils;

import java.lang.reflect.Field;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class PowerkeeperFix extends XposedModule {
    public PowerkeeperFix(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        super(loadPackageParam);
        this.startHook();
    }

    protected void startHook(){
        // MilletConfig.isGlobal - may not exist on newer HyperOS
        try {
            Class<?> MilletConfig = XposedHelpers.findClassIfExists("com.miui.powerkeeper.millet.MilletConfig", loadPackageParam.classLoader);
            if (MilletConfig != null) {
                XposedHelpers.setStaticBooleanField(MilletConfig, "isGlobal", true);
                printLog("Set com.miui.powerkeeper.millet.MilletConfig.isGlobal to true");
            } else {
                printLog("MilletConfig not found, skip isGlobal patch");
            }
        } catch (Throwable e) {
            printLog("hook error MilletConfig.isGlobal: " + e.getMessage());
        }

        // SimpleSettings.Misc.getBoolean -> gms_control
        try {
            Class<?> Misc = XposedHelpers.findClassIfExists("com.miui.powerkeeper.provider.SimpleSettings.Misc", loadPackageParam.classLoader);
            if (Misc != null) {
                printLog("[fcmfix] start hook com.miui.powerkeeper.provider.SimpleSettings.Misc.getBoolean");
                XposedUtils.findAndHookMethod(Misc, "getBoolean", 3, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        if("gms_control".equals((String) methodHookParam.args[1])) {
                            printLog("Success: PowerKeeper GMS Limitation disabled.", true);
                            methodHookParam.setResult(false);
                        }
                    }
                });
            } else {
                printLog("SimpleSettings.Misc not found, skip gms_control hook");
            }
        } catch (Throwable e) {
            printLog("hook error SimpleSettings.Misc.getBoolean: " + e.getMessage());
        }

        // GmsObserver.isGmsControlEnabled - new HyperOS fallback
        try {
            Class<?> GmsObserver = XposedHelpers.findClassIfExists("com.miui.powerkeeper.utils.GmsObserver", loadPackageParam.classLoader);
            if (GmsObserver != null) {
                XposedHelpers.findAndHookMethod(GmsObserver, "isGmsControlEnabled", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        param.setResult(false);
                        printLog("Success: GmsObserver.isGmsControlEnabled -> false", true);
                    }
                });
                printLog("Hooked GmsObserver.isGmsControlEnabled");
            }
        } catch (Throwable e) {
            printLog("hook error GmsObserver.isGmsControlEnabled: " + e.getMessage());
        }

        // MilletPolicy constructor - may not exist on newer HyperOS
        try {
            Class<?> MilletPolicy = XposedHelpers.findClassIfExists("com.miui.powerkeeper.millet.MilletPolicy", loadPackageParam.classLoader);
            if (MilletPolicy != null) {
                XC_MethodHook methodHook = new XC_MethodHook() {
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
                        super.afterHookedMethod(methodHookParam);
                        boolean mSystemBlackList = false;
                        boolean whiteApps = false;
                        boolean mDataWhiteList = false;

                        for (Field field : methodHookParam.thisObject.getClass().getDeclaredFields()) {
                            if (field.getName().equals("mSystemBlackList")) {
                                mSystemBlackList = true;
                            } else if (field.getName().equals("whiteApps")) {
                                whiteApps = true;
                            } else if (field.getName().equals("mDataWhiteList")) {
                                mDataWhiteList = true;
                            }
                        }

                        if (mSystemBlackList) {
                            List blackList = (List) XposedHelpers.getObjectField(methodHookParam.thisObject, "mSystemBlackList");
                            blackList.remove("com.google.android.gms");
                            XposedHelpers.setObjectField(methodHookParam.thisObject, "mSystemBlackList", blackList);
                            printLog("Success: MilletPolicy mSystemBlackList.");
                        } else {
                            printLog("Error: MilletPolicy. Field not found: mSystemBlackList");
                        }
                        if (whiteApps) {
                            List whiteAppList = (List) XposedHelpers.getObjectField(methodHookParam.thisObject, "whiteApps");
                            whiteAppList.remove("com.google.android.gms");
                            whiteAppList.remove("com.google.android.ext.services");
                            XposedHelpers.setObjectField(methodHookParam.thisObject, "whiteApps", whiteAppList);
                            printLog("Success: MilletPolicy whiteApps.");
                        } else {
                            printLog("Error: MilletPolicy. Field not found: whiteApps");
                        }
                        if (mDataWhiteList) {
                            List dataWhiteList = (List) XposedHelpers.getObjectField(methodHookParam.thisObject, "mDataWhiteList");
                            dataWhiteList.add("com.google.android.gms");
                            XposedHelpers.setObjectField(methodHookParam.thisObject, "mDataWhiteList", dataWhiteList);
                            printLog("Success: MilletPolicy mDataWhiteList.");
                        }
                    }
                };
                printLog("[fcmfix] start hook com.miui.powerkeeper.millet.MilletPolicy constructor");
                XposedHelpers.findAndHookConstructor(MilletPolicy, new Object[] {Context.class, methodHook});
            } else {
                printLog("MilletPolicy not found, skip blacklist/whitelist patch");
            }
        } catch (Throwable e) {
            printLog("hook error MilletPolicy: " + e.getMessage());
        }
    }
}
