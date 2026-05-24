SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
wasm-pack build "$SCRIPT_DIR/src/main/rs-wasm-script" --out-dir "$SCRIPT_DIR/static/pkg"