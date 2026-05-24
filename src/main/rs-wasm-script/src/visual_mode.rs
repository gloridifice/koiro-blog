use crate::cookie;
use wasm_bindgen::prelude::*;
use web_sys::{window, Element, HtmlLinkElement};

const CSS_ID: &str = "visual_mode_css";
const COOKIE_NAME: &str = "visual_mode";

#[derive(Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
enum VisualMode {
    Light = 0,
    Dark = 1,
}

impl VisualMode {
    fn from_str(s: &str) -> Self {
        match s {
            "dark" => VisualMode::Dark,
            _ => VisualMode::Light,
        }
    }
    fn as_str(self) -> &'static str {
        match self {
            VisualMode::Light => "light",
            VisualMode::Dark => "dark",
        }
    }
}

// Thread-local state (WASM is single-threaded)
std::thread_local! {
    static CURRENT_MODE: std::cell::Cell<u8> = const { std::cell::Cell::new(VisualMode::Light as u8) };
}

fn current_mode() -> VisualMode {
    CURRENT_MODE.with(|c| match c.get() {
        1 => VisualMode::Dark,
        _ => VisualMode::Light,
    })
}

fn set_current_mode(mode: VisualMode) {
    CURRENT_MODE.with(|c| c.set(mode as u8));
}

// --- internal helpers ---

fn get_system_visual_mode() -> VisualMode {
    if let Some(win) = window() {
        if let Ok(Some(mql)) = win.match_media("(prefers-color-scheme: dark)") {
            if mql.matches() {
                return VisualMode::Dark;
            }
        }
    }
    VisualMode::Light
}

fn get_init_visual_mode() -> VisualMode {
    if let Some(mode_cookie) = cookie::read_cookie(COOKIE_NAME) {
        return VisualMode::from_str(&mode_cookie);
    }
    let sys_mode = get_system_visual_mode();
    let _ = cookie::create_cookie(COOKIE_NAME, sys_mode.as_str(), None);
    sys_mode
}

fn set_visual_mode(mode: VisualMode) -> Result<(), JsValue> {
    let doc = window()
        .and_then(|w| w.document())
        .ok_or_else(|| JsValue::from_str("no document"))?;
    if let Some(el) = doc.get_element_by_id(CSS_ID) {
        let link: HtmlLinkElement = el.dyn_into()?;
        match mode {
            VisualMode::Light => link.set_href("/assets/css/color_scheme.light_mode.css"),
            VisualMode::Dark => link.set_href("/assets/css/color_scheme.dark_mode.css"),
        }
    }
    set_current_mode(mode);
    cookie::create_cookie(COOKIE_NAME, mode.as_str(), None)?;
    Ok(())
}

fn switch_visual_mode() -> Result<(), JsValue> {
    let next = match current_mode() {
        VisualMode::Light => VisualMode::Dark,
        VisualMode::Dark => VisualMode::Light,
    };
    set_visual_mode(next)
}

// --- public init functions ---

/// Initialize the visual mode `<link>` element and apply the initial mode.
#[wasm_bindgen]
pub fn init_visual_mode() -> Result<(), JsValue> {
    let doc = window()
        .and_then(|w| w.document())
        .ok_or_else(|| JsValue::from_str("no document"))?;

    let visual_mode: HtmlLinkElement = match doc.get_element_by_id(CSS_ID) {
        Some(el) => el.dyn_into()?,
        None => {
            let link: HtmlLinkElement = doc
                .create_element("link")?
                .dyn_into()
                .map_err(|_| JsValue::from_str("failed to create link element"))?;
            link.set_id(CSS_ID);
            link.set_rel("stylesheet");
            doc.head()
                .ok_or_else(|| JsValue::from_str("no head"))?
                .append_child(&link)?;
            link
        }
    };

    let init_mode = get_init_visual_mode();
    match init_mode {
        VisualMode::Light => visual_mode.set_href("/assets/css/color_scheme.light_mode.css"),
        VisualMode::Dark => visual_mode.set_href("/assets/css/color_scheme.dark_mode.css"),
    }
    set_current_mode(init_mode);

    Ok(())
}

/// Attach click listeners to visual-mode switch buttons.
#[wasm_bindgen]
pub fn init_visual_mode_buttons() -> Result<(), JsValue> {
    let doc = window()
        .and_then(|w| w.document())
        .ok_or_else(|| JsValue::from_str("no document"))?;
    let buttons = doc.query_selector_all(".switch_visual_mode_button")?;
    for i in 0..buttons.length() {
        if let Some(btn_node) = buttons.item(i) {
            let btn: Element = btn_node.unchecked_into();
            let cb = Closure::wrap(Box::new(move || {
                let _ = switch_visual_mode();
            }) as Box<dyn FnMut()>);
            btn.add_event_listener_with_callback("click", cb.as_ref().unchecked_ref())?;
            cb.forget();
        }
    }
    Ok(())
}

/// Attach click listeners to post-type filter buttons.
#[wasm_bindgen]
pub fn init_type_buttons() -> Result<(), JsValue> {
    let doc = window()
        .and_then(|w| w.document())
        .ok_or_else(|| JsValue::from_str("no document"))?;
    let buttons = doc.query_selector_all(".post_type_button")?;
    let selected_class = "is_selected";
    let hidden_class = "hidden";

    for i in 0..buttons.length() {
        if let Some(button_node) = buttons.item(i) {
            let btn: Element = button_node.unchecked_into();
            let btn_clone = btn.clone();
            let doc_clone = doc.clone();

            let cb = Closure::wrap(Box::new(move || {
                if btn_clone.class_list().contains(selected_class) {
                    return;
                }
                // Deselect all
                let all_buttons = doc_clone.query_selector_all(".post_type_button").unwrap();
                for j in 0..all_buttons.length() {
                    if let Some(other_node) = all_buttons.item(j) {
                        let other: Element = other_node.unchecked_into();
                        let _ = dom_token_list_remove(&other.class_list(), selected_class);
                    }
                }
                // Select self
                dom_token_list_add(&btn_clone.class_list(), selected_class);

                // Determine target previews id
                let btn_id = btn_clone.id();
                let self_previews_id = if let Some(idx) = btn_id.find('_') {
                    format!("{}_type_previews", &btn_id[..idx])
                } else {
                    format!("{}_type_previews", btn_id)
                };

                // Toggle visibility
                let all_previews =
                    doc_clone.query_selector_all(".post_type_previews").unwrap();
                for j in 0..all_previews.length() {
                    if let Some(preview_node) = all_previews.item(j) {
                        let preview: Element = preview_node.unchecked_into();
                        if preview.id() != self_previews_id {
                            dom_token_list_add(&preview.class_list(), hidden_class);
                        } else {
                            let _ = dom_token_list_remove(&preview.class_list(), hidden_class);
                        }
                    }
                }
            }) as Box<dyn FnMut()>);

            btn.add_event_listener_with_callback("click", cb.as_ref().unchecked_ref())?;
            cb.forget();
        }
    }
    Ok(())
}

/// Initialize all visual-mode related functionality.
#[wasm_bindgen]
pub fn init_visual_mode_all() -> Result<(), JsValue> {
    init_visual_mode()?;
    let _ = init_visual_mode_buttons();
    let _ = init_type_buttons();
    Ok(())
}

// --- DomTokenList helpers (web-sys 0.3 uses &js_sys::Array for add/remove) ---

fn dom_token_list_add(list: &web_sys::DomTokenList, token: &str) {
    let _ = list.add_1(token);
}

fn dom_token_list_remove(list: &web_sys::DomTokenList, token: &str) -> Result<(), JsValue> {
    list.remove_1(token)
}
