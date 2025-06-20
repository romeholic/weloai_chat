import java.util.UUID

plugins {
    id("com.android.library")
}

android {
    namespace = "org.vosk.models"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
        targetSdk = 33
    }

    buildFeatures {
        buildConfig = false
    }

    sourceSets {
        getByName("main") {
            // 保留默认的 src/main/assets ，再追加生成目录
            assets.srcDirs("src/main/assets", "$buildDir/generated/assets")
        }
    }
}

tasks.register<DefaultTask>("genUUID") {
    val uuid = UUID.randomUUID().toString()
    val odir = file("$buildDir/generated/assets/vosk-model-cn")
    val ofile = file("$odir/uuid")

    doLast {
        odir.mkdirs()
        ofile.writeText(uuid)
    }
}