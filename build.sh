#!/usr/bin/env bash
set -euo pipefail
export MSYS_NO_PATHCONV=1

PROJECT_DIR="D:/Github/gecko115-pad-browser"
BUILD_DIR="$PROJECT_DIR/build"
DIST_DIR="$PROJECT_DIR/dist"

export JAVA_HOME="D:/Github/uuyc/.tools/jdk17/jdk-17.0.21+9"
JDK_BIN="$JAVA_HOME/bin"
SDK_DIR="D:/Github/.android-sdk"
BT_DIR="$SDK_DIR/build-tools/35.0.0"
ANDROID_JAR="$SDK_DIR/platforms/android-35/android.jar"

GV_VER="115.0.20230726201356"
CACHE_DIR="D:/tmp/browsers"
GV_AAR="$CACHE_DIR/gv115.aar"
GV_EXO_AAR="$CACHE_DIR/gv115-exo2.aar"
SNAKEYAML_JAR="$CACHE_DIR/snakeyaml-2.0.jar"
GRADLE_CACHE="C:/Users/Administrator/.gradle/caches/modules-2/files-2.1"

mkdir -p "$CACHE_DIR"
if [ ! -f "$GV_AAR" ]; then
  echo "Downloading geckoview-armeabi-v7a-${GV_VER}.aar..."
  curl -fL --retry 3 -o "$GV_AAR" \
    "https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview-armeabi-v7a/${GV_VER}/geckoview-armeabi-v7a-${GV_VER}.aar"
fi
if [ ! -f "$GV_EXO_AAR" ]; then
  echo "Downloading geckoview-exoplayer2-${GV_VER}.aar..."
  curl -fL --retry 3 -o "$GV_EXO_AAR" \
    "https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview-exoplayer2/${GV_VER}/geckoview-exoplayer2-${GV_VER}.aar"
fi
if [ ! -f "$SNAKEYAML_JAR" ]; then
  curl -sL -x http://127.0.0.1:10808 --max-time 15 \
    -o "$SNAKEYAML_JAR" \
    "https://repo1.maven.org/maven2/org/yaml/snakeyaml/2.0/snakeyaml-2.0.jar"
fi

KOTLIN_STDLIB="$GRADLE_CACHE/org.jetbrains.kotlin/kotlin-stdlib/1.9.22/d6c44cd08d8f3f9bece8101216dbe6553365c6e3/kotlin-stdlib-1.9.22.jar"
ANNOTATION_JAR="$GRADLE_CACHE/androidx.annotation/annotation-jvm/1.6.0/a7257339a052df0f91433cf9651231bbb802b502/annotation-jvm-1.6.0.jar"
COLLECTION_JAR="$GRADLE_CACHE/androidx.collection/collection/1.2.0/34dbc21d203cc4d4d623ac572a21acd4ccd716af/collection-1.2.0.jar"
ARCH_COMMON_JAR="$GRADLE_CACHE/androidx.arch.core/core-common/2.2.0/5e1b8b81dfd5f52c56a8d53b18ca759c19a301f3/core-common-2.2.0.jar"
ARCH_RUNTIME_AAR="$GRADLE_CACHE/androidx.arch.core/core-runtime/2.2.0/39705982680d78556b679ef9d5400d4f17509b0b/core-runtime-2.2.0.aar"
LIFECYCLE_COMMON_JAR="$GRADLE_CACHE/androidx.lifecycle/lifecycle-common/2.6.2/10f354fdb64868baecd67128560c5a0d6312c495/lifecycle-common-2.6.2.jar"
CORE_AAR="$GRADLE_CACHE/androidx.core/core/1.12.0/5aa3088ed93ba8ebaea2b8a83befdfa829339e7a/core-1.12.0.aar"
LEGACY_V4_AAR="$GRADLE_CACHE/androidx.legacy/legacy-support-v4/1.0.0/3e1271b351e1209661b9fd769cd6681a31678fe/legacy-support-v4-1.0.0.aar"
LEGACY_UTILS_AAR="$GRADLE_CACHE/androidx.legacy/legacy-support-core-utils/1.0.0/9b9570042115da8629519090dfeb71df75da59fc/legacy-support-core-utils-1.0.0.aar"
LEGACY_UI_AAR="$GRADLE_CACHE/androidx.legacy/legacy-support-core-ui/1.0.0/61a264f996046e059f889914050fae1e75d3b702/legacy-support-core-ui-1.0.0.aar"
LIFECYCLE_PROCESS_AAR=$(find "$GRADLE_CACHE/androidx.lifecycle/lifecycle-process/2.6.2" -name "*.aar" | head -n 1)
LIFECYCLE_RUNTIME_AAR="$GRADLE_CACHE/androidx.lifecycle/lifecycle-runtime/2.6.2/f0ef27053faa89b3ec06c4f118cea0a15edde35b/lifecycle-runtime-2.6.2.aar"
STARTUP_AAR="$GRADLE_CACHE/androidx.startup/startup-runtime/1.1.1/9d4ba67056aff98b69eadc1477c19a1e67cb9470/startup-runtime-1.1.1.aar"
VP_AAR="$GRADLE_CACHE/androidx.versionedparcelable/versionedparcelable/1.1.1/18e18071e7715679e0593c570217edeb5ca48b9e/versionedparcelable-1.1.1.aar"

rm -rf "$BUILD_DIR" "$DIST_DIR"
mkdir -p "$BUILD_DIR/deps" "$BUILD_DIR/aars" "$BUILD_DIR/res_compiled" "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/apk_stage" "$DIST_DIR"

echo "[1/7] Generating multi-DPI launcher icons..."
"$JDK_BIN/javac.exe" -encoding UTF-8 -d "$BUILD_DIR/deps" "$PROJECT_DIR/tools/GenIcon.java"
"$JDK_BIN/java.exe" -cp "$BUILD_DIR/deps" GenIcon "$PROJECT_DIR/res"

echo "[2/7] Unpacking AARs & compiling each AAR's resources separately..."
RES_ZIPS=()
unpack_aar() {
  local name="$1"
  local aar="$2"
  local dir="$BUILD_DIR/aars/$name"
  mkdir -p "$dir"
  unzip -oq "$aar" -d "$dir"
  if [ -f "$dir/classes.jar" ]; then
    cp "$dir/classes.jar" "$BUILD_DIR/deps/${name}.jar"
  fi
  if [ -d "$dir/res" ] && [ -n "$(ls -A "$dir/res" 2>/dev/null)" ]; then
    "$BT_DIR/aapt2.exe" compile --dir "$dir/res" -o "$BUILD_DIR/res_compiled/${name}_res.zip"
    RES_ZIPS+=("$BUILD_DIR/res_compiled/${name}_res.zip")
  fi
}

unpack_aar "gv" "$GV_AAR"
unpack_aar "gv_exo2" "$GV_EXO_AAR"
unpack_aar "core" "$CORE_AAR"
unpack_aar "arch_runtime" "$ARCH_RUNTIME_AAR"
unpack_aar "legacy_v4" "$LEGACY_V4_AAR"
unpack_aar "legacy_utils" "$LEGACY_UTILS_AAR"
unpack_aar "legacy_ui" "$LEGACY_UI_AAR"
unpack_aar "lifecycle_process" "$LIFECYCLE_PROCESS_AAR"
unpack_aar "lifecycle_runtime" "$LIFECYCLE_RUNTIME_AAR"
unpack_aar "startup" "$STARTUP_AAR"
unpack_aar "vp" "$VP_AAR"

"$BT_DIR/aapt2.exe" compile --dir "$PROJECT_DIR/res" -o "$BUILD_DIR/res_compiled/app_res.zip"
RES_ZIPS+=("$BUILD_DIR/res_compiled/app_res.zip")

echo "[3/7] Linking all resources & generating R.java for all AndroidX + GeckoView packages..."
"$BT_DIR/aapt2.exe" link \
  --auto-add-overlay \
  -I "$ANDROID_JAR" \
  --manifest "$PROJECT_DIR/AndroidManifest.xml" \
  --java "$BUILD_DIR/gen" \
  --extra-packages "org.mozilla.geckoview:androidx.core:androidx.legacy.v4:androidx.legacy.coreutils:androidx.legacy.coreui:androidx.lifecycle.runtime:androidx.lifecycle.process:androidx.startup" \
  -A "$BUILD_DIR/aars/gv/assets" \
  -0 ja \
  -o "$BUILD_DIR/base_unaligned.apk" \
  "${RES_ZIPS[@]}"

echo "[4/7] Compiling Java sources + all generated R.java files..."
find "$BUILD_DIR/gen" -name "*.java" > "$BUILD_DIR/sources.txt"
find "$PROJECT_DIR/src" -name "*.java" >> "$BUILD_DIR/sources.txt"

"$JDK_BIN/javac.exe" -encoding UTF-8 -source 1.8 -target 1.8 \
  -cp "$ANDROID_JAR;$BUILD_DIR/deps/gv.jar;$ANNOTATION_JAR" \
  -d "$BUILD_DIR/classes" \
  @"$BUILD_DIR/sources.txt"

"$JDK_BIN/jar.exe" cf "$BUILD_DIR/deps/app.jar" -C "$BUILD_DIR/classes" .

echo "[5/7] Running D8 desugaring (target min-api 19)..."
"$BT_DIR/d8.bat" \
  --min-api 19 \
  --lib "$ANDROID_JAR" \
  --output "$BUILD_DIR/dex" \
  "$BUILD_DIR/deps/app.jar" \
  "$BUILD_DIR/deps/gv.jar" \
  "$BUILD_DIR/deps/gv_exo2.jar" \
  "$BUILD_DIR/deps/core.jar" \
  "$BUILD_DIR/deps/arch_runtime.jar" \
  "$BUILD_DIR/deps/legacy_v4.jar" \
  "$BUILD_DIR/deps/legacy_utils.jar" \
  "$BUILD_DIR/deps/legacy_ui.jar" \
  "$BUILD_DIR/deps/lifecycle_process.jar" \
  "$BUILD_DIR/deps/lifecycle_runtime.jar" \
  "$BUILD_DIR/deps/startup.jar" \
  "$BUILD_DIR/deps/vp.jar" \
  "$COLLECTION_JAR" \
  "$ARCH_COMMON_JAR" \
  "$SNAKEYAML_JAR" \
  "$KOTLIN_STDLIB" \
  "$ANNOTATION_JAR" \
  "$LIFECYCLE_COMMON_JAR"

ls -lh "$BUILD_DIR/dex/"*.dex

echo "[6/7] Packaging DEX + armeabi-v7a Gecko 115 native libraries..."
cp "$BUILD_DIR/base_unaligned.apk" "$BUILD_DIR/with_libs.apk"
mkdir -p "$BUILD_DIR/apk_stage/lib/armeabi-v7a"
cp "$BUILD_DIR/aars/gv/jni/armeabi-v7a/"*.so "$BUILD_DIR/apk_stage/lib/armeabi-v7a/"
cp "$BUILD_DIR/dex/"*.dex "$BUILD_DIR/apk_stage/"

(
  cd "$BUILD_DIR/apk_stage"
  "$JDK_BIN/jar.exe" uf "$BUILD_DIR/with_libs.apk" classes*.dex lib
)

echo "[7/7] Zipaligning (4-byte alignment for STORED omni.ja) & V1+V2 signing..."
KEYSTORE="$PROJECT_DIR/tools/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
  "$JDK_BIN/keytool.exe" -genkeypair -v \
    -keystore "$KEYSTORE" \
    -storepass android -keypass android \
    -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=GeckoPad, OU=MiPad1, O=MozillaGecko115, L=CN, S=CN, C=CN" >/dev/null 2>&1
fi

"$BT_DIR/zipalign.exe" -f 4 "$BUILD_DIR/with_libs.apk" "$BUILD_DIR/aligned.apk"

FINAL_APK="$DIST_DIR/GeckoPad_115.0_MiPad_KitKat_arm.apk"
"$BT_DIR/apksigner.bat" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --out "$FINAL_APK" \
  "$BUILD_DIR/aligned.apk"

echo "=== VERIFYING APK PACKAGING (omni.ja MUST BE Stored!) ==="
unzip -lv "$FINAL_APK" | grep -E "omni\.ja|classes.*\.dex|libxul\.so"
ls -lh "$FINAL_APK"
md5sum "$FINAL_APK"
