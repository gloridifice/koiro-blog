use wasm_bindgen::prelude::*;
use web_sys::window;

/// Creates/sets a cookie with optional expiration in days.
pub fn create_cookie(name: &str, value: &str, days: Option<i32>) -> Result<(), JsValue> {
    let doc = window()
        .and_then(|w| w.document())
        .ok_or_else(|| JsValue::from_str("no document"))?;

    let expires = match days {
        Some(d) if d >= 0 => {
            let millis = d as f64 * 24.0 * 60.0 * 60.0 * 1000.0;
            let date = js_sys::Date::new(&JsValue::from_f64(js_sys::Date::now() + millis));
            format!(
                "; expires={}",
                date.to_utc_string().as_string().unwrap_or_default()
            )
        }
        _ => String::new(),
    };
    let cookie_str = format!("{}={}{}; path=/", name, value, expires);
    js_sys::Reflect::set(&doc, &JsValue::from_str("cookie"), &JsValue::from_str(&cookie_str))?;
    Ok(())
}

/// Reads a cookie value by name. Returns `None` if not found.
pub fn read_cookie(name: &str) -> Option<String> {
    let doc = window().and_then(|w| w.document())?;
    let cookie_str: String = js_sys::Reflect::get(&doc, &JsValue::from_str("cookie"))
        .ok()?
        .as_string()?;

    let prefix = format!("{}=", name);
    for part in cookie_str.split(';') {
        let trimmed = part.trim();
        if trimmed.starts_with(&prefix) {
            return Some(trimmed[prefix.len()..].to_string());
        }
    }
    None
}

/// Expires a cookie by setting it with days=-1.
#[allow(dead_code)]
pub fn erase_cookie(name: &str) -> Result<(), JsValue> {
    create_cookie(name, "", Some(-1))
}
