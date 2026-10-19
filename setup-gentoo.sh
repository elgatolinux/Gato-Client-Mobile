#!/usr/bin/env bash
# Instala todo lo necesario para desarrollar Gato Client Mobile en Gentoo:
#   - JDK 17 (dev-java/openjdk-bin:17) y JDK 8 (toolchain que piden los módulos del relay)
#   - git, unzip, wget, adb (dev-util/android-tools)
#   - Android SDK (cmdline-tools, platform-tools, platforms;android-35/36, build-tools;35.0.0)
#   - local.properties y variables de entorno (JAVA_HOME, ANDROID_HOME, PATH)
#
# Uso:
#   ./setup-gentoo.sh            # instala dependencias + SDK
#   ./setup-gentoo.sh --studio   # además instala Android Studio (dev-util/android-studio, ~amd64)
#   ./setup-gentoo.sh --build    # al final compila GatoRelay.jar y el APK debug
#
# Variables opcionales:
#   ANDROID_HOME       (por defecto ~/Android/Sdk)
#   CMDLINE_TOOLS_ZIP  (nombre del zip de cmdline-tools en dl.google.com)

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
CMDLINE_TOOLS_ZIP="${CMDLINE_TOOLS_ZIP:-commandlinetools-linux-13114758_latest.zip}"
JDK_SLOT=17
SDK_PACKAGES=(
    "platform-tools"
    "platforms;android-35"
    "platforms;android-36"
    "build-tools;35.0.0"
)
ENV_FILE="$HOME/.config/gato-android-env.sh"

WITH_STUDIO=0
DO_BUILD=0
for arg in "$@"; do
    case "$arg" in
        --studio) WITH_STUDIO=1 ;;
        --build)  DO_BUILD=1 ;;
        -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
        *) echo "Opción desconocida: $arg" >&2; exit 1 ;;
    esac
done

log()  { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!!\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31mxxx\033[0m %s\n' "$*" >&2; exit 1; }

[[ -f /etc/gentoo-release ]] || die "Este script es solo para Gentoo."
[[ $EUID -ne 0 ]] || die "Ejecútalo como usuario normal (usará sudo cuando haga falta)."

if command -v sudo >/dev/null; then
    SUDO=sudo
elif command -v doas >/dev/null; then
    SUDO=doas
else
    die "Se necesita sudo o doas."
fi

# ---------------------------------------------------------------------------
# 1. Paquetes del sistema
# ---------------------------------------------------------------------------
PKGS=(
    "dev-java/openjdk-bin:${JDK_SLOT}"
    "dev-java/openjdk-bin:8"   # Gradle lo detecta en /usr/lib/jvm como toolchain Java 8
    dev-vcs/git
    app-arch/unzip
    net-misc/wget
    dev-util/android-tools
)

if (( WITH_STUDIO )); then
    PKGS+=(dev-util/android-studio)
    kw_dir=/etc/portage/package.accept_keywords
    if ! grep -rqs '^dev-util/android-studio' "$kw_dir"; then
        log "Aceptando ~amd64 para dev-util/android-studio"
        $SUDO mkdir -p "$kw_dir"
        echo "dev-util/android-studio ~amd64" | $SUDO tee "$kw_dir/android-studio" >/dev/null
    fi
fi

log "Instalando paquetes: ${PKGS[*]}"
$SUDO emerge --ask=n --noreplace --verbose "${PKGS[@]}"

# Permite usar adb con dispositivos USB sin root
if getent group plugdev >/dev/null && ! id -nG | grep -qw plugdev; then
    log "Añadiendo $USER al grupo plugdev (requiere volver a iniciar sesión)"
    $SUDO gpasswd -a "$USER" plugdev
fi

# ---------------------------------------------------------------------------
# 2. JDK 17
# ---------------------------------------------------------------------------
JAVA_HOME=""
for candidate in /usr/lib/jvm/openjdk-bin-${JDK_SLOT} /opt/openjdk-bin-${JDK_SLOT}*; do
    if [[ -x "$candidate/bin/java" ]]; then
        JAVA_HOME="$candidate"
        break
    fi
done
[[ -n "$JAVA_HOME" ]] || die "No se encontró el JDK ${JDK_SLOT} tras instalarlo."
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
log "Usando JAVA_HOME=$JAVA_HOME ($("$JAVA_HOME/bin/java" -version 2>&1 | head -1))"

if command -v eselect >/dev/null && eselect java-vm list >/dev/null 2>&1; then
    eselect java-vm set user "openjdk-bin-${JDK_SLOT}" || warn "No se pudo fijar la VM de usuario con eselect."
fi

# ---------------------------------------------------------------------------
# 3. Android SDK
# ---------------------------------------------------------------------------
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then
    log "Descargando Android cmdline-tools en $ANDROID_HOME"
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' EXIT
    wget -q --show-progress -O "$tmp/cmdline-tools.zip" \
        "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"
    unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    rm -rf "$ANDROID_HOME/cmdline-tools/latest"
    mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
else
    log "cmdline-tools ya instalados"
fi

log "Aceptando licencias del Android SDK"
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true

log "Instalando paquetes del SDK: ${SDK_PACKAGES[*]}"
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" --install "${SDK_PACKAGES[@]}"

# ---------------------------------------------------------------------------
# 4. Configuración del proyecto y del entorno
# ---------------------------------------------------------------------------
log "Escribiendo $PROJECT_DIR/local.properties"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$PROJECT_DIR/local.properties"

log "Escribiendo variables de entorno en $ENV_FILE"
mkdir -p "$(dirname "$ENV_FILE")"
cat > "$ENV_FILE" <<EOF
# Generado por Gato-Client-Mobile/setup-gentoo.sh
export JAVA_HOME="$JAVA_HOME"
export ANDROID_HOME="$ANDROID_HOME"
export ANDROID_SDK_ROOT="\$ANDROID_HOME"
export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
EOF

for rc in "$HOME/.zshrc" "$HOME/.bashrc"; do
    [[ -f "$rc" ]] || continue
    if ! grep -qF "$ENV_FILE" "$rc"; then
        printf '\n[ -f "%s" ] && . "%s"\n' "$ENV_FILE" "$ENV_FILE" >> "$rc"
        log "Añadido source de $ENV_FILE a $rc"
    fi
done

chmod +x "$PROJECT_DIR/gradlew"
# En main el relay es un proyecto aparte con su propio gradlew; en lynx-client es un subproyecto
RELAY_STANDALONE=0
if [[ -f "$PROJECT_DIR/relay/gradlew" ]]; then
    RELAY_STANDALONE=1
    chmod +x "$PROJECT_DIR/relay/gradlew"
fi

# ---------------------------------------------------------------------------
# 5. Compilación opcional
# ---------------------------------------------------------------------------
if (( DO_BUILD )); then
    export ANDROID_HOME
    if (( RELAY_STANDALONE )); then
        log "Compilando GatoRelay.jar"
        (cd "$PROJECT_DIR/relay" && ./gradlew jar)
        mkdir -p "$PROJECT_DIR/app/libs"
        cp "$PROJECT_DIR/relay/build/libs/GatoRelay.jar" "$PROJECT_DIR/app/libs/"
    fi

    log "Compilando APK debug"
    # --stop: un daemon previo no ve los JDK recién instalados
    (cd "$PROJECT_DIR" && ./gradlew --stop && ./gradlew :app:assembleDebug)
    log "APK en app/build/outputs/apk/debug/"
fi

log "Listo. Abre una terminal nueva (o ejecuta: . $ENV_FILE) y compila con:"
if (( RELAY_STANDALONE )); then
    echo "    cd relay && ./gradlew jar && cp build/libs/GatoRelay.jar ../app/libs/ && cd .."
fi
echo "    ./gradlew :app:assembleDebug"
