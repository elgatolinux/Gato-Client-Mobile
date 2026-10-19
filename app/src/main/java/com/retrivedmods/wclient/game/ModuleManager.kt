package com.retrivedmods.wclient.game

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.retrivedmods.wclient.application.AppContext
import com.retrivedmods.wclient.game.module.combat.AntiCrystalModule
import com.retrivedmods.wclient.game.module.combat.AntiKnockbackModule
import com.retrivedmods.wclient.game.module.combat.CrystalSmashModule
import com.retrivedmods.wclient.game.module.combat.HitboxModule
import com.retrivedmods.wclient.game.module.combat.AutoTotemModule
import com.retrivedmods.wclient.game.module.combat.GatoAuraModule        // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.combat.GatoAuraXModule       // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.combat.Plus999AuraModule     // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.combat.CriticalsModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.combat.BowSpamModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.combat.OffhandModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.combat.AutoArmorModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.combat.RotCycleModule    // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.motion.FastStopModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.motion.AutoSneakModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.misc.BlinkModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.misc.NoPacketModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.misc.DisablerModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.misc.FastUseModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.misc.KeyStrokesModule
import com.retrivedmods.wclient.game.module.misc.AutoHotbarModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.misc.ChatSuffixModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.misc.AutoTPAModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.misc.AutoKitModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.misc.AutoHubModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.misc.ArrayListModule
import com.retrivedmods.wclient.game.module.motion.NoClipModule
import com.retrivedmods.wclient.game.module.motion.BypassFlyModule       // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.misc.AutoDisconnectModule
import com.retrivedmods.wclient.game.module.misc.CommandHandlerModule
import com.retrivedmods.wclient.game.module.visual.CoordinatesModule
import com.retrivedmods.wclient.game.module.misc.DesyncModule
import com.retrivedmods.wclient.game.module.misc.FakeDeathModule
import com.retrivedmods.wclient.game.module.misc.FakeXPModule
import com.retrivedmods.wclient.game.module.misc.MinerModule
import com.retrivedmods.wclient.game.module.misc.NoChatModule
import com.retrivedmods.wclient.game.module.misc.PieChartModule
import com.retrivedmods.wclient.game.module.misc.PopCounterModule
import com.retrivedmods.wclient.game.module.misc.PositionLoggerModule
import com.retrivedmods.wclient.game.module.misc.ReplayModule
import com.retrivedmods.wclient.game.module.misc.ChestStealerModule
import com.retrivedmods.wclient.game.module.misc.SpammerModule
import com.retrivedmods.wclient.game.module.misc.ToggleSoundModule
import com.retrivedmods.wclient.game.module.misc.WaterMarkModule
import com.retrivedmods.wclient.game.module.world.AntiDebuffModule
import com.retrivedmods.wclient.game.module.world.EffectsModule
import com.retrivedmods.wclient.game.module.world.ParticlesModule
import com.retrivedmods.wclient.game.module.world.TimeShiftModule
import com.retrivedmods.wclient.game.module.world.WeatherControllerModule
import com.retrivedmods.wclient.game.module.motion.AirJumpModule
import com.retrivedmods.wclient.game.module.motion.AntiAFKModule
import com.retrivedmods.wclient.game.module.motion.AutoWalkModule
import com.retrivedmods.wclient.game.module.motion.BhopModule
import com.retrivedmods.wclient.game.module.motion.FlyModule
import com.retrivedmods.wclient.game.module.motion.HighJumpModule
import com.retrivedmods.wclient.game.module.motion.JetPackModule
import com.retrivedmods.wclient.game.module.motion.MotionFlyModule
import com.retrivedmods.wclient.game.module.motion.PlayerTPModule
import com.retrivedmods.wclient.game.module.motion.SpeedModule
import com.retrivedmods.wclient.game.module.motion.SpiderModule
import com.retrivedmods.wclient.game.module.motion.SprintModule
import com.retrivedmods.wclient.game.module.visual.CrosshairModule
import com.retrivedmods.wclient.game.module.visual.DamageTextModule
import com.retrivedmods.wclient.game.module.visual.ESPModule
import com.retrivedmods.wclient.game.module.visual.FullbrightModule
import com.retrivedmods.wclient.game.module.visual.MinimapModule
import com.retrivedmods.wclient.game.module.visual.NetworkInfoModule
import com.retrivedmods.wclient.game.module.visual.NoHurtCameraModule
import com.retrivedmods.wclient.game.module.visual.PlayerJoinModule
import com.retrivedmods.wclient.game.module.visual.SpeedDisplayModule
import com.retrivedmods.wclient.game.module.visual.WorldStateModule
import com.retrivedmods.wclient.game.module.visual.ZoomModule
import com.retrivedmods.wclient.game.module.visual.TargetHudModule
import com.retrivedmods.wclient.game.module.visual.PearlTrackerModule   // NUEVO (port de Gato Client)
import com.retrivedmods.wclient.game.module.visual.SessionInfoModule
import com.retrivedmods.wclient.game.module.visual.NotificationsModule
import com.retrivedmods.wclient.game.module.visual.CatCompanionModule
import com.retrivedmods.wclient.game.module.visual.HitEffectsModule
import com.retrivedmods.wclient.game.module.world.FreeCameraModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File

object ModuleManager {

    private val _modules: MutableList<Module> = ArrayList()

    val modules: List<Module> = _modules

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        with(_modules) {
            // Combat
            add(AutoTotemModule())
            add(AntiKnockbackModule())

            add(AntiCrystalModule())
            add(HitboxModule())
            add(CrystalSmashModule())
            add(GatoAuraModule())          // NUEVO — port de GatoAura (Gato Client)
            add(GatoAuraXModule())         // NUEVO — port de GatoAuraX (Gato Client)
            add(Plus999AuraModule())       // NUEVO — port de +999aura (Gato Client)
            add(CriticalsModule())  // NUEVO — port de Gato Client
            add(BowSpamModule())  // NUEVO — port de Gato Client
            add(OffhandModule())  // NUEVO — port de Gato Client
            add(AutoArmorModule())  // NUEVO — port de Gato Client
            add(RotCycleModule())   // NUEVO — port de Gato Client (cicla rots con 1 toque)

            // Motion
            add(MotionFlyModule())
            add(PlayerTPModule())
            add(FlyModule())
            add(BypassFlyModule())         // NUEVO — port de BypassFly (Gato Client)
            add(SpeedModule())
            add(AirJumpModule())
            add(NoClipModule())
            add(JetPackModule())
            add(HighJumpModule())
            add(BhopModule())
            add(SprintModule())
            add(AutoWalkModule())
            add(AntiAFKModule())
            add(SpiderModule())
            add(FastStopModule())  // NUEVO — port de Gato Client
            add(AutoSneakModule())  // NUEVO — port de Gato Client

            // Visual
            add(DamageTextModule())
            add(ESPModule())
            add(PlayerJoinModule())
            add(ZoomModule())
            add(CoordinatesModule())
            add(NoHurtCameraModule())
            add(SpeedDisplayModule())
            add(NetworkInfoModule())
            add(WorldStateModule())
            add(MinimapModule())
            add(CrosshairModule())
            add(TargetHudModule())
            add(FullbrightModule())
            add(PearlTrackerModule())  // NUEVO — port de Gato Client
            add(SessionInfoModule())
            add(NotificationsModule())
            add(CatCompanionModule())
            add(HitEffectsModule())

            // World
            add(FreeCameraModule())
            add(TimeShiftModule())
            add(WeatherControllerModule())
            add(EffectsModule())
            add(ParticlesModule())
            add(AntiDebuffModule())

            // Misc

            add(AutoDisconnectModule())
            add(ArrayListModule())
            add(ToggleSoundModule())
            add(ChestStealerModule())
            add(DesyncModule())
            add(SpammerModule())
            add(WaterMarkModule())
            add(PositionLoggerModule())
            add(NoChatModule())
            add(CommandHandlerModule())
            add(ReplayModule())
            add(PieChartModule())
            add(PopCounterModule())
            add(FakeDeathModule())
            add(FakeXPModule())
            add(MinerModule())
            add(BlinkModule())  // NUEVO — port de Gato Client
            add(DisablerModule())  // NUEVO — port de Gato Client
            add(FastUseModule())  // NUEVO — port de Gato Client
            add(AutoHotbarModule())  // NUEVO — port de Gato Client
            add(ChatSuffixModule())  // NUEVO — port de Gato Client
            add(AutoTPAModule())  // NUEVO — port de Gato Client
            add(AutoKitModule())  // NUEVO — port de Gato Client
            add(AutoHubModule())  // NUEVO — port de Gato Client
            add(NoPacketModule())  // NUEVO — port de Gato Client
            add(KeyStrokesModule())
        }
    }

    fun saveConfig() {

        if (!AppContext.isInitialized) {
            return
        }

        val configsDir = AppContext.instance.filesDir.resolve("configs")
        configsDir.mkdirs()

        val config = configsDir.resolve("UserConfig.json")
        val jsonObject = buildJsonObject {
            put("modules", buildJsonObject {
                _modules.forEach {
                    if (it.private) {
                        return@forEach
                    }
                    put(it.name, it.toJson())
                }
            })
        }

        config.writeText(json.encodeToString(JsonObject.serializer(), jsonObject))
    }

    fun loadConfig() {

        if (!AppContext.isInitialized) {
            return
        }

        val configsDir = AppContext.instance.filesDir.resolve("configs")
        configsDir.mkdirs()

        val config = configsDir.resolve("UserConfig.json")
        if (!config.exists()) {
            return
        }

        val jsonString = config.readText()
        if (jsonString.isEmpty()) {
            return
        }

        try {
            val jsonObject = json.parseToJsonElement(jsonString).jsonObject
            val modules = jsonObject["modules"]?.jsonObject ?: return

            _modules.forEach { module ->
                (modules[module.name] as? JsonObject)?.let {
                    module.fromJson(it)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun exportConfig(): String {
        val jsonObject = buildJsonObject {
            put("modules", buildJsonObject {
                _modules.forEach {
                    if (it.private) {
                        return@forEach
                    }
                    put(it.name, it.toJson())
                }
            })
        }
        return json.encodeToString(JsonObject.serializer(), jsonObject)
    }

    fun importConfig(configStr: String) {
        try {
            val jsonObject = json.parseToJsonElement(configStr).jsonObject
            val modules = jsonObject["modules"]?.jsonObject ?: return

            _modules.forEach { module ->
                modules[module.name]?.let {
                    if (it is JsonObject) {
                        module.fromJson(it)
                    }
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid config format: ${e.message}")
        }
    }

    fun exportConfigToFile(context: Context, filePath: String): Boolean {
        return try {
            val file = if (filePath.contains("/")) {
                File(filePath)
            } else {
                val configsDir = context.getExternalFilesDir("configs")
                configsDir?.mkdirs()
                File(configsDir, if (filePath.endsWith(".json")) filePath else "$filePath.json")
            }

            file.parentFile?.mkdirs()
            file.writeText(exportConfig())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getWClientConfigsDirectory(): File? {
        return try {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            val baseDir = if (documentsDir.exists() || documentsDir.mkdirs()) {
                documentsDir
            } else {
                downloadsDir
            }

            val wclientDir = File(baseDir, "WClient")
            val configsDir = File(wclientDir, "configs")

            if (configsDir.exists() || configsDir.mkdirs()) {
                configsDir
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun importConfigFromFile(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val configStr = input.bufferedReader().readText()
                importConfig(configStr)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}