package com.bytedance.tools.codelocator.utils;

import com.bytedance.tools.codelocator.device.DeviceManager;
import com.bytedance.tools.codelocator.device.action.AdbAction;
import com.bytedance.tools.codelocator.device.action.AdbCommand;
import com.bytedance.tools.codelocator.device.action.DeleteFileAction;
import com.bytedance.tools.codelocator.device.action.PullFileAction;
import com.bytedance.tools.codelocator.listener.CodeLocatorApplicationInitializedListener;
import com.bytedance.tools.codelocator.model.*;
import com.bytedance.tools.codelocator.parser.DumpInfoParser;
import com.bytedance.tools.codelocator.parser.JumpParser;
import com.bytedance.tools.codelocator.parser.UixInfoParser;
import com.bytedance.tools.codelocator.response.BaseResponse;
import com.bytedance.tools.codelocator.response.StringResponse;
import com.intellij.openapi.project.Project;

import java.awt.*;
import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class DataUtils {

    private static volatile String sCurrentProjectName = "unknown";

    private static volatile String sCurrentApkName = "unknown";

    private static volatile String sCurrentSDKVersion = "unknown";

    private static volatile String sUserName = null;

    private static volatile String sUid = null;

    public static String getUserName() {
        if (sUserName != null) {
            return sUserName;
        }
        synchronized (DataUtils.class) {
            if (sUserName == null) {
                sUserName = OSHelper.getInstance().getUserName();
            }
        }

        return sUserName;
    }

    public static String getUid() {
        if (sUid != null) {
            return sUid;
        }
        synchronized (DataUtils.class) {
            if (sUid == null) {
                sUid = OSHelper.getInstance().getUid();
            }
        }
        return sUid;
    }

    public static String getCurrentProjectName() {
        return sCurrentProjectName;
    }

    public static void setCurrentProjectName(String currentProjectName) {
        if (currentProjectName == null || currentProjectName.isEmpty()) {
            currentProjectName = "unknown";
        }
        DataUtils.sCurrentProjectName = currentProjectName;
    }

    public static String getCurrentApkName() {
        return sCurrentApkName;
    }

    public static void setCurrentApkName(String currentApkName) {
        if (currentApkName == null || currentApkName.isEmpty()) {
            currentApkName = "unknown";
        }
        DataUtils.sCurrentApkName = currentApkName;
    }

    public static String getCurrentSDKVersion() {
        return sCurrentSDKVersion;
    }

    public static void setCurrentSDKVersion(String currentSDKVersion) {
        if (currentSDKVersion == null || currentSDKVersion.isEmpty()) {
            currentSDKVersion = "unknown";
        }
        DataUtils.sCurrentSDKVersion = currentSDKVersion;
    }

    public static void restoreAllStructInfo(WApplication application, boolean fromFile) {
        if (!fromFile) {
            application.setGrabTime(System.currentTimeMillis());
            if (application.getColorInfo() != null) {
                CodeLocatorApplicationInitializedListener.setColorInfo(application.getColorInfo());
            }
        }
        application.restoreAllStructInfo();
        if (application.getShowInfos() != null) {
            application.getShowInfos().sort(new Comparator<ShowInfo>() {
                @Override
                public int compare(ShowInfo o1, ShowInfo o2) {
                    return o1.getShowTime() < o2.getShowTime() ? 1 : (o1.getShowTime() == o2.getShowTime() ? 0 : -1);
                }
            });
        }
        if (application.getSchemaInfos() != null) {
            application.getSchemaInfos().sort(Comparator.comparing(SchemaInfo::getSchema));
        }
        sCurrentApkName = application.getPackageName();
        if (application.getActivity() != null) {
            restoreAllViewStructInfo(application.getActivity());
            final String startInfo = application.getActivity().getStartInfo();
            if (startInfo != null) {
                application.getActivity().setOpenActivityJumpInfo(JumpParser.getSingleJumpInfo(startInfo));
            }
        }
        restoreAllShowInfo(application.getShowInfos());
    }

    public static void restoreAllFileStructInfo(WFile wFile) {
        if (wFile == null) {
            return;
        }
        wFile.restoreAllFileStructInfo();
    }

    public static void sortFile(WFile wFile, boolean sortByName) {
        if (wFile == null) {
            return;
        }
        if (wFile.getChildren() != null) {
            if (sortByName) {
                wFile.getChildren().sort(Comparator.comparing(o -> o.getName().toLowerCase()));
            } else {
                wFile.getChildren().sort((o1, o2) -> {
                    final long l = o1.getLength() - o2.getLength();
                    if (l == 0) {
                        return 0;
                    } else if (l > 0) {
                        return 1;
                    } else {
                        return -1;
                    }
                });
            }
        }
        for (int i = 0; i < wFile.getChildCount(); i++) {
            sortFile(wFile.getChildAt(i), sortByName);
        }
    }

    public static HashMap<String, ExtraInfo> getViewAllTypeExtra(WView view, int actionType, boolean includeParent) {
        HashMap<String, ExtraInfo> hashMap = new HashMap<>();
        while (view != null) {
            final List<ExtraInfo> extraInfos = view.getExtraInfos();
            if (extraInfos != null && !extraInfos.isEmpty()) {
                for (ExtraInfo extra : extraInfos) {
                    if (extra == null || hashMap.get(extra.getTag()) != null) {
                        continue;
                    }
                    final ExtraAction extraAction = extra.getExtraAction();
                    if (extraAction == null) {
                        continue;
                    }
                    if ((extraAction.getActionType() & actionType) != 0) {
                        hashMap.put(extra.getTag(), extra);
                    }
                }
            }
            if (includeParent) {
                view = view.getParentView();
            } else {
                view = null;
            }
        }
        return hashMap;
    }

    public static HashMap<String, ExtraInfo> getViewAllTableTypeExtra(WView view) {
        if (view != null) {
            HashMap<String, ExtraInfo> hashMap = new HashMap<>();
            final List<ExtraInfo> extraInfos = view.getExtraInfos();
            if (extraInfos != null && !extraInfos.isEmpty()) {
                for (ExtraInfo extra : extraInfos) {
                    if (extra == null || hashMap.get(extra.getTag()) != null || extra.getShowType() == ExtraInfo.ShowType.EXTRA_TREE) {
                        continue;
                    }
                    final ExtraAction extraAction = extra.getExtraAction();
                    if (extraAction == null) {
                        continue;
                    }
                    hashMap.put(extra.getTag(), extra);
                }
            }
            return hashMap;
        }
        return null;
    }

    public static ExtraInfo getViewExtra(WView view, String extraTag) {
        while (view != null) {
            final ExtraInfo extraByTag = getExtraByTag(view.getExtraInfos(), extraTag);
            if (extraByTag != null && !extraByTag.isTableMode()) {
                return extraByTag;
            }
            view = view.getParentView();
        }
        return null;
    }

    public static ExtraInfo getExtraByTag(List<ExtraInfo> extraInfos, String extraTag) {
        if (extraInfos == null || extraInfos.isEmpty() || extraTag == null) {
            return null;
        }
        for (ExtraInfo extraInfo : extraInfos) {
            if (extraInfo == null) {
                continue;
            }
            if (extraTag.equals(extraInfo.getTag())) {
                return extraInfo;
            }
        }
        return null;
    }

    public static void restorePlatformInfo(WApplication application, String platFormInfo) {
        if (platFormInfo == null) {
            return;
        }
        final String[] splits = platFormInfo.split(";");
        if (splits == null || splits.length <= 0) {
            return;
        }
        for (String line : splits) {
            if (line.startsWith("V:")) {
                application.setSdkVersion(line.substring("V:".length()));
            } else if (line.startsWith("M:")) {
                application.setMinPluginVersion(line.substring("M:".length()));
            }
        }
    }

    private static void restoreAllViewStructInfo(WActivity activity) {
        if (activity == null || activity.getDecorViews() == null) {
            return;
        }
        final List<WView> decorViews = activity.getDecorViews();
        for (WView view : decorViews) {
            restoreAllViewStructInfo(view);
        }
    }

    private static void restoreAllViewStructInfo(WView view) {
        if (view == null) {
            return;
        }

        view.setXmlJumpInfo(JumpParser.getXmlJumpInfo(view.getXmlTag(), view.getIdStr()));
        view.setClickJumpInfo(JumpParser.getJumpInfo(view.getClickTag()));
        view.setTouchJumpInfo(JumpParser.getJumpInfo(view.getTouchTag()));
        view.setFindViewJumpInfo(JumpParser.getJumpInfo(view.getFindViewByIdTag()));

        for (int i = 0; i < view.getChildCount(); i++) {
            final WView childView = view.getChildAt(i);
            restoreAllViewStructInfo(childView);
        }
    }

    private static void restoreAllShowInfo(List<ShowInfo> showInfos) {
        if (showInfos == null || showInfos.isEmpty()) {
            return;
        }
        for (ShowInfo showInfo : showInfos) {
            showInfo.setJumpInfo(JumpParser.getSingleJumpInfo(showInfo.getShowInfo()));
        }
    }

    public static String getViewRealAlpha(WView view) {
        if (view == null) {
            return "";
        }
        float realAlpha = 1.0f;
        while (view != null) {
            realAlpha = realAlpha * view.getAlpha();
            view = view.getParentView();
        }
        return "" + realAlpha;
    }

    public static final int VISIBLE = 0x00000000;

    public static final int GONE = 0x00000008;

    public static WApplication buildApplicationForNoSDKRelease(Project project, String pkgName, String activityName) {
        try {
            final StringResponse response = DeviceManager.executeCmd(project,
                new AdbCommand(new AdbAction(AdbCommand.ACTION.DUMPSYS, "activity " + activityName)),
                StringResponse.class);
            final String dumpActivityStr = response.getData();
            final WApplication parser = new DumpInfoParser(dumpActivityStr).parser();
            if (parser == null) {
                return null;
            }
            parser.setPackageName(pkgName);
            return parser;
        } catch (Throwable t) {
            Log.e("buildApplicationForNoSDKRelease error", t);
        }
        return null;
    }

    public static WView buildViewInfoFromUix(Project project) {
        try {
            final File tmpUixFile = new File(FileUtils.sCodeLocatorMainDirPath, FileUtils.TEMP_UIX_FILE_NAME);
            if (tmpUixFile.exists()) {
                tmpUixFile.delete();
            }
            final String tmpDevicePath = "/data/local/tmp/" + FileUtils.TEMP_UIX_FILE_NAME;
            DeviceManager.executeCmd(project, new AdbCommand(new DeleteFileAction(tmpDevicePath)), BaseResponse.class);
            DeviceManager.executeCmd(project, new AdbCommand(
                new AdbAction(AdbCommand.ACTION.UIAUTOMATOR, "dump " + tmpDevicePath)
            ), BaseResponse.class);
            DeviceManager.executeCmd(project, new AdbCommand(
                new PullFileAction(tmpDevicePath, tmpUixFile.getAbsolutePath())
            ), BaseResponse.class);
            if (!tmpUixFile.exists()) {
                return null;
            }
            final WView uixView = new UixInfoParser(tmpUixFile.getAbsolutePath()).parser();
            return uixView;
        } catch (Throwable t) {
            Log.e("buildViewInfoFromUix error", t);
        }
        return null;
    }

    public static int getTotalViewCount(WView view) {
        if (view == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < view.getChildCount(); i++) {
            count += getTotalViewCount(view.getChildAt(i));
        }
        return count + 1;
    }

}
