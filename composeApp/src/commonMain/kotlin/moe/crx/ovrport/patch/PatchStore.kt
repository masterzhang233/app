package moe.crx.ovrport.patch

import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import moe.crx.ovrport.model.generateConfig
import moe.crx.ovrport.utils.*

object PatchStore {
    val PATCHES = mutableListOf<Patch>()

    init {
        PATCHES += Patch("Copy ovrport libraries") {
            selectWorkspace {
                file.resolve("root/lib")
                    .listFiles()
                    ?.forEach {
                        val arch = file.parentFile
                            ?.parentFile
                            ?.resolve(Constants.LIBRARIES_DIR)
                            ?.resolve("lib")
                            ?.resolve(it.name)

                        if (arch?.exists() == true) {
                            arch.copyRecursively(it, true)
                        }
                    }
            }
        }
        PATCHES += Patch("Fix minimal Android SDK") {
            selectManifest {
                readJson().takeNodesEach({ named("manifest") }) {
                    takeNodesEach({ named("uses-sdk") }) {
                        takeAttributesEach({ named("minSdkVersion") }) {
                            take<Int>("data") { 29 }
                        }
                    }
                }?.let {
                    writeJson(it)
                }
            }
            selectSmali {
                replace(
                    "invoke-virtual \\{(\\w+), (\\w+)\\}, Landroid/view/Window;->setDecorFitsSystemWindows\\(Z\\)V",
                    ""
                )
            }
            selectSmali("androidx/core/view/WindowCompat\$Api30Impl") {
                replace(
                    "invoke-virtual \\{(\\w+), (\\w+)\\}, Landroid/view/Window;->setDecorFitsSystemWindows\\(Z\\)V",
                    ""
                )
            }
            selectSmali("androidx/core/view/WindowInsetsCompat\$TypeImpl30") {
                replace(
                    "(toPlatformType\\(I\\)I.*?)\\.locals (\\w+)",
                    "$0\nconst v0, 0x0\nreturn v0"
                )
            }
            selectSmali("androidx/core/view/WindowInsetsCompat\$Impl30") {
                replace(
                    "(getInsets\\(I\\)Landroidx/core/graphics/Insets;.*?)\\.locals (\\w+)",
                    "$0\nsget-object p0, Landroidx/core/graphics/Insets;->NONE:Landroidx/core/graphics/Insets;\nreturn-object p0"
                )
                replace("(isVisible\\(I\\)Z.*?)\\.locals (\\w+)", "$0\nconst/4 p0, 0\nreturn p0")
            }
            selectSmali("com/epicgames/unreal/GameActivity") {
                replace(
                    "const-string \\w+?, \\\"vibrator_manager\\\".*?iput-object \\w+?, \\w+?, Lcom/epicgames/unreal/GameActivity;->\\w+?:Landroid/os/Vibrator;",
                    ""
                )
            }
        }
        PATCHES += Patch("Generate ovrport config") {
            selectLibrary("libfrda.config.so") {
                readJson()?.let { generateConfig(it.toString()) }?.let {
                    createLibrary("liboverport.config.so") {
                        writeJson(JSONObject(it))
                    }
                }
            }
        }
        PATCHES += Patch("Remove localized app names") {
            selectResources {
                readJson().take<JSONArray>("packages") {
                    takeEach<JSONObject> {
                        take<JSONArray>("specs") {
                            takeEach<JSONObject> {
                                take<JSONArray>("types") {
                                    takeEach<JSONObject> {
                                        val configLanguage = elem<JSONObject>("config").elem<String>("language")

                                        take<JSONArray>("entries") {
                                            takeEach<JSONObject> {
                                                this.takeIf {
                                                    elem<String>("entry_name") != "app_name" || configLanguage == null
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }?.let {
                    writeJson(it)
                }
            }
        }
        PATCHES += Patch("Clean up Frida") {
            selectLibrary("libfrda.so") {
                file.delete()
            }
            selectLibrary("libfrda.config.so") {
                file.delete()
            }
            selectLibrary("libscript.so") {
                file.delete()
            }

            selectManifest {
                readJson().takeNodesEach(({ named("manifest") })) {
                    takeNodesEach({ named("application") }) {
                        takeNodesEach({ named("activity") }) {
                            attributeName()?.replace('.', '/')?.let {
                                selectSmali(it) {
                                    replace(
                                        "const-string (\\w+), \\\"frda\\\"\\s+?invoke-static \\{\\1\\}, Ljava/lang/System;->loadLibrary\\(Ljava/lang/String;\\)V",
                                        ""
                                    )
                                }
                            }

                            this
                        }
                    }
                }
            }
        }
        PATCHES += Patch("Patch Oculus detection for Unity") {
            selectSmali("com/unity/oculus/OculusUnity") {
                replace(
                    "(getIsOnOculusHardware\\(\\)Z.*?)return (\\w+)",
                    "$1\nconst/4 $2, 0x1\nreturn $2"
                )
            }
        }
        PATCHES += Patch("Patch Oculus detection for Unreal") {
            selectSmali(
                "com/epicgames/ue4/GameActivity",
                "com/epicgames/unreal/GameActivity",
            ) {
                replace(
                    "sget-object (\\w+), Landroid/os/Build;->MANUFACTURER:Ljava/lang/String;",
                    "const-string $1, \"Oculus\""
                )
                replace(
                    "sget-object (\\w+), Landroid/os/Build;->MODEL:Ljava/lang/String;",
                    "const-string $1, \"Quest 2\""
                )
            }
        }
        PATCHES += Patch("WaveSDK compatibility", "Enable WaveSDK and plugin support on Pico headsets") {
            selectSmali {
                replace(
                    "sget-object (\\w+), Landroid/os/Build;->MANUFACTURER:Ljava/lang/String;",
                    "const-string $1, \"htc\""
                )
                replace(
                    "sget-object (\\w+), Landroid/os/Build;->MODEL:Ljava/lang/String;",
                    "const-string $1, \"Vive Focus 3\""
                )
            }
        }
        PATCHES += Patch("Pico/YVR/Quest metadata") {
            selectManifest {
                readJson().takeNodesEach(({ named("manifest") })) {
                    takeNodesEach({ named("application") }) {
                        takeNodesEach({ named("meta-data") }) {
                            if (attributeName() == "com.oculus.supportedDevices") null else this
                        }
                        takeNodes {
                            this
                                ?.put(createMetadata("pvr.app.type", "vr"))
                                ?.put(createMetadata("com.yvr.intent.category.VR", "vr_only"))
                                ?.put(createMetadata("com.oculus.supportedDevices", "all"))
                        }
                    }
                }?.let {
                    writeJson(it)
                }
            }
        }
        PATCHES += Patch("Fix launcher icon entry") {
            selectManifest {
                readJson().takeNodesEach(({ named("manifest") })) {
                    takeNodesEach({ named("application") }) {
                        takeNodesEach({ named("activity") }) {
                            takeNodesEach({ named("intent-filter") }) {
                                val hasIntent = elem<JSONArray>("nodes").elemEach<JSONObject> { named("category") }.map {
                                    when (it.attributeName()) {
                                        "com.oculus.intent.category.VR" -> true
                                        "android.intent.category.LAUNCHER" -> true
                                        else -> false
                                    }
                                }.reduceOrNull { l, r -> l || r } ?: false

                                takeNodes {
                                    if (!hasIntent) this else {
                                        JSONArray()
                                            .put(createAction("android.intent.action.MAIN"))
                                            .put(createCategory("android.intent.category.LAUNCHER"))
                                            .put(createCategory("com.oculus.intent.category.VR"))
                                            .put(createCategory("com.yvr.intent.category.VR"))
                                    }
                                }
                            }
                        }
                    }
                }?.let {
                    writeJson(it)
                }
            }
        }
        PATCHES += Patch("Remove uses-library") {
            selectManifest {
                readJson().takeNodesEach({ named("manifest") }) {
                        takeNodesEach({ named("application") }) {
                            takeNodesEach({ named("uses-library") }) { null }
                            takeNodesEach({ named("uses-native-library") }) { null }
                        }
                }?.let {
                    writeJson(it)
                }
            }
        }
        PATCHES += Patch("Fix Unreal Engine 4 crash") {
            createSmali("com/unity3d/player/UnityPlayer") {
                if (readText().isBlank()) {
                    append(".class public Lcom/unity3d/player/UnityPlayer;")
                    append(".super Ljava/lang/Object;")
                    append(".field public static currentActivity:Landroid/app/Activity;")
                }
            }
            selectSmali("com/epicgames/ue4/GameActivity") {
                replace(
                    "sput-object (\\w+), Lcom\\/epicgames\\/ue4\\/GameActivity;->\\w+:Lcom\\/epicgames\\/ue4\\/GameActivity;",
                    "$0\nsput-object $1, Lcom/unity3d/player/UnityPlayer;->currentActivity:Landroid/app/Activity;"
                )
            }
        }
        PATCHES += Patch("Patch MetaXRAudioWwise") {
            selectLibrary("libMetaXRAudioWwise.so") {
                replaceHex(
                    "57 D0 3B D5 40 EB FF F0 00 0C 2D 91 E8 16 40 F9 A8 83 1F F8 DC 7F 0B 94 00 01 00 B4 E3 03 00 AA 21 EB FF B0",
                    "57 D0 3B D5 20 00 80 D2 1F 20 03 D5 E8 16 40 F9 A8 83 1F F8 1F 20 03 D5 00 01 00 B4 03 00 80 D2 21 EB FF B0"
                )
            }
        }
    }
}