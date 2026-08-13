package com.nianri.app;

import java.util.Locale;

final class BackgroundAccessGuide {
    static final class Target {
        final String packageName;
        final String className;

        Target(String packageName, String className) {
            this.packageName = packageName;
            this.className = className;
        }
    }

    final String brandName;
    final String[] steps;
    final String note;
    final Target[] targets;

    private BackgroundAccessGuide(
            String brandName,
            String[] steps,
            String note,
            Target... targets
    ) {
        this.brandName = brandName;
        this.steps = steps;
        this.note = note;
        this.targets = targets;
    }

    static BackgroundAccessGuide forDevice(String manufacturer, String brand) {
        String maker = ((manufacturer == null ? "" : manufacturer)
                + " "
                + (brand == null ? "" : brand)).toLowerCase(Locale.ROOT);
        if (maker.contains("huawei")) {
            return new BackgroundAccessGuide(
                    "华为",
                    new String[]{
                            "在“应用启动管理”中找到“念日”",
                            "关闭“自动管理”，进入“手动管理”",
                            "开启“允许自启动”和“允许后台活动”"
                    },
                    "如果新版系统中找不到“应用启动管理”，可跳过该项，继续检查电池设置。",
                    new Target(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
            );
        }
        if (maker.contains("honor") || maker.contains("hihonor")) {
            return new BackgroundAccessGuide(
                    "荣耀",
                    new String[]{
                            "在“应用启动管理”中找到“念日”",
                            "关闭“自动管理”，进入“手动管理”",
                            "开启“允许自启动”和“允许后台活动”"
                    },
                    "不同 MagicOS 版本的入口名称可能略有不同，但需要开启的两项相同。",
                    new Target(
                            "com.hihonor.systemmanager",
                            "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
            );
        }
        if (maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco")) {
            return new BackgroundAccessGuide(
                    "小米 / Redmi",
                    new String[]{
                            "在“自启动管理”中找到“念日”",
                            "开启“自启动”",
                            "进入念日的耗电设置，选择“无限制”"
                    },
                    "HyperOS 和 MIUI 的页面名称可能略有不同。",
                    new Target(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
            );
        }
        if (maker.contains("vivo") || maker.contains("iqoo")) {
            return new BackgroundAccessGuide(
                    "vivo / iQOO",
                    new String[]{
                            "在 i管家的“自启动”中找到“念日”",
                            "开启“允许自启动”",
                            "在念日的耗电详情中允许后台运行或后台高耗电"
                    },
                    "“后台弹窗”不等于后台定时唤醒，不需要为念日开启后台弹窗。",
                    new Target(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    ),
                    new Target(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                    )
            );
        }
        if (maker.contains("oppo")
                || maker.contains("realme")
                || maker.contains("oneplus")
                || maker.contains("oplus")) {
            return new BackgroundAccessGuide(
                    "OPPO / realme / 一加",
                    new String[]{
                            "在“自启动管理”中找到“念日”",
                            "开启“允许自启动”",
                            "在耗电管理中允许后台运行或选择“不限制”"
                    },
                    "ColorOS 版本不同时，“自启动”可能位于手机管家或应用管理中。",
                    new Target(
                            "com.oplus.safecenter",
                            "com.oplus.safecenter.permission.startup.StartupAppListActivity"
                    ),
                    new Target(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
            );
        }
        if (maker.contains("samsung")) {
            return new BackgroundAccessGuide(
                    "三星",
                    new String[]{
                            "打开念日的“应用信息”",
                            "进入“电池”，允许后台使用",
                            "在后台使用限制中，不要让念日进入休眠"
                    },
                    "One UI 版本不同时，电池选项的名称可能略有不同。"
            );
        }
        return new BackgroundAccessGuide(
                "Android 手机",
                new String[]{
                        "打开念日的“应用信息”",
                        "允许念日在后台运行",
                        "将电池策略设为“不限制”或同类选项"
                },
                "不同品牌的设置名称不完全相同，请优先查找“自启动”、“后台运行”或“电池”。"
        );
    }

    boolean hasDedicatedSettingsPage() {
        return targets.length > 0;
    }
}
